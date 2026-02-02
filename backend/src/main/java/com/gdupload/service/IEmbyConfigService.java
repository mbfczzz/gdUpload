package com.gdupload.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gdupload.entity.EmbyConfig;

import java.util.List;

/**
 * Emby配置服务接口
 */
public interface IEmbyConfigService extends IService<EmbyConfig> {

    /**
     * 获取默认配置
     */
    EmbyConfig getDefaultConfig();

    /**
     * 获取所有配置列表
     */
    List<EmbyConfig> getAllConfigs();

    /**
     * 设置默认配置
     */
    boolean setDefaultConfig(Long id);

    /**
     * 测试配置连接
     */
    boolean testConfig(EmbyConfig config);

    /**
     * 保存或更新配置
     */
    boolean saveOrUpdateConfig(EmbyConfig config);
}
