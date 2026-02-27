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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
@RequiredArgsConstructor
public class BatchArchiveServiceImpl implements IBatchArchiveService {

    private final ArchiveBatchTaskMapper batchTaskMapper;
    private final ArchiveHistoryMapper archiveHistoryMapper;
    private final IArchiveService archiveService;
    private final IGdAccountService accountService;
    private final RcloneUtil rcloneUtil;
    private final ObjectMapper objectMapper;

    /** 支持的媒体文件后缀 */
    private static final Set<String> MEDIA_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mkv", "mp4", "avi", "mov", "wmv", "m4v", "ts", "flv", "rmvb", "rm",
            "mpg", "mpeg", "iso", "m2ts", "webm", "strm"
    ));

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

        log.info("批量归档任务已创建: id={}, name={}, path={}", taskId, taskName, sourcePath);
        return task;
    }

    // ─── 异步执行主流程 ────────────────────────────────────────────────────────

    private void processBatchTask(Long taskId, String rcloneConfigName, String sourcePath) {
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

            final int total = mediaFiles.size();
            applyUpdate(taskId, t -> t.setTotalFiles(total));

            if (total == 0) {
                applyUpdate(taskId, t -> { t.setStatus("COMPLETED"); t.setCurrentFile(null); });
                return;
            }

            // 3. 逐文件处理
            for (GdFileItem file : mediaFiles) {
                if (Thread.currentThread().isInterrupted()) break;

                // 检查是否已被取消 / 暂停
                boolean shouldStop = false;
                while (true) {
                    ArchiveBatchTask snap = batchTaskMapper.selectById(taskId);
                    if (snap == null || "FAILED".equals(snap.getStatus())) {
                        log.info("[{}] 任务已被取消，停止处理", taskId);
                        shouldStop = true;
                        break;
                    }
                    if ("PAUSED".equals(snap.getStatus())) {
                        log.debug("[{}] 任务已暂停，等待恢复...", taskId);
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            shouldStop = true;
                            break;
                        }
                        continue;
                    }
                    break; // RUNNING — 继续处理
                }
                if (shouldStop) break;

                // 更新当前处理文件
                final String fname = file.getName();
                applyUpdate(taskId, t -> t.setCurrentFile(fname));

                safeProcessOneFile(taskId, rcloneConfigName, sourcePath, file);

                // TMDB 限速：每次 HTTP 请求之间稍作等待
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // 4. 更新最终状态
            ArchiveBatchTask finalSnap = batchTaskMapper.selectById(taskId);
            if (finalSnap != null && !"FAILED".equals(finalSnap.getStatus())) {
                boolean hasIssues = finalSnap.getManualCount() > 0 || finalSnap.getFailedCount() > 0;
                String finalStatus = hasIssues ? "PARTIAL" : "COMPLETED";
                applyUpdate(taskId, t -> { t.setStatus(finalStatus); t.setCurrentFile(null); });
                log.info("[{}] 任务完成: status={}", taskId, finalStatus);
            }

        } catch (Exception e) {
            log.error("[{}] 批量归档任务异常", taskId, e);
            applyUpdate(taskId, t -> { t.setStatus("FAILED"); t.setErrorMessage(e.getMessage()); });
        }
    }

    // ─── 单文件处理 ───────────────────────────────────────────────────────────

    private void safeProcessOneFile(Long taskId, String rcloneConfigName,
                                    String sourcePath, GdFileItem file) {
        String filename = file.getName();
        // file.getPath() 是相对于 sourcePath 的路径
        String fullPath = buildFullPath(sourcePath, file.getPath());

        try {
            // 0. 成人内容检测 — 直接标记人工处理，跳过 TMDB/AI 流程
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
                applyUpdate(taskId, t -> {
                    t.setProcessedFiles(t.getProcessedFiles() + 1);
                    t.setManualCount(t.getManualCount() + 1);
                });
                return;
            }

            // 1. 正则解析文件名
            ArchiveAnalyzeResult analyzed = archiveService.analyzeFilename(filename);
            String processMethod = "tmdb";

            // 2. 搜索 TMDB
            List<ArchiveTmdbItem> tmdbResults = Collections.emptyList();
            if (analyzed.getTitle() != null && !analyzed.getTitle().isEmpty()) {
                tmdbResults = archiveService.searchTmdb(
                        analyzed.getTitle(), analyzed.getYear(), analyzed.getMediaType());
            }

            // 3. TMDB 无结果 → AI 兜底
            if (tmdbResults.isEmpty()) {
                try {
                    ArchiveAnalyzeResult aiResult = archiveService.aiAnalyzeFilename(filename);
                    if (aiResult.getTitle() != null && !aiResult.getTitle().isEmpty()) {
                        List<ArchiveTmdbItem> aiTmdb = archiveService.searchTmdb(
                                aiResult.getTitle(), aiResult.getYear(), aiResult.getMediaType());
                        if (!aiTmdb.isEmpty()) {
                            analyzed = aiResult;
                            tmdbResults = aiTmdb;
                            processMethod = "ai";
                        }
                    }
                } catch (Exception e) {
                    log.warn("[{}] AI 分析失败: {}, err={}", taskId, filename, e.getMessage());
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

                applyUpdate(taskId, t -> {
                    t.setProcessedFiles(t.getProcessedFiles() + 1);
                    if (success) t.setSuccessCount(t.getSuccessCount() + 1);
                    else         t.setFailedCount(t.getFailedCount() + 1);
                });

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

                applyUpdate(taskId, t -> {
                    t.setProcessedFiles(t.getProcessedFiles() + 1);
                    t.setManualCount(t.getManualCount() + 1);
                });
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

            applyUpdate(taskId, t -> {
                t.setProcessedFiles(t.getProcessedFiles() + 1);
                t.setFailedCount(t.getFailedCount() + 1);
            });
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

    /** 从 DB 读取 → 应用修改 → 写回，保证计数器原子性累加 */
    private void applyUpdate(Long taskId, Consumer<ArchiveBatchTask> modifier) {
        ArchiveBatchTask t = batchTaskMapper.selectById(taskId);
        if (t == null) return;
        modifier.accept(t);
        batchTaskMapper.updateById(t);
    }

    /** 构建完整的远程相对路径 */
    private String buildFullPath(String sourcePath, String filePath) {
        if (sourcePath == null || sourcePath.isEmpty()) return filePath;
        if (filePath == null || filePath.isEmpty()) return sourcePath;
        return sourcePath + "/" + filePath;
    }

    /** 提取路径中最后一段作为文件夹名 */
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
        applyUpdate(taskId, t -> {
            if ("PAUSED".equals(t.getStatus())) {
                t.setStatus("RUNNING");
            }
        });
        log.info("批量任务已恢复: taskId={}", taskId);
    }

    // ─── 成人内容检测 ─────────────────────────────────────────────────────────

    /** 通用成人内容关键词 */
    private static final Set<String> ADULT_KEYWORDS = new HashSet<>(Arrays.asList(
            "xxx", "porn", "hentai", "erotic",
            "无码", "有码", "中出", "巨乳", "内射", "无修正"
    ));

    /**
     * 日本 AV 片号格式：2-5 个大写字母 + 连字符 + 3-6 位数字
     * 匹配出现在文件名开头、方括号后或空格后的独立片号，降低误判率
     */
    private static final Pattern AV_CODE_PATTERN =
            Pattern.compile("(?i)(^|[\\[\\s])[a-z]{2,5}-\\d{3,6}($|[\\]\\s.])");

    /** 判断文件名是否含有成人内容特征 */
    private boolean isPornographic(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        for (String kw : ADULT_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return AV_CODE_PATTERN.matcher(lower).find();
    }

    /** 判断是否为媒体文件 */
    private boolean isMediaFile(String filename) {
        if (filename == null) return false;
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return false;
        return MEDIA_EXTENSIONS.contains(filename.substring(dot + 1).toLowerCase());
    }

    /** 构建归档执行请求 */
    private ArchiveExecuteRequest buildRequest(Long taskId, String fullPath, String rcloneConfigName,
                                               ArchiveAnalyzeResult analyzed, ArchiveTmdbItem tmdb, String processMethod) {
        ArchiveExecuteRequest req = new ArchiveExecuteRequest();
        req.setBatchTaskId(taskId);
        req.setOriginalPath(fullPath);
        req.setRcloneConfigName(rcloneConfigName);

        // 新文件名
        String newFilename = analyzed.getSuggestedFilename();
        if (newFilename == null || newFilename.trim().isEmpty()) {
            // 退化到原始文件名
            newFilename = fullPath.contains("/")
                    ? fullPath.substring(fullPath.lastIndexOf('/') + 1)
                    : fullPath;
        }
        req.setNewFilename(newFilename);

        req.setCategory(tmdb.getSuggestedCategory());
        req.setDirName(tmdb.getSuggestedDirName());

        // 季目录
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
}
