package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.entity.Resource115;
import com.gdupload.service.I115TransferService;
import com.gdupload.service.IResource115Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 115转存控制器
 */
@Slf4j
@RestController
@RequestMapping("/115-transfer")
public class Transfer115Controller {

    @Autowired
    private I115TransferService transfer115Service;

    @Autowired
    private IResource115Service resource115Service;

    /**
     * 通过TMDB ID转存115资源
     */
    @PostMapping("/by-tmdb-id")
    public Result<Map<String, Object>> transferByTmdbId(@RequestBody Map<String, Object> params) {
        String tmdbId = (String) params.get("tmdbId");
        String targetFolderId = (String) params.get("targetFolderId");

        if (tmdbId == null || tmdbId.isEmpty()) {
            return Result.error("TMDB ID不能为空");
        }

        try {
            // 1. 通过TMDB ID查找115资源
            Resource115 resource = resource115Service.smartSearch(tmdbId, null, null, null);

            if (resource == null) {
                log.info("未找到TMDB ID为 {} 的115资源", tmdbId);
                return Result.error("未找到匹配的115资源");
            }

            log.info("找到115资源: {}", resource.getName());

            // 2. 转存资源
            Map<String, Object> result = transfer115Service.transferResource(resource, targetFolderId);

            if ((Boolean) result.get("success")) {
                return Result.success("转存成功", result);
            } else {
                return Result.error((String) result.get("message"));
            }

        } catch (Exception e) {
            log.error("转存115资源失败", e);
            return Result.error("转存失败: " + e.getMessage());
        }
    }

    /**
     * 直接转存115资源
     */
    @PostMapping("/transfer")
    public Result<Map<String, Object>> transfer(@RequestBody Resource115 resource) {
        try {
            Map<String, Object> result = transfer115Service.transferResource(resource, null);

            if ((Boolean) result.get("success")) {
                return Result.success("转存成功", result);
            } else {
                return Result.error((String) result.get("message"));
            }

        } catch (Exception e) {
            log.error("转存115资源失败", e);
            return Result.error("转存失败: " + e.getMessage());
        }
    }

    /**
     * 测试115 Cookie
     */
    @GetMapping("/test-cookie")
    public Result<Boolean> testCookie() {
        try {
            boolean valid = transfer115Service.testCookie();
            if (valid) {
                return Result.success("Cookie有效", true);
            } else {
                return Result.error("Cookie无效或已过期");
            }
        } catch (Exception e) {
            log.error("测试Cookie失败", e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }

    /**
     * 获取115用户信息
     */
    @GetMapping("/user-info")
    public Result<Map<String, Object>> getUserInfo() {
        try {
            Map<String, Object> userInfo = transfer115Service.getUserInfo();
            if (userInfo.containsKey("error")) {
                return Result.error((String) userInfo.get("error"));
            }
            return Result.success(userInfo);
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }
}
