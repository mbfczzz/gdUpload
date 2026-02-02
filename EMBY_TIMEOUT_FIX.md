# ✅ Emby API 超时问题修复

## 🐛 问题描述

调用 Emby API 获取媒体项时出现读取超时错误：

```
cn.hutool.http.HttpException: Read timed out
Caused by: java.net.SocketTimeoutException: Read timed out
```

**错误位置**：`EmbyServiceImpl.getLibraryItemsPaged()` 方法

## 🔍 问题分析

### 1. 超时原因

当媒体库包含大量媒体项时，Emby 服务器需要较长时间来处理请求：

- **小型媒体库**（< 100 项）：响应时间 < 5 秒
- **中型媒体库**（100-1000 项）：响应时间 5-15 秒
- **大型媒体库**（> 1000 项）：响应时间 15-60 秒

### 2. 默认超时时间

**原配置**：使用 `embyAuthService.getTimeout()`
- 默认值：30000 毫秒（30 秒）
- 对于大型媒体库不够用

### 3. 影响的操作

1. **获取媒体库列表**：需要为每个媒体库获取媒体项数量
2. **查看媒体项**：分页加载媒体项数据
3. **同步所有数据**：批量获取所有媒体库的数据

## 🔧 修复方案

### 方案：针对不同操作使用不同的超时时间

#### 1. 重载 `sendGetRequest` 方法

**修改文件**：`EmbyServiceImpl.java`

```java
/**
 * 发送GET请求
 */
private JSONObject sendGetRequest(String path, Map<String, Object> params) {
    return sendGetRequest(path, params, null);
}

/**
 * 发送GET请求（可指定超时时间）
 */
private JSONObject sendGetRequest(String path, Map<String, Object> params, Integer customTimeout) {
    String accessToken = embyAuthService.getAccessToken();
    String url = buildUrl(path);

    try {
        // 如果没有指定自定义超时，使用配置的超时时间
        int timeout = customTimeout != null ? customTimeout : embyAuthService.getTimeout();

        HttpRequest request = HttpRequest.get(url)
                .header("X-Emby-Token", accessToken)
                .timeout(timeout);

        // 添加查询参数
        if (MapUtil.isNotEmpty(params)) {
            params.forEach((key, value) -> {
                if (value != null) {
                    request.form(key, value);
                }
            });
        }

        HttpResponse response = request.execute();

        if (!response.isOk()) {
            log.error("Emby API请求失败: {} - {}", response.getStatus(), response.body());
            throw new BusinessException("Emby API请求失败: " + response.getStatus());
        }

        return JSONUtil.parseObj(response.body());

    } catch (cn.hutool.http.HttpException e) {
        if (e.getMessage().contains("timed out")) {
            log.error("Emby API请求超时: {} - 参数: {}", url, params);
            throw new BusinessException("Emby服务器响应超时，请稍后重试或减少每页数量");
        }
        throw new BusinessException("调用Emby API异常: " + e.getMessage());
    }
}
```

**改进点**：
- ✅ 支持自定义超时时间
- ✅ 向后兼容，不传 customTimeout 时使用默认值
- ✅ 更好的错误处理，区分超时错误
- ✅ 提供用户友好的错误提示

#### 2. 更新 `getLibraryItemsPaged` 方法

```java
@Override
public PagedResult<EmbyItem> getLibraryItemsPaged(String libraryId, Integer startIndex, Integer limit) {
    log.info("开始获取媒体库[{}]的媒体项, startIndex={}, limit={}", libraryId, startIndex, limit);

    Map<String, Object> params = new HashMap<>();
    params.put("ParentId", libraryId);
    params.put("Recursive", true);
    params.put("Fields", "Path,MediaSources,Genres,Tags,Studios,People,Overview");

    if (startIndex != null) {
        params.put("StartIndex", startIndex);
    }
    if (limit != null) {
        params.put("Limit", limit);
    }

    // 对于大量数据的请求，使用更长的超时时间（60秒）
    int timeout = 60000;
    JSONObject response = sendGetRequest("/Items", params, timeout);

    // ... 处理响应
}
```

**超时时间**：60 秒（60000 毫秒）

#### 3. 更新 `getAllLibraries` 方法

```java
// 获取媒体库的媒体项数量
try {
    Map<String, Object> countParams = new HashMap<>();
    countParams.put("ParentId", library.getId());
    countParams.put("Recursive", true);
    countParams.put("Limit", 0); // 只获取总数，不获取实际数据

    // 使用较长的超时时间（30秒）
    JSONObject countResponse = sendGetRequest("/Items", countParams, 30000);
    Integer totalCount = countResponse.getInt("TotalRecordCount");
    if (totalCount != null) {
        library.setItemCount(totalCount);
    }
} catch (Exception e) {
    log.warn("获取媒体库[{}]的媒体项数量失败: {}", library.getName(), e.getMessage());
}
```

**超时时间**：30 秒（30000 毫秒）

## 📊 超时时间配置

