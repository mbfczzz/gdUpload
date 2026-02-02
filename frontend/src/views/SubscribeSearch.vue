<template>
  <div class="subscribe-search-container">
    <!-- 单个搜索卡片 -->
    <el-card class="search-card">
      <template #header>
        <div class="card-header">
          <span>单个订阅搜索</span>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-row :gutter="16" class="search-bar">
        <el-col :span="8">
          <el-input
            v-model="searchId"
            placeholder="请输入订阅ID"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-col>
        <el-col :span="16">
          <el-button type="primary" @click="handleSearch" :loading="loading">
            <el-icon><Search /></el-icon>
            搜索订阅
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-col>
      </el-row>

      <!-- 搜索结果 -->
      <div v-if="searchResult" class="result-container">
        <el-divider content-position="left">
          <el-icon><InfoFilled /></el-icon>
          订阅信息
        </el-divider>

        <!-- 使用 JSON 格式化显示结果 -->
        <el-card class="result-card" shadow="never">
          <pre class="json-result">{{ formatJson(searchResult) }}</pre>
        </el-card>
      </div>

      <!-- 空状态 -->
      <el-empty
        v-if="!searchResult && !loading && searched"
        description="未找到订阅信息"
        :image-size="200"
      />

      <!-- 初始状态提示 -->
      <el-empty
        v-if="!searchResult && !loading && !searched"
        description="请输入订阅ID进行搜索"
        :image-size="200"
      >
        <template #image>
          <el-icon :size="100" color="#909399"><Search /></el-icon>
        </template>
      </el-empty>
    </el-card>

    <!-- 批量搜索卡片 -->
    <el-card class="batch-card">
      <template #header>
        <div class="card-header">
          <span>批量订阅搜索</span>
          <el-tag v-if="batchRunning" type="success" effect="dark">
            <el-icon class="is-loading"><Loading /></el-icon>
            执行中
          </el-tag>
        </div>
      </template>

      <!-- JSON导入区域 -->
      <el-row :gutter="16">
        <el-col :span="24">
          <el-upload
            class="upload-demo"
            drag
            :auto-upload="false"
            :on-change="handleFileChange"
            :show-file-list="false"
            accept=".json"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              拖拽JSON文件到此处或 <em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持包含订阅列表的JSON文件，每个订阅需包含id字段
              </div>
            </template>
          </el-upload>
        </el-col>
      </el-row>

      <!-- JSON预览和配置 -->
      <div v-if="jsonData.length > 0" class="batch-config">
        <el-divider content-position="left">
          <el-icon><Setting /></el-icon>
          批量配置
        </el-divider>

        <el-row :gutter="16" class="config-row">
          <el-col :span="12">
            <el-statistic title="订阅总数" :value="jsonData.length">
              <template #prefix>
                <el-icon><Document /></el-icon>
              </template>
            </el-statistic>
          </el-col>
          <el-col :span="12">
            <el-form label-width="120px">
              <el-form-item label="请求间隔">
                <el-input-number
                  v-model="delayMin"
                  :min="1"
                  :max="10"
                />
                <span style="margin: 0 8px;">-</span>
                <el-input-number
                  v-model="delayMax"
                  :min="1"
                  :max="10"
                />
                <span style="margin-left: 8px;">分钟</span>
              </el-form-item>
            </el-form>
          </el-col>
        </el-row>

        <el-row :gutter="16" style="margin-top: 16px;">
          <el-col :span="24">
            <el-button
              type="primary"
              @click="handleBackgroundBatchSearch"
              :disabled="jsonData.length === 0"
            >
              <el-icon><VideoPlay /></el-icon>
              后台批量搜索（关闭浏览器继续执行）
            </el-button>
            <el-button
              type="success"
              @click="handleViewTasks"
            >
              <el-icon><List /></el-icon>
              查看任务列表
            </el-button>
            <el-button @click="handleClearBatch">
              <el-icon><Delete /></el-icon>
              清空数据
            </el-button>
          </el-col>
        </el-row>
      </div>
    </el-card>

    <!-- 日志详情对话框 -->
    <el-dialog
      v-model="logDialogVisible"
      title="请求详情"
      width="70%"
      :close-on-click-modal="false"
    >
      <div v-if="currentLog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订阅ID">{{ currentLog.id }}</el-descriptions-item>
          <el-descriptions-item label="订阅名称">{{ currentLog.name }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="currentLog.status === 'success' ? 'success' : 'danger'">
              {{ currentLog.status === 'success' ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="时间">{{ formatTime(currentLog.timestamp) }}</el-descriptions-item>
          <el-descriptions-item label="延迟">{{ currentLog.delay }}秒</el-descriptions-item>
          <el-descriptions-item label="消息">{{ currentLog.message }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">请求信息</el-divider>
        <el-card class="result-card" shadow="never">
          <pre class="json-result">{{ formatJson(currentLog.request) }}</pre>
        </el-card>

        <el-divider content-position="left">响应信息</el-divider>
        <el-card class="result-card" shadow="never">
          <pre class="json-result">{{ formatJson(currentLog.response) }}</pre>
        </el-card>
      </div>
    </el-dialog>

    <!-- 任务列表对话框 -->
    <el-dialog
      v-model="taskListDialogVisible"
      title="后台任务列表"
      width="90%"
      :close-on-click-modal="false"
    >
      <el-table
        :data="taskList"
        style="width: 100%"
        v-loading="taskListLoading"
      >
        <el-table-column prop="id" label="任务ID" width="80" />
        <el-table-column prop="taskName" label="任务名称" width="200" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="200">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.round((row.completedCount / row.totalCount) * 100)"
              :status="row.status === 'COMPLETED' ? 'success' : 'info'"
            >
              <span style="font-size: 12px;">{{ row.completedCount }}/{{ row.totalCount }}</span>
            </el-progress>
          </template>
        </el-table-column>
        <el-table-column prop="successCount" label="成功" width="80" />
        <el-table-column prop="failedCount" label="失败" width="80" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING' || row.status === 'PAUSED'"
              link
              type="primary"
              size="small"
              @click="handleStartTask(row.id)"
            >
              启动
            </el-button>
            <el-button
              v-if="row.status === 'RUNNING'"
              link
              type="warning"
              size="small"
              @click="handlePauseTask(row.id)"
            >
              暂停
            </el-button>
            <el-button
              link
              type="primary"
              size="small"
              @click="handleViewTaskLogs(row.id)"
            >
              查看日志
            </el-button>
            <el-button
              link
              type="danger"
              size="small"
              @click="handleDeleteTask(row.id)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="taskPagination.current"
        v-model:page-size="taskPagination.size"
        :total="taskPagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadTaskList"
        @size-change="loadTaskList"
        style="margin-top: 16px; justify-content: center;"
      />
    </el-dialog>

    <!-- 任务日志对话框 -->
    <el-dialog
      v-model="taskLogsDialogVisible"
      title="任务执行日志"
      width="90%"
      :close-on-click-modal="false"
    >
      <el-table
        :data="taskLogs"
        style="width: 100%"
        max-height="500"
        v-loading="taskLogsLoading"
      >
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="subscribeId" label="订阅ID" width="100" />
        <el-table-column prop="subscribeName" label="订阅名称" width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="delaySeconds" label="延迟(秒)" width="100" />
        <el-table-column prop="executeTime" label="执行时间" width="180" />
        <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { searchSubscribe } from '@/api/subscribe'
import {
  createBatchTask,
  startTask,
  pauseTask,
  getTaskPage,
  getTaskLogs,
  deleteTask
} from '@/api/subscribeBatch'

// LocalStorage 键名
const STORAGE_KEYS = {
  JSON_DATA: 'subscribe_search_json_data',
  DELAY_CONFIG: 'subscribe_search_delay_config'
}

// 单个搜索相关
const searchId = ref('')
const searchResult = ref(null)
const loading = ref(false)
const searched = ref(false)

// 批量搜索相关
const jsonData = ref([])
const delayMin = ref(1) // 最小延迟（分钟）
const delayMax = ref(2) // 最大延迟（分钟）

// 日志详情对话框
const logDialogVisible = ref(false)
const currentLog = ref(null)

// 任务列表对话框
const taskListDialogVisible = ref(false)
const taskList = ref([])
const taskListLoading = ref(false)
const taskPagination = ref({
  current: 1,
  size: 10,
  total: 0
})

// 任务日志对话框
const taskLogsDialogVisible = ref(false)
const taskLogs = ref([])
const taskLogsLoading = ref(false)

// 从 localStorage 加载数据
const loadFromStorage = () => {
  try {
    // 加载 JSON 数据
    const savedJsonData = localStorage.getItem(STORAGE_KEYS.JSON_DATA)
    if (savedJsonData) {
      jsonData.value = JSON.parse(savedJsonData)
    }

    // 加载延迟配置
    const savedDelayConfig = localStorage.getItem(STORAGE_KEYS.DELAY_CONFIG)
    if (savedDelayConfig) {
      const config = JSON.parse(savedDelayConfig)
      delayMin.value = config.min
      delayMax.value = config.max
    }
  } catch (error) {
    console.error('从 localStorage 加载数据失败:', error)
  }
}

// 保存到 localStorage
const saveToStorage = () => {
  try {
    localStorage.setItem(STORAGE_KEYS.JSON_DATA, JSON.stringify(jsonData.value))
    localStorage.setItem(STORAGE_KEYS.DELAY_CONFIG, JSON.stringify({
      min: delayMin.value,
      max: delayMax.value
    }))
  } catch (error) {
    console.error('保存到 localStorage 失败:', error)
  }
}

// 监听数据变化，自动保存
watch([jsonData, delayMin, delayMax], () => {
  saveToStorage()
}, { deep: true })

// 组件挂载时加载数据
onMounted(() => {
  loadFromStorage()
  if (jsonData.value.length > 0) {
    ElMessage.success(`已恢复 ${jsonData.value.length} 个订阅数据`)
  }
})

// 单个搜索
const handleSearch = async () => {
  if (!searchId.value) {
    ElMessage.warning('请输入订阅ID')
    return
  }

  loading.value = true
  searched.value = true
  searchResult.value = null

  try {
    const res = await searchSubscribe(searchId.value)
    searchResult.value = res
    ElMessage.success('搜索成功')
  } catch (error) {
    console.error('搜索失败:', error)
    searchResult.value = null
  } finally {
    loading.value = false
  }
}

// 重置搜索
const handleReset = () => {
  searchId.value = ''
  searchResult.value = null
  searched.value = false
}

// 处理文件上传
const handleFileChange = (file) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const json = JSON.parse(e.target.result)

      // 验证JSON格式
      if (!Array.isArray(json)) {
        ElMessage.error('JSON格式错误：必须是数组格式')
        return
      }

      // 验证每个元素是否包含id字段
      const invalidItems = json.filter(item => !item.id)
      if (invalidItems.length > 0) {
        ElMessage.error(`JSON格式错误：有${invalidItems.length}个订阅缺少id字段`)
        return
      }

      jsonData.value = json
      ElMessage.success(`成功导入${json.length}个订阅`)
      saveToStorage()
    } catch (error) {
      console.error('JSON解析失败:', error)
      ElMessage.error('JSON文件解析失败，请检查文件格式')
    }
  }
  reader.readAsText(file.raw)
}

// 后台批量搜索
const handleBackgroundBatchSearch = async () => {
  if (jsonData.value.length === 0) {
    ElMessage.warning('请先导入JSON文件')
    return
  }

  try {
    await ElMessageBox.confirm(
      `即将创建后台批量任务，包含 ${jsonData.value.length} 个订阅，每次请求间隔 ${delayMin.value}-${delayMax.value} 分钟。任务将在后台执行，即使关闭浏览器也会继续运行。是否继续？`,
      '确认创建后台任务',
      {
        confirmButtonText: '创建并启动',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  try {
    // 创建任务
    const taskName = `订阅搜索任务-${new Date().toLocaleString('zh-CN')}`
    const res = await createBatchTask({
      taskName,
      jsonData: JSON.stringify(jsonData.value),
      delayMin: delayMin.value,
      delayMax: delayMax.value
    })

    const taskId = res.data

    // 启动任务
    await startTask(taskId)

    ElMessage.success('后台任务已创建并启动，可以关闭浏览器了')

    // 打开任务列表
    handleViewTasks()
  } catch (error) {
    console.error('创建后台任务失败:', error)
  }
}

// 查看任务列表
const handleViewTasks = async () => {
  taskListDialogVisible.value = true
  await loadTaskList()
}

// 加载任务列表
const loadTaskList = async () => {
  taskListLoading.value = true
  try {
    const res = await getTaskPage({
      current: taskPagination.value.current,
      size: taskPagination.value.size
    })

    taskList.value = res.data.records
    taskPagination.value.total = res.data.total
  } catch (error) {
    console.error('加载任务列表失败:', error)
  } finally {
    taskListLoading.value = false
  }
}

// 启动任务
const handleStartTask = async (taskId) => {
  try {
    await startTask(taskId)
    ElMessage.success('任务已启动')
    await loadTaskList()
  } catch (error) {
    console.error('启动任务失败:', error)
  }
}

// 暂停任务
const handlePauseTask = async (taskId) => {
  try {
    await pauseTask(taskId)
    ElMessage.success('任务已暂停')
    await loadTaskList()
  } catch (error) {
    console.error('暂停任务失败:', error)
  }
}

// 查看任务日志
const handleViewTaskLogs = async (taskId) => {
  taskLogsDialogVisible.value = true
  taskLogsLoading.value = true

  try {
    const res = await getTaskLogs(taskId)
    taskLogs.value = res.data
  } catch (error) {
    console.error('加载任务日志失败:', error)
  } finally {
    taskLogsLoading.value = false
  }
}

// 删除任务
const handleDeleteTask = async (taskId) => {
  try {
    await ElMessageBox.confirm('确定要删除这个任务吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await deleteTask(taskId)
    ElMessage.success('任务已删除')
    await loadTaskList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除任务失败:', error)
    }
  }
}

// 获取状态类型
const getStatusType = (status) => {
  const typeMap = {
    'PENDING': 'info',
    'RUNNING': 'success',
    'PAUSED': 'warning',
    'COMPLETED': 'success',
    'FAILED': 'danger'
  }
  return typeMap[status] || 'info'
}

// 获取状态文本
const getStatusText = (status) => {
  const textMap = {
    'PENDING': '待执行',
    'RUNNING': '执行中',
    'PAUSED': '已暂停',
    'COMPLETED': '已完成',
    'FAILED': '失败'
  }
  return textMap[status] || status
}

// 清空批量数据
const handleClearBatch = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要清空所有数据吗？这将删除导入的订阅配置。',
      '确认清空',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  jsonData.value = []
  saveToStorage()
  ElMessage.success('数据已清空')
}

// 查看日志详情
const handleViewLog = (log) => {
  currentLog.value = log
  logDialogVisible.value = true
}

// 格式化JSON
const formatJson = (data) => {
  return JSON.stringify(data, null, 2)
}

// 格式化时间
const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}
</script>

<style scoped>
.subscribe-search-container {
  padding: 0;
}

.search-card,
.batch-card {
  margin-bottom: 24px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 16px;
}

.search-bar {
  margin-bottom: 24px;
}

.result-container {
  margin-top: 24px;
}

.result-card {
  margin-bottom: 24px;
  background: #f5f5f7;
}

.json-result {
  margin: 0;
  padding: 16px;
  background: #1d1d1f;
  color: #34c759;
  border-radius: 8px;
  overflow-x: auto;
  font-family: 'Courier New', Courier, monospace;
  font-size: 13px;
  line-height: 1.6;
  max-height: 600px;
  overflow-y: auto;
}

.json-inline {
  margin: 0;
  padding: 8px;
  background: #f5f5f7;
  border-radius: 4px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 12px;
  line-height: 1.5;
  overflow-x: auto;
}

.result-descriptions {
  margin-top: 16px;
}

.batch-config {
  margin-top: 24px;
}

.config-row {
  margin-top: 16px;
}

.progress-container {
  margin-top: 24px;
}

.progress-text {
  font-weight: 600;
  color: #1d1d1f;
}

.logs-container {
  margin-top: 32px;
}

.upload-demo {
  width: 100%;
}

/* 滚动条样式 */
.json-result::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.json-result::-webkit-scrollbar-track {
  background: #2d2d2f;
  border-radius: 4px;
}

.json-result::-webkit-scrollbar-thumb {
  background: #4d4d4f;
  border-radius: 4px;
}

.json-result::-webkit-scrollbar-thumb:hover {
  background: #5d5d5f;
}

/* Element Plus 上传组件样式优化 */
:deep(.el-upload-dragger) {
  border-radius: 12px;
  border: 2px dashed #d1d1d6;
  background: #f5f5f7;
  transition: all 0.3s ease;
}

:deep(.el-upload-dragger:hover) {
  border-color: #007aff;
  background: #ffffff;
}

:deep(.el-icon--upload) {
  font-size: 48px;
  color: #007aff;
  margin-bottom: 16px;
}

:deep(.el-upload__text) {
  color: #1d1d1f;
  font-size: 14px;
}

:deep(.el-upload__text em) {
  color: #007aff;
  font-style: normal;
  font-weight: 600;
}

:deep(.el-upload__tip) {
  color: #86868b;
  font-size: 12px;
  margin-top: 8px;
}

/* 表格样式优化 */
:deep(.el-table) {
  border-radius: 12px;
}

/* 对话框样式优化 */
:deep(.el-dialog) {
  border-radius: 12px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .search-bar .el-col {
    margin-bottom: 12px;
  }

  .json-result {
    font-size: 11px;
    padding: 12px;
  }

  .result-descriptions {
    font-size: 12px;
  }

  .config-row .el-col {
    margin-bottom: 16px;
  }
}
</style>
