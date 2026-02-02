package com.gdupload.service;

import com.gdupload.dto.EmbyGenre;
import com.gdupload.dto.EmbyItem;
import com.gdupload.dto.EmbyLibrary;
import com.gdupload.dto.PagedResult;

import java.util.List;
import java.util.Map;

/**
 * Emby服务接口
 */
public interface IEmbyService {

    /**
     * 获取所有媒体库
     *
     * @return 媒体库列表
     */
    List<EmbyLibrary> getAllLibraries();

    /**
     * 获取指定媒体库的媒体项数量
     *
     * @param libraryId 媒体库ID
     * @return 媒体项数量
     */
    Integer getLibraryItemCount(String libraryId);

    /**
     * 获取指定媒体库的所有媒体项
     *
     * @param libraryId 媒体库ID
     * @return 媒体项列表
     */
    List<EmbyItem> getLibraryItems(String libraryId);

    /**
     * 获取指定媒体库的所有媒体项（带分页参数）
     *
     * @param libraryId 媒体库ID
     * @param startIndex 起始索引
     * @param limit 每页数量
     * @return 媒体项列表
     */
    List<EmbyItem> getLibraryItems(String libraryId, Integer startIndex, Integer limit);

    /**
     * 获取指定媒体库的所有媒体项（带分页结果）
     *
     * @param libraryId 媒体库ID
     * @param startIndex 起始索引
     * @param limit 每页数量
     * @return 分页结果
     */
    PagedResult<EmbyItem> getLibraryItemsPaged(String libraryId, Integer startIndex, Integer limit);

    /**
     * 获取媒体项详情
     *
     * @param itemId 媒体项ID
     * @return 媒体项详情
     */
    EmbyItem getItemDetail(String itemId);

    /**
     * 获取所有类型
     *
     * @return 类型列表
     */
    List<EmbyGenre> getAllGenres();

    /**
     * 获取所有标签
     *
     * @return 标签列表
     */
    List<EmbyGenre> getAllTags();

    /**
     * 获取所有工作室
     *
     * @return 工作室列表
     */
    List<EmbyGenre> getAllStudios();

    /**
     * 搜索媒体项
     *
     * @param keyword 关键词
     * @return 媒体项列表
     */
    List<EmbyItem> searchItems(String keyword);

    /**
     * 获取Emby服务器信息
     *
     * @return 服务器信息
     */
    Map<String, Object> getServerInfo();

    /**
     * 测试Emby连接
     *
     * @return 是否连接成功
     */
    boolean testConnection();

    /**
     * 同步所有媒体库数据到本地
     *
     * @return 同步结果统计
     */
    Map<String, Object> syncAllLibraries();
}
