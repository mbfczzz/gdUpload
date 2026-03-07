package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 归档历史记录实体
 */
@Data
@TableName("archive_history")
public class ArchiveHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始文件路径 */
    private String originalPath;

    /** 原始文件名 */
    private String originalFilename;

    /** 归档后新路径 */
    private String newPath;

    /** 归档后新文件名 */
    private String newFilename;

    /** TMDB ID */
    private String tmdbId;

    /** TMDB标题 */
    private String tmdbTitle;

    /** 媒体分类（如：日语动漫、国产剧、欧美电影等） */
    private String category;

    /** 季目录（如：Season 1），TV剧集才有 */
    private String seasonDir;

    /** 状态：success / failed / manual_required */
    private String status;

    /** 处理方式：regex / tmdb / ai / manual */
    private String processMethod;

    /** 失败或人工处理原因 */
    private String failReason;

    /** 备注（人工处理时用户填写） */
    private String remark;

    /** 所属批量任务ID（单文件归档时为空） */
    private Long batchTaskId;

    /** rclone配置名（云端文件归档时记录，本地文件为空） */
    private String rcloneConfigName;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
