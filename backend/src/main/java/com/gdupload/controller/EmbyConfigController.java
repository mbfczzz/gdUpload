package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.entity.EmbyConfig;
import com.gdupload.service.IEmbyConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Emby配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/emby/config")
public class EmbyConfigController {

    @Autowired
    private IEmbyConfigService embyConfigService;

    /**
     * 获取所有配置
     */
    @GetMapping("/list")
    public Result<List<EmbyConfig>> getAllConfigs() {
        List<EmbyConfig> configs = embyConfigService.getAllConfigs();
        // 隐藏密码
        configs.forEach(config -> {
            if (config.getPassword() != null) {
                config.setPassword("******");
            }
        });
        return Result.success(configs);
    }

    /**
     * 获取默认配置
     */
    @GetMapping("/default")
    public Result<EmbyConfig> getDefaultConfig() {
        EmbyConfig config = embyConfigService.getDefaultConfig();
        // 隐藏密码
        if (config.getPassword() != null) {
            config.setPassword("******");
        }
        return Result.success(config);
    }

    /**
     * 获取配置详情
     */
    @GetMapping("/{id}")
    public Result<EmbyConfig> getConfig(@PathVariable Long id) {
        EmbyConfig config = embyConfigService.getById(id);
        // 隐藏密码
        if (config != null && config.getPassword() != null) {
            config.setPassword("******");
        }
        return Result.success(config);
    }

    /**
     * 保存或更新配置
     */
    @PostMapping("/save")
    public Result<Boolean> saveConfig(@RequestBody EmbyConfig config) {
        // 如果密码是 ******，表示不修改密码
        if ("******".equals(config.getPassword())) {
            if (config.getId() != null) {
                EmbyConfig oldConfig = embyConfigService.getById(config.getId());
                config.setPassword(oldConfig.getPassword());
            } else {
                config.setPassword(null);
            }
        }

        boolean success = embyConfigService.saveOrUpdateConfig(config);
        return Result.success(success);
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteConfig(@PathVariable Long id) {
        boolean success = embyConfigService.removeById(id);
        return Result.success(success);
    }

    /**
     * 设置默认配置
     */
    @PutMapping("/{id}/default")
    public Result<Boolean> setDefaultConfig(@PathVariable Long id) {
        boolean success = embyConfigService.setDefaultConfig(id);
        return Result.success(success);
    }

    /**
     * 测试配置
     */
    @PostMapping("/test")
    public Result<Boolean> testConfig(@RequestBody EmbyConfig config) {
        try {
            log.info("开始测试Emby配置: id={}, serverUrl={}, username={}, hasApiKey={}",
                    config.getId(),
                    config.getServerUrl(),
                    config.getUsername(),
                    config.getApiKey() != null && !config.getApiKey().isEmpty());

            // 如果有 ID，从数据库获取完整配置（包括真实密码）
            EmbyConfig testConfig = config;
            if (config.getId() != null) {
                testConfig = embyConfigService.getById(config.getId());
                if (testConfig == null) {
                    return Result.error("配置不存在");
                }
                log.info("从数据库获取配置: username={}, hasPassword={}",
                        testConfig.getUsername(),
                        testConfig.getPassword() != null && !testConfig.getPassword().isEmpty());
            }

            boolean success = embyConfigService.testConfig(testConfig);

            if (success) {
                log.info("Emby配置测试成功");
            } else {
                log.warn("Emby配置测试失败");
            }

            return Result.success(success);
        } catch (Exception e) {
            log.error("测试Emby配置异常: {}", e.getMessage(), e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }

    /**
     * 启用/禁用配置
     */
    @PutMapping("/{id}/toggle")
    public Result<Boolean> toggleConfig(@PathVariable Long id) {
        EmbyConfig config = embyConfigService.getById(id);
        if (config == null) {
            return Result.error("配置不存在");
        }

        config.setEnabled(!config.getEnabled());
        boolean success = embyConfigService.updateById(config);
        return Result.success(success);
    }
}
