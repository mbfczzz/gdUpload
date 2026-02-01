import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '数据统计' }
  },
  {
    path: '/account',
    name: 'Account',
    component: () => import('@/views/Account.vue'),
    meta: { title: '账号管理' }
  },
  {
    path: '/task',
    name: 'Task',
    component: () => import('@/views/Task.vue'),
    meta: { title: '任务管理' }
  },
  {
    path: '/upload',
    name: 'Upload',
    component: () => import('@/views/Upload.vue'),
    meta: { title: '文件上传' }
  },
  {
    path: '/log',
    name: 'Log',
    component: () => import('@/views/Log.vue'),
    meta: { title: '日志查看' }
  },
  {
    path: '/subscribe-search',
    name: 'SubscribeSearch',
    component: () => import('@/views/SubscribeSearch.vue'),
    meta: { title: '订阅搜索' }
  },
  {
    path: '/emby',
    name: 'EmbyManager',
    component: () => import('@/views/EmbyManager.vue'),
    meta: { title: 'Emby管理' }
  },
  {
    path: '/emby-config',
    name: 'EmbyConfig',
    component: () => import('@/views/EmbyConfig.vue'),
    meta: { title: 'Emby配置' }
  },
  {
    path: '/emby-login',
    name: 'EmbyLogin',
    component: () => import('@/views/EmbyLogin.vue'),
    meta: { title: 'Emby登录' }
  },
  {
    path: '/emby-test',
    name: 'EmbyTest',
    component: () => import('@/views/EmbyTest.vue'),
    meta: { title: 'Emby测试' }
  },
  {
    path: '/smart-search-config',
    name: 'SmartSearchConfig',
    component: () => import('@/views/SmartSearchConfig.vue'),
    meta: { title: '智能搜索配置' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title || 'GD上传管理系统'
  next()
})

export default router
