<template>
  <el-dialog
    v-model="visible"
    title="归档文件"
    width="860px"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="onClosed"
  >
    <!-- 原始文件名 -->
    <div class="original-filename">
      <el-icon><Document /></el-icon>
      <span class="filename-text" :title="file?.filePath">{{ file?.fileName }}</span>
    </div>

    <!-- 流程状态条 -->
    <el-steps :active="flowStep" simple class="flow-steps">
      <el-step title="解析文件名" :icon="getStepIcon(0)" />
      <el-step title="TMDB匹配" :icon="getStepIcon(1)" />
      <el-step title="归档配置" :icon="getStepIcon(2)" />
    </el-steps>

    <!-- ─── SECTION 1: 文件信息解析 ─── -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <div class="section-header">
          <span>
            <el-icon><Edit /></el-icon> 文件信息
          </span>
          <el-tag v-if="parseInfo.analyzeSource === 'ai'" type="warning" size="small">AI识别</el-tag>
          <el-tag v-else type="info" size="small">正则解析</el-tag>
        </div>
      </template>

      <el-form :model="parseInfo" label-width="90px" size="small">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="作品名称">
              <el-input v-model="parseInfo.title" placeholder="请输入作品中文名称"
                        @change="onTitleChange" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="媒体类型">
              <el-select v-model="parseInfo.mediaType" @change="onMediaTypeChange">
                <el-option label="电视剧/动漫" value="tv" />
                <el-option label="电影" value="movie" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="年份">
              <el-input v-model="parseInfo.year" placeholder="如：2024" maxlength="4" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16" v-if="parseInfo.mediaType === 'tv'">
          <el-col :span="6">
            <el-form-item label="季数">
              <el-input v-model="parseInfo.season" placeholder="如：01" maxlength="3">
                <template #prepend>S</template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="集数">
              <el-input v-model="parseInfo.episode" placeholder="如：1109" maxlength="6">
                <template #prepend>E</template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="分辨率">
              <el-select v-model="parseInfo.resolution" clearable allow-create filterable>
                <el-option v-for="r in resolutions" :key="r" :label="r" :value="r" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="视频编码">
              <el-select v-model="parseInfo.videoCodec" clearable allow-create filterable>
                <el-option v-for="c in videoCodecs" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="6" v-if="parseInfo.mediaType !== 'tv'">
            <el-form-item label="分辨率">
              <el-select v-model="parseInfo.resolution" clearable allow-create filterable>
                <el-option v-for="r in resolutions" :key="r" :label="r" :value="r" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="parseInfo.mediaType !== 'tv'">
            <el-form-item label="视频编码">
              <el-select v-model="parseInfo.videoCodec" clearable allow-create filterable>
                <el-option v-for="c in videoCodecs" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="音频编码">
              <el-select v-model="parseInfo.audioCodec" clearable allow-create filterable>
                <el-option v-for="c in audioCodecs" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="字幕组">
              <el-input v-model="parseInfo.subtitleGroup" placeholder="如：银色子弹字幕组" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 建议文件名预览 -->
        <div class="filename-preview">
          <span class="preview-label">新文件名：</span>
          <span class="preview-value">{{ computedFilename }}</span>
        </div>
      </el-form>
    </el-card>

    <!-- ─── SECTION 2: TMDB匹配 ─── -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <div class="section-header">
          <span><el-icon><Search /></el-icon> TMDB匹配</span>
          <div class="tmdb-actions">
            <el-input
              v-model="tmdbSearchTitle"
              size="small"
              style="width: 200px"
              placeholder="搜索词"
              @keyup.enter="doTmdbSearch"
            />
            <el-input
              v-model="tmdbSearchYear"
              size="small"
              style="width: 80px; margin-left: 6px"
              placeholder="年份"
              maxlength="4"
            />
            <el-select v-model="tmdbSearchType" size="small" style="width: 90px; margin-left: 6px">
              <el-option label="电视剧" value="tv" />
              <el-option label="电影" value="movie" />
            </el-select>
            <el-button size="small" type="primary" :loading="tmdbLoading"
                       @click="doTmdbSearch" style="margin-left: 6px">
              搜索
            </el-button>
          </div>
        </div>
      </template>

      <!-- 搜索状态 -->
      <div v-if="tmdbLoading" class="status-row">
        <el-icon class="rotating"><Loading /></el-icon>
        <span>正在搜索 TMDB...</span>
      </div>
      <div v-else-if="aiAnalyzing" class="status-row status-ai">
        <el-icon class="rotating"><Loading /></el-icon>
        <span>TMDB 未找到结果，正在 AI 分析文件名...</span>
      </div>
      <div v-else-if="tmdbResults.length === 0 && tmdbSearched && !aiAnalyzing" class="status-row status-warn">
        <el-icon><Warning /></el-icon>
        <span>未找到匹配结果</span>
        <el-button size="small" link type="warning" @click="tryAiThenTmdb" :loading="aiAnalyzing">
          尝试AI识别
        </el-button>
      </div>

      <!-- 搜索结果列表 -->
      <div class="tmdb-results" v-if="tmdbResults.length > 0">
        <div
          v-for="item in tmdbResults"
          :key="item.tmdbId"
          class="tmdb-result-item"
          :class="{ selected: selectedTmdb?.tmdbId === item.tmdbId }"
          @click="selectTmdb(item)"
        >
          <div class="tmdb-poster">
            <img v-if="item.posterPath"
                 :src="`https://image.tmdb.org/t/p/w92${item.posterPath}`"
                 :alt="item.title" />
            <el-icon v-else :size="30"><Film /></el-icon>
          </div>
          <div class="tmdb-info">
            <div class="tmdb-title">
              {{ item.title }}
              <span class="tmdb-year" v-if="item.year">（{{ item.year }}）</span>
            </div>
            <div class="tmdb-original">{{ item.originalTitle }}</div>
            <div class="tmdb-meta">
              <el-tag size="small" type="info">{{ langLabel(item.originalLanguage) }}</el-tag>
              <el-tag size="small" type="success" style="margin-left: 4px">
                {{ item.type === 'movie' ? '电影' : '电视剧' }}
              </el-tag>
              <el-tag size="small" style="margin-left: 4px">
                TMDB: {{ item.tmdbId }}
              </el-tag>
              <el-tag size="small" type="warning" style="margin-left: 4px">
                → {{ item.suggestedCategory }}
              </el-tag>
            </div>
          </div>
          <el-icon v-if="selectedTmdb?.tmdbId === item.tmdbId" class="check-icon"><Check /></el-icon>
        </div>
      </div>

      <!-- 未选择 TMDB 时的手动填写 -->
      <div v-if="!selectedTmdb && tmdbSearched" class="manual-tmdb">
        <el-form :model="manualTmdb" label-width="90px" size="small" inline>
          <el-form-item label="TMDB ID">
            <el-input v-model="manualTmdb.tmdbId" placeholder="手动填写 TMDB ID" style="width: 160px" />
          </el-form-item>
          <el-form-item label="作品标题">
            <el-input v-model="manualTmdb.title" placeholder="TMDB 标题（用于目录名）" style="width: 220px" />
          </el-form-item>
          <el-form-item label="年份">
            <el-input v-model="manualTmdb.year" placeholder="年份" style="width: 80px" maxlength="4" />
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <!-- ─── SECTION 3: 归档配置 ─── -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <span><el-icon><FolderOpened /></el-icon> 归档设置</span>
      </template>

      <el-form :model="archiveConfig" label-width="90px" size="small">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="媒体分类">
              <el-select v-model="archiveConfig.category" placeholder="请选择分类" filterable>
                <el-option-group
                  v-for="group in categoryGroups"
                  :key="group.label"
                  :label="group.label"
                >
                  <el-option
                    v-for="cat in group.children"
                    :key="cat"
                    :label="cat"
                    :value="cat"
                  />
                </el-option-group>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="目录名称">
              <el-input v-model="archiveConfig.dirName" placeholder="作品名-年份-[tmdbid=XXXXX]">
                <template #prepend>📁</template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16" v-if="parseInfo.mediaType === 'tv'">
          <el-col :span="8">
            <el-form-item label="季目录">
              <el-select v-model="archiveConfig.seasonDir" clearable placeholder="无季目录">
                <el-option
                  v-for="s in seasonOptions"
                  :key="s"
                  :label="s"
                  :value="s"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <div style="line-height: 32px; color: #909399; font-size: 12px; padding-top: 4px">
              剧集会在目录下创建"Season N"子文件夹，留空则直接放在目录下
            </div>
          </el-col>
        </el-row>

        <!-- 路径预览 -->
        <div class="path-preview">
          <div class="preview-label">归档路径预览：</div>
          <div class="path-line">
            <el-icon><FolderOpened /></el-icon>
            <code>{{ fullPathPreview }}</code>
          </div>
        </div>
      </el-form>
    </el-card>

    <!-- 底部操作 -->
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleMarkManual" type="warning" plain :loading="executing">
          <el-icon><Warning /></el-icon>
          标记人工处理
        </el-button>
        <div>
          <el-button @click="visible = false">取消</el-button>
          <el-button
            type="primary"
            @click="handleExecute"
            :loading="executing"
            :disabled="!canExecute"
          >
            <el-icon><FolderChecked /></el-icon>
            执行归档
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Document, Edit, Search, FolderOpened, FolderChecked,
  Warning, Check, Film, Loading
} from '@element-plus/icons-vue'
import {
  analyzeFilename,
  aiAnalyzeFilename,
  searchTmdb,
  executeArchive,
  markManual
} from '@/api/archive'

