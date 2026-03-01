package com.gdupload.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.dto.ArchiveAnalyzeResult;
import com.gdupload.dto.ArchiveExecuteRequest;
import com.gdupload.dto.ArchiveTmdbItem;
import com.gdupload.dto.MediaInfoDto;
import com.gdupload.entity.ArchiveHistory;

import java.util.List;
import java.util.Map;

/**
 * 归档服务接口
 */
public interface IArchiveService {

    /**
     * 用正则解析文件名，提取各组成部分
     */
    ArchiveAnalyzeResult analyzeFilename(String filename);

    /**
     * 用 AI 分析文件名（正则无法解析时的回退方案）
     */
    ArchiveAnalyzeResult aiAnalyzeFilename(String filename);

    /**
     * 批量 AI 分析文件名，一次请求处理多个文件。
     * 返回列表与入参顺序一一对应；若某个文件解析失败则对应位置返回仅含 originalFilename 的空结果。
     */
    List<ArchiveAnalyzeResult> batchAiAnalyzeFilenames(List<String> filenames);

    /**
     * 用 ffprobe 探测媒体文件的真实技术信息（视频编码、音频编码、分辨率）
     * - rcloneConfigName 为空：直接对本地路径运行 ffprobe
     * - rcloneConfigName 非空：通过 rclone cat | ffprobe 读云端文件头
     * 失败时返回 null
     */
    MediaInfoDto getMediaInfo(String filePath, String rcloneConfigName);

    /**
     * 搜索 TMDB，返回最多5条候选结果
     */
    List<ArchiveTmdbItem> searchTmdb(String title, String year, String type);

    /**
     * 执行归档：重命名 + 移动文件 + 记录历史
     */
    Map<String, Object> executeArchive(ArchiveExecuteRequest req);

    /**
     * 将文件标记为需要人工处理
     */
    Map<String, Object> markManual(String originalPath, String originalFilename, String remark);

    /**
     * 通过 TMDB ID 查询电视剧指定集的信息（剧名、集标题、所在季）。
     * 若指定季中找不到该集，会自动遍历其他季查找。
     * 返回 Map 包含：showTitle, episodeTitle, season（格式 "01"）；失败返回 null。
     */
    Map<String, String> fetchTvEpisodeInfo(int tmdbId, int season, int episode);

    /**
     * 获取所有可用分类列表
     */
    List<String> getCategories();

    /**
     * 分页查询归档历史
     */
    IPage<ArchiveHistory> getHistory(Page<ArchiveHistory> page, String status);

    /**
     * 更新人工处理备注
     */
    void updateRemark(Long historyId, String remark);
}
