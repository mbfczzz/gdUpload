<template>
  <div class="task-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="任务名称">
          <el-input v-model="searchForm.keyword" placeholder="请输入任务名称" clearable />
        </el-form-item>
        <el-form-item label="任务状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="全部" :value="null" />
            <el-option label="待开始" :value="0" />
            <el-option label="上传中" :value="1" />
            <el-option label="已完成" :value="2" />
            <el-option label="已暂停" :value="3" />
            <el-option label="已取消" :value="4" />
            <el-option label="失败" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">任务列表</span>
          <div>
            <el-button type="danger" :disabled="selectedIds.length === 0" @click="handleBatchDelete">
              <el-icon><Delete /></el-icon>
              批量删除
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="tableData"
        style="width: 100%"
        @selection-change="handleSelectionChange"
        v-loading="loading"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="taskName" label="任务名称" min-width="150" />
        <el-table-column prop="sourcePath" label="源路径" min-width="200" show-overflow-tooltip />
        <el-table-column prop="targetPath" label="目标路径" min-width="200" show-overflow-tooltip />
        <el-table-column label="进度" width="200">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :status="getProgressStatus(row.status)" />
          </template>
        </el-table-column>
        <el-table-column label="文件数" width="180">
          <template #default="{ row }">
            <div>
              <span style="color: #67c23a;">成功: {{ row.uploadedCount }}</span>
              <span v-if="row.failedCount > 0" style="color: #f56c6c; margin-left: 8px;">失败: {{ row.failedCount }}</span>
              <span style="color: #909399; margin-left: 8px;">/ {{ row.totalCount }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="150">
          <template #default="{ row }">
            {{ formatSize(row.uploadedSize) }} / {{ formatSize(row.totalSize) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button
                v-if="row.status === 0 || row.status === 3"
                type="primary"
                size="small"
                @click="handleStart(row.id)"
              >
                <el-icon><VideoPlay /></el-icon>
                启动
              </el-button>
              <el-button
                v-if="row.status === 1"
                type="warning"
                size="small"
                @click="handlePause(row.id)"
              >
                <el-icon><VideoPause /></el-icon>
                暂停
              </el-button>
              <el-button
                v-if="row.status === 5 || (row.status === 2 && row.failedCount > 0)"
                type="success"
                size="small"
                @click="handleRetry(row.id)"
              >
                <el-icon><RefreshRight /></el-icon>
                重试
              </el-button>
              <el-button
                v-if="row.status === 1"
                type="danger"
                size="small"
                @click="handleCancel(row.id)"
              >
                <el-icon><Close /></el-icon>
                取消
              </el-button>
              <el-button
                type="info"
                size="small"
                @click="handleViewFiles(row)"
              >
                <el-icon><Document /></el-icon>
                文件
              </el-button>
              <el-button
                type="danger"
                size="small"
                @click="handleDelete(row.id)"
              >
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>

    <!-- 文件列表对话框 -->
    <el-dialog
      v-model="fileDialogVisible"
      title="任务文件列表"
      width="80%"
      :close-on-click-modal="false"
    >
      <el-table :data="fileList" style="width: 100%" max-height="500">
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="filePath" label="文件路径" min-width="250" show-overflow-tooltip />
        <el-table-column label="文件大小" width="120">
          <template #default="{ row }">
            {{ formatSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getFileStatusType(row.status)">
              {{ getFileStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="uploadEndTime" label="上传时间" width="180" />
        <el-table-column prop="errorMessage" label="错误信息" min-width="150" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search,
  Refresh,
  VideoPlay,
  VideoPause,
  RefreshRight,
  Close,
  Document,
  Delete
} from '@element-plus/icons-vue'
import axios from 'axios'
import websocketClient from '@/utils/websocket'

const loading = ref(false)
const tableData = ref([])
const selectedIds = ref([])
const fileDialogVisible = ref(false)
const fileList = ref([])
const taskSubscriptions = ref([])

const searchForm = reactive({
  keyword: '',
  status: null
})

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 初始化WebSocket连接
const initWebSocket = async () => {
  try {
    await websocketClient.connect()
    console.log('WebSocket连接成功')

    // 订阅所有任务的更新
    const allTasksSubId = websocketClient.subscribe('/topic/tasks', (message) => {
      console.log('收到任务更新:', message)
      handleTaskUpdate(message)
    })

    if (allTasksSubId) {
      taskSubscriptions.value.push(allTasksSubId)
    }
  } catch (error) {
    console.error('WebSocket连接失败:', error)
  }
}

// 订阅单个任务的实时更新
const subscribeTask = (taskId) => {
  if (!websocketClient.isConnected()) {
    return
  }

  const subId = websocketClient.subscribe(`/topic/task/${taskId}`, (message) => {
    console.log(`收到任务 ${taskId} 的更新:`, message)
    handleTaskUpdate(message)
  })

  if (subId) {
    taskSubscriptions.value.push(subId)
  }
}

// 处理任务更新
const handleTaskUpdate = (message) => {
  const { taskId, type, data } = message

  // 查找对应的任务
  const taskIndex = tableData.value.findIndex(task => task.id === taskId)
  if (taskIndex === -1) {
    return
  }

  const task = tableData.value[taskIndex]

  // 根据消息类型更新任务数据
  if (type === 'PROGRESS') {
    // 进度更新
    task.progress = data.progress || 0
    task.uploadedCount = data.uploadedCount || 0
    task.totalCount = data.totalCount || 0
    task.uploadedSize = data.uploadedSize || 0
    task.totalSize = data.totalSize || 0
    task.currentFileName = data.currentFileName || ''
  } else if (type === 'STATUS') {
    // 状态更新
    task.status = data.status
    if (data.message) {
      ElMessage.info(data.message)
    }
  } else if (type === 'FILE_STATUS') {
    // 文件状态更新（如果文件列表对话框打开，更新文件列表）
    if (fileDialogVisible.value && fileList.value.length > 0) {
      const fileIndex = fileList.value.findIndex(f => f.id === data.fileId)
      if (fileIndex !== -1) {
        fileList.value[fileIndex].status = data.status
        if (data.message) {
          fileList.value[fileIndex].errorMessage = data.message
        }
      }
    }
  }

  // 触发响应式更新
  tableData.value[taskIndex] = { ...task }
}

// 清理WebSocket订阅
const cleanupWebSocket = () => {
  // 取消所有订阅
  taskSubscriptions.value.forEach(subId => {
    websocketClient.unsubscribe(subId)
  })
  taskSubscriptions.value = []

  // 断开连接
  websocketClient.disconnect()
}

// 获取任务列表
const getTaskList = async () => {
  loading.value = true
  try {
    const { data } = await axios.get('/api/task/page', {
      params: {
        current: pagination.current,
        size: pagination.size,
        keyword: searchForm.keyword,
        status: searchForm.status
      }
    })

    if (data.code === 200) {
      tableData.value = data.data.records
      pagination.total = data.data.total

      // 为所有运行中的任务订阅实时更新
      tableData.value.forEach(task => {
        if (task.status === 1) { // 上传中
          subscribeTask(task.id)
        }
      })
    }
  } catch (error) {
    ElMessage.error('获取任务列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  getTaskList()
}

// 重置
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.status = null
  pagination.current = 1
  getTaskList()
}

// 启动任务
const handleStart = async (id) => {
  try {
    const { data } = await axios.put(`/api/task/${id}/start`)
    if (data.code === 200) {
      ElMessage.success('任务已启动')
      subscribeTask(id) // 订阅该任务的实时更新
      getTaskList()
    } else {
      ElMessage.error(data.message)
    }
  } catch (error) {
    ElMessage.error('启动任务失败')
  }
}

// 暂停任务
const handlePause = async (id) => {
  try {
    const { data } = await axios.put(`/api/task/${id}/pause`)
    if (data.code === 200) {
      ElMessage.success('任务已暂停')
      getTaskList()
    } else {
      ElMessage.error(data.message)
    }
  } catch (error) {
    ElMessage.error('暂停任务失败')
  }
}

// 取消任务
const handleCancel = async (id) => {
  try {
    await ElMessageBox.confirm('确定要取消该任务吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const { data } = await axios.put(`/api/task/${id}/cancel`)
    if (data.code === 200) {
      ElMessage.success('任务已取消')
      getTaskList()
    } else {
      ElMessage.error(data.message)
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('取消任务失败')
    }
  }
}

// 重试任务
const handleRetry = async (id) => {
  try {
    const { data } = await axios.put(`/api/task/${id}/retry`)
    if (data.code === 200) {
      ElMessage.success('任务已重试')
      subscribeTask(id) // 订阅该任务的实时更新
      getTaskList()
    } else {
      ElMessage.error(data.message)
    }
  } catch (error) {
    ElMessage.error('重试任务失败')
  }
}

// 查看文件列表
const handleViewFiles = async (row) => {
  try {
    const { data } = await axios.get(`/api/task/${row.id}/files`)
    if (data.code === 200) {
      fileList.value = data.data
      fileDialogVisible.value = true
    } else {
      ElMessage.error(data.message)
    }
  } catch (error) {
    ElMessage.error('获取文件列表失败')
  }
}

// 删除任务
const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该任务吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const { data } = await axios.delete(`/api/task/${id}`)
    if (data.code === 200) {
      ElMessage.success('删除成功')
      getTaskList()
    } else {
      ElMessage.error(data.message)
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 批量删除
const handleBatchDelete = async () => {
  try {
    await ElMessageBox.confirm('确定要删除选中的任务吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const { data } = await axios.delete('/api/task/batch', {
      data: selectedIds.value
    })

    if (data.code === 200) {
      ElMessage.success('删除成功')
      selectedIds.value = []
      getTaskList()
    } else {
      ElMessage.error(data.message)
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 选择变化
const handleSelectionChange = (selection) => {
  selectedIds.value = selection.map(item => item.id)
}

// 分页大小变化
const handleSizeChange = () => {
  getTaskList()
}

// 当前页变化
const handleCurrentChange = () => {
  getTaskList()
}

// 获取状态类型
const getStatusType = (status) => {
  const types = {
    0: 'info',
    1: 'primary',
    2: 'success',
    3: 'warning',
    4: 'info',
    5: 'danger'
  }
  return types[status] || 'info'
}

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    0: '待开始',
    1: '上传中',
    2: '已完成',
    3: '已暂停',
    4: '已取消',
    5: '失败'
  }
  return texts[status] || '未知'
}

// 获取进度条状态
const getProgressStatus = (status) => {
  if (status === 2) return 'success'
  if (status === 5) return 'exception'
  return ''
}

// 获取文件状态类型
const getFileStatusType = (status) => {
  const types = {
    0: 'info',
    1: 'primary',
    2: 'success',
    3: 'danger'
  }
  return types[status] || 'info'
}

// 获取文件状态文本
const getFileStatusText = (status) => {
  const texts = {
    0: '待上传',
    1: '上传中',
    2: '已上传',
    3: '失败'
  }
  return texts[status] || '未知'
}

// 格式化文件大小
const formatSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

onMounted(() => {
  initWebSocket()
  getTaskList()
})

onUnmounted(() => {
  cleanupWebSocket()
})
</script>

<style scoped>
.task-container {
  height: 100%;
}

.search-card {
  margin-bottom: 16px;
}

.table-card {
  height: calc(100% - 130px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #1d1d1f;
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.action-buttons .el-button {
  margin: 0;
}

/* 响应式设计 */
@media (max-width: 1400px) {
  .action-buttons {
    flex-direction: column;
    align-items: flex-start;
  }

  .action-buttons .el-button {
    width: 100%;
  }
}
</style>
