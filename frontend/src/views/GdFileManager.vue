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
          <el-button @click="refreshList">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 主体 -->
    <el-card class="content-card">
      <div class="content-layout">
        <!-- 左侧目录树 -->
        <div class="tree-panel">
          <div class="tree-header">目录树</div>
          <el-tree
            ref="treeRef"
            :props="treeProps"
            :load="loadTreeNode"
            lazy
            node-key="path"
            highlight-current
            class="dir-tree"
            @node-click="onTreeNodeClick"
          />
        </div>

        <!-- 右侧文件列表 -->
        <div class="list-panel">
          <el-table
            v-loading="loading"
            :data="pagedFileList"
            row-key="path"
            @row-dblclick="onRowDblClick"
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

            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
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

          <div v-if="fileList.length > pageSize" class="pagination-bar">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[20, 50, 100, 200]"
              :total="fileList.length"
              layout="total, sizes, prev, pager, next"
              background
              small
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAccountList } from '@/api/account'
import { listFiles, deleteFile, deleteDir, moveItem, mkdir } from '@/api/gdFileManager'

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
  refreshList()
}

// ---- 文件列表 + 分页 ----
const loading = ref(false)
const fileList = ref([])
const currentPage = ref(1)
const pageSize = ref(50)

const pagedFileList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return fileList.value.slice(start, start + pageSize.value)
})

async function refreshList() {
  if (!selectedAccountId.value) return
  loading.value = true
  try {
    const res = await listFiles(selectedAccountId.value, currentPath.value)
    fileList.value = res.data || []
    currentPage.value = 1
  } catch (e) {
    ElMessage.error('获取文件列表失败')
    fileList.value = []
  } finally {
    loading.value = false
  }
}

function onRowDblClick(row) {
  if (row.isDir) {
    currentPath.value = row.path
    currentPage.value = 1
    refreshList()
  }
}

// ---- 目录树 ----
const treeRef = ref(null)
const treeProps = {
  label: 'name',
  children: 'children',
  isLeaf: (data) => !data.isDir
}

async function loadTreeNode(node, resolve) {
  if (!selectedAccountId.value) return resolve([])
  const path = node.level === 0 ? '' : node.data.path
  try {
    const res = await listFiles(selectedAccountId.value, path)
    const dirs = (res.data || []).filter(f => f.isDir)
    resolve(dirs)
  } catch {
    resolve([])
  }
}

function onTreeNodeClick(data) {
  currentPath.value = data.path
  currentPage.value = 1
  refreshList()
}

// ---- 删除 ----
async function confirmDelete(row) {
  const label = row.isDir ? '目录' : '文件'
  try {
    await ElMessageBox.confirm(
      `确认删除${label} "${row.name}"？此操作不可撤销。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    if (row.isDir) {
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

// ---- 工具函数 ----
function formatSize(bytes) {
  if (bytes == null || bytes < 0) return '—'
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
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

.tree-panel {
  width: 260px;
  flex-shrink: 0;
  border-right: 1px solid #e5e5e7;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.tree-header {
  padding: 12px 16px;
  font-weight: 600;
  font-size: 13px;
  color: #86868b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid #f5f5f7;
  background: #fafafa;
}

.dir-tree {
  flex: 1;
  padding: 8px;
  background: transparent;
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
