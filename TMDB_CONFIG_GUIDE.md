# TMDB 配置指南

## 功能概述

TMDB (The Movie Database) 配置用于自动获取影视作品的元数据和ID，提高115资源匹配的准确度。

## 配置项说明

### 1. 启用TMDB
- **字段**: `tmdbEnabled`
- **类型**: Boolean
- **默认值**: `false`
- **说明**: 是否启用TMDB功能

### 2. TMDB API Key
- **字段**: `tmdbApiKey`
- **类型**: String
- **必填**: 是（启用TMDB时）
- **说明**: TMDB API密钥
- **获取方式**:
  1. 访问 [TMDB官网](https://www.themoviedb.org/)
  2. 注册并登录账号
  3. 进入 [API设置页面](https://www.themoviedb.org/settings/api)
  4. 申请API密钥（选择"Developer"类型）
  5. 复制API密钥到配置中

### 3. TMDB API地址
- **字段**: `tmdbApiUrl`
- **类型**: String
- **默认值**: `https://api.themoviedb.org/3`
- **说明**: TMDB API的基础URL，通常使用默认值即可

### 4. 语言设置
- **字段**: `tmdbLanguage`
- **类型**: String
- **默认值**: `zh-CN`
- **可选值**:
  - `zh-CN`: 简体中文
  - `zh-TW`: 繁体中文
  - `en-US`: 英语
  - `ja-JP`: 日语
  - `ko-KR`: 韩语
- **说明**: TMDB返回数据的语言

### 5. 请求超时
- **字段**: `tmdbTimeout`
- **类型**: Integer
- **默认值**: `10000`
- **单位**: 毫秒
- **范围**: 3000-30000
- **说明**: TMDB API请求的超时时间

### 6. 自动匹配
- **字段**: `tmdbAutoMatch`
- **类型**: Boolean
- **默认值**: `true`
- **说明**: 在115资源管理中自动搜索并匹配TMDB ID

## 配置方式

### 方式1：通过前端界面配置

1. 访问"智能搜索配置"页面
2. 找到"TMDB配置"部分
3. 开启"启用TMDB"开关
4. 填写TMDB API Key
5. 根据需要调整其他配置项
6. 点击"保存配置"按钮

### 方式2：通过数据库配置

执行SQL脚本 `database/add_tmdb_config.sql`:

```sql
INSERT INTO `smart_search_config` (`user_id`, `config_name`, `config_type`, `config_data`, `is_active`, `remark`) VALUES
('default', 'TMDB配置', 'tmdb_config', '{
  "tmdbEnabled": true,
  "tmdbApiKey": "你的API密钥",
  "tmdbApiUrl": "https://api.themoviedb.org/3",
  "tmdbLanguage": "zh-CN",
  "tmdbTimeout": 10000,
  "tmdbAutoMatch": true
}', 1, 'TMDB影视数据库配置');
```

## 使用场景

### 1. 115资源管理

在"115资源管理"页面中：

- **添加资源时**: 输入资源名称后，点击"搜索"按钮自动查找TMDB ID
- **批量补充**: 点击"批量补充TMDB ID"按钮，自动为所有未设置TMDB ID的资源补充

### 2. Emby媒体项匹配

在"Emby管理"页面中：

- 点击"下载"按钮时，系统会优先使用TMDB ID进行精确匹配
- 如果TMDB ID匹配失败，则回退到名称匹配

## 测试连接

配置完成后，点击"测试TMDB服务"按钮验证配置是否正确：

- ✅ **成功**: 显示"TMDB服务连接正常 (找到 N 个结果)"
- ❌ **失败**: 检查以下项目
  - API Key是否正确
  - 网络连接是否正常
  - API地址是否正确

## API使用限制

TMDB API有以下使用限制：

- **免费版**: 每秒最多40个请求
- **请求频率**: 建议在批量操作时添加延迟（250ms）
- **配额**: 无每日请求限制

## 数据持久化

TMDB配置会自动保存到数据库的 `smart_search_config` 表中：

- **config_type**: `tmdb_config`
- **config_data**: JSON格式的配置数据
- **is_active**: 是否启用

配置同时会备份到浏览器的 localStorage 中，作为离线备份。

## 常见问题

### Q1: API Key无效怎么办？

**A**:
1. 确认API Key是否正确复制（无多余空格）
2. 检查API Key是否已激活（新申请的Key可能需要几分钟激活）
3. 确认账号状态是否正常

### Q2: 搜索不到结果怎么办？

**A**:
1. 检查资源名称格式（建议包含年份，如"阿凡达 (2009)"）
2. 尝试使用英文名称搜索
3. 调整语言设置

### Q3: 批量补充很慢怎么办？

**A**:
1. 这是正常现象，为了遵守API频率限制，每个请求间隔250ms
2. 可以在后台运行，不影响其他操作
3. 建议在资源较少时进行批量补充

### Q4: 配置保存失败怎么办？

**A**:
1. 检查数据库连接是否正常
2. 查看后端日志获取详细错误信息
3. 确认 `smart_search_config` 表是否存在

## 相关文件

### 前端
- `frontend/src/views/SmartSearchConfig.vue` - 配置界面
- `frontend/src/api/smartSearchConfig.js` - API封装

### 后端
- `backend/src/main/java/com/gdupload/service/impl/SmartSearchConfigServiceImpl.java` - 配置服务
- `backend/src/main/java/com/gdupload/service/impl/TmdbServiceImpl.java` - TMDB服务

### 数据库
- `database/add_tmdb_config.sql` - 配置初始化脚本
- `database/smart_search_config.sql` - 表结构

## 更新日志

### 2026-02-03
- ✅ 添加TMDB配置到智能搜索配置
- ✅ 实现配置持久化到数据库
- ✅ 添加前端配置界面
- ✅ 支持测试TMDB连接
- ✅ 集成到115资源管理和Emby管理

---

**配置完成后，记得点击"保存配置"按钮！**
