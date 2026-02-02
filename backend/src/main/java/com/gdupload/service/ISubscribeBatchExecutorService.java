package com.gdupload.service;

import com.gdupload.entity.SubscribeBatchLog;
import com.gdupload.entity.SubscribeBatchTask;

/**
 * 订阅批量搜索执行器Service
 *
 * @author GD Upload Manager
 * @since 2026-01-25
 */
public interface ISubscribeBatchExecutorService {

    /**
     * 异步执行批量任务
     *
     * @param task 任务信息
     */
    void executeTask(SubscribeBatchTask task);

    /**
     * 停止任务执行
     *
     * @param taskId 任务ID
     */
    void stopTask(Long taskId);
}
