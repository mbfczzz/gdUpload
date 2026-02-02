# 🎯 Emby 配置管理功能说明

## ✨ 新功能

现在 Emby 配置已经改为 **Web 界面管理**，不需要修改配置文件和重启服务！

## 📋 功能特点

✅ **Web 界面管理** - 所有配置通过浏览器操作
✅ **多配置支持** - 可以添加多个 Emby 服务器配置
✅ **默认配置** - 设置常用的配置为默认
✅ **即时生效** - 修改配置后立即生效，无需重启
✅ **配置测试** - 保存前可以测试连接
✅ **安全存储** - 密码加密存储在数据库中

## 🚀 使用步骤

### 1. 创建数据库表

运行 SQL 脚本：

```bash
mysql -u root -p gd_upload_manager < database/emby_config.sql
```

或手动执行 `database/emby_config.sql` 中的 SQL 语句。

### 2. 启动服务

```bash
# Windows
start.bat

# Linux/Mac
./start.sh
```

### 3. 访问配置页面

```
http://localhost:3000/emby-config
```

### 4. 添加 Emby 配置

点击"添加配置"按钮，填写信息：

#### 基本信息
- **配置名称**：如"我的Emby服务器"
- **服务器地址**：`http://209.146.116.4:8096`

#### 认证方式（二选一）

**方式一：API Key**
- 选择"API Key"
- 输入 API Key

**方式二：用户名密码**
- 选择"用户名密码"
- 输入用户名：`mbfczzzz`
- 输入密码：`mbfczzzz@123`

#### 其他设置
- **超时时间**：默认 30000 毫秒
- **启用**：是否启用此配置
- **设为默认**：是否设为默认配置
- **备注**：可选说明

### 5. 测试配置

点击"测试"按钮，验证配置是否正确。

### 6. 保存配置

点击"保存"按钮，配置立即生效。

### 7. 使用 Emby 功能

访问 `http://localhost:3000/emby` 即可使用，系统会自动使用默认配置。

## 📊 配置管理功能

### 查看配置列表

- 显示所有配置
- 标记默认配置
- 显示认证方式
- 显示启用状态

### 编辑配置

- 点击"编辑"按钮
- 修改配置信息
- 保存后立即生效

### 删除配置

- 点击"删除"按钮
- 确认后删除

### 设为默认

- 点击"设为默认"按钮
- 该配置将成为默认使用的配置

### 启用/禁用

- 切换开关
- 禁用的配置不会被使用

### 测试连接

- 点击"测试"按钮
- 验证配置是否正确

## 🔄 迁移现有配置

如果你之前在 `application.yml` 中配置了 Emby，可以通过以下方式迁移：

### 方式一：通过 Web 界面添加

1. 访问 `http://localhost:3000/emby-config`
2. 点击"添加配置"
3. 填写之前的配置信息
4. 保存

### 方式二：通过 SQL 插入

```sql
INSERT INTO `emby_config` (
  `config_name`,
  `server_url`,
  `username`,
  `password`,
  `enabled`,
  `is_default`,
  `remark`
)
VALUES (
  '默认配置',
  'http://209.146.116.4:8096',
  'mbfczzzz',
  'mbfczzzz@123',
  1,
  1,
  '从配置文件迁移'
);
```

## 💡 使用建议

### 多服务器场景

如果你有多个 Emby 服务器：

1. 添加所有服务器配置
2. 设置常用的为默认
3. 需要切换时，重新设置默认配置

### 测试环境和生产环境

1. 添加测试服务器配置
2. 添加生产服务器配置
3. 根据需要切换默认配置

### 安全建议

1. **不要分享配置** - 包含敏感信息
2. **定期更换密码** - 提高安全性
3. **使用 API Key** - 比密码更安全
4. **禁用不用的配置** - 减少风险

## 🎯 优势对比

### 之前（配置文件方式）

❌ 需要修改配置文件
❌ 需要重启服务
❌ 密码明文保存
❌ 不支持多配置
❌ 切换配置麻烦

### 现在（Web 界面方式）

✅ Web 界面操作
✅ 即时生效
✅ 密码加密存储
✅ 支持多配置
✅ 一键切换

## 📱 界面说明

### 配置列表

| 列名 | 说明 |
|------|------|
| # | 序号 |
| 配置名称 | 显示配置名称，默认配置有标记 |
| 服务器地址 | Emby 服务器 URL |
| 认证方式 | API Key 或用户名密码 |
| 用户名 | 登录用户名 |
| 状态 | 启用/禁用开关 |
| 备注 | 配置说明 |
| 操作 | 测试/设为默认/编辑/删除 |

### 添加/编辑对话框

- **基本信息**：配置名称、服务器地址
- **认证方式**：API Key 或用户名密码
- **其他设置**：超时时间、启用状态、默认配置、备注

## ❓ 常见问题

### Q1: 配置保存后不生效？

**A**: 配置是即时生效的，如果不生效：
1. 检查配置是否启用
2. 检查是否设为默认
3. 刷新页面重试

### Q2: 如何切换使用的配置？

**A**: 点击要使用的配置的"设为默认"按钮。

### Q3: 密码会被加密吗？

**A**: 是的，密码在数据库中加密存储，Web 界面显示为 `******`。

### Q4: 可以同时使用多个配置吗？

**A**: 当前版本只能使用一个默认配置，但可以快速切换。

### Q5: 删除配置会影响数据吗？

**A**: 不会，只删除配置信息，不影响 Emby 服务器的数据。

### Q6: 配置文件中的配置还有用吗？

**A**: 不再使用，系统会优先使用数据库中的配置。

## 🔧 技术实现

### 数据库表

```sql
CREATE TABLE `emby_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `config_name` VARCHAR(100) NOT NULL,
  `server_url` VARCHAR(255) NOT NULL,
  `api_key` VARCHAR(255) DEFAULT NULL,
  `username` VARCHAR(100) DEFAULT NULL,
  `password` VARCHAR(255) DEFAULT NULL,
  `user_id` VARCHAR(100) DEFAULT NULL,
  `timeout` INT DEFAULT 30000,
  `enabled` TINYINT(1) DEFAULT 1,
  `is_default` TINYINT(1) DEFAULT 0,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_name` (`config_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/emby/config/list` | GET | 获取所有配置 |
| `/api/emby/config/default` | GET | 获取默认配置 |
| `/api/emby/config/{id}` | GET | 获取配置详情 |
| `/api/emby/config/save` | POST | 保存或更新配置 |
| `/api/emby/config/{id}` | DELETE | 删除配置 |
| `/api/emby/config/{id}/default` | PUT | 设为默认配置 |
| `/api/emby/config/test` | POST | 测试配置 |
| `/api/emby/config/{id}/toggle` | PUT | 启用/禁用配置 |

## 📚 相关文档

- [EMBY_INTEGRATION_GUIDE.md](./EMBY_INTEGRATION_GUIDE.md) - 完整集成指南
- [EMBY_SHARED_SERVER_GUIDE.md](./EMBY_SHARED_SERVER_GUIDE.md) - 使用别人服务器指南
- [EMBY_QUICKSTART.md](./EMBY_QUICKSTART.md) - 快速开始

---

**现在配置更简单了！** 🎉

不需要修改配置文件，不需要重启服务，一切都在 Web 界面完成！
