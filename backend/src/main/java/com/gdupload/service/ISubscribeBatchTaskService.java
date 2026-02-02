package com.gdupload.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gdupload.entity.SubscribeBatchTask;

/**
 * 订阅批量搜索任务Service
 *
 * @author GD Upload Manager
 * @since 2026-01-25
 */
public interface ISubscribeBatchTaskService extends IService<SubscribeBatchTask> {

    /**
     * 创建批量任务
     *
     * @param taskName 任务名称
     * @param jsonData JSON数据
     * @param delayMin 最小延迟(分钟)
     * @param delayMax 最大延迟(分钟)
     * @return 任务ID
     */
    Long createTask(String taskName, String jsonData, Integer delayMin, Integer delayMax);

    /**
     * 启动任务
     *
     * @param taskId 任务ID
     */
    void startTask(Long taskId);

    /**
     * 暂停任务
     *
     * @param taskId 任务ID
     */
    void pauseTask(Long taskId);

    /**
     * 获取任务详情
     *
     * @param taskId 任务ID
     * @return 任务详情
     */
    SubscribeBatchTask getTaskDetail(Long taskId);

    /**
     * 分页查询任务列表
     *
     * @param page 分页参数
     * @return 任务列表
     */
    Page<SubscribeBatchTask> getTaskPage(Page<SubscribeBatchTask> page);
}
