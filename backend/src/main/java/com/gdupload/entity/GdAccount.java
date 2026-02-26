package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Google Drive账号实体
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Data
@TableName("gd_account")
public class GdAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 账号名称
     */
    private String accountName;

    /**
     * 账号邮箱
     */
    private String accountEmail;

    /**
     * rclone配置名称
     */
    private String rcloneConfigName;

    /**
     * 状态: 0-禁用 1-启用
     */
    private Integer status;

    /**
     * 禁用时间（账号被禁用的时间）
     */
    private LocalDateTime disabledTime;

    /**
     * 备注
     */
    private String remark;

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
