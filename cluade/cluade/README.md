# Google Drive 多账号上传管理系统

## 项目简介

这是一个基于 Spring Boot + Vue3 的 Google Drive 多账号上传管理系统，通过 rclone 实现多账号自动切换，突破单账号 750GB 每日上传限制。

## 功能特性

### 核心功能
1. **多账号管理**
   - 支持添加、编辑、删除 Google Drive 账号
   - 自动计算账号剩余配额
   - 账号优先级设置
   - 账号状态管理（启用/禁用/已达上限）

2. **智能上传**
   - 自动识别 750GB 限制并切换账号
   - 支持断点续传
   - 批量文件上传
   - 文件上传状态标记（待上传/上传中/已上传/失败/跳过）

3. **任务管理**
   - 创建上传任务
   - 任务执行、暂停、取消
   - 任务进度实时监控
   - 任务历史记录

4. **详细日志**
   - 完整的上传日志记录
   - 每个账号的上传量统计
   - 任务执行详情
   - 错误信息追踪

5. **配额预警**
   - 上传量不足提前提示
   - 账号配额警告（90%阈值）
   - 每日配额自动重置

## 技术栈

### 后端
- **框架**: Spring Boot 3.2.1
- **Java版本**: JDK 17
- **ORM**: MyBatis-Plus 3.5.5
- **数据库**: MySQL 8.0+
- **连接池**: Druid 1.2.20
- **工具类**: Hutool 5.8.24
- **API文档**: Knife4j 4.4.0
- **实时通信**: WebSocket

### 前端
- **框架**: Vue 3
- **UI组件**: Element Plus
- **构建工具**: Vite
- **状态管理**: Pinia
- **HTTP客户端**: Axios
- **WebSocket**: SockJS + STOMP

### 工具
- **上传工具**: rclone

## 项目结构

```
gd-upload-manager/
├── backend/                          # 后端项目
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/gdupload/
│   │   │   │   ├── common/          # 通用类
│   │   │   │   │   ├── Result.java           # 统一响应结果
│   │   │   │   │   ├── PageResult.java       # 分页响应结果
│   │   │   │   │   ├── BusinessException.java # 业务异常
│   │   │   │   │   └── GlobalExceptionHandler.java # 全局异常处理
│   │   │   │   ├── config/          # 配置类
│   │   │   │   │   ├── MybatisPlusConfig.java
│   │   │   │   │   ├── MyMetaObjectHandler.java
│   │   │   │   │   ├── CorsConfig.java
│   │   │   │   │   └── WebSocketConfig.java
│   │   │   │   ├── controller/      # 控制器
│   │   │   │   │   ├── GdAccountController.java
│   │   │   │   │   ├── UploadTaskController.java
│   │   │   │   │   ├── FileInfoController.java
│   │   │   │   │   └── SystemLogController.java
│   │   │   │   ├── entity/          # 实体类
│   │   │   │   │   ├── GdAccount.java
│   │   │   │   │   ├── UploadTask.java
│   │   │   │   │   ├── FileInfo.java
│   │   │   │   │   ├── UploadRecord.java
│   │   │   │   │   └── SystemLog.java
│   │   │   │   ├── mapper/          # Mapper接口
│   │   │   │   │   ├── GdAccountMapper.java
│   │   │   │   │   ├── UploadTaskMapper.java
│   │   │   │   │   ├── FileInfoMapper.java
│   │   │   │   │   ├── UploadRecordMapper.java
│   │   │   │   │   └── SystemLogMapper.java
│   │   │   │   ├── service/         # 服务接口
│   │   │   │   │   ├── IGdAccountService.java
│   │   │   │   │   ├── IUploadTaskService.java
│   │   │   │   │   ├── IFileInfoService.java
│   │   │   │   │   └── IUploadService.java
│   │   │   │   ├── service/impl/    # 服务实现
│   │   │   │   │   ├── GdAccountServiceImpl.java
│   │   │   │   │   ├── UploadTaskServiceImpl.java
│   │   │   │   │   ├── FileInfoServiceImpl.java
│   │   │   │   │   └── UploadServiceImpl.java
│   │   │   │   ├── util/            # 工具类
│   │   │   │   │   └── RcloneUtil.java
│   │   │   │   └── GdUploadManagerApplication.java
│   │   │   └── resources/
│   │   │       ├── application.yml   # 应用配置
│   │   │       └── mapper/           # MyBatis XML映射文件
│   │   └── test/
│   └── pom.xml
├── frontend/                         # 前端项目
│   ├── src/
│   │   ├── api/                     # API接口
│   │   ├── assets/                  # 静态资源
│   │   ├── components/              # 组件
│   │   ├── router/                  # 路由
│   │   ├── stores/                  # 状态管理
│   │   ├── utils/                   # 工具类
│   │   ├── views/                   # 页面
│   │   │   ├── Account.vue          # 账号管理
│   │   │   ├── Task.vue             # 任务管理
│   │   │   ├── Upload.vue           # 文件上传
│   │   │   ├── Log.vue              # 日志查看
│   │   │   └── Dashboard.vue        # 统计面板
│   │   ├── App.vue
│   │   └── main.js
│   ├── package.json
│   └── vite.config.js
└── database/
    └── schema.sql                    # 数据库表结构

```

## 数据库设计

### 核心表
1. **gd_account** - Google Drive账号表
2. **upload_task** - 上传任务表
3. **file_info** - 文件信息表
4. **upload_record** - 上传记录表
5. **account_usage_stats** - 账号使用统计表
6. **system_log** - 系统日志表
7. **system_config** - 系统配置表

