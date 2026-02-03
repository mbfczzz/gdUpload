<template>
  <div class="emby-config-container">
    <el-card class="header-card">
      <div class="header-content">
        <h2>Emby 配置管理</h2>
        <el-button type="primary" @click="showAddDialog">
          <el-icon><Plus /></el-icon>
          添加配置
        </el-button>
      </div>
    </el-card>

    <!-- 配置列表 -->
    <el-card>
      <el-table :data="configs" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="60" align="center" />

        <el-table-column prop="configName" label="配置名称" min-width="150">
          <template #default="{ row }">
            <div class="config-name">
              <el-tag v-if="row.isDefault" type="success" size="small">默认</el-tag>
              <span>{{ row.configName }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="serverUrl" label="服务器地址" min-width="200" show-overflow-tooltip />

        <el-table-column prop="apiKey" label="认证方式" width="150" align="center">
          <template #default="{ row }">
            <div style="display: flex; flex-direction: column; gap: 4px; align-items: center;">
              <el-tag v-if="row.apiKey" type="success" size="small">API Key</el-tag>
              <el-tag v-if="row.username" type="warning" size="small">用户名密码</el-tag>
              <el-tag v-if="!row.apiKey && !row.username" type="info" size="small">未配置</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="username" label="用户名" width="120">
          <template #default="{ row }">
            {{ row.username || '-' }}
          </template>
        </el-table-column>

        <el-table-column prop="enabled" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              @change="handleToggle(row)"
            />
          </template>
        </el-table-column>

        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />

        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleTest(row)">
              <el-icon><Connection /></el-icon>
              测试
            </el-button>
            <el-button type="success" link size="small" @click="handleSetDefault(row)" v-if="!row.isDefault">
              <el-icon><Star /></el-icon>
              设为默认
            </el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
      >
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="form.configName" placeholder="如：我的Emby服务器" />
        </el-form-item>

        <el-form-item label="服务器地址" prop="serverUrl">
          <el-input v-model="form.serverUrl" placeholder="http://your-emby-server:8096" />
        </el-form-item>

        <el-divider content-position="left">认证方式（优先使用API Key）</el-divider>

        <el-alert
          title="推荐：从Forward抓包获取API Key和用户ID"
          type="info"
          :closable="false"
          style="margin-bottom: 15px;"
        >
          <template #default>
            <div style="font-size: 13px; line-height: 1.8;">
              <p style="margin: 0 0 8px 0;"><strong>方法1：使用API Key（推荐，不会被Cloudflare拦截）</strong></p>
              <p style="margin: 0 0 4px 0; padding-left: 12px;">1. 在iPhone上安装抓包工具（如Stream）</p>
              <p style="margin: 0 0 4px 0; padding-left: 12px;">2. 开启抓包，打开Forward app</p>
              <p style="margin: 0 0 4px 0; padding-left: 12px;">3. 在抓包记录中找到Emby API请求</p>
              <p style="margin: 0 0 4px 0; padding-left: 12px;">4. 复制请求头中的 <code>X-Emby-Token</code> 值到"API Key"字段</p>
              <p style="margin: 0 0 12px 0; padding-left: 12px;">5. 复制 <code>X-Emby-Authorization</code> 中的 <code>Emby UserId="xxx"</code> 值到"用户ID"字段</p>
              <p style="margin: 0;"><strong>方法2：使用用户名密码（备用，可能被Cloudflare拦截）</strong></p>
            </div>
          </template>
        </el-alert>

        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            placeholder="从Forward抓包获取的AccessToken（推荐）"
            clearable
          />
        </el-form-item>

        <el-form-item label="用户ID">
          <el-input
            v-model="form.userId"
            placeholder="从Forward抓包获取的UserId（使用API Key时必填）"
            clearable
          />
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">
            在Forward请求头X-Emby-Authorization中找到 Emby UserId="xxx" 的值
          </div>
        </el-form-item>

        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="Emby用户名（备用）" clearable />
        </el-form-item>

        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="Emby密码（备用）" clearable />
        </el-form-item>

        <el-divider content-position="left">其他设置</el-divider>

        <el-form-item label="超时时间">
          <el-input-number v-model="form.timeout" :min="5000" :max="120000" :step="1000" />
          <span style="margin-left: 10px; color: #909399;">毫秒</span>
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>

        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAllConfigs,
  saveConfig,
  deleteConfig,
  setDefaultConfig,
  testConfig,
  toggleConfig
} from '@/api/embyConfig'

