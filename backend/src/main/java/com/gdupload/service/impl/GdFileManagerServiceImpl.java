package com.gdupload.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdupload.common.BusinessException;
import com.gdupload.dto.GdFileItem;
import com.gdupload.service.IGdFileManagerService;
import com.gdupload.util.RcloneUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * GD文件管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GdFileManagerServiceImpl implements IGdFileManagerService {

    private final RcloneUtil rcloneUtil;
    private final ObjectMapper objectMapper;

    @Override
    public List<GdFileItem> listFiles(String rcloneConfigName, String path) {
        String json = rcloneUtil.listJson(rcloneConfigName, path);
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<GdFileItem>>() {});
        } catch (Exception e) {
            log.error("解析lsjson输出失败: configName={}, path={}, json=[{}]", rcloneConfigName, path, json, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void deleteFile(String rcloneConfigName, String filePath) {
        boolean success = rcloneUtil.deleteFile(rcloneConfigName, filePath);
        if (!success) {
            throw new BusinessException("删除文件失败: " + filePath);
        }
    }

    @Override
    public void deleteDirectory(String rcloneConfigName, String dirPath) {
        boolean success = rcloneUtil.purgeDirectory(rcloneConfigName, dirPath);
        if (!success) {
            throw new BusinessException("删除目录失败: " + dirPath);
        }
    }

    @Override
    public void moveItem(String rcloneConfigName, String oldPath, String newPath, boolean isDir) {
        boolean success = rcloneUtil.moveItem(rcloneConfigName, oldPath, newPath, isDir);
        if (!success) {
            throw new BusinessException("移动/重命名失败: " + oldPath + " -> " + newPath);
        }
    }

    @Override
    public void makeDirectory(String rcloneConfigName, String path) {
        boolean success = rcloneUtil.makeDirectory(rcloneConfigName, path);
        if (!success) {
            throw new BusinessException("创建目录失败: " + path);
        }
    }
}
