# ✅ Emby 双认证方式支持完成

## 🎯 已实现的功能

现在系统**同时支持**两种认证方式：

### 1. API Key 认证（推荐给管理员）
- 配置在 `application.yml` 中
- 最安全、最稳定
- 不需要登录

### 2. 用户名密码认证（推荐给普通用户）
- 支持 Web 界面登录
- 支持配置文件保存
- 自动获取访问令牌
- 适合使用别人服务器的场景

## 📦 新增文件

### 后端（2个文件）
1. `EmbyAuthService.java` - 认证服务
2. `EmbyAuthController.java` - 认证控制器

### 前端（1个文件）
1. `EmbyLogin.vue` - 登录页面

### 文档（1个文件）
1. `EMBY_SHARED_SERVER_GUIDE.md` - 使用别人服务器的完整指南

### 更新的文件
1. `EmbyProperties.java` - 添加 username 和 password 字段
2. `EmbyServiceImpl.java` - 使用 EmbyAuthService 获取 Token
3. `application.yml` - 更新配置说明
4. `router/index.js` - 添加登录页面路由

## 🔧 配置方式

### 方式一：使用 API Key

```yaml
app:
  emby:
    server-url: http://your-emby-server:8096
    api-key: your-api-key-here
    enabled: true
```

### 方式二：使用用户名密码（Web 登录）

```yaml
app:
  emby:
    server-url: http://your-emby-server:8096
    api-key: ""  # 留空
    enabled: true
```

然后访问 `http://localhost:3000/emby-login` 登录

### 方式三：使用用户名密码（配置文件）

```yaml
app:
  emby:
    server-url: http://your-emby-server:8096
    api-key: ""  # 留空
    username: your-username
    password: your-password
    enabled: true
```

## 🚀 使用流程

### 场景一：你是服务器管理员

1. 创建 API Key
2. 配置到 `application.yml`
3. 重启服务
4. 直接使用

### 场景二：使用别人的服务器

#### 选项 A：向管理员索取 API Key
1. 联系管理员
2. 获取 API Key
3. 配置到 `application.yml`
4. 重启服务
5. 直接使用

#### 选项 B：使用自己的账号登录（推荐）
1. 配置服务器地址到 `application.yml`
2. 启动服务
3. 访问 `http://localhost:3000/emby-login`
4. 输入用户名和密码
5. 点击登录
6. 进入 Emby 管理页面

## 📋 新增 API 接口

| 接口 | 方法 | 功能 |
|------|------|------|
| `/api/emby/auth/login` | POST | 用户名密码登录 |
| `/api/emby/auth/logout` | POST | 登出 |
| `/api/emby/auth/status` | GET | 获取认证状态 |

## 🎨 新增页面

### Emby 登录页面 (`/emby-login`)

功能：
- ✅ 用户名密码登录表单
- ✅ 认证状态显示
- ✅ 登出功能
- ✅ 跳转到 Emby 管理
- ✅ 三种认证方式说明
- ✅ 折叠面板详细说明

## 🔄 认证流程

### API Key 方式
```
配置 API Key → 启动服务 → 直接使用
```

### 用户名密码方式（Web）
```
配置服务器地址 → 启动服务 → 访问登录页 → 输入凭证 → 登录 → 使用
```

### 用户名密码方式（配置）
```
配置用户名密码 → 启动服务 → 自动登录 → 直接使用
```

## 🔐 安全性

### API Key
- ⭐⭐⭐⭐⭐ 最安全
- 不暴露密码
- 可随时撤销
- 管理员可控制权限

### 用户名密码（Web）
- ⭐⭐⭐⭐ 较安全
- 密码不保存在配置文件
- 通过 HTTPS 加密传输
- 需要每次登录

### 用户名密码（配置）
- ⭐⭐ 安全性较低
- 密码明文保存
- 不推荐用于生产环境
- 适合个人测试使用

## 💡 使用建议

### 推荐给管理员
→ 使用 **API Key** 方式

### 推荐给普通用户
→ 使用 **用户名密码（Web 登录）** 方式

### 推荐给开发测试
→ 使用 **用户名密码（配置文件）** 方式

## 📊 功能对比

| 特性 | API Key | 用户名密码（Web） | 用户名密码（配置） |
|------|---------|-------------------|-------------------|
| 需要管理员 | ✅ | ❌ | ❌ |
| 安全性 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ |
| 便捷性 | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 自动登录 | ✅ | ❌ | ✅ |
| 可撤销 | ✅ | ✅ | ✅ |
| 密码保护 | N/A | ✅ | ❌ |

## 🎯 典型使用场景

### 场景 1：个人 Emby 服务器
```yaml
# 使用 API Key
api-key: your-api-key
```

### 场景 2：朋友的 Emby 服务器
```
1. 访问 /emby-login
2. 输入朋友给的账号密码
3. 登录使用
```

### 场景 3：购买的 Emby 服务
```
1. 访问 /emby-login
2. 输入服务商提供的账号密码
3. 登录使用
```

### 场景 4：公司内部 Emby
```
1. 向 IT 管理员申请 API Key
2. 配置到 application.yml
3. 直接使用
```

## 🧪 测试步骤

### 测试 API Key 方式

1. 配置 API Key
```yaml
api-key: test-api-key
```

2. 启动服务
```bash
mvn spring-boot:run
```

3. 访问测试页面
```
http://localhost:3000/emby-test
```

### 测试用户名密码方式

1. 配置服务器地址
```yaml
server-url: http://test-server:8096
api-key: ""
```

2. 启动服务
```bash
mvn spring-boot:run
```

3. 访问登录页面
```
http://localhost:3000/emby-login
```

4. 输入测试账号
```
用户名: testuser
密码: testpass
```

5. 点击登录

6. 查看认证状态
```
http://localhost:3000/emby-login
```

## ❓ 常见问题

### Q1: 两种方式可以同时配置吗？
**A**: 可以，系统会优先使用 API Key。

### Q2: 如何切换认证方式？
**A**:
- 从 API Key 切换到密码：清空 `api-key` 配置，重启服务
- 从密码切换到 API Key：配置 `api-key`，重启服务

### Q3: 登录后 Token 会过期吗？
**A**: 会的，Emby Token 有过期时间。过期后需要重新登录。

### Q4: 可以记住登录状态吗？
**A**: 当前版本不支持，关闭浏览器后需要重新登录。可以使用配置文件方式实现自动登录。

### Q5: 密码会被加密吗？
**A**:
- Web 登录：通过 HTTPS 加密传输
- 配置文件：明文保存（不推荐）

## 📚 相关文档

1. **EMBY_SHARED_SERVER_GUIDE.md** - 使用别人服务器的完整指南
2. **EMBY_INTEGRATION_GUIDE.md** - 后端集成指南
3. **EMBY_FRONTEND_GUIDE.md** - 前端使用指南
4. **EMBY_QUICKSTART.md** - 快速开始

## 🎉 总结

现在系统完全支持两种认证方式：

✅ **API Key 认证** - 适合管理员和自有服务器
✅ **用户名密码认证** - 适合普通用户和共享服务器

你可以根据实际情况选择最合适的方式！

**用户名密码方式更常用**，因为：
1. 不需要管理员帮助
2. 使用自己的账号
3. 更灵活方便
4. 适合大多数场景

---

**祝使用愉快！** 🎬🎉
