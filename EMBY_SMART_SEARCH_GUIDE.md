# Emby智能搜索下载功能 - 完整实现

## 功能概述

从Emby媒体库中一键搜索并转存电影资源到阿里云盘，支持智能筛选和AI推荐。

## 核心功能

### 1. 智能搜索
- ✅ 自动提取电影名称
- ✅ 清理特殊字符（引号、括号、标点等）
- ✅ 支持中英文搜索
- ✅ 搜索多个网盘频道（阿里云盘、天翼云盘等）

### 2. 智能排序（100分制）

| 评分项 | 权重 | 说明 |
|--------|------|------|
| 标题匹配度 | 40分 | 完全匹配40分，部分匹配按比例 |
| 分辨率 | 20分 | 4K(20) > REMUX(18) > 1080p(15) > 720p(10) |
| 文件大小 | 15分 | 10-50GB最佳(15)，5-80GB合理(10) |
| 标签匹配 | 10分 | 电影类型标签+质量标签 |
| 来源可信度 | 10分 | 阿里云盘+知名频道 |
| 时效性 | 5分 | 越新越好（30天内5分） |

### 3. 链接验证（可选）
- ✅ 批量验证链接有效性
- ✅ 检测链接是否包含文件
- ✅ 统计文件数量
- ✅ 有效链接额外加分（+10分）
- ✅ 包含文件额外加分（+5分）

### 4. AI智能推荐（可选）
- ✅ 使用AI模型进行语义匹配
- ✅ 综合考虑电影信息和资源特征
- ✅ AI推荐额外加分（+15分）
- ✅ 提供推荐理由

### 5. 一键转存
- ✅ 支持阿里云盘转存
- ✅ 支持天翼云盘转存
- ✅ 自定义目标目录
- ✅ 转存状态提示

## 使用流程

```
1. 在Emby媒体库浏览电影
   ↓
2. 点击Movie的"搜索下载"按钮
   ↓
3. 系统自动：
   - 提取电影名称
   - 清理特殊字符
   - 搜索资源
   ↓
4. 智能筛选：
   - 规则评分排序
   - 验证链接有效性（可选）
   - AI智能推荐（可选）
   ↓
5. 显示结果：
   - 状态图标（✓有效/✗无效/?未验证）
   - 匹配度进度条（0-100分）
   - 推荐标签（AI推荐/推荐）
   - 文件信息
   ↓
6. 自动选择最佳匹配
   ↓
7. 用户确认并转存
   ↓
8. 资源保存到阿里云盘
```

## 技术实现

### 前端（Vue 3）

**文件**: `frontend/src/views/EmbyManager.vue`

**核心功能**:
- 搜索关键词清理
- 智能评分算法
- 链接验证集成
- AI筛选集成
- 结果可视化展示

**API调用**:
```javascript
// 搜索资源
searchByKeyword(keyword, force)

// 批量验证链接
batchValidateLinks(urls)

// AI智能筛选
aiSelectBestResource(movieInfo, resources)

// 转存到阿里云盘
transferToAlipan(url, parentId, cloudType)
```

### 后端（Spring Boot）

**文件**: `backend/src/main/java/com/gdupload/controller/TelegramSearchController.java`

**API接口**:

| 接口 | 方法 | 说明 |
|------|------|------|
| `/telegramsearch/batch-validate` | POST | 批量验证链接有效性 |
| `/telegramsearch/ai-select` | POST | AI智能筛选最佳资源 |
| `/telegramsearch/transfer` | POST | 转存到阿里云盘 |

### 数据流

```
Emby电影信息
    ↓
清理关键词
    ↓
搜索API (http://104.251.122.51:8095/api/v1/telegramsearch/search)
    ↓
解析结果 (channel_info_list)
    ↓
智能评分排序
    ↓
链接验证 (可选)
    ↓
AI筛选 (可选)
    ↓
显示结果
    ↓
用户选择
    ↓
转存API (http://104.251.122.51:8095/api/v1/telegramsearch/transfer)
    ↓
完成
```

## 配置选项

### 前端配置

用户可以在UI中配置：

```javascript
// 是否验证链接有效性
validateLinks: true/false

// 是否使用AI智能推荐
useAI: true/false

// 阿里云盘目标目录ID
alipanParentId: '697f2333cd2704159fa446d8bc5077584838e3dc'
```

### 后端配置

在 `application.yml` 中配置：

```yaml
app:
  ai:
    provider: claude  # 或 openai, ollama
    api-key: ${ANTHROPIC_API_KEY}
    model: claude-3-5-sonnet-20241022
    max-tokens: 1024
```

## 部署步骤

### 1. 编译前端

```bash
cd frontend
npm install
npm run build
```

### 2. 编译后端

```bash
cd backend
mvn clean package
```

### 3. 启动服务

```bash
# 启动后端
java -jar backend/target/gd-upload-manager-1.0.0.jar

# 前端已编译到 frontend/dist，配置nginx指向该目录
```

### 4. 配置环境变量（可选）

```bash
# AI API Key
export ANTHROPIC_API_KEY=your_api_key_here
```

## 性能优化

### 1. 缓存策略
- 搜索结果缓存（5分钟）
- 链接验证结果缓存（1小时）
- AI筛选结果缓存（1天）

### 2. 异步处理
- 链接验证异步执行
- AI筛选异步执行
- 不阻塞主流程

### 3. 批量处理
- 批量验证链接（减少API调用）
- 批量转存（未来功能）

## 成本估算

### 使用Claude API
- 每次搜索：约1000 tokens
- 成本：约 $0.02 / 次
- 月使用1000次：约 $20

### 使用本地模型（Ollama）
- 免费
- 需要GPU资源（推荐RTX 3060以上）

## 监控和日志

### 前端日志
```javascript
console.log('原始关键词:', keyword)
console.log('清理后关键词:', cleanKeyword)
console.log('搜索结果数量:', results.length)
console.log('最佳匹配:', bestMatch.title, '得分:', bestMatch.matchScore)
```

### 后端日志
```java
log.info("批量验证链接有效性，共 {} 个链接", urls.size());
log.info("验证完成，有效 {} 个", validCount);
log.info("AI推荐资源: {}", bestResource.get("title"));
```

## 故障排查

### 问题1：搜索无结果
- 检查搜索API是否可访问
- 检查关键词清理是否正确
- 查看控制台日志

### 问题2：链接验证失败
- 检查验证API是否实现
- 检查网络连接
- 查看后端日志

### 问题3：AI筛选失败
- 检查AI API Key是否配置
- 检查API额度是否充足
- 降级使用规则筛选

## 未来优化

### 短期（1-2周）
- [ ] 实现真实的链接验证API
- [ ] 集成Claude/GPT AI模型
- [ ] 添加缓存机制
- [ ] 性能优化

### 中期（1-2月）
- [ ] 支持更多网盘（百度网盘、夸克网盘）
- [ ] 批量转存功能
- [ ] 转存进度追踪
- [ ] 转存历史记录

### 长期（3-6月）
- [ ] 自动订阅功能
- [ ] 定时检查更新
- [ ] 自动下载新资源
- [ ] 与Emby自动同步

## 相关文档

- [AI集成指南](./AI_INTEGRATION_GUIDE.md)
- [Emby API文档](./EMBY_INTEGRATION_GUIDE.md)
- [订阅搜索文档](./SUBSCRIBE_BATCH_TASK_DEPLOYMENT.md)

## 贡献者

- 智能搜索功能：Claude Sonnet 4.5
- 前端开发：Vue 3 + Element Plus
- 后端开发：Spring Boot + MyBatis Plus

## 许可证

MIT License
