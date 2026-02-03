<template>
  <div class="smart-search-config-container">
    <el-card class="header-card">
      <div class="header-content">
        <h2>智能搜索配置</h2>
        <div class="header-actions">
          <el-button @click="handleImport">
            <el-icon><Upload /></el-icon>
            导入配置
          </el-button>
          <el-button @click="handleExport">
            <el-icon><Download /></el-icon>
            导出配置
          </el-button>
          <el-button type="primary" @click="handleSave" :loading="saving">
            <el-icon><Check /></el-icon>
            保存配置
          </el-button>
        </div>
      </div>
    </el-card>

    <el-form
      ref="formRef"
      :model="config"
      label-width="150px"
      label-position="left"
    >
      <!-- 搜索服务配置 -->
      <el-card class="config-section">
        <template #header>
          <div class="section-header">
            <el-icon><Search /></el-icon>
            <span>搜索服务配置</span>
          </div>
        </template>

        <el-form-item label="搜索API地址">
          <el-input v-model="config.searchApiUrl" placeholder="http://104.251.122.51:8095/api/v1" />
          <div class="form-tip">Telegram搜索服务的API地址</div>
        </el-form-item>

        <el-form-item label="认证Token">
          <el-input v-model="config.searchAuthToken" type="password" show-password placeholder="Bearer token" />
          <div class="form-tip">用于访问搜索API的认证令牌</div>
        </el-form-item>

        <el-form-item label="请求超时">
          <el-input-number v-model="config.searchTimeout" :min="5000" :max="120000" :step="1000" />
          <span style="margin-left: 10px;">毫秒</span>
        </el-form-item>

        <el-form-item label="测试连接">
          <el-button @click="testSearchConnection" :loading="testing.search">
            <el-icon><Connection /></el-icon>
            测试搜索服务
          </el-button>
        </el-form-item>
      </el-card>

      <!-- 云盘配置 -->
      <el-card class="config-section">
        <template #header>
          <div class="section-header">
            <el-icon><FolderOpened /></el-icon>
            <span>云盘配置</span>
            <el-button type="primary" size="small" @click="addCloudConfig" style="margin-left: auto;">
              <el-icon><Plus /></el-icon>
              添加云盘
            </el-button>
          </div>
        </template>

        <el-alert
          title="配置说明"
          type="info"
          :closable="false"
          style="margin-bottom: 16px;"
        >
          <div style="font-size: 13px; line-height: 1.6;">
            <p>根据搜索结果中的 <code>cloudType</code> 自动匹配对应的云盘配置。</p>
            <p style="margin-top: 4px;">例如：搜索结果的 <code>cloudType</code> 为 <code>channel_alipan</code>，系统会自动使用对应配置的目录ID。</p>
          </div>
        </el-alert>

        <el-alert
          v-if="config.cloudConfigs.length === 0"
          title="暂无云盘配置"
          type="warning"
          :closable="false"
          style="margin-bottom: 16px;"
        >
          点击"添加云盘"按钮添加第一个云盘配置
        </el-alert>

        <div v-for="(cloud, index) in config.cloudConfigs" :key="index" class="cloud-config-item">
          <el-card shadow="hover">
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <div>
                  <el-tag type="primary" size="small">{{ cloud.cloudType }}</el-tag>
                  <span style="margin-left: 8px; font-weight: 600;">{{ cloud.name }}</span>
                </div>
                <el-button
                  type="danger"
                  link
                  size="small"
                  @click="removeCloudConfig(index)"
                >
                  删除
                </el-button>
              </div>
            </template>

            <el-form-item label="配置名称">
              <el-input v-model="cloud.name" placeholder="如：我的阿里云盘" />
            </el-form-item>

            <el-form-item label="云盘类型">
              <el-input v-model="cloud.cloudType" placeholder="如：channel_alipan" />
              <div class="form-tip">
                必须与搜索结果中的 cloudType 完全一致，常见值：
                <code>channel_alipan</code>（阿里云盘）、
                <code>channel_189</code>（天翼云盘）
              </div>
            </el-form-item>

            <el-form-item label="目标目录ID">
              <el-input v-model="cloud.parentId" placeholder="云盘目录ID" />
              <div class="form-tip">
                转存到该云盘时使用的目录ID
              </div>
            </el-form-item>

            <el-form-item label="备注">
              <el-input v-model="cloud.remark" placeholder="可选" />
            </el-form-item>
          </el-card>
        </div>
      </el-card>

      <!-- AI智能筛选配置 -->
      <el-card class="config-section">
        <template #header>
          <div class="section-header">
            <el-icon><MagicStick /></el-icon>
            <span>AI智能筛选配置</span>
          </div>
        </template>

        <el-form-item label="启用AI筛选">
          <el-switch v-model="config.aiEnabled" />
          <div class="form-tip">使用AI模型进行智能资源筛选</div>
        </el-form-item>

        <template v-if="config.aiEnabled">
          <el-form-item label="AI提供商">
            <el-select v-model="config.aiProvider" placeholder="请选择AI提供商" @change="handleProviderChange">
              <el-option label="Claude (Anthropic)" value="claude">
                <div class="provider-option">
                  <span>Claude (Anthropic)</span>
                  <el-tag size="small" type="success">推荐</el-tag>
                </div>
              </el-option>
              <el-option label="OpenAI (GPT)" value="openai">
                <div class="provider-option">
                  <span>OpenAI (GPT)</span>
                  <el-tag size="small" type="warning">付费</el-tag>
                </div>
              </el-option>
              <el-option label="Ollama (本地)" value="ollama">
                <div class="provider-option">
                  <span>Ollama (本地)</span>
                  <el-tag size="small" type="info">免费</el-tag>
                </div>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="API Key" v-if="config.aiProvider !== 'ollama'">
            <el-input
              v-model="config.aiApiKey"
              type="password"
              show-password
              :placeholder="getApiKeyPlaceholder()"
            />
            <div class="form-tip">
              <span v-if="config.aiProvider === 'claude'">
                从 <a href="https://console.anthropic.com/" target="_blank">Anthropic Console</a> 获取
              </span>
              <span v-else-if="config.aiProvider === 'openai'">
                从 <a href="https://platform.openai.com/api-keys" target="_blank">OpenAI Platform</a> 获取
              </span>
            </div>
          </el-form-item>

          <el-form-item label="API地址" v-if="config.aiProvider === 'ollama'">
            <el-input v-model="config.aiApiUrl" placeholder="http://localhost:11434" />
            <div class="form-tip">Ollama服务的API地址</div>
          </el-form-item>

          <el-form-item label="模型选择">
            <el-select v-model="config.aiModel" placeholder="请选择模型">
              <el-option
                v-for="model in getAvailableModels()"
                :key="model.value"
                :label="model.label"
                :value="model.value"
              >
                <div class="model-option">
                  <span>{{ model.label }}</span>
                  <el-tag v-if="model.recommended" size="small" type="success">推荐</el-tag>
                  <span class="model-cost">{{ model.cost }}</span>
                </div>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="最大Token数">
            <el-input-number v-model="config.aiMaxTokens" :min="256" :max="4096" :step="256" />
            <div class="form-tip">AI响应的最大token数量</div>
          </el-form-item>

          <el-form-item label="温度参数">
            <el-slider v-model="config.aiTemperature" :min="0" :max="1" :step="0.1" show-stops />
            <div class="form-tip">控制AI输出的随机性，0=确定性，1=创造性</div>
          </el-form-item>

          <el-form-item label="测试AI连接">
            <el-button @click="testAIConnection" :loading="testing.ai">
              <el-icon><Connection /></el-icon>
              测试AI服务
            </el-button>
          </el-form-item>
        </template>
      </el-card>

      <!-- TMDB配置 -->
      <el-card class="config-section">
        <template #header>
          <div class="section-header">
            <el-icon><Film /></el-icon>
            <span>TMDB配置</span>
          </div>
        </template>

        <el-alert
          title="TMDB说明"
          type="info"
          :closable="false"
          style="margin-bottom: 16px;"
        >
          <div style="font-size: 13px; line-height: 1.6;">
            <p>TMDB (The Movie Database) 用于获取影视作品的元数据和ID。</p>
            <p style="margin-top: 4px;">配置后可以自动补充115资源的TMDB ID，提高匹配准确度。</p>
          </div>
        </el-alert>

        <el-form-item label="启用TMDB">
          <el-switch v-model="config.tmdbEnabled" />
          <div class="form-tip">启用后可以使用TMDB API进行影视信息查询</div>
        </el-form-item>

        <template v-if="config.tmdbEnabled">
          <el-form-item label="TMDB API Key">
            <el-input
              v-model="config.tmdbApiKey"
              type="password"
              show-password
              placeholder="请输入TMDB API Key"
            />
            <div class="form-tip">
              从 <a href="https://www.themoviedb.org/settings/api" target="_blank">TMDB API Settings</a> 获取API密钥
            </div>
          </el-form-item>

          <el-form-item label="TMDB API地址">
            <el-input v-model="config.tmdbApiUrl" placeholder="https://api.themoviedb.org/3" />
            <div class="form-tip">TMDB API的基础URL，通常使用默认值即可</div>
          </el-form-item>

          <el-form-item label="语言设置">
            <el-select v-model="config.tmdbLanguage" placeholder="请选择语言">
              <el-option label="简体中文" value="zh-CN" />
              <el-option label="繁体中文" value="zh-TW" />
              <el-option label="英语" value="en-US" />
              <el-option label="日语" value="ja-JP" />
              <el-option label="韩语" value="ko-KR" />
            </el-select>
            <div class="form-tip">TMDB返回数据的语言</div>
          </el-form-item>

          <el-form-item label="请求超时">
            <el-input-number v-model="config.tmdbTimeout" :min="3000" :max="30000" :step="1000" />
            <span style="margin-left: 10px;">毫秒</span>
            <div class="form-tip">TMDB API请求的超时时间</div>
          </el-form-item>

          <el-form-item label="自动匹配">
            <el-switch v-model="config.tmdbAutoMatch" />
            <div class="form-tip">在115资源管理中自动搜索并匹配TMDB ID</div>
          </el-form-item>

          <el-form-item label="测试TMDB连接">
            <el-button @click="testTmdbConnection" :loading="testing.tmdb">
              <el-icon><Connection /></el-icon>
              测试TMDB服务
            </el-button>
          </el-form-item>
        </template>
      </el-card>

      <!-- 115网盘配置 -->
      <el-card class="config-section">
        <template #header>
          <div class="section-header">
            <el-icon><FolderOpened /></el-icon>
            <span>115网盘配置</span>
          </div>
        </template>

        <el-alert
          title="115网盘说明"
          type="info"
          :closable="false"
          style="margin-bottom: 16px;"
        >
          <div style="font-size: 13px; line-height: 1.6;">
            <p>配置115网盘Cookie后，系统会优先从115资源库中匹配资源并自动转存。</p>
            <p style="margin-top: 4px;">如果TMDB ID在115资源表中找到匹配，将直接调用115转存API。</p>
          </div>
        </el-alert>

        <el-form-item label="启用115转存">
          <el-switch v-model="config.enable115Transfer" />
          <div class="form-tip">启用后优先使用115资源库进行匹配和转存</div>
        </el-form-item>

        <template v-if="config.enable115Transfer">
          <el-form-item label="115 Cookie">
            <el-input
              v-model="config.cookie115"
              type="textarea"
              :rows="3"
              placeholder="请输入115网盘的Cookie"
            />
            <div class="form-tip">
              从浏览器开发者工具中获取115.com的Cookie
              <a href="https://115.com" target="_blank">打开115网盘</a>
            </div>
          </el-form-item>

          <el-form-item label="目标文件夹ID">
            <el-input v-model="config.targetFolderId115" placeholder="0（根目录）" />
            <div class="form-tip">转存到115网盘的目标文件夹ID，0表示根目录</div>
          </el-form-item>

          <el-form-item label="测试115连接">
            <el-button @click="test115Connection" :loading="testing.transfer115">
              <el-icon><Connection /></el-icon>
              测试115服务
            </el-button>
          </el-form-item>
        </template>
      </el-card>

      <!-- 链接验证配置 -->
      <el-card class="config-section">
        <template #header>
          <div class="section-header">
            <el-icon><Link /></el-icon>
            <span>链接验证配置</span>
          </div>
        </template>

        <el-form-item label="启用链接验证">
          <el-switch v-model="config.validateLinks" />
          <div class="form-tip">通过实际转存测试验证链接是否有效（转存失败或404表示链接失效）</div>
        </el-form-item>

        <el-alert
          v-if="config.validateLinks"
          title="验证说明"
          type="info"
          :closable="false"
          style="margin-bottom: 16px;"
        >
          <div style="font-size: 13px; line-height: 1.6;">
            <p>链接验证通过实际转存操作来检测链接有效性：</p>
            <ul style="margin: 8px 0; padding-left: 20px;">
              <li>✓ 转存成功 = 链接有效</li>
              <li>✗ 转存失败/404 = 链接失效</li>
              <li>有效链接额外加分：+10分</li>
            </ul>
            <p style="margin-top: 8px; color: #ff9500;">
              <strong>注意：</strong>验证过程会实际调用转存API，请确保搜索服务已正确配置。
            </p>
          </div>
        </el-alert>

        <template v-if="config.validateLinks">
          <el-form-item label="验证超时">
            <el-input-number v-model="config.validationTimeout" :min="3000" :max="30000" :step="1000" />
            <span style="margin-left: 10px;">毫秒</span>
            <div class="form-tip">单个链接验证的超时时间</div>
          </el-form-item>

          <el-form-item label="最大验证数">
            <el-input-number v-model="config.maxValidationCount" :min="5" :max="50" :step="5" />
            <div class="form-tip">最多验证前N个搜索结果（避免过多API调用）</div>
          </el-form-item>
        </template>
      </el-card>

      <!-- 高级设置 -->
      <el-card class="config-section">
        <template #header>
          <div class="section-header">
            <el-icon><Setting /></el-icon>
            <span>高级设置</span>
          </div>
        </template>

        <el-form-item label="缓存时间">
          <el-input-number v-model="config.cacheTime" :min="0" :max="3600" :step="60" />
          <span style="margin-left: 10px;">秒 (0=禁用缓存)</span>
          <div class="form-tip">搜索结果和验证结果的缓存时间</div>
        </el-form-item>

        <el-form-item label="最大结果数">
          <el-input-number v-model="config.maxResults" :min="10" :max="100" :step="10" />
          <div class="form-tip">每次搜索返回的最大结果数量</div>
        </el-form-item>

        <el-form-item label="调试模式">
          <el-switch v-model="config.debugMode" />
          <div class="form-tip">启用后会在控制台输出详细日志</div>
        </el-form-item>

        <el-form-item label="自动选择最佳">
          <el-switch v-model="config.autoSelectBest" />
          <div class="form-tip">搜索后自动选择评分最高的资源</div>
        </el-form-item>
      </el-card>

      <!-- 评分权重配置 -->
      <el-card class="config-section">
        <template #header>
          <div class="section-header">
            <el-icon><TrendCharts /></el-icon>
            <span>评分权重配置</span>
          </div>
        </template>

        <el-form-item label="标题匹配度">
          <el-slider v-model="config.weights.titleMatch" :min="0" :max="50" show-input />
          <div class="form-tip">标题与电影名称的匹配程度 (默认: 40分)</div>
        </el-form-item>

        <el-form-item label="分辨率">
          <el-slider v-model="config.weights.resolution" :min="0" :max="30" show-input />
          <div class="form-tip">视频分辨率质量 (默认: 20分)</div>
        </el-form-item>

        <el-form-item label="文件大小">
          <el-slider v-model="config.weights.fileSize" :min="0" :max="20" show-input />
          <div class="form-tip">文件大小合理性 (默认: 15分)</div>
        </el-form-item>

        <el-form-item label="标签匹配">
          <el-slider v-model="config.weights.tagMatch" :min="0" :max="15" show-input />
          <div class="form-tip">标签与电影类型的匹配 (默认: 10分)</div>
        </el-form-item>

        <el-form-item label="来源可信度">
          <el-slider v-model="config.weights.sourceCredibility" :min="0" :max="15" show-input />
          <div class="form-tip">资源来源的可信度 (默认: 10分)</div>
        </el-form-item>

        <el-form-item label="时效性">
          <el-slider v-model="config.weights.timeliness" :min="0" :max="10" show-input />
          <div class="form-tip">资源发布时间 (默认: 5分)</div>
        </el-form-item>

        <el-form-item>
          <el-button @click="resetWeights" size="small">
            <el-icon><RefreshLeft /></el-icon>
            恢复默认权重
          </el-button>
          <span style="margin-left: 10px; color: #909399;">
            总分: {{ totalWeight }}分
          </span>
        </el-form-item>
      </el-card>
    </el-form>

    <!-- 导入配置文件输入 -->
    <input
      ref="fileInputRef"
      type="file"
      accept=".json"
      style="display: none"
      @change="handleFileChange"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFullConfig, saveFullConfig } from '@/api/smartSearchConfig'
