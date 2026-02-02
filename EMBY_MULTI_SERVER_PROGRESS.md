# 多 Emby 服务器支持 - 实施进度

## 已完成

### 1. 数据库迁移脚本 ✅
- 文件：`database/migration_add_emby_config_id.sql`
- 内容：为所有表添加 `emby_config_id` 字段，修改主键为联合主键

### 2. 实体类修改 ✅
- `EmbyLibraryCache.java` - 添加 `embyConfigId` 字段
- `EmbyItemCache.java` - 添加 `embyConfigId` 字段
- `EmbyGenreCache.java` - 统一字段名为 `embyConfigId`
- `EmbyTagCache.java` - 统一字段名为 `embyConfigId`
- `EmbyStudioCache.java` - 统一字段名为 `embyConfigId`

### 3. 配置管理服务 ✅
- `IEmbyConfigService.java` - 接口已存在
- `EmbyConfigServiceImpl.java` - 实现已存在，包含 `getDefaultConfig()` 方法

### 4. 服务层修改 ✅

已完成 `EmbyCacheServiceImpl.java` 的所有修改：

#### 4.1 添加依赖注入 ✅

```java
@Autowired
private IEmbyConfigService embyConfigService;
```

#### 4.2 添加获取当前配置ID的方法 ✅

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

#### 4.3 修改查询方法 ✅

所有查询方法都已添加 `emby_config_id` 条件：

- ✅ `getAllLibraries()` - 添加 `configId` 过滤
- ✅ `getLibraryItemsPaged()` - 添加 `configId` 过滤
- ✅ `getItemDetail()` - 使用 wrapper 查询联合主键
- ✅ `searchItems()` - 添加 `configId` 过滤
- ✅ `getAllGenres()` - 添加 `configId` 过滤
- ✅ `getAllTags()` - 添加 `configId` 过滤
- ✅ `getAllStudios()` - 添加 `configId` 过滤

#### 4.4 修改保存方法 ✅

所有保存方法都已设置 `embyConfigId`：

- ✅ `saveLibrariesToCache()` - 设置 `configId`，使用 wrapper 查询
- ✅ `saveItemToCache()` - 设置 `configId`，使用 wrapper 查询
- ✅ `saveGenresToCache()` - 设置 `configId`，使用 wrapper 查询
- ✅ `saveTagsToCache()` - 设置 `configId`，使用 wrapper 查询
- ✅ `saveStudiosToCache()` - 设置 `configId`，使用 wrapper 查询

#### 4.5 修改清空缓存方法 ✅

```java
@Override
@Transactional(rollbackFor = Exception.class)
public boolean clearAllCache() {
    try {
        Long configId = getCurrentEmbyConfigId();
        log.info("清空配置 {} 的所有缓存", configId);

        // 只删除当前配置的数据
        LambdaQueryWrapper<EmbyLibraryCache> libraryWrapper = new LambdaQueryWrapper<>();
        libraryWrapper.eq(EmbyLibraryCache::getEmbyConfigId, configId);
        libraryCacheMapper.delete(libraryWrapper);

        // ... 其他表同理
    }
}
```

#### 4.6 修改检查缓存方法 ✅

```java
@Override
public boolean hasCacheData() {
    Long configId = getCurrentEmbyConfigId();

    LambdaQueryWrapper<EmbyLibraryCache> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(EmbyLibraryCache::getEmbyConfigId, configId);

    Long count = libraryCacheMapper.selectCount(wrapper);
    return count != null && count > 0;
}
```

#### 4.7 修改单个媒体库同步方法 ✅

```java
@Override
@Transactional(rollbackFor = Exception.class)
public boolean syncLibrary(String libraryId) {
    try {
        Long configId = getCurrentEmbyConfigId();
        log.info("开始全量同步单个媒体库: {} (configId={})", libraryId, configId);

        // 先删除该媒体库的旧数据（只删除当前配置的）
        LambdaQueryWrapper<EmbyItemCache> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmbyItemCache::getEmbyConfigId, configId)
                .eq(EmbyItemCache::getParentId, libraryId);
        itemCacheMapper.delete(wrapper);
        // ...
    }
}
```

## 进行中

## 待完成

### 5. 数据库迁移执行 ⏳

需要执行数据库迁移脚本：

```bash
mysql -u root -p gd_upload_manager < database/migration_add_emby_config_id.sql
```

**注意事项**：
- 执行前请备份数据库
- 脚本会修改主键结构，需要一定时间
- 执行后需要重新同步数据

### 6. Controller 修改（可选）⏳

**简单方案**（当前实现）：不修改 Controller，始终使用默认配置

**完整方案**（未来扩展）：修改 Controller，支持传入 `configId` 参数
- 添加 `@RequestParam(required = false) Long configId` 参数
- 将 `configId` 传递给 Service 层
- Service 层优先使用传入的 `configId`，否则使用默认配置

### 7. 前端修改（可选）⏳

添加配置选择器，让用户可以切换不同的 Emby 服务器：
- 在 `EmbyManager.vue` 添加配置下拉选择器
- 调用 `/emby/config/list` 获取所有配置
- 将选中的 `configId` 传递给后端 API

## 实施建议

### 当前状态：代码修改完成 ✅

所有代码修改已完成，现在可以：

1. **执行数据库迁移**
   ```bash
   mysql -u root -p gd_upload_manager < database/migration_add_emby_config_id.sql
   ```

2. **编译项目**
   ```bash
   cd backend
   mvn clean compile
   ```

3. **测试单配置场景**
   - 启动后端服务
   - 确保 `emby_config` 表中有一条 `is_default=1` 的配置
   - 测试同步、查询、清空等功能
   - 验证数据是否正确关联到 `emby_config_id`

4. **（可选）添加多配置支持**
   - 修改 Controller 支持 `configId` 参数
   - 修改前端添加配置选择器
   - 测试多配置切换功能

### 关键变更点

1. **联合主键查询**
   - 所有查询都使用 `LambdaQueryWrapper` 添加 `emby_config_id` 条件
   - `selectById()` 改为 `selectOne(wrapper)`

2. **数据隔离**
   - 每个配置的数据完全隔离
   - 清空缓存只清空当前配置的数据
   - 同步数据只同步到当前配置

3. **默认配置**
   - 使用 `embyConfigService.getDefaultConfig()` 获取当前配置
   - 如果没有默认配置，抛出异常提示用户配置

---

**当前状态**: ✅ 代码修改完成
**下一步**: 执行数据库迁移脚本并测试
