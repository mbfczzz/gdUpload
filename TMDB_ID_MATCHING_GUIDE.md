# TMDB ID 优先匹配功能说明

## 功能概述

在 115 资源智能匹配时，优先使用 TMDB ID 进行精确匹配，如果没有匹配到再使用名称模糊匹配。

## 实现内容

### 1. 数据库修改

**文件**: `database/add_tmdb_id_to_115_resource.sql`

为 `115_resource` 表添加 `tmdb_id` 字段：

```sql
ALTER TABLE `115_resource`
ADD COLUMN `tmdb_id` VARCHAR(50) NULL COMMENT 'TMDB ID' AFTER `name`,
ADD INDEX `idx_tmdb_id` (`tmdb_id`);
```

### 2. 后端修改

#### 2.1 实体类 (Resource115.java)

添加 `tmdbId` 字段：

```java
/**
 * TMDB ID
 */
private String tmdbId;
```

#### 2.2 DTO (EmbyItem.java)

添加 `providerIds` 字段用于存储外部ID：

```java
/**
 * 外部ID映射（如 TMDB, IMDB 等）
 */
private java.util.Map<String, String> providerIds;
```

#### 2.3 Service 接口

修改方法签名，添加 `tmdbId` 参数：

```java
Resource115 smartSearch(String tmdbId, String name, String originalTitle, Integer year);
```

#### 2.4 Service 实现

实现 TMDB ID 优先匹配逻辑：

```java
@Override
public Resource115 smartSearch(String tmdbId, String name, String originalTitle, Integer year) {
    // 1. 优先使用 TMDB ID 精确匹配
    if (tmdbId != null && !tmdbId.isEmpty()) {
        LambdaQueryWrapper<Resource115> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Resource115::getTmdbId, tmdbId);
        Resource115 result = resource115Mapper.selectOne(wrapper);

        if (result != null) {
            return result; // TMDB ID 精确匹配成功
        }
    }

    // 2. 如果 TMDB ID 没有匹配，使用名称模糊匹配
    return smartSearchByName(name, originalTitle, year);
}
```

### 3. 前端修改

#### 3.1 API (resource115.js)

添加 `tmdbId` 参数：

```javascript
export function smartSearch115(tmdbId, name, originalTitle, year) {
  return request({
    url: '/resource115/smart-search',
    method: 'get',
    params: {
      tmdbId,
      name,
      originalTitle,
      year
    }
  })
}
```

#### 3.2 页面 (EmbyManager.vue)

从 Emby 媒体项中提取 TMDB ID：

```javascript
// 提取 TMDB ID
let tmdbId = null
if (item.providerIds && item.providerIds.Tmdb) {
  tmdbId = item.providerIds.Tmdb
  console.log('提取到 TMDB ID:', tmdbId)
}

// 调用搜索 API
const res = await smartSearch115(
  tmdbId,
  item.name,
  item.originalTitle,
  item.productionYear
)
```

## 使用方法

### 1. 执行数据库脚本

```bash
mysql -u root -p gd_upload_manager < database/add_tmdb_id_to_115_resource.sql
```

### 2. 为资源添加 TMDB ID

#### 方法1：直接在数据库中更新

```sql
-- 更新单个资源
UPDATE `115_resource`
SET `tmdb_id` = '114043'
WHERE `name` = '大秦赋 (2020)';

-- 批量更新
UPDATE `115_resource` SET `tmdb_id` = '114043' WHERE `name` LIKE '%大秦赋%';
UPDATE `115_resource` SET `tmdb_id` = '93405' WHERE `name` LIKE '%鱿鱼游戏%';
```

#### 方���2：插入新资源时指定 TMDB ID

```sql
INSERT INTO `115_resource` (`name`, `tmdb_id`, `type`, `size`, `url`, `code`)
VALUES ('大秦赋 (2020)', '114043', '国产剧', '109.73', 'https://115.com/s/xxx', '1234');
```

### 3. 获取 TMDB ID

#### 从 Emby 路径中提取

Emby 的媒体文件路径通常包含 TMDB ID：

```
/CloudNAS/CloudDrive/media2/media/电视剧/国产剧/大秦赋 (2020) {tmdb-114043}/Season 1/...
```

提取规则：`{tmdb-114043}` → TMDB ID = `114043`

#### 从 TMDB 网站查询

1. 访问 https://www.themoviedb.org/
2. 搜索电影或电视剧
3. 从 URL 中获取 ID

