import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/home'
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/task',
    name: 'Task',
    component: () => import('../views/Task.vue'),
    meta: { title: '任务管理' }
  },
  {
    path: '/account',
    name: 'Account',
    component: () => import('../views/Account.vue'),
    meta: { title: '账号管理' }
  },
  {
    path: '/emby',
    name: 'Emby',
    component: () => import('../views/Emby.vue'),
    meta: { title: 'Emby管理' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title || 'GD云盘'
  next()
})

export default router
