package com.gdupload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdupload.dto.ArchiveAnalyzeResult;
import com.gdupload.dto.GdFileItem;
import com.gdupload.dto.MediaInfoDto;
import com.gdupload.entity.FormatRenameHistory;
import com.gdupload.entity.FormatRenameTask;
import com.gdupload.entity.GdAccount;
import com.gdupload.mapper.FormatRenameHistoryMapper;
import com.gdupload.mapper.FormatRenameTaskMapper;
import com.gdupload.service.IArchiveService;
import com.gdupload.service.IFormatRenameService;
import com.gdupload.service.IGdAccountService;
import com.gdupload.service.IGdFileManagerService;
import com.gdupload.util.RcloneUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormatRenameServiceImpl implements IFormatRenameService {

    private final FormatRenameTaskMapper  taskMapper;
    private final FormatRenameHistoryMapper historyMapper;
    private final IGdAccountService       accountService;
    private final IGdFileManagerService   fileManagerService;
    private final IArchiveService         archiveService;
    private final RcloneUtil              rcloneUtil;
    private final ObjectMapper            objectMapper;

    private static final Set<String> MEDIA_EXTS = new HashSet<>(Arrays.asList(
            "mkv", "mp4", "avi", "mov", "ts", "m2ts", "wmv", "flv", "rmvb", "m4v", "webm"
    ));

    private static final Pattern TMDB_ID_PATTERN =
            Pattern.compile("\\[tmdbid=(\\d+)\\]", Pattern.CASE_INSENSITIVE);

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("fmt-rename-task-" + t.getId());
        return t;
    });

    // ── 接口实现 ──────────────────────────────────────────────────────────────────

    @Override
    public FormatRenameTask startTask(Long accountId, String dirPath) {
        GdAccount account = accountService.getById(accountId);
        if (account == null) throw new IllegalArgumentException("账号不存在: " + accountId);

        String folderName = (dirPath == null || dirPath.isEmpty()) ? "根目录"
                : dirPath.substring(dirPath.lastIndexOf('/') + 1);
        String taskName = "格式化命名_" + folderName + "_"
                + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

        FormatRenameTask task = new FormatRenameTask();
        task.setTaskName(taskName);
        task.setAccountId(accountId);
        task.setRcloneConfigName(account.getRcloneConfigName());
        task.setDirPath(dirPath != null ? dirPath : "");
        task.setStatus("PENDING");
        task.setTotalFiles(0);
        task.setProcessedFiles(0);
        task.setRenamedCount(0);
        task.setSkippedCount(0);
        task.setFailedCount(0);
        taskMapper.insert(task);

        final Long taskId            = task.getId();
        final String rcloneConfigName = account.getRcloneConfigName();
        final String safeDir          = task.getDirPath();

        executor.submit(() -> {
            try {
                processTask(taskId, rcloneConfigName, safeDir);
            } catch (Exception e) {
                log.error("[FormatRename] 任务异常 taskId={}", taskId, e);
                FormatRenameTask err = new FormatRenameTask();
                err.setId(taskId);
                err.setStatus("FAILED");
                err.setErrorMessage(e.getMessage());
                taskMapper.updateById(err);
            }
        });

        return task;
    }

    @Override
    public IPage<FormatRenameTask> listTasks(Page<FormatRenameTask> page) {
        return taskMapper.selectPage(page,
                new LambdaQueryWrapper<FormatRenameTask>()
                        .orderByDesc(FormatRenameTask::getCreateTime));
    }

    @Override
    public FormatRenameTask getTask(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    @Override
    public IPage<FormatRenameHistory> getTaskHistory(Long taskId,
                                                      Page<FormatRenameHistory> page,
                                                      String status) {
        LambdaQueryWrapper<FormatRenameHistory> wrapper =
                new LambdaQueryWrapper<FormatRenameHistory>()
                        .eq(FormatRenameHistory::getTaskId, taskId)
                        .orderByDesc(FormatRenameHistory::getCreateTime);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(FormatRenameHistory::getStatus, status);
        }
        return historyMapper.selectPage(page, wrapper);
    }

    @Override
    public void cancelTask(Long taskId) {
        FormatRenameTask upd = new FormatRenameTask();
        upd.setId(taskId);
        upd.setStatus("FAILED");
        upd.setCurrentFile("");
        taskMapper.updateById(upd);
    }

    @Override
    public void pauseTask(Long taskId) {
        FormatRenameTask task = taskMapper.selectById(taskId);
        if (task == null || !"RUNNING".equals(task.getStatus())) return;
        FormatRenameTask upd = new FormatRenameTask();
        upd.setId(taskId);
        upd.setStatus("PAUSED");
        taskMapper.updateById(upd);
    }

    @Override
    public void resumeTask(Long taskId) {
        FormatRenameTask task = taskMapper.selectById(taskId);
        if (task == null || !"PAUSED".equals(task.getStatus())) return;
        FormatRenameTask upd = new FormatRenameTask();
        upd.setId(taskId);
        upd.setStatus("RUNNING");
        taskMapper.updateById(upd);
    }

    // ── 核心执行逻辑 ─────────────────────────────────────────────────────────────

    private void processTask(Long taskId, String rcloneConfigName, String dirPath) {
        // 切换为 RUNNING
        setStatus(taskId, "RUNNING");

        // 1. 递归收集媒体文件（单次 rclone lsjson --recursive 调用，避免逐目录调用时
        //    因路径含 [ ] 被 rclone 当 glob 解析而报"directory not found"）
        List<GdFileItem> mediaFiles = collectAllMediaFiles(rcloneConfigName, dirPath);

        // 检查是否已被取消
        if (isCancelled(taskId)) return;

        // 2. 更新总数
        FormatRenameTask upd = new FormatRenameTask();
        upd.setId(taskId);
        upd.setTotalFiles(mediaFiles.size());
        taskMapper.updateById(upd);

        int processed = 0, renamed = 0, skipped = 0, failed = 0;

        // 3. 逐文件处理
        for (GdFileItem file : mediaFiles) {
            // 暂停 / 取消检查
            while (true) {
                FormatRenameTask snap = taskMapper.selectById(taskId);
                if (snap == null || "FAILED".equals(snap.getStatus())) return;
                if ("PAUSED".equals(snap.getStatus())) {
                    try { Thread.sleep(2000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); return;
                    }
                    continue;
                }
                break; // RUNNING
            }

            // 更新当前文件
            FormatRenameTask cur = new FormatRenameTask();
            cur.setId(taskId);
            cur.setCurrentFile(file.getName());
            taskMapper.updateById(cur);

            // 处理文件
            String result = processFile(taskId, rcloneConfigName, file);
            processed++;
            if ("renamed".equals(result)) renamed++;
            else if ("failed".equals(result)) failed++;
            else skipped++;

            // 更新进度
            FormatRenameTask prog = new FormatRenameTask();
            prog.setId(taskId);
            prog.setProcessedFiles(processed);
            prog.setRenamedCount(renamed);
            prog.setSkippedCount(skipped);
            prog.setFailedCount(failed);
            taskMapper.updateById(prog);
        }

        // 4. 标记完成
        FormatRenameTask done = new FormatRenameTask();
        done.setId(taskId);
        done.setStatus("COMPLETED");
        done.setCurrentFile("");
        taskMapper.updateById(done);
        log.info("[FormatRename] 任务完成 taskId={} renamed={} skipped={} failed={}",
                taskId, renamed, skipped, failed);
    }

    /**
     * 处理单个文件：解析 → TMDB补充剧集信息 → ffprobe编码 → 重命名
     */
    private String processFile(Long taskId, String rcloneConfigName, GdFileItem item) {
        String fileName = item.getName();
        String filePath = item.getPath();

        FormatRenameHistory history = new FormatRenameHistory();
        history.setTaskId(taskId);
        history.setOriginalFilename(fileName);
        history.setFilePath(filePath);

        try {
            // 1. 正则解析文件名
            ArchiveAnalyzeResult analyzed = archiveService.analyzeFilename(fileName);

            // 2. 从父目录路径提取 TMDB ID（如 锦密令-2025-[tmdbid=304513]/...）
            Integer tmdbId = extractTmdbIdFromPath(filePath);

            // 3. 判断是否需要通过 TMDB 补充剧集信息
            boolean isTvEpisode = "tv".equals(analyzed.getMediaType()) && analyzed.getEpisode() != null;
            boolean needsSeasonSupplement = isTvEpisode && analyzed.getSeason() == null;
            // 有 TMDB ID 且（缺少季号 或 标题为空）时才调用 TMDB
            boolean needsTmdbSupplement = tmdbId != null && isTvEpisode
                    && (needsSeasonSupplement
                            || analyzed.getTitle() == null || analyzed.getTitle().isEmpty());

            // 4. TMDB 补充
            if (needsTmdbSupplement) {
                int season = analyzed.getSeason() != null ? Integer.parseInt(analyzed.getSeason()) : 1;
                int epNum  = Integer.parseInt(analyzed.getEpisode());
                Map<String, String> epInfo = archiveService.fetchTvEpisodeInfo(tmdbId, season, epNum);
                if (epInfo != null) {
                    if (needsSeasonSupplement && epInfo.get("season") != null) {
                        analyzed.setSeason(epInfo.get("season"));
                    }
                    if (epInfo.get("showTitle") != null) {
                        analyzed.setTitle(epInfo.get("showTitle"));
                    }
                    analyzed.setEpisodeTitle(epInfo.get("episodeTitle"));
                    log.info("[FormatRename] TMDB补充: title={} s={} epTitle={}",
                            analyzed.getTitle(), analyzed.getSeason(), analyzed.getEpisodeTitle());
                }
            }

            // 5. 已有编码且不需要 TMDB 补充 → 跳过
            boolean hasCodec = analyzed.getVideoCodec() != null && !analyzed.getVideoCodec().isEmpty();
            if (hasCodec && !needsTmdbSupplement) {
                history.setStatus("skipped");
                history.setSkipReason("已有视频编码且无需TMDB补充");
                historyMapper.insert(history);
                return "skipped";
            }

            // 6. ffprobe 探测编码（无编码信息时才调用，TMDB补充走到这里也需要编码信息）
            boolean updated = needsTmdbSupplement; // TMDB已补充算有效更新
            boolean ffprobeFailed = false;
            if (!hasCodec) {
                MediaInfoDto mediaInfo = archiveService.getMediaInfo(filePath, rcloneConfigName);
                if (mediaInfo == null) {
                    ffprobeFailed = true;
                    if (!updated) {
                        history.setStatus("skipped");
                        history.setSkipReason("ffprobe 无结果（MP4 无 faststart 或 ffprobe 未安装）");
                        historyMapper.insert(history);
                        return "skipped";
                    }
                } else {
                    if (mediaInfo.getResolution() != null && analyzed.getResolution() == null) {
                        analyzed.setResolution(mediaInfo.getResolution());
                        updated = true;
                    }
                    if (mediaInfo.getVideoCodec() != null) {
                        analyzed.setVideoCodec(mediaInfo.getVideoCodec());
                        updated = true;
                    }
                    if (mediaInfo.getAudioCodec() != null && analyzed.getAudioCodec() == null) {
                        analyzed.setAudioCodec(mediaInfo.getAudioCodec());
                        updated = true;
                    }
                }
            }

            if (!updated) {
                history.setStatus("skipped");
                history.setSkipReason("无需更新");
                historyMapper.insert(history);
                return "skipped";
            }

            // 7. 生成新文件名
            String newFilename = buildFilename(analyzed);
            if (newFilename.equals(fileName)) {
                history.setStatus("skipped");
                history.setSkipReason("文件名无变化");
                historyMapper.insert(history);
                return "skipped";
            }

            // 8. 计算新路径
            // 剧集且有季号 → 移入 Season XX 子目录；rclone moveto 会自动创建中间目录
            String parentPath = filePath.contains("/")
                    ? filePath.substring(0, filePath.lastIndexOf('/'))
                    : "";
            String targetDir = parentPath;
            if ("tv".equals(analyzed.getMediaType()) && analyzed.getSeason() != null) {
                // 去掉前导零，Emby 标准格式是 "Season 1" 而非 "Season 01"
                int seasonNum = Integer.parseInt(analyzed.getSeason());
                String seasonFolder = "Season " + seasonNum;
                // 若当前父目录已是 Season 目录，原地重命名，不再嵌套
                String currentDirName = parentPath.contains("/")
                        ? parentPath.substring(parentPath.lastIndexOf('/') + 1)
                        : parentPath;
                if (!currentDirName.matches("(?i)Season\\s+\\d+")) {
                    targetDir = parentPath.isEmpty() ? seasonFolder : parentPath + "/" + seasonFolder;
                }
            }
            String newPath = targetDir.isEmpty() ? newFilename : targetDir + "/" + newFilename;

            // 9. rclone 重命名
            fileManagerService.moveItem(rcloneConfigName, filePath, newPath, false);

            // 10. 记录备注：说明本次做了什么（方便在历史列表中查看）
            List<String> notes = new ArrayList<>();
            if (needsTmdbSupplement) {
                String tmdbNote = "TMDB补充";
                if (analyzed.getTitle() != null && !analyzed.getTitle().isEmpty()) tmdbNote += ":标题";
                if (analyzed.getSeason() != null) tmdbNote += "+季号";
                if (analyzed.getEpisodeTitle() != null) tmdbNote += "+集标题";
                notes.add(tmdbNote);
            }
            if (!hasCodec && !ffprobeFailed && analyzed.getVideoCodec() != null) {
                String codecNote = "ffprobe:" + analyzed.getVideoCodec();
                if (analyzed.getResolution() != null) codecNote += " " + analyzed.getResolution();
                notes.add(codecNote);
            }
            if (ffprobeFailed) notes.add("ffprobe失败(无编码信息)");
            if (!targetDir.equals(parentPath)) notes.add("→ " + targetDir.substring(targetDir.lastIndexOf('/') + 1));

            history.setNewFilename(newFilename);
            history.setSkipReason(notes.isEmpty() ? null : String.join(" | ", notes));
            history.setStatus("renamed");
            historyMapper.insert(history);
            log.info("[FormatRename] 重命名: {} → {}", filePath, newPath);
            return "renamed";

        } catch (Exception e) {
            log.warn("[FormatRename] 处理失败: {} err={}", fileName, e.getMessage());
            history.setStatus("failed");
            history.setFailReason(e.getMessage());
            historyMapper.insert(history);
            return "failed";
        }
    }

    /** 从文件路径各段中提取 [tmdbid=xxx] */
    private Integer extractTmdbIdFromPath(String filePath) {
        if (filePath == null) return null;
        for (String part : filePath.replace('\\', '/').split("/")) {
            Matcher m = TMDB_ID_PATTERN.matcher(part);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return null;
    }

    // ── 递归收集媒体文件 ──────────────────────────────────────────────────────────

    /**
     * 使用 rclone lsjson --recursive 一次性列出所有文件，过滤出媒体文件并拼接完整路径。
     * 相比逐目录调用 listFiles，能正确处理目录名包含 [ ] 的情况（rclone 内部用 Drive API
     * 遍历，不会把子目录名当 glob 解析）。
     */
    private List<GdFileItem> collectAllMediaFiles(String rcloneConfigName, String dirPath) {
        try {
            String json = rcloneUtil.listJsonRecursive(rcloneConfigName, dirPath);
            List<GdFileItem> allItems = objectMapper.readValue(
                    json, new TypeReference<List<GdFileItem>>() {});
            List<GdFileItem> result = new ArrayList<>();
            for (GdFileItem item : allItems) {
                if (!Boolean.TRUE.equals(item.getIsDir()) && isMediaFile(item.getName())) {
                    // lsjson --recursive 返回的 Path 相对于 dirPath，拼成完整路径
                    String fullPath = (dirPath == null || dirPath.isEmpty())
                            ? item.getPath()
                            : dirPath + "/" + item.getPath();
                    item.setPath(fullPath);
                    result.add(item);
                }
            }
            log.info("[FormatRename] 递归扫描完成: configName={}, dir={}, 媒体文件 {} 个",
                    rcloneConfigName, dirPath, result.size());
            return result;
        } catch (Exception e) {
            log.error("[FormatRename] 递归扫描失败: configName={}, dir={}, err={}",
                    rcloneConfigName, dirPath, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── 辅助 ────────────────────────────────────────────────────────────────────

    private boolean isMediaFile(String name) {
        if (name == null) return false;
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return MEDIA_EXTS.contains(name.substring(dot + 1).toLowerCase());
    }

    private boolean isCancelled(Long taskId) {
        FormatRenameTask snap = taskMapper.selectById(taskId);
        return snap == null || "FAILED".equals(snap.getStatus());
    }

    private void setStatus(Long taskId, String status) {
        FormatRenameTask upd = new FormatRenameTask();
        upd.setId(taskId);
        upd.setStatus(status);
        taskMapper.updateById(upd);
    }

    /** 构建标准化文件名，含集标题（来自TMDB） */
    private String buildFilename(ArchiveAnalyzeResult r) {
        StringBuilder sb = new StringBuilder();
        if (r.getTitle() != null && !r.getTitle().isEmpty()) sb.append(r.getTitle());

        if (r.getSeason() != null && r.getEpisode() != null) {
            // 统一两位补零格式（S01E11），无论来源是 "1" 还是 "01"
            sb.append(String.format(" S%02dE%02d",
                    Integer.parseInt(r.getSeason()), Integer.parseInt(r.getEpisode())));
        } else if (r.getEpisode() != null) {
            sb.append(String.format(" E%02d", Integer.parseInt(r.getEpisode())));
        } else if ("movie".equals(r.getMediaType()) && r.getYear() != null) {
            sb.append(" (").append(r.getYear()).append(")");
        }

        // 集标题（来自 TMDB，置于 S01E04 之后、编码信息之前）
        if (r.getEpisodeTitle() != null && !r.getEpisodeTitle().isEmpty()) {
            sb.append(" ").append(r.getEpisodeTitle());
        }

        if (r.getResolution() != null) sb.append(" ").append(r.getResolution());

        List<String> codecs = new ArrayList<>();
        if (r.getVideoCodec() != null) codecs.add(r.getVideoCodec());
        if (r.getAudioCodec() != null) codecs.add(r.getAudioCodec());
        if (!codecs.isEmpty()) sb.append(".").append(String.join(".", codecs));

        if (r.getSubtitleGroup() != null && !r.getSubtitleGroup().isEmpty()) {
            sb.append("-").append(r.getSubtitleGroup());
        }

        if (r.getExtension() != null) sb.append(".").append(r.getExtension());

        return sb.toString();
    }
}
