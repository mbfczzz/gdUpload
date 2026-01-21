<template>
  <div class="account-container">
    <el-card>
      <!-- 时间信息栏 -->
      <el-row :gutter="16" class="time-info-bar">
        <el-col :span="8">
          <el-statistic title="当前时间" :value="currentTime">
            <template #prefix>
              <el-icon><Clock /></el-icon>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="8">
          <el-statistic title="账号总数" :value="pagination.total">
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="8">
          <el-alert
            title="自动刷新"
            type="success"
            :closable="false"
            show-icon
          >
            <template #default>
              每30秒自动刷新账号状态
            </template>
          </el-alert>
        </el-col>
      </el-row>

      <el-row :gutter="16" style="margin-top: 12px;">
        <el-col :span="24">
          <el-alert
            title="Google Drive 配额说明"
            type="info"
            :closable="false"
            show-icon
          >
            <template #default>
              • <strong>每日0点重置</strong>：系统按照每天凌晨0点重置配额，不是滚动24小时窗口<br/>
              • <strong>已使用</strong>和<strong>剩余配额</strong>显示的是今日从0点开始的累计数据<br/>
              • 当配额用完时状态自动变为"已达上限"，第二天0点后配额自动恢复并重新启用<br/>
              • 账号被禁用后会在24小时后自动解封（显示预计解封时间）<br/>
              • 系统每30秒自动刷新，无需手动操作
            </template>
          </el-alert>
        </el-col>
      </el-row>

      <!-- 搜索栏 -->
      <el-row :gutter="16" class="search-bar">
        <el-col :span="6">
          <el-input
            v-model="searchForm.keyword"
            placeholder="搜索账号名称或邮箱"
            clearable
            @clear="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-col>
        <el-col :span="6">
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-col>
        <el-col :span="12" class="text-right">
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增账号
          </el-button>
          <el-button
            type="danger"
            :disabled="selectedIds.length === 0"
            @click="handleBatchDelete"
          >
            <el-icon><Delete /></el-icon>
            批量删除
          </el-button>
        </el-col>
      </el-row>

      <!-- 表格 -->
      <el-table
        :data="tableData"
        style="width: 100%; margin-top: 16px"
        @selection-change="handleSelectionChange"
        v-loading="loading"
        :row-class-name="getRowClassName"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="accountName" label="账号名称" width="150" />
        <el-table-column prop="accountEmail" label="账号邮箱" width="200" />
        <el-table-column prop="rcloneConfigName" label="Rclone配置" width="150" />
        <el-table-column label="每日限制" width="120">
          <template #default="{ row }">
            {{ formatSize(row.dailyLimit) }}
          </template>
        </el-table-column>
        <el-table-column label="已使用" width="120">
          <template #default="{ row }">
            <span :style="{ color: row.status === 2 ? '#f56c6c' : '' }">
              {{ row.status === 0 ? formatSize(0) : formatSize(row.usedQuota) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="剩余配额" width="120">
          <template #default="{ row }">
            <span :style="{ color: row.remainingQuota <= 0 ? '#f56c6c' : '#67c23a', fontWeight: row.remainingQuota <= 0 ? 'bold' : 'normal' }">
              {{ row.status === 0 ? formatSize(row.dailyLimit) : formatSize(row.remainingQuota) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="使用率" width="150">
          <template #default="{ row }">
            <el-progress
              :percentage="getUsagePercent(row)"
              :color="getProgressColor(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" :effect="row.status === 2 ? 'dark' : 'light'">
              {{ getStatusText(row.status) }}
            </el-tag>
            <el-tooltip v-if="row.status === 0" content="账号已被禁用，不会参与上传" placement="top">
              <el-icon style="margin-left: 5px; color: #909399; font-size: 16px;"><WarningFilled /></el-icon>
            </el-tooltip>
            <el-tooltip v-if="row.status === 2" content="配额已用完，等待重置" placement="top">
              <el-icon style="margin-left: 5px; color: #f56c6c;"><WarningFilled /></el-icon>
            </el-tooltip>
            <div v-if="row.status === 0 && row.quotaResetTime" style="font-size: 12px; color: #909399; margin-top: 4px;">
              预计解封: {{ formatResetTime(row.quotaResetTime) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column label="操作" fixed="right" width="250">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="primary" @click="handleResetQuota(row)">
              重置配额
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="handleSearch"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
      >
        <el-form-item label="账号名称" prop="accountName">
          <el-input v-model="form.accountName" placeholder="请输入账号名称" />
        </el-form-item>
        <el-form-item label="账号邮箱" prop="accountEmail">
          <el-input v-model="form.accountEmail" placeholder="请输入账号邮箱" />
        </el-form-item>
        <el-form-item label="Rclone配置" prop="rcloneConfigName">
          <el-input
            v-model="form.rcloneConfigName"
            placeholder="请输入rclone配置名称"
          />
        </el-form-item>
        <el-form-item label="每日限制(GB)" prop="dailyLimit">
          <el-input-number
            v-model="dailyLimitGB"
            :min="1"
            :max="10000"
            placeholder="默认750GB"
          />
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number
            v-model="form.priority"
            :min="0"
            :max="100"
            placeholder="数字越大优先级越高"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, Search, Plus, Delete, WarningFilled, User } from '@element-plus/icons-vue'
import {
  getAccountPage,
  addAccount,
  updateAccount,
  deleteAccount,
  batchDeleteAccount,
  toggleAccountStatus,
  resetAccountQuota
} from '@/api/account'

// 当前时间
const currentTime = ref('')
let timeInterval = null
let refreshInterval = null

// 更新当前时间
const updateCurrentTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
}

// 搜索表单
const searchForm = reactive({
  keyword: ''
})

// 分页
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 表格数据
const tableData = ref([])
const loading = ref(false)
const selectedIds = ref([])

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('新增账号')
const formRef = ref(null)
const form = reactive({
  id: null,
  accountName: '',
  accountEmail: '',
  rcloneConfigName: '',
  dailyLimit: 805306368000,
  priority: 0,
  status: 1,
  remark: ''
})

// GB转换
const dailyLimitGB = computed({
  get: () => Math.round(form.dailyLimit / 1073741824),
  set: (val) => {
    form.dailyLimit = val * 1073741824
  }
})

// 表单验证规则
const rules = {
  accountName: [
    { required: true, message: '请输入账号名称', trigger: 'blur' }
  ],
  accountEmail: [
    { required: true, message: '请输入账号邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  rcloneConfigName: [
    { required: true, message: '请输入rclone配置名称', trigger: 'blur' }
  ]
}

// 格式化文件大小
const formatSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

// 获取使用率
const getUsagePercent = (row) => {
  // 禁用状态显示0%
  if (row.status === 0) return 0
  if (row.dailyLimit === 0) return 0
  return Math.round((row.usedQuota / row.dailyLimit) * 100)
}

// 获取进度条颜色
const getProgressColor = (row) => {
  const percent = getUsagePercent(row)
  if (percent >= 90) return '#F56C6C'
  if (percent >= 70) return '#E6A23C'
  return '#67C23A'
}

// 获取状态类型
const getStatusType = (status) => {
  const types = {
    0: 'info',
    1: 'success',
    2: 'danger'
  }
  return types[status] || 'info'
}

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    0: '禁用',
    1: '启用',
    2: '已达上限'
  }
  return texts[status] || '未知'
}

// 格式化解封时间
const formatResetTime = (timeStr) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 获取表格行样式
const getRowClassName = ({ row }) => {
  if (row.status === 0) {
    return 'disabled-row'
  }
  return ''
}

// 查询列表
const handleSearch = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      keyword: searchForm.keyword
    }
    const res = await getAccountPage(params)
    tableData.value = res.data.records
    pagination.total = res.data.total
  } catch (error) {
    console.error('查询失败:', error)
  } finally {
    loading.value = false
  }
}