详细表结构请查看 `database/schema.sql`

## 安装部署

### 前置要求
- JDK 17+
- MySQL 8.0+
- Node.js 16+
- rclone

### 后端部署

1. **创建数据库**
```bash
mysql -u root -p < database/schema.sql
```

2. **配置数据库连接**
编辑 `backend/src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/gd_upload_manager
    username: your_username
    password: your_password
```

3. **配置rclone路径**
```yaml
app:
  rclone:
    path: /usr/bin/rclone  # rclone可执行文件路径
    config-path: ~/.config/rclone/rclone.conf  # rclone配置文件路径
```

4. **编译运行**
```bash
cd backend
mvn clean package
java -jar target/gd-upload-manager-1.0.0.jar
```

访问 API 文档: http://localhost:8080/api/doc.html

### 前端部署

1. **安装依赖**
```bash
cd frontend
npm install
```

2. **配置API地址**
编辑 `frontend/src/utils/request.js`:
```javascript
const baseURL = 'http://localhost:8080/api'
```

3. **运行开发服务器**
```bash
npm run dev
```

4. **构建生产版本**
```bash
npm run build
```

## 使用说明

### 1. 配置 rclone

首先需要配置 rclone 的 Google Drive 远程:

```bash
rclone config
```

按照提示添加多个 Google Drive 账号，例如: `gd1`, `gd2`, `gd3`

### 2. 添加账号

在系统中添加已配置的 Google Drive 账号:
- 账号名称: 自定义名称
- 账号邮箱: Google Drive 邮箱
- rclone配置名称: 在 rclone 中配置的名称（如 gd1）
- 每日限制: 默认 750GB
- 优先级: 数字越大优先级越高

### 3. 创建上传任务

- 任务名称: 自定义任务名称
- 源文件路径: 本地文件或文件夹路径
- 目标路径: Google Drive 目标路径
- 选择文件: 批量选择要上传的文件

### 4. 执行任务

- 点击"开始上传"按钮
- 系统自动选择可用账号
- 达到 750GB 限制时自动切换账号
- 实时查看上传进度和日志

### 5. 查看统计

- 查看每个账号的上传量
- 查看任务执行历史
- 查看详细日志记录

## API 接口

### 账号管理
- `GET /api/account/page` - 分页查询账号列表
- `GET /api/account/{id}` - 获取账号详情
- `GET /api/account/available` - 获取可用账号列表
- `POST /api/account` - 新增账号
- `PUT /api/account` - 更新账号
- `DELETE /api/account/{id}` - 删除账号
- `PUT /api/account/{id}/status` - 启用/禁用账号
- `PUT /api/account/{id}/reset-quota` - 重置账号配额

### 任务管理
- `GET /api/task/page` - 分页查询任务列表
- `GET /api/task/{id}` - 获取任务详情
- `POST /api/task` - 创建任务
- `PUT /api/task/{id}/start` - 开始任务
- `PUT /api/task/{id}/pause` - 暂停任务
- `PUT /api/task/{id}/cancel` - 取消任务
- `DELETE /api/task/{id}` - 删除任务

### 文件管理
- `GET /api/file/page` - 分页查询文件列表
- `POST /api/file/scan` - 扫描文件
- `PUT /api/file/{id}/status` - 更新文件状态

### 日志查询
- `GET /api/log/page` - 分页查询日志
- `GET /api/log/stats` - 获取统计信息

## 配置说明

### 系统配置

在 `system_config` 表中可以配置以下参数:

- `rclone.path` - rclone可执行文件路径
- `rclone.config.path` - rclone配置文件路径
- `upload.concurrent.files` - 并发上传文件数
- `upload.retry.times` - 上传失败重试次数
- `upload.check.interval` - 上传检查间隔(秒)
- `account.daily.limit` - 账号每日上传限制(字节)
- `account.warning.threshold` - 账号配额警告阈值
- `log.retention.days` - 日志保留天数

### 定时任务

系统自动创建定时任务，每天凌晨重置账号配额。

## 注意事项

1. **rclone 配置**: 确保 rclone 已正确配置 Google Drive 账号
2. **权限问题**: 确保应用有权限访问 rclone 配置文件和上传目录
3. **配额限制**: Google Drive 每个账号每天有 750GB 上传限制
4. **网络稳定**: 上传大文件时需要稳定的网络连接
5. **磁盘空间**: 确保有足够的磁盘空间用于临时文件
6. **数据库备份**: 定期备份数据库，防止数据丢失

## 常见问题

### 1. rclone 命令执行失败
- 检查 rclone 路径是否正确
- 检查 rclone 配置文件是否存在
- 检查账号授权是否过期

### 2. 上传速度慢
- 调整并发传输数 `--transfers`
- 调整缓冲区大小 `--buffer-size`
- 检查网络带宽

### 3. 账号切换不生效
- 检查账号状态是否启用
- 检查账号剩余配额
- 查看系统日志排查问题

### 4. 文件上传失败
- 查看错误日志
- 检查文件路径是否正确
- 检查目标路径权限

## 开发计划

- [ ] 支持更多云存储平台（OneDrive、Dropbox等）
- [ ] 支持文件加密上传
- [ ] 支持上传速度限制
- [ ] 支持邮件通知
- [ ] 支持移动端适配
- [ ] 支持Docker部署

## 许可证

MIT License

## 联系方式

如有问题或建议，请提交 Issue。
