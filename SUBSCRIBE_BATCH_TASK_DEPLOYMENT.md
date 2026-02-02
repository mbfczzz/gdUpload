# 订阅批量搜索后台任务部署说明

## 功能说明

实现了订阅批量搜索的后台任务系统，支持：
- ✅ 创建后台批量任务
- ✅ 任务在服务器端异步执行
- ✅ 关闭浏览器后任务继续运行
- ✅ 实时查看任务进度和状态
- ✅ 查看详细执行日志
- ✅ 暂停/恢复/删除任务

## 部署步骤

### 1. 数据库迁移

执行数据库迁移脚本：

```bash
mysql -u root -p gd_upload_manager < database/migration_subscribe_batch_task.sql
```

这将创建两个新表：
- `subscribe_batch_task` - 批量任务表
- `subscribe_batch_log` - 任务执行日志表

### 2. 后端部署

后端代码已经完成，包含：

**实体类：**
- `SubscribeBatchTask.java` - 任务实体
- `SubscribeBatchLog.java` - 日志实体

**Mapper：**
- `SubscribeBatchTaskMapper.java`
- `SubscribeBatchLogMapper.java`

**Service：**
- `ISubscribeBatchTaskService.java` - 任务管理服务接口
- `SubscribeBatchTaskServiceImpl.java` - 任务管理服务实现
- `ISubscribeBatchExecutorService.java` - 任务执行器接口
- `SubscribeBatchExecutorServiceImpl.java` - 任务执行器实现（异步）

**Controller：**
- `SubscribeBatchTaskController.java` - API接口

重新编译并启动后端：

```bash
cd backend
mvn clean package
java -jar target/gd-upload-manager.jar
```

### 3. 前端部署

前端代码已更新：

**API模块：**
- `frontend/src/api/subscribeBatch.js` - 后台任务API

**页面组件：**
- `frontend/src/views/SubscribeSearch.vue` - 订阅搜索页面（已更新）

重新编译并部署前端：

```bash
cd frontend
npm install
npm run build
```

将 `dist` 目录部署到 Nginx。

## API 接口

### 创建批量任务
```
POST /api/subscribe-batch/create
Body: {
  "taskName": "任务名称",
  "jsonData": "[{\"id\":1,\"name\":\"订阅1\"}]",
  "delayMin": 1,
  "delayMax": 2
}
```

### 启动任务
```
POST /api/subscribe-batch/start/{taskId}
```

### 暂停任务
```
POST /api/subscribe-batch/pause/{taskId}
```

### 获取任务列表
```
GET /api/subscribe-batch/page?current=1&size=10
```

### 获取任务日志
```
GET /api/subscribe-batch/{taskId}/logs
```

### 删除任务
```
DELETE /api/subscribe-batch/{taskId}
```

## 使用流程

### 1. 导入订阅JSON

在"订阅搜索"页面，上传包含订阅列表的JSON文件：

```json
[
  {
    "id": 2,
    "name": "极限拯救",
    "year": "2026",
    "type": "电视剧"
  },
  {
    "id": 14,
    "name": "另一个订阅",
    "type": "电影"
  }
]
```

### 2. 配置请求间隔

设置每次请求之间的随机延迟时间（1-10分钟）。

### 3. 创建后台任务

点击"后台批量搜索（关闭浏览器继续执行）"按钮：
- 系统会创建一个后台任务
- 任务自动开始执行
- 可以关闭浏览器，任务继续运行

### 4. 查看任务状态

点击"查看任务列表"按钮：
- 查看所有任务的状态和进度
- 启动/暂停/删除任务
- 查看详细执行日志

## 任务状态说明

- **PENDING** - 待执行：任务已创建但未启动
- **RUNNING** - 执行中：任务正在后台执行
- **PAUSED** - 已暂停：任务被手动暂停
- **COMPLETED** - 已完成：任务执行完成
- **FAILED** - 失败：任务执行失败

## 技术实现

### 后端异步执行

使用 Spring 的 `@Async` 注解实现异步任务执行：

```java
@Async("taskExecutor")
public void executeTask(SubscribeBatchTask task) {
    // 任务在独立线程中执行
    // 即使前端断开连接，任务继续运行
}
```

### 任务持久化

所有任务信息和执行日志都保存在数据库中：
- 任务状态实时更新
- 执行日志详细记录
- 支持断点续传（暂停后可继续）

### 防重复执行

系统会记录已执行的订阅ID，避免重复请求：
- 暂停后重新启动，跳过已执行的订阅
- 失败的订阅也会被标记，避免无限重试

## 注意事项

1. **服务器资源**：后台任务会占用服务器资源，建议监控服务器负载
2. **并发限制**：线程池配置为最多10个并发任务（可在 `AsyncConfig.java` 中调整）
3. **任务清理**：建议定期清理已完成的旧任务
4. **API Token**：订阅搜索API的Token有过期时间，需要定期更新（在 `SubscribeBatchExecutorServiceImpl.java` 中）

## 优势

相比前端执行的优势：

| 特性 | 前端执行 | 后台任务 |
|------|---------|---------|
| 关闭浏览器 | ❌ 任务中断 | ✅ 继续执行 |
| 网络稳定性 | ❌ 依赖客户端网络 | ✅ 服务器网络稳定 |
| 执行记录 | ❌ 仅本地存储 | ✅ 数据库持久化 |
| 多设备查看 | ❌ 无法跨设备 | ✅ 任何设备都可查看 |
| 断点续传 | ❌ 需手动处理 | ✅ 自动支持 |
| 任务管理 | ❌ 功能有限 | ✅ 完整的任务管理 |

## 故障排查

### 任务一直处于RUNNING状态

检查后端日志：
```bash
tail -f logs/gd-upload-manager.log
```

### 任务执行失败

1. 检查订阅搜索API是否可访问
2. 检查API Token是否过期
3. 查看任务日志中的错误信息

### 数据库连接问题

检查 `application.yml` 中的数据库配置。

## 后续优化建议

1. **定时任务清理**：自动清理30天前的已完成任务
2. **邮件通知**：任务完成后发送邮件通知
3. **WebSocket推送**：实时推送任务进度到前端
4. **任务优先级**：支持设置任务优先级
5. **任务调度**：支持定时执行任务
