package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 转存历史记录实体
 */
@Data
@TableName("transfer_history")
public class TransferHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Emby媒体项ID
     */
    private String embyItemId;

    /**
     * Emby媒体项名称
     */
    private String embyItemName;

    /**
     * 年份
     */
    private Integer embyItemYear;

    /**
     * 资源ID
     */
    private String resourceId;

    /**
     * 资源标题
     */
    private String resourceTitle;

    /**
     * 资源链接
     */
    private String resourceUrl;

    /**
     * 匹配分数
     */
    private BigDecimal matchScore;

    /**
     * 云盘类型
     */
    private String cloudType;

    /**
     * 云盘名称
     */
    private String cloudName;

    /**
     * 目标目录ID
     */
    private String parentId;

    /**
     * 转存状态：success, failed, pending
     */
    private String transferStatus;

    /**
     * 转存结果消息
     */
    private String transferMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
