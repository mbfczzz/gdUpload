# 支持多个 Emby 服务器的实现方案

## 需求

支持配置和管理多个 Emby 服务器，每个服务器的数据独立存储和查询。

## 设计方案

### 1. 数据库设计

#### 核心思路
- 使用 `emby_config_id` 字段关联 `emby_config` 表
- 修改主键为联合主键 `(id, emby_config_id)`
- 所有查询都需要带上 `emby_config_id` 条件

#### 表结构修改

所有 Emby 相关表都添加 `emby_config_id` 字段：

1. **emby_library** - 媒体库表
2. **emby_item** - 媒体项表
3. **emby_genre** - 类型表
4. **emby_tag** - 标签表
5. **emby_studio** - 工作室表

#### 主键修改

```sql
-- 原主键
PRIMARY KEY (`id`)

-- 新主键（联合主键）
PRIMARY KEY (`id`, `emby_config_id`)
```

**优势**：
- 不同 Emby 服务器可以有相同的 ID
- 例如：服务器A 和服务器B 都可以有 ID 为 "258" 的媒体库
- 通过 `emby_config_id` 区分

### 2. 实体类修改

所有实体类添加 `embyConfigId` 字段：

```java
@Data
@TableName("emby_library")
public class EmbyLibraryCache {

    @TableId
    private String id;

    /**
     * Emby配置ID（关联 emby_config 表）
     */
    private Long embyConfigId;

    // ... 其他字段
}
```

**注意**：MyBatis Plus 的联合主键需要特殊处理。

### 3. 服务层修改

#### 3.1 获取当前配置ID

添加方法获取当前使用的 Emby 配置ID：

```java
@Service
public class EmbyCacheServiceImpl implements IEmbyCacheService {

    @Autowired
    private IEmbyConfigService embyConfigService;

    /**
     * 获取当前使用的 Emby 配置ID
     */
    private Long getCurrentEmbyConfigId() {
        // 获取默认配置或当前选中的配置
        EmbyConfig config = embyConfigService.getDefaultConfig();
        if (config == null) {
            throw new BusinessException("未找到可用的 Emby 配置，请先配置 Emby 服务器");
        }
        return config.getId();
    }
}
```

#### 3.2 修改所有查询方法

所有查询都需要添加 `emby_config_id` 条件：

```java
@Override
public List<EmbyLibrary> getAllLibraries(boolean forceRefresh) {
    Long configId = getCurrentEmbyConfigId();

    log.info("从数据库获取媒体库列表 (configId={})", configId);

    LambdaQueryWrapper<EmbyLibraryCache> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(EmbyLibraryCache::getEmbyConfigId, configId);

    List<EmbyLibraryCache> cacheList = libraryCacheMapper.selectList(wrapper);

    if (cacheList.isEmpty()) {
        throw new BusinessException("数据库中没有媒体库数据，请先点击\"同步所有数据\"按钮进行同步");
    }

    return cacheList.stream().map(this::convertToLibrary).collect(Collectors.toList());
}
```

#### 3.3 修改保存方法

保存时设置 `embyConfigId`：

```java
private void saveLibrariesToCache(List<EmbyLibrary> libraries) {
    Long configId = getCurrentEmbyConfigId();
    LocalDateTime now = LocalDateTime.now();

    for (EmbyLibrary library : libraries) {
        EmbyLibraryCache cache = new EmbyLibraryCache();
        cache.setId(library.getId());
        cache.setEmbyConfigId(configId);  // 设置配置ID
        cache.setName(library.getName());
        // ... 其他字段

        // 使用 insertOrUpdate
        libraryCacheMapper.insert(cache);
    }
}
```

#### 3.4 修改清空缓存方法

清空时只清空当前配置的数据：

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

        LambdaQueryWrapper<EmbyItemCache> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(EmbyItemCache::getEmbyConfigId, configId);
        itemCacheMapper.delete(itemWrapper);

        // ... 其他表

        return true;
    } catch (Exception e) {
        log.error("清空缓存失败", e);
        return false;
    }
}
```

### 4. 前端修改

#### 4.1 配置选择器

添加 Emby 配置选择器，让用户可以切换不同的 Emby 服务器：

```vue
<template>
  <div class="emby-container">
    <el-card class="header-card">
      <div class="header-content">
        <div class="header-left">
          <h2>Emby 媒体库管理</h2>
          <!-- 配置选择器 -->
          <el-select
            v-model="currentConfigId"
            placeholder="选择 Emby 服务器"
            @change="handleConfigChange"
            style="margin-left: 20px; width: 200px;"
          >
            <el-option
              v-for="config in embyConfigs"
              :key="config.id"
              :label="config.configName"
              :value="config.id"
            />
          </el-select>
        </div>
        <!-- ... -->
      </div>
    </el-card>
  </div>
