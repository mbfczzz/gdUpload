<template>
  <div class="account-container">
    <el-card shadow="never" class="main-card">
      <!-- 顶部信息栏 -->
      <div class="header-bar">
        <div class="info-section">
          <div class="info-item">
            <el-icon class="info-icon"><User /></el-icon>
            <span class="info-label">账号总数</span>
            <span class="info-value">{{ pagination.total }}</span>
          </div>
          <el-divider direction="vertical" />
          <div class="info-item">
            <el-icon class="info-icon success"><Clock /></el-icon>
            <span class="info-label">自动刷新</span>
            <span class="info-value">每30秒</span>
          </div>
        </div>

        <div class="action-section">
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
            批量删除 {{ selectedIds.length > 0 ? `(${selectedIds.length})` : '' }}
          </el-button>
        </div>
      </div>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索账号名称或邮箱"
          clearable
          @clear="handleSearch"
          @keyup.enter="handleSearch"
          style="width: 300px"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="handleSearch" style="margin-left: 12px">
          搜索
        </el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <!-- 表格 -->
      <el-table
        :data="tableData"
        v-loading="loading"
        :row-class-name="getRowClassName"
        stripe
        class="account-table"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column prop="accountName" label="账号名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="accountEmail" label="账号邮箱" min-width="200" show-overflow-tooltip />
        <el-table-column prop="rcloneConfigName" label="Rclone 配置" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="禁用时间" width="160" align="center">
          <template #default="{ row }">
            <span v-if="row.disabledTime" class="disabled-time">
              {{ formatDateTime(row.disabledTime) }}
            </span>
            <span v-else class="no-data">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="remark-text">{{ row.remark || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="280" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-divider direction="vertical" />
            <el-button
              link
              size="small"
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-divider direction="vertical" />
            <el-button link type="primary" size="small" @click="handleProbe(row)">
              探测
            </el-button>
            <el-divider direction="vertical" />
            <el-button link type="danger" size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="handleSearch"
          background
        />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      @close="handleDialogClose"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="110px"
        label-position="right"
      >
        <el-form-item label="账号名称" prop="accountName">
          <el-input
            v-model="form.accountName"
            placeholder="请输入账号名称，如：GD账号1"
            clearable
          />
        </el-form-item>
        <el-form-item label="账号邮箱" prop="accountEmail">
          <el-input
            v-model="form.accountEmail"
            placeholder="请输入Google账号邮箱"
            clearable
          />
        </el-form-item>
        <el-form-item label="Rclone 配置" prop="rcloneConfigName">
          <el-input
            v-model="form.rcloneConfigName"
            placeholder="请输入rclone配置名称，如：gd1"
            clearable
          >
            <template #append>
              <el-button>查看配置</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1" border>启用</el-radio>
            <el-radio :label="0" border>禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注信息（可选）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit">
            {{ form.id ? '保存修改' : '立即创建' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, Search, Plus, Delete, User } from '@element-plus/icons-vue'
import {
  getAccountPage,
  addAccount,
  updateAccount,
  deleteAccount,
  batchDeleteAccount,
  toggleAccountStatus,
  probeAccount
} from '@/api/account'

let refreshInterval = null

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
  status: 1,
  remark: ''
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

// 获取状态类型
const getStatusType = (status) => {
  const types = {
    0: 'info',
    1: 'success'
  }
  return types[status] || 'info'
}

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    0: '禁用',
    1: '启用'
  }
  return texts[status] || '未知'
}

// 格式化日期时间
const formatDateTime = (dateTimeStr) => {
  if (!dateTimeStr) return '-'
  const date = new Date(dateTimeStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
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

// 探测账号
const handleProbe = async (row) => {
  const loadingMsg = ElMessage({
    message: '正在探测账号，请稍候...',
    type: 'info',
    duration: 0
  })

  try {
    const res = await probeAccount(row.id)
    loadingMsg.close()

    if (res.code === 200) {
      ElMessage.success(`探测成功: ${res.data.message}`)
    } else {
      if (res.data && res.data.quotaExceeded) {
        ElMessage.error(`探测失败: 配额超限`)
      } else {
        ElMessage.error(`探测失败: ${res.message}`)
      }
    }
    // 刷新列表以更新状态
    handleSearch()
  } catch (error) {
    loadingMsg.close()
    ElMessage.error('探测失败: ' + (error.message || '未知错误'))
    console.error('探测失败:', error)
  }
}

// 初始化
onMounted(() => {
  // 加载账号列表
  handleSearch()
  // 每30秒自动刷新一次账号列表（静默刷新）
  refreshInterval = setInterval(silentRefresh, 30000)
})

// 清理定时器
onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
  }
})
</script>

<style scoped>
.account-container {
  height: 100%;
  padding: 0;
}

.main-card {
  border-radius: 8px;
}

/* 顶部信息栏 */
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  margin-bottom: 20px;
  border-bottom: 1px solid #ebeef5;
}

.info-section {
  display: flex;
  align-items: center;
  gap: 20px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-icon {
  font-size: 18px;
  color: #409eff;
}

.info-icon.success {
  color: #67c23a;
}

.info-label {
  font-size: 14px;
  color: #606266;
}

.info-value {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.action-section {
  display: flex;
  gap: 12px;
}

/* 搜索栏 */
.search-bar {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
}

/* 表格样式 */
.account-table {
  margin-top: 0;
}

.remark-text {
  color: #909399;
  font-size: 13px;
}

.disabled-time {
  color: #606266;
  font-size: 13px;
}

.no-data {
  color: #c0c4cc;
}

/* 禁用账号行样式 */
:deep(.disabled-row) {
  background-color: #fafafa !important;
  opacity: 0.75;
}

:deep(.disabled-row:hover > td) {
  background-color: #f5f5f5 !important;
}

/* 表格单元格内边距优化 */
:deep(.el-table .cell) {
  padding: 0 8px;
}

/* 分页 */
.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* 对话框 */
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.5;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* 优化按钮间距 */
:deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
