<template>
  <div class="task-page">
    <van-nav-bar title="任务管理" fixed placeholder />

    <div class="content">
      <!-- 搜索栏 -->
      <van-search
        v-model="searchKeyword"
        placeholder="搜索任务名称"
        @search="onSearch"
      />

      <!-- 筛选 -->
      <van-dropdown-menu>
        <van-dropdown-item v-model="statusFilter" :options="statusOptions" @change="onSearch" />
      </van-dropdown-menu>

      <!-- 任务列表 -->
      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list
          v-model:loading="loading"
          :finished="finished"
          finished-text="没有更多了"
          @load="onLoad"
        >
          <van-card
            v-for="task in taskList"
            :key="task.id"
            class="task-card"
          >
            <template #title>
              <div class="task-title">
                {{ task.taskName }}
                <van-tag :type="getTaskTypeTag(task.taskType)">
                  {{ task.taskType === 3 ? 'Emby下载' : '上传' }}
                </van-tag>
              </div>
            </template>

            <template #desc>
              <div class="task-info">
                <div class="info-row">
                  <van-icon name="folder-o" />
                  <span>{{ task.sourcePath }}</span>
                </div>
                <div class="info-row">
                  <van-icon name="arrow" />
                  <span>{{ task.targetPath }}</span>
                </div>
              </div>
            </template>

            <template #tags>
              <van-tag :type="getStatusType(task.status)">
                {{ getStatusText(task.status, task.taskType) }}
              </van-tag>
            </template>

            <template #footer>
              <!-- 进度条 -->
              <van-progress
                :percentage="task.progress"
                :show-pivot="false"
                :color="getProgressColor(task.status)"
                class="task-progress"
              />

              <!-- 文件统计 -->
              <div class="task-stats">
                <span class="success-count">成功: {{ task.uploadedCount }}</span>
                <span v-if="task.failedCount > 0" class="failed-count">失败: {{ task.failedCount }}</span>
                <span class="total-count">/ {{ task.totalCount }}</span>
              </div>

              <!-- 操作按钮 -->
              <div class="task-actions">
                <van-button
                  v-if="(task.status === 0 || task.status === 3) && task.taskType !== 3"
                  size="small"
                  type="primary"
                  icon="play-circle-o"
                  @click="handleStart(task.id)"
                >
                  启动
                </van-button>
                <van-button
                  v-if="task.status === 1"
                  size="small"
                  type="warning"
                  icon="pause-circle-o"
                  @click="handlePause(task.id)"
                >
                  暂停
                </van-button>
                <van-button
                  v-if="task.status === 1"
                  size="small"
                  type="danger"
                  icon="close"
                  @click="handleCancel(task.id)"
                >
                  取消
                </van-button>
                <van-button
                  size="small"
                  icon="description"
                  @click="handleViewFiles(task)"
                >
                  文件
                </van-button>
                <van-button
                  size="small"
                  type="warning"
                  icon="setting-o"
                  @click="handleFixPath(task.id)"
                >
                  修复
                </van-button>
                <van-button
                  size="small"
                  type="danger"
                  icon="delete-o"
                  @click="handleDelete(task.id)"
                >
                  删除
                </van-button>
              </div>
            </template>
          </van-card>
        </van-list>
      </van-pull-refresh>
    </div>

    <!-- 文件列表弹窗 -->
    <van-popup
      v-model:show="showFilePopup"
      position="bottom"
      :style="{ height: '70%' }"
      round
    >
      <div class="file-popup">
        <div class="popup-header">
          <h3>{{ currentTaskType === 3 ? '下载文件列表' : '任务文件列表' }}</h3>
          <van-icon name="cross" @click="showFilePopup = false" />
        </div>
        <van-list class="file-list">
          <van-cell
            v-for="file in fileList"
            :key="file.id"
            :title="file.fileName"
            :label="formatSize(file.fileSize)"
          >
            <template #right-icon>
              <van-tag :type="getFileStatusType(file.status)">
                {{ getFileStatusText(file.status) }}
              </van-tag>
            </template>
          </van-cell>
        </van-list>
      </div>
    </van-popup>

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
import { ref, reactive } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { getTaskList, startTask, pauseTask, cancelTask, deleteTask, getTaskFiles, fixFilePath } from '@/api/task'

const active = ref(1)
const searchKeyword = ref('')
const statusFilter = ref(null)
const refreshing = ref(false)
const loading = ref(false)
const finished = ref(false)
const taskList = ref([])
const showFilePopup = ref(false)
const fileList = ref([])
const currentTaskType = ref(null)

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

const statusOptions = [
  { text: '全部状态', value: null },
  { text: '待开始', value: 0 },
  { text: '上传中', value: 1 },
  { text: '已完成', value: 2 },
  { text: '已暂停', value: 3 },
  { text: '已取消', value: 4 },
  { text: '失败', value: 5 }
]

