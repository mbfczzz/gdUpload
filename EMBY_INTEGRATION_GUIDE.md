# Emby 集成功能使用文档

## 功能概述

本系统已集成 Emby API，可以读取 Emby 服务器的全部目录、分类和媒体信息，支持批量操作和同步。

## 配置步骤

### 1. 获取 Emby API Key

1. 登录你的 Emby 服务器 Web 界面（通常是 `http://your-server:8096`）
2. 进入 **设置 (Settings)** → **高级 (Advanced)** → **API 密钥 (API Keys)**
3. 点击 **新建 API 密钥 (New API Key)**
4. 输入应用名称（如 "GD Upload Manager"）
5. 复制生成的 API Key

### 2. 配置 application.yml

编辑 `backend/src/main/resources/application.yml`，找到 `app.emby` 配置节：

```yaml
app:
  emby:
    server-url: http://your-emby-server:8096  # 你的Emby服务器地址
    api-key: your-api-key-here                # 刚才获取的API Key
    user-id: your-user-id                     # 可选，留空会自动获取第一个用户
    timeout: 30000                            # 请求超时时间（毫秒）
    enabled: true                             # 是否启用Emby集成
```

### 3. 重启后端服务

```bash
cd backend
mvn clean package
java -jar target/gd-upload-manager-1.0.0.jar
```

### 4. 访问前端页面

启动前端后，访问 `http://localhost:5173/emby` 即可使用 Emby 管理功能。

## 功能说明

### 1. 测试连接

点击 **测试连接** 按钮，验证 Emby 服务器配置是否正确。

### 2. 查看服务器信息

成功连接后，会显示 Emby 服务器的基本信息：
- 服务器名称
- 版本号
- 操作系统
- 本地地址和外网地址

### 3. 媒体库管理

**查看所有媒体库：**
- 显示所有媒体库的名称、类型、路径
- 支持查看每个媒体库的媒体项数量

**查看媒体项：**
- 点击 **查看媒体项** 按钮，查看该媒体库下的所有电影、电视剧等
- 支持搜索功能
- 显示媒体项的详细信息（名称、年份、评分、类型、文件大小、路径等）

**查看媒体项详情：**
- 点击 **详情** 按钮，查看单个媒体项的完整信息
- 包括简介、标签、工作室、演员等

### 4. 分类信息

系统会自动读取并显示：
- **类型（Genres）**：如动作、喜剧、科幻等
- **标签（Tags）**：自定义标签
- **工作室（Studios）**：制作公司

### 5. 同步所有数据

点击 **同步所有数据** 按钮，系统会：
1. 读取所有媒体库
2. 遍历每个媒体库获取所有媒体项
3. 统计总数并显示结果

## API 接口说明

### 后端 API

所有接口的基础路径为 `/api/emby`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/test` | GET | 测试连接 |
| `/server-info` | GET | 获取服务器信息 |
| `/libraries` | GET | 获取所有媒体库 |
| `/libraries/{libraryId}/items` | GET | 获取媒体库的媒体项 |
| `/items/{itemId}` | GET | 获取媒体项详情 |
| `/genres` | GET | 获取所有类型 |
| `/tags` | GET | 获取所有标签 |
| `/studios` | GET | 获取所有工作室 |
| `/search?keyword=xxx` | GET | 搜索媒体项 |
| `/sync` | POST | 同步所有媒体库数据 |

### 前端 API

前端 API 封装在 `frontend/src/api/emby.js`，可以直接导入使用：

```javascript
import {
  testEmbyConnection,
  getEmbyServerInfo,
  getAllLibraries,
  getLibraryItems,
  getItemDetail,
  getAllGenres,
  getAllTags,
  getAllStudios,
  searchItems,
  syncAllLibraries
} from '@/api/emby'
```

## 数据结构

### EmbyLibrary（媒体库）

```java
{
  "id": "媒体库ID",
  "name": "媒体库名称",
  "collectionType": "类型（movies/tvshows/music）",
  "locations": ["路径1", "路径2"],
  "itemCount": 100,
  "dateCreated": "创建时间",
  "dateModified": "修改时间"
}
```

### EmbyItem（媒体项）

```java
{
  "id": "媒体项ID",
  "name": "名称",
  "originalTitle": "原始名称",
  "type": "类型（Movie/Series/Episode）",
  "path": "文件路径",
  "size": 1234567890,
  "productionYear": 2024,
  "communityRating": 8.5,
  "overview": "简介",
  "genres": ["动作", "科幻"],
  "tags": ["标签1", "标签2"],
  "studios": ["工作室1"],
  "people": ["演员1", "演员2"],
  "mediaSources": [
    {
      "id": "源ID",
      "path": "文件路径",
      "container": "mkv",
      "size": 1234567890,
      "bitrate": 10000000
    }
  ]
}
```

### EmbyGenre（类型/标签/工作室）

```java
{
  "id": "ID",
  "name": "名称",
  "type": "类型（Genre/Tag/Studio）",
  "itemCount": 50
}
```

## 使用场景

### 1. 批量导出媒体信息

```java
// 获取所有媒体库
List<EmbyLibrary> libraries = embyService.getAllLibraries();

