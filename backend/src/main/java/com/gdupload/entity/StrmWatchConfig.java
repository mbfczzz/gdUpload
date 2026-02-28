package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * STRM 监控配置实体
 */
@Data
@TableName("strm_watch_config")
public class StrmWatchConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 配置名称 */
    private String name;

    /** rclone 远程名 */
    private String gdRemote;

    /** GD 源目录 */
    private String gdSourcePath;

    /** 本地输出根目录 */
    private String outputPath;

    /** 播放 URL 前缀 */
    private String playUrlBase;

    /** 是否启用: 0-禁用 1-启用 */
    private Integer enabled;

    /** 扫描间隔（分钟） */
    private Integer scanIntervalMinutes;

    /** 上次扫描时间 */
    private LocalDateTime lastScanTime;

    /** 下次扫描时间 */
    private LocalDateTime nextScanTime;

    /** 上次新增文件数 */
    private Integer lastNewCount;

    /** 上次删除文件数 */
    private Integer lastDeletedCount;

    /** 上次更新文件数 */
    private Integer lastUpdatedCount;

    /** 当前有效文件总数 */
    private Integer totalFiles;

    /** 状态: IDLE/RUNNING */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
