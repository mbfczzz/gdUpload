<template>
  <div class="format-rename-page">

    <!-- 工具栏 -->
    <div class="toolbar">
      <el-button type="primary" @click="startDialogVisible = true">
        <el-icon><Plus /></el-icon>
        启动新任务
      </el-button>
      <el-button @click="loadTasks">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <!-- 任务列表 -->
    <el-card class="task-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">格式化命名任务</span>
          <el-badge v-if="runningCount > 0" :value="runningCount" type="warning" />
        </div>
      </template>

      <el-table v-loading="taskLoading" :data="tasks" style="width:100%">
        <el-table-column label="任务名" min-width="200">
          <template #default="{ row }">
            <span class="task-name">{{ row.taskName }}</span>
          </template>
        </el-table-column>

        <el-table-column label="目录" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.dirPath || '根目录' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="进度" min-width="180">
          <template #default="{ row }">
            <el-progress
              :percentage="calcPercent(row)"
              :status="progressStatus(row.status)"
              :stroke-width="6"
            />
            <div class="progress-text">
              {{ row.processedFiles }} / {{ row.totalFiles }} 个文件
            </div>
            <div v-if="(row.status === 'RUNNING' || row.status === 'PAUSING') && row.currentFile" class="current-file">
              {{ row.status === 'PAUSING' ? '⏳' : '▶' }} {{ row.currentFile }}
            </div>
            <div v-if="row.status === 'PAUSED'" class="paused-tip">⏸ 已暂停</div>
            <div v-if="row.status === 'PAUSING'" class="paused-tip">⏳ 暂停中...</div>
          </template>
        </el-table-column>

        <el-table-column label="统计" width="160">
          <template #default="{ row }">
            <div class="stat-row">
              <el-tag type="success" size="small">✓ {{ row.renamedCount }}</el-tag>
              <el-tag type="info"    size="small">⊘ {{ row.skippedCount }}</el-tag>
              <el-tag type="danger"  size="small">✗ {{ row.failedCount }}</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'RUNNING'"
              type="warning" link size="small"
              @click="pauseTask(row)"
            >暂停</el-button>
            <el-button
              v-if="row.status === 'PAUSED'"
              type="success" link size="small"
              @click="resumeTask(row)"
            >继续</el-button>
            <el-button
              v-if="['RUNNING','PAUSED','PENDING','PAUSING'].includes(row.status)"
              type="danger" link size="small"
              @click="cancelTask(row)"
            >取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="taskTotal > taskPageSize"
        v-model:current-page="taskPage"
        v-model:page-size="taskPageSize"
        :total="taskTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="loadTasks"
      />
    </el-card>

    <!-- ── 启动任务弹窗 ────────────────────────────────────────────────────── -->
    <el-dialog
      v-model="startDialogVisible"
      title="启动格式化命名任务"
      width="480px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="startForm" label-width="80px" @submit.prevent>
        <el-form-item label="GD账号">
          <el-select v-model="startForm.accountId" placeholder="选择账号" style="width:100%">
            <el-option
              v-for="acc in accounts"
              :key="acc.id"
              :label="acc.accountName"
              :value="acc.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目录路径">
          <el-input
            v-model="startForm.dirPath"
            placeholder="如 video2/动画电影（留空为根目录）"
            clearable
          />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" style="margin-top:8px">
        将递归扫描该目录下所有媒体文件，对文件名<strong>缺少视频编码</strong>的文件通过 ffprobe 探测后自动重命名。ffprobe 需下载每个文件头部数据（约 15MB/文件），文件多时耗时较长。
      </el-alert>
      <template #footer>
        <el-button @click="startDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="starting" @click="doStart">启动</el-button>
      </template>
    </el-dialog>

    <!-- ── 任务详情抽屉 ────────────────────────────────────────────────────── -->
    <el-drawer
      v-model="detailVisible"
      :title="selectedTask?.taskName"
      size="75%"
      direction="rtl"
    >
      <template v-if="selectedTask">
        <!-- 摘要 -->
        <div class="detail-summary">
          <div class="summary-item">
            <span class="label">目录</span>
            <span class="mono">{{ selectedTask.dirPath || '根目录' }}</span>
          </div>
          <div class="summary-item">
            <span class="label">状态</span>
            <el-tag :type="statusType(selectedTask.status)" size="small">
              {{ statusLabel(selectedTask.status) }}
            </el-tag>
          </div>
          <div class="summary-item">
            <span class="label">进度</span>
            <span>{{ selectedTask.processedFiles }} / {{ selectedTask.totalFiles }}</span>
          </div>
          <div class="summary-stats">
            <el-tag type="success" size="small">✓ 重命名 {{ selectedTask.renamedCount }}</el-tag>
            <el-tag type="info"    size="small">⊘ 跳过 {{ selectedTask.skippedCount }}</el-tag>
            <el-tag type="danger"  size="small">✗ 失败 {{ selectedTask.failedCount }}</el-tag>
          </div>
        </div>

        <!-- 历史筛选 -->
        <div class="detail-filter">
          <el-radio-group v-model="detailStatusFilter" @change="loadDetail(1)">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="renamed">已重命名</el-radio-button>
            <el-radio-button value="skipped">跳过</el-radio-button>
            <el-radio-button value="failed">失败</el-radio-button>
          </el-radio-group>
        </div>

        <!-- 文件历史表格 -->
        <el-table v-loading="detailLoading" :data="detailList" style="width:100%">
          <el-table-column label="原文件名" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="mono">{{ row.originalFilename }}</span>
            </template>
          </el-table-column>
          <el-table-column label="新文件名" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.newFilename" class="mono new-name">{{ row.newFilename }}</span>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="historyStatusType(row.status)" size="small">
                {{ historyStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span :class="row.status === 'renamed' ? 'note-renamed' : 'text-muted'">
                {{ row.skipReason || row.failReason || '' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-if="detailTotal > detailPageSize"
          v-model:current-page="detailPage"
          v-model:page-size="detailPageSize"
          :total="detailTotal"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          style="margin-top:16px"
          @change="loadDetail"
        />
      </template>
    </el-drawer>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAccountList } from '@/api/account'
import {
  startFormatRenameTask, getFormatRenameTasks, getFormatRenameTask,
  getFormatRenameHistory, cancelFormatRenameTask,
  pauseFormatRenameTask, resumeFormatRenameTask
} from '@/api/formatRename'

// ── 账号 ──────────────────────────────────────────────────────────────────────
const accounts = ref([])
async function loadAccounts() {
  try { accounts.value = (await getAccountList()).data || [] } catch { /* 静默 */ }
}

// ── 任务列表 ──────────────────────────────────────────────────────────────────
const tasks        = ref([])
const taskLoading  = ref(false)
const taskPage     = ref(1)
const taskPageSize = ref(10)
const taskTotal    = ref(0)

const runningCount = computed(() =>
  tasks.value.filter(t => t.status === 'RUNNING' || t.status === 'PAUSED' || t.status === 'PAUSING').length
)

async function loadTasks() {
  taskLoading.value = true
  try {
    const res = await getFormatRenameTasks(taskPage.value, taskPageSize.value)
    const page = res.data || {}
    tasks.value  = page.records || []
    taskTotal.value = page.total || 0
  } catch (e) {
    ElMessage.error('加载任务失败')
  } finally {
    taskLoading.value = false
  }
}

// 自动轮询运行中的任务
let pollTimer = null
function startPoll() {
  stopPoll()
  pollTimer = setInterval(async () => {
    if (!tasks.value.some(t => t.status === 'RUNNING' || t.status === 'PENDING' || t.status === 'PAUSING')) return
    await loadTasks()
    if (detailVisible.value && selectedTask.value &&
        ['RUNNING', 'PENDING', 'PAUSING'].includes(selectedTask.value.status)) {
      await refreshDetail()
    }
  }, 3000)
}
function stopPoll() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

// ── 操作 ──────────────────────────────────────────────────────────────────────
async function pauseTask(row) {
  try {
    await pauseFormatRenameTask(row.id)
    ElMessage.success('已发送暂停指令')
    await loadTasks()
  } catch (e) { ElMessage.error('暂停失败') }
}

async function resumeTask(row) {
  try {
    await resumeFormatRenameTask(row.id)
    ElMessage.success('任务继续运行')
    await loadTasks()
  } catch (e) { ElMessage.error('恢复失败') }
}

async function cancelTask(row) {
  try {
    await ElMessageBox.confirm(`确认取消任务「${row.taskName}」？`, '取消确认', {
      confirmButtonText: '确认取消', cancelButtonText: '不取消', type: 'warning'
    })
    await cancelFormatRenameTask(row.id)
    ElMessage.success('任务已取消')
    await loadTasks()
  } catch { /* 用户取消 */ }
}

// ── 启动弹窗 ──────────────────────────────────────────────────────────────────
const startDialogVisible = ref(false)
const starting = ref(false)
const startForm = ref({ accountId: null, dirPath: '' })

async function doStart() {
  if (!startForm.value.accountId) { ElMessage.warning('请选择账号'); return }
  starting.value = true
  try {
    await startFormatRenameTask(startForm.value.accountId, startForm.value.dirPath || '')
    ElMessage.success('任务已启动')
    startDialogVisible.value = false
    startForm.value = { accountId: null, dirPath: '' }
    await loadTasks()
  } catch (e) {
    ElMessage.error('启动失败: ' + (e?.message || e))
  } finally {
    starting.value = false
  }
}

// ── 详情抽屉 ──────────────────────────────────────────────────────────────────
const detailVisible      = ref(false)
const selectedTask       = ref(null)
const detailList         = ref([])
const detailLoading      = ref(false)
const detailPage         = ref(1)
const detailPageSize     = ref(20)
const detailTotal        = ref(0)
const detailStatusFilter = ref('')

async function viewDetail(row) {
  selectedTask.value   = row
  detailStatusFilter.value = ''
  detailPage.value     = 1
  detailVisible.value  = true
  await loadDetail()
}

async function loadDetail(page) {
  if (!selectedTask.value) return
  if (page) detailPage.value = page
  detailLoading.value = true
  try {
    const res = await getFormatRenameHistory(
      selectedTask.value.id, detailPage.value, detailPageSize.value,
      detailStatusFilter.value || undefined
    )
    const p = res.data || {}
    detailList.value  = p.records || []
    detailTotal.value = p.total   || 0
  } catch { /* 静默 */ } finally {
    detailLoading.value = false
  }
}

async function refreshDetail() {
  const fresh = await getFormatRenameTask(selectedTask.value.id)
  if (fresh?.data) selectedTask.value = fresh.data
  await loadDetail()
}

// ── 工具函数 ──────────────────────────────────────────────────────────────────
function calcPercent(row) {
  if (!row.totalFiles) return 0
  return Math.min(100, Math.round(row.processedFiles / row.totalFiles * 100))
}

function statusLabel(s) {
  return { PENDING: '等待中', RUNNING: '进行中', PAUSING: '暂停中...', PAUSED: '已暂停',
           COMPLETED: '已完成', FAILED: '失败/取消' }[s] || s
}
function statusType(s) {
  return { PENDING: 'info', RUNNING: 'warning', PAUSING: 'warning', PAUSED: 'info',
           COMPLETED: 'success', FAILED: 'danger' }[s] || 'info'
}
function progressStatus(s) {
  if (s === 'COMPLETED') return 'success'
  if (s === 'FAILED')    return 'exception'
  return undefined
}

function historyStatusLabel(s) {
  return { renamed: '已重命名', skipped: '跳过', failed: '失败' }[s] || s
}
function historyStatusType(s) {
  return { renamed: 'success', skipped: 'info', failed: 'danger' }[s] || 'info'
}

function formatTime(t) {
  if (!t) return '—'
  return new Date(t).toLocaleString('zh-CN', { hour12: false })
}

onMounted(async () => {
  await Promise.all([loadAccounts(), loadTasks()])
  startPoll()
})
onUnmounted(stopPoll)
</script>

<style scoped>
.format-rename-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  display: flex;
  gap: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1d1d1f;
}

.task-name {
  font-weight: 500;
}

.mono {
  font-family: monospace;
  font-size: 12px;
}

.progress-text {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.current-file {
  font-size: 11px;
  color: #e6a23c;
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 280px;
}

.paused-tip {
  font-size: 11px;
  color: #909399;
}

.stat-row {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.text-muted {
  color: #909399;
  font-size: 12px;
}

.note-renamed {
  color: #67c23a;
  font-size: 12px;
}

/* 详情抽屉 */
.detail-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 16px;
  background: #f5f5f7;
  border-radius: 8px;
  margin-bottom: 16px;
}
.summary-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.summary-item .label {
  font-size: 12px;
  color: #86868b;
}
.summary-stats {
  display: flex;
  gap: 8px;
  align-items: center;
}

.detail-filter {
  margin-bottom: 16px;
}

.new-name {
  color: #34c759;
}
</style>
