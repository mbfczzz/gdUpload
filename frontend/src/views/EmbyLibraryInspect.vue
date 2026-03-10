<template>
  <div class="emby-library-inspect">
    <!-- 工具栏 -->
    <el-card class="toolbar-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="localPath"
            placeholder="本地STRM根目录（如 /home/user/emby/video）"
            style="width: 460px"
            clearable
            @keyup.enter="startInspect"
          />

          <el-button
            type="primary"
            :loading="loading"
            style="margin-left: 12px"
            @click="startInspect"
          >
            <el-icon><Search /></el-icon>
            开始检查
          </el-button>
        </div>

        <div class="toolbar-right">
          <el-button @click="expandAll">
            <el-icon><ArrowDown /></el-icon>
            全部展开
          </el-button>
          <el-button @click="collapseAll">
            <el-icon><ArrowUp /></el-icon>
            全部折叠
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 统计卡片 -->
    <div class="stat-cards" v-if="inspected">
      <el-card class="stat-card">
        <div class="stat-number">{{ totalFiles }}</div>
        <div class="stat-label">总文件数</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-number">{{ totalDirs }}</div>
        <div class="stat-label">总目录数</div>
      </el-card>
      <el-card class="stat-card stat-error" @click="toggleSeverityFilter('error')">
        <div class="stat-number">{{ errorCount }}</div>
        <div class="stat-label">严重问题</div>
      </el-card>
      <el-card class="stat-card stat-warning" @click="toggleSeverityFilter('warning')">
        <div class="stat-number">{{ warningCount }}</div>
        <div class="stat-label">警告</div>
      </el-card>
      <el-card class="stat-card stat-info" @click="toggleSeverityFilter('info')">
        <div class="stat-number">{{ infoCount }}</div>
        <div class="stat-label">建议</div>
      </el-card>
    </div>

    <!-- 主体：筛选面板 + 文件树 -->
    <div class="main-body" v-if="inspected">
      <!-- 左侧筛选面板 -->
      <el-card class="filter-panel">
        <template #header>
          <div class="filter-header">
            <span>筛选条件</span>
            <el-button link type="primary" size="small" @click="clearFilters">清除</el-button>
          </div>
        </template>

        <!-- 严重程度筛选 -->
        <div class="filter-section">
          <div class="filter-title">严重程度</div>
          <el-checkbox-group v-model="selectedSeverities" @change="applyFilters">
            <div class="filter-item">
              <el-checkbox label="error">
                <el-tag type="danger" size="small" effect="dark">严重</el-tag>
              </el-checkbox>
            </div>
            <div class="filter-item">
              <el-checkbox label="warning">
                <el-tag type="warning" size="small" effect="dark">警告</el-tag>
              </el-checkbox>
            </div>
            <div class="filter-item">
              <el-checkbox label="info">
                <el-tag type="info" size="small" effect="dark">建议</el-tag>
              </el-checkbox>
            </div>
          </el-checkbox-group>
        </div>

        <!-- 问题分类筛选 -->
        <div class="filter-section">
          <div class="filter-title">问题分类</div>
          <el-checkbox-group v-model="selectedCategories" @change="applyFilters">
            <div class="filter-item" v-for="cat in availableCategories" :key="cat.name">
              <el-checkbox :label="cat.name">
                <span>{{ cat.name }}</span>
                <el-badge :value="cat.count" :type="cat.badgeType" class="cat-badge" />
              </el-checkbox>
            </div>
          </el-checkbox-group>
        </div>

        <!-- 仅显示问题节点 -->
        <div class="filter-section">
          <el-checkbox v-model="onlyShowIssues" @change="applyFilters">
            仅显示有问题的节点
          </el-checkbox>
        </div>
      </el-card>

      <!-- 右侧文件树 -->
      <el-card class="tree-panel">
        <el-tree
          ref="treeRef"
          :data="filteredTree"
          :props="treeProps"
          node-key="path"
          :expand-on-click-node="true"
          :default-expand-all="false"
          :default-expanded-keys="defaultExpandedKeys"
          highlight-current
          class="inspect-tree"
        >
          <template #default="{ node, data }">
            <div class="tree-node" :class="nodeClass(data)">
              <!-- 图标 -->
              <el-icon class="node-icon" :style="{ color: iconColor(data) }">
                <component :is="iconComponent(data)" />
              </el-icon>

              <!-- 名称 -->
              <span class="node-name" :class="{ 'has-error': hasIssueLevel(data, 'error') }">
                {{ data.name }}
              </span>

              <!-- 问题标签 -->
              <span class="node-tags" v-if="data.issues && data.issues.length > 0">
                <el-tag
                  v-for="(issue, idx) in data.issues.slice(0, 3)"
                  :key="idx"
                  :type="severityTagType(issue.severity)"
                  size="small"
                  effect="plain"
                  class="issue-tag"
                >
                  {{ issue.category }}
                </el-tag>
                <el-tag
                  v-if="data.issues.length > 3"
                  type="info"
                  size="small"
                  effect="plain"
                  class="issue-tag"
                >
                  +{{ data.issues.length - 3 }}
                </el-tag>
              </span>

              <!-- 子树问题数角标 -->
              <span class="node-badges" v-if="data.dir && data.issueStats">
                <el-badge
                  v-if="data.issueStats.error > 0"
                  :value="data.issueStats.error"
                  type="danger"
                  class="tree-badge"
                />
                <el-badge
                  v-if="data.issueStats.warning > 0"
                  :value="data.issueStats.warning"
                  type="warning"
                  class="tree-badge"
                />
              </span>

              <!-- 悬浮查看详情 -->
              <el-popover
                v-if="data.issues && data.issues.length > 0"
                placement="right"
                trigger="hover"
                :width="400"
              >
                <template #reference>
                  <el-icon class="detail-icon"><InfoFilled /></el-icon>
                </template>
                <div class="issue-detail-popover">
                  <div class="issue-detail-title">{{ data.name }}</div>
                  <div
                    v-for="(issue, idx) in data.issues"
                    :key="idx"
                    class="issue-detail-item"
                  >
                    <el-tag
                      :type="severityTagType(issue.severity)"
                      size="small"
                      effect="dark"
                      class="issue-detail-tag"
                    >
                      {{ severityLabel(issue.severity) }}
                    </el-tag>
                    <span class="issue-detail-msg">{{ issue.message }}</span>
                  </div>
                </div>
              </el-popover>
            </div>
          </template>
        </el-tree>

        <!-- 空状态 -->
        <el-empty
          v-if="filteredTree.length === 0 && !loading"
          description="没有匹配的结果，请调整筛选条件"
        />
      </el-card>
    </div>

    <!-- 初始状态 -->
    <el-card v-if="!inspected && !loading" class="welcome-card">
      <el-empty description="输入本地STRM根目录，点击「开始检查」扫描Emby库文件规范">
        <template #image>
          <el-icon :size="60" color="#409eff"><Checked /></el-icon>
        </template>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Search, ArrowDown, ArrowUp, InfoFilled, Checked,
  Folder, FolderOpened, Film, Monitor, Document, VideoCamera, Collection
} from '@element-plus/icons-vue'
import { inspectLibrary } from '@/api/embyLibrary'

