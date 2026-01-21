package com.gdupload.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gdupload.entity.UploadTask;

import java.util.List;

/**
 * 上传任务服务接口
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
public interface IUploadTaskService extends IService<UploadTask> {

    /**
     * 分页查询任务列表
     */
    Page<UploadTask> pageTasks(Page<UploadTask> page, String keyword, Integer status);

    /**
     * 创建上传任务
     */
    Long createTask(String taskName, String sourcePath, String targetPath, List<String> fileList);

    /**
     * 开始执行任务
     */
    boolean startTask(Long taskId);

    /**
     * 暂停任务
     */
    boolean pauseTask(Long taskId);

    /**
     * 恢复任务
     */
    boolean resumeTask(Long taskId);

    /**
     * 取消任务
     */
    boolean cancelTask(Long taskId);

    /**
     * 重试任务
     */
    boolean retryTask(Long taskId);

    /**
     * 获取任务详情
     */
    UploadTask getTaskDetail(Long taskId);

    /**
     * 更新任务进度
     */
    boolean updateTaskProgress(Long taskId, Integer uploadedCount, Long uploadedSize, Integer progress);

    /**
     * 更新任务状态
     */
    boolean updateTaskStatus(Long taskId, Integer status, String errorMessage);

    /**
     * 获取正在运行的任务列表
     */
    List<UploadTask> getRunningTasks();

    /**
     * 清理过期任务
     */
    long cleanExpiredTasks(Integer days);
}
