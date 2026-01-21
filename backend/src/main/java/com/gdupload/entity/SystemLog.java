package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统日志实体
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Data
@TableName("system_log")
public class SystemLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 日志类型: 1-信息 2-警告 3-错误 4-调试
     */
    private Integer logType;

    /**
     * 日志级别: INFO, WARN, ERROR, DEBUG
     */
    private String logLevel;

    /**
     * 模块名称
     */
    private String module;

    /**
     * 操作名称
     */
    private String operation;

    /**
     * 关联任务ID
     */
    private Long taskId;

    /**
     * 关联账号ID
     */
    private Long accountId;

    /**
     * 关联文件ID
     */
    private Long fileId;

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 文件大小(字节)
     */
    private Long fileSize;

    /**
     * 日志消息
     */
    private String message;

    /**
     * 详细信息
     */
    private String detail;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
