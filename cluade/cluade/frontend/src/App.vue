<template>
  <div id="app">
    <el-container class="layout-container">
      <!-- 侧边栏 -->
      <el-aside width="240px" class="sidebar">
        <div class="logo">
          <el-icon :size="28" class="logo-icon"><CloudUpload /></el-icon>
          <span class="logo-text">GD云盘</span>
        </div>

        <el-menu
          :default-active="activeMenu"
          router
          class="sidebar-menu"
        >
          <el-menu-item index="/dashboard" class="menu-item">
            <el-icon class="menu-icon"><DataAnalysis /></el-icon>
            <span>数据统计</span>
          </el-menu-item>
          <el-menu-item index="/account" class="menu-item">
            <el-icon class="menu-icon"><User /></el-icon>
            <span>账号管理</span>
          </el-menu-item>
          <el-menu-item index="/task" class="menu-item">
            <el-icon class="menu-icon"><List /></el-icon>
            <span>任务管理</span>
          </el-menu-item>
          <el-menu-item index="/upload" class="menu-item">
            <el-icon class="menu-icon"><Upload /></el-icon>
            <span>文件上传</span>
          </el-menu-item>
          <el-menu-item index="/log" class="menu-item">
            <el-icon class="menu-icon"><Document /></el-icon>
            <span>日志查看</span>
          </el-menu-item>
        </el-menu>

        <div class="sidebar-footer">
          <div class="version-info">版本 1.0.0</div>
        </div>
      </el-aside>

      <!-- 主内容区 -->
      <el-container class="main-container">
        <!-- 顶部导航 -->
        <el-header class="header">
          <div class="header-content">
            <div class="header-left">
              <h3 class="page-title">{{ currentPageTitle }}</h3>
            </div>
            <div class="header-right">
              <div class="user-info">
                <el-avatar :size="36" class="user-avatar">
                  <el-icon><User /></el-icon>
                </el-avatar>
                <span class="user-name">管理员</span>
              </div>
            </div>
          </div>
        </el-header>

        <!-- 内容区 -->
        <el-main class="main-content">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const activeMenu = computed(() => route.path)

