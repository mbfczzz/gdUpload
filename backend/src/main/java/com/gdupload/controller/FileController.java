package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.entity.FileInfo;
import com.gdupload.service.IFileInfoService;
import com.gdupload.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文件管理控制器
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final IFileInfoService fileInfoService;

    @PostMapping("/scan")
    public Result<Map<String, Object>> scanDirectory(@RequestBody Map<String, Object> params) {
        String directoryPath = (String) params.get("directoryPath");
        Boolean recursive = (Boolean) params.getOrDefault("recursive", false);
        Integer limit = (Integer) params.getOrDefault("limit", 1000); // 默认最多返回1000个文件

        List<FileInfo> allFiles = fileInfoService.scanDirectory(directoryPath, recursive);

        // 构建返回结果
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("totalCount", allFiles.size());
        result.put("limit", limit);

        // 如果文件数量超过限制，只返回前N个
        if (allFiles.size() > limit) {
            result.put("files", allFiles.subList(0, limit));
            result.put("hasMore", true);
            result.put("message", String.format("扫描到 %d 个文件，仅显示前 %d 个。建议使用更具体的路径或关闭递归扫描。",
                allFiles.size(), limit));
        } else {
            result.put("files", allFiles);
            result.put("hasMore", false);
        }

        // 计算总大小
        long totalSize = allFiles.stream().mapToLong(FileInfo::getFileSize).sum();
        result.put("totalSize", totalSize);

        return Result.success(result);
    }

    @PostMapping("/batch-save")
    public Result<Void> batchSaveFiles(@RequestBody Map<String, Object> params) {
        Long taskId = Long.valueOf(params.get("taskId").toString());
        List<Map<String, Object>> fileListMaps = (List<Map<String, Object>>) params.get("fileList");

        // 转换 Map 为 FileInfo 对象
        List<FileInfo> fileList = fileListMaps.stream().map(map -> {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileName((String) map.get("fileName"));
            fileInfo.setFilePath((String) map.get("filePath"));

            // 处理 fileSize，可能是 Integer 或 Long
            Object sizeObj = map.get("fileSize");
            if (sizeObj instanceof Integer) {
                fileInfo.setFileSize(((Integer) sizeObj).longValue());
            } else if (sizeObj instanceof Long) {
                fileInfo.setFileSize((Long) sizeObj);
            } else if (sizeObj != null) {
                fileInfo.setFileSize(Long.valueOf(sizeObj.toString()));
            }

            fileInfo.setStatus(0); // 待上传
            fileInfo.setCreateTime(DateTimeUtil.now());
            return fileInfo;
        }).collect(java.util.stream.Collectors.toList());

        boolean success = fileInfoService.batchSaveFiles(taskId, fileList);

        return success ? Result.success("保存成功") : Result.error("保存失败");
    }

    @GetMapping("/task/{taskId}")
    public Result<List<FileInfo>> getTaskFiles(@PathVariable Long taskId) {
        List<FileInfo> files = fileInfoService.getTaskFiles(taskId);
        return Result.success(files);
    }

    @GetMapping("/task/{taskId}/pending")
    public Result<List<FileInfo>> getPendingFiles(@PathVariable Long taskId) {
        List<FileInfo> files = fileInfoService.getPendingFiles(taskId);
        return Result.success(files);
    }

    @GetMapping("/task/{taskId}/failed")
    public Result<List<FileInfo>> getFailedFiles(@PathVariable Long taskId) {
        List<FileInfo> files = fileInfoService.getFailedFiles(taskId);
        return Result.success(files);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateFileStatus(
            @PathVariable Long id,
            @RequestParam Integer status,
            @RequestParam(required = false) String errorMessage) {
        boolean success = fileInfoService.updateFileStatus(id, status, errorMessage);
        return success ? Result.success("更新成功") : Result.error("更新失败");
    }

    @GetMapping("/check-exists")
    public Result<Boolean> checkFileExists(
            @RequestParam String filePath,
            @RequestParam(required = false) String md5) {
        boolean exists = fileInfoService.checkFileExists(filePath, md5);
        return Result.success(exists);
    }
}
