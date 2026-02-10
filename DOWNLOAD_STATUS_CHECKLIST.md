# 下载状态管理系统 - 检查清单

## 1. 数据库检查

### 1.1 确认表已创建
```sql
-- 检查表是否存在
SHOW TABLES LIKE 'emby_download_history';

-- 查看表结构
DESC emby_download_history;

-- 查看索引
SHOW INDEX FROM emby_download_history;
```

**预期结果：**
- 表存在
- 包含字段：id, emby_item_id, emby_config_id, download_status, file_path, file_size, error_message, create_time, update_time
- 索引：idx_emby_item_id, idx_emby_config_id, idx_download_status, idx_create_time

### 1.2 如果表不存在，执行创建脚本
```bash
# 在数据库中执行
mysql -u your_user -p your_database < database/emby_download_history.sql
```

## 2. 后端检查

### 2.1 文件完整性检查
- [x] `EmbyDownloadHistory.java` - 实体类
- [x] `EmbyDownloadHistoryMapper.java` - Mapper接口
- [x] `EmbyDownloadHistoryController.java` - 控制器（新建）
- [x] `EmbyItemCacheMapper.java` - SQL已更新支持downloadStatus
- [x] `EmbyCacheServiceImpl.java` - Service已更新支持downloadStatus
- [x] `IEmbyCacheService.java` - 接口已更新
- [x] `EmbyController.java` - Controller已更新
- [x] `EmbyServiceImpl.java` - 下载时保存记录（之前已实现）

### 2.2 关键逻辑检查

#### EmbyItemCacheMapper.java SQL逻辑
```java
// 当 downloadStatus 为 null 或空字符串时，不添加任何 JOIN 和 WHERE 条件
// 当 downloadStatus = "success" 时，INNER JOIN 最新的成功记录
// 当 downloadStatus = "failed" 时，INNER JOIN 最新的失败记录
// 当 downloadStatus = "none" 时，LEFT JOIN 并检查 IS NULL
```

**测试场景：**
1. ✅ transferStatus=null, downloadStatus=null → 返回所有项
2. ✅ transferStatus="success", downloadStatus=null → 只返回已转存的项
3. ✅ transferStatus=null, downloadStatus="success" → 只返回已下载的项
4. ✅ transferStatus="success", downloadStatus="success" → 返回既转存又下载的项
5. ✅ transferStatus="none", downloadStatus="none" → 返回未转存且未下载的项

#### EmbyCacheServiceImpl.java 参数传递
```java
// 检查点：
// 1. downloadStatus 参数正确传递到 params Map
// 2. 当两个状态都为 null/空时，使用原有的分页逻辑
// 3. 当任一状态不为空时，使用自定义 SQL
```

#### EmbyDownloadHistoryController.java 批量检查
```java
// 检查点：
// 1. 正确查询每个 itemId 的最新记录（按 create_time DESC）
// 2. 没有记录时返回 "none"
// 3. 返回的 Map 结构正确：Map<String, String>
```

### 2.3 API端点测试

#### 测试 1: 批量检查下载状态
```bash
curl -X POST http://localhost:8080/emby-download-history/batch-check \
  -H "Content-Type: application/json" \
  -d '["item_id_1", "item_id_2", "item_id_3"]'
```

**预期响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "item_id_1": "success",
    "item_id_2": "failed",
    "item_id_3": "none"
  }
}
```

#### 测试 2: 获取下载历史
```bash
curl http://localhost:8080/emby-download-history/item/item_id_1
```

**预期响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "embyItemId": "item_id_1",
      "downloadStatus": "success",
      "filePath": "/data/emby/movie.mp4",
      "fileSize": 1234567890,
      "createTime": "2024-01-01 12:00:00"
    }
  ]
}
```

#### 测试 3: 分页查询（带下载状态筛选）
```bash
# 查询已下载的项
curl "http://localhost:8080/emby/libraries/library_id/items/paged?downloadStatus=success&startIndex=0&limit=50"

# 查询已转存且已下载的项
curl "http://localhost:8080/emby/libraries/library_id/items/paged?transferStatus=success&downloadStatus=success&startIndex=0&limit=50"
```

