<template>
  <el-dialog
    v-model="visible"
    title="格式化命名"
    width="620px"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="onClosed"
  >
    <!-- 原始文件名 -->
    <div class="original-name">
      <el-icon><Document /></el-icon>
      <span class="name-text" :title="file?.filePath">{{ file?.fileName }}</span>
    </div>

    <!-- 加载状态 -->
    <div v-if="parsing" class="parsing-tip">
      <el-icon class="rotating"><Loading /></el-icon>
      <span>正在解析文件名...</span>
    </div>

    <!-- 编辑表单 -->
    <el-form v-else :model="info" label-width="80px" size="small" class="edit-form">
      <el-row :gutter="16">
        <el-col :span="14">
          <el-form-item label="作品名称">
            <el-input v-model="info.title" placeholder="作品名称" @input="buildPreview" />
          </el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item label="媒体类型">
            <el-select v-model="info.mediaType" @change="buildPreview" style="width:100%">
              <el-option label="电视剧 / 动漫" value="tv" />
              <el-option label="电影" value="movie" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- TV 字段 -->
      <el-row v-if="info.mediaType === 'tv'" :gutter="16">
        <el-col :span="12">
          <el-form-item label="季数">
            <el-input v-model="info.season" placeholder="如 01" @input="buildPreview">
              <template #prepend>S</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="集数">
            <el-input v-model="info.episode" placeholder="如 01" @input="buildPreview">
              <template #prepend>E</template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 电影字段 -->
      <el-row v-else :gutter="16">
        <el-col :span="12">
          <el-form-item label="年份">
            <el-input v-model="info.year" placeholder="如 2024" @input="buildPreview" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="分辨率">
            <el-select v-model="info.resolution" clearable allow-create filterable @change="buildPreview" style="width:100%">
              <el-option v-for="r in resolutions" :key="r" :label="r" :value="r" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="视频编码">
            <el-select v-model="info.videoCodec" clearable allow-create filterable @change="buildPreview" style="width:100%">
              <el-option v-for="c in videoCodecs" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="音频编码">
            <el-select v-model="info.audioCodec" clearable allow-create filterable @change="buildPreview" style="width:100%">
              <el-option v-for="c in audioCodecs" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="字幕组">
            <el-input v-model="info.subtitleGroup" placeholder="如 ���色子弹" @input="buildPreview" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="">
            <el-tag v-if="probing" type="warning" size="small">
              <el-icon class="rotating"><Loading /></el-icon> ffprobe检测中...
            </el-tag>
            <el-tag v-else-if="probeSource === 'ffprobe'" type="success" size="small">ffprobe 已补充编码</el-tag>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 预览 -->
    <div v-if="!parsing" class="preview-box">
      <div class="preview-label">预览新文件名</div>
      <div class="preview-name" :class="{ unchanged: previewName === file?.fileName }">
        {{ previewName || '(请填写作品名称)' }}
      </div>
      <div v-if="previewName === file?.fileName" class="unchanged-hint">
        与原文件名相同，无需重命名
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="renaming"
        :disabled="!canRename"
        @click="doRename"
      >确认重命名</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Loading } from '@element-plus/icons-vue'
import { analyzeFilename, getMediaInfo } from '@/api/archive'
import { moveItem } from '@/api/gdFileManager'

const props = defineProps({
  modelValue: Boolean,
  file: Object,       // { fileName, filePath, accountId, rcloneConfigName }
})
const emit = defineEmits(['update:modelValue', 'renamed'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v),
})

// ── 选项 ──────────────────────────────────────────────────────────────────────
const resolutions  = ['4K', '1080p', '720p', '480p']
const videoCodecs  = ['HEVC', 'AVC', 'AV1', 'VP9']
const audioCodecs  = ['AAC', 'AC3', 'DTS', 'DTS-HD', 'FLAC', 'EAC3', 'TrueHD']

// ── 状态 ──────────────────────────────────────────────────────────────────────
const parsing     = ref(false)
const probing     = ref(false)
const probeSource = ref('')   // 'ffprobe' | ''
const renaming    = ref(false)
const previewName = ref('')

const info = ref({
  title: '', mediaType: 'tv', season: '', episode: '',
  year: '', resolution: '', videoCodec: '', audioCodec: '',
  subtitleGroup: '', extension: '',
})

// ── 监听对话框打开 ─────────────────────────────────────────────────────────────
watch(visible, async (v) => {
  if (!v || !props.file) return
  await init()
})

