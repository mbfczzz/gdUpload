package com.gdupload.service.impl;

import com.gdupload.service.ISmartSearchConfigService;
import com.gdupload.service.IStrmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * STRM 一次性生成服务实现
 *
 * 流程：
 *  1. rclone lsf 列出 GD 目录所有视频文件
 *  2. 解析文件名 → TMDB 搜索 → 写 .strm / .nfo / 图片
 *
 * 核心处理逻辑委托给 StrmCoreHelper。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrmServiceImpl implements IStrmService {

    private final StrmCoreHelper          coreHelper;
    private final ISmartSearchConfigService configService;

    // ─── 任务状态（单实例） ───────────────────────────────────────────────────

    private static final class TaskStatus {
        volatile String phase          = "IDLE";
        volatile int    totalFiles     = 0;
        volatile int    processedFiles = 0;
        volatile int    successFiles   = 0;
        volatile int    failedFiles    = 0;
        volatile String currentFile    = "";
        volatile long   startTime      = 0;
        volatile String errorMessage   = "";
        final List<String> logs = Collections.synchronizedList(new ArrayList<>());
    }

    private final TaskStatus status = new TaskStatus();

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("phase",          status.phase);
        r.put("totalFiles",     status.totalFiles);
        r.put("processedFiles", status.processedFiles);
        r.put("successFiles",   status.successFiles);
        r.put("failedFiles",    status.failedFiles);
        r.put("currentFile",    status.currentFile);
        r.put("logs",           new ArrayList<>(status.logs));
        r.put("startTime",      status.startTime);
        r.put("errorMessage",   status.errorMessage);
        return r;
    }

    @Override
    public void startGenerate(String gdRemote, String gdSourcePath) {
        if ("RUNNING".equals(status.phase)) {
            throw new RuntimeException("已有任务正在运行，请等待完成后再启动");
        }

        Map<String, Object> cfg;
        try {
            cfg = configService.getFullConfig("default");
        } catch (Exception e) {
            cfg = Collections.emptyMap();
        }
        final String outputPath  = strOf(cfg.getOrDefault("strmOutputPath", "/strm"));
        final String playUrlBase = strOf(cfg.getOrDefault("strmPlayUrlBase", ""));
        final String tmdbApiKey  = strOf(cfg.getOrDefault("tmdbApiKey",      ""));
        final String language    = strOf(cfg.getOrDefault("tmdbLanguage",    "zh-CN"));

        status.phase          = "RUNNING";
        status.totalFiles     = 0;
        status.processedFiles = 0;
        status.successFiles   = 0;
        status.failedFiles    = 0;
        status.currentFile    = "";
        status.startTime      = System.currentTimeMillis();
        status.errorMessage   = "";
        status.logs.clear();

        Thread t = new Thread(() ->
                doGenerate(gdRemote, gdSourcePath, outputPath, playUrlBase, tmdbApiKey, language));
        t.setDaemon(true);
        t.setName("strm-generator");
        t.start();
    }

    // ─── 主流程 ───────────────────────────────────────────────────────────────

    private void doGenerate(String gdRemote, String gdSourcePath,
                            String outputPath, String playUrlBase,
                            String tmdbApiKey, String language) {
        try {
            addLog("扫描: " + gdRemote + ":" + gdSourcePath);
            List<String> files = coreHelper.listGdFiles(gdRemote, gdSourcePath);
            status.totalFiles = files.size();

            if (files.isEmpty()) {
                status.phase = "DONE";
                addLog("未找到视频文件，任务完成");
                return;
            }
            addLog("找到 " + files.size() + " 个视频文件，开始处理...");

            Map<String, StrmCoreHelper.ShowCache> showCache = new HashMap<>();

            for (String relPath : files) {
                status.currentFile = relPath;
                try {
                    coreHelper.processFileToStrm(gdRemote, gdSourcePath, relPath,
                            outputPath, playUrlBase, tmdbApiKey, language, showCache);
                    addLog("✓ " + relPath);
                    status.successFiles++;
                } catch (Exception e) {
                    log.error("处理文件失败: {}", relPath, e);
                    addLog("✗ 失败: " + relPath + " — " + e.getMessage());
                    status.failedFiles++;
                }
                status.processedFiles++;
            }

            status.phase = "DONE";
            addLog("✓ 全部完成  成功=" + status.successFiles + "  失败=" + status.failedFiles);

        } catch (Exception e) {
            log.error("STRM生成任务异常", e);
            status.phase        = "ERROR";
            status.errorMessage = e.getMessage();
            addLog("✗ 任务异常: " + e.getMessage());
        }
    }

    // ─── 辅助 ─────────────────────────────────────────────────────────────────

    private void addLog(String msg) {
        if (status.logs.size() >= 1000) status.logs.remove(0);
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        status.logs.add("[" + time + "] " + msg);
        log.info("[STRM] {}", msg);
    }

    private String strOf(Object o) {
        return o == null ? "" : o.toString();
    }
}
