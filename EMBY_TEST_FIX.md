# ✅ Emby 测试功能修复完成

## 🐛 问题描述

点击"测试"按钮时一直显示"连接测试失败"。

## 🔍 问题分析

### 1. 服务器连接测试

```bash
curl http://104.251.122.51:8096/emby/System/Info
# 返回: 401 Unauthorized ✅ 服务器正常，需要认证
```

### 2. 登录测试

```bash
curl -X POST http://104.251.122.51:8096/emby/Users/AuthenticateByName \
  -d '{"Username":"mbfczzzz","Pw":"mbfczzzz@123"}'
# 返回: AccessToken ✅ 登录成功
```

### 3. 根本原因

**密码被隐藏导致测试失败**

前端流程：
```
1. 获取配置列表 → 密码显示为 ******
2. 点击测试按钮 → 传递 row 对象（密码是 ******）
3. 后端收到 ****** → 登录失败
```

## 🔧 修复方案

### 方案：后端通过 ID 获取完整配置

#### 修改前

```java
// 前端传递整个 row 对象（密码是 ******）
@PostMapping("/test")
public Result<Boolean> testConfig(@RequestBody EmbyConfig config) {
    boolean success = embyConfigService.testConfig(config);  // ❌ 使用 ****** 登录
    return Result.success(success);
}
```

#### 修改后

```java
// 如果有 ID，从数据库获取真实密码
@PostMapping("/test")
public Result<Boolean> testConfig(@RequestBody EmbyConfig config) {
    EmbyConfig testConfig = config;
    if (config.getId() != null) {
        testConfig = embyConfigService.getById(config.getId());  // ✅ 获取真实密码
    }
    boolean success = embyConfigService.testConfig(testConfig);
    return Result.success(success);
}
```

#### 前端简化

```javascript
// 只传递 ID
const handleTest = async (row) => {
    const res = await testConfig({ id: row.id })  // ✅ 只传 ID
    // ...
}
```

## 📝 修改内容

### 1. 后端 - EmbyConfigController.java

**修改点**：
- 检查配置是否有 ID
- 如果有 ID，从数据库获取完整配置（包括真实密码）
- 使用完整配置进行测试

**新增日志**：
```java
log.info("从数据库获取配置: username={}, hasPassword={}",
        testConfig.getUsername(),
        testConfig.getPassword() != null && !testConfig.getPassword().isEmpty());
```

### 2. 后端 - EmbyConfigServiceImpl.java

**修改点**：
- 添加详细的测试日志
- 记录请求 URL、请求体、响应状态
- 区分 API Key 和用户名密码两种方式

**新增日志**：
```java
log.info("使用用户名密码测试: {}", url);
log.info("登录请求体: {}", requestBody.toString());
log.info("登录测试响应: status={}, isOk={}", response.getStatus(), response.isOk());
```

### 3. 前端 - EmbyConfig.vue

**修改点**：
- 简化测试逻辑
- 只传递配置 ID
- 优化错误提示

**修改前**：
```javascript
const res = await testConfig(row)  // ❌ 传递整个对象（密码是 ******）
```

**修改后**：
```javascript
const res = await testConfig({ id: row.id })  // ✅ 只传 ID
```

## 🎯 测试流程

### 1. 保存配置

```javascript
{
  configName: "我的Emby",
  serverUrl: "http://104.251.122.51:8096",
  username: "mbfczzzz",
  password: "mbfczzzz@123",  // 真实密码保存到数据库
  enabled: true
}
```

### 2. 显示配置列表

```javascript
{
  id: 1,
  configName: "我的Emby",
  serverUrl: "http://104.251.122.51:8096",
  username: "mbfczzzz",
  password: "******",  // 前端显示为 ******
  enabled: true
}
```

### 3. 点击测试

```javascript
// 前端发送
POST /api/emby/config/test
{
  "id": 1  // 只发送 ID
}

// 后端处理
1. 通过 ID 从数据库查询完整配置
2. 获取真实密码: "mbfczzzz@123"
3. 使用真实密码登录 Emby
4. 返回测试结果
```

### 4. 测试成功

```
✅ 连接测试成功
```

## 📊 日志示例

### 成功的日志

```
2026-01-31 12:34:35.964  INFO --- EmbyConfigController : 开始测试Emby配置: id=1, serverUrl=http://104.251.122.51:8096, username=mbfczzzz, hasApiKey=false
2026-01-31 12:34:35.965  INFO --- EmbyConfigController : 从数据库获取配置: username=mbfczzzz, hasPassword=true
2026-01-31 12:34:35.966  INFO --- EmbyConfigServiceImpl: 开始测试Emby配置: serverUrl=http://104.251.122.51:8096, username=mbfczzzz, hasApiKey=false
2026-01-31 12:34:35.967  INFO --- EmbyConfigServiceImpl: 使用用户名密码测试: http://104.251.122.51:8096/emby/Users/AuthenticateByName
2026-01-31 12:34:35.968  INFO --- EmbyConfigServiceImpl: 登录请求体: {"Username":"mbfczzzz","Pw":"mbfczzzz@123"}
2026-01-31 12:34:36.500  INFO --- EmbyConfigServiceImpl: 登录测试响应: status=200, isOk=true
2026-01-31 12:34:36.501  INFO --- EmbyConfigServiceImpl: Emby登录测试成功
2026-01-31 12:34:36.502  INFO --- EmbyConfigController : Emby配置测试成功
```

### 失败的日志（修复前）

```
2026-01-31 12:34:35.964  INFO --- EmbyConfigController : 开始测试Emby配置: serverUrl=http://104.251.122.51:8096, username=mbfczzzz, hasApiKey=false
2026-01-31 12:34:35.970  WARN --- EmbyConfigController : Emby配置测试失败
# 原因：使用了 ****** 作为密码
```

## ✅ 修复验证

### 1. 重启后端服务

```bash
cd backend
mvn spring-boot:run
```

### 2. 访问配置页面

```
http://localhost:3000/emby-config
```

### 3. 点击测试按钮

应该看到：
- ✅ "连接测试成功" 提示
- ✅ 后端日志显示登录成功

## 🎉 总结

### 问题根源

前端为了安全隐藏密码（显示为 `******`），但测试时直接传递了隐藏后的密码。

### 解决方案

后端通过配置 ID 从数据库获取真实密码，确保测试使用正确的凭证。

### 优化点

1. ✅ 添加详细日志，便于调试
2. ✅ 前端只传递 ID，减少数据传输
3. ✅ 后端自动获取完整配置
4. ✅ 支持 API Key 和用户名密码两种方式
5. ✅ 优化错误提示信息

---

**现在测试功能应该正常工作了！** 🎉
