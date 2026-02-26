package com.gdupload.dto;

import lombok.Data;

/**
 * 文件名解析结果 DTO
 */
@Data
public class ArchiveAnalyzeResult {

    /** 原始文件名 */
    private String originalFilename;

    /** 作品名称 */
    private String title;

    /** 季数（如 "01"），电影为 null */
    private String season;

    /** 集数（如 "1109"），电影为 null */
    private String episode;

    /** 分辨率（如 "1080p", "4K"） */
    private String resolution;

    /** 视频编码（如 "HEVC", "AVC"） */
    private String videoCodec;

    /** 音频编码（如 "AAC", "DTS"） */
    private String audioCodec;

    /** 字幕组（如 "银色子弹字幕组"） */
    private String subtitleGroup;

    /** 来源（如 "WEB-DL", "BluRay"） */
    private String source;

    /** 年份（四位数字，如 "2024"） */
    private String year;

    /** 文件扩展名（如 "mkv"） */
    private String extension;

    /** 媒体类型：tv / movie */
    private String mediaType;

    /** 建议的新文件名 */
    private String suggestedFilename;

    /** 解析来源：regex / ai */
    private String analyzeSource;
}
