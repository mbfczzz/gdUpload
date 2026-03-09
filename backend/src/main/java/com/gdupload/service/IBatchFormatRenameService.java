package com.gdupload.service;

import java.util.Map;

/**
 * 批量格式化命名服务
 * 扫描目录下没有编码信息的媒体文件，通过 ffprobe 探测后自动重命名
 */
public interface IBatchFormatRenameService {

    /**
     * 启动任务（异步），返回 taskId
     */
    String startTask(String rcloneConfigName, String dirPath);

    /**
     * 获取任务进度
     */
    Map<String, Object> getStatus(String taskId);

    /**
     * 取消任务
     */
    void cancelTask(String taskId);

    /**
     * 暂停任务
     */
    void pauseTask(String taskId);

    /**
     * 恢复已暂停的任务
     */
    void resumeTask(String taskId);
}
