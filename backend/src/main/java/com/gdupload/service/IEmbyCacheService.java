package com.gdupload.service;

import com.gdupload.dto.EmbyGenre;
import com.gdupload.dto.EmbyItem;
import com.gdupload.dto.EmbyLibrary;

import java.util.List;
import java.util.Map;

/**
 * Emby缓存服务接口
 */
public interface IEmbyCacheService {

    /**
     * 获取所有媒体库（从数据库）
     *
     * @param forceRefresh 已废弃，保留参数仅为兼容性
     * @return 媒体库列表
     */
    List<EmbyLibrary> getAllLibraries(boolean forceRefresh);

    /**
     * 获取媒体库的媒体项（分页，从数据库）
     *
     * @param libraryId      媒体库ID
     * @param startIndex     起始索引
     * @param limit          数量限制
     * @param transferStatus 转存状态筛选（可选：success-成功, failed-失败, none-未转存）
     * @param downloadStatus 下载状态筛选（可选：success-成功, failed-失败, none-未下载）
     * @param forceRefresh   已废弃，保留参数仅为兼容性
     * @return 媒体项分页结果
     */
    Map<String, Object> getLibraryItemsPaged(String libraryId, int startIndex, int limit, String transferStatus, String downloadStatus, boolean forceRefresh);

    /**
     * 获取媒体项详情（从数据库）
     *
     * @param itemId       媒体项ID
     * @param forceRefresh 已废弃，保留参数仅为兼容性
     * @return 媒体项详情
     */
    EmbyItem getItemDetail(String itemId, boolean forceRefresh);

    /**
     * 搜索媒体项（从数据库）
     *
     * @param keyword      搜索关键词
     * @param forceRefresh 已废弃，保留参数仅为兼容性
     * @return 搜索结果
     */
    List<EmbyItem> searchItems(String keyword, boolean forceRefresh);

    /**
     * 一次性全量同步所有数据到数据库
     * 包括：媒体库、所有媒体项、类型、标签、工作室
     *
     * @return 同步结果
     */
    Map<String, Object> syncAllData();

    /**
     * 全量同步单个媒体库到数据库
     *
     * @param libraryId 媒体库ID
     * @return 是否成功
     */
    boolean syncLibrary(String libraryId);

    /**
     * 清空所有数据库缓存
     *
     * @return 是否成功
     */
    boolean clearAllCache();

    /**
     * 检查数据库是否有缓存数据
     *
     * @return 是否存在缓存数据
     */
    boolean hasCacheData();

    /**
     * 获取所有类型（从数据库）
     *
     * @param forceRefresh 已废弃，保留参数仅为兼容性
     * @return 类型列表
     */
    List<EmbyGenre> getAllGenres(boolean forceRefresh);

    /**
     * 获取所有标签（从数据库）
     *
     * @param forceRefresh 已废弃，保留参数仅为兼容性
     * @return 标签列表
     */
    List<EmbyGenre> getAllTags(boolean forceRefresh);

    /**
     * 获取所有工作室（从数据库）
     *
     * @param forceRefresh 已废弃，保留参数仅为兼容性
     * @return 工作室列表
     */
    List<EmbyGenre> getAllStudios(boolean forceRefresh);
}
