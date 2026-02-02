# 🔍 Emby 媒体项详情 404 问题诊断

## 🐛 问题描述

点击媒体项的"详情"按钮时，Emby 服务器返回 404 错误：

```
Emby API请求失败: 404 - 找不到文件 "/Items/9045"
```

## 🔍 问题分析

### 错误来源

这个 404 错误**不是我们的应用路由问题**，而是 **Emby 服务器返回的错误**。

**请求流程**：
```
前端 → 我们的后端 → Emby 服务器
                      ↓
                    404 错误
```

### 可能的原因

#### 1. 媒体项类型不支持直接访问

某些类型的媒体项不能通过 `/Items/{id}` 直接访问，需要通过 `/Users/{userId}/Items/{id}` 访问。

**不支持的类型**：
- `Folder` - 文件夹
- `CollectionFolder` - 集合文件夹
- `UserView` - 用户视图
- `Season` - 季（某些情况下）
- `Series` - 系列（某些情况下）

**支持的类型**：
- `Movie` - 电影
- `Episode` - 剧集
- `Audio` - 音频
- `Video` - 视频

#### 2. 权限问题

该媒体项可能需要特定的用户权限才能访问。

#### 3. 媒体项已被删除

该媒体项可能已从 Emby 服务器中删除，但列表缓存还未更新。

## 🔧 解决方案

### 方案 1：添加回退机制（已实现）

当 `/Items/{id}` 返回 404 时，自动尝试使用 `/Users/{userId}/Items/{id}`。

**修改文件**：`EmbyServiceImpl.java`

```java
@Override
public EmbyItem getItemDetail(String itemId) {
    log.info("开始获取媒体项[{}]的详情", itemId);

    String path = "/Items/" + itemId;
    Map<String, Object> params = new HashMap<>();
    params.put("Fields", "Path,MediaSources,Genres,Tags,Studios,People,Overview");

    try {
        JSONObject response = sendGetRequest(path, params);
        EmbyItem item = parseEmbyItem(response);
        log.info("成功获取媒体项[{}]的详情: name={}, type={}", itemId, item.getName(), item.getType());
        return item;
    } catch (BusinessException e) {
        log.error("获取媒体项[{}]详情失败: {}", itemId, e.getMessage());

        // 如果是 404 错误，尝试使用用户ID的方式获取
        if (e.getMessage().contains("404")) {
            log.info("尝试使用用户ID方式获取媒体项[{}]详情", itemId);
            try {
                String userId = embyAuthService.getUserId();
                path = "/Users/" + userId + "/Items/" + itemId;
                JSONObject response = sendGetRequest(path, params);
                EmbyItem item = parseEmbyItem(response);
                log.info("成功通过用户ID获取媒体项[{}]的详情", itemId);
                return item;
            } catch (Exception ex) {
                log.error("通过用户ID获取媒体项[{}]详情也失败: {}", itemId, ex.getMessage());
                throw new BusinessException("无法获取媒体项详情，该项目可能不存在或无权访问");
            }
        }
        throw e;
    }
}
```

### 方案 2：过滤不支持的类型

在列表中隐藏"详情"按钮，对于不支持的类型。

**修改文件**：`EmbyManager.vue`

```vue
<el-table-column label="操作" width="100" fixed="right">
  <template #default="{ row }">
    <!-- 只为支持的类型显示详情按钮 -->
    <el-button
      v-if="['Movie', 'Episode', 'Audio', 'Video'].includes(row.type)"
      type="primary"
      link
      size="small"
      @click="viewItemDetail(row)"
    >
      <el-icon><View /></el-icon>
      详情
    </el-button>
    <el-text v-else type="info" size="small">不支持</el-text>
  </template>
</el-table-column>
```

### 方案 3：改进错误提示

在前端添加更友好的错误提示。

**修改文件**：`EmbyManager.vue`

```javascript
const viewItemDetail = async (item) => {
  console.log('查看详情 - itemId:', item.id, 'name:', item.name, 'type:', item.type)

  try {
    const res = await getItemDetail(item.id)
    currentItem.value = res.data
    detailDialogVisible.value = true
  } catch (error) {
    console.error('加载详情失败:', error)

    let errorMsg = '加载详情失败'
    if (error.response?.status === 404) {
      errorMsg = `该媒体项不存在或无权访问（ID: ${item.id}）`
    } else if (error.message) {
      errorMsg = '加载详情失败: ' + error.message
    }

    ElMessage.error(errorMsg)
  }
}
```

## 🧪 调试步骤

### 1. 查看控制台日志

打开浏览器开发者工具 -> Console 标签，点击"详情"按钮，查看输出：

```javascript
查看详情 - itemId: 9045 name: 某个媒体项 type: Folder
```

**关键信息**：
- `itemId`: 媒体项ID
- `name`: 媒体项名称
- `type`: 媒体项类型（重要！）

### 2. 查看后端日志

```bash
tail -f backend/logs/application.log | grep "获取媒体项"
```

**期望输出**：
```
开始获取媒体项[9045]的详情
获取媒体项[9045]详情失败: Emby API请求失败: 404
尝试使用用户ID方式获取媒体项[9045]详情
成功通过用户ID获取媒体项[9045]的详情
```

