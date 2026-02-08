package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.dto.EmbyGenre;
import com.gdupload.dto.EmbyItem;
import com.gdupload.dto.EmbyLibrary;
import com.gdupload.dto.PagedResult;
import com.gdupload.service.IEmbyCacheService;
import com.gdupload.service.IEmbyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Emby控制器
 */
@Slf4j
@RestController
@RequestMapping("/emby")
public class EmbyController {

    @Autowired
    private IEmbyService embyService;

    @Autowired
    private IEmbyCacheService cacheService;

    /**
     * 测试Emby连接
     */
    @GetMapping("/test")
    public Result<Boolean> testConnection() {
        boolean success = embyService.testConnection();
        return Result.success(success);
    }

    /**
     * 获取Emby服务器信息
     */
    @GetMapping("/server-info")
    public Result<Map<String, Object>> getServerInfo() {
        Map<String, Object> info = embyService.getServerInfo();
        return Result.success(info);
    }

    /**
     * 获取所有媒体库（从数据库）
     * 注意：首次使用前需要先调用 /emby/sync 接口同步数据
     */
    @GetMapping("/libraries")
    public Result<List<EmbyLibrary>> getAllLibraries(
            @RequestParam(required = false, defaultValue = "false") Boolean forceRefresh) {
        List<EmbyLibrary> libraries = cacheService.getAllLibraries(forceRefresh);
        return Result.success(libraries);
    }

    /**
     * 获取指定媒体库的媒体项数量
     *
     * @param libraryId 媒体库ID
     */
    @GetMapping("/libraries/{libraryId}/count")
    public Result<Integer> getLibraryItemCount(@PathVariable String libraryId) {
        Integer count = embyService.getLibraryItemCount(libraryId);
        return Result.success(count);
    }

    /**
     * 获取指定媒体库的所有媒体项
     *
     * @param libraryId 媒体库ID
     * @param startIndex 起始索引（可选）
     * @param limit 每页数量（可选）
     */
    @GetMapping("/libraries/{libraryId}/items")
    public Result<List<EmbyItem>> getLibraryItems(
            @PathVariable String libraryId,
            @RequestParam(required = false) Integer startIndex,
            @RequestParam(required = false) Integer limit) {
        List<EmbyItem> items = embyService.getLibraryItems(libraryId, startIndex, limit);
        return Result.success(items);
    }

    /**
     * 获取指定媒体库的所有媒体项（分页，从数据库）
     * 注意：首次使用前需要先调用 /emby/sync 接口同步数据
     *
     * @param libraryId 媒体库ID
     * @param startIndex 起始索引（可选，默认0）
     * @param limit 每页数量（可选，默认50）
     * @param transferStatus 转存状态筛选（可选：success-成功, failed-失败, none-未转存）
     * @param downloadStatus 下载状态筛选（可选：success-成功, failed-失败, none-未下载）
     * @param forceRefresh 已废弃，保留仅为兼容性
     */
    @GetMapping("/libraries/{libraryId}/items/paged")
    public Result<PagedResult<EmbyItem>> getLibraryItemsPaged(
            @PathVariable String libraryId,
            @RequestParam(required = false, defaultValue = "0") Integer startIndex,
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestParam(required = false) String transferStatus,
            @RequestParam(required = false) String downloadStatus,
            @RequestParam(required = false, defaultValue = "false") Boolean forceRefresh) {
        Map<String, Object> result = cacheService.getLibraryItemsPaged(libraryId, startIndex, limit, transferStatus, downloadStatus, forceRefresh);

        @SuppressWarnings("unchecked")
        List<EmbyItem> items = (List<EmbyItem>) result.get("items");
        Integer totalCount = ((Number) result.get("totalCount")).intValue();

        PagedResult<EmbyItem> pagedResult = new PagedResult<>();
        pagedResult.setItems(items);
        pagedResult.setTotalCount(totalCount);
        pagedResult.setStartIndex(startIndex);

        return Result.success(pagedResult);
    }

