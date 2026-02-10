# 保留目录结构上传 - 逻辑验证

## 功能需求

1. **只上传 mp4 和 mkv 文件** ✅
2. **保留完整的目录结构** ✅
3. **支持递归扫描子目录** ✅

## 逻辑验证

### 场景 1：根目录文件

**输入：**
```
源路径: /backdata/done
文件: /backdata/done/movie.mp4
```

**处理流程：**
1. 扫描时：
   - `basePath` = `/backdata/done`
   - `file.getParentFile().getAbsolutePath()` = `/backdata/done`
   - `calculateRelativePath("/backdata/done", "/backdata/done")` = `""`（空字符串）
   - `fileInfo.relativePath` = `""`

2. 上传时：
   - `task.getTargetPath()` = `/upload`
   - `remotePath` = `/upload/`
   - `fileInfo.getRelativePath()` = `""`（空字符串）
   - 条件 `!= null && !isEmpty()` = false，不添加相对路径
   - 最终 `remotePath` = `/upload/`
   - rclone 命令：`rclone move /backdata/done/movie.mp4 gdrive:/upload/`

**结果：**
- Google Drive: `/upload/movie.mp4` ✅

---

### 场景 2：一级子目录文件

**输入：**
```
源路径: /backdata/done
文件: /backdata/done/folder1/movie.mkv
```

**处理流程：**
1. 扫描时：
   - `basePath` = `/backdata/done`
   - `file.getParentFile().getAbsolutePath()` = `/backdata/done/folder1`
   - `calculateRelativePath("/backdata/done", "/backdata/done/folder1")`:
     - `normalizedBasePath` = `/backdata/done`
     - `normalizedFilePath` = `/backdata/done/folder1`
     - 检查：`/backdata/done/folder1`.startsWith(`/backdata/done/`) = true
     - `relativePath` = `/backdata/done/folder1`.substring(15) = `folder1`
   - `fileInfo.relativePath` = `folder1`

2. 上传时：
   - `task.getTargetPath()` = `/upload`
   - `remotePath` = `/upload/`
   - `fileInfo.getRelativePath()` = `folder1`
   - 条件 `!= null && !isEmpty()` = true，添加相对路径
   - `remotePath` = `/upload/folder1/`
   - rclone 命令：`rclone move /backdata/done/folder1/movie.mkv gdrive:/upload/folder1/`

**结果：**
- Google Drive: `/upload/folder1/movie.mkv` ✅

---

### 场景 3：多级子目录文件

**输入：**
```
源路径: /backdata/done
文件: /backdata/done/folder1/subfolder/movie.mp4
```

**处理流程：**
1. 扫描时：
   - `basePath` = `/backdata/done`
   - `file.getParentFile().getAbsolutePath()` = `/backdata/done/folder1/subfolder`
   - `calculateRelativePath("/backdata/done", "/backdata/done/folder1/subfolder")`:
     - `normalizedBasePath` = `/backdata/done`
     - `normalizedFilePath` = `/backdata/done/folder1/subfolder`
     - 检查：`/backdata/done/folder1/subfolder`.startsWith(`/backdata/done/`) = true
     - `relativePath` = `/backdata/done/folder1/subfolder`.substring(15) = `folder1/subfolder`
   - `fileInfo.relativePath` = `folder1/subfolder`

2. 上传时：
   - `task.getTargetPath()` = `/upload`
   - `remotePath` = `/upload/`
   - `fileInfo.getRelativePath()` = `folder1/subfolder`
   - 条件 `!= null && !isEmpty()` = true，添加相对路径
   - `remotePath` = `/upload/folder1/subfolder/`
   - rclone 命令：`rclone move /backdata/done/folder1/subfolder/movie.mp4 gdrive:/upload/folder1/subfolder/`

**结果：**
- Google Drive: `/upload/folder1/subfolder/movie.mp4` ✅

---

### 场景 4：跳过非视频文件

**输入：**
```
源路径: /backdata/done
文件:
  - /backdata/done/movie.mp4 (保留)
  - /backdata/done/readme.txt (跳过)
  - /backdata/done/folder1/movie.mkv (保留)
  - /backdata/done/folder1/image.jpg (跳过)
```

**处理流程：**
1. 扫描时：
   - 遍历所有文件
   - 对每个文件检查：
     ```java
     String fileNameLower = fileName.toLowerCase();
     if (!fileNameLower.endsWith(".mp4") && !fileNameLower.endsWith(".mkv")) {
         log.debug("跳过非视频文件: {}", fileName);
         continue;
     }
     ```
   - `readme.txt` → 跳过
   - `image.jpg` → 跳过
   - 只有 `.mp4` 和 `.mkv` 文件被添加到 `fileList`

**结果：**
- 只扫描到 2 个文件：`movie.mp4` 和 `folder1/movie.mkv` ✅

---

### 场景 5：Windows 路径

**输入：**
```
源路径: D:\backdata\done
文件: D:\backdata\done\folder1\movie.mp4
```

