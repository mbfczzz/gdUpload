<template>
  <div class="test-container">
    <el-card>
      <template #header>
        <h3>Emby API 测试工具</h3>
      </template>

      <el-form label-width="120px">
        <el-form-item label="服务器地址">
          <el-input v-model="serverUrl" placeholder="http://your-emby-server:8096" />
        </el-form-item>

        <el-form-item label="API Key">
          <el-input v-model="apiKey" type="password" show-password placeholder="输入你的 API Key" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="testApi" :loading="testing">
            测试 API
          </el-button>
          <el-button @click="clearResult">清空结果</el-button>
        </el-form-item>
      </el-form>

      <el-divider />

      <div v-if="result" class="result-container">
        <h4>测试结果：</h4>
        <el-alert
          :title="result.success ? '连接成功' : '连接失败'"
          :type="result.success ? 'success' : 'error'"
          :closable="false"
          show-icon
        />

        <div v-if="result.data" class="result-data">
          <pre>{{ JSON.stringify(result.data, null, 2) }}</pre>
        </div>

        <div v-if="result.error" class="result-error">
          <el-alert
            :title="result.error"
            type="error"
            :closable="false"
          />
        </div>
      </div>
    </el-card>

    <el-card style="margin-top: 20px;">
      <template #header>
        <h3>快速测试</h3>
      </template>

      <el-space wrap>
        <el-button @click="quickTest('serverInfo')" :loading="testing">
          获取服务器信息
        </el-button>
        <el-button @click="quickTest('libraries')" :loading="testing">
          获取媒体库列表
        </el-button>
        <el-button @click="quickTest('genres')" :loading="testing">
          获取类型列表
        </el-button>
        <el-button @click="quickTest('tags')" :loading="testing">
          获取标签列表
        </el-button>
        <el-button @click="quickTest('studios')" :loading="testing">
          获取工作室列表
        </el-button>
      </el-space>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const serverUrl = ref('http://localhost:8096')
const apiKey = ref('')
const testing = ref(false)
const result = ref(null)

const testApi = async () => {
  if (!serverUrl.value || !apiKey.value) {
    ElMessage.warning('请输入服务器地址和 API Key')
    return
  }

  testing.value = true
  result.value = null

  try {
    const response = await axios.get(`${serverUrl.value}/emby/System/Info`, {
      headers: {
        'X-Emby-Token': apiKey.value
      }
    })

    result.value = {
      success: true,
      data: response.data
    }

    ElMessage.success('API 测试成功')
  } catch (error) {
    result.value = {
      success: false,
      error: error.message
    }

    ElMessage.error('API 测试失败: ' + error.message)
  } finally {
    testing.value = false
  }
}

const quickTest = async (type) => {
  testing.value = true
  result.value = null

  try {
    let url = ''
    switch (type) {
      case 'serverInfo':
        url = '/api/emby/server-info'
        break
      case 'libraries':
        url = '/api/emby/libraries'
        break
      case 'genres':
        url = '/api/emby/genres'
        break
      case 'tags':
        url = '/api/emby/tags'
        break
      case 'studios':
        url = '/api/emby/studios'
        break
    }

    const response = await axios.get(url)

    result.value = {
      success: true,
      data: response.data
    }

    ElMessage.success('测试成功')
  } catch (error) {
    result.value = {
      success: false,
      error: error.response?.data?.message || error.message
    }

    ElMessage.error('测试失败: ' + (error.response?.data?.message || error.message))
  } finally {
    testing.value = false
  }
}

const clearResult = () => {
  result.value = null
}
</script>

<style scoped lang="scss">
.test-container {
  padding: 20px;

  .result-container {
    margin-top: 20px;

    h4 {
      margin-bottom: 10px;
    }

    .result-data {
      margin-top: 15px;
      background: #f5f5f7;
      padding: 15px;
      border-radius: 8px;
      overflow-x: auto;

      pre {
        margin: 0;
        font-family: 'Monaco', 'Menlo', monospace;
        font-size: 12px;
        line-height: 1.6;
        color: #1d1d1f;
      }
    }

    .result-error {
      margin-top: 15px;
    }
  }
}
</style>
