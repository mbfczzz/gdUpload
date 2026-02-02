# 智能搜索配置持久化 - 部署指南

## 概述

将智能搜索配置从 localStorage 迁移到数据库，实现配置的持久化存储。

## 数据库变更

### 1. 创建配置表

执行 SQL 脚本：`database/smart_search_config.sql`

```bash
mysql -u root -p gd_upload_manager < database/smart_search_config.sql
```

或者在 MySQL 客户端中执行：

```sql
source F:/cluade2/database/smart_search_config.sql;
```

### 2. 表结构说明

**表名**: `smart_search_config`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键ID |
| user_id | varchar(100) | 用户ID（默认: default） |
| config_name | varchar(100) | 配置名称 |
| config_type | varchar(50) | 配置类型：cloud_config, ai_config, search_config |
| config_data | text | 配置数据（JSON格式） |
| is_active | tinyint(1) | 是否启用 |
| remark | varchar(500) | 备注 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

### 3. 配置类型说明

- **cloud_config**: 云盘配置（可以有多个）
  ```json
  {
    "name": "阿里云盘",
    "cloudType": "channel_alipan",
    "parentId": "697f2333cd2704159fa446d8bc5077584838e3dc",
    "remark": "默认阿里云盘配置"
  }
  ```

- **ai_config**: AI和验证配置（只有一个）
  ```json
  {
    "aiEnabled": true,
    "validateLinks": false
  }
  ```

- **search_config**: 搜索权重配置（只有一个）
  ```json
  {
    "weights": {
      "titleMatch": 40,
      "resolution": 20,
      "fileSize": 15,
      "tagMatch": 10,
      "sourceCredibility": 10,
      "timeliness": 5
    },
    "maxValidationCount": 20,
    "validationTimeout": 10000,
    "debugMode": false
  }
  ```

## 后端变更

### 新增文件

1. **实体类**: `backend/src/main/java/com/gdupload/entity/SmartSearchConfig.java`
2. **Mapper**: `backend/src/main/java/com/gdupload/mapper/SmartSearchConfigMapper.java`
3. **Service接口**: `backend/src/main/java/com/gdupload/service/ISmartSearchConfigService.java`
4. **Service实现**: `backend/src/main/java/com/gdupload/service/impl/SmartSearchConfigServiceImpl.java`
5. **Controller**: `backend/src/main/java/com/gdupload/controller/SmartSearchConfigController.java`

### API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/smart-search-config/full | 获取完整配置 |
| POST | /api/smart-search-config/full | 保存完整配置 |
| GET | /api/smart-search-config/list | 获取所有配置 |
| GET | /api/smart-search-config/type/{configType} | 根据类型获取配置 |
| POST | /api/smart-search-config/save | 保存单个配置 |
| DELETE | /api/smart-search-config/{id} | 删除配置 |

## 前端变更

### 新增文件

1. **API文件**: `frontend/src/api/smartSearchConfig.js`

### 修改文件

1. **SmartSearchConfig.vue**:
   - 导入 `getFullConfig` 和 `saveFullConfig` API
   - 修改 `loadConfig()` 函数：优先从数据库加载，失败则从 localStorage 加载
   - 修改 `handleSave()` 函数：保存到数据库，同时备份到 localStorage

2. **EmbyManager.vue**:
   - 导入 `getFullConfig` API
   - 修改 `loadSmartSearchConfig()` 函数：优先从数据库加载，失败则从 localStorage 加载

## 部署步骤

### 1. 数据库迁移

```bash
# 连接到MySQL
mysql -u root -p

# 选择数据库
use gd_upload_manager;

# 执行迁移脚本
source F:/cluade2/database/smart_search_config.sql;

# 验证表是否创建成功
show tables like 'smart_search_config';
desc smart_search_config;

# 查看默认数据
select * from smart_search_config;
```

### 2. 编译后端

```bash
cd backend
mvn clean package -DskipTests
```

### 3. 重启后端服务

```bash
# Windows
start.bat

# Linux
./start.sh
```

### 4. 编译前端

```bash
cd frontend
npm run build
```

### 5. 测试验证

1. 访问前端页面：`http://localhost:8099/api`
2. 进入"智能搜索配置"页面
3. 修改配置并保存
4. 刷新页面，验证配置是否从数据库加载
5. 查看数据库，验证配置是否正确保存：
   ```sql
   select * from smart_search_config;
   ```

## 数据迁移

如果你已经在 localStorage 中有配置，系统会自动处理：

1. 首次访问时，如果数据库为空，会从 localStorage 加载
2. 点击"保存配置"后，会同时保存到数据库和 localStorage
3. 之后每次加载都会优先从数据库读取

### 手动迁移（可选）

如果需要手动迁移现有配置：

1. 打开浏览器开发者工具（F12）
2. 进入 Console 标签
3. 执行以下代码：

```javascript
// 读取localStorage配置
const config = JSON.parse(localStorage.getItem('smartSearchConfig'))
console.log('当前配置:', config)

// 保存到数据库
fetch('/api/smart-search-config/full', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(config)
}).then(res => res.json()).then(data => {
  console.log('迁移结果:', data)
})
```

## 回滚方案

如果出现问题需要回滚：

### 1. 删除数据库表

```sql
DROP TABLE IF EXISTS smart_search_config;
```

### 2. 恢复代码

```bash
git checkout HEAD -- backend/src/main/java/com/gdupload/entity/SmartSearchConfig.java
git checkout HEAD -- backend/src/main/java/com/gdupload/mapper/SmartSearchConfigMapper.java
git checkout HEAD -- backend/src/main/java/com/gdupload/service/ISmartSearchConfigService.java
git checkout HEAD -- backend/src/main/java/com/gdupload/service/impl/SmartSearchConfigServiceImpl.java
git checkout HEAD -- backend/src/main/java/com/gdupload/controller/SmartSearchConfigController.java
git checkout HEAD -- frontend/src/api/smartSearchConfig.js
git checkout HEAD -- frontend/src/views/SmartSearchConfig.vue
git checkout HEAD -- frontend/src/views/EmbyManager.vue
```

### 3. 重新编译和部署

```bash
cd backend && mvn clean package -DskipTests
cd ../frontend && npm run build
```

## 注意事项

1. **数据备份**: 迁移前建议备份 localStorage 中的配置
2. **兼容性**: 新版本完全兼容旧的 localStorage 配置
3. **双重存储**: 配置会同时保存到数据库和 localStorage，确保可靠性
4. **多用户支持**: 表结构预留了 `user_id` 字段，方便未来扩展多用户功能

## 常见问题

### Q1: 配置保存失败？

**A**: 检查以下几点：
- 数据库表是否创建成功
- 后端服务是否正常启动
- 浏览器控制台是否有错误信息
- 检查后端日志：`logs/gd-upload-manager.log`

### Q2: 配置加载失败？

**A**: 系统会自动降级到 localStorage，不影响使用。检查：
- 数据库连接是否正常
- 后端API是否可访问：`http://localhost:8099/api/smart-search-config/full`

### Q3: 如何清空所有配置？

**A**:
```sql
DELETE FROM smart_search_config WHERE user_id = 'default';
```

然后刷新页面，系统会使用默认配置。

## 技术支持

如有问题，请查看：
- 后端日志：`logs/gd-upload-manager.log`
- 浏览器控制台（F12 -> Console）
- 数据库日志
