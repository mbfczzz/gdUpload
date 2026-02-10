# GD上传管理系统 - 移动端

专为手机和平板浏览器优化的移动端UI，使用 Vant 4 组件库构建。

## 特性

- 📱 专为移动端设计，完美适配 iPhone、iPad、Android 设备
- 🎨 基于 Vant 4 UI 组件库，Apple 风格设计
- 🚀 轻量快速，优化的移动端体验
- 📊 任务管理、账号管理、Emby管理等核心功能
- 🔄 下拉刷新、上拉加载更多
- 💾 路由缓存，页面状态保持

## 安装依赖

\`\`\`bash
cd mobile-frontend
npm install
\`\`\`

## 开发运行

\`\`\`bash
npm run dev
\`\`\`

访问: http://localhost:5174

## 生产构建

\`\`\`bash
npm run build
\`\`\`

构建产物在 `dist` 目录

## 部署

### 方式1: 使用 Nginx

\`\`\`nginx
server {
    listen 5174;
    server_name localhost;

    root /path/to/mobile-frontend/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
\`\`\`

### 方式2: 使用 Node.js 静态服务器

\`\`\`bash
npm install -g serve
serve -s dist -l 5174
\`\`\`

## 功能页面

- **首页** - 数据统计、快捷操作
- **任务管理** - 查看任务列表、控制任务、查看文件、修复路径
- **账号管理** - 查看账号状态、配额使用情况
- **Emby管理** - 浏览媒体库、下载媒体

## 技术栈

- Vue 3 - 渐进式 JavaScript 框架
- Vant 4 - 移动端 Vue 组件库
- Vue Router 4 - 路由管理
- Axios - HTTP 客户端
- Vite - 构建工具

## 浏览器支持

- iOS Safari 10+
- Android Chrome 51+
- 现代移动浏览器

## 注意事项

1. 移动端项目运行在 **5174** 端口，与桌面端（5173）分离
2. API 请求会自动代理到后端 8080 端口
3. 建议在真实移动设备上测试体验
4. 支持 PWA，可添加到主屏幕

## 与桌面端的区别

| 特性 | 桌面端 | 移动端 |
|------|--------|--------|
| UI 框架 | Element Plus | Vant 4 |
| 端口 | 5173 | 5174 |
| 布局 | 侧边栏+主内容 | 底部导航 |
| 交互 | 鼠标点击 | 触摸操作 |
| 适配 | 大屏优先 | 小屏优先 |

## 开发建议

- 使用 Chrome DevTools 的移动设备模拟器测试
- 推荐在真实设备上测试触摸交互
- 注意触摸目标大小（最小 44x44px）
- 优化图片加载和网络请求
