package com.gdupload.service;

/**
 * TMDB服务接口
 */
public interface ITmdbService {

    /**
     * 通过名称搜索TMDB ID
     *
     * @param name 名称
     * @param year 年份（可选）
     * @param type 类型（movie 或 tv）
     * @return TMDB ID，如果未找到返回null
     */
    String searchTmdbId(String name, Integer year, String type);

    /**
     * 批量补充115资源的TMDB ID
     *
     * @return 补充成功的数量
     */
    int batchFillTmdbIds();
}
