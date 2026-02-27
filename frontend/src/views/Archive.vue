<template>
  <div class="archive-page">
    <!-- 快速归档 -->
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

    <!-- 归档历史 -->
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
                type="primary"
                link
                size="small"
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
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          small
          @change="loadHistory"
        />
      </div>
    </el-card>

    <!-- 归档弹窗 -->
    <ArchiveDialog
      v-model="dialogVisible"
      :file="currentFile"
      @archived="onArchived"
    />

    <!-- 备注编辑弹窗 -->
    <el-dialog v-model="remarkDialogVisible" title="编辑备注" width="420px">
      <el-input
        v-model="remarkInput"
        type="textarea"
        :rows="3"
        placeholder="请输入备注..."
      />
      <template #footer>
        <el-button @click="remarkDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="remarkLoading" @click="submitRemark">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { FolderChecked, Document, Clock, Refresh } from '@element-plus/icons-vue'
import { getArchiveHistory, updateRemark } from '@/api/archive'
import ArchiveDialog from '@/components/ArchiveDialog.vue'

// ---- 快速归档 ----
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
}

// ---- 归档历史 ----
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

// ---- 备注编辑 ----
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

// ---- 工具函数 ----
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

onMounted(loadHistory)
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

.quick-form {
  display: flex;
  gap: 12px;
  align-items: center;
}

.path-input {
  flex: 1;
}

.filename {
  font-weight: 500;
  color: #1d1d1f;
}

.new-path {
  font-family: monospace;
  font-size: 12px;
  color: #67c23a;
}

.tmdb-id {
  font-family: monospace;
  font-size: 12px;
  color: #909399;
}

.text-muted {
  color: #c0c4cc;
}

.fail-reason {
  color: #ff3b30;
  font-size: 12px;
}

.remark-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.remark-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: #606266;
}

.pagination-bar {
  padding: 16px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #f5f5f7;
}
</style>