// 遍历每个媒体库
for (EmbyLibrary library : libraries) {
    // 获取媒体项
    List<EmbyItem> items = embyService.getLibraryItems(library.getId());

    // 处理媒体项数据
    for (EmbyItem item : items) {
        System.out.println(item.getName() + " - " + item.getPath());
    }
}
```

### 2. 搜索特定媒体

```java
// 搜索关键词
List<EmbyItem> results = embyService.searchItems("复仇者联盟");

// 处理搜索结果
for (EmbyItem item : results) {
    System.out.println(item.getName() + " (" + item.getProductionYear() + ")");
}
```

### 3. 同步到本地数据库

```java
// 同步所有数据
Map<String, Object> result = embyService.syncAllLibraries();

// 获取统计信息
int totalLibraries = (int) result.get("totalLibraries");
int totalItems = (int) result.get("totalItems");
List<EmbyLibrary> libraries = (List<EmbyLibrary>) result.get("libraries");

// 保存到本地数据库
// ... 你的业务逻辑
```

## 注意事项

1. **API Key 安全**：不要将 API Key 提交到公共代码仓库
2. **网络连接**：确保后端服务器能访问 Emby 服务器
3. **性能考虑**：大型媒体库同步可能需要较长时间，建议使用分页或异步处理
4. **权限问题**：确保 API Key 对应的用户有足够的权限访问媒体库
5. **超时设置**：如果媒体库很大，可能需要增加 `timeout` 配置

## 扩展开发

### 添加新的 Emby API

1. 在 `IEmbyService` 接口中添加方法定义
2. 在 `EmbyServiceImpl` 中实现方法
3. 在 `EmbyController` 中添加对应的 REST 接口
4. 在前端 `emby.js` 中添加 API 调用方法
5. 在前端页面中使用新的 API

### 与上传系统集成

可以将 Emby 的媒体信息与 Google Drive 上传系统结合：

```java
// 示例：将 Emby 媒体项上传到 Google Drive
List<EmbyItem> items = embyService.getLibraryItems(libraryId);

for (EmbyItem item : items) {
    // 创建上传任务
    UploadTask task = new UploadTask();
    task.setName(item.getName());
    task.setSourcePath(item.getPath());
    task.setTargetPath("/GoogleDrive/" + item.getName());

    // 提交上传任务
    uploadTaskService.createTask(task);
}
```

## 故障排查

### 1. 连接失败

- 检查 `server-url` 是否正确
- 检查网络连接
- 检查 Emby 服务器是否运行

### 2. 认证失败

- 检查 `api-key` 是否正确
- 检查 API Key 是否已过期
- 重新生成 API Key

### 3. 数据为空

- 检查用户权限
- 检查媒体库是否有内容
- 查看后端日志获取详细错误信息

### 4. 超时错误

- 增加 `timeout` 配置值
- 使用分页查询减少单次请求数据量
- 检查网络速度

## 技术支持

如有问题，请查看：
- 后端日志：`logs/gd-upload-manager.log`
- Emby 官方文档：https://github.com/MediaBrowser/Emby
- Emby API 文档：https://github.com/MediaBrowser/Emby/wiki/API-Documentation
