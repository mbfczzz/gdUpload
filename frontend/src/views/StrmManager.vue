<template>
  <div class="strm-manager-page">

    <!-- ─── 监控配置列表 ─── -->
    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon><Monitor /></el-icon>
            <span>STRM 监控配置</span>
          </div>
          <div class="header-right">
            <el-button size="small" @click="loadConfigs" :loading="configLoading">
              <el-icon><Refresh /></el-icon>
            </el-button>
            <el-button type="primary" size="small" @click="openAddDialog">
              <el-icon><Plus /></el-icon>
              添加监控目录
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="configLoading"
        :data="configs"
        border
        size="small"
        row-key="id"
        @expand-change="onExpandChange"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-panel">
              <!-- 同步进度 -->
              <div v-if="syncStatus[row.id] && syncStatus[row.id].phase === 'RUNNING'" class="sync-progress">
                <div class="sync-header">
                  <el-tag type="" size="small">同步中</el-tag>
                  <span class="sync-stat">
                    新增 {{ syncStatus[row.id].newCount }}
                    · 删除 {{ syncStatus[row.id].deletedCount }}
                    · 更新 {{ syncStatus[row.id].updatedCount }}
                    · 失败 {{ syncStatus[row.id].failedCount }}
                    · 共 {{ syncStatus[row.id].total }} 文件
                  </span>
                </div>
                <el-progress
                  v-if="syncStatus[row.id].total > 0"
                  :percentage="Math.round(syncStatus[row.id].processed / syncStatus[row.id].total * 100)"
                  :stroke-width="8"
                  style="margin: 6px 0;"
                />
                <div v-if="syncStatus[row.id].currentFile" class="current-file">
                  ▶ {{ syncStatus[row.id].currentFile }}
                </div>
                <el-scrollbar height="180px" class="log-scrollbar" :ref="el => logScrollbars[row.id] = el">
                  <div class="log-container">
                    <div
                      v-for="(line, i) in syncStatus[row.id].logs"
                      :key="i"
                      :class="['log-line', line.includes('✗') ? 'log-error' : line.includes('✓') ? 'log-success' : '']"
                    >{{ line }}</div>
                  </div>
                </el-scrollbar>
              </div>

              <!-- 最近同步日志（DONE/ERROR 后仍显示） -->
              <div v-else-if="syncStatus[row.id] && syncStatus[row.id].phase !== 'IDLE'" class="sync-done">
                <div class="sync-header">
                  <el-tag :type="syncStatus[row.id].phase === 'DONE' ? 'success' : 'danger'" size="small">
                    {{ syncStatus[row.id].phase === 'DONE' ? '已完成' : '出错' }}
                  </el-tag>
                  <span class="sync-stat">
                    新增 {{ syncStatus[row.id].newCount }}
                    · 删除 {{ syncStatus[row.id].deletedCount }}
                    · 更新 {{ syncStatus[row.id].updatedCount }}
                    · 失败 {{ syncStatus[row.id].failedCount }}
                  </span>
                </div>
                <el-scrollbar height="140px" class="log-scrollbar">
                  <div class="log-container">
                    <div
                      v-for="(line, i) in syncStatus[row.id].logs.slice(-50)"
                      :key="i"
                      :class="['log-line', line.includes('✗') ? 'log-error' : line.includes('✓') ? 'log-success' : '']"
                    >{{ line }}</div>
                  </div>
                </el-scrollbar>
              </div>

              <!-- 文件记录 -->
              <div class="record-section">
                <div class="record-header">
                  <span class="section-title">文件记录</span>
                  <div class="record-filters">
                    <el-radio-group v-model="recordFilter[row.id]" size="small" @change="loadRecords(row.id)">
                      <el-radio-button label="">全部</el-radio-button>
                      <el-radio-button label="success">成功</el-radio-button>
                      <el-radio-button label="failed">失败</el-radio-button>
                      <el-radio-button label="deleted">已删除</el-radio-button>
                    </el-radio-group>
                  </div>
                </div>
                <el-table
                  v-loading="recordLoading[row.id]"
                  :data="records[row.id]"
                  border
                  size="small"
                  style="margin-top: 8px;"
                >
                  <el-table-column label="文件路径" min-width="300" show-overflow-tooltip>
                    <template #default="{ row: r }">
                      <span class="mono-text">{{ r.relFilePath }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="状态" width="80">
                    <template #default="{ row: r }">
                      <el-tag :type="recordTagType(r.status)" size="small">{{ recordStatusLabel(r.status) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="TMDB" width="80">
                    <template #default="{ row: r }">
                      <span v-if="r.tmdbId" class="mono-text">{{ r.tmdbId }}</span>
                      <span v-else class="text-muted">—</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="本地 STRM" min-width="200" show-overflow-tooltip>
                    <template #default="{ row: r }">
                      <span v-if="r.strmLocalPath" class="mono-text small-text">{{ r.strmLocalPath }}</span>
                      <span v-else-if="r.status === 'deleted'" class="text-muted">已删除</span>
                      <span v-else-if="r.failReason" class="text-danger small-text" :title="r.failReason">
                        {{ r.failReason.slice(0, 60) }}
                      </span>
                      <span v-else class="text-muted">—</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="处理时间" width="140">
                    <template #default="{ row: r }">
                      <span class="small-text">{{ formatTime(r.updateTime) }}</span>
                    </template>
                  </el-table-column>
                </el-table>
                <div class="record-pagination">
                  <el-pagination
                    v-if="recordTotal[row.id] > 20"
                    background
                    size="small"
                    layout="prev, pager, next"
                    :total="recordTotal[row.id]"
                    :page-size="20"
                    v-model:current-page="recordPage[row.id]"
                    @current-change="loadRecords(row.id)"
                  />
                </div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="名称" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="name-cell">
              <span>{{ row.name }}</span>
              <el-tag v-if="!row.enabled" type="info" size="small" style="margin-left: 4px;">已停</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="GD 路径" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono-text small-text">{{ row.gdRemote }}:{{ row.gdSourcePath }}</span>
          </template>
        </el-table-column>

        <el-table-column label="间隔" width="70">
          <template #default="{ row }">
            <span>{{ row.scanIntervalMinutes }}m</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="configStatusType(row)" size="small">{{ configStatusLabel(row) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="文件数" width="70">
          <template #default="{ row }">
            <span>{{ row.totalFiles ?? 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="上次扫描" width="150">
          <template #default="{ row }">
            <span class="small-text">{{ formatTime(row.lastScanTime) || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="下次扫描" width="150">
          <template #default="{ row }">
            <span class="small-text">{{ formatTime(row.nextScanTime) || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button
                size="small"
                type="primary"
                :loading="row.status === 'RUNNING'"
                :disabled="row.status === 'RUNNING'"
                @click.stop="doSync(row)"
              >立即同步</el-button>
              <el-button
                size="small"
                type="warning"
                :disabled="row.status === 'RUNNING'"
                @click.stop="doForceRescrape(row)"
              >强制重刮</el-button>
              <el-button size="small" @click.stop="openEditDialog(row)">编辑</el-button>
              <el-button
                size="small"
                :type="row.enabled ? 'info' : 'success'"
                @click.stop="toggleEnable(row)"
              >{{ row.enabled ? '停用' : '启用' }}</el-button>
              <el-button size="small" type="danger" @click.stop="doDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ─── 新增/编辑配置 Dialog ─── -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'add' ? '添加监控目录' : '编辑监控配置'"
      width="580px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="110px" size="default">
        <el-form-item label="配置名称" prop="name">
          <el-input v-model="form.name" placeholder="例：日剧监控" />
        </el-form-item>
        <el-form-item label="GD 远程名" prop="gdRemote">
          <el-input v-model="form.gdRemote" placeholder="例：gdrive" />
        </el-form-item>
        <el-form-item label="GD 源目录" prop="gdSourcePath">
          <el-input v-model="form.gdSourcePath" placeholder="例：video/日剧" />
        </el-form-item>
        <el-form-item label="本地输出路径" prop="outputPath">
          <el-input v-model="form.outputPath" placeholder="例：/data/strm/日剧" />
        </el-form-item>
        <el-form-item label="播放 URL 前缀" prop="playUrlBase">
          <el-input v-model="form.playUrlBase" placeholder="例：http://192.168.1.1:8080/gd" />
        </el-form-item>
        <el-form-item label="扫描间隔(分钟)" prop="scanIntervalMinutes">
          <el-input-number v-model="form.scanIntervalMinutes" :min="5" :max="10080" style="width:140px" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listConfigs, addConfig, updateConfig, deleteConfig,
  enableConfig, disableConfig, triggerSync, triggerForceRescrape,
  listRecords, getSyncStatus
} from '@/api/strmWatch'

// ─── 配置列表 ─────────────────────────────────────────────────────────────────

const configs       = ref([])
const configLoading = ref(false)

async function loadConfigs() {
  configLoading.value = true
  try {
    const res = await listConfigs(1, 100)
    configs.value = (res.data?.records || res.data || [])
  } catch (e) {
    ElMessage.error('加载配置失败')
  } finally {
    configLoading.value = false
  }
}

// ─── 展开行 & 文件记录 ────────────────────────────────────────────────────────

const records      = reactive({})  // configId → array
const recordTotal  = reactive({})  // configId → total
const recordPage   = reactive({})  // configId → page
const recordFilter = reactive({})  // configId → status filter
const recordLoading = reactive({})

async function onExpandChange(row, expandedRows) {
  const id = row.id
  const isExpanded = expandedRows.some(r => r.id === id)
  if (isExpanded) {
    if (!recordFilter[id]) recordFilter[id] = ''
    if (!recordPage[id])   recordPage[id]   = 1
    await loadRecords(id)
    // Start polling if running
    ensurePolling(id, row.status === 'RUNNING')
  }
}

async function loadRecords(configId) {
  recordLoading[configId] = true
  try {
    const page   = recordPage[configId]   || 1
    const filter = recordFilter[configId] || ''
    const res = await listRecords(configId, filter, page, 20)
    records[configId]     = res.data?.records || []
    recordTotal[configId] = res.data?.total   || 0
  } catch (e) {
    /* 静默 */
  } finally {
    recordLoading[configId] = false
  }
}

// ─── 同步状态轮询 ─────────────────────────────────────────────────────────────

const syncStatus  = reactive({}) // configId → SyncStatus
const logScrollbars = reactive({})
const pollTimers  = {}           // configId → intervalId

function ensurePolling(configId, startNow) {
  if (pollTimers[configId]) return
  if (!startNow && (!syncStatus[configId] || syncStatus[configId].phase !== 'RUNNING')) return
  startPolling(configId)
}

function startPolling(configId) {
  if (pollTimers[configId]) return
  pollTimers[configId] = setInterval(async () => {
    try {
      const res = await getSyncStatus(configId)
      syncStatus[configId] = res.data
      await scrollLogs(configId)
      if (res.data.phase !== 'RUNNING') {
        stopPolling(configId)
        // Refresh config row stats
        await loadConfigs()
        // Refresh records if expanded
        if (records[configId] !== undefined) await loadRecords(configId)
      }
    } catch { /* 静默 */ }
  }, 2000)
}

function stopPolling(configId) {
  if (pollTimers[configId]) {
    clearInterval(pollTimers[configId])
    delete pollTimers[configId]
  }
}

function stopAllPolling() {
  Object.keys(pollTimers).forEach(stopPolling)
}

async function scrollLogs(configId) {
  await nextTick()
  const sb = logScrollbars[configId]
  if (sb) sb.setScrollTop(999999)
}

// ─── 操作 ─────────────────────────────────────────────────────────────────────

async function doSync(row) {
  try {
    await triggerSync(row.id)
    ElMessage.success('增量同步已启动')
    row.status = 'RUNNING'
    syncStatus[row.id] = { phase: 'RUNNING', total: 0, processed: 0, newCount: 0, deletedCount: 0, updatedCount: 0, failedCount: 0, currentFile: '', logs: [] }
    startPolling(row.id)
  } catch (e) {
    ElMessage.error('启动失败: ' + (e?.message || e))
  }
}

async function doForceRescrape(row) {
  try {
    await ElMessageBox.confirm('将对该目录下所有文件强制重新刮削，覆盖现有 NFO/图片，确认继续？', '强制重刮', {
      type: 'warning'
    })
  } catch { return }
  try {
    await triggerForceRescrape(row.id)
    ElMessage.success('强制重刮已启动')
    row.status = 'RUNNING'
    syncStatus[row.id] = { phase: 'RUNNING', total: 0, processed: 0, newCount: 0, deletedCount: 0, updatedCount: 0, failedCount: 0, currentFile: '', logs: [] }
    startPolling(row.id)
  } catch (e) {
    ElMessage.error('启动失败: ' + (e?.message || e))
  }
}

async function toggleEnable(row) {
  try {
    if (row.enabled) {
      await disableConfig(row.id)
      row.enabled = 0
      ElMessage.success('已停用')
    } else {
      await enableConfig(row.id)
      row.enabled = 1
      ElMessage.success('已启用')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function doDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除监控配置「${row.name}」？\n将同时删除所有文件记录（本地 strm 文件不受影响）。`,
      '删除确认', { type: 'warning' }
    )
  } catch { return }
  try {
    await deleteConfig(row.id)
    ElMessage.success('已删除')
    stopPolling(row.id)
    await loadConfigs()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

// ─── 新增/编辑 Dialog ─────────────────────────────────────────────────────────

const dialogVisible = ref(false)
const dialogMode    = ref('add')   // 'add' | 'edit'
const saving        = ref(false)
const formRef       = ref(null)
const form          = reactive({
  id: null,
  name: '',
  gdRemote: '',
  gdSourcePath: '',
  outputPath: '',
  playUrlBase: '',
  scanIntervalMinutes: 60,
  enabled: 1
})
const formRules = {
  name:                 [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  gdRemote:             [{ required: true, message: '请输入 GD 远程名', trigger: 'blur' }],
  gdSourcePath:         [{ required: true, message: '请输入 GD 源目录', trigger: 'blur' }],
  outputPath:           [{ required: true, message: '请输入本地输出路径', trigger: 'blur' }],
  playUrlBase:          [{ required: true, message: '请输入播放 URL 前缀', trigger: 'blur' }],
  scanIntervalMinutes:  [{ required: true, message: '请输入扫描间隔', trigger: 'blur' }]
}

function openAddDialog() {
  dialogMode.value = 'add'
  Object.assign(form, { id: null, name: '', gdRemote: '', gdSourcePath: '', outputPath: '', playUrlBase: '', scanIntervalMinutes: 60, enabled: 1 })
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEditDialog(row) {
  dialogMode.value = 'edit'
  Object.assign(form, {
    id: row.id,
    name: row.name,
    gdRemote: row.gdRemote,
    gdSourcePath: row.gdSourcePath,
    outputPath: row.outputPath,
    playUrlBase: row.playUrlBase,
    scanIntervalMinutes: row.scanIntervalMinutes,
    enabled: row.enabled
  })
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function saveConfig() {
  try {
    await formRef.value.validate()
  } catch {
    return // 验证不通过，Element Plus 已高亮错误字段
  }
  saving.value = true
  try {
    if (dialogMode.value === 'add') {
      await addConfig({ ...form })
      ElMessage.success('已添加')
    } else {
      await updateConfig(form.id, { ...form })
      ElMessage.success('已保存')
    }
    dialogVisible.value = false
    await loadConfigs()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e?.message || e))
  } finally {
    saving.value = false
  }
}

// ─── 辅助 ─────────────────────────────────────────────────────────────────────

function configStatusType(row) {
  if (row.status === 'RUNNING') return ''
  return row.enabled ? 'success' : 'info'
}

function configStatusLabel(row) {
  if (row.status === 'RUNNING') return '同步中'
  return row.enabled ? '监控中' : '已停用'
}

function recordTagType(status) {
  return { success: 'success', failed: 'danger', deleted: 'info' }[status] || 'info'
}

function recordStatusLabel(status) {
  return { success: '成功', failed: '失败', deleted: '已删除' }[status] || status
}

function formatTime(dt) {
  if (!dt) return ''
  const d = new Date(dt)
  if (isNaN(d)) return dt
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
    + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// ─── 生命周期 ─────────────────────────────────────────────────────────────────

onMounted(loadConfigs)
onUnmounted(stopAllPolling)
</script>

<style scoped>
.strm-manager-page {
  padding: 0;
}

.config-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.header-right {
  display: flex;
  gap: 8px;
}

.name-cell {
  display: flex;
  align-items: center;
}

.action-btns {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.mono-text {
  font-family: monospace;
  font-size: 12px;
}

.small-text {
  font-size: 12px;
}

.text-muted {
  color: #909399;
  font-size: 12px;
}

.text-danger {
  color: #f56c6c;
  font-size: 12px;
}

/* ─── 展开面板 ─── */
.expand-panel {
  padding: 12px 16px;
  background: #fafafa;
}

.sync-progress,
.sync-done {
  margin-bottom: 12px;
  padding: 10px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.sync-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.sync-stat {
  font-size: 12px;
  color: #606266;
}

.current-file {
  font-size: 12px;
  color: #909399;
  margin: 4px 0;
  font-family: monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.log-scrollbar {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #1e1e1e;
  margin-top: 6px;
}

.log-container {
  padding: 6px 8px;
}

.log-line {
  font-family: monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #d4d4d4;
  white-space: pre-wrap;
  word-break: break-all;
}

.log-line.log-success { color: #73c990; }
.log-line.log-error   { color: #f44747; }

/* ─── 文件记录 ─── */
.record-section {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 10px;
}

.record-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.record-pagination {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}
</style>
