<template>
  <div class="gd-file-manager">
    <!-- 工具栏 -->
    <el-card class="toolbar-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-select
            v-model="selectedAccountId"
            placeholder="选择账号"
            style="width: 200px"
            @change="onAccountChange"
          >
            <el-option
              v-for="acc in accounts"
              :key="acc.id"
              :label="acc.accountName"
              :value="acc.id"
            />
          </el-select>

          <!-- 面包屑导航 -->
          <el-breadcrumb separator="/" class="breadcrumb">
            <el-breadcrumb-item>
              <el-link type="primary" @click="navigateTo('')">根目录</el-link>
            </el-breadcrumb-item>
            <el-breadcrumb-item
              v-for="(seg, idx) in pathSegments"
              :key="idx"
            >
              <el-link
                v-if="idx < pathSegments.length - 1"
                type="primary"
                @click="navigateTo(pathSegmentsUpTo(idx))"
              >{{ seg }}</el-link>
              <span v-else>{{ seg }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="toolbar-right">
          <el-button type="primary" @click="showMkdirDialog">
            <el-icon><FolderAdd /></el-icon>
            新建文件夹
          </el-button>
          <el-button type="warning" @click="confirmCleanEmptyDirs" :loading="cleanEmptyLoading">
            <el-icon><Delete /></el-icon>
            清理空文件夹
          </el-button>
          <el-button type="danger" @click="confirmDedupe" :loading="dedupeLoading">
            <el-icon><MagicStick /></el-icon>
            去重合并
          </el-button>
          <el-button @click="refreshList">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 后台格式化任务通知 -->
    <div v-if="bgTask" class="bg-task-bar">
      <el-icon><Loading class="rotating" /></el-icon>
      <span>批量格式化进行中：{{ bgTask.dirPath }}</span>
      <el-button size="small" type="primary" link @click="reopenBgTaskDialog">查看进度</el-button>
      <el-button size="small" type="info" link @click="bgTask = null">忽略</el-button>
    </div>

    <!-- 主体 -->
    <el-card class="content-card">
      <div class="content-layout">
        <!-- 文件列表 -->
        <div class="list-panel">
          <el-table
            v-loading="loading"
            :data="fileList"
            row-key="path"
            @row-click="onRowClick"
            class="file-table"
          >
            <el-table-column label="名称" min-width="280">
              <template #default="{ row }">
                <div class="file-name-cell">
                  <el-icon class="file-icon" :color="row.isDir ? '#007aff' : '#86868b'">
                    <Folder v-if="row.isDir" />
                    <Document v-else />
                  </el-icon>
                  <span :class="{ 'dir-name': row.isDir }">{{ row.name }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="大小" width="120">
              <template #default="{ row }">
                <span v-if="!row.isDir">{{ formatSize(row.size) }}</span>
                <span v-else class="text-muted">—</span>
              </template>
            </el-table-column>

            <el-table-column label="修改时间" width="180">
              <template #default="{ row }">
                {{ formatTime(row.modTime) }}
              </template>
            </el-table-column>

            <el-table-column label="操作" width="360" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="!row.isDir"
                  type="warning"
                  link
                  size="small"
                  @click.stop="openArchiveDialog(row)"
                >归档</el-button>
                <el-button
                  v-if="!row.isDir"
                  type="success"
                  link
                  size="small"
                  @click.stop="openFormatRenameDialog(row)"
                >格式化命名</el-button>
                <el-button
                  v-if="row.isDir"
                  type="success"
                  link
                  size="small"
                  @click.stop="openBatchArchiveDialog(row)"
                >批量归档</el-button>
                <el-button
                  v-if="row.isDir"
                  type="warning"
                  link
                  size="small"
                  @click.stop="openBatchFormatDialog(row)"
                >批量格式化</el-button>
                <el-button
                  v-if="row.isDir"
                  type="info"
                  link
                  size="small"
                  @click.stop="openStrmDialog(row)"
                >生成STRM</el-button>
                <el-button
                  v-if="row.isDir"
                  type="warning"
                  link
                  size="small"
                  @click.stop="confirmDeleteEmptyDir(row)"
                >删除空目录</el-button>
                <el-button
                  v-if="row.isDir"
                  type="danger"
                  link
                  size="small"
                  @click.stop="confirmDedupeDir(row)"
                >去重</el-button>
                <el-button
                  type="primary"
                  link
                  size="small"
                  @click.stop="showRenameDialog(row)"
                >重命名</el-button>
                <el-button
                  type="danger"
                  link
                  size="small"
                  @click.stop="confirmDelete(row)"
                >删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="!loading && fileList.length === 0" class="empty-tip">
            <el-empty description="此目录为空" />
          </div>

          <div v-if="total > 0" class="pagination-bar">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[20, 50, 100, 200]"
              :total="total"
              layout="total, sizes, prev, pager, next"
              background
              small
              @current-change="handlePageChange"
              @size-change="handleSizeChange"
            />
          </div>
        </div>
      </div>
    </el-card>

    <!-- 重命名/移动对话框 -->
    <el-dialog v-model="renameDialogVisible" title="重命名" width="480px" @close="resetRenameDialog">
      <el-form label-width="80px">
        <el-form-item label="原路径">
          <el-input :value="renameForm.oldPath" disabled />
        </el-form-item>
        <el-form-item label="新名称">
          <el-input
            v-model="renameForm.newName"
            placeholder="请输入新名称"
            @keyup.enter="confirmRename"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="renameLoading" @click="confirmRename">确认</el-button>
      </template>
    </el-dialog>

    <!-- 新建文件夹对话框 -->
    <el-dialog v-model="mkdirDialogVisible" title="新建文件夹" width="400px" @close="mkdirName = ''">
      <el-input
        v-model="mkdirName"
        placeholder="请输入文件夹名称"
        @keyup.enter="confirmMkdir"
      />
      <template #footer>
        <el-button @click="mkdirDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="mkdirLoading" @click="confirmMkdir">创建</el-button>
      </template>
    </el-dialog>

    <!-- 归档弹窗 -->
    <ArchiveDialog
      v-model="archiveDialogVisible"
      :file="archiveFile"
    />

    <!-- STRM 生成弹窗 -->
    <StrmDialog
      v-model="strmDialogVisible"
      :gd-remote="strmGdRemote"
      :gd-source-path="strmGdSourcePath"
    />

    <!-- 格式化命名弹窗（单文件） -->
    <FormatRenameDialog
      v-model="formatRenameVisible"
      :file="formatRenameFile"
      @renamed="refreshList"
    />

    <!-- 批量格式化命名弹窗（目录） -->
    <BatchFormatRenameDialog
      v-model="batchFormatVisible"
      :account-id="selectedAccountId"
      :dir-path="batchFormatDirPath"
      :initial-task-id="batchFormatInitTaskId"
      @done="onBatchFormatDone"
      @background="onBatchFormatBackground"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, shallowRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAccountList } from '@/api/account'
import { listFiles, deleteFile, deleteDir, moveItem, mkdir, deleteEmptyDir, cleanEmptyDirs, dedupePath } from '@/api/gdFileManager'
import { startBatchArchive } from '@/api/archive'
import ArchiveDialog from '@/components/ArchiveDialog.vue'
import StrmDialog from '@/components/StrmDialog.vue'
import FormatRenameDialog from '@/components/FormatRenameDialog.vue'
import BatchFormatRenameDialog from '@/components/BatchFormatRenameDialog.vue'

// ---- 账号 ----
const accounts = ref([])
const selectedAccountId = ref(null)

async function loadAccounts() {
  try {
    const res = await getAccountList()
    accounts.value = res.data || []
    if (accounts.value.length > 0 && !selectedAccountId.value) {
      selectedAccountId.value = accounts.value[0].id
      refreshList()
    }
  } catch (e) {
    ElMessage.error('获取账号列表失败')
  }
}

function onAccountChange() {
  currentPath.value = ''
  fileList.value = []
  currentPage.value = 1
  total.value = 0
  refreshList()
}

// ---- 路径 ----
const currentPath = ref('')

const pathSegments = computed(() => {
  if (!currentPath.value) return []
  return currentPath.value.split('/').filter(Boolean)
})

function pathSegmentsUpTo(idx) {
  return pathSegments.value.slice(0, idx + 1).join('/')
}

function navigateTo(path) {
  currentPath.value = path
  currentPage.value = 1
  total.value = 0
  refreshList()
}

// ---- 文件列表 + 服务端分页 ----
const loading = ref(false)
const fileList = ref([])
const currentPage = ref(1)
const pageSize = ref(50)
const total = ref(0)

async function refreshList() {
  if (!selectedAccountId.value) return
  loading.value = true
  try {
    const res = await listFiles(selectedAccountId.value, currentPath.value, currentPage.value, pageSize.value)
    const paged = res.data || {}
    fileList.value = paged.items || []
    total.value = paged.totalCount || 0
  } catch (e) {
    ElMessage.error('获取文件列表失败')
    fileList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handlePageChange(page) {
  currentPage.value = page
  refreshList()
}

function handleSizeChange(size) {
  pageSize.value = size
  currentPage.value = 1
  refreshList()
}

function onRowClick(row) {
  if (row.isDir) {
    currentPath.value = currentPath.value
      ? `${currentPath.value}/${row.name}`
      : row.name
    currentPage.value = 1
    total.value = 0
    refreshList()
  }
}


// ---- 删除 ----
async function confirmDelete(row) {
  const isDir = row.isDir
  const message = isDir
    ? `确认删除目录 "${row.name}"？\n\n⚠️ 此操作将递归删除目录内所有文件，不可撤销！`
    : `确认删除文件 "${row.name}"？此操作不可撤销。`
  try {
    await ElMessageBox.confirm(
      message,
      isDir ? '危险操作 - 删除目录' : '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        confirmButtonClass: isDir ? 'el-button--danger' : ''
      }
    )
    if (isDir) {
      await deleteDir(selectedAccountId.value, row.path)
    } else {
      await deleteFile(selectedAccountId.value, row.path)
    }
    ElMessage.success('删除成功')
    refreshList()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败: ' + (e?.message || e))
  }
}

// ---- 重命名 ----
const renameDialogVisible = ref(false)
const renameLoading = ref(false)
const renameForm = ref({ oldPath: '', newName: '', isDir: false })

function showRenameDialog(row) {
  renameForm.value = { oldPath: row.path, newName: row.name, isDir: row.isDir }
  renameDialogVisible.value = true
}

function resetRenameDialog() {
  renameForm.value = { oldPath: '', newName: '', isDir: false }
  renameLoading.value = false
}

async function confirmRename() {
  const { oldPath, newName, isDir } = renameForm.value
  if (!newName.trim()) {
    ElMessage.warning('名称不能为空')
    return
  }
  // 构造新路径：替换最后一段
  const parentPath = oldPath.includes('/')
    ? oldPath.substring(0, oldPath.lastIndexOf('/'))
    : ''
  const newPath = parentPath ? `${parentPath}/${newName.trim()}` : newName.trim()

  renameLoading.value = true
  try {
    await moveItem(selectedAccountId.value, oldPath, newPath, isDir)
    ElMessage.success('重命名成功')
    renameDialogVisible.value = false
    refreshList()
  } catch (e) {
    ElMessage.error('重命名失败: ' + (e?.message || e))
  } finally {
    renameLoading.value = false
  }
}

// ---- 新建文件夹 ----
const mkdirDialogVisible = ref(false)
const mkdirLoading = ref(false)
const mkdirName = ref('')

function showMkdirDialog() {
  if (!selectedAccountId.value) {
    ElMessage.warning('请先选择账号')
    return
  }
  mkdirName.value = ''
  mkdirDialogVisible.value = true
}

async function confirmMkdir() {
  if (!mkdirName.value.trim()) {
    ElMessage.warning('文件夹名称不能为空')
    return
  }
  const path = currentPath.value
    ? `${currentPath.value}/${mkdirName.value.trim()}`
    : mkdirName.value.trim()

  mkdirLoading.value = true
  try {
    await mkdir(selectedAccountId.value, path)
    ElMessage.success('创建成功')
    mkdirDialogVisible.value = false
    refreshList()
  } catch (e) {
    ElMessage.error('创建失败: ' + (e?.message || e))
  } finally {
    mkdirLoading.value = false
  }
}

// ---- 生成 STRM（目录） ----
const strmDialogVisible  = ref(false)
const strmGdRemote       = ref('')
const strmGdSourcePath   = ref('')

function openStrmDialog(row) {
  const sourcePath = currentPath.value
    ? `${currentPath.value}/${row.name}`
    : row.name
  const account = accounts.value.find(a => a.id === selectedAccountId.value)
  strmGdRemote.value      = account?.rcloneConfigName || ''
  strmGdSourcePath.value  = sourcePath
  strmDialogVisible.value = true
}

// ---- 归档（单文件） ----
const archiveDialogVisible = ref(false)
const archiveFile = ref(null)

function openArchiveDialog(row) {
  const fullPath = currentPath.value
    ? `${currentPath.value}/${row.name}`
    : row.name
  const account = accounts.value.find(a => a.id === selectedAccountId.value)
  archiveFile.value = {
    fileName: row.name,
    filePath: fullPath,
    fileSize: row.size,
    rcloneConfigName: account?.rcloneConfigName || ''
  }
  archiveDialogVisible.value = true
}

// ---- 格式化命名（单文件） ----
const formatRenameVisible = ref(false)
const formatRenameFile = ref(null)

function openFormatRenameDialog(row) {
  const fullPath = currentPath.value
    ? `${currentPath.value}/${row.name}`
    : row.name
  const account = accounts.value.find(a => a.id === selectedAccountId.value)
  formatRenameFile.value = {
    fileName: row.name,
    filePath: fullPath,
    fileSize: row.size,
    accountId: selectedAccountId.value,
    rcloneConfigName: account?.rcloneConfigName || '',
  }
  formatRenameVisible.value = true
}

// ---- 批量格式化命名（目录） ----
const batchFormatVisible     = ref(false)
const batchFormatDirPath     = ref('')
const batchFormatInitTaskId  = ref(null)
const bgTask                 = shallowRef(null)  // { taskId, dirPath, accountId }

function openBatchFormatDialog(row) {
  batchFormatDirPath.value    = currentPath.value
    ? `${currentPath.value}/${row.name}`
    : row.name
  batchFormatInitTaskId.value = null
  batchFormatVisible.value    = true
}

function reopenBgTaskDialog() {
  batchFormatDirPath.value    = bgTask.value.dirPath
  batchFormatInitTaskId.value = bgTask.value.taskId
  batchFormatVisible.value    = true
}

function onBatchFormatBackground(info) {
  bgTask.value             = info
  batchFormatVisible.value = false
}

function onBatchFormatDone() {
  bgTask.value = null
  refreshList()
}

// ---- 删除空目录（单个） ----
async function confirmDeleteEmptyDir(row) {
  const dirPath = currentPath.value
    ? `${currentPath.value}/${row.name}`
    : row.name
  try {
    await ElMessageBox.confirm(
      `将检查目录 "${row.name}" 及其所有子目录，只有完全没有文件时才会删除。\n\n确认检查并删除空目录？`,
      '删除空目录',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  try {
    const res = await deleteEmptyDir(selectedAccountId.value, dirPath)
    if (res.data?.deleted) {
      ElMessage.success(`空目录 "${row.name}" 已删除`)
      refreshList()
    } else {
      ElMessage.info(`目录 "${row.name}" 非空，未删除`)
    }
  } catch (e) {
    ElMessage.error('删除失败: ' + (e?.message || e))
  }
}

// ---- 批量清理空文件夹 ----
const cleanEmptyLoading = ref(false)

async function confirmCleanEmptyDirs() {
  if (!selectedAccountId.value) {
    ElMessage.warning('请先选择账号')
    return
  }
  const pathDesc = currentPath.value || '根目录'
  try {
    await ElMessageBox.confirm(
      `将扫描 "${pathDesc}" 下的所有子文件夹，递归检查每个文件夹是否包含文件。\n\n只删除完全没有文件的空文件夹，不会误删任何包含文件的目录。\n\n确认开始扫描？`,
      '批量清理空文件夹',
      { confirmButtonText: '开始扫描', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  cleanEmptyLoading.value = true
  try {
    const res = await cleanEmptyDirs(selectedAccountId.value, currentPath.value)
    const data = res.data || {}
    const deletedList = data.deleted || []
    if (deletedList.length > 0) {
      ElMessage.success(`已清理 ${deletedList.length} 个空文件夹: ${deletedList.join(', ')}`)
      refreshList()
    } else {
      ElMessage.info(`扫描了 ${data.total || 0} 个子文件夹，没有发现空文件夹`)
    }
  } catch (e) {
    ElMessage.error('清理失败: ' + (e?.message || e))
  } finally {
    cleanEmptyLoading.value = false
  }
}

// ---- 去重合并（解决GD同名文件夹问题） ----
const dedupeLoading = ref(false)

async function confirmDedupe() {
  if (!selectedAccountId.value) {
    ElMessage.warning('请先选择账号')
    return
  }
  const pathDesc = currentPath.value || '根目录'
  try {
    await ElMessageBox.confirm(
      `将对 "${pathDesc}" 执行去重合并操作：\n\n` +
      `1. 合并同名文件夹（将内容移到一个文件夹中，删除空的重复目录）\n` +
      `2. 清理重复文件（同名文件保留最新的）\n\n` +
      `此操作针对 Google Drive 上因并发创建产生的同名文件夹问题。`,
      '去重合并',
      { confirmButtonText: '开始去重', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  dedupeLoading.value = true
  try {
    const res = await dedupePath(selectedAccountId.value, currentPath.value)
    if (res.data?.success) {
      ElMessage.success('去重合并完成')
      refreshList()
    } else {
      ElMessage.error('去重失败: ' + (res.data?.message || '未知错误'))
    }
  } catch (e) {
    ElMessage.error('去重失败: ' + (e?.message || e))
  } finally {
    dedupeLoading.value = false
  }
}

async function confirmDedupeDir(row) {
  const dirPath = currentPath.value
    ? `${currentPath.value}/${row.name}`
    : row.name
  try {
    await ElMessageBox.confirm(
      `将对目录 "${row.name}" 执行去重合并：\n\n` +
      `合并该目录下的同名子文件夹，清理重复文件（保留最新）。`,
      '目录去重',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  try {
    const res = await dedupePath(selectedAccountId.value, dirPath)
    if (res.data?.success) {
      ElMessage.success(`目录 "${row.name}" 去重完成`)
      refreshList()
    } else {
      ElMessage.error('去重失败: ' + (res.data?.message || '未知错误'))
    }
  } catch (e) {
    ElMessage.error('去重失败: ' + (e?.message || e))
  }
}

// ---- 批量归档（目录） ----
async function openBatchArchiveDialog(row) {
  const sourcePath = currentPath.value
    ? `${currentPath.value}/${row.name}`
    : row.name

  const taskNamePreview = `批量归档_${row.name}_${new Date().toISOString().slice(0,10).replace(/-/g,'')}`

  try {
    await ElMessageBox.confirm(
      `将对以下目录及其所有子目录中的媒体文件启动自动归档任务：\n\n` +
      `📁 目录：${sourcePath}\n` +
      `📋 任务名：${taskNamePreview}_HHMMSS\n\n` +
      `系统将自动完成：文件名解析 → TMDB 匹配 → 执行归档\n` +
      `无法自动匹配的文件会标记为"待人工处理"，可在归档管理页面查看进度。`,
      '确认启动批量归档',
      {
        confirmButtonText: '启动任务',
        cancelButtonText: '取消',
        type: 'info',
        dangerouslyUseHTMLString: false
      }
    )
  } catch {
    return
  }

  try {
    const res = await startBatchArchive(selectedAccountId.value, sourcePath)
    ElMessage.success(`批量归档任务已创建：${res.data?.taskName || taskNamePreview}，请前往归档管理查看进度`)
  } catch (e) {
    ElMessage.error('创建批量归档任务失败：' + (e?.message || e))
  }
}

// ---- 工具函数 ----
function formatSize(bytes) {
  if (bytes == null || bytes < 0) return '—'
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + units[i]
}

function formatTime(modTime) {
  if (!modTime) return '—'
  try {
    return new Date(modTime).toLocaleString('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit'
    })
  } catch {
    return modTime
  }
}

onMounted(loadAccounts)
</script>

<style scoped>
.gd-file-manager {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.bg-task-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  background: #ecf5ff;
  border: 1px solid #b3d8ff;
  border-radius: 8px;
  font-size: 13px;
  color: #409eff;
}
.bg-task-bar span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rotating {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

.toolbar-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
  min-width: 0;
}

.toolbar-right {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.breadcrumb {
  font-size: 14px;
  flex: 1;
  min-width: 0;
}

.content-card :deep(.el-card__body) {
  padding: 0;
}

.content-layout {
  display: flex;
  height: calc(100vh - 260px);
  min-height: 400px;
}

.list-panel {
  flex: 1;
  overflow: auto;
}

.file-table {
  border: none;
  border-radius: 0;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.dir-name {
  font-weight: 500;
  color: #1d1d1f;
  cursor: pointer;
}

.dir-name:hover {
  color: #007aff;
}

.text-muted {
  color: #86868b;
}

.empty-tip {
  padding: 40px;
  text-align: center;
}

.pagination-bar {
  padding: 12px 16px;
  border-top: 1px solid #f5f5f7;
  display: flex;
  justify-content: flex-end;
}
</style>
