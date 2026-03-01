package com.gdupload.service.impl;

import com.gdupload.service.ILocalRenameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 本地文件扁平化重命名服务
 * 规则：将子目录中的文件重命名为「父文件夹名-原文件名」并移动到源路径根目录
 * 例：/backdata/emby/冰雪尖刀连 (2023)/雷大国王大春接连牺牲.mp4
 *   → /backdata/emby/冰雪尖刀连 (2023)-雷大国王大春接连牺牲.mp4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalRenameServiceImpl implements ILocalRenameService {

    private static final Set<String> MEDIA_EXTS = new HashSet<>(Arrays.asList(
            "mkv", "mp4", "avi", "mov", "ts", "m2ts", "wmv", "flv", "rmvb", "m4v", "webm"
    ));

    private final Map<String, TaskStatus> taskMap = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("local-rename-" + t.getId());
        return t;
    });

    @Override
    public String startTask(String dirPath) {
        String taskId = UUID.randomUUID().toString();
        TaskStatus status = new TaskStatus();
        taskMap.put(taskId, status);

        executor.submit(() -> {
            try {
                runTask(dirPath, status);
            } catch (Exception e) {
                log.error("[LocalRename] 任务异常", e);
                status.phase = "ERROR";
                status.errorMessage = e.getMessage();
            }
        });

        return taskId;
    }

    @Override
    public Map<String, Object> getStatus(String taskId) {
        TaskStatus s = taskMap.get(taskId);
        if (s == null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("phase", "NOT_FOUND");
            return r;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("phase",        s.phase);
        r.put("total",        s.total.get());
        r.put("processed",    s.processed.get());
        r.put("renamed",      s.renamed.get());
        r.put("skipped",      s.skipped.get());
        r.put("failed",       s.failed.get());
        r.put("currentFile",  s.currentFile);
        r.put("logs",         new ArrayList<>(s.logs));
        r.put("errorMessage", s.errorMessage);
        return r;
    }

    @Override
    public void cancelTask(String taskId) {
        TaskStatus s = taskMap.get(taskId);
        if (s != null) s.cancelled = true;
    }

    // ─── 核心逻辑 ───────────────────────────────────────────────────────────────

    private void runTask(String dirPath, TaskStatus status) {
        status.addLog("开始扫描: " + dirPath);

        Path sourceRoot = Paths.get(dirPath);
        if (!Files.exists(sourceRoot) || !Files.isDirectory(sourceRoot)) {
            status.phase = "ERROR";
            status.errorMessage = "目录不存在: " + dirPath;
            return;
        }

        // 收集所有子目录中的媒体文件
        List<Path> mediaFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> isMediaFile(p.getFileName().toString()))
                .forEach(mediaFiles::add);
        } catch (Exception e) {
            status.phase = "ERROR";
            status.errorMessage = "扫描失败: " + e.getMessage();
            return;
        }

        status.total.set(mediaFiles.size());
        status.addLog("发现 " + mediaFiles.size() + " 个媒体文件");

        if (mediaFiles.isEmpty()) {
            status.phase = "DONE";
            status.addLog("无需处理");
            return;
        }

        for (Path file : mediaFiles) {
            if (status.cancelled) {
                status.addLog("任务已取消");
                break;
            }
            status.currentFile = file.getFileName().toString();
            try {
                processFile(file, sourceRoot, status);
            } catch (Exception e) {
                log.warn("[LocalRename] 处理失败: {} err={}", file.getFileName(), e.getMessage());
                status.failed.incrementAndGet();
                status.addLog("✗ " + file.getFileName() + " — " + e.getMessage());
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
     * 处理单个文件：原目录内重命名为「父目录名-原文件名」
     */
    private void processFile(Path file, Path sourceRoot, TaskStatus status) throws Exception {
        String originalName = file.getFileName().toString();
        String parentName   = file.getParent().getFileName().toString();

        // 已在根目录，或文件名已有父目录名前缀 → 跳过
        if (file.getParent().equals(sourceRoot) || originalName.startsWith(parentName + "-")) {
            status.skipped.incrementAndGet();
            return;
        }

        String newFilename = parentName + "-" + originalName;
        Path   newPath     = file.getParent().resolve(newFilename);

        // 目标已存在则跳过
        if (Files.exists(newPath)) {
            status.skipped.incrementAndGet();
            status.addLog("跳过（目标已存在）: " + newFilename);
            return;
        }

        Files.move(file, newPath);
        status.renamed.incrementAndGet();
        status.addLog("✓ " + originalName + "\n   → " + newFilename);
        log.info("[LocalRename] {} → {}", file, newPath);
    }

    private boolean isMediaFile(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return MEDIA_EXTS.contains(name.substring(dot + 1).toLowerCase());
    }

    // ─── 内部状态 ─────────────────────────────────────────────────────────────

    static class TaskStatus {
        volatile String     phase        = "RUNNING";
        volatile String     currentFile  = "";
        volatile String     errorMessage = "";
        volatile boolean    cancelled    = false;
        final AtomicInteger total        = new AtomicInteger();
        final AtomicInteger processed    = new AtomicInteger();
        final AtomicInteger renamed      = new AtomicInteger();
        final AtomicInteger skipped      = new AtomicInteger();
        final AtomicInteger failed       = new AtomicInteger();
        final List<String>  logs = Collections.synchronizedList(new LinkedList<>());

        void addLog(String msg) {
            if (logs.size() >= 500) logs.remove(0);
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logs.add("[" + time + "] " + msg);
        }
    }
}
