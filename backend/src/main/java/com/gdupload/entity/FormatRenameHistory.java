package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 格式化命名历史记录（每个文件一条）
 */
@Data
@TableName("format_rename_history")
public class FormatRenameHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属任务 ID */
    private Long taskId;

    /** 原始文件名 */
    private String originalFilename;

    /** 新文件名（重命名后，跳过/失败时为 null） */
    private String newFilename;

    /** 文件在远端的相对路径 */
    private String filePath;

    /** 状态：renamed / skipped / failed */
    private String status;

    /** 跳过原因（status=skipped 时填写） */
    private String skipReason;

    /** 失败原因（status=failed 时填写） */
    private String failReason;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
