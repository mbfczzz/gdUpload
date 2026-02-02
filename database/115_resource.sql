-- 115资源表
CREATE TABLE IF NOT EXISTS `115_resource` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` VARCHAR(500) NOT NULL COMMENT '资源名称',
  `type` VARCHAR(50) DEFAULT NULL COMMENT '资源类型',
  `size` DECIMAL(10,2) DEFAULT NULL COMMENT '资源大小（GB）',
  `url` VARCHAR(500) NOT NULL COMMENT '115分享链接',
  `access_code` VARCHAR(50) DEFAULT NULL COMMENT '访问码',
  `sort` INT DEFAULT 0 COMMENT '排序',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_name` (`name`(255)),
  INDEX `idx_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='115资源表';

-- 插入示例数据
INSERT INTO `115_resource` (`name`, `type`, `size`, `url`, `access_code`, `sort`) VALUES
('韫色过浓 (2020)', '国产剧', 168.87, 'https://115.com/s/swzxneb36gr', '1234', 1),
('天衣无缝 (2019)', '国产剧', 16.59, 'https://115.com/s/swzxnlo36gr', '1234', 2),
('爱情万万岁 (2016)', '国产剧', 30.98, 'https://115.com/s/swzx3zq36gr', '1234', 3),
('关中匪事 (2003)', '国产剧', 8.66, 'https://115.com/s/swzx3kc36gr', '1234', 4),
('谈判专家 (2002)', '国产剧', 23.96, 'https://115.com/s/swzxnf136gr', '1234', 5),
('少帅 (2015)', '国产剧', 109.73, 'https://115.com/s/swzxn6j36gr', '1234', 6);