async function init() {
  // 重置状态
  probeSource.value = ''
  previewName.value = ''
  Object.assign(info.value, {
    title: '', mediaType: 'tv', season: '', episode: '',
    year: '', resolution: '', videoCodec: '', audioCodec: '',
    subtitleGroup: '', extension: '',
  })

  // 1. 正则解析文件名
  parsing.value = true
  try {
    const res = await analyzeFilename(props.file.fileName)
    const d = res.data || {}
    Object.assign(info.value, {
      title:         d.title         || '',
      mediaType:     d.mediaType     || 'tv',
      season:        d.season        || '',
      episode:       d.episode       || '',
      year:          d.year          || '',
      resolution:    d.resolution    || '',
      videoCodec:    d.videoCodec    || '',
      audioCodec:    d.audioCodec    || '',
      subtitleGroup: d.subtitleGroup || '',
      extension:     d.extension     || '',
    })
  } catch (e) {
    // 解析失败，至少提取扩展名
    const m = props.file.fileName.match(/\.([a-zA-Z0-9]{2,4})$/)
    if (m) info.value.extension = m[1].toLowerCase()
  } finally {
    parsing.value = false
  }

  buildPreview()

  // 2. ffprobe 探测编码（后台静默，不阻塞显示）
  if (!info.value.resolution && !info.value.videoCodec && !info.value.audioCodec) {
    tryFfprobe()
  }
}

async function tryFfprobe() {
  if (!props.file?.filePath) return
  probing.value = true
  try {
    const res = await getMediaInfo(props.file.filePath, props.file.rcloneConfigName || '')
    const d = res.data
    if (!d) return
    let filled = false
    if (d.resolution  && !info.value.resolution)  { info.value.resolution  = d.resolution;  filled = true }
    if (d.videoCodec  && !info.value.videoCodec)  { info.value.videoCodec  = d.videoCodec;  filled = true }
    if (d.audioCodec  && !info.value.audioCodec)  { info.value.audioCodec  = d.audioCodec;  filled = true }
    if (filled) {
      probeSource.value = 'ffprobe'
      buildPreview()
    }
  } catch { /* 静默 */ } finally {
    probing.value = false
  }
}

// ── 构建预览文件名（与后端 buildFilename 逻辑一致）──────────────────────────────
function buildPreview() {
  const r = info.value
  let name = r.title || ''

  if (r.mediaType === 'tv') {
    if (r.season && r.episode)     name += ` S${r.season}E${r.episode}`
    else if (r.episode)            name += ` E${r.episode}`
  } else {
    if (r.year) name += ` (${r.year})`
  }

  if (r.resolution) name += ` ${r.resolution}`

  const codecs = [r.videoCodec, r.audioCodec].filter(Boolean)
  if (codecs.length) name += `.${codecs.join('.')}`

  if (r.subtitleGroup) name += `-${r.subtitleGroup}`

  if (r.extension) name += `.${r.extension}`

  previewName.value = name
}

// ── 是否可以重命名 ─────────────────────────────────────────────────────────────
const canRename = computed(() =>
  !parsing.value &&
  previewName.value &&
  previewName.value !== props.file?.fileName
)

// ── 执行重命名 ────────────────────────────────────────────────────────────────
async function doRename() {
  if (!canRename.value) return
  const oldPath = props.file.filePath
  const parentPath = oldPath.includes('/')
    ? oldPath.substring(0, oldPath.lastIndexOf('/'))
    : ''
  const newPath = parentPath ? `${parentPath}/${previewName.value}` : previewName.value

  renaming.value = true
  try {
    await moveItem(props.file.accountId, oldPath, newPath, false)
    ElMessage.success('重命名成功')
    visible.value = false
    emit('renamed')
  } catch (e) {
    ElMessage.error('重命名失败: ' + (e?.message || e))
  } finally {
    renaming.value = false
  }
}

function onClosed() {
  probeSource.value = ''
  probing.value = false
  parsing.value = false
  renaming.value = false
}
</script>

<style scoped>
.original-name {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f5f5f7;
  border-radius: 8px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #1d1d1f;
}
.name-text {
  font-weight: 500;
  word-break: break-all;
}

.parsing-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
  font-size: 13px;
  padding: 24px 0;
  justify-content: center;
}

.edit-form {
  margin-top: 4px;
}

.preview-box {
  border: 1px dashed #d0d0d5;
  border-radius: 8px;
  padding: 12px 16px;
  margin-top: 4px;
  background: #fafafa;
}
.preview-label {
  font-size: 11px;
  color: #86868b;
  margin-bottom: 6px;
}
.preview-name {
  font-size: 13px;
  font-weight: 500;
  color: #1d1d1f;
  word-break: break-all;
  font-family: monospace;
}
.preview-name.unchanged {
  color: #86868b;
}
.unchanged-hint {
  font-size: 11px;
  color: #e6a23c;
  margin-top: 4px;
}

.rotating {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
</style>
