<template>
  <div class="archive-page">

    <!-- ─── 批量归档任务 ─── -->
    <el-card class="batch-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon><Grid /></el-icon>
            <span>批量归档任务</span>
            <el-badge v-if="runningCount > 0" :value="runningCount" type="warning" />
          </div>
          <el-button size="small" @click="loadBatchTasks" :loading="batchLoading">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </template>

      <el-table v-loading="batchLoading" :data="batchTasks" border size="small">
        <el-table-column label="任务名" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="task-name-cell">
              <span class="task-name">{{ row.taskName }}</span>
              <div v-if="row.status === 'RUNNING' && row.currentFile" class="current-file">
                ▶ {{ row.currentFile }}
              </div>
              <div v-else-if="row.status === 'PAUSED'" class="current-file paused-hint">
                ⏸ {{ row.currentFile ? '暂停于: ' + row.currentFile : '已暂停' }}
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="源路径" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="source-path">{{ row.sourcePath || '根目录' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="batchStatusType(row.status)" size="small">
              {{ batchStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="进度" width="180">
          <template #default="{ row }">
            <div v-if="row.totalFiles > 0">
              <el-progress
                :percentage="calcPercent(row)"
                :status="progressStatus(row)"
                :stroke-width="8"
                style="margin-bottom: 2px"
              />
              <span class="progress-text">{{ row.processedFiles }} / {{ row.totalFiles }}</span>
            </div>
            <span v-else class="text-muted">扫描中...</span>
          </template>
        </el-table-column>

        <el-table-column label="文件统计" width="140">
          <template #default="{ row }">
            <span class="stat-success">✓ {{ row.successCount }}</span>
            <span class="stat-sep"> / </span>
            <span class="stat-fail">✗ {{ row.failedCount }}</span>
            <span class="stat-sep"> / </span>
            <span class="stat-manual">! {{ row.manualCount }}</span>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewBatchDetail(row)">
              查看详情
            </el-button>
            <el-button
              v-if="row.status === 'RUNNING'"
              type="warning"
              link
              size="small"
              @click="pauseBatchTaskFn(row)"
            >暂停</el-button>
            <el-button
              v-if="row.status === 'PAUSED'"
              type="success"
              link
              size="small"
              @click="resumeBatchTaskFn(row)"
            >继续</el-button>
            <el-button
              v-if="row.status === 'RUNNING' || row.status === 'PENDING' || row.status === 'PAUSED'"
              type="danger"
              link
              size="small"
              @click="cancelBatchTaskFn(row)"
            >取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!batchLoading && batchTasks.length === 0" class="empty-tip">
        <el-empty description="暂无批量任务，可在 GD文件管理 中对文件夹点击「批量归档」" :image-size="60" />
      </div>

      <div v-if="batchTotal > 0" class="pagination-bar">
        <el-pagination
          v-model:current-page="batchPage"
          v-model:page-size="batchPageSize"
          :page-sizes="[10, 20, 50]"
          :total="batchTotal"
          layout="total, sizes, prev, pager, next"
          background small
          @change="loadBatchTasks"
        />
      </div>
    </el-card>

    <!-- ─── 快速归档 ─── -->
    <el-card class="quick-card">
      <template #header>
        <div class="card-header">
          <el-icon><FolderChecked /></el-icon>
          <span>快速归档</span>
        </div>
      </template>
      <div class="quick-form">
        <el-input
          v-model="inputPath"
          placeholder="输入服务器本地文件路径，如：/data/upload/剧名 S01E01.mkv"
          clearable
          @keyup.enter="openArchive"
          class="path-input"
        >
          <template #prepend><el-icon><Document /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="openArchive" :disabled="!inputPath.trim()">
          <el-icon><FolderChecked /></el-icon>
          开始归档
        </el-button>
      </div>
    </el-card>

    <!-- ─── 归档历史 ─── -->
    <el-card class="history-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon><Clock /></el-icon>
            <span>归档历史</span>
          </div>
          <div class="header-right">
            <el-radio-group v-model="statusFilter" size="small" @change="loadHistory">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="success">成功</el-radio-button>
              <el-radio-button value="failed">失败</el-radio-button>
              <el-radio-button value="manual_required">待人工</el-radio-button>
            </el-radio-group>
            <el-button size="small" @click="loadHistory" :loading="loading">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="historyList" border>
        <el-table-column label="原始文件名" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="filename">{{ row.originalFilename }}</span>
          </template>
        </el-table-column>
        <el-table-column label="归档后路径" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.newPath" class="new-path">{{ row.newPath }}</span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="100">
          <template #default="{ row }">
            <span v-if="row.category">{{ row.category }}</span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="TMDB" width="90">
          <template #default="{ row }">
            <span v-if="row.tmdbId" class="tmdb-id">{{ row.tmdbId }}</span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="方式" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="methodType(row.processMethod)">
              {{ methodLabel(row.processMethod) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="150">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="备注" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="remark-cell">
              <span v-if="row.remark" class="remark-text">{{ row.remark }}</span>
              <span v-else class="text-muted">—</span>
              <el-button
                v-if="row.status === 'manual_required'"
                type="primary" link size="small"
                @click="editRemark(row)"
              >编辑</el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.failReason" class="fail-reason">{{ row.failReason }}</span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'failed' || row.status === 'manual_required'"
              type="warning" link size="small"
              @click="retryArchive(row)"
            >手动归档</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background small
          @change="loadHistory"
        />
      </div>
    </el-card>

    <!-- ─── 归档弹窗 ─── -->
    <ArchiveDialog
      v-model="dialogVisible"
      :file="currentFile"
      @archived="onArchived"
    />

    <!-- ─── 备注编辑弹窗 ─── -->
    <el-dialog v-model="remarkDialogVisible" title="编辑备注" width="420px">
      <el-input v-model="remarkInput" type="textarea" :rows="3" placeholder="请输入备注..." />
      <template #footer>
        <el-button @click="remarkDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="remarkLoading" @click="submitRemark">保存</el-button>
      </template>
    </el-dialog>

    <!-- ─── 批量任务详情抽屉 ─── -->
    <el-drawer
      v-model="detailDrawerVisible"
      :title="`任务详情：${selectedBatchTask?.taskName || ''}`"
      size="75%"
      direction="rtl"
      destroy-on-close
    >
      <div class="drawer-content">
        <!-- 任务统计 -->
        <div class="detail-stats" v-if="selectedBatchTask">
          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="状态">
              <el-tag :type="batchStatusType(selectedBatchTask.status)" size="small">
                {{ batchStatusLabel(selectedBatchTask.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="进度">
              {{ selectedBatchTask.processedFiles }} / {{ selectedBatchTask.totalFiles }}
            </el-descriptions-item>
            <el-descriptions-item label="成功">
              <span class="stat-success">{{ selectedBatchTask.successCount }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="失败">
              <span class="stat-fail">{{ selectedBatchTask.failedCount }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="待人工">
              <span class="stat-manual">{{ selectedBatchTask.manualCount }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="源路径" :span="3">
              {{ selectedBatchTask.sourcePath || '根目录' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 状态筛选 -->
        <div class="detail-filter">
          <el-radio-group v-model="detailStatusFilter" size="small" @change="loadBatchDetail">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="success">成功</el-radio-button>
            <el-radio-button value="failed">失败</el-radio-button>
            <el-radio-button value="manual_required">待人工</el-radio-button>
          </el-radio-group>
          <el-button size="small" :loading="detailLoading" @click="loadBatchDetail" style="margin-left:8px">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>

        <!-- 文件列表 -->
        <el-table v-loading="detailLoading" :data="detailList" border size="small" style="margin-top:12px">
          <el-table-column label="原始文件名" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.originalFilename }}</template>
          </el-table-column>
          <el-table-column label="归档后路径" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.newPath" class="new-path">{{ row.newPath }}</span>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="方式" width="70">
            <template #default="{ row }">
              <el-tag size="small" :type="methodType(row.processMethod)">{{ methodLabel(row.processMethod) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="备注/失败原因" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.failReason" class="fail-reason">{{ row.failReason }}</span>
              <span v-else-if="row.remark" class="remark-text">{{ row.remark }}</span>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="140">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'failed' || row.status === 'manual_required'"
                type="warning" link size="small"
                @click="retryArchive(row)"
              >手动归档</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="detailPage"
            v-model:page-size="detailPageSize"
            :page-sizes="[20, 50, 100]"
            :total="detailTotal"
            layout="total, sizes, prev, pager, next"
            background small
            @change="loadBatchDetail"
          />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderChecked, Document, Clock, Refresh, Grid } from '@element-plus/icons-vue'
import {
  getArchiveHistory, updateRemark,
  getBatchTasks, getBatchTask, getBatchTaskHistory,
  cancelBatchTask, pauseBatchTask, resumeBatchTask
} from '@/api/archive'
import ArchiveDialog from '@/components/ArchiveDialog.vue'

// ─── 批量任务列表 ──────────────────────────────────────────────────────────────

const batchLoading = ref(false)
const batchTasks = ref([])
const batchPage = ref(1)
const batchPageSize = ref(10)
const batchTotal = ref(0)
let pollTimer = null

const runningCount = computed(() =>
  batchTasks.value.filter(t => t.status === 'RUNNING' || t.status === 'PENDING').length
)

async function loadBatchTasks() {
  batchLoading.value = true
  try {
    const res = await getBatchTasks(batchPage.value, batchPageSize.value)
    const data = res.data || {}
    batchTasks.value = data.records || []
    batchTotal.value = data.total || 0
  } catch (e) {
    ElMessage.error('获取批量任务失败')
  } finally {
    batchLoading.value = false
  }

  // 有进行中的任务时自动轮询
  if (runningCount.value > 0 && !pollTimer) {
    pollTimer = setInterval(pollRunningTasks, 3000)
  } else if (runningCount.value === 0 && pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function pollRunningTasks() {
  // 只刷新进行中任务的状态，避免全量请求闪烁
  const running = batchTasks.value.filter(t => t.status === 'RUNNING' || t.status === 'PENDING')
  if (running.length === 0) {
    clearInterval(pollTimer)
    pollTimer = null
    return
  }

  for (const task of running) {
    try {
      const res = await getBatchTask(task.id)
      const updated = res.data
      if (updated) {
        const idx = batchTasks.value.findIndex(t => t.id === task.id)
        if (idx >= 0) batchTasks.value[idx] = updated
      }
    } catch { /* 静默 */ }
  }

  // 同步更新详情抽屉中的任务信息
  if (detailDrawerVisible.value && selectedBatchTask.value) {
    const updated = batchTasks.value.find(t => t.id === selectedBatchTask.value.id)
    if (updated) selectedBatchTask.value = updated
  }
}

async function cancelBatchTaskFn(row) {
  try {
    await ElMessageBox.confirm(`确认取消任务：${row.taskName}？`, '取消确认', {
      type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '保留'
    })
  } catch { return }

  try {
    await cancelBatchTask(row.id)
    ElMessage.warning('任务已取消')
    loadBatchTasks()
  } catch (e) {
    ElMessage.error('取消失败: ' + e.message)
  }
}

async function pauseBatchTaskFn(row) {
  try {
    await pauseBatchTask(row.id)
    ElMessage.success('任务已暂停')
    loadBatchTasks()
  } catch (e) {
    ElMessage.error('暂停失败: ' + e.message)
  }
}

async function resumeBatchTaskFn(row) {
  try {
    await resumeBatchTask(row.id)
    ElMessage.success('任务已恢复')
    loadBatchTasks()
  } catch (e) {
    ElMessage.error('恢复失败: ' + e.message)
  }
}

function calcPercent(row) {
  if (!row.totalFiles || row.totalFiles === 0) return 0
  return Math.min(100, Math.round(row.processedFiles / row.totalFiles * 100))
}

function progressStatus(row) {
  if (row.status === 'COMPLETED') return 'success'
  if (row.status === 'FAILED') return 'exception'
  if (row.status === 'PARTIAL') return 'warning'
  return undefined
}

function batchStatusType(s) {
  const map = { PENDING: 'info', RUNNING: 'warning', PAUSED: 'info', COMPLETED: 'success', PARTIAL: 'warning', FAILED: 'danger' }
  return map[s] || 'info'
}

function batchStatusLabel(s) {
  const map = { PENDING: '等待中', RUNNING: '进行中', PAUSED: '已暂停', COMPLETED: '已完成', PARTIAL: '部分完成', FAILED: '失败/取消' }
  return map[s] || s
}

// ─── 批量任务详情 ──────────────────────────────────────────────────────────────

const detailDrawerVisible = ref(false)
const selectedBatchTask = ref(null)
const detailLoading = ref(false)
const detailList = ref([])
const detailPage = ref(1)
const detailPageSize = ref(20)
const detailTotal = ref(0)
const detailStatusFilter = ref('')

async function viewBatchDetail(row) {
  selectedBatchTask.value = row
  detailPage.value = 1
  detailStatusFilter.value = ''
  detailDrawerVisible.value = true
  await loadBatchDetail()
}

async function loadBatchDetail() {
  if (!selectedBatchTask.value) return
  detailLoading.value = true
  try {
    const res = await getBatchTaskHistory(
      selectedBatchTask.value.id,
      detailPage.value,
      detailPageSize.value,
      detailStatusFilter.value
    )
    const data = res.data || {}
    detailList.value = data.records || []
    detailTotal.value = data.total || 0
  } catch {
    ElMessage.error('获取任务详情失败')
  } finally {
    detailLoading.value = false
  }
}

// ─── 快速归档 ─────────────────────────────────────────────────────────────────

const inputPath = ref('')
const dialogVisible = ref(false)
const currentFile = ref(null)

function openArchive() {
  const path = inputPath.value.trim()
  if (!path) return
  const parts = path.replace(/\\/g, '/').split('/')
  const fileName = parts[parts.length - 1]
  currentFile.value = { fileName, filePath: path, fileSize: null }
  dialogVisible.value = true
}

function onArchived() {
  inputPath.value = ''
  loadHistory()
  // 若批量详情抽屉打开，也刷新详情
  if (detailDrawerVisible.value) loadBatchDetail()
}

/** 从历史记录重新发起手动归档 */
function retryArchive(row) {
  currentFile.value = {
    fileName: row.originalFilename,
    filePath: row.originalPath || ''
  }
  dialogVisible.value = true
}

// ─── 归档历史 ─────────────────────────────────────────────────────────────────

const loading = ref(false)
const historyList = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const statusFilter = ref('')

async function loadHistory() {
  loading.value = true
  try {
    const res = await getArchiveHistory(page.value, pageSize.value, statusFilter.value)
    const data = res.data
    historyList.value = data?.records || []
    total.value = data?.total || 0
  } catch (e) {
    ElMessage.error('获取归档历史失败')
  } finally {
    loading.value = false
  }
}

// ─── 备注编辑 ─────────────────────────────────────────────────────────────────

const remarkDialogVisible = ref(false)
const remarkLoading = ref(false)
const remarkInput = ref('')
const remarkTargetId = ref(null)

function editRemark(row) {
  remarkTargetId.value = row.id
  remarkInput.value = row.remark || ''
  remarkDialogVisible.value = true
}

async function submitRemark() {
  remarkLoading.value = true
  try {
    await updateRemark(remarkTargetId.value, remarkInput.value)
    ElMessage.success('备注已更新')
    remarkDialogVisible.value = false
    loadHistory()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    remarkLoading.value = false
  }
}

// ─── 工具函数 ─────────────────────────────────────────────────────────────────

function statusType(s) {
  if (s === 'success') return 'success'
  if (s === 'failed') return 'danger'
  if (s === 'manual_required') return 'warning'
  return 'info'
}

function statusLabel(s) {
  if (s === 'success') return '成功'
  if (s === 'failed') return '失败'
  if (s === 'manual_required') return '待人工'
  return s || '—'
}

function methodType(m) {
  if (m === 'ai') return 'warning'
  if (m === 'tmdb') return ''
  return 'info'
}

function methodLabel(m) {
  if (m === 'ai') return 'AI'
  if (m === 'tmdb') return 'TMDB'
  if (m === 'manual') return '手动'
  if (m === 'auto') return '自动'
  return m || '—'
}

function formatTime(t) {
  if (!t) return '—'
  try {
    return new Date(t).toLocaleString('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit'
    })
  } catch { return t }
}

// ─── 生命周期 ─────────────────────────────────────────────────────────────────

onMounted(() => {
  loadBatchTasks()
  loadHistory()
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.archive-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ─── 批量任务 ─── */
.task-name-cell { display: flex; flex-direction: column; gap: 2px; }
.task-name { font-weight: 500; color: #1d1d1f; }
.current-file {
  font-size: 11px;
  color: #e6a23c;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}
.paused-hint {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}
.source-path { font-family: monospace; font-size: 12px; color: #909399; }
.progress-text { font-size: 11px; color: #909399; }
.stat-success { color: #67c23a; font-weight: 600; }
.stat-fail    { color: #f56c6c; font-weight: 600; }
.stat-manual  { color: #e6a23c; font-weight: 600; }
.stat-sep     { color: #c0c4cc; }

.empty-tip { padding: 24px 0; }

/* ─── 快速归档 ─── */
.quick-form {
  display: flex;
  gap: 12px;
  align-items: center;
}
.path-input { flex: 1; }

/* ─── 历史 ─── */
.filename { font-weight: 500; color: #1d1d1f; }
.new-path { font-family: monospace; font-size: 12px; color: #67c23a; }
.tmdb-id { font-family: monospace; font-size: 12px; color: #909399; }
.text-muted { color: #c0c4cc; }
.fail-reason { color: #ff3b30; font-size: 12px; }
.remark-text {
  font-size: 12px; color: #606266;
  flex: 1; min-width: 0;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.remark-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pagination-bar {
  padding: 16px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #f5f5f7;
}

/* ─── 详情抽屉 ─── */
.drawer-content { padding: 0 4px; }
.detail-stats { margin-bottom: 16px; }
.detail-filter {
  display: flex;
  align-items: center;
  padding: 8px 0;
}
</style>
