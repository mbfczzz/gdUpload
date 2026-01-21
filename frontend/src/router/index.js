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
