package com.gdupload.dto;

import lombok.Data;

import java.util.List;

/**
 * TMDB搜索结果项 DTO
 */
@Data
public class ArchiveTmdbItem {

    /** TMDB ID */
    private Integer tmdbId;

    /** 中文标题 */
    private String title;

    /** 原语言标题 */
    private String originalTitle;

    /** 原语言代码（如 "ja", "zh", "ko", "en"） */
    private String originalLanguage;

    /** 年份（如 "1996"） */
    private String year;

    /** 媒体类型：movie / tv */
    private String type;

    /** 类型 ID 列表（TMDB genre_ids） */
    private List<Integer> genreIds;

    /** 简介 */
    private String overview;

    /** 海报路径（TMDB相对路径） */
    private String posterPath;

    /** 建议分类（如 "日语动漫"） */
    private String suggestedCategory;

    /** 建议目录名（如 "名侦探柯南-1996-[tmdbid=30984]"） */
    private String suggestedDirName;

    /** TMDB 标记为成人内容 */
    private Boolean isAdult;
}