// ─── Props / Emits ────────────────────────────────────────────────────────────

const props = defineProps({
  modelValue: Boolean,
  file: Object   // { fileName, filePath, fileSize }
})
const emit = defineEmits(['update:modelValue', 'archived'])

// ─── 基础状态 ─────────────────────────────────────────────────────────────────

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const flowStep  = ref(0)  // 0=解析, 1=TMDB, 2=配置

// ─── 解析结果 ─────────────────────────────────────────────────────────────────

const parseInfo = reactive({
  originalFilename: '',
  title: '',
  season: '',
  episode: '',
  resolution: '',
  videoCodec: '',
  audioCodec: '',
  subtitleGroup: '',
  year: '',
  mediaType: 'tv',
  analyzeSource: 'regex'
})

// ─── TMDB ─────────────────────────────────────────────────────────────────────

const tmdbLoading   = ref(false)
const aiAnalyzing   = ref(false)
const tmdbSearched  = ref(false)
const tmdbResults   = ref([])
const selectedTmdb  = ref(null)
const tmdbSearchTitle = ref('')
const tmdbSearchYear  = ref('')
const tmdbSearchType  = ref('tv')
const manualTmdb    = reactive({ tmdbId: '', title: '', year: '' })

// ─── 归档配置 ─────────────────────────────────────────────────────────────────

