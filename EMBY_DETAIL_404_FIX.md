# ✅ Emby 媒体项详情 404 错误修复

## 🐛 问题描述

点击媒体项的"详情"按钮时，返回 404 错误。

## 🔍 问题分析

### 可能的原因

#### 1. itemId 包含特殊字符

Emby 的 itemId 可能包含特殊字符，导致 Spring MVC 路由匹配失败。

**示例 itemId**：
```
正常: abc123def456
包含点号: abc.123.def
包含斜杠: abc/123/def (不太可能)
```

#### 2. 路由配置问题

**原配置**：
```java
@GetMapping("/items/{itemId}")
```

**问题**：
- Spring MVC 默认不匹配包含点号的路径参数
- 例如：`/items/abc.123` 会被解析为 `/items/abc`，后缀 `.123` 被当作文件扩展名

#### 3. 前端 URL 编码问题

如果 itemId 包含特殊字符但没有正确编码，也会导致 404。

## 🔧 解决方案

### 方案 1：修改路由配置（推荐）

**修改文件**：`EmbyController.java`

```java
/**
 * 获取媒体项详情
 *
 * @param itemId 媒体项ID
 */
@GetMapping("/items/{itemId:.+}")  // ✅ 添加 .+ 正则表达式
public Result<EmbyItem> getItemDetail(@PathVariable String itemId) {
    log.info("获取媒体项详情: itemId={}", itemId);
    EmbyItem item = embyService.getItemDetail(itemId);
    return Result.success(item);
}
```

**说明**：
- `{itemId:.+}` 表示匹配任意字符（包括点号）
- `.+` 是正则表达式，表示一个或多个任意字符
- 这样可以正确匹配包含点号的 itemId

### 方案 2：URL 编码（备选）

如果方案 1 不起作用，可以在前端对 itemId 进行 URL 编码。

**修改文件**：`emby.js`

```javascript
/**
 * 获取媒体项详情
 */
export function getItemDetail(itemId) {
  return request({
    url: `/emby/items/${encodeURIComponent(itemId)}`,  // ✅ URL 编码
    method: 'get'
  })
}
```

**后端解码**：
```java
@GetMapping("/items/{itemId}")
public Result<EmbyItem> getItemDetail(@PathVariable String itemId) {
    // Spring 会自动解码
    log.info("获取媒体项详情: itemId={}", itemId);
    EmbyItem item = embyService.getItemDetail(itemId);
    return Result.success(item);
}
```

## 🧪 测试方法

### 1. 检查 itemId 格式

在前端控制台查看 itemId：

```javascript
const viewItemDetail = async (item) => {
  console.log('itemId:', item.id)  // 查看 itemId 格式
  try {
    const res = await getItemDetail(item.id)
    currentItem.value = res.data
    detailDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载详情失败: ' + error.message)
  }
}
```

### 2. 检查请求 URL

在浏览器开发者工具 -> Network 标签中查看实际请求的 URL：

```
期望: http://localhost:8099/api/emby/items/abc123def456
实际: http://localhost:8099/api/emby/items/abc.123.def
```

### 3. 测试不同的 itemId

```bash
# 测试正常 itemId
curl http://localhost:8099/api/emby/items/abc123def456

# 测试包含点号的 itemId
curl http://localhost:8099/api/emby/items/abc.123.def

# 测试包含特殊字符的 itemId
curl http://localhost:8099/api/emby/items/abc-123_def
```

### 4. 查看后端日志

```bash
# 查看是否收到请求
tail -f backend/logs/application.log | grep "获取媒体项详情"
```

**期望输出**：
```
2026-01-31 13:30:00.123  INFO --- EmbyController : 获取媒体项详情: itemId=abc123def456
```

## 📊 常见 itemId 格式

根据 Emby 的实现，itemId 通常是以下格式：

| 格式 | 示例 | 是否包含特殊字符 |
|------|------|-----------------|
| 纯数字 | `123456` | ❌ 否 |
| 纯字母 | `abcdef` | ❌ 否 |
| 字母+数字 | `abc123def456` | ❌ 否 |
| UUID | `550e8400-e29b-41d4-a716-446655440000` | ⚠️ 包含连字符 |
| Base64 | `YWJjMTIzZGVmNDU2` | ❌ 否 |
| 包含点号 | `abc.123.def` | ✅ 是 |

## 🔍 调试步骤

### 1. 确认 404 来源

**前端 404**：
- 检查 baseURL 配置
- 检查 API 路径拼接

**后端 404**：
- 检查控制器路由配置
- 检查 itemId 是否包含特殊字符

### 2. 添加日志

**前端**：
```javascript
const viewItemDetail = async (item) => {
  console.log('=== 查看详情 ===')
  console.log('itemId:', item.id)
  console.log('URL:', `/emby/items/${item.id}`)

  try {
    const res = await getItemDetail(item.id)
    console.log('响应:', res)
    currentItem.value = res.data
    detailDialogVisible.value = true
  } catch (error) {
    console.error('错误:', error)
    console.error('错误响应:', error.response)
    ElMessage.error('加载详情失败: ' + error.message)
  }
}
```

**后端**：
```java
@GetMapping("/items/{itemId:.+}")
public Result<EmbyItem> getItemDetail(@PathVariable String itemId) {
    log.info("=== 获取媒体项详情 ===");
    log.info("itemId: {}", itemId);
    log.info("itemId length: {}", itemId.length());
    log.info("itemId contains dot: {}", itemId.contains("."));

    try {
        EmbyItem item = embyService.getItemDetail(itemId);
        log.info("成功获取详情: {}", item.getName());
        return Result.success(item);
    } catch (Exception e) {
        log.error("获取详情失败: {}", e.getMessage(), e);
        throw e;
    }
}
```

### 3. 检查路由注册

启动应用时，查看日志中的路由映射：

```
Mapped "{[/emby/items/{itemId:.+}],methods=[GET]}" onto public com.gdupload.common.Result<com.gdupload.dto.EmbyItem> com.gdupload.controller.EmbyController.getItemDetail(java.lang.String)
```

## 💡 其他可能的问题

### 1. CORS 问题

如果是跨域请求，可能被 CORS 策略阻止。

**检查**：
```javascript
// 浏览器控制台
// 查看是否有 CORS 错误
```

**解决**：
```java
@CrossOrigin(origins = "*")
@GetMapping("/items/{itemId:.+}")
public Result<EmbyItem> getItemDetail(@PathVariable String itemId) {
    // ...
}
```

### 2. 请求方法错误

确保前端使用 GET 方法：

```javascript
export function getItemDetail(itemId) {
  return request({
    url: `/emby/items/${itemId}`,
    method: 'get'  // ✅ 确保是 GET
  })
}
```

### 3. 认证问题

如果需要认证，确保请求包含认证信息：

```javascript
request.interceptors.request.use(
  config => {
    // 添加 token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  }
)
```

## 🎉 总结

### 问题根源

Spring MVC 默认不匹配包含点号的路径参数，导致 itemId 包含点号时返回 404。

### 解决方案

在路由配置中添加 `.+` 正则表达式：

```java
@GetMapping("/items/{itemId:.+}")
```

### 验证方法

1. 查看前端控制台的 itemId 格式
2. 查看 Network 标签的请求 URL
3. 查看后端日志是否收到请求
4. 测试不同格式的 itemId

---

**现在应该可以正常查看媒体项详情了！** 🎉
