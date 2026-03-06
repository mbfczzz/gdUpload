package com.gdupload.controller;

import com.gdupload.common.BusinessException;
import com.gdupload.common.Result;
import com.gdupload.dto.GdFileItem;
import com.gdupload.dto.PagedResult;
import com.gdupload.entity.GdAccount;
import com.gdupload.service.IBatchFormatRenameService;
import com.gdupload.service.IGdAccountService;
import com.gdupload.service.IGdFileManagerService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * GD文件管理控制器
 */
@RestController
@RequestMapping("/gd-file")
@RequiredArgsConstructor
public class GdFileManagerController {

    private final IGdFileManagerService     gdFileManagerService;
    private final IGdAccountService         accountService;
    private final IBatchFormatRenameService batchFormatRenameService;

    /**
     * 列出目录内容（服务端分页）
     */
    @GetMapping("/list")
    public Result<PagedResult<GdFileItem>> list(
            @RequestParam Long accountId,
            @RequestParam(defaultValue = "") String path,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size) {
        GdAccount account = getValidAccount(accountId);
        PagedResult<GdFileItem> result = gdFileManagerService.listFiles(account.getRcloneConfigName(), path, page, size);
        return Result.success(result);
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/file")
    public Result<Void> deleteFile(@RequestBody DeleteFileRequest req) {
        GdAccount account = getValidAccount(req.getAccountId());
        gdFileManagerService.deleteFile(account.getRcloneConfigName(), req.getFilePath());
        return Result.success();
    }

    /**
     * 删除目录
     */
    @DeleteMapping("/dir")
    public Result<Void> deleteDir(@RequestBody DeleteDirRequest req) {
        GdAccount account = getValidAccount(req.getAccountId());
        gdFileManagerService.deleteDirectory(account.getRcloneConfigName(), req.getDirPath());
        return Result.success();
    }

    /**
     * 移动/重命名
     */
    @PutMapping("/move")
    public Result<Void> move(@RequestBody MoveRequest req) {
        GdAccount account = getValidAccount(req.getAccountId());
        gdFileManagerService.moveItem(account.getRcloneConfigName(), req.getOldPath(), req.getNewPath(), Boolean.TRUE.equals(req.getIsDir()));
        return Result.success();
    }

    // ─── 批量格式化命名 ──────────────────────────────────────────────────────────

    /**
     * 启动批量格式化命名任务（异步）
     */
    @PostMapping("/batch-format/start")
    public Result<Map<String, Object>> startBatchFormat(@RequestBody BatchFormatRequest req) {
        GdAccount account = getValidAccount(req.getAccountId());
        String taskId = batchFormatRenameService.startTask(
                account.getRcloneConfigName(),
                req.getDirPath() != null ? req.getDirPath() : "");
        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("taskId", taskId);
        return Result.success(resp);
    }

    /**
     * 查询批量格式化命名任务进度
     */
    @GetMapping("/batch-format/{taskId}/status")
    public Result<Map<String, Object>> getBatchFormatStatus(@PathVariable String taskId) {
        return Result.success(batchFormatRenameService.getStatus(taskId));
    }

    /**
     * 取消批量格式化命名任务
     */
    @DeleteMapping("/batch-format/{taskId}")
    public Result<Void> cancelBatchFormat(@PathVariable String taskId) {
        batchFormatRenameService.cancelTask(taskId);
        return Result.success();
    }

    /**
     * 创建目录
     */
    @PostMapping("/mkdir")
    public Result<Void> mkdir(@RequestBody MkdirRequest req) {
        GdAccount account = getValidAccount(req.getAccountId());
        gdFileManagerService.makeDirectory(account.getRcloneConfigName(), req.getPath());
        return Result.success();
    }

    // ─── 空文件夹清理 ──────────────────────────────────────────────────────────

    /**
     * 删除单个空目录（递归检查子目录也为空才删除）
     */
    @DeleteMapping("/empty-dir")
    public Result<Map<String, Object>> deleteEmptyDir(@RequestBody DeleteDirRequest req) {
        GdAccount account = getValidAccount(req.getAccountId());
        boolean deleted = gdFileManagerService.deleteEmptyDirectory(account.getRcloneConfigName(), req.getDirPath());
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("deleted", deleted);
        result.put("message", deleted ? "空目录已删除" : "该目录非空，未删除");
        return Result.success(result);
    }

    /**
     * 批量清理当前路径下的所有空文件夹
     */
    @PostMapping("/clean-empty-dirs")
    public Result<Map<String, Object>> cleanEmptyDirs(@RequestBody CleanEmptyRequest req) {
        GdAccount account = getValidAccount(req.getAccountId());
        Map<String, Object> result = gdFileManagerService.cleanEmptyDirectories(
                account.getRcloneConfigName(), req.getBasePath() != null ? req.getBasePath() : "");
        return Result.success(result);
    }

    /**
     * 去重合并：合并同名文件夹、清理重复文件（rclone dedupe）
     * Google Drive 允许同名文件夹并存，此接口会将内容合并到一个文件夹并删除空的重复目录
     */
    @PostMapping("/dedupe")
    public Result<Map<String, Object>> dedupe(@RequestBody DedupeRequest req) {
        GdAccount account = getValidAccount(req.getAccountId());
        Map<String, Object> result = gdFileManagerService.deduplicatePath(
                account.getRcloneConfigName(), req.getPath() != null ? req.getPath() : "");
        return Result.success(result);
    }

    private GdAccount getValidAccount(Long accountId) {
        GdAccount account = accountService.getById(accountId);
        if (account == null) {
            throw new BusinessException("账号不存在: " + accountId);
        }
        return account;
    }

    @Data
    public static class DeleteFileRequest {
        private Long accountId;
        private String filePath;
    }

    @Data
    public static class DeleteDirRequest {
        private Long accountId;
        private String dirPath;
    }

    @Data
    public static class MoveRequest {
        private Long accountId;
        private String oldPath;
        private String newPath;
        private Boolean isDir;
    }

    @Data
    public static class MkdirRequest {
        private Long accountId;
        private String path;
    }

    @Data
    public static class BatchFormatRequest {
        private Long accountId;
        private String dirPath;
    }

    @Data
    public static class CleanEmptyRequest {
        private Long accountId;
        private String basePath;
    }

    @Data
    public static class DedupeRequest {
        private Long accountId;
        private String path;
    }
}
