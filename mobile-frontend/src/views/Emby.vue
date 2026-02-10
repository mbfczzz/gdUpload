<template>
  <div class="emby-page">
    <van-nav-bar title="Emby管理" fixed placeholder />

    <div class="content">
      <!-- 搜索栏 -->
      <van-search
        v-model="searchKeyword"
        placeholder="搜索媒体名称"
        @search="onSearch"
      />

      <!-- 媒体列表 -->
      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list
          v-model:loading="loading"
          :finished="finished"
          finished-text="没有更多了"
          @load="onLoad"
        >
          <van-card
            v-for="item in mediaList"
            :key="item.id"
            :thumb="item.imageUrl"
            class="media-card"
          >
            <template #title>
              <div class="media-title">{{ item.name }}</div>
            </template>

            <template #desc>
              <div class="media-info">
                <van-tag v-if="item.type" type="primary">{{ item.type }}</van-tag>
                <span class="year">{{ item.year }}</span>
              </div>
            </template>

            <template #tags>
              <van-tag v-if="item.downloadStatus === 1" type="success">已下载</van-tag>
              <van-tag v-else type="default">未下载</van-tag>
            </template>

            <template #footer>
              <van-button
                v-if="item.downloadStatus !== 1"
                size="small"
                type="primary"
                icon="down"
                @click="handleDownload(item)"
              >
                下载
              </van-button>
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
import { showToast, showConfirmDialog } from 'vant'
import axios from 'axios'

const active = ref(3)
const searchKeyword = ref('')
const refreshing = ref(false)
const loading = ref(false)
const finished = ref(false)
const mediaList = ref([])

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

const loadMediaList = async () => {
  try {
    const { data } = await axios.get('/api/emby/items', {
      params: {
        current: pagination.current,
        size: pagination.size,
        keyword: searchKeyword.value
      }
    })

    if (data.code === 200) {
      if (pagination.current === 1) {
        mediaList.value = data.data.records
      } else {
        mediaList.value.push(...data.data.records)
      }

      pagination.total = data.data.total
      loading.value = false
      refreshing.value = false

      if (mediaList.value.length >= pagination.total) {
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
  loadMediaList()
}

const onRefresh = () => {
  pagination.current = 1
  finished.value = false
  loadMediaList()
}

const onSearch = () => {
  pagination.current = 1
  finished.value = false
  mediaList.value = []
  loadMediaList()
}

const handleDownload = async (item) => {
  try {
    await showConfirmDialog({
      title: '确认下载',
      message: `确定要下载 ${item.name} 吗？`
    })

    const { data } = await axios.post('/api/emby/download', {
      itemIds: [item.id]
    })

    if (data.code === 200) {
      showToast('下载任务已创建')
      onRefresh()
    }
  } catch (error) {
    if (error !== 'cancel') {
      showToast('下载失败')
    }
  }
}

loadMediaList()
</script>

<style scoped>
.emby-page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 50px;
}

.content {
  padding-bottom: 16px;
}

.media-card {
  margin: 12px 16px;
  border-radius: 12px;
  overflow: hidden;
}

.media-title {
  font-size: 16px;
  font-weight: 600;
}

.media-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.year {
  font-size: 12px;
  color: #86868b;
}
</style>
