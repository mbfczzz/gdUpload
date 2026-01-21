package com.gdupload.service.impl;

import com.gdupload.entity.FileInfo;
import com.gdupload.entity.GdAccount;
import com.gdupload.entity.UploadTask;
import com.gdupload.service.*;
import com.gdupload.util.RcloneUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 上传核心服务实现
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements IUploadService {

    private final IUploadTaskService uploadTaskService;
    private final IFileInfoService fileInfoService;
    private final IGdAccountService gdAccountService;
    private final RcloneUtil rcloneUtil;
    private final IWebSocketService webSocketService;
    private final ISystemLogService systemLogService;

    // 存储正在运行的任务
    private final Map<Long, Boolean> runningTasks = new ConcurrentHashMap<>();

    @Override
    @Async("taskExecutor")
    public void executeTask(Long taskId) {
        if (runningTasks.containsKey(taskId)) {
            log.warn("任务已在运行中: taskId={}", taskId);
            return;
        }

        runningTasks.put(taskId, true);

        try {
            log.info("开始执行上传任务: taskId={}", taskId);

            UploadTask task = uploadTaskService.getTaskDetail(taskId);
            if (task == null) {
                log.error("任务不存在: taskId={}", taskId);
                return;
            }

            // 记录任务启动日志
            systemLogService.logTaskOperation(taskId, task.getTaskName(), "TASK_START",
                String.format("任务开始执行 - 源路径: %s, 目标路径: %s", task.getSourcePath(), task.getTargetPath()));

            // 获取待上传的文件列表
            List<FileInfo> pendingFiles = fileInfoService.getPendingFiles(taskId);

            if (pendingFiles.isEmpty()) {
                log.info("没有待上传的文件: taskId={}", taskId);
                systemLogService.logTaskOperation(taskId, task.getTaskName(), "TASK_COMPLETE",
                    "任务完成 - 没有待上传的文件");
                uploadTaskService.updateTaskStatus(taskId, 2, null); // 已完成
                return;
            }

            // 计算总大小（包括待上传和已上传的文件）
            List<FileInfo> allFiles = fileInfoService.getTaskFiles(taskId);
            long totalSize = allFiles.stream().mapToLong(FileInfo::getFileSize).sum();
            task.setTotalSize(totalSize);

            // 基于实际已上传的文件重新计算uploadedCount和uploadedSize
            List<FileInfo> uploadedFiles = allFiles.stream()
                .filter(f -> f.getStatus() == 2) // 已上传
                .collect(Collectors.toList());
            int uploadedCount = uploadedFiles.size();
            long uploadedSize = uploadedFiles.stream().mapToLong(FileInfo::getFileSize).sum();

            // 计算已处理的文件数（包括成功和失败）
            List<FileInfo> processedFiles = allFiles.stream()
                .filter(f -> f.getStatus() == 2 || f.getStatus() == 3) // 已上传或失败
                .collect(Collectors.toList());
            int processedCount = processedFiles.size();

            // 更新任务的初始状态
            task.setUploadedCount(uploadedCount);
            task.setUploadedSize(uploadedSize);
            uploadTaskService.updateById(task);

            // 逐个上传文件
            for (FileInfo fileInfo : pendingFiles) {
                // 检查任务是否被暂停或取消
                task = uploadTaskService.getTaskDetail(taskId);
                if (task.getStatus() == 3) {
                    log.info("任务被暂停: taskId={}", taskId);
                    systemLogService.logTaskOperation(taskId, task.getTaskName(), "TASK_PAUSE",
                        String.format("任务已暂停 - 已上传 %d/%d 个文件", uploadedCount, task.getTotalCount()));
                    break;
                }
                if (task.getStatus() == 4) {
                    log.info("任务被取消: taskId={}", taskId);
                    systemLogService.logTaskOperation(taskId, task.getTaskName(), "TASK_CANCEL",
                        String.format("任务已取消 - 已上传 %d/%d 个文件", uploadedCount, task.getTotalCount()));
                    break;
                }

                // 获取最佳可用账号
                GdAccount account = gdAccountService.getBestAvailableAccount(fileInfo.getFileSize());

                if (account == null) {
                    log.warn("没有账号有足够配额上传文件: taskId={}, fileId={}, fileName={}, fileSize={}",
                        taskId, fileInfo.getId(), fileInfo.getFileName(), fileInfo.getFileSize());

                    String errorMsg = "所有账号配额不足，需要 " + formatSize(fileInfo.getFileSize()) + "，最大剩余配额不足";

                    // 标记文件为失败，但不中断任务
                    fileInfoService.updateFileStatus(fileInfo.getId(), 3, errorMsg);

                    // 更新已处理文件数和进度
                    processedCount++;
                    int progress = (int) ((processedCount * 100.0) / task.getTotalCount());
                    uploadTaskService.updateTaskProgress(taskId, uploadedCount, uploadedSize, progress);

                    // 推送进度更新到WebSocket
                    webSocketService.pushTaskProgress(taskId, progress, uploadedCount, task.getTotalCount(),
                        uploadedSize, task.getTotalSize(), fileInfo.getFileName());

                    // 推送文件失败状态
                    webSocketService.pushFileStatus(taskId, fileInfo.getId(), fileInfo.getFileName(), 3, errorMsg);

                    // 记录文件上传失败日志
                    systemLogService.logFileUpload(taskId, fileInfo.getId(), fileInfo.getFileName(),
                        fileInfo.getFileSize(), null, "FILE_UPLOAD_FAILED", errorMsg, "配额不足");

                    // 继续处理下一个文件
                    continue;
                }

                // 上传文件
                boolean success = uploadFile(taskId, fileInfo.getId(), account.getId());

                // 无论成功还是失败，都计入已处理的文件
                processedCount++;

                if (success) {
                    uploadedCount++;
                    uploadedSize += fileInfo.getFileSize();

                    // 更新任务进度（进度 = 已处理文件数 / 总文件数）
                    int progress = (int) ((processedCount * 100.0) / task.getTotalCount());
                    uploadTaskService.updateTaskProgress(taskId, uploadedCount, uploadedSize, progress);

                    // 推送进度更新到WebSocket
                    webSocketService.pushTaskProgress(taskId, progress, uploadedCount, task.getTotalCount(),
                        uploadedSize, task.getTotalSize(), fileInfo.getFileName());

                    // 推送文件状态
                    webSocketService.pushFileStatus(taskId, fileInfo.getId(), fileInfo.getFileName(), 2, "上传成功");

                    // 记录文件上传成功日志
                    systemLogService.logFileUpload(taskId, fileInfo.getId(), fileInfo.getFileName(),
                        fileInfo.getFileSize(), account.getId(), "FILE_UPLOAD_SUCCESS",
                        String.format("文件上传成功 - 使用账号: %s, 进度: %d%%", account.getAccountName(), progress),
                        String.format("文件大小: %s, 已处理: %d/%d", formatSize(fileInfo.getFileSize()), processedCount, task.getTotalCount()));

                    log.info("文件上传成功: taskId={}, fileId={}, fileName={}, progress={}%",
                        taskId, fileInfo.getId(), fileInfo.getFileName(), progress);
                } else {
                    // 失败也要更新进度
                    int progress = (int) ((processedCount * 100.0) / task.getTotalCount());
                    uploadTaskService.updateTaskProgress(taskId, uploadedCount, uploadedSize, progress);

                    // 推送进度更新到WebSocket（失败也算处理完成）
                    webSocketService.pushTaskProgress(taskId, progress, uploadedCount, task.getTotalCount(),
                        uploadedSize, task.getTotalSize(), fileInfo.getFileName());

                    // 推送文件失败状态
                    webSocketService.pushFileStatus(taskId, fileInfo.getId(), fileInfo.getFileName(), 3, "上传失败");

                    // 记录文件上传失败日志
                    systemLogService.logFileUpload(taskId, fileInfo.getId(), fileInfo.getFileName(),
                        fileInfo.getFileSize(), account.getId(), "FILE_UPLOAD_FAILED",
                        String.format("文件上传失败 - 使用账号: %s, 进度: %d%%", account.getAccountName(), progress),
                        String.format("已处理: %d/%d", processedCount, task.getTotalCount()));

                    log.error("文件上传失败: taskId={}, fileId={}, fileName={}",
                        taskId, fileInfo.getId(), fileInfo.getFileName());
                }
            }

            // 检查任务是否完成
            task = uploadTaskService.getTaskDetail(taskId);
            List<FileInfo> failedFiles = fileInfoService.getFailedFiles(taskId);
            int successCount = task.getUploadedCount();
            int failedCount = failedFiles.size();
            int processedTotal = successCount + failedCount;

            // 更新任务的失败文件数
            task.setFailedCount(failedCount);
            uploadTaskService.updateById(task);

            // 判断任务是否完成：所有文件都已处理（成功或失败）
            if (processedTotal >= task.getTotalCount()) {
                // 所有文件都已处理，标记任务为已完成
                String statusMsg;
                if (failedCount > 0) {
                    statusMsg = String.format("任务完成 - 成功: %d, 失败: %d", successCount, failedCount);
                } else {
                    statusMsg = String.format("任务完成 - 全部成功: %d", successCount);
                }

                uploadTaskService.updateTaskStatus(taskId, 2, failedCount > 0 ? String.format("有 %d 个文件上传失败", failedCount) : null);

                // 推送任务状态
                webSocketService.pushTaskStatus(taskId, 2, statusMsg);

                // 记录任务完成日志
                systemLogService.logTaskOperation(taskId, task.getTaskName(), "TASK_COMPLETE",
                    String.format("任务执行完成 - 成功: %d, 失败: %d, 总计: %d, 总大小: %s",
                        successCount, failedCount, task.getTotalCount(), formatSize(task.getUploadedSize())));

                log.info("任务执行完成: taskId={}, successCount={}, failedCount={}, totalCount={}",
                    taskId, successCount, failedCount, task.getTotalCount());
            } else {
                // 还有文件待上传，保持上传中状态
                log.info("任务仍在进行中: taskId={}, processed={}/{}, success={}, failed={}",
                    taskId, processedTotal, task.getTotalCount(), successCount, failedCount);
            }

        } catch (Exception e) {
            log.error("任务执行异常: taskId={}", taskId, e);
            uploadTaskService.updateTaskStatus(taskId, 5, e.getMessage());

            // 记录任务异常日志
            UploadTask task = uploadTaskService.getTaskDetail(taskId);
            if (task != null) {
                systemLogService.logTaskOperation(taskId, task.getTaskName(), "TASK_ERROR",
                    "任务执行异常: " + e.getMessage());
            }
        } finally {
            runningTasks.remove(taskId);
        }
    }

    @Override
    public boolean uploadFile(Long taskId, Long fileId, Long accountId) {
        try {
            FileInfo fileInfo = fileInfoService.getById(fileId);
            GdAccount account = gdAccountService.getById(accountId);
            UploadTask task = uploadTaskService.getTaskDetail(taskId);

            if (fileInfo == null || account == null || task == null) {
                log.error("文件、账号或任务不存在: fileId={}, accountId={}, taskId={}",
                    fileId, accountId, taskId);
                return false;
            }

            // 处理文件名中的特殊符号
            String originalFilePath = fileInfo.getFilePath();
            String sanitizedFilePath = sanitizeFilePathForUpload(originalFilePath);

            // 如果文件名被修改了，需要重命名文件
            if (!originalFilePath.equals(sanitizedFilePath)) {
                try {
                    Path originalPath = Paths.get(originalFilePath);
                    Path sanitizedPath = Paths.get(sanitizedFilePath);

                    if (Files.exists(originalPath)) {
                        Files.move(originalPath, sanitizedPath, StandardCopyOption.REPLACE_EXISTING);
                        log.info("文件名已清理: {} -> {}", originalFilePath, sanitizedFilePath);

                        // 更新数据库中的文件路径和文件名
                        fileInfo.setFilePath(sanitizedFilePath);
                        fileInfo.setFileName(sanitizedPath.getFileName().toString());
                        fileInfoService.updateById(fileInfo);
                    }
                } catch (IOException e) {
                    log.error("重命名文件失败: {}", originalFilePath, e);
                    fileInfoService.updateFileStatus(fileId, 3, "文件名包含特殊符号且重命名失败: " + e.getMessage());
                    return false;
                }
            }

            // 更新文件状态为上传中
            fileInfoService.updateFileStatus(fileId, 1, null);

            // 构建目标路径（rclone copy 的目标应该是目录，不包含文件名）
            String remotePath = task.getTargetPath();
            if (!remotePath.endsWith("/")) {
                remotePath += "/";
            }

            // 使用rclone上传文件
            boolean success = rcloneUtil.uploadFile(
                fileInfo.getFilePath(),           // sourcePath
                account.getRcloneConfigName(),    // remoteName
                remotePath,                       // targetPath (目录路径，rclone会自动保留源文件名)
                line -> log.debug("上传进度: {}", line)  // logConsumer
            );

            if (success) {
                // 标记文件为已上传
                fileInfoService.markFileAsUploaded(fileId, accountId);

                // 更新账号配额
                gdAccountService.updateAccountQuota(accountId, fileInfo.getFileSize());

                return true;
            } else {
                // 标记文件为失败
                fileInfoService.updateFileStatus(fileId, 3, "上传失败");
                return false;
            }

        } catch (Exception e) {
            log.error("上传文件异常: fileId={}, accountId={}", fileId, accountId, e);
            fileInfoService.updateFileStatus(fileId, 3, e.getMessage());
            return false;
        }
    }

    @Override
    public void stopTask(Long taskId) {
        runningTasks.remove(taskId);
        uploadTaskService.pauseTask(taskId);
        log.info("停止任务: taskId={}", taskId);
    }

    @Override
    public boolean isTaskRunning(Long taskId) {
        return runningTasks.containsKey(taskId);
    }

    /**
     * 格式化文件大小
     */
    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final long k = 1024;
        final String[] sizes = {"B", "KB", "MB", "GB", "TB"};
        int i = (int) (Math.log(bytes) / Math.log(k));
        // 防止数组越界
        if (i >= sizes.length) {
            i = sizes.length - 1;
        }
        return String.format("%.2f %s", bytes / Math.pow(k, i), sizes[i]);
    }

    /**
     * 清理文件路径中的特殊符号，用于rclone上传
     * 移除或替换可能导致rclone上传失败的特殊字符
     */
    private String sanitizeFilePathForUpload(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return filePath;
        }

        Path path = Paths.get(filePath);
        Path parent = path.getParent();
        String fileName = path.getFileName().toString();

        // 清理文件名中的特殊符号
        // 保留扩展名，只清理文件名部分
        int lastDotIndex = fileName.lastIndexOf('.');
        String nameWithoutExt;
        String extension;

        if (lastDotIndex > 0) {
            nameWithoutExt = fileName.substring(0, lastDotIndex);
            extension = fileName.substring(lastDotIndex);
        } else {
            nameWithoutExt = fileName;
            extension = "";
        }

        // 替换特殊符号为下划线或空格
        // 移除: # % & { } \ < > * ? / $ ! ' " : @ + ` | =
        nameWithoutExt = nameWithoutExt
            .replaceAll("[#%&{}\\\\<>*?/$!'\":@+`|=\\[\\]]", "_")  // 替换特殊符号为下划线
            .replaceAll("_+", "_")  // 多个连续下划线替换为单个
            .replaceAll("^_|_$", "");  // 移除开头和结尾的下划线

        // 如果清理后文件名为空，使用默认名称
        if (nameWithoutExt.isEmpty()) {
            nameWithoutExt = "file";
        }

        String sanitizedFileName = nameWithoutExt + extension;

        if (parent != null) {
            return parent.resolve(sanitizedFileName).toString();
        } else {
            return sanitizedFileName;
        }
    }
}