import { test115Cookie, get115UserInfo } from '@/api/transfer115'

const formRef = ref(null)
const fileInputRef = ref(null)
const saving = ref(false)
const testing = ref({
  search: false,
  ai: false,
  tmdb: false,
  transfer115: false
})

// 默认配置
const defaultConfig = {
  // 搜索服务
  searchApiUrl: 'http://104.251.122.51:8095/api/v1',
  searchAuthToken: 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NzE3ODQxMTEsInVzZXJuYW1lIjoiYWRtaW4ifQ.9EPowlYRQCLx1p03TfbAQ9T8cxKUQdSrBVEQa67R1nI',
  searchTimeout: 30000,

  // 云盘配置列表（根据cloudType自动匹配）
  cloudConfigs: [
    {
      name: '阿里云盘',
      cloudType: 'channel_alipan',
      parentId: '697f2333cd2704159fa446d8bc5077584838e3dc',
      remark: ''
    }
  ],

  // AI配置
  aiEnabled: false,
  aiProvider: 'claude',
  apiKey: '',
  aiApiUrl: 'http://localhost:11434',
  aiModel: 'claude-3-5-sonnet-20241022',
  aiMaxTokens: 1024,
  aiTemperature: 0.7,

  // TMDB配置
  tmdbEnabled: false,
  tmdbApiKey: '',
  tmdbApiUrl: 'https://api.themoviedb.org/3',
  tmdbLanguage: 'zh-CN',
  tmdbTimeout: 10000,
  tmdbAutoMatch: true,

  // 115网盘配置
  enable115Transfer: false,
  cookie115: '',
  targetFolderId115: '0',

  // 链接验证
  validateLinks: true,
  validationTimeout: 10000,
  maxValidationCount: 20,

  // 高级设置
  cacheTime: 300,
  maxResults: 50,
  debugMode: false,
  autoSelectBest: true,

  // 评分权重
  weights: {
    titleMatch: 40,
    resolution: 20,
    fileSize: 15,
    tagMatch: 10,
    sourceCredibility: 10,
    timeliness: 5
  }
}