**处理流程：**
1. 扫描时：
   - `basePath` = `D:\backdata\done`
   - `file.getParentFile().getAbsolutePath()` = `D:\backdata\done\folder1`
   - `calculateRelativePath("D:\backdata\done", "D:\backdata\done\folder1")`:
     - `normalizedBasePath` = `D:\backdata\done`.replace(`\`, `/`) = `D:/backdata/done`
     - `normalizedFilePath` = `D:\backdata\done\folder1`.replace(`\`, `/`) = `D:/backdata/done/folder1`
     - 检查：`D:/backdata/done/folder1`.startsWith(`D:/backdata/done/`) = true
     - `relativePath` = `D:/backdata/done/folder1`.substring(17) = `folder1`
   - `fileInfo.relativePath` = `folder1`

2. 上传时：
   - 同场景 2

**结果：**
- Google Drive: `/upload/folder1/movie.mp4` ✅
- Windows 路径正确处理 ✅

---

### 场景 6：路径末尾有斜杠

**输入：**
```
源路径: /backdata/done/
文件: /backdata/done/folder1/movie.mp4
```

**处理流程：**
1. 扫描时：
   - `directoryPath` = `/backdata/done/`
   - `normalizedBasePath` = `/backdata/done/`.replaceAll(`[/\\]+$`, ``) = `/backdata/done`
   - 后续处理同场景 2

**结果：**
- 正确处理末尾斜杠 ✅

---

### 场景 7：目标路径末尾无斜杠

**输入：**
```
目标路径: /upload
相对路径: folder1
```

**处理流程：**
1. 上传时：
   - `task.getTargetPath()` = `/upload`
   - 检查：`!remotePath.endsWith("/")` = true
   - `remotePath` = `/upload/`
   - 添加相对路径：`remotePath` = `/upload/folder1/`

**结果：**
- 正确添加斜杠 ✅

---

## 完整测试用例

### 测试目录结构

```
/backdata/done/
├── movie1.mp4                    → /upload/movie1.mp4
├── readme.txt                    → 跳过
├── folder1/
│   ├── movie2.mkv                → /upload/folder1/movie2.mkv
│   ├── image.jpg                 → 跳过
│   └── subfolder/
│       ├── movie3.mp4            → /upload/folder1/subfolder/movie3.mp4
│       └── doc.pdf               → 跳过
├── folder2/
│   ├── movie4.mkv                → /upload/folder2/movie4.mkv
│   └── video.avi                 → 跳过
└── empty_folder/                 → 跳过（无文件）
```

### 预期扫描结果

```sql
SELECT file_name, relative_path, file_path
FROM file_info
WHERE task_id = xxx
ORDER BY relative_path, file_name;

-- 结果：
| file_name  | relative_path      | file_path                                    |
|------------|--------------------|----------------------------------------------|
| movie1.mp4 |                    | /backdata/done/movie1.mp4                    |
| movie2.mkv | folder1            | /backdata/done/folder1/movie2.mkv            |
| movie3.mp4 | folder1/subfolder  | /backdata/done/folder1/subfolder/movie3.mp4  |
| movie4.mkv | folder2            | /backdata/done/folder2/movie4.mkv            |
```

### 预期上传日志

```
准备上传文件: /backdata/done/movie1.mp4 -> gdrive:/upload/, 文件大小: 1.2 GB
准备上传文件: /backdata/done/folder1/movie2.mkv -> gdrive:/upload/folder1/, 文件大小: 800 MB
文件包含相对路径，目标路径: /upload/folder1/
准备上传文件: /backdata/done/folder1/subfolder/movie3.mp4 -> gdrive:/upload/folder1/subfolder/, 文件大小: 1.5 GB
文件包含相对路径，目标路径: /upload/folder1/subfolder/
准备上传文件: /backdata/done/folder2/movie4.mkv -> gdrive:/upload/folder2/, 文件大小: 900 MB
文件包含相对路径，目标路径: /upload/folder2/
```

### 预期 Google Drive 结构

```
/upload/
├── movie1.mp4
├── folder1/
│   ├── movie2.mkv
│   └── subfolder/
│       └── movie3.mp4
└── folder2/
    └── movie4.mkv
```

---

## 边界情况处理

### 1. 空字符串相对路径

**场景：** 文件在根目录，`relativePath` = `""`

**代码：**
```java
if (fileInfo.getRelativePath() != null && !fileInfo.getRelativePath().isEmpty()) {
    remotePath += fileInfo.getRelativePath() + "/";
}
```

**结果：** 条件为 false，不添加相对路径 ✅

### 2. NULL 相对路径

**场景：** 旧数据或异常情况，`relativePath` = `null`

**代码：**
```java
if (fileInfo.getRelativePath() != null && !fileInfo.getRelativePath().isEmpty()) {
    remotePath += fileInfo.getRelativePath() + "/";
}
```

**结果：** 条件为 false，不添加相对路径 ✅

### 3. 多余的斜杠

**场景：** 路径中有多余的斜杠

**代码：**
```java
normalizedBasePath = normalizedBasePath.replaceAll("/+$", "");
normalizedFilePath = normalizedFilePath.replaceAll("/+$", "");
```

**结果：** 移除末尾多余斜杠 ✅

### 4. 文件名包含特殊字符

**场景：** 文件名包含特殊字符（如 `[`, `]`, `(`, `)` 等）

**代码：**
```java
// 检查文件名是否包含特殊字符（只检查文件名，不检查目录）
String fileName = originalPath.getFileName().toString();
String sanitizedFileName = sanitizeFileName(fileName);

// 如果文件名需要清理，创建临时符号链接
if (!fileName.equals(sanitizedFileName)) {
    // 创建临时链接
    tempLinkPath = parent.resolve("temp_upload_" + System.currentTimeMillis() + "_" + sanitizedFileName);
    Files.createLink(tempLinkPath, originalPath);
    actualUploadPath = tempLinkPath.toString();
}
```

**结果：**
- 特殊字符处理不影响目录结构
- 只处理文件名，不处理目录名
- 相对路径保持不变 ✅

---

## 代码审查

### FileInfoServiceImpl.java

#### scanDirectory() ✅
```java
// 规范化基础路径（移除末尾的斜杠）
String normalizedBasePath = directoryPath.replaceAll("[/\\\\]+$", "");
scanDirectoryRecursive(directory, normalizedBasePath, fileList, recursive);
```
- 正确移除末尾斜杠
- 传递规范化的基础路径

#### scanDirectoryRecursive() ✅
```java
// 只允许mp4和mkv文件
String fileNameLower = fileName.toLowerCase();
if (!fileNameLower.endsWith(".mp4") && !fileNameLower.endsWith(".mkv")) {
    log.debug("跳过非视频文件: {}", fileName);
    continue;
}

// 计算相对路径
String relativePath = calculateRelativePath(basePath, file.getParentFile().getAbsolutePath());

fileInfo.setRelativePath(relativePath);
```
- 正确过滤非视频文件
- 正确计算并设置相对路径

#### calculateRelativePath() ✅
```java
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
    return relativePath;
}

