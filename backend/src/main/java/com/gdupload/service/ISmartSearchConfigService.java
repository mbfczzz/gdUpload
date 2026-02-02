package com.gdupload.service;

import com.gdupload.entity.SmartSearchConfig;

import java.util.List;
import java.util.Map;

/**
 * 智能搜索配置服务接口
 */
public interface ISmartSearchConfigService {

    /**
     * 获取所有配置
     *
     * @param userId 用户ID
     * @return 配置列表
     */
    List<SmartSearchConfig> getAllConfigs(String userId);

    /**
     * 根据类型获取配置
     *
     * @param userId     用户ID
     * @param configType 配置类型
     * @return 配置列表
     */
    List<SmartSearchConfig> getConfigsByType(String userId, String configType);

    /**
     * 保存配置
     *
     * @param config 配置对象
     * @return 是否成功
     */
    boolean saveConfig(SmartSearchConfig config);

    /**
     * 批量保存配置
     *
     * @param configs 配置列表
     * @return 是否成功
     */
    boolean batchSaveConfigs(List<SmartSearchConfig> configs);

    /**
     * 删除配置
     *
     * @param id 配置ID
     * @return 是否成功
     */
    boolean deleteConfig(Long id);

    /**
     * 获取完整配置（合并所有类型）
     *
     * @param userId 用户ID
     * @return 完整配置Map
     */
    Map<String, Object> getFullConfig(String userId);

    /**
     * 保存完整配置（拆分并保存）
     *
     * @param userId     用户ID
     * @param configData 完整配置数据
     * @return 是否成功
     */
    boolean saveFullConfig(String userId, Map<String, Object> configData);
}