const archiveConfig = reactive({
  category: '',
  dirName: '',
  seasonDir: ''
})

const executing = ref(false)

// ─── 选项数据 ─────────────────────────────────────────────────────────────────

const resolutions  = ['4K', '1080p', '720p', '480p']
const videoCodecs  = ['HEVC', 'AVC', 'AV1', 'VP9']
const audioCodecs  = ['AAC', 'AC3', 'DTS-HD-MA', 'DTS', 'TrueHD', 'FLAC', 'EAC3', 'MP3']
const seasonOptions = computed(() => {
  const opts = []
  for (let i = 1; i <= 20; i++) {
    opts.push(`Season ${String(i).padStart(2, '0')}`)
  }
  return opts
})

const categoryGroups = [
  { label: '动漫', children: ['日语动漫', '国产动漫', '韩国动漫', '欧美动漫', '其他动漫'] },
  { label: '电视剧', children: ['国产剧', '港台剧', '日剧', '韩剧', '欧美剧', '其他剧集'] },
  { label: '电影', children: ['华语电影', '日本电影', '韩国电影', '欧美电影', '其他电影'] },
  { label: '其他', children: ['纪录片', '国内综艺', '日韩综艺', '欧美综艺', '其他'] }
]

// ─── 计算属性 ─────────────────────────────────────────────────────────────────

