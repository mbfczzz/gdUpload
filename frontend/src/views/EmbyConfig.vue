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

        <el-table-column prop="apiKey" label="认证方式" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.apiKey" type="primary">API Key</el-tag>
            <el-tag v-else-if="row.username" type="warning">用户名密码</el-tag>
            <el-tag v-else type="info">未配置</el-tag>
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

        <el-divider content-position="left">认证方式</el-divider>

        <el-form-item label="认证类型">
          <el-radio-group v-model="authType">
            <el-radio label="apiKey">API Key</el-radio>
            <el-radio label="password">用户名密码</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="API Key" v-if="authType === 'apiKey'" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="输入 API Key" />
        </el-form-item>

        <template v-if="authType === 'password'">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="输入用户名" />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="输入密码" />
          </el-form-item>
        </template>

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
const authType = ref('password')
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
  ],
  apiKey: [
    { required: authType.value === 'apiKey', message: '请输入API Key', trigger: 'blur' }
  ],
  username: [
    { required: authType.value === 'password', message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: authType.value === 'password', message: '请输入密码', trigger: 'blur' }
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
  authType.value = 'password'
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
  authType.value = row.apiKey ? 'apiKey' : 'password'
  form.value = { ...row }
  dialogVisible.value = true
}

// 保存配置
const handleSave = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    saving.value = true

    try {
      // 根据认证类型清空不需要的字段
      const data = { ...form.value }
      if (authType.value === 'apiKey') {
        data.username = ''
        data.password = ''
      } else {
        data.apiKey = ''
      }

      const res = await saveConfig(data)
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
