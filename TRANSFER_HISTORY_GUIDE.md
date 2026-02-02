# 转存历史记录功能 - 部署指南

## 概述

实现转存历史记录功能，记录每次转存的详细信息，并在媒体项列表中显示转存状态。

## 主要变更

### 1. 转存策略调整
- **之前**: 尝试转存3次，成功后停止
- **现在**: 只转存匹配度最高的1个资源
- **失败处理**: 直接显示失败信息，不再重试

### 2. 新增功能
- ✅ 记录每次转存的详细信息（成功/失败）
- ✅ 媒体项列表显示"已转存"标记
- ✅ 查看转存历史记录
- ✅ 批量检查转存状态

## 数据库变更

### 1. 创建转存历史表

执行 SQL 脚本：`database/transfer_history.sql`

```bash
mysql -u root -p gd_upload_manager < database/transfer_history.sql
```

### 2. 表结构

**表名**: `transfer_history`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键ID |
| emby_item_id | varchar(100) | Emby媒体项ID |
| emby_item_name | varchar(500) | Emby媒体项名称 |
| emby_item_year | int | 年份 |
| resource_id | varchar(200) | 资源ID |
| resource_title | varchar(500) | 资源标题 |
| resource_url | text | 资源链接 |
| match_score | decimal(5,2) | 匹配分数 |
| cloud_type | varchar(100) | 云盘类型 |
| cloud_name | varchar(100) | 云盘名称 |
| parent_id | varchar(200) | 目标目录ID |
| transfer_status | varchar(50) | 转存状态：success, failed, pending |
| transfer_message | text | 转存结果消息 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

## 后端变更

### 新增文件

1. **实体类**: `backend/src/main/java/com/gdupload/entity/TransferHistory.java`
2. **Mapper**: `backend/src/main/java/com/gdupload/mapper/TransferHistoryMapper.java`
3. **Service接口**: `backend/src/main/java/com/gdupload/service/ITransferHistoryService.java`
4. **Service实现**: `backend/src/main/java/com/gdupload/service/impl/TransferHistoryServiceImpl.java`
5. **Controller**: `backend/src/main/java/com/gdupload/controller/TransferHistoryController.java`

### API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/transfer-history/save | 保存转存记录 |
| GET | /api/transfer-history/item/{embyItemId} | 获取媒体项的转存历史 |
| GET | /api/transfer-history/check/{embyItemId} | 检查媒体项是否已转存 |
| POST | /api/transfer-history/batch-check | 批量检查转存状态 |
| GET | /api/transfer-history/recent?limit=50 | 获取最近的转存记录 |
| GET | /api/transfer-history/statistics | 获取转存统计信息 |

## 前端变更

### 新增文件

1. **API文件**: `frontend/src/api/transferHistory.js`

### 修改文件

1. **EmbyManager.vue**:
   - 导入转存历史API
   - 添加 `transferStatusMap` 状态映射
   - 修改 `loadLibraryItems()`: 加载媒体项后批量检查转存状态
   - 添加 `loadTransferStatus()`: 批量检查转存状态
   - 修改 `autoTransferBestMatches()`:
     - 只尝试1次（转存匹配度最高的资源）
     - 记录转存历史到数据库
     - 显示详细的转存结果
   - 添加 `viewTransferHistory()`: 查看转存历史
   - 媒体项列表添加"已转存"标记
   - 添加"转存记录"按钮
   - 添加转存历史对话框

## 部署步骤

### 1. 数据库迁移

```bash
# 连接到MySQL
mysql -u root -p

# 选择数据库
use gd_upload_manager;

# 执行迁移脚本
source F:/cluade2/database/transfer_history.sql;

# 验证表是否创建成功
show tables like 'transfer_history';
desc transfer_history;
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

## 功能说明

### 1. 转存流程

1. 用户点击"搜索下载"
2. 系统搜索并智能排序资源
3. AI筛选（可选）
4. 过滤出评分≥30的合格资源
5. **只转存匹配度最高的1个资源**
6. 记录转存结果到数据库（成功或失败）
7. 更新媒体项的转存状态

### 2. 转存状态显示

- **媒体项列表**: 已转存的电影显示绿色"已转存"标记
- **转存记录按钮**: 点击查看该电影的所有转存历史

### 3. 转存历史

显示内容：
- 资源标题
- 匹配分数
- 云盘名称和类型
- 转存状态（成功/失败）
- 转存时间
- 结果消息
- 资源链接

### 4. 批量状态检查

- 加载媒体项列表时自动批量检查转存状态
- 使用 `IN` 查询优化性能
- 结果缓存在 `transferStatusMap` 中

## 使用示例

### 1. 搜索并转存电影

```
1. 打开 Emby 媒体库
2. 点击电影的"搜索下载"按钮
3. 系统自动搜索并转存匹配度最高的资源
4. 转存成功后，电影名称旁显示"已转存"标记
```

### 2. 查看转存历史

```
1. 找到已转存的电影
2. 点击"转存记录"按钮
3. 查看该电影的所有转存历史（包括失败记录）
```

### 3. 转存失败处理

```
如果转存失败：
1. 系统会显示失败原因
2. 失败记录会保存到数据库
3. 可以再次点击"搜索下载"重新尝试
```

## 数据统计

可以通过API获取转存统计信息：

```bash
curl http://localhost:8099/api/transfer-history/statistics
```

返回示例：
```json
{
  "code": 200,
  "data": {
    "totalCount": 150,
    "successCount": 120,
    "failedCount": 30,
    "successRate": "80.00%"
  }
}
```

## 注意事项

1. **转存策略**: 现在只转存匹配度最高的1个资源，不再重试
2. **历史记录**: 所有转存尝试（成功和失败）都会被记录
3. **性能优化**: 使用批量查询检查转存状态，避免N+1查询问题
4. **数据清理**: 建议定期清理旧的转存记录（可选）

## 常见问题

### Q1: 转存失败后如何重试？

**A**: 再次点击"搜索下载"按钮即可重新搜索和转存。

### Q2: 如何查看所有转存记录？

**A**: 调用API：`GET /api/transfer-history/recent?limit=100`

### Q3: 如何清空转存历史？

**A**:
```sql
DELETE FROM transfer_history WHERE emby_item_id = 'xxx';
-- 或清空所有
TRUNCATE TABLE transfer_history;
```

### Q4: 转存状态不更新？

**A**: 刷新页面或切换分页，系统会重新加载转存状态。

## 技术支持

如有问题，请查看：
- 后端日志：`logs/gd-upload-manager.log`
- 浏览器控制台（F12 -> Console）
- 数据库记录：`SELECT * FROM transfer_history ORDER BY create_time DESC LIMIT 10;`