/** 实时计算建议文件名 */
const computedFilename = computed(() => {
  const r = parseInfo
  if (!r.title) return r.originalFilename || ''
  let name = r.title

  if (r.mediaType === 'tv') {
    if (r.season && r.episode) name += ` S${r.season}E${r.episode}`
    else if (r.episode)        name += ` E${r.episode}`
  } else if (r.year) {
    name += ` (${r.year})`
  }

  const codecs = [r.resolution, r.videoCodec, r.audioCodec].filter(Boolean)
  if (codecs.length) {
    // resolution 单独空格，其余点连接
    name += ` ${r.resolution || ''}`
    const codecStr = [r.videoCodec, r.audioCodec].filter(Boolean).join('.')
    if (codecStr) name += `.${codecStr}`
  }
  if (r.subtitleGroup) name += `-${r.subtitleGroup}`

  const ext = parseInfo.originalFilename?.split('.').pop()?.toLowerCase()
  if (ext) name += `.${ext}`

  return name.trim().replace(/\s+/g, ' ')
})

/** 完整路径预览 */
const fullPathPreview = computed(() => {
  const parts = ['/video2']
  if (archiveConfig.category) parts.push(archiveConfig.category)
  if (archiveConfig.dirName)   parts.push(archiveConfig.dirName)
  if (parseInfo.mediaType === 'tv' && archiveConfig.seasonDir) {
    parts.push(archiveConfig.seasonDir)
  }
  parts.push(computedFilename.value || '[文件名]')
  return parts.join('/')
})

/** 是否可以执行归档 */
const canExecute = computed(() => {
  return (
    parseInfo.title &&
    archiveConfig.category &&
    archiveConfig.dirName &&
    archiveConfig.dirName.includes('tmdbid=') &&
    computedFilename.value
  )
})

// ─── 监听 visible 打开 ────────────────────────────────────────────────────────

watch(visible, async (val) => {
  if (val && props.file) {
    await initDialog()
  }
})

async function initDialog() {
  // 重置状态
  tmdbResults.value  = []
  selectedTmdb.value = null
  tmdbSearched.value = false
  flowStep.value     = 0

  Object.assign(parseInfo, {
    originalFilename: '', title: '', season: '', episode: '',
    resolution: '', videoCodec: '', audioCodec: '', subtitleGroup: '',
    year: '', mediaType: 'tv', analyzeSource: 'regex'
  })
  Object.assign(archiveConfig, { category: '', dirName: '', seasonDir: '' })
  Object.assign(manualTmdb, { tmdbId: '', title: '', year: '' })

  // 1. 解析文件名
  try {
    // request.js 拦截器已处理非200情况并直接返回 Result 对象
    // const { data } 解构出的是 Result.data 字段（即实际业务数据）
    const { data } = await analyzeFilename(props.file.fileName)
    if (data) {
      applyParseResult(data)
      flowStep.value = 1
    }
  } catch (e) {
    console.warn('文件名解析失败', e)
  }

  // 2. 自动发起 TMDB 搜索
  if (parseInfo.title) {
    tmdbSearchTitle.value = parseInfo.title
    tmdbSearchYear.value  = parseInfo.year || ''
    tmdbSearchType.value  = parseInfo.mediaType === 'movie' ? 'movie' : 'tv'
    await doTmdbSearch()
  }
}

function applyParseResult(result) {
  parseInfo.originalFilename = result.originalFilename || props.file.fileName
  parseInfo.title            = result.title || ''
  parseInfo.season           = result.season || ''
  parseInfo.episode          = result.episode || ''
  parseInfo.resolution       = result.resolution || ''
  parseInfo.videoCodec       = result.videoCodec || ''
  parseInfo.audioCodec       = result.audioCodec || ''
  parseInfo.subtitleGroup    = result.subtitleGroup || ''
  parseInfo.year             = result.year || ''
  parseInfo.mediaType        = result.mediaType || 'tv'
  parseInfo.analyzeSource    = result.analyzeSource || 'regex'
}

// ─── TMDB 搜索逻辑 ────────────────────────────────────────────────────────────

