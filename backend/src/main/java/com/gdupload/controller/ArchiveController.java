package com.gdupload.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.common.Result;
import com.gdupload.dto.ArchiveAnalyzeResult;
import com.gdupload.dto.ArchiveExecuteRequest;
import com.gdupload.dto.ArchiveTmdbItem;
import com.gdupload.dto.MediaInfoDto;
import com.gdupload.entity.ArchiveBatchTask;
import com.gdupload.entity.ArchiveHistory;
import com.gdupload.service.IBatchArchiveService;
import com.gdupload.service.IArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 归档功能 Controller
 * 流程：正则解析文件名 → TMDB搜索 → AI识别（可选）→ 执行归档 / 标记人工
 */
@RestController
@RequestMapping("/archive")
@RequiredArgsConstructor
public class ArchiveController {

    private final IArchiveService archiveService;
    private final IBatchArchiveService batchArchiveService;

    /**
     * 正则解析文件名
     */
    @GetMapping("/analyze")
    public Result<ArchiveAnalyzeResult> analyzeFilename(@RequestParam String filename) {
        return Result.success(archiveService.analyzeFilename(filename));
    }

    /**
     * AI 解析文件名（正则失败时的回退方案）
     */
    @PostMapping("/ai-analyze")
    public Result<ArchiveAnalyzeResult> aiAnalyzeFilename(@RequestBody Map<String, String> body) {
        String filename = body.get("filename");
        if (filename == null || filename.isEmpty()) {
            return Result.error("filename 不能为空");
        }
        return Result.success(archiveService.aiAnalyzeFilename(filename));
    }

    /**
     * TMDB 搜索（返回最多8条候选结果）
     */
    @GetMapping("/tmdb-search")
    public Result<List<ArchiveTmdbItem>> searchTmdb(
            @RequestParam String title,
            @RequestParam(required = false) String year,
            @RequestParam(required = false, defaultValue = "tv") String type) {
        return Result.success(archiveService.searchTmdb(title, year, type));
    }

    /**
     * 通过 TMDB ID 直接查详情（文件名中已含 tmdbid 时使用，免去搜索步骤）
     */
    @GetMapping("/tmdb-detail")
    public Result<ArchiveTmdbItem> fetchTmdbDetail(@RequestParam Integer tmdbId) {
        if (tmdbId == null || tmdbId <= 0) {
            return Result.error("tmdbId 无效");
        }
        ArchiveTmdbItem item = archiveService.fetchTmdbDetail(tmdbId);
        return item != null ? Result.success(item) : Result.error("TMDB ID " + tmdbId + " 未找到");
    }

    /**
     * 执行归档（重命名 + 移动 + 记录历史）
     */
    @PostMapping("/execute")
    public Result<Map<String, Object>> executeArchive(@RequestBody ArchiveExecuteRequest req) {
        if (req.getOriginalPath() == null || req.getOriginalPath().isEmpty()) {
            return Result.error("originalPath 不能为空");
        }
        if (req.getNewFilename() == null || req.getNewFilename().isEmpty()) {
            return Result.error("newFilename 不能为空");
        }
        if (req.getDirName() == null || req.getDirName().isEmpty()) {
            return Result.error("dirName 不能为空（必须包含 tmdbid）");
        }
        return Result.success(archiveService.executeArchive(req));
    }

    /**
     * 标记为需要人工处理
     */
    @PostMapping("/mark-manual")
    public Result<Map<String, Object>> markManual(@RequestBody Map<String, String> body) {
        String originalPath = body.get("originalPath");
        String originalFilename = body.get("originalFilename");
        String remark = body.get("remark");
        return Result.success(archiveService.markManual(originalPath, originalFilename, remark));
    }

    /**
     * 用 ffprobe 探测本地文件的真实媒体技术信息
     * 返回 null 表示文件不存在或 ffprobe 未安装
     */
    @GetMapping("/media-info")
    public Result<MediaInfoDto> getMediaInfo(
            @RequestParam String filePath,
            @RequestParam(required = false) String rcloneConfigName) {
        return Result.success(archiveService.getMediaInfo(filePath, rcloneConfigName));
    }

