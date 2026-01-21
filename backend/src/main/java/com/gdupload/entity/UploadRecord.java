package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 上传记录实体
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Data
@TableName("upload_record")
public class UploadRecord implements Serializable {

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
     * 账号ID
     */
    private Long accountId;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 上传大小(字节)
     */
    private Long uploadSize;

    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;

    /**
     * 状态: 1-成功 2-失败
     */
    private Integer status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
