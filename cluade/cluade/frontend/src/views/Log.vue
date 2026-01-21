<template>
  <div class="log-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>系统日志</span>
          <div class="header-actions">
            <el-button type="danger" size="small" @click="handleCleanLogs">
              <el-icon><Delete /></el-icon>
              清理过期日志
            </el-button>
          </div>
        </div>
      </template>

      <!-- 搜索筛选 -->
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="日志类型">
          <el-select v-model="queryParams.logType" placeholder="全部" clearable style="width: 150px">
            <el-option label="系统日志" :value="1" />
            <el-option label="任务日志" :value="2" />
            <el-option label="账号日志" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="日志级别">
          <el-select v-model="queryParams.logLevel" placeholder="全部" clearable style="width: 120px">
            <el-option label="INFO" value="INFO" />
            <el-option label="WARN" value="WARN" />
            <el-option label="ERROR" value="ERROR" />
          </el-select>
        </el-form-item>
        <el-form-item label="模块">
          <el-input v-model="queryParams.module" placeholder="模块名称" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="queryParams.keyword" placeholder="搜索日志内容" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 日志表格 -->
      <el-table :data="logList" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="logType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.logType === 1" type="info">系统</el-tag>
            <el-tag v-else-if="row.logType === 2" type="warning">任务</el-tag>
            <el-tag v-else-if="row.logType === 3" type="success">账号</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="logLevel" label="级别" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.logLevel === 'INFO'" type="info">INFO</el-tag>
            <el-tag v-else-if="row.logLevel === 'WARN'" type="warning">WARN</el-tag>
            <el-tag v-else-if="row.logLevel === 'ERROR'" type="danger">ERROR</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="120" />
        <el-table-column prop="operation" label="操作" width="150" />
        <el-table-column prop="message" label="消息" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleViewDetail(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="queryParams.current"
        v-model:page-size="queryParams.size"
        :page-sizes="[20, 50, 100, 200]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadLogs"
        @current-change="loadLogs"
      />
    </el-card>

    <!-- 日志详情对话框 -->
    <el-dialog v-model="detailVisible" title="日志详情" width="600px">
      <el-descriptions :column="1" border v-if="currentLog">
        <el-descriptions-item label="ID">{{ currentLog.id }}</el-descriptions-item>
        <el-descriptions-item label="日志类型">
          <el-tag v-if="currentLog.logType === 1" type="info">系统</el-tag>
          <el-tag v-else-if="currentLog.logType === 2" type="warning">任务</el-tag>
          <el-tag v-else-if="currentLog.logType === 3" type="success">账号</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="日志级别">
          <el-tag v-if="currentLog.logLevel === 'INFO'" type="info">INFO</el-tag>
          <el-tag v-else-if="currentLog.logLevel === 'WARN'" type="warning">WARN</el-tag>
          <el-tag v-else-if="currentLog.logLevel === 'ERROR'" type="danger">ERROR</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="模块">{{ currentLog.module }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ currentLog.operation }}</el-descriptions-item>
        <el-descriptions-item label="消息">{{ currentLog.message }}</el-descriptions-item>
        <el-descriptions-item label="详情" v-if="currentLog.detail">
          <pre style="white-space: pre-wrap; word-wrap: break-word;">{{ currentLog.detail }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="任务ID" v-if="currentLog.taskId">{{ currentLog.taskId }}</el-descriptions-item>
        <el-descriptions-item label="账号ID" v-if="currentLog.accountId">{{ currentLog.accountId }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ currentLog.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 清理日志对话框 -->
    <el-dialog v-model="cleanVisible" title="清理过期日志" width="400px">
      <el-form :model="cleanForm" label-width="120px">
        <el-form-item label="保留天数">
          <el-input-number v-model="cleanForm.days" :min="1" :max="365" />
        </el-form-item>
        <el-form-item>
          <el-alert
            title="将删除指定天数之前的所有日志，此操作不可恢复"
            type="warning"
            :closable="false"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cleanVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmCleanLogs">确认清理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getLogPage, cleanExpiredLogs } from '@/api/log'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const logList = ref([])
const total = ref(0)

const queryParams = reactive({
  current: 1,
  size: 20,
  logType: null,
  logLevel: null,
  module: null,
  keyword: null
})

const detailVisible = ref(false)
const currentLog = ref(null)

const cleanVisible = ref(false)
const cleanForm = reactive({
  days: 30
})

// 加载日志列表
const loadLogs = async () => {
  loading.value = true
  try {
    const res = await getLogPage(queryParams)
    if (res.code === 200) {
      logList.value = res.data.records || []
      total.value = res.data.total || 0
    } else {
      ElMessage.error(res.message || '加载日志失败')
    }
  } catch (error) {
    console.error('加载日志失败:', error)
    ElMessage.error('加载日志失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  queryParams.current = 1
  loadLogs()
}

// 重置
const handleReset = () => {
  queryParams.current = 1
  queryParams.size = 20
  queryParams.logType = null
  queryParams.logLevel = null
  queryParams.module = null
  queryParams.keyword = null
  loadLogs()
}

// 查看详情
const handleViewDetail = (row) => {
  currentLog.value = row
  detailVisible.value = true
}

// 清理日志
const handleCleanLogs = () => {
  cleanVisible.value = true
}

// 确认清理
const confirmCleanLogs = async () => {
  try {
    const res = await cleanExpiredLogs(cleanForm.days)
    if (res.code === 200) {
      ElMessage.success(`成功清理 ${res.data} 条过期日志`)
      cleanVisible.value = false
      loadLogs()
    } else {
      ElMessage.error(res.message || '清理日志失败')
    }
  } catch (error) {
    console.error('清理日志失败:', error)
    ElMessage.error('清理日志失败')
  }
}

onMounted(() => {
  loadLogs()
})
</script>

<style scoped>
.log-container {
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 20px;
}

.el-pagination {
  margin-top: 20px;
  justify-content: center;
}
</style>
