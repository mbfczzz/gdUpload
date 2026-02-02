package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Emby配置实体
 */
@Data
@TableName("emby_config")
public class EmbyConfig {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配置名称
     */
    private String configName;

    /**
     * Emby服务器地址
     */
    private String serverUrl;

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 超时时间（毫秒）
     */
    private Integer timeout;

    /**
     * 是否启用（0-禁用 1-启用）
     */
    private Boolean enabled;

    /**
     * 是否默认配置（0-否 1-是）
     */
    private Boolean isDefault;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
