package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.entity.SmartSearchConfig;
import com.gdupload.service.ISmartSearchConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 智能搜索配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/smart-search-config")
public class SmartSearchConfigController {

    @Autowired
    private ISmartSearchConfigService configService;

    /**
     * 获取完整配置
     */
    @GetMapping("/full")
    public Result<Map<String, Object>> getFullConfig() {
        try {
            String userId = "default"; // 暂时使用默认用户
            Map<String, Object> config = configService.getFullConfig(userId);
            return Result.success(config);
        } catch (Exception e) {
            log.error("获取配置失败", e);
            return Result.error("获取配置失败: " + e.getMessage());
        }
    }

    /**
     * 保存完整配置
     */
    @PostMapping("/full")
    public Result<String> saveFullConfig(@RequestBody Map<String, Object> configData) {
        try {
            String userId = "default"; // 暂时使用默认用户
            boolean success = configService.saveFullConfig(userId, configData);
            if (success) {
                return Result.success("保存成功");
            } else {
                return Result.error("保存失败");
            }
        } catch (Exception e) {
            log.error("保存配置失败", e);
            return Result.error("保存配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有配置
     */
    @GetMapping("/list")
    public Result<List<SmartSearchConfig>> getAllConfigs() {
        try {
            String userId = "default";
            List<SmartSearchConfig> configs = configService.getAllConfigs(userId);
            return Result.success(configs);
        } catch (Exception e) {
            log.error("获取配置列表失败", e);
            return Result.error("获取配置列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据类型获取配置
     */
    @GetMapping("/type/{configType}")
    public Result<List<SmartSearchConfig>> getConfigsByType(@PathVariable String configType) {
        try {
            String userId = "default";
            List<SmartSearchConfig> configs = configService.getConfigsByType(userId, configType);
            return Result.success(configs);
        } catch (Exception e) {
            log.error("获取配置失败", e);
            return Result.error("获取配置失败: " + e.getMessage());
        }
    }

    /**
     * 保存单个配置
     */
    @PostMapping("/save")
    public Result<String> saveConfig(@RequestBody SmartSearchConfig config) {
        try {
            if (config.getUserId() == null || config.getUserId().isEmpty()) {
                config.setUserId("default");
            }
            boolean success = configService.saveConfig(config);
            if (success) {
                return Result.success("保存成功");
            } else {
                return Result.error("保存失败");
            }
        } catch (Exception e) {
            log.error("保存配置失败", e);
            return Result.error("保存配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteConfig(@PathVariable Long id) {
        try {
            boolean success = configService.deleteConfig(id);
            if (success) {
                return Result.success("删除成功");
            } else {
                return Result.error("删除失败");
            }
        } catch (Exception e) {
            log.error("删除配置失败", e);
            return Result.error("删除配置失败: " + e.getMessage());
        }
    }
}
