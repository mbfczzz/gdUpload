# 保留目录结构上传功能

## 需求说明

上传文件时保留目录结构，例如：
- 根目录：`/backdata/done`
- 文件：`/backdata/done/folder1/subfolder/movie.mp4`
- 上传后：`targetPath/folder1/subfolder/movie.mp4`

## 修改内容

### 1. 数据库修改

在 `file_info` 表添加 `relative_path` 字段：

```sql
ALTER TABLE `file_info`
ADD COLUMN `relative_path` VARCHAR(1000) NULL COMMENT '相对路径（相对于任务源路径）'
AFTER `file_name`;
```

**字段说明：**
- `relative_path`: 文件相对于任务源路径的相对路径
- 例如：源路径 `/backdata/done`，文件 `/backdata/done/folder1/movie.mp4`
- 则 `relative_path` = `folder1`

### 2. FileInfo 实体修改

**文件：** `backend/src/main/java/com/gdupload/entity/FileInfo.java`

添加字段：
```java
/**
 * 相对路径（相对于任务源路径）
 */
private String relativePath;
```

### 3. 扫描逻辑修改

**文件：** `backend/src/main/java/com/gdupload/service/impl/FileInfoServiceImpl.java`

修改 `scanDirectory()` 和 `scanDirectoryRecursive()` 方法：

**修改前：**
```java
public List<FileInfo> scanDirectory(String directoryPath, boolean recursive) {
    List<FileInfo> fileList = new ArrayList<>();
    File directory = new File(directoryPath);

    if (!directory.exists() || !directory.isDirectory()) {
        log.error("目录不存在或不是目录: {}", directoryPath);
        return fileList;
    }

    scanDirectoryRecursive(directory, directoryPath, fileList, recursive);

    return fileList;
}

private void scanDirectoryRecursive(File directory, String basePath, List<FileInfo> fileList, boolean recursive) {
    File[] files = directory.listFiles();

    if (files == null) {
        return;
    }

    for (File file : files) {
        if (file.isFile()) {
            // 只允许mp4和mkv文件
            String fileName = fixEncoding(file.getName());
            String filePath = fixEncoding(file.getAbsolutePath());

            String fileNameLower = fileName.toLowerCase();
            if (!fileNameLower.endsWith(".mp4") && !fileNameLower.endsWith(".mkv")) {
                continue;
            }

            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileName(fileName);
            fileInfo.setFilePath(filePath);
            fileInfo.setFileSize(file.length());
            fileInfo.setStatus(0);
            fileInfo.setCreateTime(DateTimeUtil.now());

            fileList.add(fileInfo);
        } else if (file.isDirectory() && recursive) {
            scanDirectoryRecursive(file, basePath, fileList, recursive);
        }
    }
}
```

**修改后：**
```java
public List<FileInfo> scanDirectory(String directoryPath, boolean recursive) {
    List<FileInfo> fileList = new ArrayList<>();
    File directory = new File(directoryPath);

    if (!directory.exists() || !directory.isDirectory()) {
        log.error("目录不存在或不是目录: {}", directoryPath);
        return fileList;
    }

    // 规范化基础路径（移除末尾的斜杠）
    String normalizedBasePath = directoryPath.replaceAll("[/\\\\]+$", "");

    scanDirectoryRecursive(directory, normalizedBasePath, fileList, recursive);

    log.info("扫描目录完成: path={}, fileCount={}", directoryPath, fileList.size());

    return fileList;
}

private void scanDirectoryRecursive(File directory, String basePath, List<FileInfo> fileList, boolean recursive) {
    File[] files = directory.listFiles();

    if (files == null) {
        return;
    }

    for (File file : files) {
        if (file.isFile()) {
            // 获取文件名并确保使用UTF-8编码
            String fileName = fixEncoding(file.getName());
            String filePath = fixEncoding(file.getAbsolutePath());

            // 只允许mp4和mkv文件
            String fileNameLower = fileName.toLowerCase();
            if (!fileNameLower.endsWith(".mp4") && !fileNameLower.endsWith(".mkv")) {
                log.debug("跳过非视频文件: {}", fileName);
                continue;
            }

            // 计算相对路径
            String relativePath = calculateRelativePath(basePath, file.getParentFile().getAbsolutePath());

            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileName(fileName);
            fileInfo.setFilePath(filePath);
            fileInfo.setRelativePath(relativePath);
            fileInfo.setFileSize(file.length());
            fileInfo.setStatus(0); // 待上传
            fileInfo.setCreateTime(DateTimeUtil.now());

            fileList.add(fileInfo);

            log.debug("扫描到文件: fileName={}, relativePath={}, size={}",
                fileName, relativePath, file.length());
        } else if (file.isDirectory() && recursive) {
            scanDirectoryRecursive(file, basePath, fileList, recursive);
        }
    }
}

/**
 * 计算相对路径
 *
 * @param basePath 基础路径（任务源路径）
 * @param filePath 文件所在目录的绝对路径
 * @return 相对路径，如果文件在根目录则返回空字符串
 */
private String calculateRelativePath(String basePath, String filePath) {
    try {
        // 规范化路径（统一使用正斜杠）
        String normalizedBasePath = basePath.replace("\\", "/");
        String normalizedFilePath = filePath.replace("\\", "/");

        // 移除末尾的斜杠
        normalizedBasePath = normalizedBasePath.replaceAll("/+$", "");
        normalizedFilePath = normalizedFilePath.replaceAll("/+$", "");

        // 如果文件路径等于基础路径，说明文件在根目录
        if (normalizedFilePath.equals(normalizedBasePath)) {
            return "";
        }

        // 如果文件路径以基础路径开头，计算相对路径
        if (normalizedFilePath.startsWith(normalizedBasePath + "/")) {
            String relativePath = normalizedFilePath.substring(normalizedBasePath.length() + 1);
            log.debug("计算相对路径: basePath={}, filePath={}, relativePath={}",
                normalizedBasePath, normalizedFilePath, relativePath);
            return relativePath;
        }

        // 否则返回空字符串
        log.warn("文件路径不在基础路径下: basePath={}, filePath={}", normalizedBasePath, normalizedFilePath);
        return "";
    } catch (Exception e) {
        log.error("计算相对路径失败: basePath={}, filePath={}", basePath, filePath, e);
        return "";
    }
}
```

