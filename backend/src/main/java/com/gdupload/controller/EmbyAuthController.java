package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.service.impl.EmbyAuthService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Emby 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/emby/auth")
public class EmbyAuthController {

    @Autowired
    private EmbyAuthService embyAuthService;

    /**
     * 使用用户名密码登录
     */
    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody LoginRequest request) {
        String accessToken = embyAuthService.loginWithPassword(
                request.getUsername(),
                request.getPassword()
        );

        Map<String, String> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("userId", embyAuthService.getUserId());

        return Result.success(result);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        embyAuthService.logout();
        return Result.success(null);
    }

    /**
     * 获取当前认证状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            String token = embyAuthService.getAccessToken();
            String userId = embyAuthService.getUserId();

            status.put("authenticated", true);
            status.put("userId", userId);
            status.put("hasToken", token != null && !token.isEmpty());
        } catch (Exception e) {
            status.put("authenticated", false);
            status.put("message", e.getMessage());
        }

        return Result.success(status);
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