const currentPageTitle = computed(() => {
  const titles = {
    '/dashboard': '数据统计',
    '/account': '账号管理',
    '/task': '任务管理',
    '/upload': '文件上传',
    '/log': '日志查看'
  }
  return titles[route.path] || '首页'
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
  background: #f5f5f7;
}

/* 侧边栏样式 */
.sidebar {
  background: #ffffff;
  border-right: 1px solid #e5e5e7;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 80px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  border-bottom: 1px solid #e5e5e7;
}

.logo-icon {
  color: #007aff;
}

.logo-text {
  font-size: 20px;
  font-weight: 600;
  color: #1d1d1f;
  letter-spacing: -0.5px;
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  padding: 12px;
  background: transparent;
}

.menu-item {
  border-radius: 8px;
  margin-bottom: 4px;
  height: 44px;
  line-height: 44px;
  transition: all 0.2s ease;
}

.menu-item:hover {
  background: #f5f5f7;
}

.menu-item.is-active {
  background: #007aff;
  color: #ffffff;
}

.menu-item.is-active .menu-icon {
  color: #ffffff;
}

.menu-icon {
  font-size: 18px;
  color: #86868b;
  margin-right: 8px;
}

.menu-item.is-active .menu-icon {
  color: #ffffff;
}

.sidebar-footer {
  padding: 20px;
  border-top: 1px solid #e5e5e7;
}

.version-info {
  text-align: center;
  color: #86868b;
  font-size: 12px;
}

/* 主容器 */
.main-container {
  background: #f5f5f7;
}

/* 顶部导航样式 */
.header {
  background: #ffffff;
  border-bottom: 1px solid #e5e5e7;
  padding: 0 32px;
  height: 80px;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
}

.page-title {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
  color: #1d1d1f;
  letter-spacing: -0.5px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 12px 6px 6px;
  background: #f5f5f7;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.user-info:hover {
  background: #e8e8ed;
}

.user-avatar {
  background: #007aff;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #1d1d1f;
}

/* 内容区样式 */
.main-content {
  padding: 24px 32px;
  overflow-y: auto;
}

/* 页面切换动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 滚动条样式 */
.main-content::-webkit-scrollbar {
  width: 8px;
}

.main-content::-webkit-scrollbar-track {
  background: transparent;
}

.main-content::-webkit-scrollbar-thumb {
  background: #d1d1d6;
  border-radius: 4px;
}

.main-content::-webkit-scrollbar-thumb:hover {
  background: #b0b0b5;
}

/* 响应式设计 - 手机端 */
@media (max-width: 768px) {
  .sidebar {
    width: 200px !important;
  }

  .logo {
    padding: 0 16px;
  }

  .logo-text {
    font-size: 18px;
  }

  .sidebar-menu {
    padding: 8px;
  }

  .header {
    padding: 0 16px;
    height: 64px;
  }

  .page-title {
    font-size: 22px;
  }

  .main-content {
    padding: 16px;
  }

  .user-name {
    display: none;
  }
}

@media (max-width: 480px) {
  .sidebar {
    width: 60px !important;
  }

  .logo-text {
    display: none;
  }

  .menu-item span {
    display: none;
  }

  .sidebar-footer {
    padding: 12px;
  }

  .version-info {
    font-size: 10px;
  }
}
</style>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

#app {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* Element Plus 组件自定义样式 - Apple风格 */
.el-card {
  border-radius: 12px;
  border: 1px solid #e5e5e7;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.2s ease;
  background: #ffffff;
}

.el-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.el-button {
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.2s ease;
  border: none;
}

.el-button--primary {
  background: #007aff;
  color: #ffffff;
}

.el-button--primary:hover {
  background: #0051d5;
}

.el-button--success {
  background: #34c759;
  color: #ffffff;
}

.el-button--success:hover {
  background: #2da84a;
}

.el-button--warning {
  background: #ff9500;
  color: #ffffff;
}

.el-button--warning:hover {
  background: #e08600;
}

.el-button--danger {
  background: #ff3b30;
  color: #ffffff;
}

.el-button--danger:hover {
  background: #e0342a;
}

.el-button--info {
  background: #f5f5f7;
  color: #1d1d1f;
}

.el-button--info:hover {
  background: #e8e8ed;
}

.el-input__wrapper {
  border-radius: 8px;
  background: #f5f5f7;
  box-shadow: none;
  border: 1px solid #e5e5e7;
  transition: all 0.2s ease;
}

.el-input__wrapper:hover {
  border-color: #d1d1d6;
}

.el-input__wrapper.is-focus {
  border-color: #007aff;
  background: #ffffff;
}

.el-input__inner {
  color: #1d1d1f;
}

.el-input__inner::placeholder {
  color: #86868b;
}

.el-table {
  border-radius: 12px;
  overflow: hidden;
  background: #ffffff;
  border: 1px solid #e5e5e7;
}

.el-table th {
  background: #f5f5f7;
  font-weight: 600;
  color: #1d1d1f;
  border-bottom: 1px solid #e5e5e7;
}

.el-table tr:hover {
  background: #f5f5f7;
}

.el-table td {
  border-bottom: 1px solid #f5f5f7;
  color: #1d1d1f;
}

.el-pagination {
  justify-content: center;
  margin-top: 24px;
}

.el-pagination .el-pager li {
  border-radius: 6px;
  transition: all 0.2s ease;
  font-weight: 500;
  background: transparent;
  color: #1d1d1f;
}

.el-pagination .el-pager li:hover {
  background: #f5f5f7;
}

.el-pagination .el-pager li.is-active {
  background: #007aff;
  color: #ffffff;
}

.el-tag {
  border-radius: 6px;
  font-weight: 500;
  border: none;
}

.el-tag--success {
  background: #34c759;
  color: #ffffff;
}

.el-tag--warning {
  background: #ff9500;
  color: #ffffff;
}

.el-tag--danger {
  background: #ff3b30;
  color: #ffffff;
}

.el-tag--info {
  background: #007aff;
  color: #ffffff;
}

.el-progress__text {
  font-weight: 600;
  color: #1d1d1f;
}

.el-dialog {
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  background: #ffffff;
  border: 1px solid #e5e5e7;
}

.el-dialog__header {
  background: #f5f5f7;
  padding: 20px 24px;
  border-radius: 12px 12px 0 0;
  border-bottom: 1px solid #e5e5e7;
}

.el-dialog__title {
  color: #1d1d1f;
  font-weight: 600;
  font-size: 18px;
}

.el-dialog__headerbtn .el-dialog__close {
  color: #86868b;
  font-size: 18px;
}

.el-dialog__body {
  color: #1d1d1f;
  padding: 24px;
}

/* 响应式表格 */
@media (max-width: 768px) {
  .el-table {
    font-size: 12px;
  }

  .el-table th,
  .el-table td {
    padding: 8px 4px;
  }
}
</style>