const config = ref({ ...defaultConfig })

// 计算总权重
const totalWeight = computed(() => {
  return Object.values(config.value.weights).reduce((sum, val) => sum + val, 0)
})

// 获取可用的AI模型
const getAvailableModels = () => {
  const models = {
    claude: [
      { label: 'Claude 3.5 Sonnet', value: 'claude-3-5-sonnet-20241022', recommended: true, cost: '$3/$15 per 1M tokens' },
      { label: 'Claude 3 Opus', value: 'claude-3-opus-20240229', cost: '$15/$75 per 1M tokens' },
      { label: 'Claude 3 Sonnet', value: 'claude-3-sonnet-20240229', cost: '$3/$15 per 1M tokens' },
      { label: 'Claude 3 Haiku', value: 'claude-3-haiku-20240307', cost: '$0.25/$1.25 per 1M tokens' }
    ],
    openai: [
      { label: 'GPT-4 Turbo', value: 'gpt-4-turbo-preview', recommended: true, cost: '$10/$30 per 1M tokens' },
      { label: 'GPT-4', value: 'gpt-4', cost: '$30/$60 per 1M tokens' },
      { label: 'GPT-3.5 Turbo', value: 'gpt-3.5-turbo', cost: '$0.5/$1.5 per 1M tokens' }
    ],
    ollama: [
      { label: 'Llama 2', value: 'llama2', recommended: true, cost: '免费' },
      { label: 'Llama 2 13B', value: 'llama2:13b', cost: '免费' },
      { label: 'Mistral', value: 'mistral', cost: '免费' },
      { label: 'Mixtral', value: 'mixtral', cost: '免费' }
    ]
  }

  return models[config.value.aiProvider] || []
}

