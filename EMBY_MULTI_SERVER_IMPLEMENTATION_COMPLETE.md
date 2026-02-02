# 多 Emby 服务器支持 - 实施完成报告

## 概述

已完成多 Emby 服务器支持的代码实施，使用 `emby_config_id` 字段实现数据隔离。

## 已完成的工作

### 1. 数据库迁移脚本 ✅

**文件**: `database/migration_add_emby_config_id.sql`

为以下表添加 `emby_config_id` 字段并修改主键：
- `emby_library` - 媒体库缓存表
- `emby_item` - 媒体项缓存表
- `emby_genre` - 类型缓存表
- `emby_tag` - 标签缓存表
- `emby_studio` - 工作室缓存表

主键结构：从 `(id)` 改为 `(id, emby_config_id)`

### 2. 实体类修改 ✅

所有实体类已添加 `embyConfigId` 字段：

- `EmbyLibraryCache.java` - backend/src/main/java/com/gdupload/entity/EmbyLibraryCache.java:23
- `EmbyItemCache.java` - backend/src/main/java/com/gdupload/entity/EmbyItemCache.java:23
- `EmbyGenreCache.java` - backend/src/main/java/com/gdupload/entity/EmbyGenreCache.java:36
- `EmbyTagCache.java` - backend/src/main/java/com/gdupload/entity/EmbyTagCache.java:36
- `EmbyStudioCache.java` - backend/src/main/java/com/gdupload/entity/EmbyStudioCache.java:36

### 3. 服务层修改 ✅

**文件**: `backend/src/main/java/com/gdupload/service/impl/EmbyCacheServiceImpl.java`

#### 3.1 添加依赖注入

```java
@Autowired
private IEmbyConfigService embyConfigService;
```

#### 3.2 添加配置ID获取方法

```java
/**
 * 获取当前使用的 Emby 配置ID
 * 使用默认配置（简单方案）
 */
private Long getCurrentEmbyConfigId() {
    EmbyConfig config = embyConfigService.getDefaultConfig();
    if (config == null) {
        throw new BusinessException("未找到默认的 Emby 配置，请先配置 Emby 服务器");
    }
    return config.getId();
}
```

#### 3.3 修改的查询方法

所有查询方法都已添加 `emby_config_id` 过滤条件：

1. **getAllLibraries()** - EmbyCacheServiceImpl.java:78
   - 添加 `wrapper.eq(EmbyLibraryCache::getEmbyConfigId, configId)`

2. **getLibraryItemsPaged()** - EmbyCacheServiceImpl.java:92
   - 添加 `countWrapper.eq(EmbyItemCache::getEmbyConfigId, configId)`
   - 添加 `wrapper.eq(EmbyItemCache::getEmbyConfigId, configId)`

3. **getItemDetail()** - EmbyCacheServiceImpl.java:127
   - 使用 wrapper 查询联合主键
   - `wrapper.eq(EmbyItemCache::getId, itemId).eq(EmbyItemCache::getEmbyConfigId, configId)`

4. **searchItems()** - EmbyCacheServiceImpl.java:141
   - 添加 `countWrapper.eq(EmbyItemCache::getEmbyConfigId, configId)`
   - 添加 `wrapper.eq(EmbyItemCache::getEmbyConfigId, configId)`

5. **getAllGenres()** - EmbyCacheServiceImpl.java:517
   - 添加 `wrapper.eq(EmbyGenreCache::getEmbyConfigId, configId)`

6. **getAllTags()** - EmbyCacheServiceImpl.java:531
   - 添加 `wrapper.eq(EmbyTagCache::getEmbyConfigId, configId)`

7. **getAllStudios()** - EmbyCacheServiceImpl.java:545
   - 添加 `wrapper.eq(EmbyStudioCache::getEmbyConfigId, configId)`

#### 3.4 修改的保存方法

所有保存方法都已设置 `embyConfigId` 字段：

1. **saveLibrariesToCache()** - EmbyCacheServiceImpl.java:379
   - 设置 `cache.setEmbyConfigId(configId)`
   - 使用 wrapper 查询是否存在（联合主键）

2. **saveItemToCache()** - EmbyCacheServiceImpl.java:418
   - 设置 `cache.setEmbyConfigId(configId)`
   - 使用 wrapper 查询是否存在（联合主键）

3. **saveGenresToCache()** - EmbyCacheServiceImpl.java:561
   - 设置 `cache.setEmbyConfigId(configId)`
   - 使用 wrapper 查询是否存在（联合主键）

4. **saveTagsToCache()** - EmbyCacheServiceImpl.java:585
   - 设置 `cache.setEmbyConfigId(configId)`
   - 使用 wrapper 查询是否存在（联合主键）

5. **saveStudiosToCache()** - EmbyCacheServiceImpl.java:609
   - 设置 `cache.setEmbyConfigId(configId)`
   - 使用 wrapper 查询是否存在（联合主键）

#### 3.5 修改的其他方法

1. **syncLibrary()** - EmbyCacheServiceImpl.java:279
   - 删除旧数据时添加 `configId` 过滤

2. **clearAllCache()** - EmbyCacheServiceImpl.java:302
   - 只删除当前配置的数据
   - 所有表都使用 wrapper 添加 `configId` 过滤

3. **hasCacheData()** - EmbyCacheServiceImpl.java:318
   - 添加 `wrapper.eq(EmbyLibraryCache::getEmbyConfigId, configId)`

