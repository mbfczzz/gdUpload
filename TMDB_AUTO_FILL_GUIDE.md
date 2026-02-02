# 自动补充 TMDB ID 功能说明

## 功能概述

通过调用 TMDB API，根据资源名称自动查询并补充 `115_resource` 表的 `tmdb_id` 字段。

## 实现方式

### 方式1：通过 API 批量补充（推荐）

#### 1. 配置 TMDB API Key

在 `application.yml` 中添加配置：

```yaml
tmdb:
  api:
    key: your_tmdb_api_key_here
```

#### 2. 获取 TMDB API Key

1. 访问 https://www.themoviedb.org/
2. 注册账号并登录
3. 进入设置 → API → 申请 API Key
4. 选择 "Developer" 类型
5. 填写申请信息（用途可以填写"个人学习"）
6. 获得 API Key（v3 auth）

#### 3. 调用批量补充接口

**方式A：使用 Postman 或 curl**

```bash
curl -X POST http://localhost:8099/tmdb/batch-fill
```

**方式B：在浏览器中访问**

```
http://localhost:8099/tmdb/batch-fill
```

**方式C：在前端添加按钮**

可以在 Emby 管理页面添加一个"补充 TMDB ID"按钮：

```javascript
import { batchFillTmdbIds } from '@/api/tmdb'

const handleBatchFillTmdbIds = async () => {
  try {
    ElMessage.info('正在批量补充 TMDB ID，请稍候...')
    const res = await batchFillTmdbIds()
    ElMessage.success(res.data.message)
  } catch (error) {
    ElMessage.error('批量补充失败: ' + error.message)
  }
}
```

### 方式2：手动 SQL 更新

如果您已经知道某些资源的 TMDB ID，可以直接更新：

```sql
-- 单个更新
UPDATE `115_resource`
SET `tmdb_id` = '114043'
WHERE `name` = '大秦赋 (2020)';

-- 批量更新
UPDATE `115_resource` SET `tmdb_id` = '114043' WHERE `name` LIKE '%大秦赋%';
UPDATE `115_resource` SET `tmdb_id` = '93405' WHERE `name` LIKE '%鱿鱼游戏%';
```

### 方式3：从 Emby 路径提取

如果您的 Emby 媒体文件路径包含 TMDB ID，可以编写脚本提取：

```python
import re
import mysql.connector

# 连接数据库
conn = mysql.connector.connect(
    host='localhost',
    user='root',
    password='your_password',
    database='gd_upload_manager'
)
cursor = conn.cursor()

# 示例路径
paths = [
    "/media/电视剧/国产剧/大秦赋 (2020) {tmdb-114043}/Season 1/...",
    "/media/电影/鱿鱼游戏 (2021) {tmdb-93405}/movie.mkv"
]

for path in paths:
    # 提取名称和 TMDB ID
    match = re.search(r'/([^/]+)\s*\{tmdb-(\d+)\}', path)
    if match:
        name = match.group(1)
        tmdb_id = match.group(2)

        # 更新数据库
        sql = "UPDATE 115_resource SET tmdb_id = %s WHERE name LIKE %s"
        cursor.execute(sql, (tmdb_id, f"%{name}%"))
        print(f"更新: {name} -> TMDB ID: {tmdb_id}")

conn.commit()
cursor.close()
conn.close()
```

## API 说明

### 1. 搜索 TMDB ID

**接口**: `GET /tmdb/search`

**参数**:
- `name` (必填): 资源名称
- `year` (可选): 年份
- `type` (可选): 类型（movie 或 tv）

**示例**:
```
GET /tmdb/search?name=大秦赋&year=2020&type=tv
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": "114043"
}
```

### 2. 批量补充 TMDB ID

**接口**: `POST /tmdb/batch-fill`

**参数**: 无

**示例**:
```
POST /tmdb/batch-fill
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "successCount": 6,
    "message": "批量补充完成，成功 6 个"
  }
}
```

## 工作流程

```
批量补充 TMDB ID
    ↓
查询所有 tmdb_id 为空的资源
    ↓
遍历每个资源
    ├─ 从名称中提取年份
    ├─ 根据类型判断是电影还是电视剧
    ├─ 调用 TMDB API 搜索
    ├─ 获取第一个匹配结果的 ID
    ├─ 更新数据库
    └─ 休眠 250ms（避免请求过快）
    ↓
返回成功数量
```

