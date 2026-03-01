package com.gdupload.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.common.Result;
import com.gdupload.entity.FormatRenameHistory;
import com.gdupload.entity.FormatRenameTask;
import com.gdupload.service.IFormatRenameService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 格式化命名任务控制器
 */
@RestController
@RequestMapping("/format-rename")
@RequiredArgsConstructor
public class FormatRenameController {

    private final IFormatRenameService formatRenameService;

    /** 启动任务 */
    @PostMapping("/start")
    public Result<FormatRenameTask> startTask(@RequestBody StartTaskRequest req) {
        return Result.success(formatRenameService.startTask(req.getAccountId(), req.getDirPath()));
    }

    /** 任务列表（分页） */
    @GetMapping("/list")
    public Result<?> listTasks(
            @RequestParam(defaultValue = "1")  Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(formatRenameService.listTasks(new Page<>(page, size)));
    }

    /** 查询单个任务 */
    @GetMapping("/{id}")
    public Result<FormatRenameTask> getTask(@PathVariable Long id) {
        return Result.success(formatRenameService.getTask(id));
    }

    /** 查询任务文件历史 */
    @GetMapping("/{id}/history")
    public Result<?> getTaskHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1")  Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false)    String status) {
        return Result.success(
                formatRenameService.getTaskHistory(id, new Page<>(page, size), status));
    }

    /** 取消任务 */
    @DeleteMapping("/{id}")
    public Result<Void> cancelTask(@PathVariable Long id) {
        formatRenameService.cancelTask(id);
        return Result.success();
    }

    /** 暂停任务 */
    @PostMapping("/{id}/pause")
    public Result<Void> pauseTask(@PathVariable Long id) {
        formatRenameService.pauseTask(id);
        return Result.success();
    }

    /** 恢复任务 */
    @PostMapping("/{id}/resume")
    public Result<Void> resumeTask(@PathVariable Long id) {
        formatRenameService.resumeTask(id);
        return Result.success();
    }

    @Data
    public static class StartTaskRequest {
        private Long   accountId;
        private String dirPath;
    }
}