const loading = ref(false)
const saving = ref(false)
const configs = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('添加配置')
const formRef = ref(null)

const form = ref({
  id: null,
  configName: '',
  serverUrl: '',
  apiKey: '',
  username: '',
  password: '',
  userId: '',
  timeout: 30000,
  enabled: true,
  isDefault: false,
  remark: ''
})

const rules = {
  configName: [
    { required: true, message: '请输入配置名称', trigger: 'blur' }
  ],
  serverUrl: [
    { required: true, message: '请输入服务器地址', trigger: 'blur' },
    { pattern: /^https?:\/\/.+/, message: '请输入有效的URL', trigger: 'blur' }
  ]
}

// 加载配置列表
const loadConfigs = async () => {
  loading.value = true
  try {
    const res = await getAllConfigs()
    configs.value = res.data
  } catch (error) {
    ElMessage.error('加载配置失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

// 显示添加对话框
const showAddDialog = () => {
  dialogTitle.value = '添加配置'
  form.value = {
    id: null,
    configName: '',
    serverUrl: '',
    apiKey: '',
    username: '',
    password: '',
    userId: '',
    timeout: 30000,
    enabled: true,
    isDefault: false,
    remark: ''
  }
  dialogVisible.value = true
}

// 编辑配置
const handleEdit = (row) => {
  dialogTitle.value = '编辑配置'
  form.value = { ...row }
  dialogVisible.value = true
}

// 保存配置
const handleSave = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    // 至少需要配置一种认证方式
    if (!form.value.apiKey && !form.value.username) {
      ElMessage.warning('请至少配置一种认证方式（API Key 或 用户名密码）')
      return
    }

    // 如果配置了API Key，必须填写userId
    if (form.value.apiKey && !form.value.userId) {
      ElMessage.warning('使用API Key时必须填写用户ID')
      return
    }

    saving.value = true

    try {
      const res = await saveConfig(form.value)
      if (res.code === 200) {
        ElMessage.success('保存成功')
        dialogVisible.value = false
        await loadConfigs()
      }
    } catch (error) {
      ElMessage.error('保存失败: ' + error.message)
    } finally {
      saving.value = false
    }
  })
}

// 删除配置
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除配置"${row.configName}"吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const res = await deleteConfig(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      await loadConfigs()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败: ' + error.message)
    }
  }
}

// 设为默认
const handleSetDefault = async (row) => {
  try {
    const res = await setDefaultConfig(row.id)
    if (res.code === 200) {
      ElMessage.success('设置成功')
      await loadConfigs()
    }
  } catch (error) {
    ElMessage.error('设置失败: ' + error.message)
  }
}

// 测试配置
const handleTest = async (row) => {
  const loading = ElMessage({
    message: '正在测试连接...',
    type: 'info',
    duration: 0
  })

  try {
    // 只传递 ID，后端会从数据库获取完整配置
    const res = await testConfig({ id: row.id })
    loading.close()

    if (res.data) {
      ElMessage.success('连接测试成功')
    } else {
      ElMessage.error('连接测试失败，请检查服务器地址、用户名和密码')
    }
  } catch (error) {
    loading.close()
    ElMessage.error('测试失败: ' + error.message)
  }
}

// 切换启用状态
const handleToggle = async (row) => {
  try {
    const res = await toggleConfig(row.id)
    if (res.code === 200) {
      ElMessage.success(row.enabled ? '已启用' : '已禁用')
    }
  } catch (error) {
    ElMessage.error('操作失败: ' + error.message)
    row.enabled = !row.enabled
  }
}

onMounted(() => {
  loadConfigs()
})
</script>

<style scoped lang="scss">
.emby-config-container {
  padding: 20px;

  .header-card {
    margin-bottom: 20px;

    .header-content {
      display: flex;
      justify-content: space-between;
      align-items: center;

      h2 {
        margin: 0;
      }
    }
  }

  .config-name {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}
</style>