## 技术要点

### 1. 联合主键处理

由于使用了联合主键 `(id, emby_config_id)`，MyBatis Plus 的 `selectById()` 方法不再适用，所有查询都改为使用 `LambdaQueryWrapper`：

```java
// 旧代码
EmbyItemCache cache = itemCacheMapper.selectById(itemId);

// 新代码
LambdaQueryWrapper<EmbyItemCache> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(EmbyItemCache::getId, itemId)
       .eq(EmbyItemCache::getEmbyConfigId, configId);
EmbyItemCache cache = itemCacheMapper.selectOne(wrapper);
```

### 2. 数据隔离

每个 Emby 配置的数据完全隔离：
- 查询时只返回当前配置的数据
- 保存时自动关联到当前配置
- 清空缓存只清空当前配置的数据
- 同步数据只同步到当前配置

### 3. 默认配置机制

使用 `embyConfigService.getDefaultConfig()` 获取当前配置：
- 从 `emby_config` 表查询 `is_default=1` 的配置
- 如果没有默认配置，抛出异常提示用户

## 下一步操作

### 1. 执行数据库迁移 ⚠️

**重要**：执行前请备份数据库！

```bash
mysql -u root -p gd_upload_manager < database/migration_add_emby_config_id.sql
```

迁移脚本会：
1. 为所有表添加 `emby_config_id` 字段
2. 修改主键为联合主键 `(id, emby_config_id)`
3. 为现有数据设置 `emby_config_id = 1`（假设第一个配置的ID为1）

### 2. 编译项目

```bash
cd backend
mvn clean compile
```

或使用提供的脚本：
```bash
check_compile.bat
```

### 3. 测试单配置场景

1. 启动后端服务
2. 确保 `emby_config` 表中有一条 `is_default=1` 的配置
3. 测试以下功能：
   - 同步所有数据
   - 查看媒体库列表
   - 查看媒体项
   - 搜索媒体项
   - 清空缓存

### 4. 验证数据隔离

如果有多个 Emby 配置：
1. 切换默认配置（修改 `is_default` 字段）
2. 重新同步数据
3. 验证不同配置的数据是否隔离

## 可选扩展

### 方案A：前端配置选择器（推荐）

在前端添加配置选择器，让用户可以切换不同的 Emby 服务器：

1. 修改 `EmbyManager.vue`：
   ```vue
   <el-select v-model="currentConfigId" @change="onConfigChange">
     <el-option v-for="config in configList" :key="config.id"
                :label="config.name" :value="config.id" />
   </el-select>
   ```

2. 修改 Controller 接受 `configId` 参数：
   ```java
   @GetMapping("/libraries")
   public Result<List<EmbyLibrary>> getAllLibraries(
       @RequestParam(required = false) Long configId) {
       // 传递给 Service 层
   }
   ```

3. 修改 Service 层支持传入 `configId`：
   ```java
   private Long getCurrentEmbyConfigId(Long configId) {
       if (configId != null) {
           return configId;
       }
       return embyConfigService.getDefaultConfig().getId();
   }
   ```

### 方案B：Session 存储（备选）

将当前配置ID存储在 Session 中：
1. 用户登录后选择配置
2. 将 `configId` 存入 Session
3. Service 层从 Session 获取 `configId`

## 注意事项

1. **数据迁移不可逆**：执行迁移脚本后，主键结构会改变，请务必备份数据库

2. **现有数据处理**：迁移脚本会将现有数据的 `emby_config_id` 设置为 1，请确保 ID 为 1 的配置存在

3. **性能影响**：联合主键可能对查询性能有轻微影响，但数据量不大时影响可忽略

4. **兼容性**：修改后的代码向后兼容，单配置场景下使用默认配置即可

## 文件清单

### 修改的文件

1. `backend/src/main/java/com/gdupload/service/impl/EmbyCacheServiceImpl.java` - 服务层实现
2. `backend/src/main/java/com/gdupload/entity/EmbyLibraryCache.java` - 媒体库实体
3. `backend/src/main/java/com/gdupload/entity/EmbyItemCache.java` - 媒体项实体
4. `backend/src/main/java/com/gdupload/entity/EmbyGenreCache.java` - 类型实体
5. `backend/src/main/java/com/gdupload/entity/EmbyTagCache.java` - 标签实体
6. `backend/src/main/java/com/gdupload/entity/EmbyStudioCache.java` - 工作室实体

### 新增的文件

1. `database/migration_add_emby_config_id.sql` - 数据库迁移脚本
2. `EMBY_MULTI_SERVER_DESIGN.md` - 设计文档
3. `EMBY_MULTI_SERVER_PROGRESS.md` - 进度跟踪
4. `EMBY_MULTI_SERVER_IMPLEMENTATION_COMPLETE.md` - 本文档

## 总结

多 Emby 服务器支持的代码实施已全部完成，主要变更包括：

- ✅ 数据库迁移脚本
- ✅ 5 个实体类添加 `embyConfigId` 字段
- ✅ 服务层 15+ 个方法修改
- ✅ 数据隔离机制实现
- ✅ 默认配置机制实现

下一步需要执行数据库迁移并测试功能。

---

**实施日期**: 2026-02-02
**实施方案**: 方案A（完整实现）
**状态**: ✅ 代码完成，待测试
