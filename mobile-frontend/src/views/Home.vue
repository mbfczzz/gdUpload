<template>
  <div class="home-page">
    <van-nav-bar title="GD云盘" fixed placeholder />

    <div class="content">
      <!-- 统计卡片 -->
      <van-grid :column-num="2" :border="false" class="stats-grid">
        <van-grid-item>
          <van-icon name="cloud-o" size="32" color="#007aff" />
          <div class="stat-value">{{ stats.totalAccounts }}</div>
          <div class="stat-label">账号总数</div>
        </van-grid-item>
        <van-grid-item>
          <van-icon name="checked" size="32" color="#34c759" />
          <div class="stat-value">{{ stats.activeAccounts }}</div>
          <div class="stat-label">可用账号</div>
        </van-grid-item>
        <van-grid-item>
          <van-icon name="clock-o" size="32" color="#ff9500" />
          <div class="stat-value">{{ stats.runningTasks }}</div>
          <div class="stat-label">运行中任务</div>
        </van-grid-item>
        <van-grid-item>
          <van-icon name="success" size="32" color="#34c759" />
          <div class="stat-value">{{ stats.completedTasks }}</div>
          <div class="stat-label">已完成任务</div>
        </van-grid-item>
      </van-grid>

      <!-- 快捷操作 -->
      <van-cell-group title="快捷操作" inset class="quick-actions">
        <van-cell title="任务管理" icon="orders-o" is-link to="/task" />
        <van-cell title="账号管理" icon="manager-o" is-link to="/account" />
        <van-cell title="Emby管理" icon="video-o" is-link to="/emby" />
      </van-cell-group>
    </div>

    <!-- 底部导航 -->
    <van-tabbar v-model="active" fixed placeholder>
      <van-tabbar-item icon="home-o" to="/home">首页</van-tabbar-item>
      <van-tabbar-item icon="orders-o" to="/task">任务</van-tabbar-item>
      <van-tabbar-item icon="manager-o" to="/account">账号</van-tabbar-item>
      <van-tabbar-item icon="video-o" to="/emby">Emby</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const active = ref(0)
const stats = ref({
  totalAccounts: 0,
  activeAccounts: 0,
  runningTasks: 0,
  completedTasks: 0
})

const loadStats = async () => {
  try {
    const { data } = await axios.get('/api/dashboard/stats')
    if (data.code === 200) {
      stats.value = data.data
    }
  } catch (error) {
    console.error('加载统计数据失败', error)
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 50px;
}

.content {
  padding: 16px;
}

.stats-grid {
  margin-bottom: 16px;
  background: white;
  border-radius: 12px;
  overflow: hidden;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #1d1d1f;
  margin-top: 8px;
}

.stat-label {
  font-size: 12px;
  color: #86868b;
  margin-top: 4px;
}

.quick-actions {
  margin-bottom: 16px;
}
</style>
