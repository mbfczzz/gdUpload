package com.gdupload.service.impl;

import com.gdupload.dto.UploadResult;
import com.gdupload.entity.FileInfo;
import com.gdupload.entity.GdAccount;
import com.gdupload.entity.UploadRecord;
import com.gdupload.entity.UploadTask;
import com.gdupload.mapper.UploadRecordMapper;
import com.gdupload.service.*;
import com.gdupload.util.DateTimeUtil;
import com.gdupload.util.RcloneResult;
import com.gdupload.util.RcloneUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
    private final UploadRecordMapper uploadRecordMapper;

    // 自注入，用于解决 Spring 事务自调用问题
    @Autowired
    @Lazy
    private IUploadService self;

    // 存储正在运行的任务
    private final Map<Long, Boolean> runningTasks = new ConcurrentHashMap<>();

    // 存储任务的中断标志（用于暂停/取消任务）
    private final Map<Long, AtomicBoolean> taskStopFlags = new ConcurrentHashMap<>();

    // 并发上传线程池（可配置并发数，默认5个并发）
    private final ExecutorService uploadExecutor = Executors.newFixedThreadPool(5, r -> {
        Thread thread = new Thread(r);
        thread.setName("upload-worker-" + thread.getId());
        thread.setDaemon(true);
        return thread;
    });

    @Override
    @Async("taskExecutor")
    public void executeTask(Long taskId) {
        if (runningTasks.containsKey(taskId)) {
            log.warn("任务已在运行中: taskId={}", taskId);
            return;
        }

        runningTasks.put(taskId, true);
        // 创建任务中断标志
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        taskStopFlags.put(taskId, stopFlag);

        try {
            log.info("开始执行上传任务（并发模式）: taskId={}", taskId);

            UploadTask task = uploadTaskService.getTaskDetail(taskId);
            if (task == null) {
                log.error("任务不存在: taskId={}", taskId);
                return;
            }

            // 记录任务启动日志
            systemLogService.logTaskOperation(taskId, task.getTaskName(), "TASK_START",
                String.format("任务开始执行（并发模式） - 源路径: %s, 目标路径: %s", task.getSourcePath(), task.getTargetPath()));

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
            int initialUploadedCount = uploadedFiles.size();
            long initialUploadedSize = uploadedFiles.stream().mapToLong(FileInfo::getFileSize).sum();

            // 计算已处理的文件数（包括成功和失败）
            List<FileInfo> processedFiles = allFiles.stream()
                .filter(f -> f.getStatus() == 2 || f.getStatus() == 3) // 已上传或失败
                .collect(Collectors.toList());
            int initialProcessedCount = processedFiles.size();

            // 更新任务的初始状态
            task.setUploadedCount(initialUploadedCount);
            task.setUploadedSize(initialUploadedSize);
            uploadTaskService.updateById(task);

            // 使用原子计数器确保线程安全
            AtomicInteger uploadedCount = new AtomicInteger(initialUploadedCount);
            AtomicLong uploadedSize = new AtomicLong(initialUploadedSize);
            AtomicInteger processedCount = new AtomicInteger(initialProcessedCount);

            // 使用CountDownLatch等待所有上传任务完成
            CountDownLatch latch = new CountDownLatch(pendingFiles.size());

            // 提交所有上传任务到线程池
            for (FileInfo fileInfo : pendingFiles) {
                // 关键：创建final局部变量，避免lambda闭包问题
                final FileInfo currentFile = fileInfo;
                final UploadTask taskSnapshot = task;

                uploadExecutor.submit(() -> {
                    try {
                        // 检查任务是否被中断
                        if (stopFlag.get()) {
                            log.info("任务已被中断，跳过文件: taskId={}, fileId={}, fileName={}",
                                taskId, currentFile.getId(), currentFile.getFileName());
                            return;
                        }

                        // 获取下一个可用账号（轮询模式，线程安全）
                        GdAccount account = gdAccountService.getNextAvailableAccountInRotation(taskId, currentFile.getFileSize());

                        if (account == null) {
                            log.warn("没有账号有足够配额上传文件: taskId={}, fileId={}, fileName={}, fileSize={}",
                                taskId, currentFile.getId(), currentFile.getFileName(), currentFile.getFileSize());

                            // 标记文件为失败
                            fileInfoService.updateFileStatus(currentFile.getId(), 3, "所有账号配额不足");
                            processedCount.incrementAndGet();

                            // 推送文件失败状态
                            webSocketService.pushFileStatus(taskId, currentFile.getId(), currentFile.getFileName(), 3, "所有账号配额不足");

                            return;
                        }

                        // 上传文件（支持自动切换账号）
                        boolean success = uploadFileWithRetry(taskId, currentFile.getId(), account.getId());

                        // 更新计数器（原子操作）
                        int currentProcessed = processedCount.incrementAndGet();

                        if (success) {
                            int currentUploaded = uploadedCount.incrementAndGet();
                            long currentSize = uploadedSize.addAndGet(currentFile.getFileSize());

                            // 更新任务进度（进度 = 已处理文件数 / 总文件数）
                            int progress = (int) ((currentProcessed * 100.0) / taskSnapshot.getTotalCount());
                            uploadTaskService.updateTaskProgress(taskId, currentUploaded, currentSize, progress);

                            // 推送进度更新到WebSocket
                            webSocketService.pushTaskProgress(taskId, progress, currentUploaded, taskSnapshot.getTotalCount(),
                                currentSize, taskSnapshot.getTotalSize(), currentFile.getFileName());

                            // 推送文件状态
                            webSocketService.pushFileStatus(taskId, currentFile.getId(), currentFile.getFileName(), 2, "上传成功");

                            // 记录文件上传成功日志
                            systemLogService.logFileUpload(taskId, currentFile.getId(), currentFile.getFileName(),
                                currentFile.getFileSize(), account.getId(), "FILE_UPLOAD_SUCCESS",
                                String.format("文件上传成功 - 使用账号: %s, 进度: %d%%", account.getAccountName(), progress),
                                String.format("文件大小: %s, 已处理: %d/%d", formatSize(currentFile.getFileSize()), currentProcessed, taskSnapshot.getTotalCount()));

                            log.info("文件上传成功: taskId={}, fileId={}, fileName={}, progress={}%",
                                taskId, currentFile.getId(), currentFile.getFileName(), progress);
                        } else {
                            // 失败也要更新进度
                            int progress = (int) ((currentProcessed * 100.0) / taskSnapshot.getTotalCount());
                            uploadTaskService.updateTaskProgress(taskId, uploadedCount.get(), uploadedSize.get(), progress);

                            // 推送进度更新到WebSocket（失败也算处理完成）
                            webSocketService.pushTaskProgress(taskId, progress, uploadedCount.get(), taskSnapshot.getTotalCount(),
                                uploadedSize.get(), taskSnapshot.getTotalSize(), currentFile.getFileName());

                            // 推送文件失败状态
                            webSocketService.pushFileStatus(taskId, currentFile.getId(), currentFile.getFileName(), 3, "上传失败");

                            // 记录文件上传失败日志
                            systemLogService.logFileUpload(taskId, currentFile.getId(), currentFile.getFileName(),
                                currentFile.getFileSize(), account.getId(), "FILE_UPLOAD_FAILED",
                                String.format("文件上传失败 - 使用账号: %s, 进度: %d%%", account.getAccountName(), progress),
                                String.format("已处理: %d/%d", currentProcessed, taskSnapshot.getTotalCount()));

                            log.error("文件上传失败: taskId={}, fileId={}, fileName={}",
                                taskId, currentFile.getId(), currentFile.getFileName());
                        }
                    } catch (Exception e) {
                        log.error("并发上传文件异常: taskId={}, fileId={}, fileName={}",
                            taskId, currentFile.getId(), currentFile.getFileName(), e);
                        processedCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // 启动一个监控线程，定期检查任务是否被暂停或取消
            Thread monitorThread = new Thread(() -> {
                try {
                    while (!latch.await(1, TimeUnit.SECONDS)) {
                        // 检查任务状态
                        UploadTask currentTask = uploadTaskService.getTaskDetail(taskId);
                        if (currentTask.getStatus() == 3) {
                            log.info("任务被暂停，设置中断标志: taskId={}", taskId);
                            stopFlag.set(true);
                            systemLogService.logTaskOperation(taskId, currentTask.getTaskName(), "TASK_PAUSE",
                                String.format("任务已暂停 - 已上传 %d/%d 个文件", uploadedCount.get(), currentTask.getTotalCount()));
                            break;
                        }
                        if (currentTask.getStatus() == 4) {
                            log.info("任务被取消，设置中断标志: taskId={}", taskId);
                            stopFlag.set(true);
                            systemLogService.logTaskOperation(taskId, currentTask.getTaskName(), "TASK_CANCEL",
                                String.format("任务已取消 - 已上传 %d/%d 个文件", uploadedCount.get(), currentTask.getTotalCount()));
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    log.warn("监控线程被中断: taskId={}", taskId);
                }
            });
            monitorThread.setName("task-monitor-" + taskId);
            monitorThread.start();

            // 等待所有上传任务完成
            latch.await();

            // 等待监控线程结束
            monitorThread.interrupt();
            monitorThread.join(1000);

            // 检查任务是否被中断
            if (stopFlag.get()) {
                log.info("任务已被中断，不更新最终状态: taskId={}", taskId);
                // 清理任务的轮询索引
                gdAccountService.clearTaskRotationIndex(taskId);
                return;
            }

            // 检查任务是否完成
            task = uploadTaskService.getTaskDetail(taskId);
            List<FileInfo> failedFiles = fileInfoService.getFailedFiles(taskId);
            int successCount = uploadedCount.get();
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

                // 清理任务的轮询索引
                gdAccountService.clearTaskRotationIndex(taskId);

                // 记录任务完成日志
                systemLogService.logTaskOperation(taskId, task.getTaskName(), "TASK_COMPLETE",
                    String.format("任务执行完成（并发模式） - 成功: %d, 失败: %d, 总计: %d, 总大小: %s",
                        successCount, failedCount, task.getTotalCount(), formatSize(uploadedSize.get())));

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
            taskStopFlags.remove(taskId);
        }
    }

    /**
     * 上传文件（支持配额超限时自动切换账号重试）
     *
     * @param taskId 任务ID
     * @param fileId 文件ID
     * @param accountId 初始账号ID
     * @return 上传是否成功
     */
    private boolean uploadFileWithRetry(Long taskId, Long fileId, Long accountId) {
        FileInfo fileInfo = fileInfoService.getById(fileId);
        if (fileInfo == null) {
            log.error("文件不存在: fileId={}", fileId);
            return false;
        }

        // 最多尝试3次（初始账号 + 2次切换）
        int maxRetries = 3;
        Long currentAccountId = accountId;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            log.info("尝试上传文件 (第{}/{}次): taskId={}, fileId={}, fileName={}, accountId={}",
                attempt, maxRetries, taskId, fileId, fileInfo.getFileName(), currentAccountId);

            // 尝试上传
            UploadResult uploadResult = self.uploadFileInternal(taskId, fileId, currentAccountId);

            if (uploadResult.isSuccess()) {
                // 上传成功
                return true;
            }

            if (uploadResult.isQuotaExceeded() || uploadResult.isTimeout()) {
                // 配额超限或超时，尝试切换账号
                String reason = uploadResult.isQuotaExceeded() ? "配额超限" : "超时（可能IP被封）";
                log.warn("账号{}，尝试切换账号: accountId={}, attempt={}/{}", reason, currentAccountId, attempt, maxRetries);

                // 获取下一个可用账号（使用轮询）
                GdAccount nextAccount = gdAccountService.getNextAvailableAccountInRotation(taskId, fileInfo.getFileSize());

                if (nextAccount == null) {
                    // 没有可用账号了
                    log.error("没有更多可用账号，文件上传失败: fileId={}, fileName={}", fileId, fileInfo.getFileName());
                    fileInfoService.updateFileStatus(fileId, 3, "所有账号配额不足");
                    return false;
                }

                // 切换到新账号
                currentAccountId = nextAccount.getId();
                log.info("切换到新账号: accountId={}, accountName={}", currentAccountId, nextAccount.getAccountName());

                // 继续下一次尝试
                continue;
            } else {
                // 其他错误，不重试
                log.error("文件上传失败（非配额问题）: fileId={}, fileName={}, error={}",
                    fileId, fileInfo.getFileName(), uploadResult.getErrorMessage());
                return false;
            }
        }

        // 达到最大重试次数
        log.error("文件上传失败，已达到最大重试次数: fileId={}, fileName={}", fileId, fileInfo.getFileName());
        fileInfoService.updateFileStatus(fileId, 3, "达到最大重试次数");
        return false;
    }

    @Override
    public boolean uploadFile(Long taskId, Long fileId, Long accountId) {
        UploadResult result = self.uploadFileInternal(taskId, fileId, accountId);
        return result.isSuccess();
    }

    /**
     * 内部上传方法，返回详细的上传结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadResult uploadFileInternal(Long taskId, Long fileId, Long accountId) {
        try {
            FileInfo fileInfo = fileInfoService.getById(fileId);
            GdAccount account = gdAccountService.getById(accountId);
            UploadTask task = uploadTaskService.getTaskDetail(taskId);

            if (fileInfo == null || account == null || task == null) {
                log.error("文件、账号或任务不存在: fileId={}, accountId={}, taskId={}",
                    fileId, accountId, taskId);
                return UploadResult.failure("文件、账号或任务不存在");
            }

            // 处理文件名中的特殊符号
            String originalFilePath = fileInfo.getFilePath();
            Path originalPath = Paths.get(originalFilePath);

            // 检查文件名是否包含特殊字符（只检查文件名，不检查目录）
            String fileName = originalPath.getFileName().toString();
            String sanitizedFileName = sanitizeFileName(fileName);

            // 更新文件状态为上传中
            fileInfoService.updateFileStatus(fileId, 1, null);

            // 检查文件是否存在
            Path filePath = Paths.get(originalFilePath);
            if (!Files.exists(filePath)) {
                String errorMsg = "文件不存在: " + originalFilePath;
                log.error(errorMsg);
                fileInfoService.updateFileStatus(fileId, 3, errorMsg);
                return UploadResult.failure(errorMsg);
            }

            // 检查是否是文件（不是目录）
            if (!Files.isRegularFile(filePath)) {
                String errorMsg = "路径不是文件: " + originalFilePath;
                log.error(errorMsg);
                fileInfoService.updateFileStatus(fileId, 3, errorMsg);
                return UploadResult.failure(errorMsg);
            }

            // 构建目标路径（包含相对路径以保留目录结构）
            String remotePath = task.getTargetPath();
            if (!remotePath.endsWith("/")) {
                remotePath += "/";
            }

            // 如果文件有相对路径（包含源目录名+子目录），追加到目标路径中以保留目录结构
            String finalFileName = sanitizedFileName;
            if (fileInfo.getRelativePath() != null && !fileInfo.getRelativePath().isEmpty()) {
                remotePath += fileInfo.getRelativePath() + "/";
                log.info("文件包含相对路径，保留目录结构: {}", remotePath);

                // 从相对路径中提取最后一级目录名（剧集文件夹名），拼接到文件名前
                // 例如：relativePath = "重庆遇见爱 (2024)"，fileName = "第1集.mp4"
                // 结果：finalFileName = "重庆遇见爱 (2024)-第1集.mp4"
                String[] pathParts = fileInfo.getRelativePath().split("[/\\\\]");
                if (pathParts.length > 0) {
                    String folderName = pathParts[pathParts.length - 1]; // 取最后一级目录名
                    if (!folderName.isEmpty()) {
                        // 提取文件名（不含扩展名）和扩展名
                        int lastDotIndex = sanitizedFileName.lastIndexOf('.');
                        if (lastDotIndex > 0) {
                            String nameWithoutExt = sanitizedFileName.substring(0, lastDotIndex);
                            String extension = sanitizedFileName.substring(lastDotIndex);
                            // 拼接：文件夹名-原文件名.扩展名
                            finalFileName = folderName + "-" + nameWithoutExt + extension;
                            log.info("计划重命名文件: {} -> {}", sanitizedFileName, finalFileName);
                        }
                    }
                }
            } else {
                log.warn("文件relativePath为空: fileId={}, fileName={}, filePath={}",
                    fileInfo.getId(), fileInfo.getFileName(), fileInfo.getFilePath());
            }

            log.info("准备上传文件: {} -> {}:{}, 文件大小: {}",
                originalFilePath, account.getRcloneConfigName(), remotePath,
                formatSize(fileInfo.getFileSize()));

            // 使用rclone上传文件到目标目录
            RcloneResult result = rcloneUtil.uploadFile(
                originalFilePath,                 // sourcePath (原始文件路径)
                account.getRcloneConfigName(),    // remoteName
                remotePath,                       // targetPath (目录路径，以/结尾)
                line -> log.debug("上传进度: {}", line)  // logConsumer
            );

            // 如果上传成功且需要重命名
            if (result.isSuccess() && !sanitizedFileName.equals(finalFileName)) {
                log.info("上传成功，开始重命名文件: {} -> {}", sanitizedFileName, finalFileName);
                RcloneResult renameResult = rcloneUtil.renameFile(
                    account.getRcloneConfigName(),
                    remotePath + sanitizedFileName,  // 原文件路径
                    remotePath + finalFileName       // 新文件路径
                );
                if (!renameResult.isSuccess()) {
                    log.error("文件重命名失败: {}", renameResult.getErrorMessage());
                    // 重命名失败不影响上传结果，继续处理
                }
            }

            // 检查是否配额超限
            if (result.isQuotaExceeded()) {
                log.error("========== 检测到配额超限错误 ==========");
                log.error("账号ID: {}, 账号名称: {}, 当前状态: {}",
                    account.getId(), account.getAccountName(), account.getStatus());
                log.error("错误信息: {}", result.getErrorMessage());

                // 记录禁用时间（移除自动解封时间）
                LocalDateTime now = DateTimeUtil.now();
                account.setDisabledTime(now);
                account.setQuotaResetTime(null);  // 不再设置自动解封时间

                // 禁用账号
                Integer oldStatus = account.getStatus();
                account.setStatus(0);

                log.error("准备更新账号状态: {} -> 0 (禁用)", oldStatus);
                log.error("禁用时间: {}",
                    now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                boolean updateResult = gdAccountService.updateById(account);
                log.error("账号状态更新结果: {}, 账号ID: {}", updateResult ? "成功" : "失败", account.getId());

                // 验证更新是否成功
                GdAccount updatedAccount = gdAccountService.getById(account.getId());
                log.error("更新后账号状态: {}",
                    updatedAccount.getStatus());
                log.error("========================================");

                // 记录日志
                systemLogService.logFileUpload(taskId, fileInfo.getId(), fileInfo.getFileName(),
                    fileInfo.getFileSize(), account.getId(), "FILE_UPLOAD_FAILED",
                    String.format("账号配额超限 - 账号: %s 已自动禁用，需手动启用", account.getAccountName()),
                    "User rate limit exceeded");

                // 返回配额超限结果（不更新文件状态，由上层重试逻辑处理）
                String errorMsg = "账号配额超限（已超过750GB），账号已自动禁用，需手动启用";
                return UploadResult.quotaExceeded(errorMsg);
            }

            // 检查是否超时（可能是IP被封或网络问题）
            if (result.isTimeout()) {
                log.error("========== 检测到rclone超时错误 ==========");
                log.error("账号ID: {}, 账号名称: {}, 当前状态: {}",
                    account.getId(), account.getAccountName(), account.getStatus());
                log.error("错误信息: {}", result.getErrorMessage());

                // 记录禁用时间（移除自动解封时间）
                LocalDateTime now = DateTimeUtil.now();
                account.setDisabledTime(now);
                account.setQuotaResetTime(null);  // 不再设置自动解封时间

                // 禁用账号
                Integer oldStatus = account.getStatus();
                account.setStatus(0);

                log.error("准备更新账号状态: {} -> 0 (禁用)", oldStatus);
                log.error("禁用时间: {}",
                    now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                boolean updateResult = gdAccountService.updateById(account);
                log.error("账号状态更新结果: {}, 账号ID: {}", updateResult ? "成功" : "失败", account.getId());

                // 验证更新是否成功
                GdAccount updatedAccount = gdAccountService.getById(account.getId());
                log.error("更新后账号状态: {}",
                    updatedAccount.getStatus());
                log.error("========================================");

                // 记录日志
                systemLogService.logFileUpload(taskId, fileInfo.getId(), fileInfo.getFileName(),
                    fileInfo.getFileSize(), account.getId(), "FILE_UPLOAD_FAILED",
                    String.format("rclone超时（可能IP被封或网络问题） - 账号: %s 已自动禁用，需手动启用", account.getAccountName()),
                    "Timeout");

                // 返回超时结果（区别于配额超限，但同样触发账号切换）
                String errorMsg = "rclone执行超时（5分钟），可能IP被封或网络问题，账号已自动禁用，需手动启用";
                return UploadResult.timeout(errorMsg);
            }

            if (result.isSuccess()) {
                // 标记文件为已上传
                fileInfoService.markFileAsUploaded(fileId, accountId);

                // 插入上传记录（用于实时计算配额）
                UploadRecord uploadRecord = new UploadRecord();
                uploadRecord.setTaskId(taskId);
                uploadRecord.setAccountId(accountId);
                uploadRecord.setFileId(fileId);
                uploadRecord.setUploadSize(fileInfo.getFileSize());
                uploadRecord.setUploadTime(DateTimeUtil.now());
                uploadRecord.setStatus(1); // 1-成功

                log.info("准备插入上传记录: taskId={}, accountId={}, fileId={}, size={}, uploadTime={}",
                    taskId, accountId, fileId, fileInfo.getFileSize(), uploadRecord.getUploadTime());

                int insertResult = uploadRecordMapper.insert(uploadRecord);

                log.info("上传记录插入结果: insertResult={}, recordId={}", insertResult, uploadRecord.getId());

                // 验证插入是否成功
                Long todayUsed = gdAccountService.getTodayUsedQuota(accountId);
                log.info("插入后查询今日已使用配额: accountId={}, todayUsed={}", accountId, todayUsed);

                // 更新账号配额状态（检查是否达到上限）
                gdAccountService.updateAccountQuota(accountId, fileInfo.getFileSize());

                // 探测账号是否仍然可用（上传后检测）
                log.info("开始探测账号状态: accountId={}, accountName={}", accountId, account.getAccountName());
                RcloneUtil.ProbeResult probeResult = rcloneUtil.probeAccount(
                    account.getRcloneConfigName(),
                    remotePath
                );

                if (probeResult.isQuotaExceeded()) {
                    log.error("========== 探测检测到配额超限 ==========");
                    log.error("账号ID: {}, 账号名称: {}", account.getId(), account.getAccountName());
                    log.error("探测结果: {}", probeResult.getMessage());

                    // 记录禁用时间（移除自动解封时间）
                    LocalDateTime now = DateTimeUtil.now();
                    account.setDisabledTime(now);
                    account.setQuotaResetTime(null);  // 不再设置自动解封时间

                    // 禁用账号
                    account.setStatus(0);

                    log.error("准备更新账号状态: 1 -> 0 (禁用)");
                    log.error("禁用时间: {}",
                        now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    boolean updateResult = gdAccountService.updateById(account);
                    log.error("账号状态更新结果: {}, 账号ID: {}", updateResult ? "成功" : "失败", account.getId());

                    // 验证更新是否成功
                    GdAccount updatedAccount = gdAccountService.getById(account.getId());
                    log.error("更新后账号状态: {}",
                        updatedAccount.getStatus());
                    log.error("========================================");

                    // 记录日志
                    systemLogService.logFileUpload(taskId, fileInfo.getId(), fileInfo.getFileName(),
                        fileInfo.getFileSize(), account.getId(), "ACCOUNT_QUOTA_EXCEEDED",
                        String.format("探测检测到账号配额超限 - 账号: %s 已自动禁用，需手动启用", account.getAccountName()),
                        probeResult.getMessage());

                    log.warn("账号 {} 在上传成功后探测到配额超限，已自动禁用", account.getAccountName());
                } else if (!probeResult.isAvailable()) {
                    log.warn("探测账号失败，但不影响本次上传结果: accountId={}, message={}",
                        accountId, probeResult.getMessage());
                } else {
                    log.info("探测成功: 账号 {} 仍然可用", account.getAccountName());
                }

                return UploadResult.success();
            } else {
                // 标记文件为失败
                fileInfoService.updateFileStatus(fileId, 3, "上传失败");
                return UploadResult.failure("上传失败: " + result.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("上传文件异常: fileId={}, accountId={}", fileId, accountId, e);
            fileInfoService.updateFileStatus(fileId, 3, e.getMessage());
            return UploadResult.failure(e.getMessage());
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
     * 清理单个文件名或目录名中的特殊符号（直接删除特殊字符）
     */
    private String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        // 保留扩展名，只清理文件名部分
        int lastDotIndex = name.lastIndexOf('.');
        String nameWithoutExt;
        String extension;

        if (lastDotIndex > 0) {
            nameWithoutExt = name.substring(0, lastDotIndex);
            extension = name.substring(lastDotIndex);
        } else {
            nameWithoutExt = name;
            extension = "";
        }

        // 直接删除特殊符号（不替换为下划线）
        // 删除: # % & { } \ < > * ? / $ ! ' " : @ + ` | = [ ] 和其他可能导致问题的字符
        nameWithoutExt = nameWithoutExt
            .replaceAll("[#%&{}\\\\<>*?/$!'\":@+`|=\\[\\]]", "")  // 直接删除特殊符号
            .trim();  // 移除首尾空格

        // 如果清理后文件名为空，使用默认名称
        if (nameWithoutExt.isEmpty()) {
            nameWithoutExt = "file";
        }

        return nameWithoutExt + extension;
    }
}
