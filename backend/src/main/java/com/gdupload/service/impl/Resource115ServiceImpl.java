package com.gdupload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdupload.entity.Resource115;
import com.gdupload.mapper.Resource115Mapper;
import com.gdupload.service.IResource115Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Comparator;

/**
 * 115资源服务实现
 */
@Slf4j
@Service
public class Resource115ServiceImpl implements IResource115Service {

    @Autowired
    private Resource115Mapper resource115Mapper;

    @Override
    public Resource115 smartSearch(String tmdbId, String name, String originalTitle, Integer year) {
        log.info("智能搜索115资源: tmdbId={}, name={}, originalTitle={}, year={}", tmdbId, name, originalTitle, year);

        // 1. 优先使用 TMDB ID 精确匹配
        if (tmdbId != null && !tmdbId.isEmpty()) {
            log.info("尝试使用 TMDB ID 精确匹配: {}", tmdbId);
            LambdaQueryWrapper<Resource115> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Resource115::getTmdbId, tmdbId);
            Resource115 result = resource115Mapper.selectOne(wrapper);

            if (result != null) {
                log.info("TMDB ID 精确匹配成功: [{}]", result.getName());
                return result;
            } else {
                log.info("TMDB ID 未匹配到资源，将使用名称模糊匹配");
            }
        }

        // 2. 如果 TMDB ID 没有匹配，使用名称模糊匹配
        return smartSearchByName(name, originalTitle, year);
    }

    /**
     * 使用名称进行智能搜索
     */
    private Resource115 smartSearchByName(String name, String originalTitle, Integer year) {
        log.info("使用名称智能搜索: name={}, originalTitle={}, year={}", name, originalTitle, year);

        // 1. 构建搜索关键词列表
        List<String> searchTerms = new ArrayList<>();

        // 添加主名称
        if (name != null && !name.isEmpty()) {
            searchTerms.add(name);
            // 添加清理后的名称（去除年份）
            String cleanName = removeYear(name);
            if (!cleanName.equals(name)) {
                searchTerms.add(cleanName);
            }
        }

        // 添加原始名称
        if (originalTitle != null && !originalTitle.isEmpty()) {
            searchTerms.add(originalTitle);
            String cleanOriginal = removeYear(originalTitle);
            if (!cleanOriginal.equals(originalTitle)) {
                searchTerms.add(cleanOriginal);
            }
        }

        log.info("搜索关键词: {}", searchTerms);

        // 2. 获取所有可能匹配的资源
        List<Resource115> allResources = resource115Mapper.selectList(null);
        if (allResources.isEmpty()) {
            log.info("数据库中没有115资源");
            return null;
        }

        log.info("数据库中共有 {} 个资源", allResources.size());

        // 3. 计算每个资源的匹配分数
        Map<Resource115, Integer> scoreMap = new HashMap<>();

        for (Resource115 resource : allResources) {
            int score = calculateMatchScore(resource, searchTerms, year);
            if (score > 0) {
                scoreMap.put(resource, score);
                log.info("资源 [{}] 匹配分数: {}", resource.getName(), score);
            }
        }

        // 4. 如果没有匹配的资源
        if (scoreMap.isEmpty()) {
            log.info("未找到匹配的115资源");
            return null;
        }

        // 5. 按分数排序，返回最高分的资源
        Resource115 bestMatch = scoreMap.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);

        if (bestMatch != null) {
            int bestScore = scoreMap.get(bestMatch);
            log.info("最佳匹配: [{}], 分数: {}", bestMatch.getName(), bestScore);

            // 设置最低分数阈值，避免误匹配
            // 完全匹配至少1000分，包含匹配至少500分
            if (bestScore < 500) {
                log.info("最佳匹配分数 {} 低于阈值 500，不返回结果", bestScore);
                return null;
            }
        }

        return bestMatch;
    }

    /**
     * 计算匹配分数
     * 分数越高，匹配度越好
     */
    private int calculateMatchScore(Resource115 resource, List<String> searchTerms, Integer year) {
        String resourceName = resource.getName();
        if (resourceName == null || resourceName.isEmpty()) {
            return 0;
        }

        int totalScore = 0;

        // 1. 完全匹配（最高分）
        for (String term : searchTerms) {
            if (resourceName.equals(term)) {
                totalScore += 10000; // 大幅提高完全匹配分数
                log.debug("  完全匹配: {} == {}", resourceName, term);
                return totalScore; // 完全匹配直接返回
            }
        }

        // 2. 包含匹配（高分）
        for (String term : searchTerms) {
            if (resourceName.contains(term)) {
                totalScore += 5000; // 大幅提高包含匹配分数
                log.debug("  包含匹配: {} contains {}", resourceName, term);
            }
        }

        // 3. 反向包含匹配（搜索词包含资源名）
        String cleanResourceName = removeYear(resourceName);
        for (String term : searchTerms) {
            if (term.contains(cleanResourceName) && cleanResourceName.length() >= 3) {
                totalScore += 3000;
                log.debug("  反向包含匹配: {} contains {}", term, cleanResourceName);
            }
        }

        // 4. 去除特殊字符后匹配
        String simplifiedResource = simplifyString(resourceName);
        for (String term : searchTerms) {
            String simplifiedTerm = simplifyString(term);
            if (simplifiedResource.equals(simplifiedTerm)) {
                totalScore += 2000;
                log.debug("  简化完全匹配: {} == {}", simplifiedResource, simplifiedTerm);
            } else if (simplifiedResource.contains(simplifiedTerm) && simplifiedTerm.length() >= 3) {
                totalScore += 1000;
                log.debug("  简化包含匹配: {} contains {}", simplifiedResource, simplifiedTerm);
            }
        }

        // 5. 年份匹配（加分项）
        if (year != null) {
            String yearStr = year.toString();
            if (resourceName.contains(yearStr)) {
                totalScore += 500;
                log.debug("  年份匹配: {}", yearStr);
            }
        }

        // 6. 主要词匹配（至少4个字符的词）
        for (String term : searchTerms) {
            if (term.length() >= 4) {
                String[] words = extractMainWords(term);
                for (String word : words) {
                    if (word.length() >= 4 && resourceName.contains(word)) {
                        totalScore += 100;
                        log.debug("  主要词匹配: {}", word);
                    }
                }
            }
        }

        return totalScore;
    }

    /**
     * 提取主要词汇（至少4个字符）
     */
    private String[] extractMainWords(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }

        List<String> words = new ArrayList<>();
        String simplified = simplifyString(text);

        // 提取连续的中文词（4个字符以上）
        for (int len = Math.min(simplified.length(), 10); len >= 4; len--) {
            for (int i = 0; i <= simplified.length() - len; i++) {
                String word = simplified.substring(i, i + len);
                if (word.matches("[\\u4e00-\\u9fa5]+")) {
                    words.add(word);
                }
            }
        }

        return words.toArray(new String[0]);
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
     * 简化字符串（去除特殊字符、空格，统一大小写）
     */
    private String simplifyString(String text) {
        if (text == null) {
            return "";
        }
        // 去除所有非中文、非英文、非数字的字符
        String simplified = text.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", "");
        return simplified.toLowerCase();
    }
}
