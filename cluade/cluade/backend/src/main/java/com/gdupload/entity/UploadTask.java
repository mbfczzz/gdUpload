package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 上传任务实体
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Data
@TableName("upload_task")
public class UploadTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 任务类型: 1-普通上传 2-增量上传
     */
    private Integer taskType;

    /**
     * 源文件路径
     */
    private String sourcePath;

    /**
     * 目标路径
     */
    private String targetPath;

    /**
     * 总文件数
     */
    private Integer totalCount;

    /**
     * 已上传文件数
     */
    private Integer uploadedCount;

    /**
     * 失败文件数
     */
    private Integer failedCount;

    /**
     * 总大小(字节)
     */
    private Long totalSize;

    /**
     * 已上传大小(字节)
     */
    private Long uploadedSize;

    /**
     * 进度百分比(0-100)
     */
    private Integer progress;

    /**
     * 状态: 0-待开始 1-上传中 2-已完成 3-已暂停 4-已取消 5-失败
     */
    private Integer status;

    /**
     * 当前使用的账号ID
     */
    private Long currentAccountId;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
