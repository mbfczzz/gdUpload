package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Emby媒体项实体
 */
@Data
@TableName("emby_item")
public class EmbyItemCache {

    @TableId
    private String id;

    /**
     * Emby配置ID（关联 emby_config 表）
     */
    private Long embyConfigId;

    /**
     * 名称
     */
    private String name;

    /**
     * 原始名称
     */
    private String originalTitle;

    /**
     * 类型：Movie, Series, Episode等
     */
    private String type;

    /**
     * 父级ID（媒体库ID）
     */
    private String parentId;

    /**
     * 年份
     */
    private Integer productionYear;

    /**
     * 评分
     */
    private BigDecimal communityRating;

    /**
     * 分级
     */
    private String officialRating;

    /**
     * 简介
     */
    private String overview;

    /**
     * 类型列表（JSON数组）
     */
    private String genres;

    /**
     * 标签列表（JSON数组）
     */
    private String tags;

    /**
     * 工作室列表（JSON数组）
     */
    private String studios;

    /**
     * 演员列表（JSON数组）
     */
    private String people;

    /**
     * 文件路径
     */
    private String path;

    /**
     * 文件大小（字节）
     */
    private Long size;

    /**
     * 播放次数
     */
    private Integer playCount;

    /**
     * 媒体源信息（JSON）
     */
    private String mediaSources;

    /**
     * Emby服务器ID
     */
    private String serverId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 最后同步时间
     */
    private LocalDateTime lastSyncTime;
}