async function doTmdbSearch() {
  if (!tmdbSearchTitle.value) return
  tmdbLoading.value  = true
  tmdbSearched.value = false
  tmdbResults.value  = []
  selectedTmdb.value = null

  try {
    const { data } = await searchTmdb(
      tmdbSearchTitle.value,
      tmdbSearchYear.value,
      tmdbSearchType.value
    )
    tmdbResults.value  = Array.isArray(data) ? data : []
    tmdbSearched.value = true
    flowStep.value     = Math.max(flowStep.value, 1)

    // 自动选中唯一结果
    if (tmdbResults.value.length === 1) {
      selectTmdb(tmdbResults.value[0])
    }
  } catch (e) {
    ElMessage.error('TMDB搜索失败: ' + e.message)
  } finally {
    tmdbLoading.value = false
  }
}

/** AI 识别 → 再次搜索 TMDB */
async function tryAiThenTmdb() {
  aiAnalyzing.value = true
  try {
    const { data } = await aiAnalyzeFilename(props.file.fileName)
    if (data) {
      applyParseResult(data)
      tmdbSearchTitle.value = parseInfo.title
      tmdbSearchYear.value  = parseInfo.year || ''
      tmdbSearchType.value  = parseInfo.mediaType === 'movie' ? 'movie' : 'tv'
      ElMessage.info(`AI识别：${parseInfo.title}，重新搜索TMDB...`)
    }
  } catch (e) {
    ElMessage.warning('AI识别失败，请手动填写')
  } finally {
    aiAnalyzing.value = false
  }

  // AI 解析后再搜一次 TMDB
  await doTmdbSearch()
}

function selectTmdb(item) {
  selectedTmdb.value = item
  flowStep.value     = 2

  // 自动填充归档配置
  archiveConfig.category = item.suggestedCategory || ''
  archiveConfig.dirName  = item.suggestedDirName  || ''

  // 自动设置季目录
  if (parseInfo.mediaType === 'tv' && parseInfo.season) {
    archiveConfig.seasonDir = `Season ${parseInfo.season.replace(/^0+/, '') || '1'}`
  } else {
    archiveConfig.seasonDir = ''
  }

  // 同步年份到解析结果
  if (!parseInfo.year && item.year) parseInfo.year = item.year
}

// ─── 字段联动 ─────────────────────────────────────────────────────────────────

function onTitleChange() {
  tmdbSearchTitle.value = parseInfo.title
}

function onMediaTypeChange(val) {
  tmdbSearchType.value = val === 'movie' ? 'movie' : 'tv'
  if (val === 'movie') archiveConfig.seasonDir = ''
}

// ─── 工具函数 ─────────────────────────────────────────────────────────────────

function langLabel(lang) {
  const map = {
    ja: '日语', zh: '中文', ko: '韩语', en: '英语',
    fr: '法语', de: '德语', es: '西班牙语', it: '意大利语'
  }
  return map[lang] || lang || '未知语言'
}

function getStepIcon(step) {
  if (flowStep.value > step) return Check
  return undefined
}

// ─── 执行归档 ─────────────────────────────────────────────────────────────────

async function handleExecute() {
  if (!canExecute.value) {
    ElMessage.warning('请确保目录名包含 tmdbid，并选择好分类')
    return
  }

  // 确认提示
  try {
    await ElMessageBox.confirm(
      `确认将文件归档至：\n${fullPathPreview.value}`,
      '确认归档',
      { confirmButtonText: '执行', cancelButtonText: '取消', type: 'info' }
    )
  } catch { return }

  executing.value = true
  try {
    const tmdb = selectedTmdb.value
    const req = {
      originalPath:  props.file.filePath,
      newFilename:   computedFilename.value,
      category:      archiveConfig.category,
      dirName:       archiveConfig.dirName,
      seasonDir:     parseInfo.mediaType === 'tv' ? archiveConfig.seasonDir : '',
      tmdbId:        tmdb?.tmdbId?.toString() || manualTmdb.tmdbId || '',
      tmdbTitle:     tmdb?.title || manualTmdb.title || '',
      processMethod: parseInfo.analyzeSource === 'ai'
                       ? 'ai'
                       : (tmdb ? 'tmdb' : 'manual')
    }

    const { data } = await executeArchive(req)
    if (data?.success) {
      ElMessage.success('归档成功！')
      emit('archived', { file: props.file, targetPath: data.targetPath })
      visible.value = false
    } else {
      ElMessage.error(data?.message || '归档失败')
    }
  } catch (e) {
    ElMessage.error('归档失败: ' + e.message)
  } finally {
    executing.value = false
  }
}

