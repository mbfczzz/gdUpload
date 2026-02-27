package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批量归档任务实体
 */
@Data
@TableName("archive_batch_task")
public class ArchiveBatchTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务名称（自动生成：批量归档_文件夹名_时间戳） */
    private String taskName;

    /** rclone 配置名 */
    private String rcloneConfigName;

    /** 源目录路径（相对于远程根目录） */
    private String sourcePath;

    /** 状态：PENDING / RUNNING / PAUSED / COMPLETED / PARTIAL / FAILED */
    private String status;

    /** 扫描到的媒体文件总数 */
    private Integer totalFiles;

    /** 已处理文件数 */
    private Integer processedFiles;

    /** 成功归档数 */
    private Integer successCount;

    /** 失败数 */
    private Integer failedCount;

    /** 待人工处理数 */
    private Integer manualCount;

    /** 当前正在处理的文件名 */
    private String currentFile;

    /** 错误信息（任务级别） */
    @TableField("error_message")
    private String errorMessage;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
