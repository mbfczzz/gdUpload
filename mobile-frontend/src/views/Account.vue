<template>
  <div class="account-page">
    <van-nav-bar title="账号管理" fixed placeholder />

    <div class="content">
      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list
          v-model:loading="loading"
          :finished="finished"
          finished-text="没有更多了"
          @load="onLoad"
        >
          <van-card
            v-for="account in accountList"
            :key="account.id"
            class="account-card"
          >
            <template #title>
              <div class="account-title">
                {{ account.accountName }}
                <van-tag :type="account.status === 1 ? 'success' : 'danger'">
                  {{ account.status === 1 ? '可用' : '禁用' }}
                </van-tag>
              </div>
            </template>

            <template #desc>
              <div class="account-info">
                <div class="info-item">
                  <span class="label">配置名:</span>
                  <span class="value">{{ account.rcloneConfigName }}</span>
                </div>
                <div class="info-item">
                  <span class="label">24h配额:</span>
                  <span class="value">{{ formatSize(account.dailyQuota) }}</span>
                </div>
                <div class="info-item">
                  <span class="label">已用:</span>
                  <span class="value">{{ formatSize(account.usedQuota) }}</span>
                </div>
              </div>
            </template>

            <template #footer>
              <van-progress
                :percentage="getQuotaPercentage(account)"
                :show-pivot="false"
                :color="getQuotaColor(account)"
              />
            </template>
          </van-card>
        </van-list>
      </van-pull-refresh>
    </div>

    <!-- 底部导航 -->
    <van-tabbar v-model="active" fixed placeholder>
      <van-tabbar-item icon="home-o" to="/home">首页</van-tabbar-item>
      <van-tabbar-item icon="orders-o" to="/task">任务</van-tabbar-item>
      <van-tabbar-item icon="manager-o" to="/account">账号</van-tabbar-item>
      <van-tabbar-item icon="video-o" to="/emby">Emby</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { showToast } from 'vant'
import axios from 'axios'

const active = ref(2)
const refreshing = ref(false)
const loading = ref(false)
const finished = ref(false)
const accountList = ref([])

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

const loadAccountList = async () => {
  try {
    const { data } = await axios.get('/api/account/page', {
      params: {
        current: pagination.current,
        size: pagination.size
      }
    })

    if (data.code === 200) {
      if (pagination.current === 1) {
        accountList.value = data.data.records
      } else {
        accountList.value.push(...data.data.records)
      }

      pagination.total = data.data.total
      loading.value = false
      refreshing.value = false

      if (accountList.value.length >= pagination.total) {
        finished.value = true
      }
    }
  } catch (error) {
    loading.value = false
    refreshing.value = false
    showToast('加载失败')
  }
}

const onLoad = () => {
  pagination.current++
  loadAccountList()
}

const onRefresh = () => {
  pagination.current = 1
  finished.value = false
  loadAccountList()
}

const getQuotaPercentage = (account) => {
  if (!account.dailyQuota || account.dailyQuota === 0) return 0
  return Math.min(100, Math.round((account.usedQuota / account.dailyQuota) * 100))
}

const getQuotaColor = (account) => {
  const percentage = getQuotaPercentage(account)
  if (percentage >= 90) return '#ff3b30'
  if (percentage >= 70) return '#ff9500'
  return '#34c759'
}

const formatSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

loadAccountList()
</script>

<style scoped>
.account-page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 50px;
}

.content {
  padding: 16px;
}

.account-card {
  margin-bottom: 12px;
  border-radius: 12px;
  overflow: hidden;
}

.account-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.account-info {
  margin-top: 8px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  margin-bottom: 4px;
}

.label {
  color: #86868b;
}

.value {
  color: #1d1d1f;
  font-weight: 500;
}
</style>