    /**
     * 获取媒体项详情（从数据库）
     * 注意：首次使用前需要先调用 /emby/sync 接口同步数据
     *
     * @param itemId 媒体项ID
     * @param forceRefresh 已废弃，保留仅为兼容性
     */
    @GetMapping("/items/{itemId:.+}")
    public Result<EmbyItem> getItemDetail(
            @PathVariable String itemId,
            @RequestParam(required = false, defaultValue = "false") Boolean forceRefresh) {
        log.info("获取媒体项详情: itemId={}", itemId);
        EmbyItem item = cacheService.getItemDetail(itemId, forceRefresh);
        return Result.success(item);
    }

    /**
     * 获取电视剧的所有剧集
     *
     * @param seriesId 电视剧ID
     */
    @GetMapping("/series/{seriesId}/episodes")
    public Result<List<EmbyItem>> getSeriesEpisodes(@PathVariable String seriesId) {
        log.info("获取电视剧剧集列表: seriesId={}", seriesId);
        List<EmbyItem> episodes = embyService.getSeriesEpisodes(seriesId);
        return Result.success(episodes);
    }

    /**
     * 获取所有类型（从数据库）
     * 注意：首次使用前需要先调用 /emby/sync 接口同步数据
     */
    @GetMapping("/genres")
    public Result<List<EmbyGenre>> getAllGenres(
            @RequestParam(required = false, defaultValue = "false") Boolean forceRefresh,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        List<EmbyGenre> genres = cacheService.getAllGenres(forceRefresh);
        // 限制返回数量，避免前端卡顿
        if (limit != null && limit > 0 && genres.size() > limit) {
            genres = genres.subList(0, limit);
        }
        return Result.success(genres);
    }

    /**
     * 获取所有标签（从数据库）
     * 注意：首次使用前需要先调用 /emby/sync 接口同步数据
     */
    @GetMapping("/tags")
    public Result<List<EmbyGenre>> getAllTags(
            @RequestParam(required = false, defaultValue = "false") Boolean forceRefresh,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        List<EmbyGenre> tags = cacheService.getAllTags(forceRefresh);
        // 限制返回数量，避免前端卡顿
        if (limit != null && limit > 0 && tags.size() > limit) {
            tags = tags.subList(0, limit);
        }
        return Result.success(tags);
    }

    /**
     * 获取所有工作室（从数据库）
     * 注意：首次使用前需要先调用 /emby/sync 接口同步数据
     */
    @GetMapping("/studios")
    public Result<List<EmbyGenre>> getAllStudios(
            @RequestParam(required = false, defaultValue = "false") Boolean forceRefresh,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        List<EmbyGenre> studios = cacheService.getAllStudios(forceRefresh);
        // 限制返回数量，避免前端卡顿
        if (limit != null && limit > 0 && studios.size() > limit) {
            studios = studios.subList(0, limit);
        }
        return Result.success(studios);
    }