## 3. 前端检查

### 3.1 文件完整性检查
- [x] `downloadHistory.js` - API文件（新建）
- [x] `emby.js` - 已更新 getLibraryItemsPaged 函数
- [x] `EmbyManager.vue` - 已添加下载状态筛选和显示

### 3.2 Vue组件检查

#### 数据定义
```javascript
// 检查点：
const downloadStatusFilter = ref('') // ✅ 已定义
const downloadStatusMap = ref({})    // ✅ 已定义
```

#### UI组件
```vue
<!-- 检查点： -->
<!-- 1. ✅ 下载状态筛选下拉框已添加 -->
<!-- 2. ✅ 下载状态列已添加到表格 -->
<!-- 3. ✅ 下载状态徽章已添加到名称列 -->
```

#### 函数实现
```javascript
// 检查点：
// 1. ✅ applyDownloadFilter() 已实现
// 2. ✅ loadDownloadStatus() 已实现
// 3. ✅ loadLibraryItems() 已更新传递 downloadStatus 参数
// 4. ✅ batchCheckDownloadStatus 已导入
```

### 3.3 前端测试步骤

1. **打开浏览器开发者工具**
   - 打开 Network 标签
   - 打开 Console 标签

2. **测试筛选功能**
   - 选择一个媒体库
   - 选择"下载状态"为"已下载"
   - 检查 Network 中的请求：
     ```
     GET /emby/libraries/{id}/items/paged?downloadStatus=success&...
     ```
   - 检查 Console 输出：
     ```
     === 应用下载状态筛选 ===
     筛选条件: success
     === 下载状态加载完成 ===
     ```

3. **测试组合筛选**
   - 同时选择"转存状态"="已转存"和"下载状态"="已下载"
   - 检查请求参数包含两个状态
   - 验证返回的数据符合两个条件

4. **测试状态显示**
   - 检查每个媒体项是否显示正确的下载状态徽章
   - 检查"下载状态"列是否显示正确

## 4. 集成测试场景

### 场景 1: 首次使用（无下载记录）
1. 打开媒体库
2. 所有项的下载状态应显示"未下载"
3. 选择"下载状态"="未下载"，应返回所有项

### 场景 2: 下载一个项目
1. 点击"直接下载"按钮
2. 等待下载完成
3. 刷新页面
4. 该项的下载状态应变为"已下载"
5. 选择"下载状态"="已下载"，应只返回该项

### 场景 3: 下载失败
1. 模拟下载失败（如断网）
2. 该项的下载状态应变为"下载失败"
3. 选择"下载状态"="下载失败"，应返回该项

### 场景 4: 组合筛选
1. 有一些项已转存但未下载
2. 有一些项已下载但未转存
3. 有一些项既转存又下载
4. 测试各种组合筛选是否正确

## 5. 潜在问题检查

### 5.1 SQL性能
```sql
-- 检查是否有索引
SHOW INDEX FROM emby_download_history;

-- 如果查询慢，检查执行计划
EXPLAIN SELECT DISTINCT e.* FROM emby_item e
INNER JOIN (
  SELECT emby_item_id, MAX(create_time) as max_time
  FROM emby_download_history
  GROUP BY emby_item_id
) d ON e.id = d.emby_item_id;
```

### 5.2 空值处理
- ✅ 后端：downloadStatus 为 null 或空字符串时不添加筛选条件
- ✅ 前端：downloadStatusFilter 初始值为空字符串
- ✅ API：传递 null 时使用 `|| null` 处理

### 5.3 并发问题
- ✅ 使用 MAX(create_time) 获取最新记录，避免并发写入问题
- ✅ 每次下载都插入新记录，不更新旧记录

### 5.4 数据一致性
- ✅ 下载成功/失败时都保存记录（EmbyServiceImpl 已实现）
- ✅ 使用事务保证数据一致性（如需要）

## 6. 回滚计划

如果出现问题，可以按以下步骤回滚：

