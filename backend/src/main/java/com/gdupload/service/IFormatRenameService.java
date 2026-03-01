package com.gdupload.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.entity.FormatRenameHistory;
import com.gdupload.entity.FormatRenameTask;

/**
 * 格式化命名任务服务
 */
public interface IFormatRenameService {

    /** 启动任务（异步），返回任务记录 */
    FormatRenameTask startTask(Long accountId, String dirPath);

    /** 分页查询任务列表 */
    IPage<FormatRenameTask> listTasks(Page<FormatRenameTask> page);

    /** 查询单个任务（含最新状态） */
    FormatRenameTask getTask(Long taskId);

    /** 查询任务下的文件历史 */
    IPage<FormatRenameHistory> getTaskHistory(Long taskId, Page<FormatRenameHistory> page, String status);

    /** 取消任务 */
    void cancelTask(Long taskId);

    /** 暂停任务 */
    void pauseTask(Long taskId);

    /** 恢复已暂停的任务 */
    void resumeTask(Long taskId);
}
