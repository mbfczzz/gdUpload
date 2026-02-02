package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订阅批量搜索任务日志实体
 *
 * @author GD Upload Manager
 * @since 2026-01-25
 */
@Data
@TableName("subscribe_batch_log")
public class SubscribeBatchLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 订阅ID
     */
    private Integer subscribeId;

    /**
     * 订阅名称
     */
    private String subscribeName;

    /**
     * 状态: SUCCESS-成功, FAILED-失败
     */
    private String status;

    /**
     * 延迟秒数
     */
    private Integer delaySeconds;

    /**
     * 请求URL
     */
    private String requestUrl;

    /**
     * 请求数据
     */
    private String requestData;

    /**
     * 响应数据
     */
    private String responseData;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 执行时间
     */
    private LocalDateTime executeTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