</template>

<script setup>
const currentConfigId = ref(null)
const embyConfigs = ref([])

// 加载配置列表
const loadEmbyConfigs = async () => {
  const res = await getEmbyConfigs()
  embyConfigs.value = res.data
  if (embyConfigs.value.length > 0) {
    // 选择默认配置
    const defaultConfig = embyConfigs.value.find(c => c.isDefault)
    currentConfigId.value = defaultConfig?.id || embyConfigs.value[0].id
  }
}

// 切换配置
const handleConfigChange = async (configId) => {
  // 重新加载数据
  await loadLibraries()
  await loadGenres()
  await loadTags()
  await loadStudios()
}
</script>
```

#### 4.2 API 修改

所有 API 请求都需要带上 `configId` 参数（可选，后端会使用默认配置）：

```javascript
export function getAllLibraries(configId) {
  return request({
    url: '/emby/libraries',
    method: 'get',
    params: { configId }
  })
}
```

### 5. 配置管理

#### 5.1 EmbyConfigService

添加获取默认配置的方法：

```java
public interface IEmbyConfigService {

    /**
     * 获取默认配置
     */
    EmbyConfig getDefaultConfig();

    /**
     * 根据ID获取配置
     */
    EmbyConfig getById(Long id);

    /**
     * 获取所有启用的配置
     */
    List<EmbyConfig> getAllEnabled();
}
```

#### 5.2 实现

```java
@Service
public class EmbyConfigServiceImpl implements IEmbyConfigService {

    @Autowired
    private EmbyConfigMapper embyConfigMapper;

    @Override
    public EmbyConfig getDefaultConfig() {
        LambdaQueryWrapper<EmbyConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmbyConfig::getEnabled, true)
               .eq(EmbyConfig::getIsDefault, true)
               .last("LIMIT 1");

        EmbyConfig config = embyConfigMapper.selectOne(wrapper);

        // 如果没有默认配置，返回第一个启用的配置
        if (config == null) {
            wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(EmbyConfig::getEnabled, true)
                   .last("LIMIT 1");
            config = embyConfigMapper.selectOne(wrapper);
        }

        return config;
    }
}
```

### 6. 数据迁移

#### 6.1 执行迁移脚本

```bash
mysql -u root -p gd_upload_manager < database/migration_add_emby_config_id.sql
```

#### 6.2 迁移现有数据

如果数据库中已有数据，需要为现有数据设置 `emby_config_id`：

```sql
-- 假设默认配置的 ID 为 1
UPDATE emby_library SET emby_config_id = 1 WHERE emby_config_id IS NULL;
UPDATE emby_item SET emby_config_id = 1 WHERE emby_config_id IS NULL;
UPDATE emby_genre SET emby_config_id = 1 WHERE emby_config_id IS NULL;
UPDATE emby_tag SET emby_config_id = 1 WHERE emby_config_id IS NULL;
UPDATE emby_studio SET emby_config_id = 1 WHERE emby_config_id IS NULL;
```

### 7. 使用场景

#### 场景1：单个 Emby 服务器

- 配置一个 Emby 服务器，设置为默认
- 所有数据都关联到这个配置
- 用户无需选择配置

#### 场景2：多个 Emby 服务器

- 配置多个 Emby 服务器（如家庭服务器、公司服务器）
- 用户可以通过下拉框切换服务器
- 每个服务器的数据独立存储和查询

#### 场景3：数据隔离

- 不同配置的数据完全隔离
- 同步时只同步当前配置的数据
- 清空缓存时只清空当前配置的数据

### 8. 优势

✅ **数据隔离** - 不同 Emby 服务器的数据完全独立
✅ **灵活切换** - 用户可以轻松切换不同的服务器
✅ **扩展性好** - 可以无限添加 Emby 服务器
✅ **向后兼容** - 单服务器场景下使用默认配置，无需改变使用方式

### 9. 注意事项

⚠️ **联合主键** - MyBatis Plus 对联合主键的支持有限，需要特殊处理
⚠️ **数据迁移** - 现有数据需要设置 `emby_config_id`
⚠️ **查询性能** - 所有查询都需要带上 `emby_config_id` 条件
⚠️ **前端兼容** - 前端需要添加配置选择功能

### 10. 实施步骤

1. ✅ 创建数据库迁移脚本
2. ⏳ 执行迁移脚本，添加字段和索引
3. ⏳ 修改实体类，添加 `embyConfigId` 字段
4. ⏳ 修改服务层，所有查询和保存都带上 `embyConfigId`
5. ⏳ 添加配置管理服务
6. ⏳ 修改前端，添加配置选择器
7. ⏳ 测试多配置场景
8. ⏳ 迁移现有数据

---

**文档创建时间**: 2026-02-02
**状态**: 设计完成，待实施
