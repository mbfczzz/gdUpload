# AI智能筛选集成指南

## 概述

本系统已实现完整的AI智能筛选框架，包括：
1. ✅ 前端UI和交互
2. ✅ 后端API接口
3. ⏳ AI模型集成（待实现）

## 当前功能

### 1. 智能排序（已实现）
- 标题匹配度评分（40分）
- 分辨率评分（20分）
- 文件大小评分（15分）
- 标签匹配评分（10分）
- 来源可信度评分（10分）
- 时效性评分（5分）

### 2. 链接验证（框架已实现）
- 批量验证链接有效性
- 检测链接是否包含文件
- 统计文件数量

### 3. AI筛选（框架已实现）
- 接收电影信息和候选资源
- 调用AI模型进行语义匹配
- 返回最佳推荐和理由

## AI模型集成方案

### 方案A：使用Claude API

#### 1. 添加依赖

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.anthropic</groupId>
    <artifactId>anthropic-sdk-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

#### 2. 配置API Key

在 `application.yml` 中添加：

```yaml
app:
  ai:
    provider: claude
    api-key: ${ANTHROPIC_API_KEY}
    model: claude-3-5-sonnet-20241022
    max-tokens: 1024
```

#### 3. 实现AI服务

创建 `AIService.java`:

```java
@Service
public class AIService {

    @Value("${app.ai.api-key}")
    private String apiKey;

    @Value("${app.ai.model}")
    private String model;

    public String selectBestResource(Map<String, Object> movieInfo, List<Map<String, Object>> resources) {
        // 构建提示词
        String prompt = buildPrompt(movieInfo, resources);

        // 调用Claude API
        AnthropicClient client = new AnthropicClient(apiKey);

        MessageRequest request = MessageRequest.builder()
            .model(model)
            .maxTokens(1024)
            .messages(List.of(
                Message.builder()
                    .role("user")
                    .content(prompt)
                    .build()
            ))
            .build();

        MessageResponse response = client.messages().create(request);

        return response.getContent().get(0).getText();
    }

    private String buildPrompt(Map<String, Object> movieInfo, List<Map<String, Object>> resources) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个专业的影视资源筛选助手。\n\n");
        prompt.append("电影信息：\n");
        prompt.append("- 名称：").append(movieInfo.get("name")).append("\n");
        prompt.append("- 原始名称：").append(movieInfo.get("originalTitle")).append("\n");
        prompt.append("- 年份：").append(movieInfo.get("productionYear")).append("\n");
        prompt.append("- 评分：").append(movieInfo.get("communityRating")).append("\n\n");

        prompt.append("候选资源列表：\n");
        for (int i = 0; i < resources.size(); i++) {
            Map<String, Object> resource = resources.get(i);
            prompt.append(String.format("%d. ID: %s\n", i + 1, resource.get("id")));
            prompt.append(String.format("   标题: %s\n", resource.get("title")));
            prompt.append(String.format("   大小: %s\n", resource.get("size")));
            prompt.append(String.format("   分辨率: %s\n", resource.get("resolution")));
            prompt.append(String.format("   标签: %s\n", resource.get("tags")));
            prompt.append(String.format("   匹配分数: %.1f\n", resource.get("matchScore")));
            prompt.append(String.format("   链接有效: %s\n\n", resource.get("isValid")));
        }

        prompt.append("请分析以上资源，选择最适合的一个。考虑因素：\n");
        prompt.append("1. 标题与电影名称的匹配度\n");
        prompt.append("2. 分辨率和画质（优先4K/1080p）\n");
        prompt.append("3. 文件大小是否合理\n");
        prompt.append("4. 链接是否有效\n");
        prompt.append("5. 是否包含完整的电影内容\n\n");

        prompt.append("请以JSON格式返回结果：\n");
        prompt.append("{\n");
        prompt.append("  \"best_resource_id\": \"资源ID\",\n");
        prompt.append("  \"reason\": \"选择理由\",\n");
        prompt.append("  \"confidence\": 0.95\n");
        prompt.append("}");

        return prompt.toString();
    }
}
```

#### 4. 更新Controller

在 `TelegramSearchController.java` 中：

```java
@Autowired
private AIService aiService;

@PostMapping("/ai-select")
public Result<Map<String, Object>> aiSelectBestResource(@RequestBody Map<String, Object> params) {
    try {
        @SuppressWarnings("unchecked")
        Map<String, Object> movieInfo = (Map<String, Object>) params.get("movie_info");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) params.get("resources");

        // 调用AI服务
        String aiResponse = aiService.selectBestResource(movieInfo, resources);

        // 解析AI返回的JSON
        JSONObject result = JSONUtil.parseObj(aiResponse);

        return Result.success(result);

    } catch (Exception e) {
        log.error("AI筛选失败", e);
        return Result.error("AI筛选失败: " + e.getMessage());
    }
}
```