const loadTaskList = async () => {
  try {
    const { data } = await getTaskList({
      current: pagination.current,
      size: pagination.size,
      keyword: searchKeyword.value,
      status: statusFilter.value
    })

    if (pagination.current === 1) {
      taskList.value = data.data.records
    } else {
      taskList.value.push(...data.data.records)
    }

    pagination.total = data.data.total
    loading.value = false
    refreshing.value = false

    if (taskList.value.length >= pagination.total) {
      finished.value = true
    }
  } catch (error) {
    loading.value = false
    refreshing.value = false
    showToast('加载失败')
  }
}

const onLoad = () => {
  pagination.current++
  loadTaskList()
}

const onRefresh = () => {
  pagination.current = 1
  finished.value = false
  loadTaskList()
}

const onSearch = () => {
  pagination.current = 1
  finished.value = false
  taskList.value = []
  loadTaskList()
}

const handleStart = async (id) => {
  try {
    await startTask(id)
    showToast('任务已启动')
    onRefresh()
  } catch (error) {
    showToast('启动失败')
  }
}

const handlePause = async (id) => {
  try {
    await pauseTask(id)
    showToast('任务已暂停')
    onRefresh()
  } catch (error) {
    showToast('暂停失败')
  }
}

const handleCancel = async (id) => {
  try {
    await showConfirmDialog({
      title: '确认取消',
      message: '确定要取消该任务吗？'
    })
    await cancelTask(id)
    showToast('任务已取消')
    onRefresh()
  } catch (error) {
    if (error !== 'cancel') {
      showToast('取消失败')
    }
  }
}

const handleDelete = async (id) => {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: '确定要删除该任务吗？'
    })
    await deleteTask(id)
    showToast('删除成功')
    onRefresh()
  } catch (error) {
    if (error !== 'cancel') {
      showToast('删除失败')
    }
  }
}

const handleViewFiles = async (task) => {
  try {
    currentTaskType.value = task.taskType
    const { data } = await getTaskFiles(task.id)
    fileList.value = data.data
    showFilePopup.value = true
  } catch (error) {
    showToast('获取文件列表失败')
  }
}

const handleFixPath = async (id) => {
  try {
    const { data } = await fixFilePath(id)
    const result = data.data
    showToast(`修复完成！更新: ${result.updatedCount}, 跳过: ${result.skippedCount}`)
    onRefresh()
  } catch (error) {
    showToast('修复失败')
  }
}

const getTaskTypeTag = (type) => {
  return type === 3 ? 'warning' : 'primary'
}

const getStatusType = (status) => {
  const types = { 0: 'default', 1: 'primary', 2: 'success', 3: 'warning', 4: 'default', 5: 'danger' }
  return types[status] || 'default'
}

const getStatusText = (status, taskType) => {
  if (taskType === 3) {
    const texts = { 0: '待下载', 1: '下载中', 2: '已完成', 3: '已暂停', 4: '已取消', 5: '失败' }
    return texts[status] || '未知'
  }
  const texts = { 0: '待开始', 1: '上传中', 2: '已完成', 3: '已暂停', 4: '已取消', 5: '失败' }
  return texts[status] || '未知'
}

const getProgressColor = (status) => {
  if (status === 2) return '#34c759'
  if (status === 5) return '#ff3b30'
  return '#007aff'
}

const getFileStatusType = (status) => {
  const types = { 0: 'default', 1: 'primary', 2: 'success', 3: 'danger', 4: 'warning' }
  return types[status] || 'default'
}

const getFileStatusText = (status) => {
  if (currentTaskType.value === 3) {
    const texts = { 0: '待下载', 1: '下载中', 2: '已下载', 3: '失败', 4: '跳过' }
    return texts[status] || '未知'
  }
  const texts = { 0: '待上传', 1: '上传中', 2: '已上传', 3: '失败' }
  return texts[status] || '未知'
}

const formatSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

// 初始加载
loadTaskList()
</script>

<style scoped>
.task-page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 50px;
}

.content {
  padding-bottom: 16px;
}

.task-card {
  margin: 12px 16px;
  border-radius: 12px;
  overflow: hidden;
}

.task-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.task-info {
  margin-top: 8px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #86868b;
  margin-bottom: 4px;
}

.task-progress {
  margin: 12px 0 8px;
}

.task-stats {
  font-size: 12px;
  margin-bottom: 12px;
}

.success-count {
  color: #34c759;
  margin-right: 8px;
}

.failed-count {
  color: #ff3b30;
  margin-right: 8px;
}

.total-count {
  color: #86868b;
}

.task-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.task-actions .van-button {
  flex: 0 0 auto;
}

.file-popup {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e5e5e7;
}

.popup-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.file-list {
  flex: 1;
  overflow-y: auto;
}
</style>
