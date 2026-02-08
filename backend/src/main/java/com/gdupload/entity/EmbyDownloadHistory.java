package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Emby下载历史
 */
@Data
@TableName("emby_download_history")
public class EmbyDownloadHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Emby媒体项ID
     */
    private String embyItemId;

    /**
     * Emby配置ID
     */
    private Long embyConfigId;

    /**
     * 下载状态：success-成功, failed-失败
     */
    private String downloadStatus;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