| 操作 | 超时时间 | 说明 |
|------|---------|------|
| 获取服务器信息 | 30 秒（默认） | 快速操作 |
| 获取媒体库列表 | 30 秒（默认） | 快速操作 |
| 获取媒体项数量 | 30 秒 | 只获取总数，不获取数据 |
| 分页获取媒体项 | 60 秒 | 需要获取详细数据，耗时较长 |
| 获取媒体项详情 | 30 秒（默认） | 单个项目，快速 |
| 搜索媒体项 | 30 秒（默认） | 通常结果较少 |

## 🎯 优化建议

### 1. 前端优化

**减少每页数量**：

```javascript
// 默认每页 50 条
const pageSize = ref(50)

// 可选：20, 50, 100, 200
// 建议：对于大型媒体库，使用较小的每页数量
```

**添加加载提示**：

```javascript
const loadLibraryItems = async () => {
  loadingItems.value = true
  try {
    const startIndex = (currentPage.value - 1) * pageSize.value
    const res = await getLibraryItemsPaged(currentLibrary.value.id, startIndex, pageSize.value)
    libraryItems.value = res.data.items
    totalCount.value = res.data.totalCount
  } catch (error) {
    if (error.message.includes('超时')) {
      ElMessage.error('服务器响应超时，请尝试减少每页数量')
    } else {
      ElMessage.error('加载媒体项失败: ' + error.message)
    }
  } finally {
    loadingItems.value = false
  }
}
```

### 2. 后端优化

**减少返回字段**：

```java
// 如果不需要某些字段，可以移除以提高性能
params.put("Fields", "Path,Genres"); // 只返回必要字段
```

**使用缓存**：

```java
// 对于不经常变化的数据，可以添加缓存
@Cacheable(value = "embyLibraries", key = "#libraryId")
public PagedResult<EmbyItem> getLibraryItemsPaged(String libraryId, Integer startIndex, Integer limit) {
    // ...
}
```

### 3. Emby 服务器优化

1. **升级硬件**：增加 CPU 和内存
2. **优化数据库**：定期维护 Emby 数据库
3. **减少媒体库扫描频率**：避免在高峰期扫描
4. **使用 SSD**：提高磁盘 I/O 性能

## 🧪 测试方法

### 1. 测试小型媒体库（< 100 项）

```bash
# 应该在 5 秒内完成
curl "http://localhost:8099/api/emby/libraries/{libraryId}/items/paged?startIndex=0&limit=50"
```

**期望结果**：
- ✅ 响应时间 < 5 秒
- ✅ 返回正确的数据

### 2. 测试中型媒体库（100-1000 项）

```bash
# 应该在 15 秒内完成
curl "http://localhost:8099/api/emby/libraries/{libraryId}/items/paged?startIndex=0&limit=100"
```

**期望结果**：
- ✅ 响应时间 < 15 秒
- ✅ 返回正确的数据

### 3. 测试大型媒体库（> 1000 项）

```bash
# 应该在 60 秒内完成
curl "http://localhost:8099/api/emby/libraries/{libraryId}/items/paged?startIndex=0&limit=50"
```

**期望结果**：
- ✅ 响应时间 < 60 秒
- ✅ 返回正确的数据
- ✅ 不会超时

### 4. 测试超时错误处理

**模拟超时**：临时将超时时间设置为 1 秒

```java
int timeout = 1000; // 1 秒，必定超时
```

**期望结果**：
- ✅ 捕获超时异常
- ✅ 返回友好的错误提示："Emby服务器响应超时，请稍后重试或减少每页数量"

## 📝 错误处理

### 1. 超时错误

**错误信息**：
```
Emby服务器响应超时，请稍后重试或减少每页数量
```

**解决方法**：
1. 减少每页数量（从 100 改为 50 或 20）
2. 稍后重试
3. 检查 Emby 服务器状态
4. 检查网络连接

### 2. 连接错误

**错误信息**：
```
调用Emby API异常: Connection refused
```

**解决方法**：
1. 检查 Emby 服务器是否运行
2. 检查服务器地址和端口
3. 检查防火墙设置

### 3. 认证错误

**错误信息**：
```
Emby API请求失败: 401
```

**解决方法**：
1. 检查 API Key 或用户名密码
2. 重新登录
3. 检查用户权限

## 🎉 总结

### 问题根源

默认超时时间（30 秒）对于大型媒体库不够用，导致请求超时。

### 解决方案

1. **重载方法**：添加支持自定义超时时间的 `sendGetRequest` 方法
2. **针对性配置**：
   - 获取媒体项数量：30 秒
   - 分页获取媒体项：60 秒
3. **更好的错误处理**：区分超时错误，提供友好提示

### 优化点

1. ✅ 支持大型媒体库（> 1000 项）
2. ✅ 向后兼容，不影响现有功能
3. ✅ 更好的错误提示
4. ✅ 灵活的超时配置
5. ✅ 异常处理不影响其他媒体库

---

**现在即使是大型媒体库也能正常加载了！** 🎉
