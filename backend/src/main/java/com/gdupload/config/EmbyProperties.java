package com.gdupload.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Emby配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.emby")
public class EmbyProperties {

    /**
     * Emby服务器地址
     */
    private String serverUrl;

    /**
     * API密钥（可选，如果没有则使用用户名密码登录）
     */
    private String apiKey;

    /**
     * 用户ID（可选）
     */
    private String userId;

    /**
     * 用户名（用于密码登录）
     */
    private String username;

    /**
     * 密码（用于密码登录）
     */
    private String password;

    /**
     * 请求超时时间（毫秒）
     */
    private Integer timeout = 30000;

    /**
     * 是否启用Emby集成
     */
    private Boolean enabled = true;
}

