package com.gdupload.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.entity.ArchiveBatchTask;
import com.gdupload.entity.ArchiveHistory;

/**
 * 批量归档服务接口
 */
public interface IBatchArchiveService {

    /**
     * 启动批量归档任务（异步执行）
     *
     * @param accountId  GD账号ID
     * @param sourcePath 源目录路径（相对于远程根目录）
     * @return 创建的任务实体
     */
    ArchiveBatchTask startBatchTask(Long accountId, String sourcePath);

    /**
     * 分页查询批量任务列表
     */
    IPage<ArchiveBatchTask> listTasks(Page<ArchiveBatchTask> page);

    /**
     * 查询单个任务（含最新状态）
     */
    ArchiveBatchTask getTask(Long taskId);

    /**
     * 查询任务下的归档历史（支持按状态过滤）
     */
    IPage<ArchiveHistory> getTaskHistory(Long taskId, Page<ArchiveHistory> page, String status);

    /**
     * 取消正在运行的任务
     */
    void cancelTask(Long taskId);

    /**
     * 暂停正在运行的任务（下一个文件处理前生效）
     */
    void pauseTask(Long taskId);

    /**
     * 恢复已暂停的任务
     */
    void resumeTask(Long taskId);
}
