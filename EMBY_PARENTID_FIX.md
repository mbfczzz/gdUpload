# Emby ParentId 问题修复

## 问题描述

用户点击"查看媒体项"按钮后，提示"数据库中没有媒体库 258 的数据"，即使已经执行了同步操作。

## 根本原因

**Emby API 返回的 `ParentId` 不是媒体库的 ID，而是父文件夹的 ID**。

### 示例

当查询媒体库 ID 为 `258` 的媒体项时：

```
GET /Users/{userId}/Items?ParentId=258&Recursive=true
```

返回的媒体项数据：
```json
{
  "Id": "12345",
  "Name": "电影名称",
  "ParentId": "67890",  // ← 这不是 258，而是父文件夹的 ID
  "Type": "Movie"
}
```

### 导致的问题

1. 同步时，媒体项的 `parentId` 被保存为 `67890`
2. 查询时，使用 `WHERE parent_id = '258'` 查询
3. 查询结果为空，因为数据库中的 `parent_id` 是 `67890`

## 解决方案

在保存媒体项到数据库时，**强制设置 `parentId` 为媒体库 ID**，而不是使用 Emby API 返回的 `ParentId`。

### 修改代码

```java
private void saveItemsToCache(List<EmbyItem> items, String libraryId) {
    LocalDateTime now = LocalDateTime.now();

    for (EmbyItem item : items) {
        // 强制设置 parentId 为媒体库 ID，确保查询时能找到
        item.setParentId(libraryId);
        saveItemToCache(item);
    }
}
```

## 修复效果

### 修复前

```
同步时保存：
  item_id: 12345
  parent_id: 67890  ← Emby API 返回的值

查询时：
  SELECT * FROM emby_item_cache WHERE parent_id = '258'
  结果：0 条记录 ✗
```

### 修复后

```
同步时保存：
  item_id: 12345
  parent_id: 258  ← 强制设置为媒体库 ID

查询时：
  SELECT * FROM emby_item_cache WHERE parent_id = '258'
  结果：找到所有媒体项 ✓
```

## 数据流向

```
Emby API
  ↓ 返回媒体项（ParentId = 父文件夹ID）
后端同步逻辑
  ↓ 强制设置 parentId = 媒体库ID
数据库
  ↓ 保存（parent_id = 媒体库ID）
查询逻辑
  ↓ WHERE parent_id = 媒体库ID
返回结果 ✓
```

## 测试步骤

1. **清空数据库**
   ```sql
   DELETE FROM emby_item_cache;
   ```

2. **重新同步**
   - 点击"同步所有数据"按钮
   - 等待同步完成

3. **验证数据**
   ```sql
   -- 检查媒体项的 parent_id 是否为媒体库 ID
   SELECT id, name, parent_id FROM emby_item_cache LIMIT 10;
   ```

4. **测试查询**
   - 点击"查看媒体项"按钮
   - 应该能看到媒体项列表 ✓

## 注意事项

### 1. 需要重新同步

修复代码后，**必须重新同步数据**，因为数据库中已有的数据 `parent_id` 还是错误的。

### 2. 原始 ParentId 丢失

强制设置 `parentId` 后，Emby API 返回的原始 `ParentId`（父文件夹 ID）会丢失。

**影响**：
- 如果需要知道媒体项的父文件夹，无法从数据库获取
- 但对于当前需求（按媒体库查询媒体项）没有影响

**解决方法**（如果需要）：
- 添加一个新字段 `original_parent_id` 保存原始值
- `parent_id` 保存媒体库 ID 用于查询

### 3. Recursive 参数

查询时使用了 `Recursive=true`，表示递归获取所有子项，包括子文件夹中的媒体项。

这就是为什么 Emby API 返回的 `ParentId` 不是媒体库 ID 的原因。

## 相关代码位置

### 修改的文件
- `EmbyCacheServiceImpl.java` - `saveItemsToCache()` 方法

### 相关方法
- `syncLibraryItemsAll()` - 同步媒体库的所有媒体项
- `getLibraryItemsPaged()` - 查询媒体库的媒体项（分页）

## 总结

通过强制设置 `parentId` 为媒体库 ID，解决了查询时找不到数据的问题。

✅ **修复前**：查询失败，提示"数据库中没有数据"
✅ **修复后**：查询成功，显示所有媒体项

**重要**：修复后需要重新同步数据！

---

**修复完成时间**: 2026-02-02
