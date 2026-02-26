-- 归档历史记录表
-- 记录每次归档操作的完整信息：成功、失败、需要人工处理

CREATE TABLE IF NOT EXISTS `archive_history`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `original_path`     VARCHAR(1000) NOT NULL COMMENT '原始文件完整路径',
    `original_filename` VARCHAR(500) NOT NULL COMMENT '原始文件名',
    `new_path`          VARCHAR(1000)          DEFAULT NULL COMMENT '归档后新路径',
    `new_filename`      VARCHAR(500)           DEFAULT NULL COMMENT '归档后新文件名',
    `tmdb_id`           VARCHAR(50)            DEFAULT NULL COMMENT 'TMDB ID',
    `tmdb_title`        VARCHAR(500)           DEFAULT NULL COMMENT 'TMDB标题',
    `category`          VARCHAR(100)           DEFAULT NULL COMMENT '媒体分类（如：日语动漫、欧美电影等）',
    `season_dir`        VARCHAR(50)            DEFAULT NULL COMMENT '季目录（如：Season 1）',
    `status`            VARCHAR(20)  NOT NULL  DEFAULT 'pending'
        COMMENT '状态：success=归档成功 / failed=归档失败 / manual_required=需要人工处理',
    `process_method`    VARCHAR(20)            DEFAULT NULL
        COMMENT '处理方式：regex=仅正则解析 / tmdb=TMDB匹配 / ai=AI识别 / manual=手动填写',
    `fail_reason`       TEXT                   DEFAULT NULL COMMENT '失败原因或说明',
    `remark`            VARCHAR(1000)          DEFAULT NULL COMMENT '人工处理备注',
    `deleted`           TINYINT(1)   NOT NULL  DEFAULT 0 COMMENT '逻辑删除（0=未删除，1=已删除）',
    `create_time`       DATETIME               DEFAULT NULL COMMENT '创建时间',
    `update_time`       DATETIME               DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_time` (`create_time`),
    INDEX `idx_tmdb_id` (`tmdb_id`),
    INDEX `idx_category` (`category`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '归档历史记录表';
