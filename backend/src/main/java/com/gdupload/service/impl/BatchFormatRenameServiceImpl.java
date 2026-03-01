package com.gdupload.service.impl;

import com.gdupload.dto.ArchiveAnalyzeResult;
import com.gdupload.dto.GdFileItem;
import com.gdupload.dto.MediaInfoDto;
import com.gdupload.dto.PagedResult;
import com.gdupload.service.IArchiveService;
import com.gdupload.service.IBatchFormatRenameService;
import com.gdupload.service.IGdFileManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final IArchiveService    archiveService;
    private final IGdFileManagerService gdFileManagerService;

    /** 支持的媒体文件扩展名 */
    private static final Set<String> MEDIA_EXTS = new HashSet<>(Arrays.asList(
            "mkv", "mp4", "avi", "mov", "ts", "m2ts", "wmv", "flv", "rmvb", "m4v", "webm"
    ));

    /** 任务表（内存，无需持久化） */
    private final Map<String, TaskStatus> taskMap = new ConcurrentHashMap<>();

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
        return r;
    }

    @Override
    public void cancelTask(String taskId) {
        TaskStatus s = taskMap.get(taskId);
        if (s != null) s.cancelled = true;
    }

    // ─── 核心逻辑 ───────────────────────────────────────────────────────────────

    private void runTask(String rcloneConfigName, String dirPath, TaskStatus status) {
        status.addLog("开始扫描目录: " + (dirPath.isEmpty() ? "根目录" : dirPath));

        // 1. 收集所有媒体文件（递归遍历子目录）
        List<GdFileItem> mediaFiles = collectMediaFiles(rcloneConfigName, dirPath, status);
        status.total.set(mediaFiles.size());
        status.addLog("发现 " + mediaFiles.size() + " 个媒体文件");

        if (mediaFiles.isEmpty()) {
            status.phase = "DONE";
            status.addLog("无需处理，任务结束");
            return;
        }

        // 2. 逐文件处理
        for (GdFileItem item : mediaFiles) {
            if (status.cancelled) {
                status.addLog("任务已取消");
                break;
            }

            String fileName = item.getName();
            String filePath = item.getPath(); // rclone 返回的相对路径（相对于 dirPath 的父目录）
            status.currentFile = fileName;

            try {
                processFile(rcloneConfigName, filePath, fileName, status);
            } catch (Exception e) {
                log.warn("[BatchFormatRename] 处理失败: {}, err={}", fileName, e.getMessage());
                status.failed.incrementAndGet();
                status.addLog("✗ 失败: " + fileName + " — " + e.getMessage());
            } finally {
                status.processed.incrementAndGet();
            }
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

        // 2. 已有视频编码 → 跳过
        if (analyzed.getVideoCodec() != null && !analyzed.getVideoCodec().isEmpty()) {
            status.skipped.incrementAndGet();
            return;
        }

        // 3. ffprobe 探测编码（via rclone cat 管道）
        MediaInfoDto mediaInfo = archiveService.getMediaInfo(filePath, rcloneConfigName);
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

    // ─── 辅助：收集媒体文件 ────────────────────────────────────────────────────

    private List<GdFileItem> collectMediaFiles(String rcloneConfigName, String dirPath, TaskStatus status) {
        List<GdFileItem> result = new ArrayList<>();
        collectRecursive(rcloneConfigName, dirPath, result, status);
        return result;
    }

    private void collectRecursive(String rcloneConfigName, String dirPath,
                                   List<GdFileItem> result, TaskStatus status) {
        if (status.cancelled) return;
        int page = 1;
        final int PAGE_SIZE = 200;
        List<String> subDirPaths = new ArrayList<>();
        while (true) {
            if (status.cancelled) return;
            PagedResult<GdFileItem> paged;
            try {
                paged = gdFileManagerService.listFiles(rcloneConfigName, dirPath, page, PAGE_SIZE);
            } catch (Exception e) {
                log.warn("[BatchFormatRename] 跳过无法访问的目录: {}, err={}", dirPath, e.getMessage());
                return;
            }
            List<GdFileItem> items = paged.getItems();
            if (items == null || items.isEmpty()) break;

            for (GdFileItem item : items) {
                if (Boolean.TRUE.equals(item.getIsDir())) {
                    subDirPaths.add(item.getPath());
                } else if (isMediaFile(item.getName())) {
                    result.add(item);
                }
            }

            if (items.size() < PAGE_SIZE) break;
            page++;
        }
        for (String subPath : subDirPaths) {
            if (status.cancelled) return;
            status.addLog("扫描子目录: " + subPath);
            collectRecursive(rcloneConfigName, subPath, result, status);
        }
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
