package com.gdupload.dto;

import lombok.Data;
import java.util.List;

/**
 * Emby媒体项信息
 */
@Data
public class EmbyItem {

    /**
     * 媒体项ID
     */
    private String id;

    /**
     * 名称
     */
    private String name;

    /**
     * 原始名称
     */
    private String originalTitle;

    /**
     * 类型（Movie, Series, Season, Episode, Audio等）
     */
    private String type;

    /**
     * 媒体类型（Video, Audio等）
     */
    private String mediaType;

    /**
     * 父级ID
     */
    private String parentId;

    /**
     * 电视剧ID（仅Episode类型有效）
     */
    private String seriesId;

    /**
     * 电视剧名称（仅Episode类型有效）
     */
    private String seriesName;

    /**
     * 季数（仅Episode类型有效）
     */
    private Integer parentIndexNumber;

    /**
     * 集数（仅Episode类型有效）
     */
    private Integer indexNumber;

    /**
     * 文件路径
     */
    private String path;

    /**
     * 文件大小（字节）
     */
    private Long size;

    /**
     * 年份
     */
    private Integer productionYear;

    /**
     * 首映日期
     */
    private String premiereDate;

    /**
     * 评分
     */
    private Double communityRating;

    /**
     * 官方评分
     */
    private String officialRating;

    /**
     * 简介
     */
    private String overview;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 类型列表
     */
    private List<String> genres;

    /**
     * 工作室列表
     */
    private List<String> studios;

    /**
     * 演员列表
     */
    private List<String> people;

    /**
     * 运行时长（分钟）
     */
    private Long runTimeTicks;

    /**
     * 是否已播放
     */
    private Boolean played;

    /**
     * 播放次数
     */
    private Integer playCount;

    /**
     * 创建时间
     */
    private String dateCreated;

    /**
     * 最后修改时间
     */
    private String dateModified;

    /**
     * 媒体源信息
     */
    private List<MediaSource> mediaSources;

    /**
     * 服务器ID
     */
    private String serverId;

    /**
     * 外部ID映射（如 TMDB, IMDB 等）
     */
    private java.util.Map<String, String> providerIds;

    @Data
    public static class MediaSource {
        private String id;
        private String path;
        private String container;
        private Long size;
        private Long bitrate;
    }
}
