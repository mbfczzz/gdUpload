package com.gdupload.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.common.PageResult;
import com.gdupload.common.Result;
import com.gdupload.entity.FileInfo;
import com.gdupload.entity.UploadTask;
import com.gdupload.service.IFileInfoService;
import com.gdupload.service.ISystemLogService;
import com.gdupload.service.IUploadService;
import com.gdupload.service.IUploadTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 上传任务管理控制器
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class UploadTaskController {

    private final IUploadTaskService uploadTaskService;
    private final IFileInfoService fileInfoService;
    private final IUploadService uploadService;
    private final ISystemLogService systemLogService;

    @GetMapping("/page")
    public Result<PageResult<UploadTask>> page(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        Page<UploadTask> page = new Page<>(current, size);
        Page<UploadTask> result = uploadTaskService.pageTasks(page, keyword, status);
        return Result.success(PageResult.of(result));
    }

    @GetMapping("/{id}")
    public Result<UploadTask> getById(@PathVariable Long id) {
        UploadTask task = uploadTaskService.getTaskDetail(id);
        return Result.success(task);
    }

    @GetMapping("/{id}/files")
    public Result<List<FileInfo>> getTaskFiles(@PathVariable Long id) {
        List<FileInfo> files = fileInfoService.getTaskFiles(id);
        return Result.success(files);
    }

    @PostMapping
    public Result<Long> create(@RequestBody Map<String, Object> params) {
        String taskName = (String) params.get("taskName");
        String sourcePath = (String) params.get("sourcePath");
        String targetPath = (String) params.get("targetPath");
        Boolean uploadAll = (Boolean) params.get("uploadAll");
        Boolean recursive = (Boolean) params.get("recursive");
        List<String> fileList = (List<String>) params.get("fileList");

        Long taskId;

        // 如果是上传全部文件，重新扫描目录
        if (uploadAll != null && uploadAll) {
            // 扫描目录获取所有文件
            List<FileInfo> allFiles = fileInfoService.scanDirectory(sourcePath, recursive != null && recursive);
            fileList = allFiles.stream().map(FileInfo::getFilePath).collect(java.util.stream.Collectors.toList());

            // 创建任务
            taskId = uploadTaskService.createTask(taskName, sourcePath, targetPath, fileList);

            // 保存文件信息
            if (taskId != null) {
                fileInfoService.batchSaveFiles(taskId, allFiles);
            }
        } else {
            // 正常创建任务（文件信息由前端单独保存）
            taskId = uploadTaskService.createTask(taskName, sourcePath, targetPath, fileList);
        }

        if (taskId != null) {
            return Result.success("创建成功", taskId);
        } else {
            return Result.error("创建失败");
        }
    }

    @PutMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        boolean success = uploadTaskService.startTask(id);

        if (success) {
            // 异步执行任务
            uploadService.executeTask(id);
            return Result.success("任务已启动");
        } else {
            return Result.error("启动失败");
        }
    }

    @PutMapping("/{id}/pause")
    public Result<Void> pause(@PathVariable Long id) {
        uploadService.stopTask(id);
        return Result.success("任务已暂停");
    }

    @PutMapping("/{id}/resume")
    public Result<Void> resume(@PathVariable Long id) {
        boolean success = uploadTaskService.resumeTask(id);

        if (success) {
            uploadService.executeTask(id);
            return Result.success("任务已恢复");
        } else {
            return Result.error("恢复失败");
        }
    }

    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        uploadService.stopTask(id);
        boolean success = uploadTaskService.cancelTask(id);
        return success ? Result.success("任务已取消") : Result.error("取消失败");
    }

    @PutMapping("/{id}/retry")
    public Result<Void> retry(@PathVariable Long id) {
        boolean success = uploadTaskService.retryTask(id);

        if (success) {
            uploadService.executeTask(id);
            return Result.success("任务已重试");
        } else {
            return Result.error("重试失败");
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // Get task details before deletion for logging
        UploadTask task = uploadTaskService.getTaskDetail(id);

        boolean success = uploadTaskService.removeById(id);

        if (success && task != null) {
            // Log task deletion
            systemLogService.logTaskOperation(id, task.getTaskName(), "TASK_DELETE",
                String.format("删除任务 - 状态: %d, 已上传: %d/%d, 进度: %d%%",
                    task.getStatus(), task.getUploadedCount(), task.getTotalCount(), task.getProgress()));
        }

        return success ? Result.success("删除成功") : Result.error("删除失败");
    }

    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        // Get task details before deletion for logging
        List<UploadTask> tasks = uploadTaskService.listByIds(ids);

        boolean success = uploadTaskService.removeByIds(ids);

        if (success && tasks != null && !tasks.isEmpty()) {
            // Log each task deletion
            for (UploadTask task : tasks) {
                systemLogService.logTaskOperation(task.getId(), task.getTaskName(), "TASK_DELETE",
                    String.format("批量删除任务 - 状态: %d, 已上传: %d/%d, 进度: %d%%",
                        task.getStatus(), task.getUploadedCount(), task.getTotalCount(), task.getProgress()));
            }
        }

        return success ? Result.success("删除成功") : Result.error("删除失败");
    }

    @GetMapping("/running")
    public Result<List<UploadTask>> getRunningTasks() {
        List<UploadTask> tasks = uploadTaskService.getRunningTasks();
        return Result.success(tasks);
    }

    @DeleteMapping("/clean")
    public Result<Long> cleanExpiredTasks(@RequestParam(defaultValue = "30") Integer days) {
        long count = uploadTaskService.cleanExpiredTasks(days);

        // Log cleanup operation
        if (count > 0) {
            systemLogService.log(1, "INFO", "TASK_OPERATION", "TASK_CLEAN",
                String.format("清理过期任务 - 清理数量: %d, 保留天数: %d", count, days),
                String.format("清理了 %d 天前的已完成、已取消和失败任务", days));
        }

        return Result.success("清理完成", count);
    }
}
