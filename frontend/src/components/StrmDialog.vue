<template>
  <el-dialog
    :model-value="modelValue"
    :title="`生成 STRM — ${gdSourcePath}`"
    width="720px"
    :close-on-click-modal="false"
    :close-on-press-escape="!isRunning"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="onOpen"
    @close="onClose"
  >
    <!-- 配置预览 -->
    <el-descriptions :column="2" border size="small" class="config-preview">
      <el-descriptions-item label="GD 远程">
        <el-tag size="small">{{ gdRemote }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="源目录">{{ gdSourcePath }}</el-descriptions-item>
      <el-descriptions-item label="本地输出路径">
        <span v-if="strmConfig.strmOutputPath" class="path-text">{{ strmConfig.strmOutputPath }}</span>
        <el-text v-else type="warning" size="small">未配置，请先在智能搜索配置中设置</el-text>
      </el-descriptions-item>
      <el-descriptions-item label="播放 URL 前缀">
        <span v-if="strmConfig.strmPlayUrlBase" class="path-text">{{ strmConfig.strmPlayUrlBase }}</span>
        <el-text v-else type="warning" size="small">未配置</el-text>
      </el-descriptions-item>
    </el-descriptions>

    <el-alert
      type="info"
      :closable="false"
      style="margin: 12px 0 0;"
    >
      <template #default>
        <div style="font-size: 12px; line-height: 1.7;">
          STRM 内容 =
          <code>{{ strmConfig.strmPlayUrlBase || '{播放URL前缀}' }}/{{ gdSourcePath }}/{文件相对路径}</code>
          <br/>同时生成 .nfo 元数据文件和封面图片（需配置 TMDB API Key）。
        </div>
      </template>
    </el-alert>

    <!-- 进度区域 -->
    <div v-if="taskStatus.phase !== 'IDLE'" class="progress-section">
      <div class="progress-header">
        <el-tag :type="phaseTagType" size="small">{{ phaseLabel }}</el-tag>
        <span class="progress-text">
          {{ taskStatus.processedFiles }} / {{ taskStatus.totalFiles }} 文件
          &nbsp;·&nbsp;成功 {{ taskStatus.successFiles }}
          &nbsp;·&nbsp;失败 {{ taskStatus.failedFiles }}
        </span>
      </div>

      <el-progress
        :percentage="progressPct"
        :status="progressStatus"
        :stroke-width="10"
        style="margin: 8px 0;"
      />

      <div v-if="taskStatus.currentFile" class="current-file">
        <el-icon><Loading v-if="isRunning" /></el-icon>
        {{ taskStatus.currentFile }}
      </div>

      <!-- 日志 -->
      <el-scrollbar ref="logScrollbar" height="200px" class="log-scrollbar">
        <div class="log-container">
          <div
            v-for="(line, i) in taskStatus.logs"
            :key="i"
            :class="['log-line', line.includes('✗') ? 'log-error' : line.includes('✓') ? 'log-success' : '']"
          >{{ line }}</div>
        </div>
      </el-scrollbar>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="$emit('update:modelValue', false)" :disabled="isRunning">
          {{ taskStatus.phase === 'DONE' || taskStatus.phase === 'ERROR' ? '关闭' : '取消' }}
        </el-button>
        <el-button
          type="primary"
          :loading="isRunning"
          :disabled="isRunning || !strmConfig.strmOutputPath || !strmConfig.strmPlayUrlBase"
          @click="startGenerate"
        >
          <el-icon v-if="!isRunning"><VideoPlay /></el-icon>
          {{ isRunning ? '生成中...' : '开始生成' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, nextTick, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getFullConfig } from '@/api/smartSearchConfig'
import { generateStrm, getStrmStatus } from '@/api/strm'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  gdRemote:     { type: String, default: '' },
  gdSourcePath: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const strmConfig   = ref({ strmOutputPath: '', strmPlayUrlBase: '' })
const taskStatus   = ref({
  phase: 'IDLE', totalFiles: 0, processedFiles: 0,
  successFiles: 0, failedFiles: 0, currentFile: '',
  logs: [], errorMessage: ''
})

const logScrollbar = ref(null)
let pollTimer = null

// ── 计算属性 ──────────────────────────────────────────────────────────────────

const isRunning = computed(() => taskStatus.value.phase === 'RUNNING')

const progressPct = computed(() => {
  const total = taskStatus.value.totalFiles
  if (!total) return 0
  return Math.round((taskStatus.value.processedFiles / total) * 100)
})

const progressStatus = computed(() => {
  if (taskStatus.value.phase === 'ERROR') return 'exception'
  if (taskStatus.value.phase === 'DONE')  return 'success'
  return ''
})

const phaseTagType = computed(() => ({
  IDLE: 'info', RUNNING: '', DONE: 'success', ERROR: 'danger'
})[taskStatus.value.phase] || 'info')

const phaseLabel = computed(() => ({
  IDLE: '待机', RUNNING: '生成中', DONE: '已完成', ERROR: '出错'
})[taskStatus.value.phase] || '')

// ── 生命周期 ──────────────────────────────────────────────────────────────────

async function onOpen() {
  // 加载 STRM 配置
  try {
    const res = await getFullConfig()
    const cfg = res.data || {}
    strmConfig.value = {
      strmOutputPath:  cfg.strmOutputPath  || '',
      strmPlayUrlBase: cfg.strmPlayUrlBase || ''
    }
  } catch {
    /* 静默 */
  }
  // 同步当前后台任务状态
  await fetchStatus()
  if (taskStatus.value.phase === 'RUNNING') {
    startPolling()
  }
}

function onClose() {
  stopPolling()
}

onUnmounted(() => stopPolling())

// ── 操作 ──────────────────────────────────────────────────────────────────────

async function startGenerate() {
  try {
    await generateStrm(props.gdRemote, props.gdSourcePath)
    ElMessage.success('STRM 生成任务已启动')
    taskStatus.value.phase = 'RUNNING'
    startPolling()
  } catch (e) {
    ElMessage.error('启动失败: ' + (e?.message || e))
  }
}

async function fetchStatus() {
  try {
    const res = await getStrmStatus()
    taskStatus.value = res.data || taskStatus.value
    await scrollLogsToBottom()
  } catch { /* 静默 */ }
}

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    await fetchStatus()
    if (taskStatus.value.phase !== 'RUNNING') {
      stopPolling()
    }
  }, 2000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function scrollLogsToBottom() {
  await nextTick()
  if (logScrollbar.value) {
    logScrollbar.value.setScrollTop(999999)
  }
}
</script>

<style scoped>
.config-preview {
  margin-bottom: 0;
}

.path-text {
  font-family: monospace;
  font-size: 12px;
  word-break: break-all;
}

.progress-section {
  margin-top: 16px;
  padding: 12px;
  background: #f8f9fa;
  border-radius: 6px;
}

.progress-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.progress-text {
  font-size: 13px;
  color: #606266;
}

.current-file {
  font-size: 12px;
  color: #909399;
  margin: 4px 0 8px;
  display: flex;
  align-items: center;
  gap: 4px;
  word-break: break-all;
}

.log-scrollbar {
  margin-top: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #1e1e1e;
}

.log-container {
  padding: 8px;
}

.log-line {
  font-family: monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #d4d4d4;
  white-space: pre-wrap;
  word-break: break-all;
}

.log-line.log-success {
  color: #73c990;
}

.log-line.log-error {
  color: #f44747;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
