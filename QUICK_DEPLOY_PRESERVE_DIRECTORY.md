# 保留目录结构上传 - 快速部署指南

## 修改内容

上传文件时保留目录结构，例如：
- 源路径：`/backdata/done`
- 文件：`/backdata/done/folder1/movie.mp4`
- 上传后：`targetPath/folder1/movie.mp4`

## 部署步骤

### 1. 执行数据库迁移

```bash
# SSH 登录服务器
ssh user@server

# 连接数据库
mysql -u root -p

# 执行迁移脚本
USE gd_upload_manager;

ALTER TABLE `file_info`
ADD COLUMN `relative_path` VARCHAR(1000) NULL COMMENT '相对路径（相对于任务源路径）'
AFTER `file_name`;

# 验证
DESC file_info;
# 应该看到 relative_path 字段

exit
```

### 2. 编译并部署后端

```bash
# 本地编译
cd D:\mbfczzzz\claude\gdUpload\backend
mvn clean package -DskipTests

# 上传到服务器
scp target/gdupload-0.0.1-SNAPSHOT.jar user@server:/work/

# SSH 登录服务器
ssh user@server

# 重启服务
cd /work
./stop.sh
./start.sh

# 查看日志
tail -f /work/nohup.out
```

### 3. 测试

#### 创建测试目录结构

```bash
# 创建测试目录
mkdir -p /backdata/done/folder1/subfolder
mkdir -p /backdata/done/folder2

# 复制测试文件（或创建空文件）
touch /backdata/done/movie1.mp4
touch /backdata/done/folder1/movie2.mkv
touch /backdata/done/folder1/subfolder/movie3.mp4
touch /backdata/done/folder2/movie4.mkv
```

#### 创建上传任务

1. 打开前端页面
2. 进入"创建上传任务"
3. 输入：
   - 任务名称：测试目录结构
   - 源路径：`/backdata/done`
   - 目标路径：`/test_upload`
   - 递归扫描：开启
4. 点击"扫描目录"
5. 查看扫描结果

#### 验证数据库

```sql
# 查看扫描到的文件
SELECT
    file_name,
    relative_path,
    file_path
FROM file_info
WHERE task_id = (SELECT MAX(id) FROM upload_task)
ORDER BY relative_path, file_name;

# 预期结果：
# | file_name  | relative_path      | file_path                                    |
# |------------|--------------------|----------------------------------------------|
# | movie1.mp4 |                    | /backdata/done/movie1.mp4                    |
# | movie2.mkv | folder1            | /backdata/done/folder1/movie2.mkv            |
# | movie3.mp4 | folder1/subfolder  | /backdata/done/folder1/subfolder/movie3.mp4  |
# | movie4.mkv | folder2            | /backdata/done/folder2/movie4.mkv            |
```

#### 启动上传并查看日志

```bash
# 查看上传日志
tail -f /work/nohup.out | grep "准备上传文件"

# 预期日志：
# 准备上传文件: /backdata/done/movie1.mp4 -> gdrive:/test_upload/
# 文件包含相对路径，目标路径: /test_upload/folder1/
# 准备上传文件: /backdata/done/folder1/movie2.mkv -> gdrive:/test_upload/folder1/
# 文件包含相对路径，目标路径: /test_upload/folder1/subfolder/
# 准备上传文件: /backdata/done/folder1/subfolder/movie3.mp4 -> gdrive:/test_upload/folder1/subfolder/
# 文件包含相对路径，目标路径: /test_upload/folder2/
# 准备上传文件: /backdata/done/folder2/movie4.mkv -> gdrive:/test_upload/folder2/
```

#### 验证 Google Drive

登录 Google Drive，查看 `/test_upload` 目录结构：

```
/test_upload/
├── movie1.mp4
├── folder1/
│   ├── movie2.mkv
│   └── subfolder/
│       └── movie3.mp4
└── folder2/
    └── movie4.mkv
```

## 修改的文件

1. **FileInfo.java** - 添加 `relativePath` 字段
2. **FileInfoServiceImpl.java** - 修改扫描逻辑，计算相对路径
3. **UploadServiceImpl.java** - 修改上传逻辑，使用相对路径构建目标路径
4. **数据库** - 添加 `relative_path` 字段

## 注意事项

1. **已有任务**：修改前创建的任务不受影响，会按旧逻辑上传（所有文件在根目录）
2. **路径分隔符**：代码统一使用 `/`，兼容 Windows 和 Linux
3. **特殊字符**：文件名特殊字符处理不受影响
4. **rclone 自动创建目录**：不需要手动创建目标目录

## 故障排查

### 问题1：relative_path 字段为 NULL

**原因：** 数据库字段未添加或代码未正确设置

**解决：**
```sql
-- 检查字段是否存在
DESC file_info;

-- 如果不存在，手动添加
ALTER TABLE `file_info`
ADD COLUMN `relative_path` VARCHAR(1000) NULL COMMENT '相对路径（相对于任务源路径）'
AFTER `file_name`;
```

### 问题2：上传后目录结构丢失

**原因：** 上传逻辑未正确使用 relative_path

**解决：**
```bash
# 查看后端日志
tail -f /work/nohup.out | grep "文件包含相对路径"

# 如果没有这条日志，说明代码未正确部署
# 重新编译并部署
```

### 问题3：编译错误

**错误：** `cannot find symbol: variable relativePath`

**解决：**
```bash
# 确保 FileInfo.java 中添加了字段
# 重新编译
cd backend
mvn clean package -DskipTests
```

## 完成

现在上传文件时会自动保留目录结构！
