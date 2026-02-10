# 剧集目录结构修复

## 问题描述

下载单个剧集时，文件直接保存到 `/data/emby/` 根目录，而不是保存到剧集目录中。

**期望行为：**
```
/data/emby/剧集名称 (年份)/第 1 集.mp4
```

**实际行为：**
```
/data/emby/第 1 集.mp4
```

## 根本原因

`parseEmbyItem()` 方法没有从 Emby API 响应中提取剧集相关字段：
- `SeriesId` - 剧集ID
- `SeriesName` - 剧集名称
- `ParentIndexNumber` - 季数
- `IndexNumber` - 集数

导致 `downloadEpisodeWithSeriesDir()` 方法无法获取剧集信息来创建目录。

## 修复内容

### 修改文件：`backend/src/main/java/com/gdupload/service/impl/EmbyServiceImpl.java`

在 `parseEmbyItem()` 方法中添加剧集字段解析（第 667-671 行）：

```java
// 解析剧集信息（仅Episode类型有效）
item.setSeriesId(json.getStr("SeriesId"));
item.setSeriesName(json.getStr("SeriesName"));
item.setParentIndexNumber(json.getInt("ParentIndexNumber"));
item.setIndexNumber(json.getInt("IndexNumber"));
```

## 工作原理

### 1. 解析剧集信息

当从 Emby API 获取 Episode 类型的媒体项时，现在会提取：
- `SeriesId`: 用于获取剧集详细信息
- `SeriesName`: 剧集名称（备用）
- `ParentIndexNumber`: 季数（如 S01）
- `IndexNumber`: 集数（如 E01）

### 2. 下载流程

```java
public Map<String, Object> downloadToServer(String itemId) {
    EmbyItem item = getItemDetail(itemId);

    if ("Episode".equals(item.getType())) {
        // 使用剧集目录下载
        return downloadEpisodeWithSeriesDir(item);
    } else {
        // 直接下载到根目录
        return downloadSingleItem(item);
    }
}
```

### 3. 创建剧集目录

```java
private Map<String, Object> downloadEpisodeWithSeriesDir(EmbyItem episode) {
    String seriesId = episode.getSeriesId();

    if (seriesId != null && !seriesId.isEmpty()) {
        // 获取剧集信息
        EmbyItem series = getItemDetail(seriesId);

        // 构建剧集目录名：剧集名称 (年份)
        String seriesName = series.getName();
        if (series.getProductionYear() != null) {
            seriesName += " (" + series.getProductionYear() + ")";
        }

        // 清理非法字符
        seriesName = seriesName.replaceAll("[\\\\/:*?\"<>|]", "_");

        // 创建目录：/data/emby/剧集名称 (年份)/
        Path seriesDirPath = Paths.get("/data/emby", seriesName);
        Files.createDirectories(seriesDirPath);

        // 下载到剧集目录
        return downloadSingleItem(episode, seriesDirPath.toString());
    }

    // 如果没有 seriesId，降级到根目录下载
    return downloadSingleItem(episode);
}
```

## 部署步骤

### 1. 重新编译

```bash
cd backend
mvn clean package -DskipTests
```

### 2. 上传并部署

```bash
# 上传 JAR 文件到服务器
scp target/gdupload-0.0.1-SNAPSHOT.jar user@server:/work/

# SSH 登录服务器
ssh user@server

# 停止旧服务
cd /work
./stop.sh

# 启动新服务
./start.sh
```

### 3. 验证部署

```bash
# 查看日志，确认启动成功
tail -f /work/nohup.out

# 应该看到：
# JVM 默认编码: UTF-8
# 系统默认字符集: UTF-8
```

## 测试步骤

### 1. 下载单个剧集

1. 在前端界面，展开一个剧集
2. 点击某一集的"下载到服务器"按钮
3. 等待下载完成

### 2. 检查文件结构

```bash
# SSH 登录服务器
ssh user@server

# 查看 /data/emby 目录
ls -la /data/emby/

# 应该看到剧集目录
drwxr-xr-x 2 user user 4096 Feb  6 02:00 剧集名称 (2024)

# 查看剧集目录内容
ls -la "/data/emby/剧集名称 (2024)/"

# 应该看到剧集文件
-rw-r--r-- 1 user user 1024000000 Feb  6 02:00 第 1 集.mp4
```

### 3. 验证日志

```bash
# 查看下载日志
tail -100 /work/nohup.out | grep -A 10 "开始下载"

# 应该看到：
# 开始下载 Episode: 第 1 集
# 剧集ID: abc123
# 剧集名称: 剧集名称
# 创建剧集目录: /data/emby/剧集名称 (2024)
# 目标文件路径: /data/emby/剧集名称 (2024)/第 1 集.mp4
# 下载完成！
```

## 预期结果

### 成功标志

```bash
$ ls -la /data/emby/
drwxr-xr-x 2 user user 4096 Feb  6 02:00 剧集名称 (2024)

$ ls -la "/data/emby/剧集名称 (2024)/"
-rw-r--r-- 1 user user 1024000000 Feb  6 02:00 第 1 集.mp4
-rw-r--r-- 1 user user 1024000000 Feb  6 02:05 第 2 集.mp4
```

### 失败标志

如果仍然下载到根目录：
```bash
$ ls -la /data/emby/
-rw-r--r-- 1 user user 1024000000 Feb  6 02:00 第 1 集.mp4
```

**可能原因：**
1. Emby API 没有返回 `SeriesId` 字段
2. 代码没有正确编译部署
3. 缓存的旧数据没有 `SeriesId`

**解决方法：**
```bash
# 查看日志中的 seriesId
tail -100 /work/nohup.out | grep -i "seriesId"

# 如果 seriesId 为 null，检查 Emby API 响应
# 可能需要在前端强制刷新缓存
```

## 关键改进

### 1. UTF-8 编码支持

使用 Java NIO 确保中文文件名正确：
```java
Path seriesDirPath = Paths.get("/data/emby", seriesName);
Files.createDirectories(seriesDirPath);
Path targetPath = seriesDirPath.resolve(filename);
```

### 2. 保持原始文件名

不做任何转换，保持 Emby 返回的原始名称：
```java
String filename = item.getName();
filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
filename += ".mp4";
```

### 3. 降级处理

如果没有 `seriesId`，自动降级到根目录下载：
```java
if (seriesId == null || seriesId.isEmpty()) {
    return downloadSingleItem(episode);
}
```

## 总结

这次修复的核心：
1. **提取剧集字段** - 从 Emby API 响应中提取 SeriesId 等字段
2. **创建剧集目录** - 使用剧集名称和年份创建目录
3. **UTF-8 安全** - 使用 Java NIO 确保中文文件名正确
4. **降级处理** - 如果没有剧集信息，降级到根目录下载

现在下载单个剧集时，文件会自动保存到 `/data/emby/剧集名称 (年份)/` 目录中，文件名保持中文不乱码。