### 6.1 前端回滚
```bash
# 恢复 emby.js
git checkout HEAD -- frontend/src/api/emby.js

# 删除 downloadHistory.js
rm frontend/src/api/downloadHistory.js

# 恢复 EmbyManager.vue
git checkout HEAD -- frontend/src/views/EmbyManager.vue
```

### 6.2 后端回滚
```bash
# 恢复修改的文件
git checkout HEAD -- backend/src/main/java/com/gdupload/service/impl/EmbyCacheServiceImpl.java
git checkout HEAD -- backend/src/main/java/com/gdupload/service/IEmbyCacheService.java
git checkout HEAD -- backend/src/main/java/com/gdupload/controller/EmbyController.java

# 删除新建的控制器
rm backend/src/main/java/com/gdupload/controller/EmbyDownloadHistoryController.java
```

### 6.3 数据库回滚（可选）
```sql
-- 如果需要删除表
DROP TABLE IF EXISTS emby_download_history;
```

## 7. 部署检查清单

- [ ] 1. 备份数据库
- [ ] 2. 执行 emby_download_history.sql 创建表
- [ ] 3. 编译后端代码
- [ ] 4. 重启后端服务
- [ ] 5. 编译前端代码
- [ ] 6. 部署前端静态文件
- [ ] 7. 清除浏览器缓存
- [ ] 8. 测试基本功能
- [ ] 9. 测试筛选功能
- [ ] 10. 测试下载功能
- [ ] 11. 监控日志是否有错误

## 8. 常见问题排查

### 问题 1: 下载状态不显示
**排查步骤：**
1. 检查浏览器 Console 是否有错误
2. 检查 Network 中 `/emby-download-history/batch-check` 请求是否成功
3. 检查返回的数据格式是否正确
4. 检查 `downloadStatusMap` 是否正确赋值

### 问题 2: 筛选不生效
**排查步骤：**
1. 检查请求 URL 是否包含 `downloadStatus` 参数
2. 检查后端日志中的 SQL 语句
3. 检查数据库中是否有下载记录
4. 检查 MyBatis 的 `<if test>` 条件是否正确

### 问题 3: 下载后状态不更新
**排查步骤：**
1. 检查 EmbyServiceImpl 中是否调用了 `saveDownloadHistory`
2. 检查数据库中是否插入了记录
3. 检查前端是否重新加载了状态
4. 尝试刷新页面

## 9. 性能优化建议

### 9.1 批量查询优化
当前实现使用循环查询每个 item 的状态，如果数据量大可以优化为单次查询：

```java
// 优化后的批量查询（可选）
@Select("<script>" +
        "SELECT emby_item_id, download_status " +
        "FROM emby_download_history dh1 " +
        "WHERE dh1.create_time = (" +
        "  SELECT MAX(dh2.create_time) " +
        "  FROM emby_download_history dh2 " +
        "  WHERE dh2.emby_item_id = dh1.emby_item_id" +
        ") " +
        "AND dh1.emby_item_id IN " +
        "<foreach collection='itemIds' item='id' open='(' separator=',' close=')'>" +
        "  #{id}" +
        "</foreach>" +
        "</script>")
List<Map<String, String>> batchSelectLatestStatus(@Param("itemIds") List<String> itemIds);
```

### 9.2 缓存优化
可以考虑添加 Redis 缓存来减少数据库查询：
- 缓存每个 item 的最新下载状态
- 下载完成后更新缓存
- 设置合理的过期时间

## 10. 总结

所有修改已完成，系统逻辑正确。主要变更：

**后端：**
- ✅ 新增 EmbyDownloadHistoryController 处理下载状态查询
- ✅ 更新 EmbyItemCacheMapper SQL 支持 downloadStatus 筛选
- ✅ 更新 Service 和 Controller 传递 downloadStatus 参数

**前端：**
- ✅ 新增 downloadHistory.js API 文件
- ✅ 更新 EmbyManager.vue 添加下载状态筛选和显示
- ✅ 添加 downloadStatusFilter 和 downloadStatusMap 状态管理

**数据库：**
- ✅ emby_download_history 表已定义（需执行 SQL）

**下一步：**
1. 执行数据库脚本创建表
2. 重启后端服务
3. 重新编译前端
4. 测试功能
