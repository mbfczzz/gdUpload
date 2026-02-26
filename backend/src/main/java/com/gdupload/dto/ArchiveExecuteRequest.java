package com.gdupload.dto;

import lombok.Data;

/**
 * 执行归档请求 DTO
 */
@Data
public class ArchiveExecuteRequest {

    /** 原始文件完整路径 */
    private String originalPath;

    /** 归档后新文件名（已重命名） */
    private String newFilename;

    /** 媒体分类（如 "日语动漫"） */
    private String category;

    /** 目录名（如 "名侦探柯南-1996-[tmdbid=30984]"） */
    private String dirName;

    /** 季目录（如 "Season 1"），无则留空 */
    private String seasonDir;

    /** TMDB ID */
    private String tmdbId;

    /** TMDB标题（用于记录） */
    private String tmdbTitle;

    /** 处理方式：tmdb / ai / manual */
    private String processMethod;

    /** 关联的 file_info ID（可选） */
    private Long fileInfoId;
}
