package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订阅批量搜索任务实体
 *
 * @author GD Upload Manager
 * @since 2026-01-25
 */
@Data
@TableName("subscribe_batch_task")
public class SubscribeBatchTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 订阅总数
     */
    private Integer totalCount;

    /**
     * 已完成数量
     */
    private Integer completedCount;

    /**
     * 成功数量
     */
    private Integer successCount;

    /**
     * 失败数量
     */
    private Integer failedCount;

    /**
     * 任务状态: PENDING-待执行, RUNNING-执行中, PAUSED-已暂停, COMPLETED-已完成, FAILED-失败
     */
    private String status;

    /**
     * 最小延迟(分钟)
     */
    private Integer delayMin;

    /**
     * 最大延迟(分钟)
     */
    private Integer delayMax;

    /**
     * 订阅JSON数据
     */
    private String jsonData;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