### 4. 上传逻辑修改

**文件：** `backend/src/main/java/com/gdupload/service/impl/UploadServiceImpl.java`

修改 `uploadFileInternal()` 方法中构建目标路径的逻辑：

**修改前（约492行）：**
```java
// 构建目标路径（rclone copy 的目标应该是目录，不包含文件名）
String remotePath = task.getTargetPath();
if (!remotePath.endsWith("/")) {
    remotePath += "/";
}
```

**修改后：**
```java
// 构建目标路径（包含相对路径以保留目录结构）
String remotePath = task.getTargetPath();
if (!remotePath.endsWith("/")) {
    remotePath += "/";
}

// 如果文件有相对路径，添加到目标路径中
if (fileInfo.getRelativePath() != null && !fileInfo.getRelativePath().isEmpty()) {
    remotePath += fileInfo.getRelativePath() + "/";
    log.info("文件包含相对路径，目标路径: {}", remotePath);
}
```

## 部署步骤

### 1. 执行数据库迁移

```bash
# 连接数据库
mysql -u root -p

# 选择数据库
USE gd_upload_manager;

# 添加 relative_path 字段
ALTER TABLE `file_info`
ADD COLUMN `relative_path` VARCHAR(1000) NULL COMMENT '相对路径（相对于任务源路径）'
AFTER `file_name`;

# 验证字段添加成功
DESC file_info;
```

### 2. 修改后端代码

按照上述修改内容修改以下文件：
1. `FileInfo.java` - 添加 `relativePath` 字段
2. `FileInfoServiceImpl.java` - 修改扫描逻辑，计算相对路径
3. `UploadServiceImpl.java` - 修改上传逻辑，使用相对路径

### 3. 重新编译后端

```bash
cd backend
mvn clean package -DskipTests
```

### 4. 上传到服务器

```bash
scp target/gdupload-0.0.1-SNAPSHOT.jar user@server:/work/
```

### 5. 重启服务

```bash
ssh user@server
cd /work
./stop.sh
./start.sh
```

### 6. 验证日志

```bash
tail -f /work/nohup.out
```

## 测试步骤

### 1. 准备测试目录结构

```bash
# SSH 登录服务器
ssh user@server

# 创建测试目录结构
mkdir -p /backdata/done/folder1/subfolder
mkdir -p /backdata/done/folder2

# 创建测试文件（或复制真实文件）
touch /backdata/done/movie1.mp4
touch /backdata/done/folder1/movie2.mkv
touch /backdata/done/folder1/subfolder/movie3.mp4
touch /backdata/done/folder2/movie4.mkv
```

### 2. 创建上传任务

```
1. 打开前端页面
2. 进入"创建上传任务"页面
3. 输入信息：
   - 任务名称：测试目录结构保留
   - 源路径：/backdata/done
   - 目标路径：/test_upload
   - 递归扫描：开启
4. 点击"扫描目录"
5. 查看扫描结果，应该看到所有 .mp4 和 .mkv 文件
```