### 3. 测试不同类型的媒体项

分别点击不同类型媒体项的"详情"按钮：

| 类型 | 是否成功 | 备注 |
|------|---------|------|
| Movie | ✅ | 应该成功 |
| Episode | ✅ | 应该成功 |
| Audio | ✅ | 应该成功 |
| Folder | ❌ | 可能失败 |
| Series | ⚠️ | 可能成功或失败 |

### 4. 直接测试 Emby API

使用 curl 测试 Emby API：

```bash
# 测试方式 1：直接访问
curl -H "X-Emby-Token: YOUR_TOKEN" \
  http://104.251.122.51:8096/emby/Items/9045

# 测试方式 2：通过用户ID访问
curl -H "X-Emby-Token: YOUR_TOKEN" \
  http://104.251.122.51:8096/emby/Users/USER_ID/Items/9045
```

## 📊 媒体项类型说明

### Emby 媒体项类型层次结构

```
CollectionFolder (媒体库)
  ├─ Folder (文件夹)
  │   ├─ Movie (电影) ✅ 支持详情
  │   └─ Video (视频) ✅ 支持详情
  │
  ├─ Series (系列)
  │   ├─ Season (季)
  │   │   └─ Episode (剧集) ✅ 支持详情
  │
  └─ MusicAlbum (音乐专辑)
      └─ Audio (音频) ✅ 支持详情
```

### 类型判断

**可以查看详情的类型**：
- `Movie` - 电影
- `Episode` - 剧集
- `Audio` - 音频
- `Video` - 视频
- `MusicVideo` - 音乐视频
- `Photo` - 照片

**不建议查看详情的类型**：
- `Folder` - 文件夹（应该显示子项列表）
- `CollectionFolder` - 集合文件夹（应该显示子项列表）
- `UserView` - 用户视图（应该显示子项列表）
- `BoxSet` - 合集（应该显示子项列表）

**可能需要特殊处理的类型**：
- `Series` - 系列（应该显示季列表）
- `Season` - 季（应该显示剧集列表）
- `MusicAlbum` - 音乐专辑（应该显示曲目列表）

## 💡 改进建议

### 1. 根据类型显示不同操作

```vue
<el-table-column label="操作" width="150" fixed="right">
  <template #default="{ row }">
    <!-- 可以查看详情的类型 -->
    <el-button
      v-if="['Movie', 'Episode', 'Audio', 'Video'].includes(row.type)"
      type="primary"
      link
      size="small"
      @click="viewItemDetail(row)"
    >
      <el-icon><View /></el-icon>
      详情
    </el-button>

    <!-- 文件夹类型 -->
    <el-button
      v-else-if="['Folder', 'CollectionFolder'].includes(row.type)"
      type="success"
      link
      size="small"
      @click="viewFolderItems(row)"
    >
      <el-icon><FolderOpened /></el-icon>
      打开
    </el-button>

    <!-- 系列类型 -->
    <el-button
      v-else-if="row.type === 'Series'"
      type="warning"
      link
      size="small"
      @click="viewSeasons(row)"
    >
      <el-icon><List /></el-icon>
      季列表
    </el-button>

    <!-- 其他类型 -->
    <el-text v-else type="info" size="small">{{ row.type }}</el-text>
  </template>
</el-table-column>
```

### 2. 添加类型图标

```vue
<el-icon v-if="row.type === 'Movie'" color="#34c759"><Film /></el-icon>
<el-icon v-else-if="row.type === 'Series'" color="#007aff"><Monitor /></el-icon>
<el-icon v-else-if="row.type === 'Episode'" color="#007aff"><VideoPlay /></el-icon>
<el-icon v-else-if="row.type === 'Folder'" color="#ff9500"><Folder /></el-icon>
<el-icon v-else-if="row.type === 'Audio'" color="#ff9500"><Headset /></el-icon>
<el-icon v-else color="#86868b"><Document /></el-icon>
```

### 3. 添加类型过滤

```vue
<el-select v-model="typeFilter" placeholder="筛选类型" clearable>
  <el-option label="全部" value="" />
  <el-option label="电影" value="Movie" />
  <el-option label="剧集" value="Episode" />
  <el-option label="音频" value="Audio" />
  <el-option label="文件夹" value="Folder" />
</el-select>
```

## 🎉 总结

### 问题根源

某些类型的媒体项（如 Folder）不能通过 `/Items/{id}` 直接访问，需要通过 `/Users/{userId}/Items/{id}` 访问。

### 解决方案

1. ✅ 添加回退机制：404 时自动尝试用户ID方式
2. ✅ 改进错误提示：显示友好的错误信息
3. ✅ 添加日志：便于调试和定位问题

### 下一步

根据实际测试结果，可以考虑：
- 为不同类型的媒体项显示不同的操作按钮
- 添加类型过滤功能
- 为文件夹类型实现"打开"功能，显示子项列表

---

**现在请重启后端，然后点击"详情"按钮，查看控制台输出的 itemId 和 type，告诉我具体是什么类型的媒体项！** 🔍