// 静默刷新列表（不显示loading）
const silentRefresh = async () => {
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      keyword: searchForm.keyword
    }
    const res = await getAccountPage(params)
    tableData.value = res.data.records
    pagination.total = res.data.total
  } catch (error) {
    console.error('静默刷新失败:', error)
  }
}

// 重置搜索
const handleReset = () => {
  searchForm.keyword = ''
  pagination.current = 1
  handleSearch()
}

// 选择变化
const handleSelectionChange = (selection) => {
  selectedIds.value = selection.map(item => item.id)
}

// 新增
const handleAdd = () => {
  dialogTitle.value = '新增账号'
  Object.assign(form, {
    id: null,
    accountName: '',
    accountEmail: '',
    rcloneConfigName: '',
    dailyLimit: 805306368000,
    priority: 0,
    status: 1,
    remark: ''
  })
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row) => {
  dialogTitle.value = '编辑账号'
  Object.assign(form, row)
  dialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (form.id) {
          await updateAccount(form)
          ElMessage.success('更新成功')
        } else {
          await addAccount(form)
          ElMessage.success('添加成功')
        }
        dialogVisible.value = false
        handleSearch()
      } catch (error) {
        console.error('提交失败:', error)
      }
    }
  })
}

// 关闭对话框
const handleDialogClose = () => {
  formRef.value?.resetFields()
}

// 删除
const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该账号吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteAccount(row.id)
      ElMessage.success('删除成功')
      handleSearch()
    } catch (error) {
      console.error('删除失败:', error)
    }
  })
}

// 批量删除
const handleBatchDelete = () => {
  ElMessageBox.confirm('确定要删除选中的账号吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await batchDeleteAccount(selectedIds.value)
      ElMessage.success('删除成功')
      handleSearch()
    } catch (error) {
      console.error('删除失败:', error)
    }
  })
}

// 切换状态
const handleToggleStatus = (row) => {
  const status = row.status === 1 ? 0 : 1
  const text = status === 1 ? '启用' : '禁用'
  ElMessageBox.confirm(`确定要${text}该账号吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await toggleAccountStatus(row.id, status)
      ElMessage.success(`${text}成功`)
      handleSearch()
    } catch (error) {
      console.error('操作失败:', error)
    }
  })
}

// 重置配额
const handleResetQuota = (row) => {
  ElMessageBox.confirm('确定要重置该账号的配额吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await resetAccountQuota(row.id)
      ElMessage.success('重置成功')
      handleSearch()
    } catch (error) {
      console.error('重置失败:', error)
    }
  })
}

// 初始化
onMounted(() => {
  // 初始化当前时间
  updateCurrentTime()
  // 每秒更新一次当前时间
  timeInterval = setInterval(updateCurrentTime, 1000)
  // 加载账号列表
  handleSearch()
  // 每30秒自动刷新一次账号列表（静默刷新）
  refreshInterval = setInterval(silentRefresh, 30000)
})

// 清理定时器
onUnmounted(() => {
  if (timeInterval) {
    clearInterval(timeInterval)
  }
  if (refreshInterval) {
    clearInterval(refreshInterval)
  }
})
</script>

<style scoped>
.account-container {
  height: 100%;
}

.time-info-bar {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.search-bar {
  margin-top: 16px;
  margin-bottom: 0;
}

.text-right {
  text-align: right;
}

/* 禁用账号行样式 */
:deep(.disabled-row) {
  background-color: #f5f7fa !important;
  opacity: 0.7;
}

:deep(.disabled-row:hover > td) {
  background-color: #f0f2f5 !important;
}
</style>
