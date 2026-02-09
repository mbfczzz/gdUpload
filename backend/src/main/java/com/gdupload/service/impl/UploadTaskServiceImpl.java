package com.gdupload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gdupload.entity.UploadTask;
import com.gdupload.mapper.FileInfoMapper;
import com.gdupload.mapper.UploadTaskMapper;
import com.gdupload.service.ISystemLogService;
import com.gdupload.service.IUploadTaskService;
import com.gdupload.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 上传任务服务实现
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadTaskServiceImpl extends ServiceImpl<UploadTaskMapper, UploadTask> implements IUploadTaskService {

    private final ISystemLogService systemLogService;
    private final FileInfoMapper fileInfoMapper;

    @Override
    public Page<UploadTask> pageTasks(Page<UploadTask> page, String keyword, Integer status) {
        LambdaQueryWrapper<UploadTask> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(UploadTask::getTaskName, keyword);
        }

        if (status != null) {
            wrapper.eq(UploadTask::getStatus, status);
        }

        wrapper.orderByDesc(UploadTask::getCreateTime);

        Page<UploadTask> result = this.page(page, wrapper);

        // 实时计算每个任务的进度（基于FileInfo表的实际状态）
        result.getRecords().forEach(task -> {
            try {
                // 查询文件状态统计
                List<java.util.Map<String, Object>> stats = fileInfoMapper.selectTaskFileStats(task.getId());

                int totalFiles = 0;
                int completedFiles = 0; // status=2(已上传) + status=4(跳过)

                for (java.util.Map<String, Object> stat : stats) {
                    Integer fileStatus = (Integer) stat.get("status");
                    Long count = (Long) stat.get("count");

                    totalFiles += count.intValue();

                    // status=2(已上传) 或 status=4(跳过) 都算完成
                    if (fileStatus == 2 || fileStatus == 4) {
                        completedFiles += count.intValue();
                    }
                }

                // 更新实时进度
                if (totalFiles > 0) {
                    int realProgress = (int) ((completedFiles * 100L) / totalFiles);
                    task.setProgress(realProgress);
                    task.setUploadedCount(completedFiles);
                    task.setTotalCount(totalFiles);
                }
            } catch (Exception e) {
                log.warn("计算任务进度失败: taskId={}, error={}", task.getId(), e.getMessage());
            }
        });

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(String taskName, String sourcePath, String targetPath, List<String> fileList) {
        UploadTask task = new UploadTask();
        task.setTaskName(taskName);
        task.setSourcePath(sourcePath);
        task.setTargetPath(targetPath);
        task.setTotalCount(fileList.size());
        task.setUploadedCount(0);
        task.setTotalSize(0L);
        task.setUploadedSize(0L);
        task.setProgress(0);
        task.setStatus(0); // 待开始
        task.setCreateTime(DateTimeUtil.now());

        boolean saved = this.save(task);

        if (saved) {
            log.info("创建上传任务成功: taskId={}, taskName={}, fileCount={}",
                task.getId(), taskName, fileList.size());

            // 记录任务创建日志
            systemLogService.logTaskOperation(task.getId(), taskName, "TASK_CREATE",
                String.format("创建任务 - 源路径: %s, 目标路径: %s, 文件数: %d",
                    sourcePath, targetPath, fileList.size()));

            return task.getId();
        }

        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean startTask(Long taskId) {
        UploadTask task = this.getById(taskId);
        if (task == null) {
            log.error("任务不存在: taskId={}", taskId);
            return false;
        }

        if (task.getStatus() != 0 && task.getStatus() != 3) {
            log.error("任务状态不允许启动: taskId={}, status={}", taskId, task.getStatus());
            return false;
        }

        task.setStatus(1); // 上传中
        task.setStartTime(DateTimeUtil.now());

        boolean updated = this.updateById(task);

        if (updated) {
            log.info("启动任务成功: taskId={}", taskId);
        }

        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean pauseTask(Long taskId) {
        UploadTask task = this.getById(taskId);
        if (task == null || task.getStatus() != 1) {
            return false;
        }

        task.setStatus(3); // 已暂停

        boolean updated = this.updateById(task);

        if (updated) {
            log.info("暂停任务成功: taskId={}", taskId);

            // 记录任务暂停日志
            systemLogService.logTaskOperation(taskId, task.getTaskName(), "TASK_PAUSE",
                String.format("暂停任务 - 已上传: %d/%d, 进度: %d%%",
                    task.getUploadedCount(), task.getTotalCount(), task.getProgress()));
        }

        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resumeTask(Long taskId) {
        UploadTask task = this.getById(taskId);
        if (task == null || task.getStatus() != 3) {
            return false;
        }

        task.setStatus(1); // 上传中

        boolean updated = this.updateById(task);

        if (updated) {
            log.info("恢复任务成功: taskId={}", taskId);

            // 记录任务恢复日志
            systemLogService.logTaskOperation(taskId, task.getTaskName(), "TASK_RESUME",
                String.format("恢复任务 - 当前进度: %d/%d, %d%%",
                    task.getUploadedCount(), task.getTotalCount(), task.getProgress()));
        }

        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelTask(Long taskId) {
        UploadTask task = this.getById(taskId);
        if (task == null) {
            return false;
        }

        task.setStatus(4); // 已取消
        task.setEndTime(DateTimeUtil.now());

        boolean updated = this.updateById(task);

        if (updated) {
            log.info("取消任务成功: taskId={}", taskId);

            // 记录任务取消日志
            systemLogService.logTaskOperation(taskId, task.getTaskName(), "TASK_CANCEL",
                String.format("取消任务 - 已上传: %d/%d, 进度: %d%%",
                    task.getUploadedCount(), task.getTotalCount(), task.getProgress()));
        }

        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean retryTask(Long taskId) {
        UploadTask task = this.getById(taskId);
        if (task == null) {
            return false;
        }

        task.setStatus(1); // 上传中
        task.setStartTime(DateTimeUtil.now());
        task.setEndTime(null);
        task.setErrorMessage(null);

        boolean updated = this.updateById(task);

        if (updated) {
            log.info("重试任务成功: taskId={}", taskId);

            // 记录任务重试日志
            systemLogService.logTaskOperation(taskId, task.getTaskName(), "TASK_RETRY",
                String.format("重试任务 - 当前进度: %d/%d, %d%%",
                    task.getUploadedCount(), task.getTotalCount(), task.getProgress()));
        }

        return updated;
    }

    @Override
    public UploadTask getTaskDetail(Long taskId) {
        return this.getById(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTaskProgress(Long taskId, Integer uploadedCount, Long uploadedSize, Integer progress) {
        UploadTask task = this.getById(taskId);
        if (task == null) {
            return false;
        }

        task.setUploadedCount(uploadedCount);
        task.setUploadedSize(uploadedSize);
        task.setProgress(progress);

        return this.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTaskStatus(Long taskId, Integer status, String errorMessage) {
        UploadTask task = this.getById(taskId);
        if (task == null) {
            return false;
        }

        task.setStatus(status);

        if (status == 2 || status == 4 || status == 5) {
            task.setEndTime(DateTimeUtil.now());
        }

        if (errorMessage != null) {
            task.setErrorMessage(errorMessage);
        }

        boolean updated = this.updateById(task);

        if (updated) {
            log.info("更新任务状态: taskId={}, status={}", taskId, status);
        }

        return updated;
    }

    @Override
    public List<UploadTask> getRunningTasks() {
        LambdaQueryWrapper<UploadTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UploadTask::getStatus, 1); // 上传中
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long cleanExpiredTasks(Integer days) {
        LocalDateTime expireTime = DateTimeUtil.now().minusDays(days);

        LambdaQueryWrapper<UploadTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(UploadTask::getCreateTime, expireTime)
               .in(UploadTask::getStatus, 2, 4, 5); // 已完成、已取消、失败

        long count = this.count(wrapper);

        if (count > 0) {
            this.remove(wrapper);
            log.info("清理过期任务: count={}", count);
        }

        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDownloadTask(String taskName, String targetPath, int totalCount) {
        UploadTask task = new UploadTask();
        task.setTaskName(taskName);
        task.setTaskType(3); // Emby下载
        task.setSourcePath("Emby Server");
        task.setTargetPath(targetPath);
        task.setTotalCount(totalCount);
        task.setUploadedCount(0);
        task.setFailedCount(0);
        task.setTotalSize(0L);
        task.setUploadedSize(0L);
        task.setProgress(0);
        task.setStatus(1); // 直接设为运行中
        task.setStartTime(DateTimeUtil.now());
        task.setCreateTime(DateTimeUtil.now());

        boolean saved = this.save(task);

        if (saved) {
            log.info("创建Emby下载任务成功: taskId={}, taskName={}, totalCount={}",
                task.getId(), taskName, totalCount);

            systemLogService.logTaskOperation(task.getId(), taskName, "TASK_CREATE",
                String.format("创建Emby下载任务 - 目标路径: %s, 媒体项数: %d", targetPath, totalCount));

            return task.getId();
        }

        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTaskTargetPath(Long taskId, String targetPath) {
        UploadTask task = this.getById(taskId);
        if (task == null) {
            log.error("任务不存在: taskId={}", taskId);
            return false;
        }

        String oldTargetPath = task.getTargetPath();
        task.setTargetPath(targetPath);

        boolean updated = this.updateById(task);

        if (updated) {
            log.info("更新任务目标路径成功: taskId={}, oldPath={}, newPath={}",
                taskId, oldTargetPath, targetPath);

            // 记录任务路径修改日志
            systemLogService.logTaskOperation(taskId, task.getTaskName(), "TASK_UPDATE_PATH",
                String.format("修改目标路径 - 原路径: %s, 新路径: %s", oldTargetPath, targetPath));
        }

        return updated;
    }
}
