package com.gdupload.service;

/**
 * WebSocket消息推送服务接口
 *
 * @author GD Upload Manager
 * @since 2026-01-19
 */
public interface IWebSocketService {

    /**
     * 推送任务进度更新
     *
     * @param taskId 任务ID
     * @param progress 进度百分比
     * @param uploadedCount 已上传文件数
     * @param totalCount 总文件数
     * @param uploadedSize 已上传大小
     * @param totalSize 总大小
     * @param currentFileName 当前上传文件名
     */
    void pushTaskProgress(Long taskId, Integer progress, Integer uploadedCount, Integer totalCount,
                          Long uploadedSize, Long totalSize, String currentFileName);

    /**
     * 推送任务状态变更
     *
     * @param taskId 任务ID
     * @param status 任务状态
     * @param message 状态消息
     */
    void pushTaskStatus(Long taskId, Integer status, String message);

    /**
     * 推送文件上传状态
     *
     * @param taskId 任务ID
     * @param fileId 文件ID
     * @param fileName 文件名
     * @param status 文件状态
     * @param message 状态消息
     */
    void pushFileStatus(Long taskId, Long fileId, String fileName, Integer status, String message);
}
