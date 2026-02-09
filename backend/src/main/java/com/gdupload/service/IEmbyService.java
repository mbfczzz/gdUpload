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
     * 获取电视剧的所有剧集
     *
     * @param seriesId 电视剧ID
     * @return 剧集列表
     */
    List<EmbyItem> getSeriesEpisodes(String seriesId);

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

    /**
     * 获取媒体项的下载URL
     * 包括：直接下载URL、流媒体URL等
     *
     * @param itemId 媒体项ID
     * @return URL映射
     */
    Map<String, String> getDownloadUrls(String itemId);

    /**
     * 代理下载媒体项（模拟播放器请求）
     * 通过后端请求视频流并转发给前端
     *
     * @param itemId 媒体项ID
     * @param filename 下载文件名
     * @param response HTTP响应
     */
    void proxyDownload(String itemId, String filename, javax.servlet.http.HttpServletResponse response) throws Exception;

    /**
     * 下载媒体项到服务器本地
     * 下载到 /data/emby 目录
     *
     * @param itemId 媒体项ID
     * @return 下载结果（包含文件路径、大小等信息）
     */
    Map<String, Object> downloadToServer(String itemId) throws Exception;

    /**
     * 异步下载媒体项到服务器本地
     * 下载到 /data/emby 目录
     *
     * @param itemId 媒体项ID
     */
    void downloadToServerAsync(String itemId);

    /**
     * 批量异步下载媒体项到服务器本地
     * 后端依次下载，不依赖前端保持连接
     *
     * @param itemIds 媒体项ID列表
     * @return 任务ID（用于在任务管理页面查看进度）
     */
    Long batchDownloadToServerAsync(List<String> itemIds);

    /**
     * 批量异步下载媒体项到服务器本地（支持断点续传）
     * 后端依次下载，不依赖前端保持连接
     *
     * @param itemIds 媒体项ID列表
     * @param existingTaskId 已存在的任务ID（用于恢复任务），如果为null则创建新任务
     * @return 任务ID（用于在任务管理页面查看进度）
     */
    Long batchDownloadToServerAsync(List<String> itemIds, Long existingTaskId);

    /**
     * 获取批量下载进度
     *
     * @return 进度信息
     */
    Map<String, Object> getBatchDownloadProgress();

    /**
     * 暂停下载任务
     *
     * @param taskId 任务ID
     * @return 是否成功
     */
    boolean pauseDownloadTask(Long taskId);

    /**
     * 取消下载任务
     *
     * @param taskId 任务ID
     * @return 是否成功
     */
    boolean cancelDownloadTask(Long taskId);
}
