package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Emby媒体库实体
 */
@Data
@TableName("emby_library")
public class EmbyLibraryCache {

    @TableId
    private String id;

    /**
     * Emby配置ID（关联 emby_config 表）
     */
    private Long embyConfigId;

    /**
     * 媒体库名称
     */
    private String name;

    /**
     * 集合类型：movies, tvshows, music等
     */
    private String collectionType;

    /**
     * 媒体项数量
     */
    private Integer itemCount;

    /**
     * 路径列表（JSON数组）
     */
    private String locations;

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
