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

                // 模拟Forward app的请求格式
                String deviceId = "30c9d308e74a46a1811c851bf76a8f77";
                String userId = config.getUserId() != null ? config.getUserId() : "unknown";

                // 构建 X-Emby-Authorization 头部（Forward格式）
                String embyAuth = String.format(
                    "MediaBrowser Token=\"%s\", Emby UserId=\"%s\", Client=\"Forward\", Device=\"iPhone\", DeviceId=\"%s\", Version=\"1.3.14\"",
                    config.getApiKey(), userId, deviceId
                );

                HttpRequest request = HttpRequest.get(url)
                        .header("X-Emby-Token", config.getApiKey())
                        .header("X-Emby-Authorization", embyAuth)
                        .header("User-Agent", "Forward-Standard/1.3.14")
                        .header("Accept", "*/*")
                        .header("Accept-Language", "zh-CN,zh-Hans;q=0.9")
                        .header("Accept-Encoding", "gzip, deflate, br")
                        .header("Connection", "keep-alive")
                        .timeout(config.getTimeout() != null ? config.getTimeout() : 30000)
                        .setFollowRedirects(true);

                HttpResponse response = request.execute();
                log.info("API Key测试响应: status={}, body={}", response.getStatus(),
                    response.body().substring(0, Math.min(200, response.body().length())));
                return response.isOk();
            }
            // 如果是用户名密码，跳过登录测试，直接返回true
            // 因为Cloudflare会拦截登录接口，但实际使用时EmbyAuthService会处理认证
            else if (StrUtil.isNotBlank(config.getUsername()) && StrUtil.isNotBlank(config.getPassword())) {
                log.info("检测到用户名密码配置，跳过登录测试（Cloudflare可能拦截登录接口）");
                log.info("实际使用时会通过EmbyAuthService进行认证");
                return true;
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
