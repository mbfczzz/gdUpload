<template>
  <div class="resource115-container">
    <el-card class="header-card">
      <div class="header-content">
        <div class="header-left">
          <h2>115 资源管理</h2>
          <el-tag type="success" size="large">
            <el-icon><Files /></el-icon>
            资源库
          </el-tag>
        </div>
        <div class="header-actions">
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            添加资源
          </el-button>
          <el-button type="warning" @click="handleBatchFillTmdb" :loading="fillingTmdb">
            <el-icon><MagicStick /></el-icon>
            批量补充 TMDB ID
          </el-button>
          <el-button
            type="danger"
            :disabled="selectedIds.length === 0"
            @click="handleBatchDelete"
          >
            <el-icon><Delete /></el-icon>
            批量删除 ({{ selectedIds.length }})
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索资源名称、TMDB ID、类型..."
        clearable
        @clear="handleSearch"
        @keyup.enter="handleSearch"
        style="max-width: 400px"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
        <template #append>
          <el-button @click="handleSearch" :icon="Search">搜索</el-button>
        </template>
      </el-input>
    </el-card>

    <!-- 资源列表 -->
    <el-card class="table-card">
      <el-table
        :data="resourceList"
        v-loading="loading"
        @selection-change="handleSelectionChange"
        stripe
        border
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="资源名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="tmdbId" label="TMDB ID" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.tmdbId" type="success" size="small">{{ row.tmdbId }}</el-tag>
            <el-tag v-else type="info" size="small">未设置</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="120" />
        <el-table-column prop="size" label="大小" width="100" />
        <el-table-column prop="url" label="分享链接" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link :href="row.url" target="_blank" type="primary">{{ row.url }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="访问码" width="100" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
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

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100, 200]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item label="资源名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入资源名称" />
        </el-form-item>
        <el-form-item label="TMDB ID" prop="tmdbId">
          <el-input v-model="formData.tmdbId" placeholder="请输入 TMDB ID（可选）">
            <template #append>
              <el-button @click="handleSearchTmdb" :loading="searchingTmdb">
                <el-icon><Search /></el-icon>
                搜索
              </el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="资源类型" prop="type">
          <el-select v-model="formData.type" placeholder="请选择资源类型">
            <el-option label="国产剧" value="国产剧" />
            <el-option label="韩剧" value="韩剧" />
            <el-option label="美剧" value="美剧" />
            <el-option label="日剧" value="日剧" />
            <el-option label="电影" value="电影" />
            <el-option label="动漫" value="动漫" />
            <el-option label="综艺" value="综艺" />
            <el-option label="纪录片" value="纪录片" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源大小" prop="size">
          <el-input v-model="formData.size" placeholder="请输入资源大小（如：109.73 GB）" />
        </el-form-item>
        <el-form-item label="分享链接" prop="url">
          <el-input v-model="formData.url" placeholder="请输入 115 分享链接" />
        </el-form-item>
        <el-form-item label="访问码" prop="code">
          <el-input v-model="formData.code" placeholder="请输入访问码（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Edit,
  Delete,
  Search,
  Files,
  MagicStick
} from '@element-plus/icons-vue'
import {
  getResource115Page,
  addResource115,
  updateResource115,
  deleteResource115,
  batchDeleteResource115
} from '@/api/resource115Manage'
import { searchTmdbId, batchFillTmdbIds } from '@/api/tmdb'

// 状态
const loading = ref(false)
const resourceList = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const searchKeyword = ref('')
const selectedIds = ref([])

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('添加资源')
const formRef = ref(null)
const formData = ref({
  id: null,
  name: '',
  tmdbId: '',
  type: '',
  size: '',
  url: '',
  code: ''
})
const submitting = ref(false)
const searchingTmdb = ref(false)
const fillingTmdb = ref(false)

// 表单验证规则
const formRules = {
  name: [{ required: true, message: '请输入资源名称', trigger: 'blur' }],
  url: [{ required: true, message: '请输入分享链接', trigger: 'blur' }]
}

// 加载资源列表
const loadResourceList = async () => {
  loading.value = true
  try {
    const res = await getResource115Page(currentPage.value, pageSize.value, searchKeyword.value)
    resourceList.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    console.error('加载资源列表失败:', error)
    ElMessage.error('加载资源列表失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
  loadResourceList()
}

// 分页
const handleSizeChange = () => {
  currentPage.value = 1
  loadResourceList()
}

const handlePageChange = () => {
  loadResourceList()
}

// 选择
const handleSelectionChange = (selection) => {
  selectedIds.value = selection.map(item => item.id)
}

// 添加
const handleAdd = () => {
  dialogTitle.value = '添加资源'
  formData.value = {
    id: null,
    name: '',
    tmdbId: '',
    type: '',
    size: '',
    url: '',
    code: ''
  }
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row) => {
  dialogTitle.value = '编辑资源'
  formData.value = { ...row }
  dialogVisible.value = true
}

// 提交
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      if (formData.value.id) {
        // 更新
        await updateResource115(formData.value)
        ElMessage.success('更新成功')
      } else {
        // 添加
        await addResource115(formData.value)
        ElMessage.success('添加成功')
      }
      dialogVisible.value = false
      loadResourceList()
    } catch (error) {
      console.error('操作失败:', error)
      ElMessage.error('操作失败: ' + error.message)
    } finally {
      submitting.value = false
    }
  })
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除资源 "${row.name}" 吗？`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await deleteResource115(row.id)
    ElMessage.success('删除成功')
    loadResourceList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败: ' + error.message)
    }
  }
}

// 批量删除
const handleBatchDelete = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedIds.value.length} 个资源吗？`,
      '确认批量删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await batchDeleteResource115(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    loadResourceList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('批量删除失败:', error)
      ElMessage.error('批量删除失败: ' + error.message)
    }
  }
}

// 搜索 TMDB ID
const handleSearchTmdb = async () => {
  if (!formData.value.name) {
    ElMessage.warning('请先输入资源名称')
    return
  }

  searchingTmdb.value = true
  try {
    // 从名称中提取年份
    const yearMatch = formData.value.name.match(/\((\d{4})\)/)
    const year = yearMatch ? parseInt(yearMatch[1]) : null

    const res = await searchTmdbId(formData.value.name, year, formData.value.type)
    if (res.data) {
      formData.value.tmdbId = res.data
      ElMessage.success('找到 TMDB ID: ' + res.data)
    } else {
      ElMessage.warning('未找到匹配的 TMDB ID')
    }
  } catch (error) {
    console.error('搜索 TMDB ID 失败:', error)
    ElMessage.error('搜索失败: ' + error.message)
  } finally {
    searchingTmdb.value = false
  }
}

// 批量补充 TMDB ID
const handleBatchFillTmdb = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要批量补充所有资源的 TMDB ID 吗？这可能需要几分钟时间。',
      '确认批量补充',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    fillingTmdb.value = true
    ElMessage.info('正在批量补充 TMDB ID，请稍候...')

    const res = await batchFillTmdbIds()
    ElMessage.success(res.data.message)
    loadResourceList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('批量补充失败:', error)
      ElMessage.error('批量补充失败: ' + error.message)
    }
  } finally {
    fillingTmdb.value = false
  }
}

// 初始化
onMounted(() => {
  loadResourceList()
})
</script>

<style scoped>
.resource115-container {
  padding: 20px;
}

.header-card {
  margin-bottom: 20px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.header-left h2 {
  margin: 0;
  font-size: 24px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.search-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>
