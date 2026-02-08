package com.gdupload.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.gdupload.entity.Resource115;
import com.gdupload.service.I115TransferService;
import com.gdupload.service.ISmartSearchConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 115网盘转存服务实现
 */
@Slf4j
@Service
public class Transfer115ServiceImpl implements I115TransferService {

    @Autowired
    private ISmartSearchConfigService smartSearchConfigService;

    @Value("${app.115.cookie:}")
    private String cookie115;

    @Value("${app.115.target-folder-id:0}")
    private String defaultTargetFolderId;

    // 115 API 地址
    private static final String API_BASE = "https://webapi.115.com";
    private static final String RECEIVE_URL = API_BASE + "/share/receive";
    private static final String USER_INFO_URL = "https://my.115.com/?ct=ajax&ac=nav";

    /**
     * 获取115 Cookie（优先从数据库读取）
     */
    private String get115Cookie() {
        try {
            Map<String, Object> config = smartSearchConfigService.getFullConfig("default");
            if (config != null && config.containsKey("cookie115")) {
                String dbCookie = (String) config.get("cookie115");
                if (dbCookie != null && !dbCookie.isEmpty()) {
                    return dbCookie;
                }
            }
        } catch (Exception e) {
            log.debug("从数据库读取115 Cookie失败: {}", e.getMessage());
        }
        return cookie115;
    }

    /**
     * 获取目标文件夹ID（优先从数据库读取）
     */
    private String getTargetFolderId() {
        try {
            Map<String, Object> config = smartSearchConfigService.getFullConfig("default");
            if (config != null && config.containsKey("targetFolderId115")) {
                String dbFolderId = (String) config.get("targetFolderId115");
                if (dbFolderId != null && !dbFolderId.isEmpty()) {
                    return dbFolderId;
                }
            }
        } catch (Exception e) {
            log.debug("从数据库读取115目标文件夹ID失败: {}", e.getMessage());
        }
        return defaultTargetFolderId;
    }

    @Override
    public Map<String, Object> transferResource(Resource115 resource, String targetFolderId) {
        log.info("开始转存115资源: {}", resource.getName());

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);

        // 获取Cookie（优先从数据库）
        String cookie = get115Cookie();
        if (cookie == null || cookie.isEmpty()) {
            log.error("115 Cookie 未配置");
            result.put("message", "115 Cookie 未配置");
            return result;
        }

        // 检查资源信息
        if (resource.getUrl() == null || resource.getUrl().isEmpty()) {
            log.error("115资源URL为空");
            result.put("message", "115资源URL为空");
            return result;
        }

        try {
            // 1. 解析分享链接，提取 share_code
            String shareCode = extractShareCode(resource.getUrl());
            if (shareCode == null) {
                log.error("无法从URL中提取分享码: {}", resource.getUrl());
                result.put("message", "无效的115分享链接");
                return result;
            }

            log.info("分享码: {}", shareCode);

            // 2. 使用目标文件夹ID（如果未指定，使用配置的默认值）
            String folderId = (targetFolderId != null && !targetFolderId.isEmpty())
                ? targetFolderId
                : getTargetFolderId();

            // 3. 构建转存请求
            Map<String, Object> params = new HashMap<>();
            params.put("share_code", shareCode);
            params.put("receive_code", resource.getCode() != null ? resource.getCode() : "");
            params.put("to_cid", folderId);
            params.put("user_id", ""); // 通常为空

            log.info("转存参数: {}", params);

            // 4. 发送转存请求
            HttpResponse response = HttpRequest.post(RECEIVE_URL)
                    .header("Cookie", cookie)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36")
                    .header("Accept", "*/*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Connection", "keep-alive")
                    .header("Referer", "https://115.com/")
                    .header("Origin", "https://115.com")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "same-site")
                    .form(params)
                    .timeout(30000)
                    .execute();

            String body = response.body();
            log.info("115 API响应: {}", body);

            // 5. 解析响应
            JSONObject json = JSONUtil.parseObj(body);
            boolean state = json.getBool("state", false);

            if (state) {
                log.info("转存成功: {}", resource.getName());
                result.put("success", true);
                result.put("message", "转存成功");
                result.put("data", json);
            } else {
                String errorMsg = json.getStr("error", "未知错误");
                log.error("转存失败: {}", errorMsg);
                result.put("message", "转存失败: " + errorMsg);
                result.put("error_code", json.getInt("errno", -1));
            }

        } catch (Exception e) {
            log.error("转存115资源异常", e);
            result.put("message", "转存异常: " + e.getMessage());
        }

        return result;
    }

    @Override
    public boolean testCookie() {
        String cookie = get115Cookie();
        if (cookie == null || cookie.isEmpty()) {
            log.warn("115 Cookie 未配置");
            return false;
        }

        log.info("测试115 Cookie，长度: {}", cookie.length());
        log.debug("Cookie前50字符: {}", cookie.length() > 50 ? cookie.substring(0, 50) + "..." : cookie);

        try {
            // 使用正确的 API 端点
            String testUrl = USER_INFO_URL;
            log.info("测试 API: {}", testUrl);

            HttpResponse response = HttpRequest.get(testUrl)
                    .header("Cookie", cookie)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36")
                    .header("Accept", "*/*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Connection", "keep-alive")
                    .header("Referer", "https://115.com/")
                    .header("Origin", "https://115.com")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "same-site")
                    .timeout(10000)
                    .execute();

            String responseBody = response.body();
            log.info("115 API响应状态码: {}", response.getStatus());
            log.info("115 API响应内容: {}", responseBody);

            JSONObject json = JSONUtil.parseObj(responseBody);
            boolean state = json.getBool("state", false);

            if (state) {
                log.info("115 Cookie 有效");
                return true;
            } else {
                log.warn("115 Cookie 无效，响应: {}", json);
                return false;
            }
        } catch (Exception e) {
            log.error("测试115 Cookie失败", e);
            return false;
        }
    }

    @Override
    public Map<String, Object> getUserInfo() {
        Map<String, Object> result = new HashMap<>();

        String cookie = get115Cookie();
        if (cookie == null || cookie.isEmpty()) {
            result.put("error", "115 Cookie 未配置");
            return result;
        }

        try {
            HttpResponse response = HttpRequest.get(USER_INFO_URL)
                    .header("Cookie", cookie)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36")
                    .header("Accept", "*/*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Connection", "keep-alive")
                    .header("Referer", "https://115.com/")
                    .header("Origin", "https://115.com")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "same-site")
                    .timeout(10000)
                    .execute();

            JSONObject json = JSONUtil.parseObj(response.body());
            boolean state = json.getBool("state", false);

            if (state) {
                JSONObject data = json.getJSONObject("data");
                result.put("user_id", data.getStr("user_id"));
                result.put("user_name", data.getStr("user_name"));
                result.put("space_info", data.getJSONObject("space_info"));
            } else {
                result.put("error", "获取用户信息失败");
            }
        } catch (Exception e) {
            log.error("获取115用户信息失败", e);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 从115分享链接中提取分享码
     * 支持格式：
     * - https://115.com/s/xxxxx
     * - https://115.com/s/xxxxx?password=yyyy
     */
    private String extractShareCode(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        try {
            // 提取 /s/ 后面的部分
            if (url.contains("/s/")) {
                String[] parts = url.split("/s/");
                if (parts.length > 1) {
                    String code = parts[1];
                    // 去除查询参数
                    if (code.contains("?")) {
                        code = code.split("\\?")[0];
                    }
                    return code.trim();
                }
            }
        } catch (Exception e) {
            log.error("提取分享码失败", e);
        }

        return null;
    }
}
