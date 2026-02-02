package com.gdupload.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.gdupload.common.BusinessException;
import com.gdupload.entity.EmbyConfig;
import com.gdupload.service.IEmbyConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Emby 认证服务
 */
@Slf4j
@Service
public class EmbyAuthService {

    @Autowired
    private IEmbyConfigService embyConfigService;

    private String cachedAccessToken;
    private String cachedUserId;
    private Long currentConfigId;

    /**
     * 使用用户名密码登录获取 Access Token
     *
     * @param config Emby配置
     * @return Access Token
     */
    public String loginWithConfig(EmbyConfig config) {
        if (StrUtil.isBlank(config.getUsername()) || StrUtil.isBlank(config.getPassword())) {
            throw new BusinessException("用户名或密码为空");
        }

        String url = config.getServerUrl();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        url += "/emby/Users/AuthenticateByName";

        try {
            // 伪装成 Forward app 的设备信息
            String deviceId = "30c9d308e74a46a1811c851bf76a8f77";
            String forwardVersion = "1.3.13";

            JSONObject requestBody = new JSONObject();
            requestBody.set("Username", config.getUsername());
            requestBody.set("Pw", config.getPassword());

            // 构建 X-Emby-Authorization 请求头（Forward app 格式）
            String embyAuth = String.format(
                "MediaBrowser Client=\"Forward\", Device=\"iPhone\", DeviceId=\"%s\", Version=\"%s\"",
                deviceId, forwardVersion
            );

            HttpResponse response = HttpRequest.post(url)
                    .header("Content-Type", "application/json")
                    .header("X-Emby-Authorization", embyAuth)
                    .header("User-Agent", "Forward/" + forwardVersion)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "zh-CN,zh-Hans;q=0.9")
                    .header("Connection", "keep-alive")
                    .body(requestBody.toString())
                    .timeout(config.getTimeout() != null ? config.getTimeout() : 30000)
                    .execute();

            if (!response.isOk()) {
                log.error("Emby 登录失败: {} - {}", response.getStatus(), response.body());
                throw new BusinessException("Emby 登录失败: " + response.getStatus());
            }

            JSONObject result = JSONUtil.parseObj(response.body());
            cachedAccessToken = result.getStr("AccessToken");
            cachedUserId = result.getJSONObject("User").getStr("Id");
            currentConfigId = config.getId();

            log.info("Emby 登录成功，用户ID: {}", cachedUserId);
            return cachedAccessToken;

        } catch (Exception e) {
            log.error("Emby 登录异常: {}", e.getMessage(), e);
            throw new BusinessException("Emby 登录异常: " + e.getMessage());
        }
    }

    /**
     * 使用用户名密码登录获取 Access Token
     *
     * @param username 用户名
     * @param password 密码
     * @return Access Token
     */
    public String loginWithPassword(String username, String password) {
        EmbyConfig config = embyConfigService.getDefaultConfig();
        config.setUsername(username);
        config.setPassword(password);
        return loginWithConfig(config);
    }

    /**
     * 获取当前的 Access Token（优先使用 API Key，其次使用登录 Token）
     */
    public String getAccessToken() {
        EmbyConfig config = embyConfigService.getDefaultConfig();

        // 如果配置了 API Key，优先使用
        if (StrUtil.isNotBlank(config.getApiKey())) {
            return config.getApiKey();
        }

        // 如果配置了用户名密码，自动登录
        if (StrUtil.isNotBlank(config.getUsername()) && StrUtil.isNotBlank(config.getPassword())) {
            // 如果缓存的 Token 是当前配置的，直接返回
            if (cachedAccessToken != null && config.getId().equals(currentConfigId)) {
                return cachedAccessToken;
            }
            // 否则重新登录
            return loginWithConfig(config);
        }

        // 否则返回缓存的 Access Token
        if (cachedAccessToken == null || cachedAccessToken.isEmpty()) {
            throw new BusinessException("未配置 API Key 且未登录，请先配置或登录");
        }

        return cachedAccessToken;
    }

    /**
     * 获取当前用户ID
     */
    public String getUserId() {
        EmbyConfig config = embyConfigService.getDefaultConfig();

        // 如果配置了用户ID，优先使用
        if (StrUtil.isNotBlank(config.getUserId())) {
            log.debug("使用配置的用户ID: {}", config.getUserId());
            return config.getUserId();
        }

        // 如果有缓存的用户ID，返回
        if (StrUtil.isNotBlank(cachedUserId)) {
            log.debug("使用缓存的用户ID: {}", cachedUserId);
            return cachedUserId;
        }

        // 否则需要先登录
        log.info("用户ID未配置且未缓存，尝试登录获取");
        getAccessToken();

        if (StrUtil.isBlank(cachedUserId)) {
            throw new BusinessException("无法获取用户ID，请检查配置或登录信息");
        }

        return cachedUserId;
    }

    /**
     * 获取服务器地址
     */
    public String getServerUrl() {
        EmbyConfig config = embyConfigService.getDefaultConfig();
        return config.getServerUrl();
    }

    /**
     * 获取超时时间
     */
    public Integer getTimeout() {
        EmbyConfig config = embyConfigService.getDefaultConfig();
        return config.getTimeout() != null ? config.getTimeout() : 30000;
    }

    /**
     * 登出
     */
    public void logout() {
        cachedAccessToken = null;
        cachedUserId = null;
        currentConfigId = null;
        log.info("已登出 Emby");
    }
}
