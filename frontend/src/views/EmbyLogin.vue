<template>
  <div class="emby-login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <h2>Emby 登录</h2>
          <el-tag v-if="isAuthenticated" type="success">已登录</el-tag>
        </div>
      </template>

      <el-alert
        v-if="!isAuthenticated"
        title="使用用户名密码登录"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 20px"
      >
        <template #default>
          <p>如果你没有 API Key，可以使用 Emby 账号的用户名和密码登录。</p>
          <p>登录后系统会自动获取访问令牌。</p>
        </template>
      </el-alert>

      <el-form
        v-if="!isAuthenticated"
        :model="loginForm"
        :rules="rules"
        ref="loginFormRef"
        label-width="100px"
      >
        <el-form-item label="服务器地址" prop="serverUrl">
          <el-input
            v-model="loginForm.serverUrl"
            placeholder="http://your-emby-server:8096"
          />
        </el-form-item>

        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="输入你的 Emby 用户名"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            show-password
            placeholder="输入你的 Emby 密码"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleLogin" :loading="logging" style="width: 100%">
            <el-icon><User /></el-icon>
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div v-else class="auth-info">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户ID">
            {{ authStatus.userId }}
          </el-descriptions-item>
          <el-descriptions-item label="认证状态">
            <el-tag type="success">已认证</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div style="margin-top: 20px; text-align: center;">
          <el-button type="danger" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
            登出
          </el-button>
          <el-button type="primary" @click="goToEmbyManager">
            <el-icon><VideoPlay /></el-icon>
            进入 Emby 管理
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card style="margin-top: 20px;">
      <template #header>
        <h3>说明</h3>
      </template>

      <el-collapse>
        <el-collapse-item title="方式一：使用 API Key（推荐）" name="1">
          <p>如果你是服务器管理员，推荐使用 API Key 方式：</p>
          <ol>
            <li>登录 Emby 管理后台</li>
            <li>进入 设置 → 高级 → API 密钥</li>
            <li>创建新的 API Key</li>
            <li>在 <code>application.yml</code> 中配置</li>
          </ol>
        </el-collapse-item>

        <el-collapse-item title="方式二：使用用户名密码" name="2">
          <p>如果你使用别人的 Emby 服务器：</p>
          <ol>
            <li>在上方表单中输入服务器地址</li>
            <li>输入你的 Emby 用户名和密码</li>
            <li>点击登录按钮</li>
            <li>登录成功后即可使用所有功能</li>
          </ol>
          <el-alert
            title="注意"
            type="warning"
            :closable="false"
            style="margin-top: 10px"
          >
            <p>密码会通过 HTTPS 加密传输，但建议使用 API Key 方式更安全。</p>
          </el-alert>
        </el-collapse-item>

        <el-collapse-item title="方式三：向管理员索取 API Key" name="3">
          <p>联系 Emby 服务器管理员：</p>
          <blockquote style="background: #f5f5f7; padding: 10px; border-left: 3px solid #007aff;">
            你好，我需要通过 API 访问 Emby 服务器，<br>
            能否为我创建一个 API Key？<br>
            应用名称可以填：GD Upload Manager
          </blockquote>
          <p>管理员创建后会给你一个 API Key，配置到 <code>application.yml</code> 即可。</p>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const router = useRouter()

const logging = ref(false)
const isAuthenticated = ref(false)
const authStatus = ref({})

const loginForm = ref({
  serverUrl: 'http://localhost:8096',
  username: '',
  password: ''
})

const loginFormRef = ref(null)

const rules = {
  serverUrl: [
    { required: true, message: '请输入服务器地址', trigger: 'blur' }
  ],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

// 检查认证状态
const checkAuthStatus = async () => {
  try {
    const res = await axios.get('/api/emby/auth/status')
    if (res.data.code === 200 && res.data.data.authenticated) {
      isAuthenticated.value = true
      authStatus.value = res.data.data
    }
  } catch (error) {
    console.error('检查认证状态失败:', error)
  }
}

// 登录
const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return

    logging.value = true

    try {
      // 先更新后端配置（如果需要）
      // 这里假设后端会读取配置文件中的 server-url

      // 调用登录接口
      const res = await axios.post('/api/emby/auth/login', {
        username: loginForm.value.username,
        password: loginForm.value.password
      })

      if (res.data.code === 200) {
        ElMessage.success('登录成功')
        isAuthenticated.value = true
        authStatus.value = {
          userId: res.data.data.userId,
          authenticated: true
        }

        // 清空密码
        loginForm.value.password = ''
      } else {
        ElMessage.error(res.data.message || '登录失败')
      }
    } catch (error) {
      ElMessage.error('登录失败: ' + (error.response?.data?.message || error.message))
    } finally {
      logging.value = false
    }
  })
}

// 登出
const handleLogout = async () => {
  try {
    await axios.post('/api/emby/auth/logout')
    ElMessage.success('已登出')
    isAuthenticated.value = false
    authStatus.value = {}
  } catch (error) {
    ElMessage.error('登出失败: ' + error.message)
  }
}

// 进入 Emby 管理页面
const goToEmbyManager = () => {
  router.push('/emby')
}

onMounted(() => {
  checkAuthStatus()
})
</script>

<style scoped lang="scss">
.emby-login-container {
  padding: 20px;
  max-width: 600px;
  margin: 0 auto;

  .login-card {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      h2 {
        margin: 0;
      }
    }
  }

  .auth-info {
    padding: 20px 0;
  }

  code {
    background: #f5f5f7;
    padding: 2px 6px;
    border-radius: 3px;
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 13px;
  }

  blockquote {
    margin: 10px 0;
    font-size: 14px;
    line-height: 1.6;
  }

  ol {
    padding-left: 20px;
    line-height: 1.8;
  }

  p {
    margin: 10px 0;
    line-height: 1.6;
  }
}
</style>