例如：
- URL: `https://www.themoviedb.org/tv/114043`
- TMDB ID: `114043`

## 匹配流程

```
用户点击"搜索下载"
    ↓
提取 TMDB ID（从 providerIds.Tmdb）
    ↓
调用 smartSearch115(tmdbId, name, originalTitle, year)
    ↓
有 TMDB ID？
    ├─ 是 → 使用 TMDB ID 精确匹配
    │        ↓
    │     匹配成功？
    │        ├─ 是 → 返回资源 ✅
    │        └─ 否 → 继续名称模糊匹配
    │
    └─ 否 → 直接使用名称模糊匹配
              ↓
           匹配成功？
              ├─ 是 → 返回资源 ✅
              └─ 否 → 返回 null，使用搜索引擎
```

## 优势

### 1. 精确匹配

TMDB ID 是唯一标识符，可以避免名称相似导致的误匹配。

**示例**：
- "鱿鱼游戏" (TMDB: 93405)
- "不能犯规的游戏" (TMDB: 另一个ID)

使用 TMDB ID 可以精确区分这两部完全不同的作品。

### 2. 多语言支持

同一部作品在不同地区有不同名称，但 TMDB ID 是唯一的。

**示例**：
- 中文名：鱿鱼游戏
- 英文名：Squid Game
- 韩文名：오징어 게임
- TMDB ID：93405（唯一）

### 3. 降级处理

如果 TMDB ID 没有匹配，自动降级到名称模糊匹配，保证兼容性。

## 示例场景

### 场景1：TMDB ID 精确匹配

**Emby 媒体项**：
- 名称：`大秦赋`
- TMDB ID：`114043`

**115 资源库**：
- 名称：`大秦赋 (2020)`
- TMDB ID：`114043`

**匹配过程**：
1. 提取 TMDB ID：`114043`
2. 使用 TMDB ID 查询：`SELECT * FROM 115_resource WHERE tmdb_id = '114043'`
3. ✅ 精确匹配成功

### 场景2：TMDB ID 未匹配，降级到名称匹配

**Emby 媒体项**：
- 名称：`韫色过浓`
- TMDB ID：`12345`（假设）

**115 资源库**：
- 名称：`韫色过浓 (2020)`
- TMDB ID：`NULL`（未设置）

**匹配过程**：
1. 提取 TMDB ID：`12345`
2. 使用 TMDB ID 查询：未找到
3. 降级到名称模糊匹配：`韫色过浓`
4. ✅ 名称匹配成功

### 场景3：无 TMDB ID，直接名称匹配

**Emby 媒体项**：
- 名称：`天衣无缝`
- TMDB ID：`NULL`（未设置）

**115 资源库**：
- 名称：`天衣无缝 (2019)`
- TMDB ID：`NULL`

**匹配过程**：
1. 无 TMDB ID
2. 直接使用名称模糊匹配：`天衣无缝`
3. ✅ 名称匹配成功

## 数据维护

### 批量更新 TMDB ID

可以编写脚本批量更新资源的 TMDB ID：

```sql
-- 示例：批量更新国产剧的 TMDB ID
UPDATE `115_resource` SET `tmdb_id` = '114043' WHERE `name` = '大秦赋 (2020)';
UPDATE `115_resource` SET `tmdb_id` = '93405' WHERE `name` = '鱿鱼游戏 (2021)';
UPDATE `115_resource` SET `tmdb_id` = '12345' WHERE `name` = '韫色过浓 (2020)';
-- ... 更多资源
```

### 从 Emby 路径提取 TMDB ID

可以编写脚本从 Emby 媒体文件路径中批量提取 TMDB ID：

```python
import re

path = "/CloudNAS/CloudDrive/media2/media/电视剧/国产剧/大秦赋 (2020) {tmdb-114043}/Season 1/..."
match = re.search(r'\{tmdb-(\d+)\}', path)
if match:
    tmdb_id = match.group(1)
    print(f"TMDB ID: {tmdb_id}")  # 输出: 114043
```

## 注意事项

1. **TMDB ID 格式**：纯数字字符串，如 `"114043"`
2. **可选字段**：`tmdb_id` 是可选的，没有设置时会自动降级到名称匹配
3. **唯一性**：同一个 TMDB ID 在 `115_resource` 表中可以有多个资源（不同版本、不同清晰度等）
4. **索引优化**：已为 `tmdb_id` 字段添加索引，查询性能高

---

**实施日期**: 2026-02-03
**功能状态**: ✅ 已完成
