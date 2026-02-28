package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * STRM 文件记录实体
 */
@Data
@TableName("strm_file_record")
public class StrmFileRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联监控配置ID */
    private Long watchConfigId;

    /** rclone 远程名 */
    private String gdRemote;

    /** GD 文件相对路径 */
    private String relFilePath;

    /** rclone 返回的修改时间（变更检测用） */
    private String fileModTime;

    /** 本地 .strm 路径 */
    private String strmLocalPath;

    /** 本地 .nfo 路径 */
    private String nfoLocalPath;

    /** 节目根目录（删除时清理用） */
    private String showDir;

    /** TMDB ID */
    private Integer tmdbId;

    /** 状态: success/failed/deleted */
    private String status;

    /** 失败原因（IGNORED 策略：updateById 时即便为 null 也会写入 DB，确保成功重处理后能清除旧错误信息） */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String failReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
