# Emby库检查模块 实施计划

## 功能目标
对 GD 盘上的 strm 目录进行文件名/目录名合规性检查，以文件树形式展示，标注问题严重程度，支持按问题分类筛选。

---

## 一、后端

### 1. 新建 DTO: `EmbyLibraryFileNode.java`
文件树节点，字段：
- `name` (文件/目录名)
- `path` (完整相对路径)
- `isDir` (是否目录)
- `fileType` (movie/tv/episode/season/unknown — 用于前端图标)
- `size`, `modTime`
- `issues` (List<Issue>) — 本节点的问题列表
- `children` (List<EmbyLibraryFileNode>) — 子节点（目录时有值）
- `issueStats` (Map<String,Integer>) — 子树中各级别问题数量汇总

### 2. 内嵌 DTO: `Issue`
- `code` (问题编码，如 `MISSING_TMDBID`, `CONTAINS_NULL` 等)
- `severity` ("error" | "warning" | "info")
- `message` (人类可读描述)
- `category` (问题分类，用于前端筛选)

### 3. 问题编码规则（检查项）

**error 级别（直接影响 Emby 识别/播放）**:
| code | category | 说明 |
|------|----------|------|
| MISSING_SEASON | 缺少季号 | 电视剧文件缺少 S01 |
| MISSING_EPISODE | 缺少集号 | 电视剧文件缺少 E01 |
| CONTAINS_NULL | 包含null | 文件名含 "null" 字符串（如 S1 null） |
| INVALID_STRM_EXTENSION | 扩展名错误 | 非 .strm 文件出现在库中 |
| EMPTY_FILENAME | 文件名为空 | 文件名空白或仅空格 |

**warning 级别（有问题但不影响播放）**:
| code | category | 说明 |
|------|----------|------|
| MISSING_TMDBID | 缺少TMDB ID | 目录名缺少 [tmdbid=xxx] |
| MISSING_YEAR | 缺少年份 | 目录名缺少 (2024) |
| MISSING_RESOLUTION | 缺少分辨率 | 文件名无 720p/1080p/4K 等 |
| SPECIAL_CHARACTERS | 特殊字符 | 含 Emby 不友好字符 |
| INCONSISTENT_NAMING | 命名不统一 | 同目录下文件命名风格不一致 |

**info 级别（建议优化）**:
| code | category | 说明 |
|------|----------|------|
| MISSING_CODEC_INFO | 缺少编码信息 | 文件名无 HEVC/x264 等 |
| MISSING_AUDIO_CODEC | 缺少音频编码 | 无 AAC/FLAC 等 |
| NO_EPISODE_TITLE | 缺少集标题 | S01E01 后无集标题 |

### 4. 新建 Service: `IEmbyLibraryInspectService`
实现类 `EmbyLibraryInspectServiceImpl`

方法：
- `List<EmbyLibraryFileNode> inspectLibrary(String rcloneRemote, String path)` — 递归扫描 GD 目录，构建带验证结果的文件树
- `Map<String, Object> getInspectSummary(String rcloneRemote, String path)` — 返回汇总统计（总文件数、各级别问题数、各分类问题数）

内部逻辑：
1. 调用 `rcloneUtil.listJson(remote, path)` 获取当层目录列表
2. 对每个节点执行验证规则
3. 目录节点递归子层
4. 从目录路径深度推断节点类型（第1层=分类, 第2层=作品目录, 第3层=Season, 第4层=文件）
5. 汇总子树 issue 统计到父节点

### 5. 新建 Controller: `EmbyLibraryInspectController`
路径前缀 `/emby-library`

| Method | Path | 说明 |
|--------|------|------|
| GET | `/inspect` | 扫描指定路径并返回文件树 + 验证结果 |
| GET | `/summary` | 返回汇总统计 |

参数：`rcloneRemote` (远程名), `path` (目录路径, 默认 `/`)

---

## 二、前端

### 1. 新建 API: `frontend/src/api/embyLibrary.js`
- `inspectLibrary(rcloneRemote, path)`
- `getInspectSummary(rcloneRemote, path)`

### 2. 新建页面: `frontend/src/views/EmbyLibraryInspect.vue`

**布局结构**:
```
┌──────────────────────────────────────────────────┐
│ 工具栏: [选择远程] [路径输入] [扫描] [展开/折��]      │
├──────────────────────────────────────────────────┤
│ 统计卡片:  总文件 | 严重问题 | 警告 | 建议           │
├────────────┬─────────────────────────────────────┤
│ 筛选面板    │  文件树（el-tree）                     │
│            │                                      │
│ 严重程度    │  📁 动漫/                             │
│ ☑ 严重     │    📁 名侦探柯南-1996-[tmdbid=30984]/ │
│ ☑ 警告     │      📁 Season 1/                    │
│ ☑ 建议     │        🎬 S01E01 xxx.strm            │
│            │        ⚠️ S01E02 null.strm  ← 标红   │
│ 问题分类    │      📁 Season 2/                    │
│ ☑ 缺少季号  │        ...                           │
│ ☑ 包含null  │                                      │
│ ☑ 缺少TMDB │                                      │
│ ...        │                                      │
└────────────┴─────────────────────────────────────┘
```

**文件类型图标**:
- 📁 FolderOpened — 普通目录
- 📂 Folder (蓝) — 分类目录（动漫/电视剧/电影）
- 🎬 Film — 电影文件
- 📺 Monitor — 电视剧集文件
- 📄 Document — 其他文件
- ⚠️ 问题节点用颜色高亮（红=error, 橙=warning, 蓝=info）

**节点显示**:
- 文件/目录名 + 问题标签 (el-tag)
- 节点右侧显示子树问题数量角标
- 点击节点展开问题详情面板

**筛选功能**:
- 严重程度多选（error/warning/info）
- 问题分类多选（缺少季号/包含null/缺少TMDB等）
- 筛选后隐藏无问题的节点，只显示有匹配问题的分支

### 3. 路由注册
`/emby-library` → `EmbyLibraryInspect.vue`，菜单名"Emby库检查"

### 4. 侧边栏菜单
在 Emby管理 下方添加菜单项，图标用 `Checked` (打勾)

---

## 三、实施步骤

1. 创建后端 DTO（EmbyLibraryFileNode + Issue）
2. 创建后端 Service（验证逻辑）
3. 创建后端 Controller
4. 创建前端 API
5. 创建前端页面
6. 注册路由 + 侧边栏
