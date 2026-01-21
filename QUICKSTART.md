# 快速开始指南

## 环境准备

### 1. 安装必要软件

#### Windows环境
- **JDK 17**: 从 [Oracle官网](https://www.oracle.com/java/technologies/downloads/) 或 [OpenJDK](https://adoptium.net/) 下载安装
- **MySQL 8.0**: 从 [MySQL官网](https://dev.mysql.com/downloads/mysql/) 下载安装
- **Node.js 16+**: 从 [Node.js官网](https://nodejs.org/) 下载安装
- **Maven**: 从 [Maven官网](https://maven.apache.org/download.cgi) 下载并配置环境变量
- **rclone**: 从 [rclone官网](https://rclone.org/downloads/) 下载

#### 验证安装
```bash
java -version
mysql --version
node -v
npm -v
mvn -v
rclone version
```

### 2. 配置rclone

#### 添加Google Drive账号
```bash
rclone config
```

按照提示操作：
1. 选择 `n` 新建远程配置
2. 输入名称，例如: `gd1`
3. 选择 `drive` (Google Drive)
4. 按照提示完成OAuth授权
5. 重复以上步骤添加多个账号 (`gd2`, `gd3`, ...)

#### 验证配置
```bash
rclone listremotes
rclone lsd gd1:
```

## 数据库配置

### 1. 创建数据库
```bash
# 登录MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE gd_upload_manager DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 退出
exit
```

### 2. 导入表结构
```bash
# Windows
mysql -u root -p gd_upload_manager < F:\cluade\database\schema.sql

# Linux/Mac
mysql -u root -p gd_upload_manager < /path/to/database/schema.sql
```

### 3. 验证表结构
```bash
mysql -u root -p gd_upload_manager

SHOW TABLES;
```

应该看到以下表：
- gd_account
- upload_task
- file_info
- upload_record
- account_usage_stats
- system_log
- system_config

## 后端配置与启动

### 1. 修改配置文件

编辑 `backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/gd_upload_manager?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root          # 修改为你的MySQL用户名
    password: your_password # 修改为你的MySQL密码

app:
  rclone:
    path: C:/path/to/rclone.exe  # Windows: 修改为rclone.exe的完整路径
    # path: /usr/bin/rclone      # Linux/Mac: 通常是这个路径
    config-path: C:/Users/YourName/.config/rclone/rclone.conf  # Windows
    # config-path: ~/.config/rclone/rclone.conf                # Linux/Mac
```

### 2. 编译项目

```bash
cd F:\cluade\backend

# 清理并编译
mvn clean package -DskipTests
```

### 3. 启动后端服务

```bash
# 方式1: 使用Maven运行
mvn spring-boot:run

# 方式2: 运行jar包
java -jar target/gd-upload-manager-1.0.0.jar
```

### 4. 验证后端启动

访问以下地址：
- API文档: http://localhost:8080/api/doc.html
- Druid监控: http://localhost:8080/api/druid/index.html (用户名/密码: admin/admin)

看到以下输出表示启动成功：
```
========================================
  GD Upload Manager 启动成功!
  API文档地址: http://localhost:8080/api/doc.html
========================================
```

## 前端配置与启动

### 1. 安装依赖

```bash
cd F:\cluade\frontend

# 安装依赖
npm install

# 如果npm速度慢，可以使用淘宝镜像
npm install --registry=https://registry.npmmirror.com
```

### 2. 启动开发服务器

```bash
npm run dev
```

### 3. 访问前端

浏览器访问: http://localhost:3000

## 使用流程

### 1. 添加Google Drive账号

1. 点击左侧菜单 "账号管理"
2. 点击 "新增账号" 按钮
3. 填写账号信息：
   - 账号名称: 自定义名称，如 "账号1"
   - 账号邮箱: Google Drive邮箱
   - Rclone配置: 在rclone中配置的名称，如 "gd1"
   - 每日限制: 默认750GB
   - 优先级: 数字越大优先级越高
   - 状态: 启用
4. 点击 "确定" 保存

### 2. 创建上传任务

1. 点击左侧菜单 "任务管理"
2. 点击 "新建任务" 按钮
3. 填写任务信息：
   - 任务名称: 自定义任务名称
   - 源文件路径: 本地文件或文件夹路径，如 `D:\videos`
   - 目标路径: Google Drive目标路径，如 `/backup/videos`
   - 任务类型: 普通上传 或 增量上传
4. 点击 "确定" 创建任务

### 3. 选择上传文件

1. 在任务列表中点击 "选择文件"
2. 系统会扫描源路径下的所有文件
3. 勾选需要上传的文件
4. 点击 "确认选择"

### 4. 开始上传

1. 在任务列表中点击 "开始上传"
2. 系统自动选择可用账号开始上传
3. 实时查看上传进度和日志
4. 当账号达到750GB限制时，自动切换到下一个账号

### 5. 查看统计

1. 点击左侧菜单 "数据统计"
2. 查看总体上传情况
3. 查看各账号使用情况
4. 查看任务执行历史

### 6. 查看日志

1. 点击左侧菜单 "日志查看"
2. 查看详细的上传日志
3. 筛选错误日志
4. 导出日志报告

## 常见问题

### 1. 后端启动失败

**问题**: 数据库连接失败
```
Could not create connection to database server
```

**解决方案**:
- 检查MySQL是否启动
- 检查数据库用户名密码是否正确
- 检查数据库是否已创建
- 检查防火墙是否阻止连接

### 2. rclone命令执行失败

**问题**: 找不到rclone命令
```
Cannot run program "rclone"
```

**解决方案**:
- 检查rclone是否已安装
- 检查application.yml中的rclone路径是否正确
- Windows需要使用完整路径，如 `C:/rclone/rclone.exe`
- 确保应用有权限执行rclone

### 3. 前端无法连接后端

**问题**: 网络错误
```
Network Error
```

**解决方案**:
- 检查后端是否已启动
- 检查后端端口是否为8080
- 检查防火墙是否阻止连接
- 检查vite.config.js中的代理配置

### 4. 上传速度慢

**解决方案**:
- 调整并发传输数: 修改application.yml中的 `upload.concurrent-files`
- 调整rclone缓冲区: 修改 `rclone.buffer-size`
- 检查网络带宽
- 使用有线网络而非WiFi

### 5. 账号切换不生效

**解决方案**:
- 检查账号状态是否为"启用"
- 检查账号剩余配额是否充足
- 查看系统日志排查问题
- 手动重置账号配额

## 生产环境部署

### 1. 后端部署

```bash
# 编译生产版本
cd backend
mvn clean package -DskipTests

# 运行jar包
nohup java -jar target/gd-upload-manager-1.0.0.jar > app.log 2>&1 &
```

### 2. 前端部署

```bash
# 构建生产版本
cd frontend
npm run build

# 将dist目录部署到Nginx或其他Web服务器
```

### 3. Nginx配置示例

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端
    location / {
        root /path/to/frontend/dist;
        try_files $uri $uri/ /index.html;
    }

    # 后端API
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # WebSocket
    location /ws {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

## 性能优化建议

1. **数据库优化**
   - 定期清理过期日志
   - 为常用查询字段添加索引
   - 使用连接池管理数据库连接

2. **上传优化**
   - 根据网络带宽调整并发数
   - 使用SSD存储临时文件
   - 启用rclone的断点续传功能

3. **系统优化**
   - 增加JVM内存: `-Xmx2g -Xms2g`
   - 使用异步处理上传任务
   - 定期备份数据库

## 技术支持

如遇到问题，请查看：
1. 系统日志: `logs/gd-upload-manager.log`
2. 数据库日志: `system_log` 表
3. rclone日志: 上传任务的详细日志

需要帮助请提交Issue或查看README文档。
