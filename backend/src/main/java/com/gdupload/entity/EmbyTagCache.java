package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Emby标签缓存实体
 */
@Data
@TableName("emby_tag")
public class EmbyTagCache {

    /**
     * 标签ID（来自Emby）
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 标签名称
     */
    private String name;

    /**
     * 媒体项数量
     */
    private Integer itemCount;

    /**
     * Emby配置ID（关联 emby_config 表）
     */
    private Long embyConfigId;

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
