package com.gdupload.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdupload.entity.SmartSearchConfig;
import com.gdupload.mapper.SmartSearchConfigMapper;
import com.gdupload.service.ISmartSearchConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 智能搜索配置服务实现
 */
@Slf4j
@Service
public class SmartSearchConfigServiceImpl implements ISmartSearchConfigService {

    @Autowired
    private SmartSearchConfigMapper configMapper;

    @Override
    public List<SmartSearchConfig> getAllConfigs(String userId) {
        LambdaQueryWrapper<SmartSearchConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmartSearchConfig::getUserId, userId)
                .eq(SmartSearchConfig::getIsActive, true)
                .orderByAsc(SmartSearchConfig::getConfigType)
                .orderByAsc(SmartSearchConfig::getId);
        return configMapper.selectList(wrapper);
    }

    @Override
    public List<SmartSearchConfig> getConfigsByType(String userId, String configType) {
        LambdaQueryWrapper<SmartSearchConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmartSearchConfig::getUserId, userId)
                .eq(SmartSearchConfig::getConfigType, configType)
                .eq(SmartSearchConfig::getIsActive, true)
                .orderByAsc(SmartSearchConfig::getId);
        return configMapper.selectList(wrapper);
    }

    @Override
    public boolean saveConfig(SmartSearchConfig config) {
        if (config.getId() != null) {
            return configMapper.updateById(config) > 0;
        } else {
            return configMapper.insert(config) > 0;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchSaveConfigs(List<SmartSearchConfig> configs) {
        for (SmartSearchConfig config : configs) {
            if (!saveConfig(config)) {
                throw new RuntimeException("保存配置失败: " + config.getConfigName());
            }
        }
        return true;
    }

    @Override
    public boolean deleteConfig(Long id) {
        return configMapper.deleteById(id) > 0;
    }

    @Override
    public Map<String, Object> getFullConfig(String userId) {
        List<SmartSearchConfig> allConfigs = getAllConfigs(userId);

        Map<String, Object> fullConfig = new HashMap<>();
        List<Map<String, Object>> cloudConfigs = new ArrayList<>();
        Map<String, Object> weights = null;
        Boolean aiEnabled = null;
        Boolean validateLinks = null;
        Integer maxValidationCount = null;
        Integer validationTimeout = null;
        Boolean debugMode = null;

        // AI扩展配置变量
        String aiProvider = null;
        String aiApiKey = null;
        String aiApiUrl = null;
        String aiModel = null;
        Object aiMaxTokens = null;
        Object aiTemperature = null;
        Boolean archiveAiEnabled = null;

        // TMDB配置变量
        Boolean tmdbEnabled = null;
        String tmdbApiKey = null;
        String tmdbApiUrl = null;
        String tmdbLanguage = null;
        Integer tmdbTimeout = null;
        Boolean tmdbAutoMatch = null;

        // 115配置变量
        Boolean enable115Transfer = null;
        String cookie115 = null;
        String targetFolderId115 = null;

        // Emby配置变量
        String embyDownloadDir = null;
        String uploadDir = null;
        String gdTargetPath = null;

        // STRM配置变量
        String strmOutputPath  = null;
        String strmGdRemote    = null;
        String strmGdPath      = null;
        String strmPlayUrlBase = null;

        for (SmartSearchConfig config : allConfigs) {
            try {
                Map<String, Object> data = JSONUtil.toBean(config.getConfigData(), Map.class);

                switch (config.getConfigType()) {
                    case "cloud_config":
                        cloudConfigs.add(data);
                        break;
                    case "ai_config":
                        if (data.containsKey("aiEnabled")) {
                            aiEnabled = (Boolean) data.get("aiEnabled");
                        }
                        if (data.containsKey("validateLinks")) {
                            validateLinks = (Boolean) data.get("validateLinks");
                        }
                        if (data.containsKey("aiProvider")) {
                            aiProvider = (String) data.get("aiProvider");
                        }
                        if (data.containsKey("aiApiKey")) {
                            aiApiKey = (String) data.get("aiApiKey");
                        }
                        if (data.containsKey("aiApiUrl")) {
                            aiApiUrl = (String) data.get("aiApiUrl");
                        }
                        if (data.containsKey("aiModel")) {
                            aiModel = (String) data.get("aiModel");
                        }
                        if (data.containsKey("aiMaxTokens")) {
                            aiMaxTokens = data.get("aiMaxTokens");
                        }
                        if (data.containsKey("aiTemperature")) {
                            aiTemperature = data.get("aiTemperature");
                        }
                        if (data.containsKey("archiveAiEnabled")) {
                            archiveAiEnabled = (Boolean) data.get("archiveAiEnabled");
                        }
                        break;
                    case "search_config":
                        if (data.containsKey("weights")) {
                            weights = (Map<String, Object>) data.get("weights");
                        }
                        if (data.containsKey("maxValidationCount")) {
                            maxValidationCount = (Integer) data.get("maxValidationCount");
                        }
                        if (data.containsKey("validationTimeout")) {
                            validationTimeout = (Integer) data.get("validationTimeout");
                        }
                        if (data.containsKey("debugMode")) {
                            debugMode = (Boolean) data.get("debugMode");
                        }
                        break;
                    case "tmdb_config":
                        if (data.containsKey("tmdbEnabled")) {
                            tmdbEnabled = (Boolean) data.get("tmdbEnabled");
                        }
                        if (data.containsKey("tmdbApiKey")) {
                            tmdbApiKey = (String) data.get("tmdbApiKey");
                        }
                        if (data.containsKey("tmdbApiUrl")) {
                            tmdbApiUrl = (String) data.get("tmdbApiUrl");
                        }
                        if (data.containsKey("tmdbLanguage")) {
                            tmdbLanguage = (String) data.get("tmdbLanguage");
                        }
                        if (data.containsKey("tmdbTimeout")) {
                            tmdbTimeout = (Integer) data.get("tmdbTimeout");
                        }
                        if (data.containsKey("tmdbAutoMatch")) {
                            tmdbAutoMatch = (Boolean) data.get("tmdbAutoMatch");
                        }
                        break;
                    case "115_config":
                        if (data.containsKey("enable115Transfer")) {
                            enable115Transfer = (Boolean) data.get("enable115Transfer");
                        }
                        if (data.containsKey("cookie115")) {
                            cookie115 = (String) data.get("cookie115");
                        }
                        if (data.containsKey("targetFolderId115")) {
                            targetFolderId115 = (String) data.get("targetFolderId115");
                        }
                        break;
                    case "emby_config":
                        if (data.containsKey("embyDownloadDir")) {
                            embyDownloadDir = (String) data.get("embyDownloadDir");
                        }
                        if (data.containsKey("uploadDir")) {
                            uploadDir = (String) data.get("uploadDir");
                        }
                        if (data.containsKey("gdTargetPath")) {
                            gdTargetPath = (String) data.get("gdTargetPath");
                        }
                        break;
                    case "strm_config":
                        if (data.containsKey("strmOutputPath"))  strmOutputPath  = (String) data.get("strmOutputPath");
                        if (data.containsKey("strmGdRemote"))    strmGdRemote    = (String) data.get("strmGdRemote");
                        if (data.containsKey("strmGdPath"))      strmGdPath      = (String) data.get("strmGdPath");
                        if (data.containsKey("strmPlayUrlBase")) strmPlayUrlBase = (String) data.get("strmPlayUrlBase");
                        break;
                }
            } catch (Exception e) {
                log.error("解析配置失败: {}", config.getConfigName(), e);
            }
        }

        fullConfig.put("cloudConfigs", cloudConfigs);
        if (weights != null) {
            fullConfig.put("weights", weights);
        }
        if (aiEnabled != null) {
            fullConfig.put("aiEnabled", aiEnabled);
        }
        if (validateLinks != null) {
            fullConfig.put("validateLinks", validateLinks);
        }
        if (aiProvider != null) {
            fullConfig.put("aiProvider", aiProvider);
        }
        if (aiApiKey != null) {
            fullConfig.put("aiApiKey", aiApiKey);
        }
        if (aiApiUrl != null) {
            fullConfig.put("aiApiUrl", aiApiUrl);
        }
        if (aiModel != null) {
            fullConfig.put("aiModel", aiModel);
        }
        if (aiMaxTokens != null) {
            fullConfig.put("aiMaxTokens", aiMaxTokens);
        }
        if (aiTemperature != null) {
            fullConfig.put("aiTemperature", aiTemperature);
        }
        if (archiveAiEnabled != null) {
            fullConfig.put("archiveAiEnabled", archiveAiEnabled);
        }
        if (maxValidationCount != null) {
            fullConfig.put("maxValidationCount", maxValidationCount);
        }
        if (validationTimeout != null) {
            fullConfig.put("validationTimeout", validationTimeout);
        }
        if (debugMode != null) {
            fullConfig.put("debugMode", debugMode);
        }

        // 添加TMDB配置到返回结果
        if (tmdbEnabled != null) {
            fullConfig.put("tmdbEnabled", tmdbEnabled);
        }
        if (tmdbApiKey != null) {
            fullConfig.put("tmdbApiKey", tmdbApiKey);
        }
        if (tmdbApiUrl != null) {
            fullConfig.put("tmdbApiUrl", tmdbApiUrl);
        }
        if (tmdbLanguage != null) {
            fullConfig.put("tmdbLanguage", tmdbLanguage);
        }
        if (tmdbTimeout != null) {
            fullConfig.put("tmdbTimeout", tmdbTimeout);
        }
        if (tmdbAutoMatch != null) {
            fullConfig.put("tmdbAutoMatch", tmdbAutoMatch);
        }

        // 添加115配置到返回结果
        if (enable115Transfer != null) {
            fullConfig.put("enable115Transfer", enable115Transfer);
        }
        if (cookie115 != null) {
            fullConfig.put("cookie115", cookie115);
        }
        if (targetFolderId115 != null) {
            fullConfig.put("targetFolderId115", targetFolderId115);
        }

        // 添加Emby配置到返回结果
        if (embyDownloadDir != null) {
            fullConfig.put("embyDownloadDir", embyDownloadDir);
        }
        if (uploadDir != null) {
            fullConfig.put("uploadDir", uploadDir);
        }
        if (gdTargetPath != null) {
            fullConfig.put("gdTargetPath", gdTargetPath);
        }

        // 添加STRM配置到返回结果
        if (strmOutputPath  != null) fullConfig.put("strmOutputPath",  strmOutputPath);
        if (strmGdRemote    != null) fullConfig.put("strmGdRemote",    strmGdRemote);
        if (strmGdPath      != null) fullConfig.put("strmGdPath",      strmGdPath);
        if (strmPlayUrlBase != null) fullConfig.put("strmPlayUrlBase", strmPlayUrlBase);

        return fullConfig;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveFullConfig(String userId, Map<String, Object> configData) {
        try {
            // 1. 删除旧配置
            LambdaQueryWrapper<SmartSearchConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SmartSearchConfig::getUserId, userId);
            configMapper.delete(wrapper);

            // 2. 保存云盘配置
            if (configData.containsKey("cloudConfigs")) {
                List<Map<String, Object>> cloudConfigs = (List<Map<String, Object>>) configData.get("cloudConfigs");
                for (int i = 0; i < cloudConfigs.size(); i++) {
                    Map<String, Object> cloudConfig = cloudConfigs.get(i);
                    SmartSearchConfig config = new SmartSearchConfig();
                    config.setUserId(userId);
                    config.setConfigName((String) cloudConfig.getOrDefault("name", "云盘配置" + (i + 1)));
                    config.setConfigType("cloud_config");
                    config.setConfigData(JSONUtil.toJsonStr(cloudConfig));
                    config.setIsActive(true);
                    config.setRemark((String) cloudConfig.get("remark"));
                    configMapper.insert(config);
                }
            }

            // 3. 保存AI配置
            Map<String, Object> aiConfig = new HashMap<>();
            if (configData.containsKey("aiEnabled")) {
                aiConfig.put("aiEnabled", configData.get("aiEnabled"));
            }
            if (configData.containsKey("validateLinks")) {
                aiConfig.put("validateLinks", configData.get("validateLinks"));
            }
            if (configData.containsKey("aiProvider")) {
                aiConfig.put("aiProvider", configData.get("aiProvider"));
            }
            if (configData.containsKey("aiApiKey")) {
                aiConfig.put("aiApiKey", configData.get("aiApiKey"));
            }
            if (configData.containsKey("aiApiUrl")) {
                aiConfig.put("aiApiUrl", configData.get("aiApiUrl"));
            }
            if (configData.containsKey("aiModel")) {
                aiConfig.put("aiModel", configData.get("aiModel"));
            }
            if (configData.containsKey("aiMaxTokens")) {
                aiConfig.put("aiMaxTokens", configData.get("aiMaxTokens"));
            }
            if (configData.containsKey("aiTemperature")) {
                aiConfig.put("aiTemperature", configData.get("aiTemperature"));
            }
            if (configData.containsKey("archiveAiEnabled")) {
                aiConfig.put("archiveAiEnabled", configData.get("archiveAiEnabled"));
            }
            if (!aiConfig.isEmpty()) {
                SmartSearchConfig config = new SmartSearchConfig();
                config.setUserId(userId);
                config.setConfigName("AI配置");
                config.setConfigType("ai_config");
                config.setConfigData(JSONUtil.toJsonStr(aiConfig));
                config.setIsActive(true);
                config.setRemark("AI和验证配置");
                configMapper.insert(config);
            }

            // 4. 保存搜索配置
            Map<String, Object> searchConfig = new HashMap<>();
            if (configData.containsKey("weights")) {
                searchConfig.put("weights", configData.get("weights"));
            }
            if (configData.containsKey("maxValidationCount")) {
                searchConfig.put("maxValidationCount", configData.get("maxValidationCount"));
            }
            if (configData.containsKey("validationTimeout")) {
                searchConfig.put("validationTimeout", configData.get("validationTimeout"));
            }
            if (configData.containsKey("debugMode")) {
                searchConfig.put("debugMode", configData.get("debugMode"));
            }
            if (!searchConfig.isEmpty()) {
                SmartSearchConfig config = new SmartSearchConfig();
                config.setUserId(userId);
                config.setConfigName("搜索权重配置");
                config.setConfigType("search_config");
                config.setConfigData(JSONUtil.toJsonStr(searchConfig));
                config.setIsActive(true);
                config.setRemark("搜索评分权重配置");
                configMapper.insert(config);
            }

            // 5. 保存TMDB配置
            Map<String, Object> tmdbConfig = new HashMap<>();
            if (configData.containsKey("tmdbEnabled")) {
                tmdbConfig.put("tmdbEnabled", configData.get("tmdbEnabled"));
            }
            if (configData.containsKey("tmdbApiKey")) {
                tmdbConfig.put("tmdbApiKey", configData.get("tmdbApiKey"));
            }
            if (configData.containsKey("tmdbApiUrl")) {
                tmdbConfig.put("tmdbApiUrl", configData.get("tmdbApiUrl"));
            }
            if (configData.containsKey("tmdbLanguage")) {
                tmdbConfig.put("tmdbLanguage", configData.get("tmdbLanguage"));
            }
            if (configData.containsKey("tmdbTimeout")) {
                tmdbConfig.put("tmdbTimeout", configData.get("tmdbTimeout"));
            }
            if (configData.containsKey("tmdbAutoMatch")) {
                tmdbConfig.put("tmdbAutoMatch", configData.get("tmdbAutoMatch"));
            }
            if (!tmdbConfig.isEmpty()) {
                SmartSearchConfig config = new SmartSearchConfig();
                config.setUserId(userId);
                config.setConfigName("TMDB配置");
                config.setConfigType("tmdb_config");
                config.setConfigData(JSONUtil.toJsonStr(tmdbConfig));
                config.setIsActive(true);
                config.setRemark("TMDB影视数据库配置");
                configMapper.insert(config);
            }

            // 6. 保存115配置
            Map<String, Object> config115 = new HashMap<>();
            if (configData.containsKey("enable115Transfer")) {
                config115.put("enable115Transfer", configData.get("enable115Transfer"));
            }
            if (configData.containsKey("cookie115")) {
                config115.put("cookie115", configData.get("cookie115"));
            }
            if (configData.containsKey("targetFolderId115")) {
                config115.put("targetFolderId115", configData.get("targetFolderId115"));
            }
            if (!config115.isEmpty()) {
                SmartSearchConfig config = new SmartSearchConfig();
                config.setUserId(userId);
                config.setConfigName("115网盘配置");
                config.setConfigType("115_config");
                config.setConfigData(JSONUtil.toJsonStr(config115));
                config.setIsActive(true);
                config.setRemark("115网盘转存配置");
                configMapper.insert(config);
            }

            // 7. 保存Emby配置
            Map<String, Object> embyConfig = new HashMap<>();
            if (configData.containsKey("embyDownloadDir")) {
                embyConfig.put("embyDownloadDir", configData.get("embyDownloadDir"));
            }
            if (configData.containsKey("uploadDir")) {
                embyConfig.put("uploadDir", configData.get("uploadDir"));
            }
            if (configData.containsKey("gdTargetPath")) {
                embyConfig.put("gdTargetPath", configData.get("gdTargetPath"));
            }
            if (!embyConfig.isEmpty()) {
                SmartSearchConfig config = new SmartSearchConfig();
                config.setUserId(userId);
                config.setConfigName("Emby下载配置");
                config.setConfigType("emby_config");
                config.setConfigData(JSONUtil.toJsonStr(embyConfig));
                config.setIsActive(true);
                config.setRemark("Emby下载和上传目录配置");
                configMapper.insert(config);
            }

            // 8. 保存STRM配置
            Map<String, Object> strmConfig = new HashMap<>();
            if (configData.containsKey("strmOutputPath"))  strmConfig.put("strmOutputPath",  configData.get("strmOutputPath"));
            if (configData.containsKey("strmGdRemote"))    strmConfig.put("strmGdRemote",    configData.get("strmGdRemote"));
            if (configData.containsKey("strmGdPath"))      strmConfig.put("strmGdPath",      configData.get("strmGdPath"));
            if (configData.containsKey("strmPlayUrlBase")) strmConfig.put("strmPlayUrlBase", configData.get("strmPlayUrlBase"));
            if (!strmConfig.isEmpty()) {
                SmartSearchConfig config = new SmartSearchConfig();
                config.setUserId(userId);
                config.setConfigName("STRM生成配置");
                config.setConfigType("strm_config");
                config.setConfigData(JSONUtil.toJsonStr(strmConfig));
                config.setIsActive(true);
                config.setRemark("STRM文件生成和刮削配置");
                configMapper.insert(config);
            }

            return true;
        } catch (Exception e) {
            log.error("保存完整配置失败", e);
            throw new RuntimeException("保存配置失败: " + e.getMessage());
        }
    }
}
