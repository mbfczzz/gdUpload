package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.service.IAIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Telegram搜索控制器
 * 提供链接验证和AI筛选功能
 */
@Slf4j
@RestController
@RequestMapping("/telegramsearch")
public class TelegramSearchController {

    @Autowired
    private IAIService aiService;

    /**
     * 批量验证链接有效性
     */
    @PostMapping("/batch-validate")
    public Result<List<Map<String, Object>>> batchValidateLinks(@RequestBody Map<String, Object> params) {
        log.info("批量验证链接有效性");

        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) params.get("urls");

        if (urls == null || urls.isEmpty()) {
            return Result.error("链接列表不能为空");
        }

        List<Map<String, Object>> results = new ArrayList<>();

        for (String url : urls) {
            Map<String, Object> validation = new HashMap<>();
            validation.put("url", url);

            try {
                // TODO: 实际验证逻辑
                // 这里需要调用阿里云盘或天翼云盘的API来验证链接
                // 暂时返回模拟数据

                // 简单判断：阿里云盘链接格式
                boolean isValid = url.contains("alipan.com") || url.contains("aliyundrive.com") || url.contains("cloud.189.cn");

                validation.put("valid", isValid);
                validation.put("message", isValid ? "链接格式正确" : "链接格式错误");
                validation.put("has_files", isValid); // 假设有效链接都有文件
                validation.put("file_count", isValid ? (int)(Math.random() * 10 + 1) : 0);

            } catch (Exception e) {
                log.error("验证链接失败: {}", url, e);
                validation.put("valid", false);
                validation.put("message", "验证失败: " + e.getMessage());
                validation.put("has_files", false);
                validation.put("file_count", 0);
            }

            results.add(validation);
        }

        log.info("验证完成，共 {} 个链接，有效 {} 个",
                urls.size(),
                results.stream().filter(r -> (Boolean) r.get("valid")).count());

        return Result.success(results);
    }

    /**
     * AI智能筛选最佳资源
     */
    @PostMapping("/ai-select")
    public Result<Map<String, Object>> aiSelectBestResource(@RequestBody Map<String, Object> params) {
        log.info("AI智能筛选最佳资源");

        @SuppressWarnings("unchecked")
        Map<String, Object> movieInfo = (Map<String, Object>) params.get("movie_info");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) params.get("resources");

        if (movieInfo == null || resources == null || resources.isEmpty()) {
            return Result.error("参数错误");
        }

        try {
            String movieName = (String) movieInfo.get("name");
            log.info("为电影 {} 筛选最佳资源，候选资源数: {}", movieName, resources.size());

            // 调用AI服务
            Map<String, Object> result = aiService.selectBestResource(movieInfo, resources);

            log.info("AI推荐资源ID: {}, 理由: {}", result.get("best_resource_id"), result.get("reason"));

            return Result.success(result);

        } catch (Exception e) {
            log.error("AI筛选失败", e);
            return Result.error("AI筛选失败: " + e.getMessage());
        }
    }
}