// ─── 标记人工处理 ─────────────────────────────────────────────────────────────

async function handleMarkManual() {
  let remark = ''
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入人工处理备注（可选）',
      '标记人工处理',
      { confirmButtonText: '确定', cancelButtonText: '取消',
        inputPlaceholder: '说明无法自动处理的原因...' }
    )
    remark = value || ''
  } catch { return }

  executing.value = true
  try {
    const { data } = await markManual(
      props.file.filePath,
      props.file.fileName,
      remark
    )
    if (data?.success) {
      ElMessage.warning('已标记为需要人工处理，可在归档历史中查看')
      visible.value = false
    }
  } catch (e) {
    ElMessage.error('标记失败: ' + e.message)
  } finally {
    executing.value = false
  }
}

function onClosed() {
  // 重置状态（destroy-on-close 已处理组件，这里仅做补充）
  tmdbResults.value  = []
  selectedTmdb.value = null
}
</script>

<style scoped>
.original-filename {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #f5f7fa;
  border-radius: 6px;
  margin-bottom: 14px;
  font-size: 13px;
  color: #606266;
}
.filename-text {
  font-weight: 600;
  color: #303133;
  word-break: break-all;
}

.flow-steps {
  margin-bottom: 14px;
}

.section-card {
  margin-bottom: 12px;
  border-radius: 8px;
}
.section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
  justify-content: space-between;
}
.tmdb-actions {
  display: flex;
  align-items: center;
}

/* 文件名预览 */
.filename-preview {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 12px;
  background: #ecf5ff;
  border-radius: 4px;
  font-size: 13px;
  margin-top: 6px;
  flex-wrap: wrap;
}
.preview-label {
  color: #909399;
  white-space: nowrap;
  font-weight: 600;
}
.preview-value {
  color: #409eff;
  word-break: break-all;
}

/* 状态行 */
.status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  font-size: 13px;
  color: #909399;
}
.status-ai  { color: #e6a23c; }
.status-warn { color: #f56c6c; }
.rotating {
  animation: spin 1.2s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* TMDB 结果列表 */
.tmdb-results {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
}
.tmdb-result-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}
.tmdb-result-item:hover {
  border-color: #409eff;
  background: #ecf5ff;
}
.tmdb-result-item.selected {
  border-color: #409eff;
  background: #ecf5ff;
}
.tmdb-poster {
  width: 48px;
  height: 72px;
  border-radius: 4px;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
  color: #c0c4cc;
}
.tmdb-poster img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.tmdb-info { flex: 1; min-width: 0; }
.tmdb-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}
.tmdb-year { color: #909399; font-weight: 400; font-size: 13px; }
.tmdb-original { color: #909399; font-size: 12px; margin: 3px 0; }
.tmdb-meta { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 4px; }
.check-icon {
  color: #409eff;
  font-size: 20px;
  position: absolute;
  right: 14px;
}

/* 手动填写 TMDB */
.manual-tmdb {
  margin-top: 10px;
  padding: 10px;
  background: #fdf6ec;
  border-radius: 6px;
}

/* 路径预览 */
.path-preview {
  margin-top: 10px;
  padding: 12px;
  background: #f0f9eb;
  border-radius: 6px;
}
.preview-label {
  font-weight: 600;
  font-size: 13px;
  color: #67c23a;
  margin-bottom: 6px;
}
.path-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  flex-wrap: wrap;
}
.path-line code {
  color: #67c23a;
  font-family: monospace;
  word-break: break-all;
  background: transparent;
}

/* 底部按钮 */
.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
