package com.gdupload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.entity.StrmFileRecord;
import com.gdupload.entity.StrmWatchConfig;
import com.gdupload.mapper.StrmFileRecordMapper;
import com.gdupload.mapper.StrmWatchConfigMapper;
import com.gdupload.service.ISmartSearchConfigService;
import com.gdupload.service.IStrmWatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * STRM 监控同步服务实现
 *
 * 同步算法：
 *  1. rclone lsf --format "pt" → Map&lt;relPath, mtime&gt;
 *  2. DB 查非 deleted 记录 → Map&lt;relPath, StrmFileRecord&gt;
 *  3. 删除：在 DB 不在 GD → 删本地文件 + 更新 DB status=deleted
 *  4. 更新：两边都有但 mtime 变化，或 forceOverwrite
 *  5. 新增：在 GD 不在 DB（或 status=failed）
 *  6. 更新 strm_watch_config 统计字段
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrmWatchServiceImpl implements IStrmWatchService {

    private final StrmWatchConfigMapper    configMapper;
    private final StrmFileRecordMapper     fileRecordMapper;
    private final StrmCoreHelper           coreHelper;
    private final ISmartSearchConfigService globalConfigService;

    /** 每批 DB 批量写入的大小 */
    private static final int DB_BATCH_SIZE = 500;

    /** 文件处理并发数（每个同步任务内部并行处理文件的线程数） */
    @Value("${app.strm.process-concurrency:16}")
    private int processConcurrency;

    /** 各配置独立的同步状态，key = configId */
    private final Map<Long, SyncStatus> syncStatusMap = new ConcurrentHashMap<>();

    /** 防止同一配置并发同步 */
    private final Set<Long> runningSyncs = ConcurrentHashMap.newKeySet();

    /** 异步线程池 */
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("strm-watch-" + t.getId());
        return t;
    });

    // ─── 内部状态类 ───────────────────────────────────────────────────────────

    static class SyncStatus {
        volatile String phase        = "IDLE"; // IDLE/RUNNING/DONE/ERROR
        final AtomicInteger total        = new AtomicInteger();
        final AtomicInteger processed    = new AtomicInteger();
        final AtomicInteger newCount     = new AtomicInteger();
        final AtomicInteger deletedCount = new AtomicInteger();
        final AtomicInteger updatedCount = new AtomicInteger();
        final AtomicInteger failedCount  = new AtomicInteger();
        volatile String currentFile  = "";
        volatile String errorMessage = "";
        // LinkedList: 头部删除 O(1)，ArrayList 头部删除 O(n)
        // 海量文件时 ArrayList 会因频繁 remove(0) 产生严重性能问题
        final List<String> logs = Collections.synchronizedList(new LinkedList<>());
    }

    // ─── 接口实现 ─────────────────────────────────────────────────────────────

    @Override
    public IPage<StrmWatchConfig> listConfigs(int page, int size) {
        return configMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<StrmWatchConfig>().orderByDesc("create_time"));
    }

    @Override
    public void addConfig(StrmWatchConfig config) {
        config.setStatus("IDLE");
        config.setEnabled(config.getEnabled() != null ? config.getEnabled() : 1);
        config.setScanIntervalMinutes(config.getScanIntervalMinutes() != null
                ? config.getScanIntervalMinutes() : 60);
        configMapper.insert(config);
    }

    @Override
    public void updateConfig(StrmWatchConfig config) {
        configMapper.updateById(config);
    }

    @Override
    public void deleteConfig(Long id) {
        fileRecordMapper.delete(new QueryWrapper<StrmFileRecord>().eq("watch_config_id", id));
        configMapper.deleteById(id);
        syncStatusMap.remove(id);
    }

    @Override
    public void enableConfig(Long id) {
        StrmWatchConfig c = new StrmWatchConfig();
        c.setId(id);
        c.setEnabled(1);
        configMapper.updateById(c);
    }

    @Override
    public void disableConfig(Long id) {
        StrmWatchConfig c = new StrmWatchConfig();
        c.setId(id);
        c.setEnabled(0);
        configMapper.updateById(c);
    }

    @Override
    public void triggerSync(Long id) {
        submitSync(id, false);
    }

    @Override
    public void triggerForceRescrape(Long id) {
        submitSync(id, true);
    }

    @Override
    public IPage<StrmFileRecord> listRecords(Long configId, String status, int page, int size) {
        QueryWrapper<StrmFileRecord> qw = new QueryWrapper<StrmFileRecord>()
                .eq("watch_config_id", configId)
                .orderByDesc("update_time");
        if (status != null && !status.isEmpty()) {
            qw.eq("status", status);
        }
        return fileRecordMapper.selectPage(new Page<>(page, size), qw);
    }

    @Override
    public Map<String, Object> getSyncStatus(Long configId) {
        SyncStatus s = syncStatusMap.getOrDefault(configId, new SyncStatus());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("phase",        s.phase);
        r.put("total",        s.total.get());
        r.put("processed",    s.processed.get());
        r.put("newCount",     s.newCount.get());
        r.put("deletedCount", s.deletedCount.get());
        r.put("updatedCount", s.updatedCount.get());
        r.put("failedCount",  s.failedCount.get());
        r.put("currentFile",  s.currentFile);
        r.put("logs",         new ArrayList<>(s.logs));
        r.put("errorMessage", s.errorMessage);
        return r;
    }

    @Override
    public void checkAndTriggerScheduled() {
        List<StrmWatchConfig> due = configMapper.selectList(
                new QueryWrapper<StrmWatchConfig>()
                        .eq("enabled", 1)
                        .eq("status", "IDLE")
                        .and(qw -> qw
                                .isNull("next_scan_time")
                                .or()
                                .le("next_scan_time", LocalDateTime.now()))
        );
        for (StrmWatchConfig cfg : due) {
            log.info("[StrmWatch] 定时触发同步: id={}, name={}", cfg.getId(), cfg.getName());
            submitSync(cfg.getId(), false);
        }
    }

    // ─── 同步提交与执行 ───────────────────────────────────────────────────────

    private void submitSync(Long configId, boolean forceOverwrite) {
        if (!runningSyncs.add(configId)) {
            log.warn("[StrmWatch] configId={} 已在同步中，跳过", configId);
            return;
        }
        SyncStatus syncStatus = new SyncStatus();
        syncStatusMap.put(configId, syncStatus);

        executor.submit(() -> {
            try {
                doSync(configId, forceOverwrite, syncStatus);
            } finally {
                runningSyncs.remove(configId);
            }
        });
    }

    private void doSync(Long configId, boolean forceOverwrite, SyncStatus syncStatus) {
        StrmWatchConfig config = configMapper.selectById(configId);
        if (config == null) {
            syncStatus.phase        = "ERROR";
            syncStatus.errorMessage = "配置不存在";
            return;
        }

        // 标记 DB 状态为 RUNNING
        StrmWatchConfig runningUpdate = new StrmWatchConfig();
        runningUpdate.setId(configId);
        runningUpdate.setStatus("RUNNING");
        configMapper.updateById(runningUpdate);
        syncStatus.phase = "RUNNING";

        // 读取全局 TMDB 配置
        String tmdbApiKey, language;
        try {
            Map<String, Object> globalCfg = globalConfigService.getFullConfig("default");
            tmdbApiKey = strOf(globalCfg.getOrDefault("tmdbApiKey",   ""));
            language   = strOf(globalCfg.getOrDefault("tmdbLanguage", "zh-CN"));
        } catch (Exception e) {
            tmdbApiKey = "";
            language   = "zh-CN";
        }
        // lambda 只能捕获 effectively final 变量，try-catch 双分支赋值使编译器无法判定，用 final 副本解决
        final String tmdbKey  = tmdbApiKey;
        final String tmdbLang = language;

        addLog(syncStatus, "开始同步: " + config.getName() + (forceOverwrite ? " [强制重刮]" : ""));

        try {
            // ── 1. 流式扫描 GD 文件（rclone 输出逐行读取，不缓冲整个字符串）
            addLog(syncStatus, "正在扫描 " + config.getGdRemote() + ":" + config.getGdSourcePath() + " ...");
            Map<String, String> gdFiles = coreHelper.listGdFilesWithMtime(
                    config.getGdRemote(), config.getGdSourcePath());
            int gdTotal = gdFiles.size();
            addLog(syncStatus, "GD 文件数: " + gdTotal);
            syncStatus.total.set(gdTotal);

            // ── 2. 分页加载 DB 记录，避免百万级 selectList 一次 OOM
            addLog(syncStatus, "加载 DB 已有记录...");
            Map<String, StrmFileRecord> dbMap = loadDbRecordsPaged(configId);
            addLog(syncStatus, "DB 记录数: " + dbMap.size());

            // ── 3. 删除处理：在 DB 但不在 GD（批量标记 deleted）
            // 已是 deleted 状态的记录跳过（避免重复处理历史删除记录）
            Set<String> gdPathSet = gdFiles.keySet();
            List<StrmFileRecord> toDelete = dbMap.values().stream()
                    .filter(r -> !gdPathSet.contains(r.getRelFilePath())
                            && !"deleted".equals(r.getStatus()))
                    .collect(Collectors.toList());
            if (!toDelete.isEmpty()) {
                processDeletes(toDelete, syncStatus);
            }

            // ── 4/5. 新增 & 更新处理（并行）
            // ConcurrentHashMap 保证 showCache 多线程安全（computeIfAbsent 原子性）
            Map<String, StrmCoreHelper.ShowCache> showCache = new ConcurrentHashMap<>();
            ConcurrentLinkedQueue<StrmFileRecord> insertQueue = new ConcurrentLinkedQueue<>();
            ConcurrentLinkedQueue<StrmFileRecord> updateQueue = new ConcurrentLinkedQueue<>();

            int concurrency = Math.max(1, processConcurrency);
            ExecutorService filePool = Executors.newFixedThreadPool(concurrency, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("strm-proc-" + t.getId());
                return t;
            });
            List<Future<?>> futures = new ArrayList<>(gdFiles.size());

            for (Map.Entry<String, String> e : gdFiles.entrySet()) {
                final String relPath = e.getKey();
                final String mtime   = e.getValue();
                final StrmFileRecord existing = dbMap.get(relPath);
                // deleted：文件曾被删后重新出现在 GD，需重新处理
                final boolean isNew = existing == null
                        || "failed".equals(existing.getStatus())
                        || "deleted".equals(existing.getStatus());
                final boolean isUpdated = !isNew && !mtime.equals(existing.getFileModTime());

                // 未变化且不强制：跳过（仍计入进度）
                if (!isNew && !isUpdated && !forceOverwrite) {
                    syncStatus.processed.incrementAndGet();
                    continue;
                }

                futures.add(filePool.submit(() -> {
                    // 删除旧文件（更新 / 强制覆盖时）
                    if (existing != null && (isUpdated || forceOverwrite)) {
                        coreHelper.deleteLocalFiles(
                                existing.getStrmLocalPath(), existing.getNfoLocalPath(), existing.getShowDir());
                    }

                    try {
                        StrmCoreHelper.StrmFileResult result = coreHelper.processFileToStrm(
                                config.getGdRemote(), config.getGdSourcePath(), relPath,
                                config.getOutputPath(), config.getPlayUrlBase(),
                                tmdbKey, tmdbLang, showCache);

                        StrmFileRecord record = existing != null ? existing : new StrmFileRecord();
                        record.setWatchConfigId(configId);
                        record.setGdRemote(config.getGdRemote());
                        record.setRelFilePath(relPath);
                        record.computePathHash();
                        record.setFileModTime(mtime);
                        record.setStrmLocalPath(result.strmLocalPath);
                        record.setNfoLocalPath(result.nfoLocalPath);
                        record.setShowDir(result.showDir);
                        record.setTmdbId(result.tmdbId);
                        record.setStatus("success");
                        record.setFailReason(null);

                        if (existing == null) {
                            insertQueue.add(record);
                            syncStatus.newCount.incrementAndGet();
                        } else {
                            updateQueue.add(record);
                            if (isNew || isUpdated || forceOverwrite) syncStatus.updatedCount.incrementAndGet();
                        }

                        // 每 200 条成功记录打一次进度日志
                        int doneOk = syncStatus.newCount.get() + syncStatus.updatedCount.get();
                        if (doneOk == 1 || doneOk % 200 == 0) {
                            addLog(syncStatus, "进度: 已处理 " + syncStatus.processed.get()
                                    + "/" + syncStatus.total.get()
                                    + "  新增=" + syncStatus.newCount.get()
                                    + " 更新=" + syncStatus.updatedCount.get()
                                    + " 失败=" + syncStatus.failedCount.get());
                        }

                    } catch (Exception ex) {
                        log.error("[StrmWatch] 处理文件失败: {}", relPath, ex);
                        addLog(syncStatus, "✗ 失败: " + relPath + " — " + ex.getMessage());

                        StrmFileRecord record = existing != null ? existing : new StrmFileRecord();
                        record.setWatchConfigId(configId);
                        record.setGdRemote(config.getGdRemote());
                        record.setRelFilePath(relPath);
                        record.computePathHash();
                        record.setFileModTime(mtime);
                        record.setStatus("failed");
                        record.setFailReason(ex.getMessage() != null
                                ? ex.getMessage().substring(0, Math.min(500, ex.getMessage().length())) : "unknown");
                        // 失败记录立即写入（不批量，方便断点续传）
                        if (existing == null) fileRecordMapper.insert(record);
                        else                 fileRecordMapper.updateById(record);
                        syncStatus.failedCount.incrementAndGet();
                    } finally {
                        syncStatus.processed.incrementAndGet();
                    }
                }));
            }

            // 等待所有文件处理完成（大目录可能耗时数小时）
            filePool.shutdown();
            try {
                filePool.awaitTermination(24, TimeUnit.HOURS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[StrmWatch] 等待文件处理完成时被中断");
            }

            // 一次性批量刷库
            flushInsert(new ArrayList<>(insertQueue));
            flushUpdate(new ArrayList<>(updateQueue));

            // ── 6. 更新配置统计
            long totalValid = fileRecordMapper.selectCount(
                    new QueryWrapper<StrmFileRecord>()
                            .eq("watch_config_id", configId)
                            .eq("status", "success"));

            StrmWatchConfig doneUpdate = new StrmWatchConfig();
            doneUpdate.setId(configId);
            doneUpdate.setStatus("IDLE");
            doneUpdate.setLastScanTime(LocalDateTime.now());
            doneUpdate.setNextScanTime(LocalDateTime.now().plusMinutes(config.getScanIntervalMinutes()));
            doneUpdate.setLastNewCount(syncStatus.newCount.get());
            doneUpdate.setLastDeletedCount(syncStatus.deletedCount.get());
            doneUpdate.setLastUpdatedCount(syncStatus.updatedCount.get());
            doneUpdate.setTotalFiles((int) totalValid);
            configMapper.updateById(doneUpdate);

            syncStatus.phase = "DONE";
            addLog(syncStatus, "✓ 全部完成  新增=" + syncStatus.newCount.get()
                    + " 删除=" + syncStatus.deletedCount.get()
                    + " 更新=" + syncStatus.updatedCount.get()
                    + " 失败=" + syncStatus.failedCount.get());

        } catch (Exception e) {
            log.error("[StrmWatch] 同步异常: configId={}", configId, e);

            StrmWatchConfig errUpdate = new StrmWatchConfig();
            errUpdate.setId(configId);
            errUpdate.setStatus("IDLE");
            configMapper.updateById(errUpdate);

            syncStatus.phase        = "ERROR";
            syncStatus.errorMessage = e.getMessage();
            addLog(syncStatus, "✗ 同步异常: " + e.getMessage());
        }
    }

    // ─── 辅助：分页加载 DB 记录 ────────────────────────────────────────────────

    /**
     * 分 10000 条/页加载 DB 已有记录，避免一次性 selectList 把百万条记录全加载进内存。
     * 同时只 select 比对需要的字段，减少单条记录内存占用。
     *
     * 注意：包含 deleted 记录——当 GD 上的文件被删除后重新添加时，DB 中已有该路径的
     * deleted 行；若此处过滤掉它，重新插入时会触发唯一键冲突。
     */
    private Map<String, StrmFileRecord> loadDbRecordsPaged(Long configId) {
        Map<String, StrmFileRecord> dbMap = new HashMap<>();
        long pageNum = 1;
        final int PAGE_SIZE = 10_000;
        while (true) {
            IPage<StrmFileRecord> page = fileRecordMapper.selectPage(
                    new Page<>(pageNum, PAGE_SIZE),
                    new QueryWrapper<StrmFileRecord>()
                            .eq("watch_config_id", configId)
                            .select("id", "rel_file_path", "file_mod_time",
                                    "strm_local_path", "nfo_local_path", "show_dir",
                                    "tmdb_id", "status"));
            for (StrmFileRecord r : page.getRecords()) {
                dbMap.put(r.getRelFilePath(), r);
            }
            if (pageNum >= page.getPages()) break;
            pageNum++;
        }
        return dbMap;
    }

    // ─── 辅助：批量删除 ────────────────────────────────────────────────────────

    private void processDeletes(List<StrmFileRecord> toDelete, SyncStatus syncStatus) {
        addLog(syncStatus, "发现 " + toDelete.size() + " 个已从 GD 删除的文件，正在清理本地...");
        // 先删除本地文件（传入 showDir 作为向上清理的边界）
        for (StrmFileRecord r : toDelete) {
            coreHelper.deleteLocalFiles(r.getStrmLocalPath(), r.getNfoLocalPath(), r.getShowDir());
            syncStatus.deletedCount.incrementAndGet();
        }
        // 批量更新 DB status=deleted（按 1000 条分批避免 IN 子句过长）
        List<Long> ids = toDelete.stream().map(StrmFileRecord::getId).collect(Collectors.toList());
        for (int i = 0; i < ids.size(); i += 1000) {
            List<Long> batch = ids.subList(i, Math.min(i + 1000, ids.size()));
            fileRecordMapper.update(null,
                    new UpdateWrapper<StrmFileRecord>().in("id", batch).set("status", "deleted"));
        }
        addLog(syncStatus, "🗑 已清理 " + toDelete.size() + " 条删除记录");
    }

    // ─── 辅助：批量 INSERT / UPDATE ────────────────────────────────────────────

    private void flushInsert(List<StrmFileRecord> records) {
        if (records.isEmpty()) return;
        try {
            fileRecordMapper.insertBatch(records);
        } catch (Exception e) {
            // Fallback：逐条 insert（保证数据不丢）
            log.warn("[StrmWatch] 批量 insert 失败，降级为逐条: {}", e.getMessage());
            for (StrmFileRecord r : records) {
                try { fileRecordMapper.insert(r); } catch (Exception e2) {
                    log.warn("[StrmWatch] insert 失败: {}", e2.getMessage());
                }
            }
        }
    }

    private void flushUpdate(List<StrmFileRecord> records) {
        // updateBatchById 无原生批量接口，逐条执行即可（更新通常远少于插入）
        for (StrmFileRecord r : records) {
            try { fileRecordMapper.updateById(r); } catch (Exception e) {
                log.warn("[StrmWatch] update 失败: id={}, err={}", r.getId(), e.getMessage());
            }
        }
    }

    private void addLog(SyncStatus syncStatus, String msg) {
        if (syncStatus.logs.size() >= 2000) syncStatus.logs.remove(0);
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        syncStatus.logs.add("[" + time + "] " + msg);
        log.info("[StrmWatch] {}", msg);
    }

    private String strOf(Object o) {
        return o == null ? "" : o.toString();
    }
}
