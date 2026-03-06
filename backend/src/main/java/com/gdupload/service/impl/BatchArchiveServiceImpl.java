package com.gdupload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdupload.common.BusinessException;
import com.gdupload.dto.ArchiveAnalyzeResult;
import com.gdupload.dto.ArchiveExecuteRequest;
import com.gdupload.dto.ArchiveTmdbItem;
import com.gdupload.dto.GdFileItem;
import com.gdupload.entity.ArchiveBatchTask;
import com.gdupload.entity.ArchiveHistory;
import com.gdupload.entity.GdAccount;
import com.gdupload.mapper.ArchiveBatchTaskMapper;
import com.gdupload.mapper.ArchiveHistoryMapper;
import com.gdupload.service.IBatchArchiveService;
import com.gdupload.service.IArchiveService;
import com.gdupload.service.IGdAccountService;
import com.gdupload.util.RcloneUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 批量归档服务实现
 * 递归遍历 GD 目录下的所有媒体文件，自动完成：
 * 正则解析文件名 → TMDB 匹配 → AI 兜底 → 执行归档 / 标记待处理
 */
@Slf4j
@Service
public class BatchArchiveServiceImpl implements IBatchArchiveService {

    private final ArchiveBatchTaskMapper batchTaskMapper;
    private final ArchiveHistoryMapper archiveHistoryMapper;
    private final IArchiveService archiveService;
    private final IGdAccountService accountService;
    private final RcloneUtil rcloneUtil;
    private final ObjectMapper objectMapper;

    /** 批量归档并行线程数 */
    @Value("${app.archive.batch-threads:24}")
    private int batchThreads;

    /** AI 并发调用数 */
    @Value("${app.archive.ai-concurrency:3}")
    private int aiConcurrency;

    /** AI 调用最小间隔(ms) */
    @Value("${app.archive.ai-call-interval:1000}")
    private long aiCallInterval;

    /** TMDB API 调用间隔(ms) */
    @Value("${app.archive.tmdb-call-interval:100}")
    private long tmdbCallInterval;

    /** 进度刷DB间隔（每处理N个文件刷一次） */
    @Value("${app.archive.db-flush-interval:5}")
    private int dbFlushInterval;

    public BatchArchiveServiceImpl(ArchiveBatchTaskMapper batchTaskMapper,
                                   ArchiveHistoryMapper archiveHistoryMapper,
                                   IArchiveService archiveService,
                                   IGdAccountService accountService,
                                   RcloneUtil rcloneUtil,
                                   ObjectMapper objectMapper) {
        this.batchTaskMapper = batchTaskMapper;
        this.archiveHistoryMapper = archiveHistoryMapper;
        this.archiveService = archiveService;
        this.accountService = accountService;
        this.rcloneUtil = rcloneUtil;
        this.objectMapper = objectMapper;
    }

