<template>
  <div class="dashboard-container">
    <el-row :gutter="16">
      <!-- 统计卡片 -->
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #007aff;">
              <el-icon :size="30"><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalAccounts }}</div>
              <div class="stat-label">总账号数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #34c759;">
              <el-icon :size="30"><CircleCheck /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.availableAccounts }}</div>
              <div class="stat-label">可用账号</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #ff9500;">
              <el-icon :size="30"><List /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.runningTasks }}</div>
              <div class="stat-label">运行中任务</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #ff3b30;">
              <el-icon :size="30"><Upload /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatSize(stats.todayUpload) }}</div>
              <div class="stat-label">今日上传</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px;">
      <el-col :span="24">
        <el-card v-loading="loading">
          <template #header>
            <div class="card-header">
              <span>系统概览</span>
              <div>
                <el-button type="danger" size="small" @click="cleanupFiles" :loading="cleanupLoading">
                  <el-icon><Delete /></el-icon>
                  清理已上传文件
                </el-button>
                <el-button type="primary" size="small" @click="loadStats" style="margin-left: 10px;">
                  <el-icon><Refresh /></el-icon>
                  刷新
                </el-button>
              </div>
            </div>
          </template>
          <el-row :gutter="16">
            <el-col :span="12">
              <div class="overview-item">
                <div class="overview-label">总任务数</div>
                <div class="overview-value">{{ stats.totalTasks || 0 }}</div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="overview-item">
                <div class="overview-label">账号使用率</div>
                <div class="overview-value">
                  {{ stats.totalAccounts > 0 ? ((stats.availableAccounts / stats.totalAccounts) * 100).toFixed(1) : 0 }}%
                </div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getStats } from '@/api/dashboard'
import { cleanupUploadedFiles } from '@/api/fileCleanup'
import { ElMessage, ElMessageBox } from 'element-plus'

const stats = reactive({
  totalAccounts: 0,
  availableAccounts: 0,
  runningTasks: 0,
  todayUpload: 0
})

const loading = ref(false)
const cleanupLoading = ref(false)

const formatSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

const loadStats = async () => {
  loading.value = true
  try {
    const res = await getStats()
    if (res.code === 200) {
      stats.totalAccounts = res.data.totalAccounts || 0
      stats.availableAccounts = res.data.availableAccounts || 0
      stats.runningTasks = res.data.runningTasks || 0
      stats.todayUpload = res.data.todayUploadSize || 0
    } else {
      ElMessage.error(res.message || '加载统计数据失败')
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
    ElMessage.error('加载统计数据失败')
  } finally {
    loading.value = false
  }
}

const cleanupFiles = async () => {
  try {
    await ElMessageBox.confirm(
      '此操作将删除所有已上传成功的文件物理文件，释放服务器磁盘空间。确认继续吗？',
      '清理已上传文件',
      {
        confirmButtonText: '确认清理',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    cleanupLoading.value = true
    const res = await cleanupUploadedFiles()

    if (res.code === 200) {
      const data = res.data
      ElMessage.success(
        `清理完成！成功删除 ${data.successCount} 个文件，释放空间 ${data.deletedSizeFormatted}`
      )
    } else {
      ElMessage.error(res.message || '清理失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('清理文件失败:', error)
      ElMessage.error('清理文件失败')
    }
  } finally {
    cleanupLoading.value = false
  }
}

onMounted(() => {
  loadStats()
  // 每30秒刷新一次数据
  setInterval(loadStats, 30000)
})
</script>

<style scoped>
.dashboard-container {
  height: 100%;
}

.stat-card {
  cursor: pointer;
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.stat-content {
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-right: 20px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.overview-item {
  padding: 20px;
  text-align: center;
  background: #f5f5f7;
  border-radius: 8px;
}

.overview-label {
  font-size: 14px;
  color: #86868b;
  margin-bottom: 8px;
}

.overview-value {
  font-size: 24px;
  font-weight: 600;
  color: #1d1d1f;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .stat-card {
    margin-bottom: 16px;
  }

  .stat-value {
    font-size: 24px;
  }

  .stat-icon {
    width: 50px;
    height: 50px;
  }

  .overview-item {
    padding: 16px;
  }

  .overview-value {
    font-size: 20px;
  }
}
</style>
