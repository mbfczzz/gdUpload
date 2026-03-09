<template>
  <el-dialog
    v-model="visible"
    title="批量格式化命名"
    width="600px"
    :close-on-click-modal="false"
    :close-on-press-escape="phase === 'IDLE'"
    destroy-on-close
    @closed="onClosed"
  >
    <!-- 目录信息 -->
    <div class="dir-info">
      <el-icon><Folder /></el-icon>
      <span class="dir-path">{{ dirPath || '根目录' }}</span>
    </div>

    <!-- IDLE：说明 -->
    <div v-if="phase === 'IDLE'" class="desc-box">
      <p>将递归扫描该目录下的所有媒体文件，对<strong>文件名中缺少视频编码信息</strong>的文件：</p>
      <ol>
        <li>通过 <code>ffprobe</code> 实际探测视频编码（HEVC / AVC 等）</li>
        <li>重新生成标准命名（保留原有的剧名、季集、分辨率等）</li>
        <li>在原目录就地重命名，不移动文件</li>
      </ol>
      <el-alert type="warning" :closable="false" style="margin-top: 8px">
        ffprobe 探测需下载每个文件头部数据（约 15MB/文件），文件数量多时耗时较长，请耐心等待。
        任务会记录到「格式化命名」模块，可在那里查看完整历史。
      </el-alert>
    </div>

    <!-- RUNNING / DONE / ERROR：进度 -->
    <div v-else class="progress-box">
      <!-- 总进度条 -->
      <div class="progress-row">
        <el-progress
          :percentage="percent"
          :status="progressStatus"
          :stroke-width="10"
          style="flex:1"
        />
        <span class="progress-text">{{ processed }} / {{ total }}</span>
      </div>

      <!-- 统计标签 -->
      <div class="stat-row">
        <el-tag type="success" size="small">✓ 重命名 {{ renamed }}</el-tag>
        <el-tag type="info"    size="small">⊘ 跳过   {{ skipped }}</el-tag>
        <el-tag type="danger"  size="small">✗ 失败   {{ failed }}</el-tag>
        <el-tag v-if="phase === 'RUNNING'" type="warning" size="small">
          <el-icon class="rotating"><Loading /></el-icon> 进行中
        </el-tag>
        <el-tag v-else-if="phase === 'PAUSING'" type="warning" size="small">
          <el-icon class="rotating"><Loading /></el-icon> 暂停中...
        </el-tag>
        <el-tag v-else-if="phase === 'PAUSED'" type="info" size="small">⏸ 已暂停</el-tag>
        <el-tag v-else-if="phase === 'DONE'"   type="success" size="small">完成</el-tag>
        <el-tag v-else-if="phase === 'ERROR'"  type="danger"  size="small">失败/取消</el-tag>
      </div>

      <!-- 当前文件 -->
      <div v-if="currentFile && (phase === 'RUNNING' || phase === 'PAUSED' || phase === 'PAUSING')" class="current-file">
        {{ phase === 'PAUSING' ? '⏳' : '▶' }} {{ currentFile }}
      </div>

      <!-- 跳转提示 -->
      <div class="detail-tip">
        <el-icon><InfoFilled /></el-icon>
        可前往「格式化命名」模块查看每个文件的处理详情
      </div>
    </div>

    <template #footer>
      <template v-if="phase === 'IDLE'">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="starting" @click="startTask">开始格式化</el-button>
      </template>
      <template v-else-if="phase === 'RUNNING' || phase === 'PAUSED' || phase === 'PAUSING'">
        <el-button @click="runInBackground">后台运行</el-button>
        <el-button
          v-if="phase === 'RUNNING'"
          type="info"
          @click="pauseTask"
        >暂停</el-button>
        <el-button
          v-if="phase === 'PAUSED'"
          type="success"
          @click="doResumeTask"
        >恢复</el-button>
        <el-button type="warning" @click="cancelTask">取消任务</el-button>
      </template>
      <template v-else>
        <el-button type="primary" @click="visible = false">关闭</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Folder, Loading, InfoFilled } from '@element-plus/icons-vue'
import { startFormatRenameTask, getFormatRenameTask, cancelFormatRenameTask, pauseFormatRenameTask, resumeFormatRenameTask } from '@/api/formatRename'

const props = defineProps({
  modelValue:    Boolean,
  accountId:     [Number, String],
  dirPath:       String,
  initialTaskId: { type: [String, Number], default: null },
})
const emit = defineEmits(['update:modelValue', 'done', 'background'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v),
})