    /** 支持的媒体文件后缀 */
    private static final Set<String> MEDIA_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mkv", "mp4", "avi", "mov", "wmv", "m4v", "ts", "flv", "rmvb", "rm",
            "mpg", "mpeg", "iso", "m2ts", "webm", "strm"
    ));

    /** 用于父目录名解析：括号年份 (2023)、连字符年份 -2023-、tmdbid 标记、Season 目录 */
    private static final Pattern DIR_YEAR_PAREN   = Pattern.compile("\\((\\d{4})\\)");
    private static final Pattern DIR_YEAR_DASH    = Pattern.compile("-(\\d{4})(?:-|$)");
    private static final Pattern DIR_TMDB_ID      = Pattern.compile("\\[tmdbid=\\d+\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIR_SEASON       = Pattern.compile("(?i)^(season|s)\\s*\\d+$");

    /** AI 调用最后时间戳（用于限速） */
    private volatile long lastAiCallMillis = 0;

    // ─── 启动任务 ──────────────────────────────────────────────────────────────

    @Override
    public ArchiveBatchTask startBatchTask(Long accountId, String sourcePath) {
        GdAccount account = accountService.getById(accountId);
        if (account == null) {
            throw new BusinessException("账号不存在: " + accountId);
        }

        String folderName = extractFolderName(sourcePath);
        String taskName = "批量归档_" + folderName + "_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        ArchiveBatchTask task = new ArchiveBatchTask();
        task.setTaskName(taskName);
        task.setRcloneConfigName(account.getRcloneConfigName());
        task.setSourcePath(sourcePath);
        task.setStatus("PENDING");
        task.setTotalFiles(0);
        task.setProcessedFiles(0);
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setManualCount(0);

        batchTaskMapper.insert(task);

        Long taskId = task.getId();
        String configName = account.getRcloneConfigName();
        Thread thread = new Thread(() -> processBatchTask(taskId, configName, sourcePath));
        thread.setDaemon(true);
        thread.setName("batch-archive-" + taskId);
        thread.start();

        log.info("批量归档任务已创建: id={}, name={}, path={}, threads={}",
                taskId, taskName, sourcePath, batchThreads);
        return task;
    }

    // ─── 异步执行主流程 ────────────────────────────────────────────────────────

    private void processBatchTask(Long taskId, String rcloneConfigName, String sourcePath) {
        processBatchTask(taskId, rcloneConfigName, sourcePath, Collections.emptySet());
    }

    private void processBatchTask(Long taskId, String rcloneConfigName, String sourcePath,
                                  Set<String> skipPaths) {
        try {
            applyUpdate(taskId, t -> {
                t.setStatus("RUNNING");
                t.setCurrentFile("正在扫描文件列表...");
            });

            // 1. 递归列举所有文件（--fast-list 使用 GD 服务端批量列举）
            log.info("[{}] 开始递归列举: configName={}, path={}", taskId, rcloneConfigName, sourcePath);
            String json = rcloneUtil.listJsonRecursive(rcloneConfigName, sourcePath);

            List<GdFileItem> allItems;
            try {
                allItems = objectMapper.readValue(json, new TypeReference<List<GdFileItem>>() {});
            } catch (Exception e) {
                log.error("[{}] 解析文件列表失败", taskId, e);
                applyUpdate(taskId, t -> {
                    t.setStatus("FAILED");
                    t.setErrorMessage("解析文件列表失败: " + e.getMessage());
                });
                return;
            }

            // 2. 过滤媒体文件
            List<GdFileItem> mediaFiles = allItems.stream()
                    .filter(f -> !Boolean.TRUE.equals(f.getIsDir()))
                    .filter(f -> isMediaFile(f.getName()))
                    .collect(Collectors.toList());

            log.info("[{}] 扫描完成: 共 {} 项，媒体文件 {} 个", taskId, allItems.size(), mediaFiles.size());

            // 续跑模式：跳过已完成/已人工标记的文件，不重复处理
            if (!skipPaths.isEmpty()) {
                int before = mediaFiles.size();
                mediaFiles = mediaFiles.stream()
                        .filter(f -> !skipPaths.contains(buildFullPath(sourcePath, f.getPath())))
                        .collect(Collectors.toList());
                log.info("[{}] 续跑模式：跳过已处理文件 {} 个，剩余待处理 {} 个",
                        taskId, before - mediaFiles.size(), mediaFiles.size());
            }

            final int total = mediaFiles.size();
            applyUpdate(taskId, t -> t.setTotalFiles(total));

            if (total == 0) {
                applyUpdate(taskId, t -> { t.setStatus("COMPLETED"); t.setCurrentFile(null); });
                return;
            }

            // 3. 预扫描：正则解析所有文件名，对"正则无法提取标题"的文件批量调用 AI
            Map<String, List<ArchiveTmdbItem>> tmdbCache   = new ConcurrentHashMap<>();
            Map<String, ArchiveAnalyzeResult>  aiResultCache = preScanBatchAi(taskId, mediaFiles, tmdbCache);
            log.info("[{}] 预扫描完成，批量AI已缓存 {} 个文件", taskId, aiResultCache.size());

            // 取消标志
            AtomicInteger cancelFlag = new AtomicInteger(0);

            // ── 原子计数器：替代每次 synchronized DB 读写，定期刷回 ──
            AtomicInteger processedCounter = new AtomicInteger(0);
            AtomicInteger successCounter   = new AtomicInteger(0);
            AtomicInteger failedCounter    = new AtomicInteger(0);
            AtomicInteger manualCounter    = new AtomicInteger(0);
            AtomicInteger sinceLastFlush   = new AtomicInteger(0);

            // 使用可配置线程数（I/O 密集型，rclone moveto + TMDB HTTP）
            log.info("[{}] 启动 {} 线程并行处理", taskId, batchThreads);
            ExecutorService pool = Executors.newFixedThreadPool(batchThreads, r -> {
                Thread t = new Thread(r, "batch-archive-worker");
                t.setDaemon(true);
                return t;
            });
            List<Future<?>> futures = new ArrayList<>(mediaFiles.size());

            // Semaphore 控制提交节奏，与线程数一致
            final Semaphore sem = new Semaphore(batchThreads);

            // AI 调用限流信号量（可配置并发数）
            final Semaphore aiSem = new Semaphore(aiConcurrency);

            for (GdFileItem file : mediaFiles) {
                if (cancelFlag.get() != 0 || Thread.currentThread().isInterrupted()) break;

                // 暂停 & 槽位等待：二合一循环
                while (true) {
                    ArchiveBatchTask snap = batchTaskMapper.selectById(taskId);
                    if (snap == null || "FAILED".equals(snap.getStatus())) {
                        cancelFlag.set(1);
                        break;
                    }
                    if ("PAUSED".equals(snap.getStatus())) {
                        try { Thread.sleep(2000); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            cancelFlag.set(1);
                            break;
                        }
                        continue;
                    }
                    // RUNNING：尝试拿信号量（2秒超时后回头重检DB状态）
                    try {
                        if (sem.tryAcquire(2, TimeUnit.SECONDS)) break;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        cancelFlag.set(1);
                        break;
                    }
                }
                if (cancelFlag.get() != 0) break;

                final String fname = file.getName();
                // 更新当前文件名（低频操作，直接写DB）
                applyUpdate(taskId, t -> t.setCurrentFile(fname));

                futures.add(pool.submit(() -> {
                    try {
                        if (cancelFlag.get() != 0) return;
                        int result = safeProcessOneFile(taskId, rcloneConfigName, sourcePath,
                                file, tmdbCache, aiResultCache, aiSem);
                        // 0=success, 1=failed, 2=manual
                        processedCounter.incrementAndGet();
                        if (result == 0) successCounter.incrementAndGet();
                        else if (result == 1) failedCounter.incrementAndGet();
                        else manualCounter.incrementAndGet();

                        // 每 N 个文件刷一次 DB，减少锁争用
                        if (sinceLastFlush.incrementAndGet() >= dbFlushInterval) {
                            sinceLastFlush.set(0);
                            flushCounters(taskId, processedCounter, successCounter,
                                    failedCounter, manualCounter);
                        }
                    } finally {
                        sem.release();
                    }
                }));
            }

            pool.shutdown();
            try {
                pool.awaitTermination(24, TimeUnit.HOURS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            // 最终刷一次计数器
            flushCounters(taskId, processedCounter, successCounter, failedCounter, manualCounter);

            // 4. 更新最终状态
            ArchiveBatchTask finalSnap = batchTaskMapper.selectById(taskId);
            if (finalSnap != null && !"FAILED".equals(finalSnap.getStatus())) {
                boolean hasIssues = finalSnap.getManualCount() > 0 || finalSnap.getFailedCount() > 0;
                String finalStatus = hasIssues ? "PARTIAL" : "COMPLETED";
                applyUpdate(taskId, t -> { t.setStatus(finalStatus); t.setCurrentFile(null); });
                log.info("[{}] 任务完成: status={}, 线程数={}", taskId, finalStatus, batchThreads);
            }

        } catch (Exception e) {
            log.error("[{}] 批量归档任务异常", taskId, e);
            applyUpdate(taskId, t -> { t.setStatus("FAILED"); t.setErrorMessage(e.getMessage()); });
        }
    }

    // ─── 批量刷计数器到 DB ──────────────────────────────────────────────────────

    private synchronized void flushCounters(Long taskId,
                                            AtomicInteger processed,
                                            AtomicInteger success,
                                            AtomicInteger failed,
                                            AtomicInteger manual) {
        ArchiveBatchTask t = batchTaskMapper.selectById(taskId);
        if (t == null) return;
        t.setProcessedFiles(processed.get());
        t.setSuccessCount(success.get());
        t.setFailedCount(failed.get());
        t.setManualCount(manual.get());
        batchTaskMapper.updateById(t);
    }

    // ─── 单文件处理（返回 0=success, 1=failed, 2=manual）──────────────────────

    private int safeProcessOneFile(Long taskId, String rcloneConfigName,
                                    String sourcePath, GdFileItem file,
                                    Map<String, List<ArchiveTmdbItem>> tmdbCache,
                                    Map<String, ArchiveAnalyzeResult> aiResultCache,
                                    Semaphore aiSem) {
        String filename = file.getName();
        String fullPath = buildFullPath(sourcePath, file.getPath());

        try {
            // 0. 成人内容检测 — 直接标记人工处理
            if (isPornographic(filename)) {
                log.info("[{}] 疑似成人内容，标记人工处理: {}", taskId, filename);
                ArchiveHistory ph = new ArchiveHistory();
                ph.setBatchTaskId(taskId);
                ph.setOriginalPath(fullPath);
                ph.setOriginalFilename(filename);
                ph.setStatus("manual_required");
                ph.setProcessMethod("manual");
                ph.setRemark("疑似成人内容，需人工审核");
                archiveHistoryMapper.insert(ph);
                return 2; // manual
            }

            // 1. 正则解析文件名
            ArchiveAnalyzeResult analyzed = archiveService.analyzeFilename(filename);
            String processMethod = "tmdb";

            // 2. 搜索 TMDB（带缓存）
            List<ArchiveTmdbItem> tmdbResults = Collections.emptyList();
            if (analyzed.getTitle() != null && !analyzed.getTitle().isEmpty()) {
                final String analyzedTitle = analyzed.getTitle();
                final String analyzedYear  = analyzed.getYear();
                final String analyzedType  = analyzed.getMediaType();
                String cacheKey = analyzedTitle.toLowerCase().trim()
                        + "|" + (analyzedYear != null ? analyzedYear : "")
                        + "|" + (analyzedType != null ? analyzedType : "");
                boolean cacheHit = tmdbCache.containsKey(cacheKey);
                tmdbResults = tmdbCache.computeIfAbsent(cacheKey, k ->
                        archiveService.searchTmdb(analyzedTitle, analyzedYear, analyzedType));
                if (!cacheHit && !tmdbResults.isEmpty()) {
                    sleepQuietly(tmdbCallInterval);
                }
            }

            // 3. TMDB 无结果 → AI 兜底
            if (tmdbResults.isEmpty()) {
                try {
                    ArchiveAnalyzeResult aiResult = aiResultCache.get(filename);
                    if (aiResult == null) {
                        // 预扫描未覆盖，走限速请求（可配置并发数）
                        aiSem.acquire();
                        try {
                            long gap = System.currentTimeMillis() - lastAiCallMillis;
                            if (gap < aiCallInterval) Thread.sleep(aiCallInterval - gap);
                            lastAiCallMillis = System.currentTimeMillis();
                            aiResult = archiveService.aiAnalyzeFilename(filename);
                        } finally {
                            aiSem.release();
                        }
                    }
                    if (aiResult != null && aiResult.getTitle() != null && !aiResult.getTitle().isEmpty()) {
                        final ArchiveAnalyzeResult finalAiResult = aiResult;
                        String aiKey = finalAiResult.getTitle().toLowerCase().trim()
                                + "|" + (finalAiResult.getYear() != null ? finalAiResult.getYear() : "")
                                + "|" + (finalAiResult.getMediaType() != null ? finalAiResult.getMediaType() : "");
                        boolean aiCacheHit = tmdbCache.containsKey(aiKey);
                        List<ArchiveTmdbItem> aiTmdb = tmdbCache.computeIfAbsent(aiKey, k ->
                                archiveService.searchTmdb(finalAiResult.getTitle(), finalAiResult.getYear(), finalAiResult.getMediaType()));
                        if (!aiCacheHit && !aiTmdb.isEmpty()) {
                            sleepQuietly(tmdbCallInterval);
                        }
                        if (!aiTmdb.isEmpty()) {
                            analyzed = finalAiResult;
                            tmdbResults = aiTmdb;
                            processMethod = "ai";
                        }
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.warn("[{}] AI 分析失败: {}, err={}", taskId, filename, e.getMessage());
                }
            }

            // 3.5. 仍无结果 → 用父目录名兜底搜索 TMDB
            if (tmdbResults.isEmpty()) {
                ArchiveAnalyzeResult parentAnalyzed = parseParentDir(fullPath);
                if (parentAnalyzed != null) {
                    log.info("[{}] 尝试父目录标题搜索 TMDB: title={} year={}",
                            taskId, parentAnalyzed.getTitle(), parentAnalyzed.getYear());
                    String parentKey = parentAnalyzed.getTitle().toLowerCase().trim()
                            + "|" + (parentAnalyzed.getYear() != null ? parentAnalyzed.getYear() : "")
                            + "|";
                    boolean parentCacheHit = tmdbCache.containsKey(parentKey);
                    List<ArchiveTmdbItem> parentTmdb = tmdbCache.computeIfAbsent(parentKey, k ->
                            archiveService.searchTmdb(parentAnalyzed.getTitle(), parentAnalyzed.getYear(), null));
                    if (!parentCacheHit && !parentTmdb.isEmpty()) {
                        sleepQuietly(tmdbCallInterval);
                    }
                    if (!parentTmdb.isEmpty()) {
                        analyzed.setTitle(parentAnalyzed.getTitle());
                        if (analyzed.getYear() == null) analyzed.setYear(parentAnalyzed.getYear());
                        if (analyzed.getMediaType() == null) analyzed.setMediaType(parentAnalyzed.getMediaType());
                        tmdbResults = parentTmdb;
                        processMethod = "parent_dir";
                    }
                }
            }

            if (!tmdbResults.isEmpty()) {
                // 4a. 有 TMDB 结果 → 执行归档
                ArchiveTmdbItem tmdb = tmdbResults.get(0);
                ArchiveExecuteRequest req = buildRequest(taskId, fullPath, rcloneConfigName, analyzed, tmdb, processMethod);
                Map<String, Object> res = archiveService.executeArchive(req);

                boolean success = Boolean.TRUE.equals(res.get("success"));
                log.info("[{}] 归档{}: {} → {}", taskId, success ? "成功" : "失败",
                        filename, res.get("targetPath"));
                return success ? 0 : 1;

            } else {
                // 4b. 无匹配 → 标记人工处理
                log.info("[{}] 无法匹配，标记待处理: {}", taskId, filename);
                ArchiveHistory h = new ArchiveHistory();
                h.setBatchTaskId(taskId);
                h.setOriginalPath(fullPath);
                h.setOriginalFilename(filename);
                h.setStatus("manual_required");
                h.setProcessMethod("manual");
                h.setRemark("批量归档无法自动匹配 TMDB");
                archiveHistoryMapper.insert(h);
                return 2; // manual
            }

        } catch (Exception e) {
            log.error("[{}] 处理文件失败: {}", taskId, filename, e);
            ArchiveHistory h = new ArchiveHistory();
            h.setBatchTaskId(taskId);
            h.setOriginalPath(fullPath);
            h.setOriginalFilename(filename);
            h.setStatus("failed");
            h.setProcessMethod("auto");
            h.setFailReason(e.getMessage());
            archiveHistoryMapper.insert(h);
            return 1; // failed
        }
    }

    // ─── 查询 ─────────────────────────────────────────────────────────────────

    @Override
    public IPage<ArchiveBatchTask> listTasks(Page<ArchiveBatchTask> page) {
        return batchTaskMapper.selectPage(page,
                new LambdaQueryWrapper<ArchiveBatchTask>()
                        .orderByDesc(ArchiveBatchTask::getCreateTime));
    }

    @Override
    public ArchiveBatchTask getTask(Long taskId) {
        return batchTaskMapper.selectById(taskId);
    }

    @Override
    public IPage<ArchiveHistory> getTaskHistory(Long taskId, Page<ArchiveHistory> page, String status) {
        LambdaQueryWrapper<ArchiveHistory> wrapper = new LambdaQueryWrapper<ArchiveHistory>()
                .eq(ArchiveHistory::getBatchTaskId, taskId)
                .orderByDesc(ArchiveHistory::getCreateTime);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ArchiveHistory::getStatus, status);
        }
        return archiveHistoryMapper.selectPage(page, wrapper);
    }

    @Override
    public void cancelTask(Long taskId) {
        applyUpdate(taskId, t -> {
            t.setStatus("FAILED");
            t.setCurrentFile(null);
            t.setErrorMessage("用户手动取消");
        });
        log.info("批量任务已取消: taskId={}", taskId);
    }

    // ─── 内部工具 ─────────────────────────────────────────────────────────────

    private Set<String> loadProcessedPaths(Long taskId) {
        return archiveHistoryMapper.selectList(
                new LambdaQueryWrapper<ArchiveHistory>()
                        .eq(ArchiveHistory::getBatchTaskId, taskId)
                        .in(ArchiveHistory::getStatus, Arrays.asList("success", "manual_required"))
                        .select(ArchiveHistory::getOriginalPath)
        ).stream()
                .map(ArchiveHistory::getOriginalPath)
                .filter(p -> p != null)
                .collect(Collectors.toSet());
    }

    /** 从 DB 读取 → 应用修改 → 写回（仅用于状态变更等低频操作） */
    private synchronized void applyUpdate(Long taskId, Consumer<ArchiveBatchTask> modifier) {
        ArchiveBatchTask t = batchTaskMapper.selectById(taskId);
        if (t == null) return;
        modifier.accept(t);
        batchTaskMapper.updateById(t);
    }

    private String buildFullPath(String sourcePath, String filePath) {
        if (sourcePath == null || sourcePath.isEmpty()) return filePath;
        if (filePath == null || filePath.isEmpty()) return sourcePath;
        return sourcePath + "/" + filePath;
    }

    private ArchiveAnalyzeResult parseParentDir(String fullPath) {
        int fileSlash = fullPath.lastIndexOf('/');
        if (fileSlash <= 0) return null;
        String parentPath = fullPath.substring(0, fileSlash);

        String dirName = parentPath.contains("/")
                ? parentPath.substring(parentPath.lastIndexOf('/') + 1)
                : parentPath;

        if (DIR_SEASON.matcher(dirName).matches()) {
            int grandSlash = parentPath.lastIndexOf('/');
            if (grandSlash <= 0) return null;
            dirName = parentPath.substring(grandSlash + 1);
        }
        if (dirName.isEmpty()) return null;

        String cleaned = DIR_TMDB_ID.matcher(dirName).replaceAll("").trim();

        String year = null;
        Matcher m = DIR_YEAR_PAREN.matcher(cleaned);
        if (m.find()) {
            year = m.group(1);
            cleaned = (cleaned.substring(0, m.start()) + cleaned.substring(m.end())).trim();
        } else {
            m = DIR_YEAR_DASH.matcher(cleaned);
            if (m.find()) {
                year = m.group(1);
                cleaned = (cleaned.substring(0, m.start()) + cleaned.substring(m.end())).trim();
            }
        }

        cleaned = cleaned.replaceAll("^[-\\s]+|[-\\s]+$", "").trim();
        if (cleaned.isEmpty()) return null;

        ArchiveAnalyzeResult result = new ArchiveAnalyzeResult();
        result.setTitle(cleaned);
        result.setYear(year);
        return result;
    }

    private String extractFolderName(String path) {
        if (path == null || path.isEmpty()) return "根目录";
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int last = p.lastIndexOf('/');
        String name = last >= 0 ? p.substring(last + 1) : p;
        return name.isEmpty() ? "根目录" : name;
    }

    @Override
    public void pauseTask(Long taskId) {
        applyUpdate(taskId, t -> {
            if ("RUNNING".equals(t.getStatus())) {
                t.setStatus("PAUSED");
            }
        });
        log.info("批量任务已暂停: taskId={}", taskId);
    }

    @Override
    public void resumeTask(Long taskId) {
        ArchiveBatchTask task = batchTaskMapper.selectById(taskId);
        if (task == null) return;

        if ("PAUSED".equals(task.getStatus())) {
            applyUpdate(taskId, t -> t.setStatus("RUNNING"));
            log.info("批量任务已恢复(PAUSED→RUNNING): taskId={}", taskId);

        } else if ("FAILED".equals(task.getStatus())) {
            Set<String> skipPaths = loadProcessedPaths(taskId);
            applyUpdate(taskId, t -> {
                t.setStatus("PENDING");
                t.setErrorMessage(null);
                t.setCurrentFile(null);
                t.setProcessedFiles(0);
                t.setSuccessCount(0);
                t.setFailedCount(0);
                t.setManualCount(0);
            });
            String configName  = task.getRcloneConfigName();
            String sourcePath  = task.getSourcePath();
            Thread thread = new Thread(() -> processBatchTask(taskId, configName, sourcePath, skipPaths));
            thread.setDaemon(true);
            thread.setName("batch-archive-" + taskId);
            thread.start();
            log.info("批量任务已重新提交(FAILED→续跑): taskId={}, 跳过已处理 {} 个文件", taskId, skipPaths.size());
        }
    }

    // ─── 成人内容检测 ─────────────────────────────────────────────────────────

    private static final Set<String> ADULT_KEYWORDS = new HashSet<>(Arrays.asList(
            "xxx", "porn", "hentai", "erotic",
            "无码", "有码", "中出", "巨乳", "内射", "无修正"
    ));

    private static final Pattern AV_CODE_PATTERN =
            Pattern.compile("(?i)(^|[\\[\\s])[a-z]{2,5}-\\d{3,6}($|[\\]\\s.])");

    // ─── 预扫描：批量 AI ──────────────────────────────────────────────────────

    private Map<String, ArchiveAnalyzeResult> preScanBatchAi(
            Long taskId,
            List<GdFileItem> mediaFiles,
            Map<String, List<ArchiveTmdbItem>> tmdbCache) {

        Map<String, ArchiveAnalyzeResult> aiResultCache = new ConcurrentHashMap<>();

        List<String> aiNeeded = new ArrayList<>();
        for (GdFileItem file : mediaFiles) {
            ArchiveAnalyzeResult analyzed = archiveService.analyzeFilename(file.getName());
            if (analyzed.getTitle() == null || analyzed.getTitle().trim().isEmpty()) {
                aiNeeded.add(file.getName());
            }
        }

        if (aiNeeded.isEmpty()) return aiResultCache;
        log.info("[{}] 预扫描：发现 {} 个无标题文件，将批量 AI 识别", taskId, aiNeeded.size());

        final int BATCH_SIZE = 20;
        for (int i = 0; i < aiNeeded.size(); i += BATCH_SIZE) {
            ArchiveBatchTask snap = batchTaskMapper.selectById(taskId);
            if (snap == null || "FAILED".equals(snap.getStatus())) break;

            List<String> chunk = aiNeeded.subList(i, Math.min(i + BATCH_SIZE, aiNeeded.size()));
            try {
                List<ArchiveAnalyzeResult> results = archiveService.batchAiAnalyzeFilenames(chunk);
                for (int j = 0; j < chunk.size(); j++) {
                    String fname = chunk.get(j);
                    ArchiveAnalyzeResult aiResult = j < results.size() ? results.get(j) : null;
                    if (aiResult == null || aiResult.getTitle() == null || aiResult.getTitle().trim().isEmpty()) {
                        continue;
                    }
                    aiResultCache.put(fname, aiResult);

                    String aiKey = aiResult.getTitle().toLowerCase().trim()
                            + "|" + (aiResult.getYear()      != null ? aiResult.getYear()      : "")
                            + "|" + (aiResult.getMediaType() != null ? aiResult.getMediaType() : "");
                    if (!tmdbCache.containsKey(aiKey)) {
                        List<ArchiveTmdbItem> aiTmdb = archiveService.searchTmdb(
                                aiResult.getTitle(), aiResult.getYear(), aiResult.getMediaType());
                        tmdbCache.put(aiKey, aiTmdb);
                        if (!aiTmdb.isEmpty()) {
                            sleepQuietly(tmdbCallInterval);
                        }
                    }
                }
                log.info("[{}] 批量AI第 {}/{} 批完成", taskId,
                        (i / BATCH_SIZE + 1), (int) Math.ceil((double) aiNeeded.size() / BATCH_SIZE));
            } catch (Exception e) {
                log.warn("[{}] 批量AI第 {} 批失败，跳过: {}", taskId, i / BATCH_SIZE + 1, e.getMessage());
            }
        }

        return aiResultCache;
    }

    private boolean isPornographic(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        for (String kw : ADULT_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return AV_CODE_PATTERN.matcher(lower).find();
    }

    private boolean isMediaFile(String filename) {
        if (filename == null) return false;
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return false;
        return MEDIA_EXTENSIONS.contains(filename.substring(dot + 1).toLowerCase());
    }

    private ArchiveExecuteRequest buildRequest(Long taskId, String fullPath, String rcloneConfigName,
                                               ArchiveAnalyzeResult analyzed, ArchiveTmdbItem tmdb, String processMethod) {
        ArchiveExecuteRequest req = new ArchiveExecuteRequest();
        req.setBatchTaskId(taskId);
        req.setOriginalPath(fullPath);
        req.setRcloneConfigName(rcloneConfigName);

        String newFilename = analyzed.getSuggestedFilename();
        if (newFilename == null || newFilename.trim().isEmpty()) {
            newFilename = fullPath.contains("/")
                    ? fullPath.substring(fullPath.lastIndexOf('/') + 1)
                    : fullPath;
        }
        req.setNewFilename(newFilename);

        req.setCategory(tmdb.getSuggestedCategory());
        req.setDirName(tmdb.getSuggestedDirName());

        if ("tv".equals(analyzed.getMediaType())
                && analyzed.getSeason() != null && !analyzed.getSeason().isEmpty()) {
            try {
                int seasonNum = Integer.parseInt(analyzed.getSeason());
                req.setSeasonDir("Season " + seasonNum);
            } catch (NumberFormatException ignore) { }
        }

        req.setTmdbId(tmdb.getTmdbId() != null ? tmdb.getTmdbId().toString() : null);
        req.setTmdbTitle(tmdb.getTitle());
        req.setProcessMethod(processMethod);

        return req;
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) return;
        try { Thread.sleep(millis); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
