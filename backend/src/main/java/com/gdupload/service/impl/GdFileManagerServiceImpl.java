package com.gdupload.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdupload.common.BusinessException;
import com.gdupload.dto.GdFileItem;
import com.gdupload.dto.PagedResult;
import com.gdupload.service.IGdFileManagerService;
import com.gdupload.util.RcloneUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GD文件管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GdFileManagerServiceImpl implements IGdFileManagerService {

    private final RcloneUtil rcloneUtil;
    private final ObjectMapper objectMapper;

    // ── 目录列表缓存（60s TTL，写操作自动失效） ───────────────────────────────────
    /** 缓存条目：已排序的完整文件列表 + 写入时间戳 */
    private static class CachedListing {
        final List<GdFileItem> items;
        final long timestamp;
        CachedListing(List<GdFileItem> items) {
            this.items     = items;
            this.timestamp = System.currentTimeMillis();
        }
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    private static final long CACHE_TTL_MS = 60_000; // 60 秒
    private final ConcurrentHashMap<String, CachedListing> listCache = new ConcurrentHashMap<>();

    private String cacheKey(String configName, String path) {
        return configName + ":" + (path == null ? "" : path);
    }

    /** 让某目录及其父目录的缓存失效（写操作后调用） */
    private void invalidateCache(String configName, String path) {
        listCache.remove(cacheKey(configName, path));
        if (path != null && path.contains("/")) {
            String parent = path.substring(0, path.lastIndexOf('/'));
            listCache.remove(cacheKey(configName, parent));
        } else {
            listCache.remove(cacheKey(configName, ""));
        }
    }

    /** 拉取远端目录、解析 JSON、排序后放入缓存 */
    private List<GdFileItem> fetchAndCache(String rcloneConfigName, String path) {
        String json = rcloneUtil.listJson(rcloneConfigName, path);
        List<GdFileItem> all = Collections.emptyList();
        if (StrUtil.isNotBlank(json)) {
            try {
                all = objectMapper.readValue(json, new TypeReference<List<GdFileItem>>() {});
            } catch (Exception e) {
                log.error("解析lsjson输出失败: configName={}, path={}", rcloneConfigName, path, e);
            }
        }
        // 目录优先，同类型按名称字母序（不区分大小写）
        List<GdFileItem> sorted = new ArrayList<>(all);
        sorted.sort(Comparator
                .comparing((GdFileItem f) -> Boolean.TRUE.equals(f.getIsDir()) ? 0 : 1)
                .thenComparing(f -> f.getName() == null ? "" : f.getName().toLowerCase()));

        listCache.put(cacheKey(rcloneConfigName, path), new CachedListing(sorted));
        return sorted;
    }

    // ── 接口实现 ─────────────────────────────────────────────────────────────────

    @Override
    public PagedResult<GdFileItem> listFiles(String rcloneConfigName, String path, int page, int size) {
        String key = cacheKey(rcloneConfigName, path);
        CachedListing cached = listCache.get(key);
        List<GdFileItem> sorted;
        if (cached != null && !cached.isExpired()) {
            sorted = cached.items;
        } else {
            sorted = fetchAndCache(rcloneConfigName, path);
        }

        int total     = sorted.size();
        int fromIndex = (page - 1) * size;
        int toIndex   = Math.min(fromIndex + size, total);
        List<GdFileItem> items = fromIndex < total
                ? sorted.subList(fromIndex, toIndex)
                : Collections.emptyList();
        return new PagedResult<>(items, total, fromIndex, size);
    }

    @Override
    public void deleteFile(String rcloneConfigName, String filePath) {
        boolean success = rcloneUtil.deleteFile(rcloneConfigName, filePath);
        if (!success) throw new BusinessException("删除文件失败: " + filePath);
        invalidateCache(rcloneConfigName, filePath);
    }

    @Override
    public void deleteDirectory(String rcloneConfigName, String dirPath) {
        boolean success = rcloneUtil.purgeDirectory(rcloneConfigName, dirPath);
        if (!success) throw new BusinessException("删除目录失败: " + dirPath);
        invalidateCache(rcloneConfigName, dirPath);
    }

    @Override
    public void moveItem(String rcloneConfigName, String oldPath, String newPath, boolean isDir) {
        boolean success = rcloneUtil.moveItem(rcloneConfigName, oldPath, newPath, isDir);
        if (!success) throw new BusinessException("移动/重命名失败: " + oldPath + " -> " + newPath);
        invalidateCache(rcloneConfigName, oldPath);
        invalidateCache(rcloneConfigName, newPath);
    }

    @Override
    public void makeDirectory(String rcloneConfigName, String path) {
        boolean success = rcloneUtil.makeDirectory(rcloneConfigName, path);
        if (!success) throw new BusinessException("创建目录失败: " + path);
        invalidateCache(rcloneConfigName, path);
    }
}