// ── 状态 ──────────────────────────────────────────────────
const localPath = ref('')
const loading = ref(false)
const inspected = ref(false)

const treeRef = ref(null)
const rawTree = ref([])
const filteredTree = ref([])
const defaultExpandedKeys = ref([])

// 统计数据
const totalFiles = ref(0)
const totalDirs = ref(0)
const errorCount = ref(0)
const warningCount = ref(0)
const infoCount = ref(0)

// 筛选
const selectedSeverities = ref(['error', 'warning', 'info'])
const selectedCategories = ref([])
const onlyShowIssues = ref(false)
const availableCategories = ref([])

// ── 树配置 ──────────────────────────────────────────────
const treeProps = {
  children: 'children',
  label: 'name',
  isLeaf: (data) => !data.dir
}

// ── 开始检查 ──────────────────────────────────────────────
async function startInspect() {
  if (!localPath.value || !localPath.value.trim()) {
    ElMessage.warning('请输入本地STRM根目录')
    return
  }

  loading.value = true
  inspected.value = false

  try {
    const res = await inspectLibrary(localPath.value.trim())
    rawTree.value = res.data || []

    // 统计
    const stats = { files: 0, dirs: 0, error: 0, warning: 0, info: 0 }
    const catMap = {}
    collectStats(rawTree.value, stats, catMap)
    totalFiles.value = stats.files
    totalDirs.value = stats.dirs
    errorCount.value = stats.error
    warningCount.value = stats.warning
    infoCount.value = stats.info

    // 构建分类列表
    availableCategories.value = Object.entries(catMap)
      .map(([name, info]) => ({
        name,
        count: info.count,
        badgeType: info.maxSeverity === 'error' ? 'danger' : info.maxSeverity === 'warning' ? 'warning' : 'info'
      }))
      .sort((a, b) => b.count - a.count)

    // 默认全选分类
    selectedCategories.value = availableCategories.value.map(c => c.name)

    // 默认展开第一层
    defaultExpandedKeys.value = rawTree.value.map(n => n.path)

    applyFilters()
    inspected.value = true

    ElMessage.success(`检查完成: ${stats.files} 个文件, ${stats.error} 个严重问题`)
  } catch (e) {
    ElMessage.error('检查失败: ' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// ── 递归统计 ──────────────────────────────────────────────
function collectStats(nodes, stats, catMap) {
  for (const node of nodes) {
    if (node.dir) {
      stats.dirs++
    } else {
      stats.files++
    }
    if (node.issues) {
      for (const issue of node.issues) {
        if (issue.severity === 'error') stats.error++
        else if (issue.severity === 'warning') stats.warning++
        else stats.info++

        if (!catMap[issue.category]) {
          catMap[issue.category] = { count: 0, maxSeverity: 'info' }
        }
        catMap[issue.category].count++
        if (issue.severity === 'error') catMap[issue.category].maxSeverity = 'error'
        else if (issue.severity === 'warning' && catMap[issue.category].maxSeverity !== 'error') {
          catMap[issue.category].maxSeverity = 'warning'
        }
      }
    }
    if (node.children) collectStats(node.children, stats, catMap)
  }
}

// ── 筛选逻辑 ──────────────────────────────────────────────
function applyFilters() {
  filteredTree.value = filterNodes(rawTree.value)
}

function filterNodes(nodes) {
  const result = []
  for (const node of nodes) {
    const clone = { ...node }

    // 过滤 issues
    if (clone.issues) {
      clone.issues = clone.issues.filter(issue =>
        selectedSeverities.value.includes(issue.severity) &&
        selectedCategories.value.includes(issue.category)
      )
    }

    // 递归过滤子节点
    if (clone.children) {
      clone.children = filterNodes(clone.children)
    }

    // 判断是否保留此节点
    if (onlyShowIssues.value) {
      const hasOwnIssues = clone.issues && clone.issues.length > 0
      const hasChildIssues = clone.children && clone.children.length > 0
      if (hasOwnIssues || hasChildIssues) {
        result.push(clone)
      }
    } else {
      result.push(clone)
    }
  }
  return result
}

function clearFilters() {
  selectedSeverities.value = ['error', 'warning', 'info']
  selectedCategories.value = availableCategories.value.map(c => c.name)
  onlyShowIssues.value = false
  applyFilters()
}

function toggleSeverityFilter(severity) {
  selectedSeverities.value = [severity]
  onlyShowIssues.value = true
  applyFilters()
}

// ── 展开/折叠 ──────────────────────────────────────────────
function expandAll() {
  const tree = treeRef.value
  if (!tree) return
  const allNodes = getAllTreeNodes(tree.store.root)
  allNodes.forEach(n => { n.expanded = true })
}

function collapseAll() {
  const tree = treeRef.value
  if (!tree) return
  const allNodes = getAllTreeNodes(tree.store.root)
  allNodes.forEach(n => { n.expanded = false })
}

function getAllTreeNodes(node) {
  let result = []
  if (node.childNodes) {
    for (const child of node.childNodes) {
      result.push(child)
      result = result.concat(getAllTreeNodes(child))
    }
  }
  return result
}

// ── 图标和样式辅助 ──────────────────────────────────────────

function iconComponent(data) {
  if (data.dir) {
    if (data.fileType === 'category') return Collection
    if (data.fileType === 'season') return FolderOpened
    return Folder
  }
  if (data.fileType === 'movie') return Film
  if (data.fileType === 'episode') return Monitor
  return Document
}

function iconColor(data) {
  if (hasIssueLevel(data, 'error')) return '#F56C6C'
  if (data.dir) {
    if (data.fileType === 'category') return '#409EFF'
    return '#E6A23C'
  }
  if (data.fileType === 'movie') return '#67C23A'
  if (data.fileType === 'episode') return '#909399'
  return '#C0C4CC'
}

function nodeClass(data) {
  if (hasIssueLevel(data, 'error')) return 'node-error'
  if (hasIssueLevel(data, 'warning')) return 'node-warning'
  return ''
}

function hasIssueLevel(data, severity) {
  return data.issues && data.issues.some(i => i.severity === severity)
}

function severityTagType(severity) {
  if (severity === 'error') return 'danger'
  if (severity === 'warning') return 'warning'
  return 'info'
}

function severityLabel(severity) {
  if (severity === 'error') return '严重'
  if (severity === 'warning') return '警告'
  return '建议'
}
</script>

<style scoped>
.emby-library-inspect {
  padding: 16px;
}

.toolbar-card {
  margin-bottom: 16px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
}

/* ── 统计卡片 ── */
.stat-cards {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  flex: 1;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.stat-number {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.stat-error .stat-number { color: #F56C6C; }
.stat-warning .stat-number { color: #E6A23C; }
.stat-info .stat-number { color: #909399; }

/* ── 主体布局 ── */
.main-body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

/* ── 筛选面板 ── */
.filter-panel {
  width: 220px;
  flex-shrink: 0;
  position: sticky;
  top: 16px;
}

.filter-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.filter-section {
  margin-bottom: 16px;
}

.filter-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}

.filter-item {
  margin-bottom: 4px;
}

.filter-item .el-checkbox {
  display: flex;
  align-items: center;
}

.cat-badge {
  margin-left: 6px;
}

/* ── 文件树 ── */
.tree-panel {
  flex: 1;
  min-width: 0;
  overflow: auto;
}

.inspect-tree {
  font-size: 14px;
}

.inspect-tree :deep(.el-tree-node__content) {
  height: auto;
  min-height: 32px;
  padding: 4px 0;
}

.tree-node {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  gap: 6px;
  padding: 2px 0;
}

.node-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.node-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 1;
  min-width: 0;
}

.node-name.has-error {
  color: #F56C6C;
  font-weight: 600;
}

.node-error {
  background-color: #FEF0F0;
  border-radius: 4px;
  padding: 2px 6px;
}

.node-warning {
  background-color: #FDF6EC;
  border-radius: 4px;
  padding: 2px 6px;
}

.node-tags {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.issue-tag {
  font-size: 11px;
  height: 20px;
  line-height: 18px;
  padding: 0 6px;
}

.node-badges {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
  margin-left: 4px;
}

.tree-badge {
  margin-right: 2px;
}

.tree-badge :deep(.el-badge__content) {
  font-size: 10px;
  height: 16px;
  line-height: 16px;
  padding: 0 5px;
}

.detail-icon {
  color: #909399;
  cursor: pointer;
  flex-shrink: 0;
  margin-left: 4px;
}

.detail-icon:hover {
  color: #409EFF;
}

/* ── 问题详情弹窗 ── */
.issue-detail-popover {
  max-height: 300px;
  overflow-y: auto;
}

.issue-detail-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 10px;
  word-break: break-all;
}

.issue-detail-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
  line-height: 1.5;
}

.issue-detail-tag {
  flex-shrink: 0;
  font-size: 11px;
}

.issue-detail-msg {
  font-size: 13px;
  color: #606266;
  word-break: break-all;
}

/* ── 欢迎卡片 ── */
.welcome-card {
  margin-top: 16px;
}
</style>