return "";
```
- 正确处理 Windows 和 Linux 路径
- 正确处理根目录文件（返回空字符串）
- 正确计算相对路径

### UploadServiceImpl.java

#### uploadFileInternal() ✅
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
- 正确处理目标路径末尾斜杠
- 正确处理 NULL 和空字符串
- 正确拼接相对路径

---

## 潜在问题检查

### ❌ 问题 1：相对路径编码

**问题：** 相对路径中的中文目录名可能有编码问题

**检查：**
```java
String relativePath = calculateRelativePath(basePath, file.getParentFile().getAbsolutePath());
```

**分析：**
- `file.getParentFile().getAbsolutePath()` 返回的是系统路径
- 没有经过 `fixEncoding()` 处理
- 可能导致相对路径中的中文目录名乱码

**修复：** 需要对相对路径也进行编码修复

### ✅ 问题 2：rclone 自动创建目录

**问题：** rclone 是否会自动创建目标目录？

**检查：**
```bash
rclone move /source/file.mp4 gdrive:/upload/folder1/subfolder/
```

**分析：**
- rclone 的 `move` 命令会自动创建目标目录
- 不需要手动创建

**结论：** 无问题 ✅

### ✅ 问题 3：目标路径末尾斜杠

**问题：** 目标路径末尾必须有斜杠吗？

**检查：**
```java
if (!remotePath.endsWith("/")) {
    remotePath += "/";
}
```

**分析：**
- rclone 的 `move` 命令：
  - `rclone move file.mp4 gdrive:/upload/` → 上传到 `/upload/file.mp4`
  - `rclone move file.mp4 gdrive:/upload` → 上传到 `/upload`（文件名变成 upload）
- 必须有末尾斜杠

**结论：** 代码正确 ✅

---

## 需要修复的问题

### 问题：相对路径编码

**位置：** `FileInfoServiceImpl.java` 第 76 行

**当前代码：**
```java
String relativePath = calculateRelativePath(basePath, file.getParentFile().getAbsolutePath());
```

**问题：**
- `file.getParentFile().getAbsolutePath()` 可能返回乱码路径
- 导致相对路径中的中文目录名乱码

**修复方案：**
```java
String parentPath = fixEncoding(file.getParentFile().getAbsolutePath());
String relativePath = calculateRelativePath(basePath, parentPath);
```

---

## 总结

### ✅ 正确的逻辑

1. **只上传 mp4 和 mkv 文件** - 扫描时正确过滤
2. **保留目录结构** - 计算相对路径并在上传时使用
3. **处理路径分隔符** - 统一使用正斜杠
4. **处理末尾斜杠** - 正确移除和添加
5. **处理根目录文件** - 相对路径为空字符串
6. **处理 NULL 值** - 正确检查 NULL 和空字符串

### ⚠️ 需要修复

1. **相对路径编码** - 需要对父目录路径进行编码修复

### 📝 建议

1. 添加更多日志，方便调试
2. 添加单元测试
3. 添加集成测试
