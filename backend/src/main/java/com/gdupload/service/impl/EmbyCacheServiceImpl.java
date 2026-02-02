package com.gdupload.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.common.BusinessException;
import com.gdupload.dto.EmbyGenre;
import com.gdupload.dto.EmbyItem;
import com.gdupload.dto.EmbyLibrary;
import com.gdupload.dto.PagedResult;
import com.gdupload.entity.EmbyConfig;
import com.gdupload.entity.EmbyGenreCache;
import com.gdupload.entity.EmbyItemCache;
import com.gdupload.entity.EmbyLibraryCache;
import com.gdupload.entity.EmbyStudioCache;
import com.gdupload.entity.EmbyTagCache;
import com.gdupload.mapper.EmbyGenreCacheMapper;
import com.gdupload.mapper.EmbyItemCacheMapper;
import com.gdupload.mapper.EmbyLibraryCacheMapper;
import com.gdupload.mapper.EmbyStudioCacheMapper;
import com.gdupload.mapper.EmbyTagCacheMapper;
import com.gdupload.service.IEmbyCacheService;
import com.gdupload.service.IEmbyConfigService;
import com.gdupload.service.IEmbyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Emby缓存服务实现
 */
@Slf4j
@Service
public class EmbyCacheServiceImpl implements IEmbyCacheService {

    @Autowired
    private EmbyLibraryCacheMapper libraryCacheMapper;

    @Autowired
    private EmbyItemCacheMapper itemCacheMapper;

    @Autowired
    private EmbyGenreCacheMapper genreCacheMapper;

    @Autowired
    private EmbyTagCacheMapper tagCacheMapper;

    @Autowired
    private EmbyStudioCacheMapper studioCacheMapper;

    @Autowired
    private IEmbyService embyService;

    @Autowired
    private IEmbyConfigService embyConfigService;

    /**
     * 获取当前使用的 Emby 配置ID
     * 使用默认配置（简单方案）
     */
    private Long getCurrentEmbyConfigId() {
        EmbyConfig config = embyConfigService.getDefaultConfig();
        if (config == null) {
            throw new BusinessException("未找到默认的 Emby 配置，请先配置 Emby 服务器");
        }
        return config.getId();
    }

    @Override
    public List<EmbyLibrary> getAllLibraries(boolean forceRefresh) {
        Long configId = getCurrentEmbyConfigId();
        // 直接从数据库获取
        log.info("从数据库获取媒体库列表 (configId={})", configId);

        LambdaQueryWrapper<EmbyLibraryCache> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmbyLibraryCache::getEmbyConfigId, configId);

        List<EmbyLibraryCache> cacheList = libraryCacheMapper.selectList(wrapper);

        if (cacheList.isEmpty()) {
            log.warn("数据库中没有媒体库数据，请先执行同步操作");
            throw new BusinessException("数据库中没有媒体库数据，请先点击\"同步所有数据\"按钮进行同步");
        }

        return cacheList.stream().map(this::convertToLibrary).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getLibraryItemsPaged(String libraryId, int startIndex, int limit, boolean forceRefresh) {
        Long configId = getCurrentEmbyConfigId();
        // 直接从数据库获取
        log.info("从数据库获取媒体库 {} 的媒体项 (configId={})", libraryId, configId);

        // 检查数据库是否有数据
        LambdaQueryWrapper<EmbyItemCache> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(EmbyItemCache::getEmbyConfigId, configId)
                .eq(EmbyItemCache::getParentId, libraryId);
        Long cacheCount = itemCacheMapper.selectCount(countWrapper);

        if (cacheCount == 0) {
            log.warn("数据库中没有媒体库 {} 的数据，请先执行同步操作", libraryId);
            throw new BusinessException("数据库中没有该媒体库的数据，请先点击\"同步所有数据\"按钮进行同步");
        }

        // 分页查询数据库
        Page<EmbyItemCache> page = new Page<>(startIndex / limit + 1, limit);
        LambdaQueryWrapper<EmbyItemCache> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmbyItemCache::getEmbyConfigId, configId)
                .eq(EmbyItemCache::getParentId, libraryId)
                .orderByDesc(EmbyItemCache::getUpdateTime);

