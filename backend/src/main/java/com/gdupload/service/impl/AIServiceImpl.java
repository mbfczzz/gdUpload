package com.gdupload.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.gdupload.service.IAIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI服务实现类
 * 支持 Claude、OpenAI、Ollama
 */
@Slf4j
@Service
public class AIServiceImpl implements IAIService {

    @Value("${app.ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${app.ai.provider:claude}")
    private String aiProvider;

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.api-url:}")
    private String apiUrl;

    @Value("${app.ai.model:claude-3-5-sonnet-20241022}")
    private String model;

    @Value("${app.ai.max-tokens:1024}")
    private int maxTokens;

    @Value("${app.ai.temperature:0.7}")
    private double temperature;

    @Override
    public Map<String, Object> selectBestResource(Map<String, Object> movieInfo, List<Map<String, Object>> resources) {
        if (!isAvailable()) {
            log.warn("AI服务未启用或配置不完整，使用规则筛选");
            return fallbackSelection(resources);
        }

        try {
            String prompt = buildPrompt(movieInfo, resources);
            String aiResponse = callAI(prompt);
            return parseAIResponse(aiResponse);
        } catch (Exception e) {
            log.error("AI筛选失败，降级使用规则筛选", e);
            return fallbackSelection(resources);
        }
    }

    @Override
    public boolean isAvailable() {
        if (!aiEnabled) {
            return false;
        }

        // Ollama不需要API Key
        if ("ollama".equalsIgnoreCase(aiProvider)) {
            return apiUrl != null && !apiUrl.isEmpty();
        }

        // Claude和OpenAI需要API Key
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * 构建AI提示词
     */
    private String buildPrompt(Map<String, Object> movieInfo, List<Map<String, Object>> resources) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个专业的影视资源筛选助手。\n\n");
        prompt.append("电影信息：\n");
        prompt.append("- 名称：").append(movieInfo.get("name")).append("\n");
        if (movieInfo.get("originalTitle") != null) {
            prompt.append("- 原始名称：").append(movieInfo.get("originalTitle")).append("\n");
        }
        if (movieInfo.get("productionYear") != null) {
            prompt.append("- 年份：").append(movieInfo.get("productionYear")).append("\n");
        }
        if (movieInfo.get("communityRating") != null) {
            prompt.append("- 评分：").append(movieInfo.get("communityRating")).append("\n");
        }
        prompt.append("\n");

        prompt.append("候选资源列表：\n");
        for (int i = 0; i < resources.size(); i++) {
            Map<String, Object> resource = resources.get(i);
            prompt.append(String.format("%d. ID: %s\n", i + 1, resource.get("id")));
            prompt.append(String.format("   标题: %s\n", resource.get("title")));
            if (resource.get("size") != null) {
                prompt.append(String.format("   大小: %s\n", resource.get("size")));
            }
            if (resource.get("resolution") != null) {
                prompt.append(String.format("   分辨率: %s\n", resource.get("resolution")));
            }
            if (resource.get("matchScore") != null) {
                prompt.append(String.format("   匹配分数: %.1f\n", resource.get("matchScore")));
            }
            if (resource.get("isValid") != null) {
                prompt.append(String.format("   链接有效: %s\n", resource.get("isValid")));
            }
            prompt.append("\n");
        }

        prompt.append("请分析以上资源，选择最适合的一个。\n\n");
        prompt.append("**重要规则**：\n");
        prompt.append("1. 如果资源标题与电影名称完全不匹配，必须返回 null（表示丢弃）\n");
        prompt.append("2. 只有标题明确匹配电影名称的资源才能被推荐\n");
        prompt.append("3. 考虑因素：标题匹配度、分辨率、文件大小、链接有效性\n\n");

        prompt.append("请以JSON格式返回结果：\n");
        prompt.append("{\n");
        prompt.append("  \"best_resource_id\": \"资源ID或null\",\n");
        prompt.append("  \"reason\": \"选择理由或拒绝理由\",\n");
        prompt.append("  \"confidence\": 0.95,\n");
        prompt.append("  \"title_match\": true或false\n");
        prompt.append("}\n\n");
        prompt.append("如果所有资源都不匹配，返回：\n");
        prompt.append("{\n");
        prompt.append("  \"best_resource_id\": null,\n");
        prompt.append("  \"reason\": \"所有资源标题都与电影名称不匹配\",\n");
        prompt.append("  \"confidence\": 0.0,\n");
        prompt.append("  \"title_match\": false\n");
        prompt.append("}");

        return prompt.toString();
    }

    /**
     * 调用AI API
     */
    private String callAI(String prompt) throws Exception {
        switch (aiProvider.toLowerCase()) {
            case "claude":
                return callClaude(prompt);
            case "openai":
                return callOpenAI(prompt);
            case "ollama":
                return callOllama(prompt);
            default:
                throw new IllegalArgumentException("不支持的AI提供商: " + aiProvider);
        }
    }

    /**
     * 调用Claude API
     */
    private String callClaude(String prompt) throws Exception {
        String url = "https://api.anthropic.com/v1/messages";

        JSONObject requestBody = new JSONObject();
        requestBody.set("model", model);
        requestBody.set("max_tokens", maxTokens);

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.set("messages", messages);

        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .body(requestBody.toString())
                .timeout(30000)
                .execute();

        if (!response.isOk()) {
            throw new RuntimeException("Claude API调用失败: " + response.body());
        }

        JSONObject result = JSONUtil.parseObj(response.body());
        return result.getJSONArray("content").getJSONObject(0).getStr("text");
    }

    /**
     * 调用OpenAI API
     */
    private String callOpenAI(String prompt) throws Exception {
        String url = "https://api.openai.com/v1/chat/completions";

        JSONObject requestBody = new JSONObject();
        requestBody.set("model", model);
        requestBody.set("max_tokens", maxTokens);
        requestBody.set("temperature", temperature);

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.set("messages", messages);

        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .body(requestBody.toString())
                .timeout(30000)
                .execute();

        if (!response.isOk()) {
            throw new RuntimeException("OpenAI API调用失败: " + response.body());
        }

        JSONObject result = JSONUtil.parseObj(response.body());
        return result.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getStr("content");
    }

    /**
     * 调用Ollama API
     */
    private String callOllama(String prompt) throws Exception {
        String url = apiUrl + "/api/generate";

        JSONObject requestBody = new JSONObject();
        requestBody.set("model", model);
        requestBody.set("prompt", prompt);
        requestBody.set("stream", false);

        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(60000)
                .execute();

        if (!response.isOk()) {
            throw new RuntimeException("Ollama API调用失败: " + response.body());
        }

        JSONObject result = JSONUtil.parseObj(response.body());
        return result.getStr("response");
    }

    /**
     * 解析AI响应
     */
    private Map<String, Object> parseAIResponse(String aiResponse) {
        try {
            // 尝试从响应中提取JSON
            String jsonStr = aiResponse;

            // 如果响应包含markdown代码块，提取其中的JSON
            if (aiResponse.contains("```json")) {
                int start = aiResponse.indexOf("```json") + 7;
                int end = aiResponse.indexOf("```", start);
                jsonStr = aiResponse.substring(start, end).trim();
            } else if (aiResponse.contains("```")) {
                int start = aiResponse.indexOf("```") + 3;
                int end = aiResponse.indexOf("```", start);
                jsonStr = aiResponse.substring(start, end).trim();
            }

            // 查找第一个 { 和最后一个 }
            int firstBrace = jsonStr.indexOf('{');
            int lastBrace = jsonStr.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                jsonStr = jsonStr.substring(firstBrace, lastBrace + 1);
            }

            JSONObject json = JSONUtil.parseObj(jsonStr);

            Map<String, Object> result = new HashMap<>();

            // 检查是否匹配
            Boolean titleMatch = json.getBool("title_match", true);
            String bestResourceId = json.getStr("best_resource_id");

            // 如果AI判断不匹配或返回null，返回null结果
            if (!titleMatch || "null".equals(bestResourceId) || bestResourceId == null) {
                result.put("best_resource_id", null);
                result.put("reason", json.getStr("reason", "AI判断资源标题与电影名称不匹配"));
                result.put("confidence", 0.0);
                result.put("title_match", false);
                log.warn("AI判断所有资源都不匹配: {}", json.getStr("reason"));
                return result;
            }

            result.put("best_resource_id", bestResourceId);
            result.put("reason", json.getStr("reason"));
            result.put("confidence", json.getDouble("confidence", 0.85));
            result.put("title_match", titleMatch);

            return result;
        } catch (Exception e) {
            log.error("解析AI响应失败: {}", aiResponse, e);
            throw new RuntimeException("解析AI响应失败", e);
        }
    }

    /**
     * 降级方案：使用规则筛选
     */
    private Map<String, Object> fallbackSelection(List<Map<String, Object>> resources) {
        // 选择评分最高的
        Map<String, Object> bestResource = resources.stream()
                .max((r1, r2) -> {
                    double score1 = getScore(r1);
                    double score2 = getScore(r2);
                    return Double.compare(score1, score2);
                })
                .orElse(resources.get(0));

        Map<String, Object> result = new HashMap<>();
        result.put("best_resource_id", bestResource.get("id"));
        result.put("reason", "该资源标题匹配度高，分辨率优秀，文件大小合理，是最佳选择（规则筛选）");
        result.put("confidence", 0.75);

        return result;
    }

    private double getScore(Map<String, Object> resource) {
        Object score = resource.get("matchScore");
        if (score instanceof Number) {
            return ((Number) score).doubleValue();
        }
        return 0.0;
    }
}
