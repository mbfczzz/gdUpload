package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 格式化命名任务实体
 */
@Data
@TableName("format_rename_task")
public class FormatRenameTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务名称（自动生成：格式化命名_文件夹名_时间戳） */
    private String taskName;

    /** GD 账号 ID */
    private Long accountId;

    /** rclone 配置名 */
    private String rcloneConfigName;

    /** 扫描目录路径（相对于远程根目录） */
    private String dirPath;

    /** 状态：PENDING / RUNNING / PAUSED / COMPLETED / FAILED */
    private String status;

    /** 扫描到的媒体文件总数 */
    private Integer totalFiles;

    /** 已处理文件数 */
    private Integer processedFiles;

    /** 重命名成功数 */
    private Integer renamedCount;

    /** 跳过数（已有编码或文件名无变化） */
    private Integer skippedCount;

    /** 失败数 */
    private Integer failedCount;

    /** 当前正在处理的文件名 */
    private String currentFile;

    /** 任务级别错误信息 */
    @TableField("error_message")
    private String errorMessage;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
