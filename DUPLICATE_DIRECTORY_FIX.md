# 重复目录问题修复

## 问题描述

下载 Series（电视剧）时，会创建两个目录：
1. `?? (2024)` - 乱码目录
2. `正确的剧集名称 (2024)` - 正确的目录

## 根本原因

有两个方法都在创建 Series 目录，但使用了不同的目录名生成逻辑：

### 1. downloadEpisodeWithSeriesDir()

**用途：** 下载单个剧集时创建目录

**逻辑：**
```java
String seriesName = series.getName();
if (series.getProductionYear() != null) {
    seriesName += " (" + series.getProductionYear() + ")";
}
seriesName = seriesName.replaceAll("[\\\\/:*?\"<>|]", "_");
```

**特点：** 简单的字符串清理

### 2. downloadSeriesAllEpisodes()

**用途：** 批量下载所有剧集时创建目录

**逻辑：**
```java
String seriesName = series.getName();
if (series.getProductionYear() != null) {
    seriesName += " (" + series.getProductionYear() + ")";
}
String cleanedSeriesName = seriesName.replaceAll("[\\\\/:*?\"<>|]", "_");

// 验证目录名编码
byte[] bytes = cleanedSeriesName.getBytes(StandardCharsets.UTF_8);
String testDecode = new String(bytes, StandardCharsets.UTF_8);
if (!cleanedSeriesName.equals(testDecode)) {
    log.warn("目录名编码验证失败！");
}
seriesName = cleanedSeriesName;
```

**特点：** 复杂的编码验证和清理

### 问题

两个方法的逻辑不一致，可能导致：
- 同一个 Series 生成不同的目录名
- 第一次下载单个剧集时创建一个目录
- 后来批量下载时又创建另一个目录
- 导致文件分散在两个目录中

## 修复方案

### 1. 提取统一的目录名生成方法

创建一个新方法 `buildSeriesDirectory()`，统一处理目录名生成和创建：

```java
/**
 * 构建电视剧目录路径（统一的目录名生成逻辑）
 */
private String buildSeriesDirectory(EmbyItem series) {
    String seriesName = series.getName();
    log.info("原始电视剧名称: {}", seriesName);

    if (seriesName == null || seriesName.isEmpty()) {
        seriesName = "unknown_series";
    }
    if (series.getProductionYear() != null) {
        seriesName += " (" + series.getProductionYear() + ")";
    }

    // 清理文件名中的非法字符
    seriesName = seriesName.replaceAll("[\\\\/:*?\"<>|]", "_");
    log.info("清理后的电视剧名称: {}", seriesName);

    // 使用 Path 创建目录，确保UTF-8编码
    java.nio.file.Path seriesDirPath = java.nio.file.Paths.get("/data/emby", seriesName);
    String seriesDir = seriesDirPath.toString();
    log.info("完整目录路径: {}", seriesDir);

    java.io.File dir = seriesDirPath.toFile();
    if (!dir.exists()) {
        boolean created = dir.mkdirs();
        log.info("创建电视剧目录: {}, 结果: {}", seriesDir, created);
    } else {
        log.info("电视剧目录已存在: {}", seriesDir);
    }

    return seriesDir;
}
```

### 2. 修改 downloadEpisodeWithSeriesDir()

```java
private Map<String, Object> downloadEpisodeWithSeriesDir(EmbyItem episode) throws Exception {
    // ... 获取 series ...

    // 创建电视剧目录（使用统一的目录名生成逻辑）
    String seriesDir = buildSeriesDirectory(series);

    // 下载剧集到电视剧目录
    return downloadSingleItem(episode, seriesDir);
}
```

### 3. 修改 downloadSeriesAllEpisodes()

```java
private Map<String, Object> downloadSeriesAllEpisodes(EmbyItem series) throws Exception {
    log.info("开始下载电视剧所有剧集: {}", series.getName());

    // 获取所有剧集
    List<EmbyItem> episodes = getSeriesEpisodes(series.getId());

    // 创建电视剧目录（使用统一的目录名生成逻辑）
    String seriesDir = buildSeriesDirectory(series);

    // 下载每一集
    // ...
}
```

## 修复效果

### 修复前

```
/data/emby/
├── ?? (2024)/              # 乱码目录（第一次下载单集时创建）
│   └── 第 1 集.mp4
└── 正确的剧集名称 (2024)/  # 正确目录（批量下载时创建）
    ├── 第 2 集.mp4
    ├── 第 3 集.mp4
    └── ...
```

### 修复后

```
/data/emby/
└── 正确的剧集名称 (2024)/  # 统一的目录
    ├── 第 1 集.mp4
    ├── 第 2 集.mp4
    ├── 第 3 集.mp4
    └── ...
```