// ── 状态 ──────────────────────────────────────────────────────────────────────
const phase       = ref('IDLE')   // IDLE / RUNNING / PAUSED / DONE / ERROR
const taskId      = ref(null)
const total       = ref(0)
const processed   = ref(0)
const renamed     = ref(0)
const skipped     = ref(0)
const failed      = ref(0)
const currentFile = ref('')
const starting    = ref(false)
let   pollTimer   = null

const percent = computed(() => {
  if (!total.value) return 0
  return Math.min(100, Math.round(processed.value / total.value * 100))
})

const progressStatus = computed(() => {
  if (phase.value === 'DONE')  return 'success'
  if (phase.value === 'ERROR') return 'exception'
  return undefined
})

// 打开时：有 initialTaskId 则恢复后台任务，否则重置为 IDLE
watch(visible, v => {
  if (!v) return
  if (props.initialTaskId) {
    taskId.value = props.initialTaskId
    phase.value  = 'RUNNING'
    startPolling()
  } else {
    phase.value       = 'IDLE'
    taskId.value      = null
    total.value       = 0
    processed.value   = 0
    renamed.value     = 0
    skipped.value     = 0
    failed.value      = 0
    currentFile.value = ''
  }
})

// ── 启���任务（调用 FormatRename 接口，任务会持久化到 DB）─────────────────────
async function startTask() {
  starting.value = true
  try {
    const res = await startFormatRenameTask(props.accountId, props.dirPath || '')
    const task = res.data
    if (!task?.id) { ElMessage.error('启动失败'); return }
    taskId.value = task.id
    phase.value  = 'RUNNING'
    startPolling()
  } catch (e) {
    ElMessage.error('启动失败: ' + (e?.message || e))
  } finally {
    starting.value = false
  }
}

// ── 取消任务 ───────────────────────────────────────────────────────────────────
async function cancelTask() {
  if (!taskId.value) return
  try {
    await cancelFormatRenameTask(taskId.value)
  } catch { /* 静默 */ }
}

// ── 暂停任务 ───────────────────────────────────────────────────────────────────
async function pauseTask() {
  if (!taskId.value) return
  try {
    await pauseFormatRenameTask(taskId.value)
    phase.value = 'PAUSING'
  } catch { /* 静默 */ }
}

// ── 恢复任务 ───────────────────────────────────────────────────────────────────
async function doResumeTask() {
  if (!taskId.value) return
  try {
    await resumeFormatRenameTask(taskId.value)
    phase.value = 'RUNNING'
  } catch { /* 静默 */ }
}

// ── 后台运行（关闭弹窗，任务继续） ────────────────────────────────────────────
function runInBackground() {
  stopPolling()
  emit('background', { taskId: taskId.value, dirPath: props.dirPath, accountId: props.accountId })
  visible.value = false
}

// ── 轮询进度（改为查询 FormatRenameTask，字段名映射） ────────────────────────
function startPolling() {
  stopPolling()
  pollTimer = setInterval(poll, 2000)
  poll()
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

/** FormatRenameTask.status → dialog phase */
function mapStatus(status) {
  if (status === 'COMPLETED') return 'DONE'
  if (status === 'FAILED')    return 'ERROR'
  if (status === 'PAUSED')    return 'PAUSED'
  if (status === 'PAUSING')   return 'PAUSING'
  return 'RUNNING'  // PENDING / RUNNING
}

async function poll() {
  if (!taskId.value) return
  try {
    const res  = await getFormatRenameTask(taskId.value)
    const data = res.data

    if (!data) {
      stopPolling()
      phase.value = 'ERROR'
      return
    }

    phase.value       = mapStatus(data.status)
    total.value       = data.totalFiles     || 0
    processed.value   = data.processedFiles || 0
    renamed.value     = data.renamedCount   || 0
    skipped.value     = data.skippedCount   || 0
    failed.value      = data.failedCount    || 0
    currentFile.value = data.currentFile    || ''

    if (phase.value === 'DONE' || phase.value === 'ERROR') {
      stopPolling()
      if (phase.value === 'DONE' && renamed.value > 0) emit('done')
    }
  } catch { /* 网络抖动，静默 */ }
}

function onClosed() {
  stopPolling()
  // 关闭弹窗不取消任务，任务在后台继续运行
}
</script>

<style scoped>
.dir-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f5f5f7;
  border-radius: 8px;
  margin-bottom: 16px;
  font-size: 13px;
}
.dir-path {
  font-weight: 500;
  word-break: break-all;
  font-family: monospace;
}

.desc-box {
  font-size: 13px;
  color: #606266;
  line-height: 1.8;
}
.desc-box ol {
  padding-left: 20px;
  margin: 8px 0;
}

.progress-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.progress-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.progress-text {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
.stat-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.current-file {
  font-size: 12px;
  color: #e6a23c;
  font-family: monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.detail-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
}

.rotating {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
</style>
