package com.gdupload.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gdupload.entity.SystemLog;

/**
 * 系统日志服务接口
 *
 * @author GD Upload Manager
 * @since 2026-01-19
 */
public interface ISystemLogService extends IService<SystemLog> {

    /**
     * 分页查询日志
     *
     * @param page 分页对象
     * @param logType 日志类型
     * @param logLevel 日志级别
     * @param module 模块
     * @param keyword 关键词
     * @return 分页结果
     */
    Page<SystemLog> pageLogs(Page<SystemLog> page, Integer logType, String logLevel,
                             String module, String keyword);

    /**
     * 记录日志
     *
     * @param logType 日志类型
     * @param logLevel 日志级别
     * @param module 模块
     * @param operation 操作
     * @param message 消息
     * @param detail 详情
     */
    void log(Integer logType, String logLevel, String module, String operation,
             String message, String detail);

    /**
     * 记录任务日志
     *
     * @param taskId 任务ID
     * @param logType 日志类型
     * @param logLevel 日志级别
     * @param operation 操作
     * @param message 消息
     */
    void logTask(Long taskId, Integer logType, String logLevel, String operation, String message);

    /**
     * 记录账号日志
     *
     * @param accountId 账号ID
     * @param logType 日志类型
     * @param logLevel 日志级别
     * @param operation 操作
     * @param message 消息
     */
    void logAccount(Long accountId, Integer logType, String logLevel, String operation, String message);

    /**
     * 记录文件上传日志
     *
     * @param taskId 任务ID
     * @param fileId 文件ID
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param accountId 账号ID
     * @param operation 操作
     * @param message 消息
     * @param detail 详情
     */
    void logFileUpload(Long taskId, Long fileId, String fileName, Long fileSize,
                       Long accountId, String operation, String message, String detail);

    /**
     * 记录任务操作日志（启动、暂停、取消、重试等）
     *
     * @param taskId 任务ID
     * @param taskName 任务名称
     * @param operation 操作
     * @param message 消息
     */
    void logTaskOperation(Long taskId, String taskName, String operation, String message);

    /**
     * 清理过期日志
     *
     * @param days 保留天数
     * @return 清理数量
     */
    long cleanExpiredLogs(Integer days);
}