### 方案B：使用OpenAI API

类似方案A，但使用OpenAI的SDK：

```xml
<dependency>
    <groupId>com.theokanning.openai-gpt3-java</groupId>
    <artifactId>service</artifactId>
    <version>0.18.2</version>
</dependency>
```

```java
OpenAiService service = new OpenAiService(apiKey);

ChatCompletionRequest request = ChatCompletionRequest.builder()
    .model("gpt-4")
    .messages(List.of(
        new ChatMessage("user", prompt)
    ))
    .build();

ChatCompletionResult result = service.createChatCompletion(request);
```

### 方案C：使用本地模型（Ollama）

如果不想使用云端API，可以使用本地模型：

```bash
# 安装Ollama
curl -fsSL https://ollama.com/install.sh | sh

# 下载模型
ollama pull llama2

# 启动服务
ollama serve
```

Java调用：

```java
String url = "http://localhost:11434/api/generate";

JSONObject requestBody = new JSONObject();
requestBody.set("model", "llama2");
requestBody.set("prompt", prompt);

HttpResponse response = HttpRequest.post(url)
    .body(requestBody.toString())
    .execute();
```

## 链接验证集成

### 阿里云盘链接验证

需要调用阿里云盘的分享链接API：

```java
public boolean validateAlipanLink(String shareUrl) {
    try {
        // 提取分享ID
        String shareId = extractShareId(shareUrl);

        // 调用阿里云盘API
        String apiUrl = "https://api.alipan.com/adrive/v1/share/get_share_by_anonymous";

        JSONObject requestBody = new JSONObject();
        requestBody.set("share_id", shareId);

        HttpResponse response = HttpRequest.post(apiUrl)
            .header("Content-Type", "application/json")
            .body(requestBody.toString())
            .execute();

        if (response.isOk()) {
            JSONObject result = JSONUtil.parseObj(response.body());
            return result.getBool("success", false);
        }

        return false;

    } catch (Exception e) {
        log.error("验证阿里云盘链接失败", e);
        return false;
    }
}
```

### 天翼云盘链接验证

类似的，调用天翼云盘API：

```java
public boolean validate189Link(String shareUrl) {
    // 实现天翼云盘链接验证逻辑
    // ...
}
```

## 使用说明

### 1. 前端使用

用户在Emby媒体库中点击"搜索下载"后：

1. **自动搜索**：使用电影名称搜索资源
2. **智能排序**：基于规则评分系统排序
3. **链接验证**：验证链接有效性（可选）
4. **AI筛选**：使用AI模型进行语义匹配（可选）
5. **自动推荐**：显示最佳匹配资源

### 2. 配置选项

用户可以在UI中选择：
- ☑️ 验证链接有效性
- ☑️ 使用AI智能推荐

### 3. 结果展示

- **状态图标**：✓ 有效 / ✗ 无效 / ? 未验证
- **匹配度进度条**：0-100分，颜色编码
- **推荐标签**：AI推荐 / 推荐
- **文件信息**：文件数量

## 性能优化

### 1. 缓存验证结果

```java
@Cacheable(value = "linkValidation", key = "#url")
public boolean validateLink(String url) {
    // ...
}
```

### 2. 异步验证

```java
@Async
public CompletableFuture<Boolean> validateLinkAsync(String url) {
    return CompletableFuture.completedFuture(validateLink(url));
}
```

### 3. 批量处理

一次性验证多个链接，减少API调用次数。

## 成本估算

### Claude API
- 输入：$3 / 1M tokens
- 输出：$15 / 1M tokens
- 每次筛选约1000 tokens
- 成本：约 $0.02 / 次

### OpenAI GPT-4
- 输入：$10 / 1M tokens
- 输出：$30 / 1M tokens
- 成本：约 $0.04 / 次

### 本地模型（Ollama）
- 免费
- 需要GPU资源

## 下一步

1. ✅ 实现链接验证API
2. ✅ 集成AI模型
3. ⏳ 添加缓存机制
4. ⏳ 性能优化
5. ⏳ 监控和日志

## 参考资料

- [Claude API文档](https://docs.anthropic.com/claude/reference/getting-started-with-the-api)
- [OpenAI API文档](https://platform.openai.com/docs/api-reference)
- [Ollama文档](https://github.com/ollama/ollama)
- [阿里云盘开放平台](https://www.alipan.com/drive/open)
