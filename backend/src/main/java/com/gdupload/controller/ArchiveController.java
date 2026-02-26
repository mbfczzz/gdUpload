package com.gdupload.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.common.Result;
import com.gdupload.dto.ArchiveAnalyzeResult;
import com.gdupload.dto.ArchiveExecuteRequest;
import com.gdupload.dto.ArchiveTmdbItem;
import com.gdupload.entity.ArchiveHistory;
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
}
