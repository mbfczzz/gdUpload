package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件信息实体
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Data
@TableName("file_info")
public class FileInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小(字节)
     */
    private Long fileSize;

    /**
     * 文件MD5
     */
    private String fileMd5;

    /**
     * 状态: 0-待上传 1-上传中 2-已上传 3-失败 4-跳过
     */
    private Integer status;

    /**
     * 上传使用的账号ID
     */
    private Long uploadAccountId;

    /**
     * 上传开始时间
     */
    private LocalDateTime uploadStartTime;

    /**
     * 上传结束时间
     */
    private LocalDateTime uploadEndTime;

    /**
     * 重试次数
     */
    private Integer retryCount;

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
