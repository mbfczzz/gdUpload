package com.gdupload.service;

import java.util.List;
import java.util.Map;

/**
 * AI服务接口
 */
public interface IAIService {

    /**
     * 使用AI筛选最佳资源
     *
     * @param movieInfo 电影信息
     * @param resources 候选资源列表
     * @return AI推荐结果
     */
    Map<String, Object> selectBestResource(Map<String, Object> movieInfo, List<Map<String, Object>> resources);

    /**
     * 检查AI服务是否可用
     *
     * @return 是否可用
     */
    boolean isAvailable();
}