## 匹配逻辑

### 1. 名称清理

自动去除年份括号：
- `大秦赋 (2020)` → `大秦赋`
- `鱿鱼游戏 （2021）` → `鱿鱼游戏`

### 2. 年份提取

从名称中提取年份用于精确匹配：
- `大秦赋 (2020)` → 年份: `2020`

### 3. 类型判断

根据资源类型判断搜索类型：
- 包含"电影"或"movie" → 搜索电影 (movie)
- 其他 → 搜索电视剧 (tv)

### 4. API 调用

调用 TMDB API：
```
https://api.themoviedb.org/3/search/tv?api_key=xxx&query=大秦赋&language=zh-CN&year=2020
```

### 5. 结果选择

返回第一个匹配结果的 ID。

## 示例

### 示例1：国产剧

**资源**:
- 名称: `大秦赋 (2020)`
- 类型: `国产剧`

**处理过程**:
1. 清理名称: `大秦赋`
2. 提取年份: `2020`
3. 判断类型: `tv`（电视剧）
4. 调用 API: `search/tv?query=大秦赋&year=2020`
5. 获取结果: TMDB ID = `114043`
6. 更新数据库: `UPDATE 115_resource SET tmdb_id = '114043' WHERE id = 1`

### 示例2：韩剧

**资源**:
- 名称: `鱿鱼游戏 (2021)`
- 类型: `韩剧`

**处理过程**:
1. 清理名称: `鱿鱼游戏`
2. 提取年份: `2021`
3. 判断类型: `tv`
4. 调用 API: `search/tv?query=鱿鱼游戏&year=2021`
5. 获取结果: TMDB ID = `93405`
6. 更新数据库

## 注意事项

### 1. API Key 配置

必须在 `application.yml` 中配置 TMDB API Key，否则无法使用。

### 2. 请求频率限制

为避免触发 TMDB API 的频率限制，每次请求后会休眠 250ms。

### 3. 匹配准确度

- TMDB API 返回的第一个结果不一定完全准确
- 建议批量补充后，手动检查重要资源的 TMDB ID
- 可以通过 TMDB 网站验证：`https://www.themoviedb.org/tv/{tmdb_id}`

### 4. 中文支持

API 调用时使用 `language=zh-CN` 参数，优先返回中文结果。

### 5. 失败处理

如果某个资源搜索失败，会跳过继续处理下一个，不会中断整个批量补充过程。

## 验证结果

批量补充完成后，可以查询数据库验证：

```sql
-- 查看已补充 TMDB ID 的资源
SELECT id, name, tmdb_id, type
FROM 115_resource
WHERE tmdb_id IS NOT NULL AND tmdb_id != '';

-- 查看未补充的资源
SELECT id, name, type
FROM 115_resource
WHERE tmdb_id IS NULL OR tmdb_id = '';

-- 统计
SELECT
    COUNT(*) AS total,
    SUM(CASE WHEN tmdb_id IS NOT NULL AND tmdb_id != '' THEN 1 ELSE 0 END) AS filled,
    SUM(CASE WHEN tmdb_id IS NULL OR tmdb_id = '' THEN 1 ELSE 0 END) AS empty
FROM 115_resource;
```

## 手动修正

如果自动补充的 TMDB ID 不准确，可以手动修正：

```sql
-- 修正单个资源
UPDATE `115_resource`
SET `tmdb_id` = '正确的TMDB_ID'
WHERE `id` = 资源ID;

-- 清空错误的 TMDB ID
UPDATE `115_resource`
SET `tmdb_id` = NULL
WHERE `tmdb_id` = '错误的TMDB_ID';
```

## 配置文件示例

`application.yml`:

```yaml
# TMDB API 配置
tmdb:
  api:
    key: your_api_key_here  # 替换为您的 TMDB API Key
```

## 日志输出

批量补充时会输出详细日志：

```
开始批量补充 TMDB ID
找到 6 个需要补充 TMDB ID 的资源
处理 [1/6]: 韫色过浓 (2020)
搜索 TMDB ID: name=韫色过浓, year=2020, type=tv
找到 TMDB ID: 12345 - 韫色过浓
✓ 成功: 韫色过浓 (2020) -> TMDB ID: 12345
处理 [2/6]: 天衣无缝 (2019)
...
批量补充完成: 成功 5, 失败 1
```

---

**实施日期**: 2026-02-03
**功能状态**: ✅ 已完成
