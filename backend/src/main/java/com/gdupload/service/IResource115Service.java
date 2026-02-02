package com.gdupload.service;

import com.gdupload.entity.Resource115;

/**
 * 115资源服务接口
 */
public interface IResource115Service {

    /**
     * 智能搜索匹配资源
     * 优先使用 TMDB ID 精确匹配，如果没有匹配则使用名称模糊匹配
     *
     * @param tmdbId TMDB ID（优先匹配）
     * @param name 媒体项名称
     * @param originalTitle 原始名称
     * @param year 年份
     * @return 匹配的资源，如果没有匹配则返回null
     */
    Resource115 smartSearch(String tmdbId, String name, String originalTitle, Integer year);
}