        Page<EmbyItemCache> cachePage = itemCacheMapper.selectPage(page, wrapper);

        List<EmbyItem> items = cachePage.getRecords().stream()
                .map(this::convertToItem)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("totalCount", cachePage.getTotal());
        result.put("startIndex", startIndex);

        return result;
    }

    @Override
    public EmbyItem getItemDetail(String itemId, boolean forceRefresh) {
        Long configId = getCurrentEmbyConfigId();
        // 直接从数据库获取
        log.info("从数据库获取媒体项详情: {} (configId={})", itemId, configId);

        // MyBatis Plus 不支持联合主键查询，需要使用 wrapper
        LambdaQueryWrapper<EmbyItemCache> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmbyItemCache::getId, itemId)
                .eq(EmbyItemCache::getEmbyConfigId, configId);

        EmbyItemCache cache = itemCacheMapper.selectOne(wrapper);

        if (cache == null) {
            log.warn("数据库中没有媒体项 {} 的数据，请先执行同步操作", itemId);
            throw new BusinessException("数据库中没有该媒体项的数据，请先点击\"同步所有数据\"按钮进行同步");
        }

        return convertToItem(cache);
    }

    @Override
    public List<EmbyItem> searchItems(String keyword, boolean forceRefresh) {
        Long configId = getCurrentEmbyConfigId();
        // 直接从数据库搜索
        log.info("从数据库搜索: {} (configId={})", keyword, configId);

        // 检查数据库是否有数据
        LambdaQueryWrapper<EmbyItemCache> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(EmbyItemCache::getEmbyConfigId, configId);
        Long cacheCount = itemCacheMapper.selectCount(countWrapper);

        if (cacheCount == 0) {
            log.warn("数据库中没有媒体项数据，请先执行同步操作");
            throw new BusinessException("数据库中没有媒体项数据，请先点击\"同步���有数据\"按钮进行同步");
        }

        // 使用LIKE搜索
        LambdaQueryWrapper<EmbyItemCache> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmbyItemCache::getEmbyConfigId, configId)
                .and(w -> w.like(EmbyItemCache::getName, keyword)
                        .or()
                        .like(EmbyItemCache::getOriginalTitle, keyword))
                .orderByDesc(EmbyItemCache::getUpdateTime)
                .last("LIMIT 100");

        List<EmbyItemCache> cacheList = itemCacheMapper.selectList(wrapper);

        return cacheList.stream().map(this::convertToItem).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncAllData() {
        log.info("========================================");
        log.info("开始一次性全量同步所有Emby数据到数据库");
        log.info("========================================");

        long startTime = System.currentTimeMillis();

        try {
            // 1. 清空旧数据
            log.info("步骤1: 清空旧数据...");
            clearAllCache();
            log.info("旧数据已清空");

            // 2. 获取并保存所有媒体库
            log.info("步骤2: 同步媒体库列表...");
            List<EmbyLibrary> libraries = embyService.getAllLibraries();
            log.info("从Emby API获取到 {} 个媒体库", libraries.size());
            saveLibrariesToCache(libraries);
            log.info("媒体库列表已保存到数据库");

            // 3. 同步每个媒体库的所有媒体项（全量）
            log.info("步骤3: 同步所有媒体库的媒体项...");
            int totalItems = 0;
            int successCount = 0;
            int failedCount = 0;
            List<String> failedLibraries = new ArrayList<>();

            for (int i = 0; i < libraries.size(); i++) {
                EmbyLibrary library = libraries.get(i);
                log.info("正在同步媒体库 [{}/{}]: {} (ID: {})",
                        i + 1, libraries.size(), library.getName(), library.getId());

                try {
                    int itemCount = syncLibraryItemsAll(library.getId());
                    totalItems += itemCount;
                    successCount++;
                    log.info("✓ 媒体库 {} 同步完成，共 {} 个媒体项", library.getName(), itemCount);
                } catch (Exception e) {
                    failedCount++;
                    failedLibraries.add(library.getName() + " (ID: " + library.getId() + ")");
                    log.error("✗ 媒体库 {} 同步失败: {}", library.getName(), e.getMessage(), e);
                    // 继续同步下一个媒体库，不中断整个同步过程
                }
            }

            log.info("所有媒体项同步完成，共 {} 个", totalItems);
            log.info("成功: {} 个媒体库，失败: {} 个媒体库", successCount, failedCount);
            if (failedCount > 0) {
                log.warn("失败的媒体库: {}", String.join(", ", failedLibraries));
            }

            // 4. 同步类型、标签、工作室
            log.info("步骤4: 同步类型、标签、工作室...");

            List<EmbyGenre> genres = embyService.getAllGenres();
            saveGenresToCache(genres);
            log.info("同步 {} 个类型", genres.size());

            List<EmbyGenre> tags = embyService.getAllTags();
            saveTagsToCache(tags);
            log.info("同步 {} 个标签", tags.size());

            List<EmbyGenre> studios = embyService.getAllStudios();
            saveStudiosToCache(studios);
            log.info("同步 {} 个工作室", studios.size());

            long endTime = System.currentTimeMillis();
            long duration = (endTime - startTime) / 1000;

            Map<String, Object> result = new HashMap<>();
            result.put("success", failedCount == 0); // 只有全部成功才算成功
            result.put("totalLibraries", libraries.size());
            result.put("successLibraries", successCount);
            result.put("failedLibraries", failedCount);
            result.put("totalItems", totalItems);
            result.put("totalGenres", genres.size());
            result.put("totalTags", tags.size());
            result.put("totalStudios", studios.size());
            result.put("duration", duration + "秒");

            if (failedCount > 0) {
                result.put("failedLibraryNames", failedLibraries);
                result.put("message", "部分媒体库同步失败，请查看日志");
            }

            log.info("========================================");
            log.info("全量同步完成！");
            log.info("媒体库: {} 个（成功: {}，失败: {}）", libraries.size(), successCount, failedCount);
            log.info("媒体项: {} 个", totalItems);
            log.info("类型: {} 个", genres.size());
            log.info("标签: {} 个", tags.size());
            log.info("工作室: {} 个", studios.size());
            log.info("耗时: {} 秒", duration);
            if (failedCount > 0) {
                log.warn("失败的媒体库: {}", String.join(", ", failedLibraries));
            }
            log.info("========================================");

            return result;
        } catch (Exception e) {
            log.error("========================================");
            log.error("同步数据失败: {}", e.getMessage(), e);
            log.error("========================================");

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean syncLibrary(String libraryId) {
        try {
            Long configId = getCurrentEmbyConfigId();
            log.info("开始全量同步单个媒体库: {} (configId={})", libraryId, configId);

            // 先删除该媒体库的旧数据
            LambdaQueryWrapper<EmbyItemCache> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(EmbyItemCache::getEmbyConfigId, configId)
                    .eq(EmbyItemCache::getParentId, libraryId);
            itemCacheMapper.delete(wrapper);
            log.info("已删除媒体库 {} 的旧数据", libraryId);

            // 全量同步该媒体库的所有媒体项
            int itemCount = syncLibraryItemsAll(libraryId);
            log.info("媒体库 {} 同步完成，共 {} 个媒体项", libraryId, itemCount);

            return true;
        } catch (Exception e) {
            log.error("同步媒体库失败: {}", libraryId, e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean clearAllCache() {
        try {
            Long configId = getCurrentEmbyConfigId();
            log.info("清空配置 {} 的所有缓存", configId);

            // 只删除当前配置的数据
            LambdaQueryWrapper<EmbyLibraryCache> libraryWrapper = new LambdaQueryWrapper<>();
            libraryWrapper.eq(EmbyLibraryCache::getEmbyConfigId, configId);
            libraryCacheMapper.delete(libraryWrapper);

            LambdaQueryWrapper<EmbyItemCache> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(EmbyItemCache::getEmbyConfigId, configId);
            itemCacheMapper.delete(itemWrapper);

            LambdaQueryWrapper<EmbyGenreCache> genreWrapper = new LambdaQueryWrapper<>();
            genreWrapper.eq(EmbyGenreCache::getEmbyConfigId, configId);
            genreCacheMapper.delete(genreWrapper);

            LambdaQueryWrapper<EmbyTagCache> tagWrapper = new LambdaQueryWrapper<>();
            tagWrapper.eq(EmbyTagCache::getEmbyConfigId, configId);
            tagCacheMapper.delete(tagWrapper);

            LambdaQueryWrapper<EmbyStudioCache> studioWrapper = new LambdaQueryWrapper<>();
            studioWrapper.eq(EmbyStudioCache::getEmbyConfigId, configId);
            studioCacheMapper.delete(studioWrapper);

            return true;
        } catch (Exception e) {
            log.error("清空缓存失败", e);
            return false;
        }
    }

    @Override
    public boolean hasCacheData() {
        Long configId = getCurrentEmbyConfigId();

        LambdaQueryWrapper<EmbyLibraryCache> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmbyLibraryCache::getEmbyConfigId, configId);

        Long count = libraryCacheMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /**
     * 同步媒体库的所有媒体项（全量）
     */
    private int syncLibraryItemsAll(String libraryId) {
        int totalCount = 0;
        int startIndex = 0;
        int limit = 100; // 每次获取100条
        int batchNumber = 1;

        log.info("开始全量同步媒体库 {} 的所有媒体项", libraryId);

        while (true) {
            try {
                log.info("获取第 {} 批数据 (startIndex={}, limit={})", batchNumber, startIndex, limit);

                PagedResult<EmbyItem> pagedResult = embyService.getLibraryItemsPaged(libraryId, startIndex, limit);
                List<EmbyItem> items = pagedResult.getItems();

                if (items == null || items.isEmpty()) {
                    log.info("没有更多数据，同步完成");
                    break;
                }

                // 批量保存到数据库
                log.info("保存第 {} 批数据到数据库，共 {} 条", batchNumber, items.size());
                saveItemsToCache(items, libraryId);

                totalCount += items.size();
                startIndex += limit;
                batchNumber++;

                // 如果返回的数量少于limit，说明已经是最后一页
                if (items.size() < limit) {
                    log.info("已获取所有数据（最后一批数据量: {}）", items.size());
                    break;
                }

                // 如果有总数，可以显示进度
                if (pagedResult.getTotalCount() != null && pagedResult.getTotalCount() > 0) {
                    int progress = (int) ((totalCount * 100.0) / pagedResult.getTotalCount());
                    log.info("同步进度: {}/{} ({}%)", totalCount, pagedResult.getTotalCount(), progress);
                }

            } catch (Exception e) {
                log.error("同步第 {} 批数据时出错: {}", batchNumber, e.getMessage(), e);
                throw e;
            }
        }

        log.info("媒体库 {} 全量同步完成，共 {} 个媒体项", libraryId, totalCount);
        return totalCount;
    }

    /**
     * 保存媒体库列表到缓存
     */
    private void saveLibrariesToCache(List<EmbyLibrary> libraries) {
        Long configId = getCurrentEmbyConfigId();
        LocalDateTime now = LocalDateTime.now();

        for (EmbyLibrary library : libraries) {
            EmbyLibraryCache cache = new EmbyLibraryCache();
            cache.setId(library.getId());
            cache.setEmbyConfigId(configId);  // 设置配置ID
            cache.setName(library.getName());
            cache.setCollectionType(library.getCollectionType());
            cache.setItemCount(library.getItemCount());
            cache.setLocations(JSONUtil.toJsonStr(library.getLocations()));
            cache.setServerId(library.getServerId());
            cache.setLastSyncTime(now);

            // 使用 wrapper 查询是否存在（因为是联合主键）
            LambdaQueryWrapper<EmbyLibraryCache> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(EmbyLibraryCache::getId, library.getId())
                    .eq(EmbyLibraryCache::getEmbyConfigId, configId);
            EmbyLibraryCache existing = libraryCacheMapper.selectOne(wrapper);

            if (existing != null) {
                libraryCacheMapper.updateById(cache);
            } else {
                libraryCacheMapper.insert(cache);
            }
        }
    }

    /**
     * 保存媒体项列表到缓存
     */
    private void saveItemsToCache(List<EmbyItem> items, String libraryId) {
        LocalDateTime now = LocalDateTime.now();

        for (EmbyItem item : items) {
            // 强制设置 parentId 为媒体库 ID，确保查询时能找到
            item.setParentId(libraryId);
            saveItemToCache(item);
        }
    }

    /**
     * 保存单个媒体项到缓存
     */
    private void saveItemToCache(EmbyItem item) {
        Long configId = getCurrentEmbyConfigId();
        LocalDateTime now = LocalDateTime.now();

        EmbyItemCache cache = new EmbyItemCache();
        cache.setId(item.getId());
        cache.setEmbyConfigId(configId);  // 设置配置ID
        cache.setName(item.getName());
        cache.setOriginalTitle(item.getOriginalTitle());
        cache.setType(item.getType());
        cache.setParentId(item.getParentId());
        cache.setProductionYear(item.getProductionYear());

        if (item.getCommunityRating() != null) {
            cache.setCommunityRating(BigDecimal.valueOf(item.getCommunityRating()));
        }

        cache.setOfficialRating(item.getOfficialRating());
        cache.setOverview(item.getOverview());
        cache.setGenres(JSONUtil.toJsonStr(item.getGenres()));
        cache.setTags(JSONUtil.toJsonStr(item.getTags()));
        cache.setStudios(JSONUtil.toJsonStr(item.getStudios()));
        cache.setPeople(JSONUtil.toJsonStr(item.getPeople()));
        cache.setPath(item.getPath());
        cache.setSize(item.getSize());
        cache.setPlayCount(item.getPlayCount());
        cache.setMediaSources(JSONUtil.toJsonStr(item.getMediaSources()));
        cache.setServerId(item.getServerId());
        cache.setLastSyncTime(now);

        // 使用 wrapper 查询是否存在（因为是联合主键）
        LambdaQueryWrapper<EmbyItemCache> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmbyItemCache::getId, item.getId())
                .eq(EmbyItemCache::getEmbyConfigId, configId);
        EmbyItemCache existing = itemCacheMapper.selectOne(wrapper);

        if (existing != null) {
            itemCacheMapper.updateById(cache);
        } else {
            itemCacheMapper.insert(cache);
        }
    }

    /**
     * 转换缓存对象为DTO
     */
    private EmbyLibrary convertToLibrary(EmbyLibraryCache cache) {
        EmbyLibrary library = new EmbyLibrary();
        library.setId(cache.getId());
        library.setName(cache.getName());
        library.setCollectionType(cache.getCollectionType());
        library.setItemCount(cache.getItemCount());
        library.setServerId(cache.getServerId());

        if (cache.getLocations() != null) {
            library.setLocations(JSONUtil.toList(cache.getLocations(), String.class));
        }

        return library;
    }

    /**
     * 转换缓存对象为DTO
     */
    private EmbyItem convertToItem(EmbyItemCache cache) {
        EmbyItem item = new EmbyItem();
        item.setId(cache.getId());
        item.setName(cache.getName());
        item.setOriginalTitle(cache.getOriginalTitle());
        item.setType(cache.getType());
        item.setParentId(cache.getParentId());
        item.setProductionYear(cache.getProductionYear());

        if (cache.getCommunityRating() != null) {
            item.setCommunityRating(cache.getCommunityRating().doubleValue());
        }

        item.setOfficialRating(cache.getOfficialRating());
        item.setOverview(cache.getOverview());
        item.setPath(cache.getPath());
        item.setSize(cache.getSize());
        item.setPlayCount(cache.getPlayCount());
        item.setServerId(cache.getServerId());

        if (cache.getGenres() != null) {
            item.setGenres(JSONUtil.toList(cache.getGenres(), String.class));
        }
        if (cache.getTags() != null) {
            item.setTags(JSONUtil.toList(cache.getTags(), String.class));
        }
        if (cache.getStudios() != null) {
            item.setStudios(JSONUtil.toList(cache.getStudios(), String.class));
        }
        if (cache.getPeople() != null) {
            item.setPeople(JSONUtil.toList(cache.getPeople(), String.class));
        }
        if (cache.getMediaSources() != null) {
            item.setMediaSources(JSONUtil.toList(cache.getMediaSources(), EmbyItem.MediaSource.class));
        }

        return item;
    }

    @Override
    public List<EmbyGenre> getAllGenres(boolean forceRefresh) {
        Long configId = getCurrentEmbyConfigId();
        // 直接从数据库获取
        log.info("从数据库获取类型列表 (configId={})", configId);

        LambdaQueryWrapper<EmbyGenreCache> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmbyGenreCache::getEmbyConfigId, configId);

        List<EmbyGenreCache> cacheList = genreCacheMapper.selectList(wrapper);

        if (cacheList.isEmpty()) {
            log.warn("数据库中没有类型数据，请先执行同步操作");
            throw new BusinessException("数据库中没有类型数据，请先点击\"同步所有数据\"按钮进行同步");
        }

        return cacheList.stream().map(this::convertToGenre).collect(Collectors.toList());
    }

    @Override
    public List<EmbyGenre> getAllTags(boolean forceRefresh) {
        Long configId = getCurrentEmbyConfigId();
        // 直接从数据库获取
        log.info("从数据库获取标签列表 (configId={})", configId);

        LambdaQueryWrapper<EmbyTagCache> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmbyTagCache::getEmbyConfigId, configId);

        List<EmbyTagCache> cacheList = tagCacheMapper.selectList(wrapper);

        if (cacheList.isEmpty()) {
            log.warn("数据库中没有标签数据，请先执行同步操作");
            throw new BusinessException("数据库中没有标签数据，请先点击\"同步所有数据\"按钮进行同步");
        }

        return cacheList.stream().map(this::convertToTag).collect(Collectors.toList());
    }

    @Override
    public List<EmbyGenre> getAllStudios(boolean forceRefresh) {
        Long configId = getCurrentEmbyConfigId();
        // 直接从数据库获取
        log.info("从数据库获取工作室列表 (configId={})", configId);

        LambdaQueryWrapper<EmbyStudioCache> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmbyStudioCache::getEmbyConfigId, configId);

        List<EmbyStudioCache> cacheList = studioCacheMapper.selectList(wrapper);

        if (cacheList.isEmpty()) {
            log.warn("数据库中没有工作室数据，请先执行同步操作");
            throw new BusinessException("数据库中没有工作室数据，请先点击\"同步所有数据\"按钮进行同步");
        }

        return cacheList.stream().map(this::convertToStudio).collect(Collectors.toList());
    }

    /**
     * 保存类型列表到缓存
     */
    private void saveGenresToCache(List<EmbyGenre> genres) {
        Long configId = getCurrentEmbyConfigId();
        LocalDateTime now = LocalDateTime.now();

        for (EmbyGenre genre : genres) {
            EmbyGenreCache cache = new EmbyGenreCache();
            cache.setId(genre.getId());
            cache.setEmbyConfigId(configId);  // 设置配置ID
            cache.setName(genre.getName());
            cache.setItemCount(genre.getItemCount());
            cache.setLastSyncTime(now);

            // 使用 wrapper 查询是否存在（因为是联合主键）
            LambdaQueryWrapper<EmbyGenreCache> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(EmbyGenreCache::getId, genre.getId())
                    .eq(EmbyGenreCache::getEmbyConfigId, configId);
            EmbyGenreCache existing = genreCacheMapper.selectOne(wrapper);

            if (existing != null) {
                genreCacheMapper.updateById(cache);
            } else {
                genreCacheMapper.insert(cache);
            }
        }
    }

    /**
     * 保存标签列表到缓存
     */
    private void saveTagsToCache(List<EmbyGenre> tags) {
        Long configId = getCurrentEmbyConfigId();
        LocalDateTime now = LocalDateTime.now();

        for (EmbyGenre tag : tags) {
            EmbyTagCache cache = new EmbyTagCache();
            cache.setId(tag.getId());
            cache.setEmbyConfigId(configId);  // 设置配置ID
            cache.setName(tag.getName());
            cache.setItemCount(tag.getItemCount());
            cache.setLastSyncTime(now);

            // 使用 wrapper 查询是否存在（因为是联合主键）
            LambdaQueryWrapper<EmbyTagCache> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(EmbyTagCache::getId, tag.getId())
                    .eq(EmbyTagCache::getEmbyConfigId, configId);
            EmbyTagCache existing = tagCacheMapper.selectOne(wrapper);

            if (existing != null) {
                tagCacheMapper.updateById(cache);
            } else {
                tagCacheMapper.insert(cache);
            }
        }
    }

    /**
     * 保存工作室列表到缓存
     */
    private void saveStudiosToCache(List<EmbyGenre> studios) {
        Long configId = getCurrentEmbyConfigId();
        LocalDateTime now = LocalDateTime.now();

        for (EmbyGenre studio : studios) {
            EmbyStudioCache cache = new EmbyStudioCache();
            cache.setId(studio.getId());
            cache.setEmbyConfigId(configId);  // 设置配置ID
            cache.setName(studio.getName());
            cache.setItemCount(studio.getItemCount());
            cache.setLastSyncTime(now);

            // 使用 wrapper 查询是否存在（因为是联合主键）
            LambdaQueryWrapper<EmbyStudioCache> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(EmbyStudioCache::getId, studio.getId())
                    .eq(EmbyStudioCache::getEmbyConfigId, configId);
            EmbyStudioCache existing = studioCacheMapper.selectOne(wrapper);

            if (existing != null) {
                studioCacheMapper.updateById(cache);
            } else {
                studioCacheMapper.insert(cache);
            }
        }
    }

    /**
     * 转换缓存对象为DTO
     */
    private EmbyGenre convertToGenre(EmbyGenreCache cache) {
        EmbyGenre genre = new EmbyGenre();
        genre.setId(cache.getId());
        genre.setName(cache.getName());
        genre.setItemCount(cache.getItemCount());
        genre.setType("Genre");
        return genre;
    }

    /**
     * 转换缓存对象为DTO
     */
    private EmbyGenre convertToTag(EmbyTagCache cache) {
        EmbyGenre tag = new EmbyGenre();
        tag.setId(cache.getId());
        tag.setName(cache.getName());
        tag.setItemCount(cache.getItemCount());
        tag.setType("Tag");
        return tag;
    }

    /**
     * 转换缓存对象为DTO
     */
    private EmbyGenre convertToStudio(EmbyStudioCache cache) {
        EmbyGenre studio = new EmbyGenre();
        studio.setId(cache.getId());
        studio.setName(cache.getName());
        studio.setItemCount(cache.getItemCount());
        studio.setType("Studio");
        return studio;
    }
}
