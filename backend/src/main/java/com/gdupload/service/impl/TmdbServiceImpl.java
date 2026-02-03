package com.gdupload.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdupload.entity.Resource115;
import com.gdupload.mapper.Resource115Mapper;
import com.gdupload.service.ISmartSearchConfigService;
import com.gdupload.service.ITmdbService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TMDB服务实现
 */
@Slf4j
@Service
public class TmdbServiceImpl implements ITmdbService {

    @Autowired
    private Resource115Mapper resource115Mapper;

    @Autowired
    private ISmartSearchConfigService smartSearchConfigService;

    @Value("${tmdb.api.key:}")
    private String tmdbApiKey;

    private static final String TMDB_SEARCH_URL = "https://api.themoviedb.org/3/search/%s?api_key=%s&query=%s&language=%s";

    @Override
    public String searchTmdbId(String name, Integer year, String type) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        // 优先从数据库配置读取 API Key
        String apiKey = getTmdbApiKey();
        String language = getTmdbLanguage();

        // 如果数据库没有配置，使用 application.yml 的配置
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = tmdbApiKey;
        }

        // 如果还是没有配置 API Key，返回 null
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("TMDB API Key 未配置，无法搜索");
            return null;
        }

        // 清理名称（去除年份括号）
        String cleanName = removeYear(name);

        // 确定搜索类型（movie 或 tv）
        String searchType = determineType(type);

        log.info("搜索 TMDB ID: name={}, year={}, type={}, language={}", cleanName, year, searchType, language);

        try {
            // 构建搜索 URL
            String url = String.format(TMDB_SEARCH_URL, searchType, apiKey, cleanName, language);
            if (year != null) {
                url += "&year=" + year;
            }

            // 发送请求
            String response = HttpRequest.get(url)
                    .timeout(10000)
                    .execute()
                    .body();

            // 解析响应
            JSONObject json = JSONUtil.parseObj(response);
            JSONArray results = json.getJSONArray("results");

            if (results != null && !results.isEmpty()) {
                // 返回第一个结果的 ID
                JSONObject firstResult = results.getJSONObject(0);
                Integer tmdbId = firstResult.getInt("id");
                String title = firstResult.getStr("title");
                if (title == null) {
                    title = firstResult.getStr("name"); // TV 剧集使用 name 字段
                }

                log.info("找到 TMDB ID: {} - {}", tmdbId, title);
                return tmdbId.toString();
            } else {
                log.info("未找到匹配的 TMDB ID");
                return null;
            }
        } catch (Exception e) {
            log.error("搜索 TMDB ID 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从数据库配置获取 TMDB API Key
     */
    private String getTmdbApiKey() {
        try {
            Map<String, Object> config = smartSearchConfigService.getFullConfig("default");
            if (config != null && config.containsKey("tmdbApiKey")) {
                return (String) config.get("tmdbApiKey");
            }
        } catch (Exception e) {
            log.debug("从数据库读取 TMDB API Key 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从数据库配置获取 TMDB 语言设置
     */
    private String getTmdbLanguage() {
        try {
            Map<String, Object> config = smartSearchConfigService.getFullConfig("default");
            if (config != null && config.containsKey("tmdbLanguage")) {
                return (String) config.get("tmdbLanguage");
            }
        } catch (Exception e) {
            log.debug("从数据库读取 TMDB 语言设置失败: {}", e.getMessage());
        }
        return "zh-CN"; // 默认简体中文
    }

    @Override
    public int batchFillTmdbIds() {
        log.info("开始批量补充 TMDB ID");

        // 查询所有没有 TMDB ID 的资源
        LambdaQueryWrapper<Resource115> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(Resource115::getTmdbId)
                .or()
                .eq(Resource115::getTmdbId, "");

        List<Resource115> resources = resource115Mapper.selectList(wrapper);
        log.info("找到 {} 个需要补充 TMDB ID 的资源", resources.size());

        int successCount = 0;
        int failedCount = 0;

        for (int i = 0; i < resources.size(); i++) {
            Resource115 resource = resources.get(i);
            log.info("处理 [{}/{}]: {}", i + 1, resources.size(), resource.getName());

            try {
                // 从名称中提取年份
                Integer year = extractYear(resource.getName());

                // 搜索 TMDB ID
                String tmdbId = searchTmdbId(resource.getName(), year, resource.getType());

                if (tmdbId != null) {
                    // 更新数据库
                    resource.setTmdbId(tmdbId);
                    resource115Mapper.updateById(resource);
                    successCount++;
                    log.info("✓ 成功: {} -> TMDB ID: {}", resource.getName(), tmdbId);
                } else {
                    failedCount++;
                    log.warn("✗ 失败: {} - 未找到匹配", resource.getName());
                }

                // 避免请求过快，休眠 250ms
                Thread.sleep(250);

            } catch (Exception e) {
                failedCount++;
                log.error("✗ 异常: {} - {}", resource.getName(), e.getMessage());
            }
        }

        log.info("批量补充完成: 成功 {}, 失败 {}", successCount, failedCount);
        return successCount;
    }

    /**
     * 去除年份
     */
    private String removeYear(String text) {
        if (text == null) {
            return "";
        }
        // 去除 (年份) 和 （年份） 格式
        String result = text.replaceAll("\\s*[\\(（]\\d{4}[\\)）]\\s*", " ");
        return result.trim();
    }

    /**
     * 从名称中提取年份
     */
    private Integer extractYear(String text) {
        if (text == null) {
            return null;
        }

        // 匹配 (2020) 或 （2020） 格式
        Pattern pattern = Pattern.compile("[\\(（](\\d{4})[\\)）]");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return null;
    }

    /**
     * 确定搜索类型
     */
    private String determineType(String type) {
        if (type == null) {
            return "tv"; // 默认为电视剧
        }

        // 根据类型判断
        if (type.contains("电影") || type.equalsIgnoreCase("movie")) {
            return "movie";
        } else {
            return "tv";
        }
    }
}