    /**
     * 检查 ffprobe / rclone 是否可用
     */
    @GetMapping("/check-tools")
    public Result<Map<String, Object>> checkTools() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("ffprobe", runVersionCheck("ffprobe", "-version"));
        result.put("rclone",  runVersionCheck("rclone",  "version"));
        return Result.success(result);
    }

    private Map<String, Object> runVersionCheck(String tool, String arg) {
        Map<String, Object> info = new java.util.LinkedHashMap<>();
        try {
            Process p = new ProcessBuilder(tool, arg)
                    .redirectErrorStream(true)
                    .start();
            String firstLine;
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                firstLine = br.readLine();
            }
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            info.put("available", true);
            info.put("version", firstLine != null ? firstLine.trim() : "");
        } catch (Exception e) {
            info.put("available", false);
            info.put("error", e.getMessage());
        }
        return info;
    }

    /**
     * 获取所有分类列表
     */
    @GetMapping("/categories")
    public Result<List<String>> getCategories() {
        return Result.success(archiveService.getCategories());
    }

    /**
     * 分页查询归档历史
     *
     * @param status 可选过滤：success / failed / manual_required
     */
    @GetMapping("/history")
    public Result<IPage<ArchiveHistory>> getHistory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String status) {
        return Result.success(archiveService.getHistory(new Page<>(page, size), status));
    }

    /**
     * 更新人工处理备注
     */
    @PutMapping("/history/{id}/remark")
    public Result<String> updateRemark(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        archiveService.updateRemark(id, body.get("remark"));
        return Result.success("备注已更新");
    }

    /**
     * 批量给文件添加 TMDB ID 标记（重命名文件，追加 [tmdbid=xxx]）
     * 请求体：{ "historyIds": [1,2,3], "tmdbId": 12345 }
     */
    @PostMapping("/batch-add-tmdb-tag")
    public Result<Map<String, Object>> batchAddTmdbTag(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> rawIds = (List<Number>) body.get("historyIds");
        if (rawIds == null || rawIds.isEmpty()) {
            return Result.error("historyIds 不能为空");
        }
        Number tmdbIdNum = (Number) body.get("tmdbId");
        if (tmdbIdNum == null || tmdbIdNum.intValue() <= 0) {
            return Result.error("tmdbId 无效");
        }
        List<Long> historyIds = new java.util.ArrayList<>();
        for (Number n : rawIds) historyIds.add(n.longValue());
        return Result.success(archiveService.batchAddTmdbTag(historyIds, tmdbIdNum.intValue()));
    }

    // ─── 批量归档任务 ──────────────────────────────────────────────────────────

    /**
     * 启动批量归档任务
     * 请求体：{ "accountId": 1, "sourcePath": "movies/2024" }
     */
    @PostMapping("/batch/start")
    public Result<ArchiveBatchTask> startBatch(@RequestBody Map<String, Object> body) {
        Long accountId = Long.valueOf(body.get("accountId").toString());
        String sourcePath = (String) body.get("sourcePath");
        return Result.success(batchArchiveService.startBatchTask(accountId, sourcePath));
    }

    /**
     * 分页查询批量任务列表
     */
    @GetMapping("/batch/list")
    public Result<IPage<ArchiveBatchTask>> listBatchTasks(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(batchArchiveService.listTasks(new Page<>(page, size)));
    }

    /**
     * 查询单个批量任务（含最新进度）
     */
    @GetMapping("/batch/{id}")
    public Result<ArchiveBatchTask> getBatchTask(@PathVariable Long id) {
        return Result.success(batchArchiveService.getTask(id));
    }

    /**
     * 查询批量任务下的归档历史
     */
    @GetMapping("/batch/{id}/history")
    public Result<IPage<ArchiveHistory>> getBatchTaskHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size,
            @RequestParam(required = false) String status) {
        return Result.success(batchArchiveService.getTaskHistory(id, new Page<>(page, size), status));
    }

    /**
     * 取消批量任务
     */
    @DeleteMapping("/batch/{id}")
    public Result<String> cancelBatchTask(@PathVariable Long id) {
        batchArchiveService.cancelTask(id);
        return Result.success("任务已取消");
    }

    /**
     * 暂停批量任务（RUNNING → PAUSED，下一个文件处理前生效）
     */
    @PostMapping("/batch/{id}/pause")
    public Result<String> pauseBatchTask(@PathVariable Long id) {
        batchArchiveService.pauseTask(id);
        return Result.success("任务已暂停");
    }

    /**
     * 恢复已暂停的批量任务（PAUSED → RUNNING）
     */
    @PostMapping("/batch/{id}/resume")
    public Result<String> resumeBatchTask(@PathVariable Long id) {
        batchArchiveService.resumeTask(id);
        return Result.success("任务已恢复");
    }
}
