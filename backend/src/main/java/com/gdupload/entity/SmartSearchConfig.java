package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能搜索配置实体
 */
@Data
@TableName("smart_search_config")
public class SmartSearchConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（预留多用户支持）
     */
    private String userId;

    /**
     * 配置名称
     */
    private String configName;

    /**
     * 配置类型：cloud_config, ai_config, search_config
     */
    private String configType;

    /**
     * 配置数据（JSON格式）
     */
    private String configData;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
