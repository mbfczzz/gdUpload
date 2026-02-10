# Emby 直接下载是否计入观影榜

## 问题

使用"直接下载"功能下载视频时，会被计入 Emby 的一天观影榜吗？

## 答案

**会的，很可能会被计入观影统计。**

## 原因分析

### 1. 使用的 API 端点

代码使用的是 Emby 的流媒体端点：

```java
String streamUrl = String.format(
    "%s/Videos/%s/stream?api_key=%s&Static=true",
    baseUrl, item.getId(), accessToken
);
```

**端点说明：**
- `/Videos/{id}/stream` - Emby 的视频流媒体端点
- 这是 Emby 客户端播放视频时使用的标准端点
- Emby 服务器会记录这个请求为"播放"行为

### 2. 伪装成播放器

代码还伪装成了 Emby 播放器：

```java
String embyAuth = String.format(
    "MediaBrowser Token=\"%s\", Emby UserId=\"%s\", Client=\"Forward\", Device=\"iPhone\", DeviceId=\"%s\", Version=\"%s\"",
    accessToken, userId, deviceId, forwardVersion
);

connection.setRequestProperty("X-Emby-Authorization", embyAuth);
connection.setRequestProperty("User-Agent", "Forward/1.3.14 (iPhone; iOS 17.0)");
```

**说明：**
- `Client="Forward"` - 伪装成 Forward 播放器
- `Device="iPhone"` - 伪装成 iPhone 设备
- `User-Agent` - 伪装成 iOS 客户端

### 3. Emby 的统计机制

Emby 服务器会记录以下信息：
- **播放开始**：当客户端请求 `/stream` 端点时
- **播放进度**：通过 `/PlayingItemStopped` 等端点上报
- **播放完成**：通过 `/PlayingItemStopped` 端点标记完成

**我们的下载行为：**
- ✅ 请求了 `/stream` 端点（会被记录为播放开始）
- ❌ 没有上报播放进度
- ❌ 没有上报播放完成

**结果：**
- Emby 会认为你"开始播放"了这个视频
- 但没有完成播放（因为没有上报完成）
- 可能会显示在"最近播放"或"正在播放"列表中
- **可能会计入观影榜**（取决于 Emby 的统计规则）

## 验证方法

### 1. 查看 Emby 仪表板

```
1. 登录 Emby 管理后台
2. 进入"仪表板" → "活动"
3. 查看"最近播放"列表
4. 下载一个视频后，看是否出现在列表中
```

### 2. 查看用户播放历史

```
1. 登录 Emby 管理后台
2. 进入"用户" → 选择你的用户
3. 查看"播放历史"
4. 看是否有下载的视频记录
```

### 3. 查看 Emby 日志

```bash
# SSH 登录 Emby 服务器
ssh user@emby-server

# 查看 Emby 日志
tail -f /var/lib/emby/logs/embyserver.txt

# 下载一个视频，观察日志
# 应该会看到类似的记录：
# [PlaybackManager] Playback start: User=xxx, Item=xxx, Client=Forward
```

## 如何避免计入观影榜

### 方案 1：使用下载端点（推荐）

Emby 提供了专门的下载端点，不会计入播放统计：

```java
String downloadUrl = String.format(
    "%s/Items/%s/Download?api_key=%s",
    baseUrl, item.getId(), accessToken
);
```

**优点：**
- 不会计入播放统计
- 不会出现在"最近播放"列表
- 专门用于下载，语义更清晰

**缺点：**
- 可能需要特定权限
- 某些 Emby 配置可能禁用下载功能

### 方案 2：使用匿名设备 ID

使用一个特殊的设备 ID，然后在 Emby 中排除这个设备的统计：

```java
String deviceId = "gdupload-download-bot"; // 特殊的设备 ID
String embyAuth = String.format(
    "MediaBrowser Token=\"%s\", Emby UserId=\"%s\", Client=\"GdUpload\", Device=\"Server\", DeviceId=\"%s\", Version=\"1.0\"",
    accessToken, userId, deviceId
);
```

然后在 Emby 管理后台：
```
1. 进入"设备"
2. 找到 "gdupload-download-bot"
3. 设置为"不计入统计"（如果 Emby 支持）
```

### 方案 3：使用系统账号

创建一个专门的系统账号用于下载，不计入个人观影统计：

```
1. 在 Emby 中创建一个新用户：download-bot
2. 给这个用户分配下载权限
3. 使用这个用户的 token 进行下载
4. 这样下载记录不会出现在你的个人账号中
```

## 修改代码使用下载端点

如果你想避免计入观影榜，可以修改代码使用下载端点：

### 修改位置

**文件：** `backend/src/main/java/com/gdupload/service/impl/EmbyServiceImpl.java`

**原代码：**
```java
String[] streamUrls = {
    String.format("%s/Videos/%s/stream?api_key=%s&Static=true",
        baseUrl, item.getId(), accessToken),
    // ...
};
```

**修改为：**
```java
String[] streamUrls = {
    // 方式1：使用下载端点（不计入播放统计）
    String.format("%s/Items/%s/Download?api_key=%s",
        baseUrl, item.getId(), accessToken),
    // 方式2：使用流媒体端点（备用）
    String.format("%s/Videos/%s/stream?api_key=%s&Static=true",
        baseUrl, item.getId(), accessToken),
};
```

**说明：**
- 优先尝试下载端点
- 如果下载端点失败（权限不足或被禁用），降级到流媒体端点

## 测试步骤

### 1. 修改代码使用下载端点

```bash
# 修改代码
# 重新编译
cd backend
mvn clean package -DskipTests

# 部署
scp target/gdupload-0.0.1-SNAPSHOT.jar user@server:/work/
ssh user@server
cd /work
./stop.sh
./start.sh
```

### 2. 下载一个视频

```bash
# 在前端下载一个视频
# 等待下载完成
```

### 3. 检查 Emby 统计

```bash
# 登录 Emby 管理后台
# 查看"最近播放"列表
# 看是否有刚才下载的视频
```

### 4. 查看后端日志

```bash
# 查看使用的 URL
tail -100 /work/nohup.out | grep "尝试方式"

# 应该看到：
# 尝试方式 1: http://emby-server:8096/Items/xxx/Download?api_key=xxx
# 或
# 尝试方式 1: http://emby-server:8096/Videos/xxx/stream?api_key=xxx
```

## 总结

### 当前行为

- ✅ 使用 `/Videos/{id}/stream` 端点
- ✅ 伪装成播放器
- ❌ **会被计入 Emby 播放统计**
- ❌ **可能会出现在观影榜**

### 推荐方案

1. **修改代码使用下载端点**（最简单）
   - 使用 `/Items/{id}/Download` 端点
   - 不会计入播放统计

2. **使用系统账号**（最彻底）
   - 创建专门的下载账号
   - 下载记录不会出现在个人账号中

3. **接受现状**（最省事）
   - 如果不在意观影统计
   - 保持现有代码不变

### 注意事项

- 下载端点可能需要特定权限
- 某些 Emby 配置可能禁用下载功能
- 如果下载端点不可用，可以降级到流媒体端点
- 建议先测试下载端点是否可用

如果你想避免计入观影榜，我可以帮你修改代码使用下载端点。
