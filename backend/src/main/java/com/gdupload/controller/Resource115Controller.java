package com.gdupload.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdupload.common.Result;
import com.gdupload.entity.Resource115;
import com.gdupload.entity.SmartSearchConfig;
import com.gdupload.mapper.SmartSearchConfigMapper;
import com.gdupload.service.IResource115Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 115资源控制器
 */
@Slf4j
@RestController
@RequestMapping("/resource115")
public class Resource115Controller {

    @Autowired
    private IResource115Service resource115Service;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SmartSearchConfigMapper smartSearchConfigMapper;

    /**
     * 从数据库获取115 Cookie
     */
    private String get115Cookie() {
        try {
            LambdaQueryWrapper<SmartSearchConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SmartSearchConfig::getConfigType, "115_config")
                    .eq(SmartSearchConfig::getIsActive, true)
                    .last("LIMIT 1");

            SmartSearchConfig config = smartSearchConfigMapper.selectOne(wrapper);
            if (config != null && config.getConfigData() != null) {
                Map<String, Object> data = JSONUtil.toBean(config.getConfigData(), Map.class);
                if (data.containsKey("cookie115")) {
                    String cookie = (String) data.get("cookie115");
                    if (cookie != null && !cookie.isEmpty()) {
                        return cookie;
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取115 Cookie失败", e);
        }
        return null;
    }

    /**
     * 从数据库获取115目标文件夹ID
     */
    private String get115TargetFolderId() {
        try {
            LambdaQueryWrapper<SmartSearchConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SmartSearchConfig::getConfigType, "115_config")
                    .eq(SmartSearchConfig::getIsActive, true)
                    .last("LIMIT 1");

            SmartSearchConfig config = smartSearchConfigMapper.selectOne(wrapper);
            if (config != null && config.getConfigData() != null) {
                Map<String, Object> data = JSONUtil.toBean(config.getConfigData(), Map.class);
                if (data.containsKey("targetFolderId115")) {
                    String folderId = (String) data.get("targetFolderId115");
                    if (folderId != null && !folderId.isEmpty()) {
                        return folderId;
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取115目标文件夹ID失败", e);
        }
        return "0"; // 默认返回根目录
    }

    /**
     * 智能搜索匹配资源
     *
     * @param tmdbId TMDB ID（优先匹配）
     * @param name 媒体项名称
     * @param originalTitle 原始名称
     * @param year 年份
     * @return 匹配的资源
     */
    @GetMapping("/smart-search")
    public Result<Resource115> smartSearch(
            @RequestParam(required = false) String tmdbId,
            @RequestParam String name,
            @RequestParam(required = false) String originalTitle,
            @RequestParam(required = false) Integer year) {

        log.info("智能搜索115资源: tmdbId={}, name={}, originalTitle={}, year={}", tmdbId, name, originalTitle, year);

        Resource115 resource = resource115Service.smartSearch(tmdbId, name, originalTitle, year);

        if (resource != null) {
            return Result.success(resource);
        } else {
            return Result.success("未找到匹配的资源", null);
        }
    }

    /**
     * 转存115资源
     *
     * @param url 115分享链接
     * @param code 访问码
     * @param targetFolderId 目标文件夹ID（可选，不传则使用配置的默认值）
     * @return 转存结果
     */
    @PostMapping("/transfer")
    public Result<Map<String, Object>> transfer(
            @RequestParam String url,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String targetFolderId) {

        log.info("转存115资源: url={}, code={}, targetFolderId={}", url, code, targetFolderId);

        try {
            // 从数据库获取Cookie配置
            String cookie115 = get115Cookie();
            if (cookie115 == null || cookie115.isEmpty()) {
                log.error("115 Cookie未配置");
                return Result.error("115 Cookie未配置，请在智能搜索配置中配置115服务");
            }

            // 确定目标文件夹ID：优先使用传入的参数，否则使用配置的默认值
            String finalTargetFolderId = targetFolderId;
            if (finalTargetFolderId == null || finalTargetFolderId.isEmpty()) {
                finalTargetFolderId = get115TargetFolderId();
            }

            // 提取纯数字ID（如果格式是 video2:3355571321271865685，则提取后面的数字）
            if (finalTargetFolderId.contains(":")) {
                String[] parts = finalTargetFolderId.split(":");
                if (parts.length > 1) {
                    finalTargetFolderId = parts[1];
                }
            }

            log.info("115转存目标文件夹ID: {}", finalTargetFolderId);

            // 构建请求URL
            String apiUrl = "https://webapi.115.com/share/receive";

            // 构建请求参数 - 使用 MultiValueMap
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

            // 从分享链接中提取分享码
            String shareCode = extractShareCode(url);
            if (shareCode == null) {
                return Result.error("无效的115分享链接");
            }

            params.add("share_code", shareCode);
            if (code != null && !code.isEmpty()) {
                params.add("receive_code", code);
            }
            params.add("cid", finalTargetFolderId); // 使用确定的目标文件夹ID

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Cookie", cookie115);
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            // 发送请求 - 先获取String响应
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);

            String responseBody = response.getBody();
            log.info("115转存原始响应: {}", responseBody);

            // 尝试解析JSON响应
            Map<String, Object> result;
            try {
                result = JSONUtil.toBean(responseBody, Map.class);
            } catch (Exception e) {
                log.error("解析115响应失败，可能Cookie无效或已过期。响应内容: {}", responseBody);
                return Result.error("转存失败：Cookie可能无效或已过期，请重新配置115 Cookie");
            }

            // 检查响应
            if (result != null && result.containsKey("state")) {
                boolean success = (boolean) result.get("state");
                String errorMsg = result.containsKey("error") ? result.get("error").toString() : "";
                Integer errno = result.containsKey("errno") ? (Integer) result.get("errno") : null;

                if (success) {
                    return Result.success("转存成功", result);
                } else {
                    // 特殊处理：已经转存过的文件也算成功
                    if (errno != null && errno == 4100024) {
                        log.info("文件已经转存过，视为成功");
                        return Result.success("文件已存在（之前已转存）", result);
                    }

                    // 其他错误
                    return Result.error(errorMsg.isEmpty() ? "转存失败" : errorMsg);
                }
            }

            return Result.error("转存失败：响应格式异常");

        } catch (Exception e) {
            log.error("转存115资源失败", e);
            return Result.error("转存失败: " + e.getMessage());
        }
    }

    /**
     * 从115分享链接中提取分享码
     * 例如: https://115.com/s/swzx7yl36gr -> swzx7yl36gr
     */
    private String extractShareCode(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // 移除URL参数
        int questionMarkIndex = url.indexOf('?');
        if (questionMarkIndex > 0) {
            url = url.substring(0, questionMarkIndex);
        }

        // 提取分享码
        if (url.contains("/s/")) {
            String[] parts = url.split("/s/");
            if (parts.length > 1) {
                return parts[1].trim();
            }
        }

        return null;
    }

    /**
     * 测试115 Cookie是否有效
     */
    @GetMapping("/test-cookie")
    public Result<Void> testCookie() {
        log.info("测试115 Cookie");

        try {
            // 从数据库获取Cookie配置
            String cookie115 = get115Cookie();
            if (cookie115 == null || cookie115.isEmpty()) {
                return Result.error("115 Cookie未配置");
            }

            // 调用115 API获取用户信息来验证Cookie
            String apiUrl = "https://my.115.com/?ct=ajax&ac=nav";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", cookie115);
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.GET, request, Map.class);

            Map<String, Object> result = response.getBody();
            log.info("115 Cookie测试响应: {}", result);

            if (result != null && result.containsKey("state") && (boolean) result.get("state")) {
                return Result.success("115 Cookie有效");
            } else {
                return Result.error("115 Cookie无效或已过期");
            }

        } catch (Exception e) {
            log.error("测试115 Cookie失败", e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }

    /**
     * 获取115用户信息
     */
    @GetMapping("/user-info")
    public Result<Map<String, Object>> getUserInfo() {
        log.info("获取115用户信息");

        try {
            // 从数据库获取Cookie配置
            String cookie115 = get115Cookie();
            if (cookie115 == null || cookie115.isEmpty()) {
                return Result.error("115 Cookie未配置");
            }

            // 调用115 API获取用户信息
            String apiUrl = "https://my.115.com/?ct=ajax&ac=nav";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", cookie115);
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.GET, request, Map.class);

            Map<String, Object> result = response.getBody();
            log.info("115用户信息响应: {}", result);

            if (result != null && result.containsKey("data")) {
                return Result.success(result);
            } else {
                return Result.error("获取用户信息失败");
            }

        } catch (Exception e) {
            log.error("获取115用户信息失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }
}
