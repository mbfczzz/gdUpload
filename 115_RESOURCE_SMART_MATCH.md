# 115 资源智能匹配转存功能

## 功能概述

在 Emby 媒体项列表点击"搜索下载"按钮时，系统会先从 115 资源库智能搜索匹配资源，如果找到就直接转存到 115 网盘，否则走原有的搜索引擎搜��逻辑。

## 实现文件

### 后端

1. **实体类**: `backend/src/main/java/com/gdupload/entity/Resource115.java`
   - 115 资源实体，对应 `115_resource` 表

2. **Mapper**: `backend/src/main/java/com/gdupload/mapper/Resource115Mapper.java`
   - MyBatis Plus Mapper 接口

3. **Service 接口**: `backend/src/main/java/com/gdupload/service/IResource115Service.java`
   - 定义智能搜索方法

4. **Service 实现**: `backend/src/main/java/com/gdupload/service/impl/Resource115ServiceImpl.java`
   - 实现智能匹配逻辑，支持多种匹配策略

5. **Controller**: `backend/src/main/java/com/gdupload/controller/Resource115Controller.java`
   - 提供 `/resource115/smart-search` API 接口

### 前端

1. **API 文件**: `frontend/src/api/resource115.js`
   - 封装 115 资源搜索 API

2. **页面修改**: `frontend/src/views/EmbyManager.vue`
   - 修改 `handleQuickDownload` 方法，添加 115 资源优先匹配逻辑
   - 添加 `transfer115Resource` 方法，处理 115 资源转存

### 数据库

1. **表结构**: `database/115_resource.sql`
   - 创建 `115_resource` 表
   - 插入示例数据

## 智能匹配策略

Service 层实现了多层级的智能匹配策略：

### 1. 精确匹配（名称 + 年份）

尝试以下格式：
- `名称 (年份)` - 如 "韫色过浓 (2020)"
- `名称 年份` - 如 "韫色过浓 2020"

### 2. 模糊匹配（只用名称）

去除年份后匹配：
- "韫色过浓"

### 3. 分词匹配

去除特殊字符后匹配：
- 只保留中文、英文、数字
- 至少 3 个字符才搜索

### 4. 多名称支持

同时使用以下名称进行匹配：
- 中文名称 (`name`)
- 原始名称 (`originalTitle`)

## 工作流程

```
用户点击"搜索下载"
    ↓
调用 smartSearch115 API
    ↓
找到匹配资源？
    ├─ 是 → 直接转存到 115
    │        ↓
    │     转存成功？
    │        ├─ 是 → 完成，关闭对话框
    │        └─ 否 → 继续走搜索引擎逻辑
    │
    └─ 否 → 走原有搜索引擎逻辑
```

## API 接口

### 智能搜索 115 资源

**请求**:
```
GET /resource115/smart-search
```

**参数**:
- `name` (必填): 媒体项名称
- `originalTitle` (可选): 原始名称
- `year` (可选): 年份

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "韫色过浓 (2020)",
    "type": "国产剧",
    "size": 168.87,
    "url": "https://115.com/s/swzxneb36gr",
    "accessCode": "1234",
    "sort": 1
  }
}
```

如果未找到匹配资源，`data` 为 `null`。

## 数据库表结构

```sql
CREATE TABLE `115_resource` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(500) NOT NULL COMMENT '资源名称',
  `type` VARCHAR(50) DEFAULT NULL COMMENT '资源类型',
  `size` DECIMAL(10,2) DEFAULT NULL COMMENT '资源大小（GB）',
  `url` VARCHAR(500) NOT NULL COMMENT '115分享链接',
  `access_code` VARCHAR(50) DEFAULT NULL COMMENT '访问码',
  `sort` INT DEFAULT 0 COMMENT '排序',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_name` (`name`(255)),
  INDEX `idx_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 使用说明

### 1. 执行数据库脚本

```bash
mysql -u root -p gd_upload_manager < database/115_resource.sql
```

### 2. 添加资源数据

可以通过 SQL 直接插入资源：

```sql
INSERT INTO `115_resource` (`name`, `type`, `size`, `url`, `access_code`, `sort`)
VALUES ('电影名称 (年份)', '类型', 大小, '115链接', '访问码', 排序);
```

### 3. 使用功能

1. 在 Emby 管理页面，找到想要下载的媒体项
2. 点击"搜索下载"按钮
3. 系统会自动：
   - 先从 115 资源库搜索匹配资源
   - 如果找到，直接转存
   - 如果没找到或转存失败，使用搜索引擎搜索

## 转存记录

所有转存操作（包括 115 资源转存）都会记录到 `transfer_history` 表中，可以在媒体项列表点击"转存记录"查看历史。

## 注意事项

1. **资源名称格式**: 建议使用 "名称 (年份)" 格式，如 "韫色过浓 (2020)"
2. **访问码**: 如果 115 分享链接需要访问码，请在 `access_code` 字段填写
3. **云盘类型**: 转存时 `cloudType` 设置为 "115"
4. **匹配分数**: 115 资源库匹配的资源，匹配分数固定为 100

## 优势

1. **快速转存**: 无需搜索引擎，直接从资源库匹配
2. **准确度高**: 自己维护的资源库，匹配准确
3. **降级处理**: 如果资源库没有或转存失败，自动降级到搜索引擎
4. **完整记录**: 所有转存操作都有历史记录

## 扩展建议

1. **批量导入**: 可以开发批量导入功能，从 Excel 或 CSV 导入资源
2. **资源管理**: 可以开发前端页面管理 115 资源（增删改查）
3. **自动更新**: 可以定期检查资源链接有效性
4. **分类管理**: 可以按类型、年份等维度分类管理资源

---

**实施日期**: 2026-02-03
**状态**: ✅ 完成
