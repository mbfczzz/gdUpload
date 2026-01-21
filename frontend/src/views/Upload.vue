<template>
  <div class="upload-container">
    <!-- 创建任务卡片 -->
    <el-card class="create-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">创建上传任务</span>
        </div>
      </template>

      <el-form :model="taskForm" label-width="120px">
        <el-form-item label="任务名称">
          <el-input v-model="taskForm.taskName" placeholder="请输入任务名称" />
        </el-form-item>

        <el-form-item label="源路径">
          <el-input v-model="taskForm.sourcePath" placeholder="请输入服务器上的文件路径，如 /data/files">
            <template #append>
              <el-button @click="handleScanDirectory">
                <el-icon><FolderOpened /></el-icon>
                扫描目录
              </el-button>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="目标路径">
          <el-input v-model="taskForm.targetPath" placeholder="请输入Google Drive目标路径，如 /backup" />
        </el-form-item>

        <el-form-item label="递归扫描">
          <el-switch v-model="taskForm.recursive" />
          <span style="margin-left: 10px; color: rgba(255, 255, 255, 0.6); font-size: 12px;">
            开启后将扫描子目录中的所有文件
          </span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 文件列表卡片 -->
    <el-card class="file-list-card" v-if="scannedFiles.length > 0">
      <template #header>
        <div class="card-header">
          <span class="card-title">
            已扫描文件 ({{ selectedFiles.length }} / {{ scannedFiles.length }})
          </span>
          <div>
            <el-button type="warning" @click="handleSmartSelect">
              <el-icon><MagicStick /></el-icon>
              智能选择
            </el-button>
            <el-button type="primary" @click="handleSelectAll">
              <el-icon><Select /></el-icon>
              全选
            </el-button>
            <el-button @click="handleDeselectAll">
              <el-icon><Close /></el-icon>
              取消全选
            </el-button>
            <el-button type="success" :disabled="selectedFiles.length === 0" @click="handleCreateTask">
              <el-icon><Upload /></el-icon>
              创建任务
            </el-button>
          </div>
        </div>
      </template>

      <div class="file-stats">
        <el-statistic title="文件总数" :value="scannedFiles.length" />
        <el-statistic title="已选文件" :value="selectedFiles.length" />
        <el-statistic title="总大小" :value="formatSize(totalSize)" />
        <el-statistic title="已选大小" :value="formatSize(selectedSize)" />
      </div>

      <el-table
        ref="fileTableRef"
        :data="scannedFiles"
        style="width: 100%; margin-top: 16px;"
        max-height="500"
        @selection-change="handleSelectionChange"
        v-loading="scanning"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="filePath" label="文件路径" min-width="300" show-overflow-tooltip />
        <el-table-column label="文件大小" width="120">
          <template #default="{ row }">
            {{ formatSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column label="文件类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ getFileExtension(row.fileName) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 空状态 -->
    <el-card v-if="scannedFiles.length === 0 && !scanning" class="empty-card">
      <el-empty description="请输入源路径并点击扫描目录按钮">
        <template #image>
          <el-icon :size="100" style="color: #86868b;">
            <FolderOpened />
          </el-icon>
        </template>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  FolderOpened,
  Select,
  Close,
  Upload,
  MagicStick
} from '@element-plus/icons-vue'
import axios from 'axios'

const router = useRouter()

const fileTableRef = ref(null)
const scanning = ref(false)
const scannedFiles = ref([])
const selectedFiles = ref([])
const scanMetadata = ref(null) // 存储扫描元数据

const taskForm = reactive({
  taskName: '',
  sourcePath: '',
  targetPath: '',
  recursive: false
})

// 计算总大小
const totalSize = computed(() => {
  return scannedFiles.value.reduce((sum, file) => sum + file.fileSize, 0)
})

// 计算已选大小
const selectedSize = computed(() => {
  return selectedFiles.value.reduce((sum, file) => sum + file.fileSize, 0)
})

// 扫描目录
const handleScanDirectory = async () => {
  if (!taskForm.sourcePath) {
    ElMessage.warning('请输入源路径')
    return
  }

  scanning.value = true
  scannedFiles.value = []
  selectedFiles.value = []

  try {
    const { data } = await axios.post('/api/file/scan', {
      directoryPath: taskForm.sourcePath,
      recursive: taskForm.recursive,
      limit: 1000  // 最多显示1000个文件
    })

    if (data.code === 200) {
      const result = data.data
      scannedFiles.value = result.files
      scanMetadata.value = result // 保存完整的扫描结果

      // 显示扫描结果
      if (result.hasMore) {
        ElMessage.warning({
          message: result.message,
          duration: 5000,
          showClose: true
        })
      } else {
        ElMessage.success(`扫描完成，共找到 ${result.totalCount} 个文件`)
      }

      // 自动生成任务名称
      if (!taskForm.taskName) {
        const timestamp = new Date().toLocaleString('zh-CN', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit'
        }).replace(/\//g, '-')
        taskForm.taskName = `上传任务_${timestamp}`
      }
    } else {
      ElMessage.error(data.message || '扫描失败')
    }
  } catch (error) {
    ElMessage.error('扫描目录失败: ' + (error.response?.data?.message || error.message))
  } finally {
    scanning.value = false
  }
}

// 选择变化
const handleSelectionChange = (selection) => {
  selectedFiles.value = selection
}

// 全选
const handleSelectAll = () => {
  selectedFiles.value = [...scannedFiles.value]
  // 同步表格复选框状态
  if (fileTableRef.value) {
    scannedFiles.value.forEach(file => {
      fileTableRef.value.toggleRowSelection(file, true)
    })
  }
}

// 取消全选
const handleDeselectAll = () => {
  selectedFiles.value = []
  // 同步表格复选框状态
  if (fileTableRef.value) {
    fileTableRef.value.clearSelection()
  }
}

// 智能选择 - 根据账号剩余配额自动选择文件
const handleSmartSelect = async () => {
  try {
    // 获取所有账号信息
    const { data } = await axios.get('/api/account/list')

    if (data.code !== 200) {
      ElMessage.error('获取账号信息失败')
      return
    }

    const accounts = data.data

    if (!accounts || accounts.length === 0) {
      ElMessage.warning('没有可用的账号')
      return
    }

    // 计算总可用配额（只计算启用状态的账号）
    let totalAvailableQuota = 0
    accounts.forEach(account => {
      const remainingQuota = account.dailyLimit - account.usedQuota
      // 只计算启用状态(status=1)且有剩余配额的账号
      if (account.status === 1 && remainingQuota > 0) {
        totalAvailableQuota += remainingQuota
      }
    })

    if (totalAvailableQuota <= 0) {
      ElMessage.warning('所有账号配额已用完')
      return
    }

    // 按文件大小从小到大排序
    const sortedFiles = [...scannedFiles.value].sort((a, b) => a.fileSize - b.fileSize)

    // 贪心算法：从小到大选择文件，直到超过总配额
    const selected = []
    let currentSize = 0

    for (const file of sortedFiles) {
      if (currentSize + file.fileSize <= totalAvailableQuota) {
        selected.push(file)
        currentSize += file.fileSize
      }
    }

    if (selected.length === 0) {
      ElMessage.warning('没有文件可以在当前配额内上传')
      return
    }

    selectedFiles.value = selected

    // 同步表格复选框状态
    if (fileTableRef.value) {
      // 先清空所有选择
      fileTableRef.value.clearSelection()
      // 然后勾选智能选择的文件
      selected.forEach(file => {
        fileTableRef.value.toggleRowSelection(file, true)
      })
    }

    ElMessage.success({
      message: `智能选择完成：已选择 ${selected.length} 个文件，总大小 ${formatSize(currentSize)}，剩余配额 ${formatSize(totalAvailableQuota - currentSize)}`,
      duration: 5000,
      showClose: true
    })
  } catch (error) {
    ElMessage.error('智能选择失败: ' + (error.response?.data?.message || error.message))
  }
}

// 创建任务
const handleCreateTask = async () => {
  if (!taskForm.taskName) {
    ElMessage.warning('请输入任务名称')
    return
  }

  if (!taskForm.targetPath) {
    ElMessage.warning('请输入目标路径')
    return
  }

  if (selectedFiles.value.length === 0) {
    ElMessage.warning('请至少选择一个文件')
    return
  }

  try {
    // 如果扫描到的文件超过显示的文件，询问用户
    let uploadAllFiles = false
    if (scanMetadata.value && scanMetadata.value.hasMore) {
      try {
        await ElMessageBox.confirm(
          `检测到目录中共有 ${scanMetadata.value.totalCount} 个文件，但仅显示了前 ${scanMetadata.value.limit} 个。\n\n` +
          `您当前选择了 ${selectedFiles.value.length} 个文件。\n\n` +
          `是否要上传目录中的全部 ${scanMetadata.value.totalCount} 个文件？\n\n` +
          `点击"全部上传"将上传所有文件，点击"仅上传已选"将只上传您选择的文件。`,
          '文件数量提示',
          {
            confirmButtonText: '全部上传',
            cancelButtonText: '仅上传已选',
            type: 'warning',
            distinguishCancelAndClose: true
          }
        )
        uploadAllFiles = true
      } catch (action) {
        if (action === 'close') {
          return // 用户关闭对话框，取消操作
        }
        uploadAllFiles = false // 用户选择"仅上传已选"
      }
    }

    // 确认创建任务
    const fileCount = uploadAllFiles ? scanMetadata.value.totalCount : selectedFiles.value.length
    const fileSize = uploadAllFiles ? scanMetadata.value.totalSize : selectedSize.value

    await ElMessageBox.confirm(
      `确定创建上传任务吗？将上传 ${fileCount} 个文件，总大小 ${formatSize(fileSize)}`,
      '确认创建',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    )

    // 创建任务
    const taskData = {
      taskName: taskForm.taskName,
      sourcePath: taskForm.sourcePath,
      targetPath: taskForm.targetPath
    }

    // 如果上传全部，传递 uploadAll 标志；否则传递选中的文件列表
    if (uploadAllFiles) {
      taskData.uploadAll = true
      taskData.recursive = taskForm.recursive
    } else {
      taskData.fileList = selectedFiles.value.map(f => f.filePath)
    }

    const { data } = await axios.post('/api/task', taskData)

    if (data.code === 200) {
      const taskId = data.data

      // 如果不是上传全部，需要保存文件信息
      if (!uploadAllFiles) {
        await axios.post('/api/file/batch-save', {
          taskId: taskId,
          fileList: selectedFiles.value
        })
      }

      ElMessage.success('任务创建成功')

      // 询问是否立即启动
      try {
        await ElMessageBox.confirm('是否立即启动该任务？', '提示', {
          confirmButtonText: '立即启动',
          cancelButtonText: '稍后启动',
          type: 'info'
        })

        // 启动任务
        await axios.put(`/api/task/${taskId}/start`)
        ElMessage.success('任务已启动')
      } catch (error) {
        // 用户选择稍后启动
      }

      // 跳转到任务管理页面
      router.push('/task')
    } else {
      ElMessage.error(data.message || '创建任务失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('创建任务失败: ' + (error.response?.data?.message || error.message))
    }
  }
}

// 获取文件扩展名
const getFileExtension = (fileName) => {
  const ext = fileName.split('.').pop()
  return ext ? ext.toUpperCase() : 'FILE'
}

// 格式化文件大小
const formatSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}
</script>

<style scoped>
.upload-container {
  height: 100%;
  overflow-y: auto;
}

.create-card {
  margin-bottom: 16px;
}

.file-list-card {
  margin-bottom: 16px;
}

.empty-card {
  height: calc(100% - 260px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #1d1d1f;
}

.file-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  padding: 20px;
  background: #f5f5f7;
  border-radius: 12px;
  border: 1px solid #e5e5e7;
}

.file-stats :deep(.el-statistic__head) {
  color: #86868b;
  font-size: 14px;
}

.file-stats :deep(.el-statistic__content) {
  color: #1d1d1f;
  font-size: 24px;
  font-weight: 700;
}
</style>