    /**
     * 搜索媒体项（从数据库）
     * 注意：首次使用前需要先调用 /emby/sync 接口同步数据
     *
     * @param keyword 关键词
     * @param forceRefresh 已废弃，保留仅为兼容性
     */
    @GetMapping("/search")
    public Result<List<EmbyItem>> searchItems(
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "false") Boolean forceRefresh) {
        List<EmbyItem> items = cacheService.searchItems(keyword, forceRefresh);
        return Result.success(items);
    }

    /**
     * 一次性全量同步所有媒体库数据到数据库
     * 包括：媒体库列表、所有媒体项、类型、标签、工作室
     * 注意：此操作会清空旧数据，重新全量同步，可能耗时较长
     */
    @PostMapping("/sync")
    public Result<Map<String, Object>> syncAllLibraries() {
        Map<String, Object> result = cacheService.syncAllData();
        return Result.success(result);
    }

    /**
     * 清空所有数据库缓存
     * 注意：清空后需要重新执行同步操作
     */
    @PostMapping("/cache/clear")
    public Result<String> clearCache() {
        boolean success = cacheService.clearAllCache();
        if (success) {
            return Result.success("缓存已清空");
        } else {
            return Result.error("清空缓存失败");
        }
    }

    /**
     * 检查数据库缓存状态
     */
    @GetMapping("/cache/status")
    public Result<Map<String, Object>> getCacheStatus() {
        Map<String, Object> status = new java.util.HashMap<>();
        status.put("hasCache", cacheService.hasCacheData());
        return Result.success(status);
    }

    /**
     * 获取Emby媒体项的下载URL
     * 注意：此功能仅用于测试，需要Emby服务器开启下载权限
     *
     * @param itemId 媒体项ID
     * @return 下载URL信息
     */
    @GetMapping("/items/{itemId}/download-urls")
    public Result<Map<String, String>> getDownloadUrls(@PathVariable String itemId) {
        log.info("获取媒体项下载URL: itemId={}", itemId);
        Map<String, String> urls = embyService.getDownloadUrls(itemId);
        return Result.success(urls);
    }

    /**
     * 代理下载Emby媒体项（模拟播放器请求）
     * 通过后端代理请求视频流，绕过直接下载限制
     *
     * @param itemId 媒体项ID
     * @param response HTTP响应
     */
    @GetMapping("/items/{itemId}/proxy-download")
    public void proxyDownload(
            @PathVariable String itemId,
            @RequestParam(required = false, defaultValue = "video.mp4") String filename,
            javax.servlet.http.HttpServletResponse response) {
        log.info("代理下载媒体项: itemId={}, filename={}", itemId, filename);
        try {
            embyService.proxyDownload(itemId, filename, response);
        } catch (Exception e) {
            log.error("代理下载失败", e);
            response.setStatus(500);
        }
    }

    /**
     * 下载Emby媒体项到服务器本地
     * 下载到 /data/emby 目录
     *
     * @param itemId 媒体项ID
     * @return 下载结果
     */
    @PostMapping("/items/{itemId}/download-to-server")
    public Result<Map<String, Object>> downloadToServer(@PathVariable String itemId) {
        log.info("下载媒体项到服务器: itemId={}", itemId);
        try {
            // 启动异步下载任务
            embyService.downloadToServerAsync(itemId);

            // 立即返回，告诉前端已开始下载
            Map<String, Object> result = new HashMap<>();
            result.put("status", "started");
            result.put("message", "下载任务已启动，请查看后端日志了解进度");
            result.put("itemId", itemId);

            return Result.success(result);
        } catch (Exception e) {
            log.error("启动下载任务失败", e);
            return Result.error("启动下载失败: " + e.getMessage());
        }
    }

    /**
     * 批量下载Emby媒体项到服务器本地（后端队列执行）
     * 所有下载任务在后端依次执行，不依赖前端保持连接
     *
     * @param itemIds 媒体项ID列表
     * @return 批量下载任务启动结果
     */
    @PostMapping("/batch-download-to-server")
    public Result<Map<String, Object>> batchDownloadToServer(@RequestBody List<String> itemIds) {
        log.info("批量下载媒体项到服务器，共 {} 个", itemIds.size());
        try {
            // 启动后端批量下载任务，返回taskId
            Long taskId = embyService.batchDownloadToServerAsync(itemIds);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "started");
            result.put("message", "批量下载任务已启动，共 " + itemIds.size() + " 个媒体项");
            result.put("totalCount", itemIds.size());
            result.put("taskId", taskId);

            return Result.success(result);
        } catch (Exception e) {
            log.error("启动批量下载任务失败", e);
            return Result.error("启动批量下载失败: " + e.getMessage());
        }
    }

    /**
     * 获取批量下载进度
     *
     * @return 当前批量下载的进度信息
     */
    @GetMapping("/batch-download-progress")
    public Result<Map<String, Object>> getBatchDownloadProgress() {
        Map<String, Object> progress = embyService.getBatchDownloadProgress();
        return Result.success(progress);
    }
}
