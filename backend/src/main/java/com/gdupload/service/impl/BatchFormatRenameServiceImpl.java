package com.gdupload.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdupload.dto.ArchiveAnalyzeResult;
import com.gdupload.dto.GdFileItem;
import com.gdupload.dto.MediaInfoDto;
import com.gdupload.service.IArchiveService;
import com.gdupload.service.IBatchFormatRenameService;
import com.gdupload.service.IGdFileManagerService;
import com.gdupload.util.RcloneUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchFormatRenameServiceImpl implements IBatchFormatRenameService {

    private final IArchiveService       archiveService;
    private final IGdFileManagerService gdFileManagerService;
    private final RcloneUtil            rcloneUtil;
    private final ObjectMapper          objectMapper;

    /** 支持的媒体文件扩展名 */
    private static final Set<String> MEDIA_EXTS = new HashSet<>(Arrays.asList(
            "mkv", "mp4", "avi", "mov", "ts", "m2ts", "wmv", "flv", "rmvb", "m4v", "webm"
    ));

    /** 文件处理并发数（ffprobe via rclone cat 是 I/O 密集型，线程可以远多于 CPU 核数） */
    @Value("${app.format-rename.process-concurrency:32}")
    private int processConcurrency;

    /** 任务表（内存，无需持久化） */
    private final Map<String, TaskStatus> taskMap = new ConcurrentHashMap<>();

    /**
     * 目录级编码缓存：同一目录下的文件编码通常完全相同（同一压制组的季合集）。
     * 对第一个文件做 ffprobe，其余文件直接复用结果，跳过下载。
     * key = 父目录路径，value = [videoCodec, audioCodec, resolution]（null 表示探测失败）
     */
    private static final int DIR_CODEC_CACHE_MAX = 5000;  // 提高缓存容量
    private final Map<String, String[]> dirCodecCache = new java.util.concurrent.ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("fmt-rename-" + t.getId());
        return t;
    });

    // ─── 接口实现 ───────────────────────────────────────────────────────────────

    @Override
    public String startTask(String rcloneConfigName, String dirPath) {
        String taskId = UUID.randomUUID().toString();
        TaskStatus status = new TaskStatus();
        taskMap.put(taskId, status);

        executor.submit(() -> {
            try {
                runTask(rcloneConfigName, dirPath, status);
            } catch (Exception e) {
                log.error("[BatchFormatRename] 任务异常", e);
                status.phase        = "ERROR";
                status.errorMessage = e.getMessage();
            }
        });

        return taskId;
    }

    @Override
    public Map<String, Object> getStatus(String taskId) {
        TaskStatus s = taskMap.get(taskId);
        if (s == null) {
            Map<String, Object> notFound = new LinkedHashMap<>();
            notFound.put("phase", "NOT_FOUND");
            return notFound;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("phase",       s.phase);
        r.put("total",       s.total.get());
        r.put("processed",   s.processed.get());
        r.put("renamed",     s.renamed.get());
        r.put("skipped",     s.skipped.get());
        r.put("failed",      s.failed.get());
        r.put("currentFile", s.currentFile);
        r.put("logs",        new ArrayList<>(s.logs));
        r.put("errorMessage",s.errorMessage);
        r.put("paused",      s.paused);
        return r;
    }

    @Override
    public void cancelTask(String taskId) {
        TaskStatus s = taskMap.get(taskId);
        if (s != null) s.cancelled = true;
    }

    @Override
    public void pauseTask(String taskId) {
        TaskStatus s = taskMap.get(taskId);
        if (s != null && "RUNNING".equals(s.phase)) {
            s.paused = true;
            s.phase = "PAUSING";
            s.addLog("暂停请求已发送，等待工作线程结束...");
            log.info("[BatchFormatRename] 暂停任务: taskId={}", taskId);
        }
    }

    @Override
    public void resumeTask(String taskId) {
        TaskStatus s = taskMap.get(taskId);
        // 仅允许在 PAUSED 状态恢复，PAUSING 时工作线程可能还在运行
        if (s != null && "PAUSED".equals(s.phase)) {
            s.paused = false;
            s.phase = "RUNNING";
            s.addLog("任务已恢复");
            log.info("[BatchFormatRename] 恢复任务: taskId={}", taskId);
        }
    }

    // ─── 核心逻辑 ───────────────────────────────────────────────────────────────

    private void runTask(String rcloneConfigName, String dirPath, TaskStatus status) {
        dirCodecCache.clear(); // 每次新任务清空目录编码缓存
        status.addLog("开始扫描目录: " + (dirPath.isEmpty() ? "根目录" : dirPath));

        // 1. 一次性递归列举所有文件（单条 rclone lsjson --recursive，比逐目录分页快数十倍）
        List<GdFileItem> mediaFiles;
        try {
            String json = rcloneUtil.listJsonRecursive(rcloneConfigName, dirPath);
            List<GdFileItem> allItems = objectMapper.readValue(json, new TypeReference<List<GdFileItem>>() {});
            mediaFiles = allItems.stream()
                    .filter(f -> !Boolean.TRUE.equals(f.getIsDir()))
                    .filter(f -> isMediaFile(f.getName()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[BatchFormatRename] 扫描目录失败", e);
            status.phase = "ERROR";
            status.errorMessage = "扫描目录失败: " + e.getMessage();
            status.addLog("✗ 扫描失败: " + e.getMessage());
            return;
        }

        status.total.set(mediaFiles.size());
        status.addLog("发现 " + mediaFiles.size() + " 个媒体文件");

        if (mediaFiles.isEmpty()) {
            status.phase = "DONE";
            status.addLog("无需处理，任务结束");
            return;
        }

        // 2. 按目录分组（提高缓存命中率：同目录文件连续处理，第1个ffprobe后其余走缓存）
        Map<String, List<GdFileItem>> dirGroups = mediaFiles.stream()
                .collect(Collectors.groupingBy(item -> {
                    String path = item.getPath();
                    return path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
                }, LinkedHashMap::new, Collectors.toList()));
        status.addLog("分组到 " + dirGroups.size() + " 个目录");

        // 3. 并行处理（ffprobe via rclone cat 是主瓶颈，多线程并行大幅提速）
        int concurrency = Math.max(1, processConcurrency);
        status.addLog("并发线程数: " + concurrency);
        ExecutorService filePool = Executors.newFixedThreadPool(concurrency, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("fmt-rename-worker");
            return t;
        });

        // 按目录投递任务（同目录文件顺序提交，利用缓存）
        for (List<GdFileItem> dirFiles : dirGroups.values()) {
            for (GdFileItem item : dirFiles) {
                if (status.cancelled) break;

                // 暂停检查：如果暂停了就等待恢复
                while (status.paused && !status.cancelled) {
                    if ("PAUSING".equals(status.phase) && !status.cancelled) {
                        status.phase = "PAUSED";
                        status.addLog("任务已暂停");
                    }
                    try { Thread.sleep(1000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (status.cancelled) break;

                // listJsonRecursive 返回的 Path 相对于 dirPath，拼接还原完整路径
                final String fullPath = dirPath.isEmpty()
                        ? item.getPath()
                        : dirPath + "/" + item.getPath();
                final String fileName = item.getName();

                filePool.submit(() -> {
                    if (status.cancelled) return;
                    // 暂停时等待恢复（不要直接 return，否则文件会被跳过不处理）
                    while (status.paused && !status.cancelled) {
                        try { Thread.sleep(1000); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt(); return;
                        }
                    }
                    if (status.cancelled) return;
                    status.currentFile = fileName;
                    try {
                        processFile(rcloneConfigName, fullPath, fileName, status);
                    } catch (Exception e) {
                        log.warn("[BatchFormatRename] 处理失败: {}, err={}", fileName, e.getMessage());
                        status.failed.incrementAndGet();
                        status.addLog("✗ 失败: " + fileName + " — " + e.getMessage());
                    } finally {
                        status.processed.incrementAndGet();
                    }
                });
            }
        }

        filePool.shutdown();
        try {
            filePool.awaitTermination(24, TimeUnit.HOURS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        status.currentFile = "";
        status.phase = status.cancelled ? "CANCELLED" : "DONE";
        status.addLog("完成  重命名=" + status.renamed.get()
                + "  跳过=" + status.skipped.get()
                + "  失败=" + status.failed.get());
    }

    /**
     * 处理单个文件：解析文件名 → 判断是否缺编码信息 → ffprobe → 重命名
     *
     * @param rcloneConfigName  rclone 远程配置名
     * @param filePath          文件在远程的完整相对路径（如 dir/sub/file.mkv）
     * @param fileName          仅文件名（不含目录）
     * @param status            任务状态
     */
    private void processFile(String rcloneConfigName, String filePath, String fileName, TaskStatus status) {
        // 1. 正则解析文件名
        ArchiveAnalyzeResult analyzed = archiveService.analyzeFilename(fileName);

        // 2. 判断是否已符合归档命名规范 → 跳过
        // 标准格式：标题 S01E01 1080p.HEVC.AAC-字幕组.mkv
        // 必须有：标题、分辨率、视频编码
        boolean hasTitle = analyzed.getTitle() != null && !analyzed.getTitle().trim().isEmpty();
        boolean hasResolution = analyzed.getResolution() != null && !analyzed.getResolution().trim().isEmpty();
        boolean hasVideoCodec = analyzed.getVideoCodec() != null && !analyzed.getVideoCodec().trim().isEmpty();

        if (hasTitle && hasResolution && hasVideoCodec) {
            // 已符合归档命名规范，跳过 ffprobe
            status.skipped.incrementAndGet();
            return;
        }

        // 3. ffprobe 探测编码（via rclone cat 管道）
        //    优先查目录级缓存：同季目录的集数编码几乎100%相同，命中则跳过下载
        String parentDir = filePath.contains("/")
                ? filePath.substring(0, filePath.lastIndexOf('/')) : "";

        MediaInfoDto mediaInfo;
        String[] cached = dirCodecCache.get(parentDir);
        if (cached != null) {
            // 缓存命中：直接构造 MediaInfoDto，免去 rclone cat + ffprobe
            mediaInfo = new MediaInfoDto();
            mediaInfo.setVideoCodec(cached[0]);
            mediaInfo.setAudioCodec(cached[1]);
            mediaInfo.setResolution(cached[2]);
            log.debug("目录缓存命中: {} → [{}, {}, {}]", parentDir, cached[0], cached[1], cached[2]);
        } else {
            mediaInfo = archiveService.getMediaInfo(filePath, rcloneConfigName);
            if (mediaInfo != null && dirCodecCache.size() < DIR_CODEC_CACHE_MAX) {
                // 写入缓存（允许 null 值字段，表示该目录探测到的部分信息）
                dirCodecCache.put(parentDir, new String[]{
                        mediaInfo.getVideoCodec(),
                        mediaInfo.getAudioCodec(),
                        mediaInfo.getResolution()
                });
            }
        }
        if (mediaInfo == null) {
            status.skipped.incrementAndGet();
            status.addLog("跳过(ffprobe无结果): " + fileName);
            return;
        }

        // 4. 补充字段（只填充空字段）
        boolean updated = false;
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

        if (!updated) {
            status.skipped.incrementAndGet();
            return;
        }

        // 5. 生成新文件名
        String newFilename = buildFilename(analyzed);
        if (newFilename.equals(fileName)) {
            status.skipped.incrementAndGet();
            return;
        }

        // 6. 计算新路径（同目录，仅替换文件名部分）
        String parentPath = filePath.contains("/")
                ? filePath.substring(0, filePath.lastIndexOf('/'))
                : "";
        String newPath = parentPath.isEmpty() ? newFilename : parentPath + "/" + newFilename;

        // 7. rclone 重命名（moveto）
        gdFileManagerService.moveItem(rcloneConfigName, filePath, newPath, false);

        status.renamed.incrementAndGet();
        status.addLog("✓ " + fileName + "\n   → " + newFilename);
        log.info("[BatchFormatRename] 重命名: {} → {}", filePath, newPath);
    }

    private boolean isMediaFile(String name) {
        if (name == null) return false;
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return MEDIA_EXTS.contains(name.substring(dot + 1).toLowerCase());
    }

    // ─── 辅助：构建文件名（与后端 ArchiveServiceImpl.buildFilename 逻辑一致）────

    private String buildFilename(ArchiveAnalyzeResult r) {
        StringBuilder sb = new StringBuilder();
        if (r.getTitle() != null && !r.getTitle().isEmpty()) sb.append(r.getTitle());

        if (r.getSeason() != null && r.getEpisode() != null) {
            sb.append(" S").append(r.getSeason()).append("E").append(r.getEpisode());
        } else if (r.getEpisode() != null) {
            sb.append(" E").append(r.getEpisode());
        } else if ("movie".equals(r.getMediaType()) && r.getYear() != null) {
            sb.append(" (").append(r.getYear()).append(")");
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

    // ─── 内部状态类 ────────────────────────────────────────────────────────────

    static class TaskStatus {
        volatile String       phase        = "RUNNING";
        volatile String       currentFile  = "";
        volatile String       errorMessage = "";
        volatile boolean      cancelled    = false;
        volatile boolean      paused       = false;
        final AtomicInteger   total        = new AtomicInteger();
        final AtomicInteger   processed    = new AtomicInteger();
        final AtomicInteger   renamed      = new AtomicInteger();
        final AtomicInteger   skipped      = new AtomicInteger();
        final AtomicInteger   failed       = new AtomicInteger();
        // LinkedList: 头部 remove O(1)
        final List<String>    logs = Collections.synchronizedList(new LinkedList<>());

        void addLog(String msg) {
            if (logs.size() >= 500) logs.remove(0);
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logs.add("[" + time + "] " + msg);
        }
    }
}
