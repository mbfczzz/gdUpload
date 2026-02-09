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
import com.gdupload.service.IEmbyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class UploadTaskController {

    private final IUploadTaskService uploadTaskService;
    private final IFileInfoService fileInfoService;
    private final IUploadService uploadService;
    private final ISystemLogService systemLogService;
    private final IEmbyService embyService;

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
        UploadTask task = uploadTaskService.getTaskDetail(id);
        if (task != null && task.getTaskType() != null && task.getTaskType() == 3) {
            // Emby下载任务：重新启动（会自动跳过已下载的文件）
            try {
                // 获取任务的文件列表（itemIds）
                List<FileInfo> fileList = fileInfoService.getTaskFiles(id);
                List<String> itemIds = fileList.stream()
                    .map(FileInfo::getFilePath)  // filePath存储的是embyItemId
                    .collect(java.util.stream.Collectors.toList());

                // 重新启动下载任务（会自动跳过已下载的文件）
                embyService.batchDownloadToServerAsync(itemIds, id);
                return Result.success("下载任务已重新启动（会自动跳过已下载的文件）");
            } catch (Exception e) {
                log.error("重新启动Emby下载任务失败: taskId={}", id, e);
                return Result.error("启动失败: " + e.getMessage());
            }
        }
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
        UploadTask task = uploadTaskService.getTaskDetail(id);
        if (task != null && task.getTaskType() != null && task.getTaskType() == 3) {
            // Emby下载任务
            boolean success = embyService.pauseDownloadTask(id);
            return success ? Result.success("任务已暂停") : Result.error("暂停失败");
        }
        uploadService.stopTask(id);
        return Result.success("任务已暂停");
    }

    @PutMapping("/{id}/resume")
    public Result<Void> resume(@PathVariable Long id) {
        UploadTask task = uploadTaskService.getTaskDetail(id);
        if (task != null && task.getTaskType() != null && task.getTaskType() == 3) {
            // Emby下载任务：支持断点续传
            try {
                // 从FileInfo表中获取所有文件的itemId（存储在filePath字段）
                List<FileInfo> fileInfoList = fileInfoService.getTaskFiles(id);
                List<String> itemIds = fileInfoList.stream()
                    .map(FileInfo::getFilePath)
                    .collect(java.util.stream.Collectors.toList());

                // 调用断点续传方法（传入existingTaskId）
                embyService.batchDownloadToServerAsync(itemIds, id);
                return Result.success("Emby下载任务已恢复（会自动跳过已下载的文件）");
            } catch (Exception e) {
                log.error("恢复Emby下载任务失败: taskId={}", id, e);
                return Result.error("恢复失败: " + e.getMessage());
            }
        }
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
        UploadTask task = uploadTaskService.getTaskDetail(id);
        if (task != null && task.getTaskType() != null && task.getTaskType() == 3) {
            // Emby下载任务
            boolean success = embyService.cancelDownloadTask(id);
            return success ? Result.success("任务已取消") : Result.error("取消失败");
        }
        uploadService.stopTask(id);
        boolean success = uploadTaskService.cancelTask(id);
        return success ? Result.success("任务已取消") : Result.error("取消失败");
    }

    @PutMapping("/{id}/retry")
    public Result<Void> retry(@PathVariable Long id) {
        UploadTask task = uploadTaskService.getTaskDetail(id);
        if (task != null && task.getTaskType() != null && task.getTaskType() == 3) {
            return Result.error("Emby下载任务不支持重试，请重新创建下载任务");
        }
        boolean success = uploadTaskService.retryTask(id);

        if (success) {
            uploadService.executeTask(id);
            return Result.success("任务已重试");
        } else {
            return Result.error("重试失败");
        }
    }

    @PutMapping("/{id}/target-path")
    public Result<Void> updateTargetPath(@PathVariable Long id, @RequestBody Map<String, String> params) {
        String targetPath = params.get("targetPath");
        if (targetPath == null || targetPath.trim().isEmpty()) {
            return Result.error("目标路径不能为空");
        }

        UploadTask task = uploadTaskService.getTaskDetail(id);
        if (task == null) {
            return Result.error("任务不存在");
        }

        // 检查任务状态：只允许修改待开始、已暂停、失败的任务
        if (task.getStatus() != 0 && task.getStatus() != 3 && task.getStatus() != 5) {
            return Result.error("只能修改待开始、已暂停或失败状态的任务路径");
        }

        boolean success = uploadTaskService.updateTaskTargetPath(id, targetPath);
        return success ? Result.success("目标路径已更新") : Result.error("更新失败");
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