### 3. 查看数据库

```sql
# 连接数据库
mysql -u root -p
USE gd_upload_manager;

# 查看扫描到的文件信息
SELECT
    id,
    file_name,
    relative_path,
    file_path
FROM file_info
WHERE task_id = (SELECT MAX(id) FROM upload_task)
ORDER BY relative_path, file_name;

# 应该看到类似的结果：
# | id | file_name  | relative_path      | file_path                                    |
# |----|------------|--------------------|----------------------------------------------|
# | 1  | movie1.mp4 |                    | /backdata/done/movie1.mp4                    |
# | 2  | movie2.mkv | folder1            | /backdata/done/folder1/movie2.mkv            |
# | 3  | movie3.mp4 | folder1/subfolder  | /backdata/done/folder1/subfolder/movie3.mp4  |
# | 4  | movie4.mkv | folder2            | /backdata/done/folder2/movie4.mkv            |
```

### 4. 启动上传任务

```
1. 选择刚创建的任务
2. 点击"启动"按钮
3. 观察上传进度
```

### 5. 查看后端日志

```bash
# 查看上传日志
tail -f /work/nohup.out | grep "准备上传文件"

# 应该看到类似的日志：
# 准备上传文件: /backdata/done/movie1.mp4 -> gdrive:/test_upload/
# 准备上传文件: /backdata/done/folder1/movie2.mkv -> gdrive:/test_upload/folder1/
# 准备上传文件: /backdata/done/folder1/subfolder/movie3.mp4 -> gdrive:/test_upload/folder1/subfolder/
# 准备上传文件: /backdata/done/folder2/movie4.mkv -> gdrive:/test_upload/folder2/
```

### 6. 验证 Google Drive 目录结构

```
登录 Google Drive，查看 /test_upload 目录：

/test_upload/
├── movie1.mp4
├── folder1/
│   ├── movie2.mkv
│   └── subfolder/
│       └── movie3.mp4
└── folder2/
    └── movie4.mkv
```

## 预期结果

### 成功标志

1. **数据库字段添加成功**
   ```sql
   DESC file_info;
   # 应该看到 relative_path 字段
   ```

2. **扫描时正确计算相对路径**
   - 根目录文件：`relative_path` 为空字符串或 NULL
   - 子目录文件：`relative_path` 为相对路径（如 `folder1` 或 `folder1/subfolder`）

3. **上传时保留目录结构**
   - 后端日志显示正确的目标路径
   - Google Drive 中文件按目录结构存放

4. **前端显示正常**
   - 文件列表正常显示
   - 上传进度正常更新

### 失败标志

如果出现以下情况，说明修改失败：

1. **数据库字段添加失败**
   ```sql
   DESC file_info;
   # 没有看到 relative_path 字段
   ```
   - 解决方法：手动执行 ALTER TABLE 语句

2. **扫描时 relative_path 为 NULL**
   ```sql
   SELECT relative_path FROM file_info WHERE task_id = xxx;
   # 所有记录的 relative_path 都是 NULL
   ```
   - 解决方法：检查 `calculateRelativePath()` 方法是否正确实现

3. **上传时目录结构丢失**
   - Google Drive 中所有文件都在根目录
   - 解决方法：检查 `uploadFileInternal()` 方法中的目标路径构建逻辑

4. **编译错误**
   ```
   [ERROR] FileInfo.java: cannot find symbol relativePath
   ```
   - 解决方法：确保 `FileInfo.java` 中添加了 `relativePath` 字段

## 注意事项

### 1. 已有任务的处理

修改后，已经创建的任务中的文件没有 `relative_path` 字段，会按照旧逻辑上传（所有文件在根目录）。

如果需要重新扫描：
```sql
-- 删除旧的文件信息
DELETE FROM file_info WHERE task_id = xxx;

-- 在前端重新扫描目录并保存
```

### 2. 路径分隔符

代码中统一使用正斜杠 `/` 作为路径分隔符，兼容 Windows 和 Linux。

### 3. 特殊字符处理

文件名中的特殊字符仍然会被处理（创建临时链接），不影响目录结构保留。

### 4. rclone 命令

rclone 的 `move` 命令会自动创建目标目录，不需要手动创建。

## 总结

这次修改的核心：

1. **添加 relative_path 字段**：存储文件相对于任务源路径的相对路径
2. **扫描时计算相对路径**：使用 `calculateRelativePath()` 方法
3. **上传时使用相对路径**：构建包含相对路径的目标路径

现在上传文件时会保留完整的目录结构，不再是所有文件都上传到根目录！