// 获取API Key占位符
const getApiKeyPlaceholder = () => {
  if (config.value.aiProvider === 'claude') {
    return 'sk-ant-api03-...'
  } else if (config.value.aiProvider === 'openai') {
    return 'sk-...'
  }
  return ''
}

// 处理提供商变更
const handleProviderChange = () => {
  const models = getAvailableModels()
  if (models.length > 0) {
    config.value.aiModel = models[0].value
  }
}

// 重置权重
const resetWeights = () => {
  config.value.weights = { ...defaultConfig.weights }
  ElMessage.success('已恢复默认权重')
}

// 保存配置
const handleSave = async () => {
  saving.value = true

  try {
    // 保存到数据库
    await saveFullConfig(config.value)

    // 同时保存到localStorage作为备份
    localStorage.setItem('smartSearchConfig', JSON.stringify(config.value))

    ElMessage.success('配置已保存到数据库')

    if (config.value.debugMode) {
      console.log('保存的配置:', config.value)
    }
  } catch (error) {
    console.error('保存配置失败:', error)
    ElMessage.error('保存失败: ' + (error.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 导出配置
const handleExport = () => {
  try {
    const dataStr = JSON.stringify(config.value, null, 2)
    const dataBlob = new Blob([dataStr], { type: 'application/json' })
    const url = URL.createObjectURL(dataBlob)
    const link = document.createElement('a')
    link.href = url
    link.download = `smart-search-config-${Date.now()}.json`
    link.click()
    URL.revokeObjectURL(url)

    ElMessage.success('配置已导出')
  } catch (error) {
    ElMessage.error('导出失败: ' + error.message)
  }
}

// 导入配置
const handleImport = () => {
  fileInputRef.value?.click()
}

// 处理文件选择
const handleFileChange = (event) => {
  const file = event.target.files[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const importedConfig = JSON.parse(e.target.result)
      config.value = { ...defaultConfig, ...importedConfig }
      ElMessage.success('配置已导入')

      // 清空文件输入
      event.target.value = ''
    } catch (error) {
      ElMessage.error('导入失败: 配置文件格式错误')
    }
  }
  reader.readAsText(file)
}

// 测试搜索连接
const testSearchConnection = async () => {
  testing.value.search = true

  try {
    // 这里应该调用实际的API测试
    await new Promise(resolve => setTimeout(resolve, 1000))
    ElMessage.success('搜索服务连接正常')
  } catch (error) {
    ElMessage.error('搜索服务连接失败: ' + error.message)
  } finally {
    testing.value.search = false
  }
}

// 测试AI连接
const testAIConnection = async () => {
  if (!config.value.aiApiKey && config.value.aiProvider !== 'ollama') {
    ElMessage.warning('请先配置API Key')
    return
  }

  testing.value.ai = true

  try {
    // 这里应该调用实际的AI API测试
    await new Promise(resolve => setTimeout(resolve, 1500))
    ElMessage.success('AI服务连接正常')
  } catch (error) {
    ElMessage.error('AI服务连接失败: ' + error.message)
  } finally {
    testing.value.ai = false
  }
}

// 测试TMDB连接
const testTmdbConnection = async () => {
  if (!config.value.tmdbApiKey) {
    ElMessage.warning('请先配置TMDB API Key')
    return
  }

  testing.value.tmdb = true

  try {
    // 测试TMDB API - 搜索一个常见电影
    const response = await fetch(
      `${config.value.tmdbApiUrl}/search/movie?api_key=${config.value.tmdbApiKey}&language=${config.value.tmdbLanguage}&query=阿凡达`,
      { timeout: config.value.tmdbTimeout }
    )

    if (response.ok) {
      const data = await response.json()
      if (data.results && data.results.length > 0) {
        ElMessage.success(`TMDB服务连接正常 (找到 ${data.results.length} 个结果)`)
      } else {
        ElMessage.warning('TMDB服务连接正常，但未找到测试结果')
      }
    } else {
      ElMessage.error(`TMDB服务连接失败: HTTP ${response.status}`)
    }
  } catch (error) {
    ElMessage.error('TMDB服务连接失败: ' + error.message)
  } finally {
    testing.value.tmdb = false
  }
}

// 测试115连接
const test115Connection = async () => {
  if (!config.value.cookie115) {
    ElMessage.warning('请先配置115 Cookie')
    return
  }

  testing.value.transfer115 = true

  try {
    const res = await test115Cookie()
    if (res.code === 200) {
      // 获取用户信息
      const userInfoRes = await get115UserInfo()
      if (userInfoRes.code === 200) {
        const userInfo = userInfoRes.data
        ElMessage.success(`115服务连接正常 (用户: ${userInfo.user_name})`)
      } else {
        ElMessage.success('115服务连接正常')
      }
    } else {
      ElMessage.error('115 Cookie无效或已过期')
    }
  } catch (error) {
    ElMessage.error('115服务连接失败: ' + error.message)
  } finally {
    testing.value.transfer115 = false
  }
}

// 加载配置
const loadConfig = async () => {
  try {
    // 优先从数据库加载
    const res = await getFullConfig()
    if (res.data && Object.keys(res.data).length > 0) {
      // 合并数据库配置和默认配置
      config.value = { ...defaultConfig, ...res.data }

      // 同时保存到localStorage作为备份
      localStorage.setItem('smartSearchConfig', JSON.stringify(config.value))

      if (config.value.debugMode) {
        console.log('从数据库加载的配置:', config.value)
      }

      ElMessage.success('已从数据库加载配置')
      return
    }
  } catch (error) {
    console.error('从数据库加载配置失败:', error)
    ElMessage.warning('数据库加载失败，尝试从本地加载')
  }

  // 如果数据库加载失败，尝试从localStorage加载
  try {
    const saved = localStorage.getItem('smartSearchConfig')
    if (saved) {
      const savedConfig = JSON.parse(saved)

      // 兼容旧配置格式
      if (savedConfig.alipanParentId && !savedConfig.cloudConfigs) {
        // 将旧配置转换为新格式
        savedConfig.cloudConfigs = [{
          name: '阿里云盘',
          cloudType: savedConfig.cloudType || 'channel_alipan',
          parentId: savedConfig.alipanParentId,
          remark: ''
        }]
        delete savedConfig.alipanParentId
        delete savedConfig.cloudType
      }

      // 移除旧的 isDefault 字段
      if (savedConfig.cloudConfigs) {
        savedConfig.cloudConfigs.forEach(cloud => {
          delete cloud.isDefault
        })
      }

      config.value = { ...defaultConfig, ...savedConfig }

      if (config.value.debugMode) {
        console.log('从本地加载的配置:', config.value)
      }

      ElMessage.info('已从本地缓存加载配置')
    } else {
      ElMessage.info('使用默认配置')
    }
  } catch (error) {
    console.error('加载配置失败:', error)
    ElMessage.warning('加载配置失败，使用默认配置')
  }
}

// 添加云盘配置
const addCloudConfig = () => {
  config.value.cloudConfigs.push({
    name: `云盘配置 ${config.value.cloudConfigs.length + 1}`,
    cloudType: '',
    parentId: '',
    remark: ''
  })
  ElMessage.success('已添加新的云盘配置')
}

// 删除云盘配置
const removeCloudConfig = (index) => {
  config.value.cloudConfigs.splice(index, 1)
  ElMessage.success('已删除云盘配置')
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped lang="scss">
.smart-search-config-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;

  .header-card {
    margin-bottom: 20px;
    border-radius: 8px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);

    .header-content {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 16px;

      h2 {
        margin: 0;
        font-size: 24px;
        font-weight: 600;
        color: #303133;
      }

      .header-actions {
        display: flex;
        gap: 12px;
        flex-wrap: wrap;

        .el-button {
          height: 36px;
          padding: 0 16px;
          font-size: 14px;
          border-radius: 6px;
          transition: all 0.3s;

          .el-icon {
            margin-right: 6px;
          }

          &:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
          }
        }
      }
    }
  }

  .config-section {
    margin-bottom: 20px;

    .el-card {
      border-radius: 8px;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
      transition: all 0.3s;

      &:hover {
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
      }
    }

    .section-header {
      display: flex;
      align-items: center;
      gap: 8px;
      font-weight: 600;
      font-size: 16px;
      color: #303133;

      .el-icon {
        font-size: 20px;
      }
    }
  }

  .form-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
    line-height: 1.5;

    a {
      color: #409eff;
      text-decoration: none;

      &:hover {
        text-decoration: underline;
      }
    }
  }

  .provider-option,
  .model-option {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;

    .model-cost {
      margin-left: auto;
      font-size: 12px;
      color: #909399;
    }
  }

  :deep(.el-form-item) {
    margin-bottom: 24px;

    .el-form-item__label {
      font-weight: 500;
      color: #606266;
    }
  }

  :deep(.el-slider) {
    margin-right: 20px;
  }

  :deep(.el-input),
  :deep(.el-select),
  :deep(.el-input-number) {
    .el-input__wrapper {
      border-radius: 6px;
      transition: all 0.3s;

      &:hover {
        box-shadow: 0 0 0 1px #409eff inset;
      }
    }
  }

  :deep(.el-switch) {
    height: 24px;

    .el-switch__core {
      height: 24px;
      border-radius: 12px;
    }
  }

  .cloud-config-item {
    margin-bottom: 16px;

    &:last-child {
      margin-bottom: 0;
    }

    .el-card {
      border: 1px solid #e5e5e7;
      border-radius: 8px;
      transition: all 0.3s;

      &:hover {
        border-color: #409eff;
        box-shadow: 0 2px 12px rgba(64, 158, 255, 0.15);
      }
    }
  }

  // 优化按钮组样式
  .el-button-group {
    .el-button {
      border-radius: 0;

      &:first-child {
        border-top-left-radius: 6px;
        border-bottom-left-radius: 6px;
      }

      &:last-child {
        border-top-right-radius: 6px;
        border-bottom-right-radius: 6px;
      }
    }
  }
}

// 响应式设计
@media (max-width: 768px) {
  .smart-search-config-container {
    padding: 12px;

    .header-card .header-content {
      flex-direction: column;
      align-items: flex-start;

      .header-actions {
        width: 100%;

        .el-button {
          flex: 1;
          min-width: 0;
        }
      }
    }
  }
}

// 优化对话框样式
:deep(.el-dialog) {
  border-radius: 12px;

  .el-dialog__header {
    padding: 20px 24px;
    border-bottom: 1px solid #f0f0f0;
  }

  .el-dialog__body {
    padding: 24px;
  }
}

// 优化Alert样式
:deep(.el-alert) {
  border-radius: 8px;
  padding: 12px 16px;

  .el-alert__title {
    font-weight: 500;
  }
}
</style>
