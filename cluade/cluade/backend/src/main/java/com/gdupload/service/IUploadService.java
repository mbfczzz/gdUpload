package com.gdupload.service;

/**
 * 上传核心服务接口
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
public interface IUploadService {

    /**
     * 执行上传任务
     */
    void executeTask(Long taskId);

    /**
     * 上传单个文件
     */
    boolean uploadFile(Long taskId, Long fileId, Long accountId);

    /**
     * 停止任务执行
     */
    void stopTask(Long taskId);

    /**
     * 获取任务执行状态
     */
    boolean isTaskRunning(Long taskId);
}