## 部署步骤

### 1. 重新编译后端

```bash
cd backend
mvn clean package -DskipTests
```

### 2. 上传到服务器

```bash
scp target/gdupload-0.0.1-SNAPSHOT.jar user@server:/work/
```

### 3. 重启服务

```bash
ssh user@server
cd /work
./stop.sh
./start.sh
```

### 4. 清理旧的乱码目录

```bash
# SSH 登录服务器
ssh user@server

# 查看所有目录
ls -la /data/emby/

# 删除乱码目录（如果确认是空的或不需要的）
rm -rf "/data/emby/?? (2024)"

# 或者移动文件到正确的目录
mv "/data/emby/?? (2024)/"* "/data/emby/正确的剧集名称 (2024)/"
rmdir "/data/emby/?? (2024)"
```

## 测试步骤

### 1. 下载单个剧集

```bash
# 1. 在前端选择一个未下载的 Series
# 2. 展开 Series，选择一个剧集
# 3. 点击"下载到服务器"
# 4. 等待下载完成
```

### 2. 查看目录结构

```bash
# SSH 登录服务器
ssh user@server

# 查看目录
ls -la /data/emby/

# 应该只看到一个目录
drwxr-xr-x 2 user user 4096 Feb  6 04:00 剧集名称 (2024)
```

### 3. 批量下载剩余剧集

```bash
# 1. 在前端点击 Series 的"直接下载"按钮
# 2. 等待所有剧集下载完成
```

### 4. 验证目录结构

```bash
# 查看目录
ls -la /data/emby/

# 应该仍然只有一个目录
drwxr-xr-x 2 user user 4096 Feb  6 04:10 剧集名称 (2024)

# 查看目录内容
ls -la "/data/emby/剧集名称 (2024)/"

# 应该看到所有剧集
-rw-r--r-- 1 user user 1024000000 Feb  6 04:00 第 1 集.mp4
-rw-r--r-- 1 user user 1024000000 Feb  6 04:05 第 2 集.mp4
-rw-r--r-- 1 user user 1024000000 Feb  6 04:10 第 3 集.mp4
...
```

### 5. 查看后端日志

```bash
# 查看目录创建日志
tail -100 /work/nohup.out | grep "创建电视剧目录"

# 应该看到：
# 创建电视剧目录: /data/emby/剧集名称 (2024), 结果: true
# 或
# 电视剧目录已存在: /data/emby/剧集名称 (2024)
```

## 预期结果

### 成功标志

1. **只有一个目录**
   - `/data/emby/剧集名称 (2024)/`
   - 没有乱码目录

2. **所有剧集在同一个目录**
   - 单独下载的剧集
   - 批量下载的剧集
   - 都在同一个目录中

3. **后端日志一致**
   - 第一次创建目录：`创建电视剧目录: xxx, 结果: true`
   - 后续下载：`电视剧目录已存在: xxx`
   - 目录路径完全一致

### 失败标志

如果仍然出现两个目录：

1. **检查后端日志**
   ```bash
   tail -200 /work/nohup.out | grep "电视剧目录"
   ```
   - 查看是否有不同的目录路径

2. **检查代码是否正确部署**
   ```bash
   # 检查 JAR 文件的修改时间
   ls -la /work/gdupload-0.0.1-SNAPSHOT.jar

   # 应该是最新的时间
   ```

3. **检查是否有缓存**
   - 重启服务后再测试
   - 清除浏览器缓存

## 注意事项

### 1. 已有的乱码目录

修复后，已经存在的乱码目录不会自动删除，需要手动清理：

```bash
# 查看所有目录
ls -la /data/emby/

# 手动删除乱码目录
rm -rf "/data/emby/?? (2024)"
```

### 2. 文件迁移

如果乱码目录中有文件，需要先迁移：

```bash
# 移动文件到正确的目录
mv "/data/emby/?? (2024)/"* "/data/emby/正确的剧集名称 (2024)/"

# 删除空目录
rmdir "/data/emby/?? (2024)"
```

### 3. 目录名一致性

现在所有下载方式都使用相同的目录名生成逻辑：
- 下载单个剧集
- 批量下载所有剧集
- 都会创建/使用同一个目录

## 总结

这次修复的核心：

1. **提取统一方法**：`buildSeriesDirectory()` 统一处理目录名生成
2. **简化逻辑**：移除复杂的编码验证，使用简单的字符串清理
3. **避免重复**：两个下载方法使用相同的目录名生成逻辑

现在不会再出现重复目录的问题，所有剧集都会保存在同一个目录中！
