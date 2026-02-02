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
     * @param forceRefresh 已废弃，保留仅为兼容性
     */
    @GetMapping("/libraries/{libraryId}/items/paged")
    public Result<PagedResult<EmbyItem>> getLibraryItemsPaged(
            @PathVariable String libraryId,
            @RequestParam(required = false, defaultValue = "0") Integer startIndex,
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestParam(required = false, defaultValue = "false") Boolean forceRefresh) {
        Map<String, Object> result = cacheService.getLibraryItemsPaged(libraryId, startIndex, limit, forceRefresh);

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
     * 获取所有类型（从数据库）
     * 注意：首次使用前需要先调用 /emby/sync 接口同步数据
     */
    @GetMapping("/genres")
    public Result<List<EmbyGenre>> getAllGenres(
            @RequestParam(required = false, defaultValue = "false") Boolean forceRefresh) {
        List<EmbyGenre> genres = cacheService.getAllGenres(forceRefresh);
        return Result.success(genres);
    }

    /**
     * 获取所有标签（从数据库）
     * 注意：首次使用前需要先调用 /emby/sync 接口同步数据
     */
    @GetMapping("/tags")
    public Result<List<EmbyGenre>> getAllTags(
            @RequestParam(required = false, defaultValue = "false") Boolean forceRefresh) {
        List<EmbyGenre> tags = cacheService.getAllTags(forceRefresh);
        return Result.success(tags);
    }

    /**
     * 获取所有工作室（从数据库）
     * 注意：首次使用前需要先调用 /emby/sync 接口同步数据
     */
    @GetMapping("/studios")
    public Result<List<EmbyGenre>> getAllStudios(
            @RequestParam(required = false, defaultValue = "false") Boolean forceRefresh) {
        List<EmbyGenre> studios = cacheService.getAllStudios(forceRefresh);
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
}
