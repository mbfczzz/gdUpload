-- 115资源表示例数据
-- 表结构已存在，只插入示例数据

-- 清空现有数据（可选）
-- TRUNCATE TABLE `115_resource`;

-- 插入示例数据
INSERT INTO `115_resource` (`name`, `type`, `size`, `url`, `code`) VALUES
('韫色过浓 (2020)', '国产剧', '168.87', 'https://115.com/s/swzxneb36gr', '1234'),
('天衣无缝 (2019)', '国产剧', '16.59', 'https://115.com/s/swzxnlo36gr', '1234'),
('爱情万万岁 (2016)', '国产剧', '30.98', 'https://115.com/s/swzx3zq36gr', '1234'),
('关中匪事 (2003)', '国产剧', '8.66', 'https://115.com/s/swzx3kc36gr', '1234'),
('谈判专家 (2002)', '国产剧', '23.96', 'https://115.com/s/swzxnf136gr', '1234'),
('少帅 (2015)', '国产剧', '109.73', 'https://115.com/s/swzxn6j36gr', '1234');

-- 查看插入结果
SELECT * FROM `115_resource` ORDER BY id;
