package com.gdupload.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gdupload.common.BusinessException;
import com.gdupload.entity.EmbyConfig;
import com.gdupload.mapper.EmbyConfigMapper;
import com.gdupload.service.IEmbyConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Emby配置服务实现
 */
@Slf4j
@Service
public class EmbyConfigServiceImpl extends ServiceImpl<EmbyConfigMapper, EmbyConfig> implements IEmbyConfigService {

    @Override
    public EmbyConfig getDefaultConfig() {
        // 先查找标记为默认的配置
        LambdaQueryWrapper<EmbyConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmbyConfig::getIsDefault, true)
                .eq(EmbyConfig::getEnabled, true)
                .orderByDesc(EmbyConfig::getUpdateTime)
                .last("LIMIT 1");

        EmbyConfig config = this.getOne(wrapper);

        // 如果没有默认配置，返回第一个启用的配置
        if (config == null) {
            wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(EmbyConfig::getEnabled, true)
                    .orderByDesc(EmbyConfig::getUpdateTime)
                    .last("LIMIT 1");
            config = this.getOne(wrapper);
        }

        if (config == null) {
            throw new BusinessException("未找到可用的Emby配置，请先添加配置");
        }

        return config;
    }

    @Override
    public List<EmbyConfig> getAllConfigs() {
        LambdaQueryWrapper<EmbyConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(EmbyConfig::getIsDefault)
                .orderByDesc(EmbyConfig::getUpdateTime);
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setDefaultConfig(Long id) {
        // 先取消所有默认配置
        LambdaQueryWrapper<EmbyConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmbyConfig::getIsDefault, true);
        List<EmbyConfig> configs = this.list(wrapper);

        for (EmbyConfig config : configs) {
            config.setIsDefault(false);
            this.updateById(config);
        }

        // 设置新的默认配置
        EmbyConfig config = this.getById(id);
        if (config == null) {
            throw new BusinessException("配置不存在");
        }

        config.setIsDefault(true);
        config.setEnabled(true);
        return this.updateById(config);
    }

    @Override
    public boolean testConfig(EmbyConfig config) {
        try {
            log.info("开始测试Emby配置: serverUrl={}, username={}, hasApiKey={}",
                    config.getServerUrl(), config.getUsername(),
                    StrUtil.isNotBlank(config.getApiKey()));

            String url = config.getServerUrl();
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }

            // 如果有 API Key，直接测试
            if (StrUtil.isNotBlank(config.getApiKey())) {
                url += "/emby/System/Info";
                log.info("使用API Key测试: {}", url);

                HttpRequest request = HttpRequest.get(url)
                        .header("X-Emby-Token", config.getApiKey())
                        .timeout(config.getTimeout() != null ? config.getTimeout() : 30000);

                HttpResponse response = request.execute();
                log.info("API Key测试响应: status={}, body={}", response.getStatus(), response.body());
                return response.isOk();
            }
            // 如果是用户名密码，尝试登录
            else if (StrUtil.isNotBlank(config.getUsername()) && StrUtil.isNotBlank(config.getPassword())) {
                url += "/emby/Users/AuthenticateByName";
                log.info("使用用户名密码测试: {}", url);

                cn.hutool.json.JSONObject requestBody = new cn.hutool.json.JSONObject();
                requestBody.set("Username", config.getUsername());
                requestBody.set("Pw", config.getPassword());

                log.info("登录请求体: {}", requestBody.toString());

                HttpResponse response = HttpRequest.post(url)
                        .header("Content-Type", "application/json")
                        .header("X-Emby-Authorization",
                                "MediaBrowser Client=\"GD Upload Manager\", Device=\"Server\", " +
                                "DeviceId=\"test-device\", Version=\"1.0.0\"")
                        .body(requestBody.toString())
                        .timeout(config.getTimeout() != null ? config.getTimeout() : 30000)
                        .execute();

                log.info("登录测试响应: status={}, isOk={}, body={}",
                        response.getStatus(), response.isOk(), response.body().substring(0, Math.min(200, response.body().length())));

                boolean success = response.isOk();
                if (success) {
                    log.info("Emby登录测试成功");
                } else {
                    log.warn("Emby登录测试失败: HTTP {}", response.getStatus());
                }

                return success;
            } else {
                log.error("未配置API Key或用户名密码");
                throw new BusinessException("未配置API Key或用户名密码");
            }

        } catch (Exception e) {
            log.error("测试Emby配置异常: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateConfig(EmbyConfig config) {
        // 如果是新配置且没有其他配置，自动设为默认
        if (config.getId() == null) {
            long count = this.count();
            if (count == 0) {
                config.setIsDefault(true);
            }
        }

        // 如果设置为默认，取消其他默认配置
        if (config.getIsDefault() != null && config.getIsDefault()) {
            LambdaQueryWrapper<EmbyConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(EmbyConfig::getIsDefault, true);
            if (config.getId() != null) {
                wrapper.ne(EmbyConfig::getId, config.getId());
            }

            List<EmbyConfig> configs = this.list(wrapper);
            for (EmbyConfig c : configs) {
                c.setIsDefault(false);
                this.updateById(c);
            }
        }

        return this.saveOrUpdate(config);
    }
}
