<template>
  <div class="emby-container">
    <el-card class="header-card">
      <div class="header-content">
        <div class="header-left">
          <h2>Emby 媒体库管理</h2>
          <el-tag v-if="serverInfo" type="success" size="large">
            <el-icon><CircleCheck /></el-icon>
            已连接
          </el-tag>
        </div>
        <div class="header-actions">
          <el-button type="primary" @click="testConnection" :loading="testing">
            <el-icon><Connection /></el-icon>
            测试连接
          </el-button>
          <el-button type="success" @click="syncAllData" :loading="syncing">
            <el-icon><Refresh /></el-icon>
            同步所有数据
          </el-button>
          <el-button type="warning" @click="clearCache" :loading="clearing">
            <el-icon><Delete /></el-icon>
            清空缓存
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 服务器信息 -->
    <el-card v-if="serverInfo" class="info-card">
      <template #header>
        <div class="card-header">
          <span>服务器信息</span>
          <el-tag type="info">{{ serverInfo.version }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="服务器名称">
          <el-text tag="b">{{ serverInfo.serverName }}</el-text>
        </el-descriptions-item>
        <el-descriptions-item label="版本">{{ serverInfo.version }}</el-descriptions-item>
        <el-descriptions-item label="操作系统">{{ serverInfo.operatingSystem }}</el-descriptions-item>
        <el-descriptions-item label="本地地址">
          <el-link :href="`http://${serverInfo.localAddress}`" target="_blank" type="primary">
            {{ serverInfo.localAddress }}
          </el-link>
        </el-descriptions-item>
        <el-descriptions-item label="外网地址">
          <el-link v-if="serverInfo.wanAddress" :href="`http://${serverInfo.wanAddress}`" target="_blank" type="primary">
            {{ serverInfo.wanAddress }}
          </el-link>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="服务器ID">
          <el-text type="info" size="small">{{ serverInfo.id }}</el-text>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 配置提示 -->
    <el-alert
      v-if="!serverInfo && !testing"
      title="请先配置 Emby 服务器"
      type="warning"
      :closable="false"
      show-icon
      class="config-alert"
    >
      <template #default>
        <div>
          <p>请在后端配置文件 <code>application.yml</code> 中配置 Emby 服务器信息：</p>
          <pre class="config-example">
app:
  emby:
    server-url: http://your-emby-server:8096
    api-key: your-api-key-here
    enabled: true</pre>
          <p>配置完成后，点击"测试连接"按钮验证配置。</p>
        </div>
      </template>
    </el-alert>

    <!-- 数据同步提示 -->
    <el-alert
      v-if="serverInfo && !hasCache && !syncing"
      title="⚠️ 首次使用需要同步数据"
      type="error"
      :closable="false"
      show-icon
      class="sync-alert"
    >
      <template #default>
        <div>
          <p><strong>数据库中暂无数据，请先点击"同步所有数据"按钮！</strong></p>
          <p>同步过程会从 Emby 服务器获取所有媒体库、媒体项、类型、标签、工作室等数据并保存到数据库。</p>
          <p>根据媒体库大小，同步可能需要几分钟到几十分钟，请耐心等待。</p>
          <el-button type="danger" size="large" @click="syncAllData" :loading="syncing" style="margin-top: 10px;">
            <el-icon><Refresh /></el-icon>
            立即同步所有数据
          </el-button>
        </div>
      </template>
    </el-alert>

    <!-- 媒体库列表 -->
    <el-card class="library-card">
      <template #header>
        <div class="card-header">
          <span>媒体库列表 ({{ libraries.length }})</span>
          <el-button type="primary" size="small" @click="loadLibraries">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>

      <el-empty v-if="!loadingLibraries && libraries.length === 0" description="暂无媒体库数据">
        <el-button type="primary" @click="loadLibraries">刷新数据</el-button>
      </el-empty>

      <el-table v-else :data="libraries" v-loading="loadingLibraries" border stripe>
        <el-table-column type="index" label="#" width="60" align="center" />
        <el-table-column prop="name" label="名称" min-width="150">
          <template #default="{ row }">
            <div class="library-name">
              <el-icon v-if="row.collectionType === 'movies'" color="#34c759"><Film /></el-icon>
              <el-icon v-else-if="row.collectionType === 'tvshows'" color="#007aff"><Monitor /></el-icon>
              <el-icon v-else-if="row.collectionType === 'music'" color="#ff9500"><Headset /></el-icon>
              <el-icon v-else color="#86868b"><Folder /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="collectionType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.collectionType === 'movies'" type="success">电影</el-tag>
            <el-tag v-else-if="row.collectionType === 'tvshows'" type="primary">电视剧</el-tag>
            <el-tag v-else-if="row.collectionType === 'music'" type="warning">音乐</el-tag>
            <el-tag v-else>{{ row.collectionType || '其他' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="itemCount" label="媒体项数量" width="120" align="center">
          <template #default="{ row }">
            <el-text v-if="row.itemCount !== undefined && row.itemCount !== null" type="primary" tag="b">
              {{ row.itemCount }}
            </el-text>
            <el-text v-else type="info" size="small">点击查看</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="locations" label="路径" min-width="300">
          <template #default="{ row }">
            <div v-if="row.locations && row.locations.length > 0">
              <div v-for="(path, index) in row.locations" :key="index" class="path-item">
                <el-icon><FolderOpened /></el-icon>
                {{ path }}
              </div>
            </div>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewLibraryItems(row)">
              <el-icon><View /></el-icon>
              查看媒体项
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 分类信息 -->
    <el-row :gutter="20" class="category-row">
      <el-col :span="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>类型 ({{ genres.length }})</span>
              <el-button type="text" size="small" @click="loadGenres">刷新</el-button>
            </div>
          </template>
          <div class="category-list" v-loading="loadingGenres">
            <el-empty v-if="!loadingGenres && genres.length === 0" description="暂无数据" :image-size="60" />
            <el-tag v-else v-for="genre in genres" :key="genre.id" class="category-tag">
              {{ genre.name }}
              <span v-if="genre.itemCount" class="count">({{ genre.itemCount }})</span>
            </el-tag>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>标签 ({{ tags.length }})</span>
              <el-button type="text" size="small" @click="loadTags">刷新</el-button>
            </div>
          </template>
          <div class="category-list" v-loading="loadingTags">
            <el-empty v-if="!loadingTags && tags.length === 0" description="暂无数据" :image-size="60" />
            <el-tag v-else v-for="tag in tags" :key="tag.id" type="success" class="category-tag">
              {{ tag.name }}
              <span v-if="tag.itemCount" class="count">({{ tag.itemCount }})</span>
            </el-tag>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>工作室 ({{ studios.length }})</span>
              <el-button type="text" size="small" @click="loadStudios">刷新</el-button>
            </div>
          </template>
          <div class="category-list" v-loading="loadingStudios">
            <el-empty v-if="!loadingStudios && studios.length === 0" description="暂无数据" :image-size="60" />
            <el-tag v-else v-for="studio in studios" :key="studio.id" type="warning" class="category-tag">
              {{ studio.name }}
              <span v-if="studio.itemCount" class="count">({{ studio.itemCount }})</span>
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 媒体项对话框 -->
    <el-dialog
      v-model="itemsDialogVisible"
      :title="`${currentLibrary?.name} - 媒体项列表`"
      width="90%"
      top="5vh"
    >
      <div class="items-dialog-content">
        <div class="dialog-toolbar">
          <div style="display: flex; align-items: center; gap: 12px;">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索媒体项..."
              style="width: 300px"
              clearable
              @clear="loadLibraryItems"
            >
              <template #append>
                <el-button @click="searchInLibrary">
                  <el-icon><Search /></el-icon>
                </el-button>
              </template>
            </el-input>
            <el-select
              v-model="transferStatusFilter"
              placeholder="转存状态"
              style="width: 140px"
              clearable
              @change="applyTransferFilter"
            >
              <el-option label="全部" value="" />
              <el-option label="转存成功" value="success" />
              <el-option label="转存失败" value="failed" />
              <el-option label="未转存" value="none" />
            </el-select>
            <el-select
              v-model="downloadStatusFilter"
              placeholder="下载状态"
              style="width: 140px"
              clearable
              @change="applyDownloadFilter"
            >
              <el-option label="全部" value="" />
              <el-option label="下载成功" value="success" />
              <el-option label="下载失败" value="failed" />
              <el-option label="未下载" value="none" />
            </el-select>
          </div>
          <div style="display: flex; align-items: center; gap: 12px;">
            <el-button
              type="primary"
              @click="handleBatchDownload"
              :loading="batchDownloading"
              :disabled="libraryItems.length === 0 || transferStatusFilter === 'success'"
            >
              <el-icon><Download /></el-icon>
              批量搜索下载
            </el-button>
            <el-button
              type="warning"
              @click="handleBatchDirectDownload"
              :loading="batchDirectDownloading"
              :disabled="libraryItems.length === 0"
            >
              <el-icon><Download /></el-icon>
              批量直接下载
            </el-button>
            <span class="total-count">共 {{ totalCount }} 项 (当前页 {{ libraryItems.length }} 项)</span>
          </div>
        </div>

        <el-empty v-if="!loadingItems && libraryItems.length === 0" description="暂无媒体项数据">
          <el-button type="primary" @click="loadLibraryItems">刷新数据</el-button>
        </el-empty>

        <el-table
          v-else
          :data="libraryItems"
          v-loading="loadingItems"
          border
          stripe
          max-height="600"
          :expand-row-keys="expandedRows"
          row-key="id"
          @expand-change="handleExpandChange"
        >
          <el-table-column type="expand" width="50">
            <template #default="{ row }">
              <div v-if="row.type === 'Series'" class="episode-list">
                <div v-if="loadingEpisodes[row.id]" class="loading-episodes">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  <span>加载剧集中...</span>
                </div>
                <div v-else-if="episodesMap[row.id] && episodesMap[row.id].length > 0" class="episodes-container">
                  <div class="episodes-header">
                    <span class="episodes-title">剧集列表 (共 {{ episodesMap[row.id].length }} 集)</span>
                  </div>
                  <el-table :data="episodesMap[row.id]" border size="small">
                    <el-table-column type="index" label="#" width="50" align="center" />
                    <el-table-column prop="name" label="集数" min-width="200">
                      <template #default="{ row: episode }">
                        <div class="episode-name">
                          <el-icon color="#007aff"><VideoPlay /></el-icon>
                          <span>{{ episode.name }}</span>
                          <el-tag v-if="downloadStatusMap[episode.id] === 'success'" type="success" size="small" effect="plain" style="margin-left: 8px;">
                            <el-icon><Download /></el-icon>
                            已下载
                          </el-tag>
                          <el-tag v-else-if="downloadStatusMap[episode.id] === 'failed'" type="danger" size="small" effect="plain" style="margin-left: 8px;">
                            <el-icon><Close /></el-icon>
                            下载失败
                          </el-tag>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column prop="indexNumber" label="第几集" width="80" align="center">
                      <template #default="{ row: episode }">
                        <el-tag size="small" type="info">E{{ episode.indexNumber }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="parentIndexNumber" label="第几季" width="80" align="center">
                      <template #default="{ row: episode }">
                        <el-tag size="small" type="primary">S{{ episode.parentIndexNumber }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="runTimeTicks" label="时长" width="100" align="center">
                      <template #default="{ row: episode }">
                        <span v-if="episode.runTimeTicks">{{ formatDuration(episode.runTimeTicks) }}</span>
                        <span v-else class="text-muted">-</span>
                      </template>
                    </el-table-column>
                    <el-table-column prop="overview" label="简介" min-width="300" show-overflow-tooltip>
                      <template #default="{ row: episode }">
                        <span v-if="episode.overview">{{ episode.overview }}</span>
                        <span v-else class="text-muted">暂无简介</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="120" align="center">
                      <template #default="{ row: episode }">
                        <el-button
                          type="warning"
                          link
                          size="small"
                          @click="handleDirectDownload(episode)"
                        >
                          <el-icon><Download /></el-icon>
                          下载
                        </el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
                <el-empty v-else description="暂无剧集数据" :image-size="60" />
              </div>
            </template>
          </el-table-column>
          <el-table-column type="index" label="#" width="60" align="center" :index="(index) => (currentPage - 1) * pageSize + index + 1" />
          <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="item-name">
                <el-icon v-if="row.type === 'Movie'" color="#34c759"><Film /></el-icon>
                <el-icon v-else-if="row.type === 'Series'" color="#007aff"><Monitor /></el-icon>
                <el-icon v-else-if="row.type === 'Episode'" color="#007aff"><VideoPlay /></el-icon>
                <el-icon v-else-if="row.type === 'Audio'" color="#ff9500"><Headset /></el-icon>
                <el-icon v-else color="#86868b"><Document /></el-icon>
                <span>{{ row.name }}</span>
                <el-tag v-if="transferStatusMap[row.id] === 'success'" type="success" size="small" effect="dark" style="margin-left: 8px;">
                  <el-icon><Check /></el-icon>
                  已转存
                </el-tag>
                <el-tag v-else-if="transferStatusMap[row.id] === 'failed'" type="danger" size="small" effect="dark" style="margin-left: 8px;">
                  <el-icon><Close /></el-icon>
                  失败
                </el-tag>
                <el-tag v-if="downloadStatusMap[row.id] === 'success'" type="success" size="small" effect="plain" style="margin-left: 8px;">
                  <el-icon><Download /></el-icon>
                  已下载
                </el-tag>
                <el-tag v-else-if="downloadStatusMap[row.id] === 'failed'" type="danger" size="small" effect="plain" style="margin-left: 8px;">
                  <el-icon><Close /></el-icon>
                  下载失败
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="type" label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small">{{ row.type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="productionYear" label="年份" width="80" align="center">
            <template #default="{ row }">
              {{ row.productionYear || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="communityRating" label="评分" width="100" align="center">
            <template #default="{ row }">
              <el-rate
                v-if="row.communityRating"
                :model-value="row.communityRating / 2"
                disabled
                show-score
                :score-template="row.communityRating.toFixed(1)"
                size="small"
              />
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="genres" label="类型" width="180">
            <template #default="{ row }">
              <el-tag
                v-for="(genre, index) in row.genres?.slice(0, 2)"
                :key="index"
                size="small"
                class="genre-tag"
              >
                {{ genre }}
              </el-tag>
              <el-tag v-if="row.genres?.length > 2" size="small" type="info">
                +{{ row.genres.length - 2 }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="size" label="大小" width="120" align="right">
            <template #default="{ row }">
              <el-text v-if="row.size" type="primary">{{ formatFileSize(row.size) }}</el-text>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="path" label="路径" min-width="300" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tooltip v-if="row.path" :content="row.path" placement="top">
                <el-text type="info" size="small">{{ row.path }}</el-text>
              </el-tooltip>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="转存状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="transferStatusMap[row.id] === 'success'" type="success" size="small">
                已转存
              </el-tag>
              <el-tag v-else-if="transferStatusMap[row.id] === 'failed'" type="danger" size="small">
                转存失败
              </el-tag>
              <el-tag v-else type="info" size="small">
                未转存
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="下载状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="downloadStatusMap[row.id] === 'success'" type="success" size="small">
                已下载
              </el-tag>
              <el-tag v-else-if="downloadStatusMap[row.id] === 'failed'" type="danger" size="small">
                下载失败
              </el-tag>
              <el-tag v-else type="info" size="small">
                未下载
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="350" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="viewItemDetail(row)">
                <el-icon><View /></el-icon>
                详情
              </el-button>
              <el-button
                v-if="row.type === 'Movie' || row.type === 'Series'"
                type="success"
                link
                size="small"
                @click="handleQuickDownload(row)"
              >
                <el-icon><Download /></el-icon>
                搜索下载
              </el-button>
              <el-button
                v-if="row.type === 'Movie' || row.type === 'Series'"
                type="warning"
                link
                size="small"
                @click="handleDirectDownload(row)"
              >
                <el-icon><Download /></el-icon>
                直接下载
              </el-button>
              <el-button
                v-if="downloadStatusMap[row.id] !== 'success'"
                type="success"
                link
                size="small"
                @click="handleMarkDownloadSuccess(row)"
              >
                <el-icon><Check /></el-icon>
                标记已下载
              </el-button>
              <el-button
                v-if="(row.type === 'Movie' || row.type === 'Series') && transferStatusMap[row.id] && transferStatusMap[row.id] !== 'none'"
                type="info"
                link
                size="small"
                @click="viewTransferHistory(row)"
              >
                <el-icon><Document /></el-icon>
                转存记录
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
            :total="totalCount"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </div>
    </el-dialog>

    <!-- 快速下载对话框 -->
    <el-dialog
      v-model="downloadDialogVisible"
      :title="downloadItem?.type === 'Series' ? '搜索并下载电视剧' : '搜索并下载电影'"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-if="downloadItem" class="download-dialog-content">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="电影名称">
            <el-text tag="b">{{ downloadItem.name }}</el-text>
          </el-descriptions-item>
          <el-descriptions-item label="原始名称" v-if="downloadItem.originalTitle">
            {{ downloadItem.originalTitle }}
          </el-descriptions-item>
          <el-descriptions-item label="年份" v-if="downloadItem.productionYear">
            {{ downloadItem.productionYear }}
          </el-descriptions-item>
          <el-descriptions-item label="评分" v-if="downloadItem.communityRating">
            <el-rate
              :model-value="downloadItem.communityRating / 2"
              disabled
              show-score
              :score-template="downloadItem.communityRating.toFixed(1)"
              size="small"
            />
          </el-descriptions-item>
        </el-descriptions>

        <el-divider />

        <el-form label-width="100px">
          <el-form-item label="搜索关键词">
            <el-input
              v-model="searchKeywordInput"
              placeholder="自动填充电影名称，可修改"
              clearable
              @keyup.enter="handleSearchByKeyword"
            >
              <template #append>
                <el-button @click="handleSearchByKeyword" :loading="searchingKeyword">
                  <el-icon><Search /></el-icon>
                  搜索
                </el-button>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="智能筛选">
            <el-checkbox v-model="validateLinks" label="验证链接有效性" />
            <el-checkbox v-model="useAI" label="使用AI智能推荐" style="margin-left: 16px;" />
          </el-form-item>

          <el-alert
            title="云盘配置说明"
            type="info"
            :closable="false"
            style="margin-bottom: 16px;"
          >
            <div style="font-size: 13px; line-height: 1.6;">
              <p>系统会根据搜索结果的 <code>cloudType</code> 自动匹配对应的云盘配置。</p>
              <p style="margin-top: 4px;">
                已配置 <strong>{{ cloudConfigs.length }}</strong> 个云盘：
                <el-tag
                  v-for="(cloud, index) in cloudConfigs"
                  :key="index"
                  size="small"
                  style="margin-left: 4px;"
                >
                  {{ cloud.cloudType }}
                </el-tag>
              </p>
              <p style="margin-top: 4px; color: #909399;">
                如需添加或修改云盘配置，请前往"智能搜索配置"页面
              </p>
            </div>
          </el-alert>
        </el-form>

        <!-- 搜索结果列表 -->
        <div v-if="searchResults && searchResults.length > 0" class="search-results">
          <el-alert
            :title="`找到 ${searchResults.length} 个结果`"
            type="success"
            :closable="false"
            show-icon
            style="margin-bottom: 16px;"
          >
            <template #default>
              <div v-if="validatingLinks">
                <el-icon class="is-loading"><Loading /></el-icon>
                正在验证链接有效性...
              </div>
              <div v-else-if="aiSelecting">
                <el-icon class="is-loading"><Loading /></el-icon>
                AI正在智能筛选最佳资源...
              </div>
              <div v-else>
                已完成智能筛选和排序
              </div>
            </template>
          </el-alert>

          <el-table
            :data="searchResults"
            style="width: 100%"
            max-height="400"
            highlight-current-row
            @current-change="handleSelectResult"
          >
            <el-table-column type="index" label="#" width="50" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tooltip :content="row.validationMessage || '未验证'" placement="top">
                  <div>
                    <el-icon v-if="row.isValid === true" color="#67c23a" :size="20">
                      <CircleCheck />
                    </el-icon>
                    <el-icon v-else-if="row.isValid === false" color="#f56c6c" :size="20">
                      <CircleClose />
                    </el-icon>
                    <el-icon v-else color="#909399" :size="20">
                      <QuestionFilled />
                    </el-icon>
                  </div>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="匹配度" width="100" sortable :sort-method="(a, b) => b.matchScore - a.matchScore">
              <template #default="{ row }">
                <el-progress
                  :percentage="Math.min(Math.round(row.matchScore), 100)"
                  :color="row.matchScore >= 80 ? '#67c23a' : row.matchScore >= 60 ? '#e6a23c' : '#909399'"
                  :stroke-width="8"
                />
                <el-tooltip :content="row.matchReasons?.join('\n')" placement="top">
                  <el-text size="small" type="info" style="cursor: help;">
                    {{ Math.round(row.matchScore) }}分
                  </el-text>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="250" show-overflow-tooltip>
              <template #default="{ row }">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <el-image
                    v-if="row.image"
                    :src="row.image"
                    style="width: 40px; height: 40px; border-radius: 4px;"
                    fit="cover"
                  />
                  <div>
                    <div>{{ row.title }}</div>
                    <div style="margin-top: 4px;">
                      <el-tag v-if="row.aiRecommended" size="small" type="danger" effect="dark">
                        <el-icon><Star /></el-icon>
                        AI推荐
                      </el-tag>
                      <el-tag v-else-if="row.matchScore >= 80" size="small" type="success" effect="dark">
                        推荐
                      </el-tag>
                      <el-tag v-if="row.hasFiles" size="small" type="info" style="margin-left: 4px;">
                        {{ row.fileCount }}个文件
                      </el-tag>
                    </div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="size" label="大小" width="120">
              <template #default="{ row }">
                <el-text type="primary">{{ row.size || '-' }}</el-text>
              </template>
            </el-table-column>
            <el-table-column prop="resolution" label="分辨率" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.resolution" size="small" type="success">
                  {{ row.resolution }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="tags" label="标签" width="150">
              <template #default="{ row }">
                <el-tag
                  v-for="(tag, index) in row.tags.slice(0, 2)"
                  :key="index"
                  size="small"
                  style="margin-right: 4px;"
                >
                  {{ tag }}
                </el-tag>
                <el-tag v-if="row.tags.length > 2" size="small" type="info">
                  +{{ row.tags.length - 2 }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="channelName" label="来源" width="100">
              <template #default="{ row }">
                <el-text size="small" type="info">{{ row.channelName || '-' }}</el-text>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  size="small"
                  @click="handleTransferToAlipan(row)"
                  :loading="transferring && selectedResult?.id === row.id"
                >
                  <el-icon><Download /></el-icon>
                  转存
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 空状态 -->
        <el-empty
          v-if="searchResults && searchResults.length === 0 && !searchingKeyword"
          description="未找到搜索结果"
          :image-size="100"
        />

        <!-- 选中的结果详情 -->
        <div v-if="selectedResult" class="selected-result">
          <el-divider content-position="left">选中的资源</el-divider>
          <el-card class="result-preview" shadow="never">
            <pre class="json-preview">{{ formatJson(selectedResult) }}</pre>
          </el-card>
        </div>
      </div>
    </el-dialog>

    <!-- 媒体项详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="currentItem?.name"
      width="70%"
    >
      <el-descriptions v-if="currentItem" :column="2" border>
        <el-descriptions-item label="ID">
          <el-text type="info" size="small">{{ currentItem.id }}</el-text>
        </el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag>{{ currentItem.type }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="原始名称">
          {{ currentItem.originalTitle || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="年份">
          <el-tag v-if="currentItem.productionYear" type="info">
            {{ currentItem.productionYear }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="评分">
          <el-rate
            v-if="currentItem.communityRating"
            :model-value="currentItem.communityRating / 2"
            disabled
            show-score
            :score-template="currentItem.communityRating.toFixed(1)"
          />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="分级">
          <el-tag v-if="currentItem.officialRating" type="warning">
            {{ currentItem.officialRating }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="文件大小">
          <el-text v-if="currentItem.size" type="primary" tag="b">
            {{ formatFileSize(currentItem.size) }}
          </el-text>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="播放次数">
          <el-tag type="success">{{ currentItem.playCount || 0 }} 次</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="类型" :span="2">
          <div v-if="currentItem.genres && currentItem.genres.length > 0">
            <el-tag v-for="(genre, index) in currentItem.genres" :key="index" class="detail-tag">
              {{ genre }}
            </el-tag>
          </div>
          <span v-else class="text-muted">-</span>
        </el-descriptions-item>
        <el-descriptions-item label="标签" :span="2">
          <div v-if="currentItem.tags && currentItem.tags.length > 0">
            <el-tag v-for="(tag, index) in currentItem.tags" :key="index" type="success" class="detail-tag">
              {{ tag }}
            </el-tag>
          </div>
          <span v-else class="text-muted">-</span>
        </el-descriptions-item>
        <el-descriptions-item label="工作室" :span="2">
          <div v-if="currentItem.studios && currentItem.studios.length > 0">
            <el-tag v-for="(studio, index) in currentItem.studios" :key="index" type="warning" class="detail-tag">
              {{ studio }}
            </el-tag>
          </div>
          <span v-else class="text-muted">-</span>
        </el-descriptions-item>
        <el-descriptions-item label="演员" :span="2">
          <div v-if="currentItem.people && currentItem.people.length > 0">
            <el-tag v-for="(person, index) in currentItem.people?.slice(0, 10)" :key="index" type="info" class="detail-tag">
              {{ person }}
            </el-tag>
            <el-tag v-if="currentItem.people.length > 10" type="info">
              +{{ currentItem.people.length - 10 }}
            </el-tag>
          </div>
          <span v-else class="text-muted">-</span>
        </el-descriptions-item>
        <el-descriptions-item label="简介" :span="2">
          <el-text v-if="currentItem.overview" class="overview-text">
            {{ currentItem.overview }}
          </el-text>
          <span v-else class="text-muted">-</span>
        </el-descriptions-item>
        <el-descriptions-item label="文件路径" :span="2">
          <el-text v-if="currentItem.path" type="info" size="small">
            {{ currentItem.path }}
          </el-text>
          <span v-else class="text-muted">-</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentItem.mediaSources && currentItem.mediaSources.length > 0" label="媒体源" :span="2">
          <div v-for="(source, index) in currentItem.mediaSources" :key="index" class="media-source">
            <el-tag type="primary">{{ source.container?.toUpperCase() }}</el-tag>
            <el-text type="info" size="small">{{ formatFileSize(source.size) }}</el-text>
            <el-text type="info" size="small">{{ formatBitrate(source.bitrate) }}</el-text>
          </div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 转存历史对话框 -->
    <el-dialog
      v-model="transferHistoryDialogVisible"
      title="转存历史记录"
      width="800px"
    >
      <el-empty v-if="!currentTransferHistory || currentTransferHistory.length === 0" description="暂无转存记录" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="(record, index) in currentTransferHistory"
          :key="index"
          :timestamp="record.createTime"
          placement="top"
          :type="record.transferStatus === 'success' ? 'success' : 'danger'"
          :icon="record.transferStatus === 'success' ? 'CircleCheck' : 'CircleClose'"
        >
          <el-card shadow="hover">
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span style="font-weight: bold;">{{ record.resourceTitle }}</span>
                <el-tag :type="record.transferStatus === 'success' ? 'success' : 'danger'" size="small">
                  {{ record.transferStatus === 'success' ? '转存成功' : '转存失败' }}
                </el-tag>
              </div>
            </template>
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="匹配分数">
                <el-progress
                  v-if="record.matchScore"
                  :percentage="Math.min(Math.round(record.matchScore), 100)"
                  :color="record.matchScore >= 80 ? '#67c23a' : record.matchScore >= 60 ? '#e6a23c' : '#909399'"
                  :stroke-width="6"
                  :show-text="false"
                  style="width: 100px; display: inline-block; margin-right: 8px;"
                />
                <span>{{ record.matchScore ? Math.round(record.matchScore) + '分' : '-' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="云盘">
                {{ record.cloudName || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="云盘类型">
                <el-tag size="small">{{ record.cloudType || '-' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="转存时间">
                {{ record.createTime }}
              </el-descriptions-item>
              <el-descriptions-item label="结果消息" :span="2">
                <el-text :type="record.transferStatus === 'success' ? 'success' : 'danger'">
                  {{ record.transferMessage || '-' }}
                </el-text>
              </el-descriptions-item>
              <el-descriptions-item label="资源链接" :span="2">
                <el-link :href="record.resourceUrl" target="_blank" type="primary" :underline="false" style="font-size: 12px;">
                  {{ record.resourceUrl }}
                </el-link>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import {
  testEmbyConnection,
  getEmbyServerInfo,
  getAllLibraries,
  getLibraryItemsPaged,
  getItemDetail,
  getSeriesEpisodes,
  getAllGenres,
  getAllTags,
  getAllStudios,
  searchItems,
  syncAllLibraries,
  getCacheStatus,
  getDownloadUrls,
  downloadToServer,
  batchDownloadToServer
} from '@/api/emby'
import { searchSubscribe, searchByKeyword, transferToAlipan, batchValidateLinks, aiSelectBestResource } from '@/api/subscribe'
import { createBatchTask, startTask } from '@/api/subscribeBatch'
import { getFullConfig } from '@/api/smartSearchConfig'
import { saveTransferHistory, batchCheckTransferStatus, getHistoryByEmbyItemId } from '@/api/transferHistory'
import { batchCheckDownloadStatus, markDownloadStatus } from '@/api/downloadHistory'
import { smartSearch115, transfer115 } from '@/api/resource115'

// 状态
const testing = ref(false)
const syncing = ref(false)
const clearing = ref(false)
const loadingLibraries = ref(false)
const loadingItems = ref(false)
const loadingGenres = ref(false)
const loadingTags = ref(false)
const loadingStudios = ref(false)
const batchDownloading = ref(false) // 批量下载状态
const batchDirectDownloading = ref(false) // 批量直接下载状态

// 数据
const serverInfo = ref(null)
const hasCache = ref(true) // 是否有缓存数据
const libraries = ref([])
const genres = ref([])
const tags = ref([])
const studios = ref([])
const libraryItems = ref([])
const currentLibrary = ref(null)
const currentItem = ref(null)
const searchKeyword = ref('')
const transferStatusFilter = ref('') // 转存状态筛选
const downloadStatusFilter = ref('') // 下载状态筛选

// 剧集展开相关
const expandedRows = ref([])
const episodesMap = ref({})
const loadingEpisodes = ref({})

// 分页
const currentPage = ref(1)
const pageSize = ref(50)
const totalCount = ref(0)

// 计算属性：过滤后的媒体项列表
// 已移除客户端过滤逻辑，现在由服务端处理

// 对话框
const itemsDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const downloadDialogVisible = ref(false)
const transferHistoryDialogVisible = ref(false)
const currentTransferHistory = ref([])

// 快速下载相关
const downloadItem = ref(null)
const subscribeId = ref('')
const subscribeResult = ref(null)
const searchingSubscribe = ref(false)
const searchKeywordInput = ref('')
const searchingKeyword = ref(false)
const searchResults = ref([])
const selectedResult = ref(null)
const transferring = ref(false)
const cloudConfigs = ref([]) // 云盘配置列表
const validatingLinks = ref(false)
const aiSelecting = ref(false)
const useAI = ref(true) // 是否使用AI筛选
const validateLinks = ref(true) // 是否验证链接
const transferStatusMap = ref({}) // 媒体项转存状态映射 {embyItemId: boolean}
const downloadStatusMap = ref({}) // 媒体项下载状态映射 {embyItemId: status}

// 根据cloudType获取云盘配置
const getCloudConfigByType = (cloudType) => {
  const config = cloudConfigs.value.find(c => c.cloudType === cloudType)
  if (!config) {
    console.warn(`未找到 cloudType=${cloudType} 的云盘配置`)
    return null
  }
  return config
}

// 加载智能搜索配置
const loadSmartSearchConfig = async () => {
  try {
    // 优先从数据库加载
    const res = await getFullConfig()
    if (res.data && Object.keys(res.data).length > 0) {
      // 加载云盘配置列表
      if (res.data.cloudConfigs && Array.isArray(res.data.cloudConfigs) && res.data.cloudConfigs.length > 0) {
        cloudConfigs.value = res.data.cloudConfigs
      }

      // 应用其他配置
      if (res.data.aiEnabled !== undefined) {
        useAI.value = res.data.aiEnabled
      }
      if (res.data.validateLinks !== undefined) {
        validateLinks.value = res.data.validateLinks
      }

      console.log('从数据库加载智能搜索配置:', res.data)
      console.log('云盘配置列表:', cloudConfigs.value)
      return
    }
  } catch (error) {
    console.error('从数据库加载配置失败:', error)
  }

  // 如果数据库加载失败，尝试从localStorage加载
  try {
    const saved = localStorage.getItem('smartSearchConfig')
    if (saved) {
      const config = JSON.parse(saved)

      // 加载云盘配置列表
      if (config.cloudConfigs && Array.isArray(config.cloudConfigs) && config.cloudConfigs.length > 0) {
        cloudConfigs.value = config.cloudConfigs
      } else {
        // 兼容旧配置
        cloudConfigs.value = [{
          name: '阿里云盘',
          cloudType: config.cloudType || 'channel_alipan',
          parentId: config.alipanParentId || '697f2333cd2704159fa446d8bc5077584838e3dc',
          remark: ''
        }]
      }

      // 应用其他配置
      if (config.aiEnabled !== undefined) {
        useAI.value = config.aiEnabled
      }
      if (config.validateLinks !== undefined) {
        validateLinks.value = config.validateLinks
      }

      console.log('从本地加载智能搜索配置:', config)
      console.log('云盘配置列表:', cloudConfigs.value)
    }
  } catch (error) {
    console.error('加载智能搜索配置失败:', error)
  }
}

// 测试连接
const testConnection = async () => {
  testing.value = true
  try {
    const res = await testEmbyConnection()
    if (res.data) {
      ElMessage.success('Emby连接成功')
      await loadServerInfo()
    } else {
      ElMessage.error('Emby连接失败')
    }
  } catch (error) {
    ElMessage.error('连接测试失败: ' + error.message)
  } finally {
    testing.value = false
  }
}

// 加载服务器信息
const loadServerInfo = async () => {
  try {
    const res = await getEmbyServerInfo()
    serverInfo.value = res.data
    // 加载服务器信息后，检查缓存状态
    await checkCacheStatus()
  } catch (error) {
    console.error('加载服务器信息失败:', error)
  }
}

// 检查缓存状态
const checkCacheStatus = async () => {
  try {
    const res = await getCacheStatus()
    hasCache.value = res.data.hasCache
    if (!hasCache.value) {
      ElMessage.warning({
        message: '数据库中暂无数据，请先点击"同步所有数据"按钮！',
        duration: 5000,
        showClose: true
      })
    }
  } catch (error) {
    console.error('检查缓存状态失败:', error)
  }
}

// 加载媒体库
const loadLibraries = async () => {
  loadingLibraries.value = true
  try {
    const res = await getAllLibraries()
    libraries.value = res.data
  } catch (error) {
    ElMessage.error('加载媒体库失败: ' + error.message)
  } finally {
    loadingLibraries.value = false
  }
}

// 加载类型
const loadGenres = async () => {
  loadingGenres.value = true
  try {
    const res = await getAllGenres()
    genres.value = res.data
  } catch (error) {
    ElMessage.error('加载类型失败: ' + error.message)
  } finally {
    loadingGenres.value = false
  }
}

// 加载标签
const loadTags = async () => {
  loadingTags.value = true
  try {
    const res = await getAllTags()
    tags.value = res.data
  } catch (error) {
    ElMessage.error('加载标签失败: ' + error.message)
  } finally {
    loadingTags.value = false
  }
}

// 加载工作室
const loadStudios = async () => {
  loadingStudios.value = true
  try {
    const res = await getAllStudios()
    studios.value = res.data
  } catch (error) {
    ElMessage.error('加载工作室失败: ' + error.message)
  } finally {
    loadingStudios.value = false
  }
}

// 查看媒体库的媒体项
const viewLibraryItems = async (library) => {
  currentLibrary.value = library
  currentPage.value = 1
  itemsDialogVisible.value = true
  await loadLibraryItems()
}

// 处理表格行展开
const handleExpandChange = async (row, expandedRowsData) => {
  // 如果不是电视剧，不处理
  if (row.type !== 'Series') {
    return
  }

  // 检查是否是展开操作
  const isExpanding = expandedRowsData.some(r => r.id === row.id)

  if (isExpanding) {
    // 如果已经加载过剧集，只刷新下载状态
    if (episodesMap.value[row.id]) {
      const episodeIds = episodesMap.value[row.id].map(ep => ep.id)
      if (episodeIds.length > 0) {
        try {
          const statusRes = await batchCheckDownloadStatus(episodeIds)
          if (statusRes.data) {
            Object.assign(downloadStatusMap.value, statusRes.data)
            console.log(`刷新剧集下载状态完成，共 ${episodeIds.length} 集`)
          }
        } catch (error) {
          console.error('刷新剧集下载状态失败:', error)
        }
      }
      return
    }

    // 加载剧集列表
    loadingEpisodes.value[row.id] = true
    try {
      const res = await getSeriesEpisodes(row.id)
      episodesMap.value[row.id] = res.data || []
      console.log(`加载电视剧 [${row.name}] 的剧集，共 ${res.data?.length || 0} 集`)

      // 加载剧集的下载状态
      if (res.data && res.data.length > 0) {
        const episodeIds = res.data.map(ep => ep.id)
        try {
          const statusRes = await batchCheckDownloadStatus(episodeIds)
          if (statusRes.data) {
            // 合并到 downloadStatusMap
            Object.assign(downloadStatusMap.value, statusRes.data)
            console.log(`加载剧集下载状态完成，共 ${episodeIds.length} 集`)
          }
        } catch (error) {
          console.error('加载剧集下载状态失败:', error)
        }
      }
    } catch (error) {
      console.error('加载剧集失败:', error)
      ElMessage.error('加载剧集失败')
      episodesMap.value[row.id] = []
    } finally {
      loadingEpisodes.value[row.id] = false
    }
  }
}

// 格式化时长（从ticks转换为分钟）
const formatDuration = (ticks) => {
  if (!ticks) return '-'
  const minutes = Math.round(ticks / 600000000) // 1 tick = 100 nanoseconds
  if (minutes < 60) {
    return `${minutes}分钟`
  }
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return `${hours}小时${mins}分钟`
}

// 加载媒体库的媒体项
const loadLibraryItems = async () => {
  if (!currentLibrary.value) return

  loadingItems.value = true
  try {
    const startIndex = (currentPage.value - 1) * pageSize.value

    // 传递转存状态和下载状态筛选参数到后端
    const res = await getLibraryItemsPaged(
      currentLibrary.value.id,
      startIndex,
      pageSize.value,
      transferStatusFilter.value || null,
      downloadStatusFilter.value || null
    )

    libraryItems.value = res.data.items
    totalCount.value = res.data.totalCount

    // 更新媒体库列表中的数量（如果还没有的话）
    if (currentLibrary.value.itemCount === undefined || currentLibrary.value.itemCount === null) {
      const libraryIndex = libraries.value.findIndex(lib => lib.id === currentLibrary.value.id)
      if (libraryIndex !== -1) {
        libraries.value[libraryIndex].itemCount = res.data.totalCount
        currentLibrary.value.itemCount = res.data.totalCount
      }
    }

    // 批量检查转存状态
    await loadTransferStatus()
    // 批量检查下载状态
    await loadDownloadStatus()
  } catch (error) {
    ElMessage.error('加载媒体项失败: ' + error.message)
  } finally {
    loadingItems.value = false
  }
}

// 加载媒体项的转存状态
const loadTransferStatus = async () => {
  if (!libraryItems.value || libraryItems.value.length === 0) return

  try {
    const itemIds = libraryItems.value.map(item => item.id)
    const res = await batchCheckTransferStatus(itemIds)
    if (res.data) {
      transferStatusMap.value = res.data
      console.log('=== 转存状态加载完成 ===')
      console.log('状态映射:', transferStatusMap.value)
      console.log('示例数据:', Object.entries(transferStatusMap.value).slice(0, 5))
    }
  } catch (error) {
    console.error('加载转存状态失败:', error)
  }
}

// 加载媒体项的下载状态
const loadDownloadStatus = async () => {
  if (!libraryItems.value || libraryItems.value.length === 0) return

  try {
    const itemIds = libraryItems.value.map(item => item.id)
    const res = await batchCheckDownloadStatus(itemIds)
    if (res.data) {
      downloadStatusMap.value = res.data
      console.log('=== 下载状态加载完成 ===')
      console.log('状态映射:', downloadStatusMap.value)
      console.log('示例数据:', Object.entries(downloadStatusMap.value).slice(0, 5))
    }
  } catch (error) {
    console.error('加载下载状态失败:', error)
  }
}

// 分页改变
const handlePageChange = (page) => {
  currentPage.value = page
  loadLibraryItems()
}

// 应用转存状态筛选
const applyTransferFilter = () => {
  console.log('=== 应用转存状态筛选 ===')
  console.log('筛选条件:', transferStatusFilter.value)

  // 重置到第一页并重新加载数据
  currentPage.value = 1
  loadLibraryItems()
}

// 应用下载状态筛选
const applyDownloadFilter = () => {
  console.log('=== 应用下载状态筛选 ===')
  console.log('筛选条件:', downloadStatusFilter.value)

  // 重置到第一页并重新加载数据
  currentPage.value = 1
  loadLibraryItems()
}

// 每页数量改变
const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadLibraryItems()
}

// 在媒体库中搜索
const searchInLibrary = async () => {
  if (!searchKeyword.value.trim()) {
    await loadLibraryItems()
    return
  }

  loadingItems.value = true
  try {
    const res = await searchItems(searchKeyword.value)
    // 过滤出当前媒体库的结果
    libraryItems.value = res.data.filter(item =>
      item.parentId === currentLibrary.value.id ||
      item.id === currentLibrary.value.id
    )
  } catch (error) {
    ElMessage.error('搜索失败: ' + error.message)
  } finally {
    loadingItems.value = false
  }
}

// 查看媒体项详情
const viewItemDetail = async (item) => {
  console.log('查看详情 - itemId:', item.id, 'name:', item.name, 'type:', item.type)

  try {
    const res = await getItemDetail(item.id)
    currentItem.value = res.data
    detailDialogVisible.value = true
  } catch (error) {
    console.error('加载详情失败:', error)

    let errorMsg = '加载详情失败'
    if (error.response?.status === 404) {
      errorMsg = `该媒体项不存在或无权访问（ID: ${item.id}）`
    } else if (error.message) {
      errorMsg = '加载详情失败: ' + error.message
    }

    ElMessage.error(errorMsg)
  }
}

// 查看转存历史
const viewTransferHistory = async (item) => {
  try {
    const res = await getHistoryByEmbyItemId(item.id)
    currentTransferHistory.value = res.data || []
    transferHistoryDialogVisible.value = true
  } catch (error) {
    console.error('加载转存历史失败:', error)
    ElMessage.error('加载转存历史失败: ' + error.message)
  }
}

// 同步所有数据
const syncAllData = async () => {
  try {
    await ElMessageBox.confirm(
      '此操作将一次性全量同步所有媒体库数据到数据库，可能需要较长时间，是否继续？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    syncing.value = true
    const res = await syncAllLibraries()

    if (res.data.success) {
      // 全部成功
      ElMessage.success({
        message: `同步完成！共 ${res.data.totalLibraries} 个媒体库，${res.data.totalItems} 个媒体项，耗时 ${res.data.duration}`,
        duration: 5000
      })
      // 更新缓存状态
      hasCache.value = true
      // 刷新数据
      await loadLibraries()
      await loadGenres()
      await loadTags()
      await loadStudios()
    } else if (res.data.failedLibraries > 0) {
      // 部分失败
      ElMessage.warning({
        message: `同步完成，但有 ${res.data.failedLibraries} 个媒体库失败。成功: ${res.data.successLibraries} 个，媒体项: ${res.data.totalItems} 个。失败的媒体库: ${res.data.failedLibraryNames?.join(', ')}`,
        duration: 10000,
        showClose: true
      })
      // 更新缓存状态
      hasCache.value = true
      // 刷新数据
      await loadLibraries()
      await loadGenres()
      await loadTags()
      await loadStudios()
    } else {
      // 完全失败
      ElMessage.error('同步失败: ' + (res.data.error || res.data.message))
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('同步失败: ' + error.message)
    }
  } finally {
    syncing.value = false
  }
}

// 清空缓存
const clearCache = async () => {
  try {
    await ElMessageBox.confirm(
      '此操作将清空所有Emby数据缓存，下次访问时将从Emby服务器重新获取数据，是否继续？',
      '警告',
      {
        confirmButtonText: '确定清空',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    clearing.value = true

    // 调用清空缓存API
    const res = await fetch('/api/emby/cache/clear', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }).then(r => r.json())

    if (res.code === 200) {
      ElMessage.success('缓存已清空，下次访问将从Emby服务器获取最新数据')

      // 清空当前显示的数据
      libraries.value = []
      libraryItems.value = []
      genres.value = []
      tags.value = []
      studios.value = []
    } else {
      ElMessage.error('清空缓存失败: ' + res.message)
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('清空缓存失败: ' + error.message)
    }
  } finally {
    clearing.value = false
  }
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes) return '-'
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(2) + ' ' + sizes[i]
}

// 格式化比特率
const formatBitrate = (bitrate) => {
  if (!bitrate) return '-'
  return (bitrate / 1000000).toFixed(2) + ' Mbps'
}

// 清理搜索关键词
const cleanSearchKeyword = (keyword) => {
  if (!keyword) return ''

  let cleaned = keyword

  // 第一步：移除所有类型的引号（使用 Unicode 范围）
  // 包括: " " ' ' ` ´ ′ ″ ‴ ‵ ‶ ‷ 〝 〞 〟 ＂ ＇ ‹ › « » 「 」 『 』 等
  cleaned = cleaned.replace(/[\u0022\u0027\u0060\u00B4\u2018\u2019\u201A\u201B\u201C\u201D\u201E\u201F\u2032\u2033\u2034\u2035\u2036\u2037\u2039\u203A\u275B\u275C\u275D\u275E\u276E\u276F\u2E42\u301D\u301E\u301F\uFF02\uFF07\u00AB\u00BB\u3008\u3009\u300A\u300B\u300C\u300D\u300E\u300F\u3010\u3011]/g, '')

  // 第二步：移除括号及其内容（包括年份、版本信息等）
  cleaned = cleaned.replace(/\([^)]*\)/g, ' ')
  cleaned = cleaned.replace(/\[[^\]]*\]/g, ' ')
  cleaned = cleaned.replace(/\{[^}]*\}/g, ' ')
  cleaned = cleaned.replace(/（[^）]*）/g, ' ')
  cleaned = cleaned.replace(/【[^】]*】/g, ' ')
  cleaned = cleaned.replace(/〔[^〕]*〕/g, ' ')
  cleaned = cleaned.replace(/〈[^〉]*〉/g, ' ')
  cleaned = cleaned.replace(/《[^》]*》/g, ' ')
  cleaned = cleaned.replace(/﹝[^﹞]*﹞/g, ' ')
  cleaned = cleaned.replace(/﹙[^﹚]*﹚/g, ' ')

  // 第三步：移除常见的标点符号
  cleaned = cleaned.replace(/[,，.。:：;；!！?？、]/g, ' ')

  // 第四步：移除连接符和特殊符号
  cleaned = cleaned.replace(/[-–—_~·•]/g, ' ')

  // 第五步：移除斜杠和反斜杠
  cleaned = cleaned.replace(/[/\\|]/g, ' ')

  // 第六步：移除其他特殊符号
  cleaned = cleaned.replace(/[&＆@＠#＃$＄%％^＾*＊+=＋＝<>＜＞]/g, ' ')

  // 第七步：移除表情符号和特殊Unicode字符
  cleaned = cleaned.replace(/[\u{1F300}-\u{1F9FF}]/gu, '')
  cleaned = cleaned.replace(/[\u{2600}-\u{26FF}]/gu, '')
  cleaned = cleaned.replace(/[\u{2700}-\u{27BF}]/gu, '')

  // 第八步：移除多余的空格并返回
  cleaned = cleaned.replace(/\s+/g, ' ').trim()

  console.log('清理关键词 - 原始:', keyword)
  console.log('清理关键词 - 结果:', cleaned)

  return cleaned
}

// 快速下载
const handleQuickDownload = async (item) => {
  downloadItem.value = item
  subscribeId.value = ''
  subscribeResult.value = null
  searchResults.value = []
  selectedResult.value = null

  // 提取 TMDB ID
  let tmdbId = null
  if (item.providerIds && item.providerIds.Tmdb) {
    tmdbId = item.providerIds.Tmdb
    console.log('提取到 TMDB ID:', tmdbId)
  } else {
    console.log('未找到 TMDB ID')
  }

  // 先尝试从115资源库智能匹配
  try {
    ElMessage.info('正在从115资源库搜索匹配资源...')
    const res = await smartSearch115(
      tmdbId,
      item.name,
      item.originalTitle,
      item.productionYear
    )

    if (res.data) {
      // 找到匹配的115资源
      const resource = res.data
      ElMessage.success(`找到匹配资源: ${resource.name}`)

      // 直接转存到115
      await transfer115Resource(resource)
      return
    } else {
      ElMessage.info('115资源库中未找到匹配资源，将使用搜索引擎搜索')
    }
  } catch (error) {
    console.error('搜索115资源失败:', error)
    ElMessage.warning('115资源库搜索失败，将使用搜索引擎搜索')
  }

  // 如果115资源库没有找到，走原来的搜索逻辑
  // 自动填充搜索关键词（优先使用原始名称，否则使用中文名）
  let keyword = item.originalTitle || item.name

  // 清理搜索关键词中的特殊字符
  keyword = cleanSearchKeyword(keyword)

  console.log('原始关键词:', item.originalTitle || item.name)
  console.log('清理后关键词:', keyword)

  searchKeywordInput.value = keyword

  downloadDialogVisible.value = true

  // 自动搜索
  handleSearchByKeyword()
}

// 直接从Emby下载
const handleDirectDownload = async (item) => {
  try {
    // 根据类型显示不同的确认信息
    let confirmMessage = ''
    if (item.type === 'Series') {
      confirmMessage = `确定要下载电视剧 "${item.name}" 的所有剧集吗？\n\n文件将保存到服务器的 /data/emby/${item.name}/ 目录下。\n\n下载将在后台进行，请查看后端日志了解进度。`
    } else if (item.type === 'Episode') {
      confirmMessage = `确定要下载剧集 "${item.name}" 吗？\n\n文件将保存到服务器的 /data/emby 目录下。\n\n下载将在后台进行，请查看后端日志了解进度。`
    } else {
      confirmMessage = `确定要下载 "${item.name}" 到服务器吗？\n\n文件将保存到服务器的 /data/emby 目录下。\n\n下载将在后台进行，请查看后端日志了解进度。`
    }

    // 确认下载
    await ElMessageBox.confirm(
      confirmMessage,
      '确认下载',
      {
        confirmButtonText: '确定下载',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    try {
      const res = await downloadToServer(item.id)

      if (res.data && res.data.status === 'started') {
        ElMessage.success({
          message: '下载任务已启动！下载完成后将自动刷新状态。',
          duration: 5000
        })

        // 启动定时器，每10秒刷新一次下载状态，持续5分钟
        let refreshCount = 0
        const maxRefreshCount = 30 // 5分钟 = 30次 * 10秒
        const refreshInterval = setInterval(async () => {
          refreshCount++

          // 刷新当前页面的下载状态
          if (libraryItems.value && libraryItems.value.length > 0) {
            const itemIds = libraryItems.value.map(i => i.id)
            try {
              const statusRes = await batchCheckDownloadStatus(itemIds)
              if (statusRes.data) {
                Object.assign(downloadStatusMap.value, statusRes.data)
              }
            } catch (error) {
              console.error('刷新下载状态失败:', error)
            }
          }

          // 如果达到最大刷新次数，停止定时器
          if (refreshCount >= maxRefreshCount) {
            clearInterval(refreshInterval)
          }
        }, 10000) // 每10秒刷新一次
      } else {
        ElMessage.error('启动下载失败')
      }
    } catch (error) {
      console.error('启动下载失败:', error)
      ElMessage.error('启动下载失败: ' + (error.message || '未知错误'))
    }

  } catch {
    // 用户取消
  }
}

// 手动标记下载成功
const handleMarkDownloadSuccess = async (item) => {
  try {
    await ElMessageBox.confirm(
      `确定要将 "${item.name}" 标记为已下载吗？`,
      '确认标记',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    try {
      const res = await markDownloadStatus(item.id, 'success')

      if (res.code === 200) {
        ElMessage.success('标记成功')

        // 更新本地状态
        downloadStatusMap.value[item.id] = 'success'
      } else {
        ElMessage.error('标记失败: ' + (res.message || '未知错误'))
      }
    } catch (error) {
      console.error('标记失败:', error)
      ElMessage.error('标记失败: ' + (error.message || '未知错误'))
    }

  } catch {
    // 用户取消
  }
}

// 通过关键词搜索
const handleSearchByKeyword = async () => {
  if (!searchKeywordInput.value) {
    ElMessage.warning('请输入搜索关键词')
    return
  }

  searchingKeyword.value = true
  searchResults.value = []
  selectedResult.value = null

  try {
    const res = await searchByKeyword(searchKeywordInput.value, true)

    console.log('=== 搜索返回原始结果 ===')
    console.log('完整响应:', res)
    console.log('res.data:', res.data)
    console.log('res.data.channels:', res.data?.channels)
    console.log('res.data.channel_info_list:', res.data?.channel_info_list)

    // 解析返回的数据结构
    let results = []

    // 处理嵌套的数据结构 - 支持两种字段名
    const channels = res.data?.channels || res.data?.channel_info_list

    if (channels && Array.isArray(channels)) {
      console.log(`找到 ${channels.length} 个频道`)

      // 遍历所有频道
      channels.forEach((channel, channelIndex) => {
        const channelInfo = channel.channelInfo || {}
        console.log(`\n=== 频道 ${channelIndex + 1}: ${channelInfo.name || '未知'} ===`)

        // 处理 messages 数组（阿里云盘格式）
        if (channel.messages && Array.isArray(channel.messages)) {
          console.log(`  - messages 数组长度: ${channel.messages.length}`)

          channel.messages.forEach((message, msgIndex) => {
            console.log(`    消息 ${msgIndex + 1}:`, {
              messageId: message.messageId,
              title: message.title?.substring(0, 50),
              text: message.text?.substring(0, 50),
              cloudLinks: message.cloudLinks
            })

            if (message.cloudLinks && message.cloudLinks.length > 0) {
              message.cloudLinks.forEach((link, linkIndex) => {
                const rawTitle = message.title || message.text
                const cleanTitle = extractTitle(rawTitle)

                console.log(`      链接 ${linkIndex + 1}: ${link}`)
                console.log(`      提取的标题: ${cleanTitle}`)

                results.push({
                  id: `${message.messageId}-${linkIndex}`,
                  messageId: message.messageId,
                  title: cleanTitle,
                  rawTitle: rawTitle,
                  description: message.text || message.content,
                  url: link,
                  link: link,
                  share_url: link,
                  size: extractSize(message.text || message.content),
                  resolution: extractResolution(message.text || message.content),
                  tags: message.tags || [],
                  image: message.image,
                  cloudType: message.cloudType,
                  channelId: message.channelId || channelInfo.id,
                  channelName: channelInfo.name || '未知来源'
                })
              })
            }
          })
        }

        // 处理 list 数组（天翼云盘格式）
        if (channel.list && Array.isArray(channel.list)) {
          console.log(`  - list 数组长度: ${channel.list.length}`)

          channel.list.forEach((item, itemIndex) => {
            console.log(`    项目 ${itemIndex + 1}:`, {
              messageId: item.messageId,
              title: item.title?.substring(0, 50),
              content: item.content?.substring(0, 50),
              cloudLinks: item.cloudLinks
            })

            if (item.cloudLinks && item.cloudLinks.length > 0) {
              item.cloudLinks.forEach((link, linkIndex) => {
                const rawTitle = item.title || item.content
                const cleanTitle = extractTitle(rawTitle)

                console.log(`      链接 ${linkIndex + 1}: ${link}`)
                console.log(`      提取的标题: ${cleanTitle}`)

                results.push({
                  id: `${item.messageId}-${linkIndex}`,
                  messageId: item.messageId,
                  title: cleanTitle,
                  rawTitle: rawTitle,
                  description: item.content || item.title,
                  url: link,
                  link: link,
                  share_url: link,
                  size: extractSize(item.content || item.title),
                  resolution: extractResolution(item.content || item.title),
                  tags: item.tags || [],
                  image: item.image,
                  cloudType: item.cloudType,
                  channelId: item.channelId || channelInfo.id,
                  channelName: channelInfo.name || '未知来源',
                  pubDate: item.pubDate
                })
              })
            }
          })
        }
      })
    } else if (Array.isArray(res)) {
      console.log('响应是数组格式')
      results = res
    } else if (res.data && Array.isArray(res.data)) {
      console.log('res.data 是数组格式')
      results = res.data
    } else if (res.results && Array.isArray(res.results)) {
      console.log('res.results 是数组格式')
      results = res.results
    } else {
      console.error('无法识别的数据格式:', res)
    }

    console.log(`\n=== 最终结果 ===`)
    console.log(`总共提取了 ${results.length} 个结果`)
    console.log('前3个结果:', results.slice(0, 3))

    // 第一步：智能排序
    if (results.length > 0) {
      results = smartSortResults(results, searchKeywordInput.value, downloadItem.value)
      console.log('排序后前3个结果:', results.slice(0, 3))
    }

    // 第二步：AI智能筛选（可选）
    if (useAI.value && results.length > 0) {
      await aiSelectBest(results)
    }

    searchResults.value = results

    if (results.length > 0) {
      // 过滤掉不匹配的结果（评分太低）
      const minScore = 30 // 最低30分才考虑
      const qualifiedResults = results.filter(r => r.matchScore >= minScore)

      if (qualifiedResults.length === 0) {
        ElMessage.warning('未找到符合要求的资源（所有结果评分过低）')
        return
      }

      console.log(`\n=== 筛选结果 ===`)
      console.log(`原始结果: ${results.length} 个`)
      console.log(`合格结果: ${qualifiedResults.length} 个（评分≥${minScore}）`)

      ElMessage.success(`找到 ${qualifiedResults.length} 个合格结果，准备自动转存`)

      // 自动转存最佳匹配（尝试所有结果直到成功，最多20个）
      await autoTransferBestMatches(qualifiedResults)
    } else {
      ElMessage.warning('未找到搜索结果')
    }
  } catch (error) {
    console.error('搜索失败:', error)
    searchResults.value = []
  } finally {
    searchingKeyword.value = false
  }
}

// 验证资源链接有效性（通过实际转存测试）
const validateResourceLinks = async (results) => {
  console.log('\n=== 开始验证链接有效性 ===')
  validatingLinks.value = true

  try {
    // 检查是否有云盘配置
    if (cloudConfigs.value.length === 0) {
      console.warn('未配置任何云盘，跳过链接验证')
      ElMessage.warning('请先在智能搜索配置中添加云盘配置')
      return
    }

    // 加载配置
    let maxValidationCount = 20
    let validationTimeout = 10000

    try {
      const saved = localStorage.getItem('smartSearchConfig')
      if (saved) {
        const config = JSON.parse(saved)
        if (config.maxValidationCount) {
          maxValidationCount = config.maxValidationCount
        }
        if (config.validationTimeout) {
          validationTimeout = config.validationTimeout
        }
      }
    } catch (error) {
      console.error('加载验证配置失败:', error)
    }

    // 只验证前N个结果
    const resultsToValidate = results.slice(0, maxValidationCount)
    console.log(`验证前 ${resultsToValidate.length} 个链接（最多${maxValidationCount}个）`)

    // 逐个验证链接（通过实际转存测试）
    for (let i = 0; i < resultsToValidate.length; i++) {
      const result = resultsToValidate[i]

      try {
        console.log(`验证链接 ${i + 1}/${resultsToValidate.length}: ${result.title}`)

        // 根据资源的cloudType获取对应的云盘配置
        const cloudConfig = getCloudConfigByType(result.cloudType)

        if (!cloudConfig) {
          result.isValid = null
          result.validationMessage = `未配置 ${result.cloudType}`
          console.log(`? 未配置云盘: ${result.title} - ${result.cloudType}`)
          continue
        }

        if (!cloudConfig.parentId) {
          result.isValid = null
          result.validationMessage = '未设置目录ID'
          console.log(`? 未设置目录ID: ${result.title}`)
          continue
        }

        console.log(`使用云盘配置: ${cloudConfig.name} (${cloudConfig.cloudType})`)

        // 调用转存API测试链接
        const res = await Promise.race([
          transferToAlipan(result.url, cloudConfig.parentId, cloudConfig.cloudType),
          new Promise((_, reject) =>
            setTimeout(() => reject(new Error('timeout')), validationTimeout)
          )
        ])

        // 转存成功表示链接有效
        if (res && res.code === 200) {
          result.isValid = true
          result.validationMessage = '链接有效'
          result.matchScore += 10
          result.matchReasons.push('链接有效: +10分')
          console.log(`✓ 链接有效: ${result.title}`)
        } else {
          result.isValid = false
          result.validationMessage = res?.message || '转存失败'
          console.log(`✗ 链接失效: ${result.title} - ${result.validationMessage}`)
        }
      } catch (error) {
        // 转存失败或超时表示链接失效
        result.isValid = false
        result.validationMessage = error.message === 'timeout' ? '验证超时' : '链接失效'
        console.log(`✗ 链接失效: ${result.title} - ${result.validationMessage}`)
      }
    }

    // 未验证的链接标记为未知
    for (let i = maxValidationCount; i < results.length; i++) {
      results[i].isValid = null
      results[i].validationMessage = '未验证'
    }

    // 重新排序（优先显示有效的链接）
    results.sort((a, b) => {
      // 有效的链接优先
      if (a.isValid === true && b.isValid !== true) return -1
      if (a.isValid !== true && b.isValid === true) return 1
      // 然后按评分排序
      return b.matchScore - a.matchScore
    })

    const validCount = results.filter(r => r.isValid === true).length
    const invalidCount = results.filter(r => r.isValid === false).length
    const unknownCount = results.filter(r => r.isValid === null).length

    console.log(`验证完成：✓ ${validCount} 个有效，✗ ${invalidCount} 个失效，? ${unknownCount} 个未验证`)

    if (validCount === 0 && invalidCount > 0) {
      ElMessage.warning('所有验证的链接都已失效，请尝试其他搜索关键词')
    }
  } catch (error) {
    console.error('验证链接失败:', error)
    ElMessage.warning('链接验证失败，将使用规则评分')
  } finally {
    validatingLinks.value = false
  }
}

// AI智能筛选最佳资源
const aiSelectBest = async (results) => {
  console.log('\n=== 开始AI智能筛选 ===')
  aiSelecting.value = true

  try {
    // 准备电影信息
    const movieInfo = {
      name: downloadItem.value.name,
      originalTitle: downloadItem.value.originalTitle,
      productionYear: downloadItem.value.productionYear,
      communityRating: downloadItem.value.communityRating,
      genres: downloadItem.value.genres,
      overview: downloadItem.value.overview
    }

    // 准备资源列表（只发送前10个，避免token过多）
    const topResources = results.slice(0, 10).map(r => ({
      id: r.id,
      title: r.title,
      size: r.size,
      resolution: r.resolution,
      tags: r.tags,
      description: r.description?.substring(0, 200), // 限制描述长度
      matchScore: r.matchScore,
      isValid: r.isValid,
      hasFiles: r.hasFiles
    }))

    console.log('发送给AI的数据:', { movieInfo, resourceCount: topResources.length })

    // 调用AI筛选API
    const res = await aiSelectBestResource(movieInfo, topResources)

    console.log('AI筛选结果:', res)

    // 检查AI是否判断所有资源都不匹配
    if (res.data && res.data.title_match === false) {
      console.log('AI判断: 所有资源都不匹配')
      ElMessage.warning(`AI判断: ${res.data.reason}`)

      // 清空结果列表，不进行转存
      results.length = 0
      return
    }

    if (res.data && res.data.best_resource_id) {
      // 找到AI推荐的资源
      const bestResourceId = res.data.best_resource_id
      const bestResource = results.find(r => r.id === bestResourceId)

      if (bestResource) {
        // 标记为AI推荐
        bestResource.aiRecommended = true
        bestResource.aiReason = res.data.reason
        bestResource.matchScore += 15 // AI推荐加分
        bestResource.matchReasons.push('AI推荐: +15分')

        console.log('AI推荐资源:', bestResource.title)
        console.log('推荐理由:', res.data.reason)

        // 将AI推荐的资源移到第一位
        const index = results.indexOf(bestResource)
        if (index > 0) {
          results.splice(index, 1)
          results.unshift(bestResource)
        }
      }
    }
  } catch (error) {
    console.error('AI筛选失败:', error)
    // AI筛选失败不影响主流程
    ElMessage.warning('AI筛选服务暂时不可用，使用规则筛选')
  } finally {
    aiSelecting.value = false
  }
}

// 智能排序结果
const smartSortResults = (results, searchKeyword, movieItem) => {
  console.log('\n=== 开始智能排序 ===')
  console.log('搜索关键词:', searchKeyword)
  console.log('电影信息:', movieItem)

  // 加载配置权重
  let weights = {
    titleMatch: 40,
    resolution: 20,
    fileSize: 15,
    tagMatch: 10,
    sourceCredibility: 10,
    timeliness: 5
  }

  try {
    const saved = localStorage.getItem('smartSearchConfig')
    if (saved) {
      const config = JSON.parse(saved)
      if (config.weights) {
        weights = config.weights
        console.log('使用自定义权重:', weights)
      }
    }
  } catch (error) {
    console.error('加载权重配置失败:', error)
  }

  // 为每个结果计算评分
  const scoredResults = results.map(result => {
    let score = 0
    const reasons = []

    // 1. 标题匹配度
    const titleScore = calculateTitleMatch(result.title, searchKeyword, movieItem, weights.titleMatch)
    score += titleScore
    if (titleScore > 0) {
      reasons.push(`标题匹配: ${titleScore.toFixed(1)}分`)
    }

    // 2. 分辨率评分
    const resolutionScore = calculateResolutionScore(result.resolution, result.description, weights.resolution)
    score += resolutionScore
    if (resolutionScore > 0) {
      reasons.push(`分辨率: ${resolutionScore.toFixed(1)}分`)
    }

    // 3. 文件大小评分
    const sizeScore = calculateSizeScore(result.size, weights.fileSize)
    score += sizeScore
    if (sizeScore > 0) {
      reasons.push(`文件大小: ${sizeScore.toFixed(1)}分`)
    }

    // 4. 标签匹配评分
    const tagScore = calculateTagScore(result.tags, movieItem, weights.tagMatch)
    score += tagScore
    if (tagScore > 0) {
      reasons.push(`标签匹配: ${tagScore.toFixed(1)}分`)
    }

    // 5. 来源可信度
    const sourceScore = calculateSourceScore(result.channelName, result.cloudType, weights.sourceCredibility)
    score += sourceScore
    if (sourceScore > 0) {
      reasons.push(`来源: ${sourceScore.toFixed(1)}分`)
    }

    // 6. 发布时间 - 越新越好
    const timeScore = calculateTimeScore(result.pubDate, weights.timeliness)
    score += timeScore
    if (timeScore > 0) {
      reasons.push(`时效性: ${timeScore.toFixed(1)}分`)
    }

    console.log(`\n资源: ${result.title}`)
    console.log(`总分: ${score.toFixed(1)}`)
    console.log(`评分详情: ${reasons.join(', ')}`)

    return {
      ...result,
      matchScore: score,
      matchReasons: reasons
    }
  })

  // 按评分降序排序
  scoredResults.sort((a, b) => b.matchScore - a.matchScore)

  console.log('\n=== 排序完成 ===')
  console.log('最佳匹配:', scoredResults[0]?.title, '得分:', scoredResults[0]?.matchScore.toFixed(1))

  return scoredResults
}

// 计算标题匹配度
const calculateTitleMatch = (title, searchKeyword, movieItem, maxScore = 40) => {
  let score = 0
  const titleLower = title.toLowerCase()
  const keywordLower = searchKeyword.toLowerCase()

  // 完全匹配
  if (titleLower.includes(keywordLower)) {
    score += maxScore
  } else {
    // 部分匹配：计算关键词中有多少字符在标题中
    const keywordChars = keywordLower.split('')
    const matchedChars = keywordChars.filter(char => titleLower.includes(char))
    score += (matchedChars.length / keywordChars.length) * (maxScore * 0.75)
  }

  // 如果有原始标题，也检查匹配
  if (movieItem?.originalTitle) {
    const originalLower = movieItem.originalTitle.toLowerCase()
    if (titleLower.includes(originalLower)) {
      score += maxScore * 0.25
    }
  }

  return Math.min(score, maxScore)
}

// 计算分辨率评分
const calculateResolutionScore = (resolution, description, maxScore = 20) => {
  const text = (resolution || '') + ' ' + (description || '')
  const textLower = text.toLowerCase()

  // 优先级：4K > 1080p > 720p > 其他
  if (textLower.includes('4k') || textLower.includes('2160p')) {
    return maxScore
  } else if (textLower.includes('remux') || textLower.includes('蓝光原盘')) {
    return maxScore * 0.9
  } else if (textLower.includes('1080p') || textLower.includes('bluray')) {
    return maxScore * 0.75
  } else if (textLower.includes('720p')) {
    return maxScore * 0.5
  } else if (textLower.includes('480p')) {
    return maxScore * 0.25
  }

  return 0
}

// 计算文件大小评分
const calculateSizeScore = (sizeStr, maxScore = 15) => {
  if (!sizeStr || sizeStr === '-' || sizeStr === 'N') {
    return 0
  }

  // 提取数字和单位
  const match = sizeStr.match(/(\d+\.?\d*)\s*(GB|MB|TB)/i)
  if (!match) return 0

  const size = parseFloat(match[1])
  const unit = match[2].toUpperCase()

  // 转换为GB
  let sizeInGB = size
  if (unit === 'MB') {
    sizeInGB = size / 1024
  } else if (unit === 'TB') {
    sizeInGB = size * 1024
  }

  // 电影合理大小：2GB - 100GB
  // 最佳范围：10GB - 50GB（高清电影）
  if (sizeInGB >= 10 && sizeInGB <= 50) {
    return maxScore // 最佳范围
  } else if (sizeInGB >= 5 && sizeInGB <= 80) {
    return maxScore * 0.67 // 合理范围
  } else if (sizeInGB >= 2 && sizeInGB <= 100) {
    return maxScore * 0.33 // 可接受范围
  }

  return 0
}

// 计算标签匹配评分
const calculateTagScore = (tags, movieItem, maxScore = 10) => {
  if (!tags || tags.length === 0) return 0

  let score = 0

  // 检查是否有电影类型标签
  const movieTags = ['#电影', '#movie', '#剧情', '#动作', '#喜剧', '#科幻']
  const hasMovieTag = tags.some(tag =>
    movieTags.some(mt => tag.toLowerCase().includes(mt.toLowerCase()))
  )
  if (hasMovieTag) {
    score += maxScore * 0.5
  }

  // 检查是否有质量标签
  const qualityTags = ['#蓝光', '#高清', '#4k', '#remux', '#bluray']
  const hasQualityTag = tags.some(tag =>
    qualityTags.some(qt => tag.toLowerCase().includes(qt.toLowerCase()))
  )
  if (hasQualityTag) {
    score += maxScore * 0.5
  }

  return Math.min(score, maxScore)
}

// 计算来源可信度评分
const calculateSourceScore = (channelName, cloudType, maxScore = 10) => {
  let score = maxScore * 0.5 // 基础分

  // 阿里云盘通常更可靠
  if (cloudType === 'channel_alipan') {
    score += maxScore * 0.3
  }

  // 知名频道加分
  const trustedChannels = ['ali-01', '天翼云盘', 'shareAliyun']
  if (trustedChannels.includes(channelName)) {
    score += maxScore * 0.2
  }

  return Math.min(score, maxScore)
}

// 计算时效性评分
const calculateTimeScore = (pubDate, maxScore = 5) => {
  if (!pubDate) return 0

  try {
    const publishTime = new Date(pubDate)
    const now = new Date()
    const daysDiff = (now - publishTime) / (1000 * 60 * 60 * 24)

    // 越新越好
    if (daysDiff <= 30) {
      return maxScore // 一个月内
    } else if (daysDiff <= 90) {
      return maxScore * 0.6 // 三个月内
    } else if (daysDiff <= 180) {
      return maxScore * 0.2 // 半年内
    }
  } catch (e) {
    console.error('解析发布时间失败:', e)
  }

  return 0
}
const extractTitle = (text) => {
  if (!text) return '未知标题'

  // 如果 text 本身就是简短的标题（不包含换行），直接返回
  if (!text.includes('\n') && text.length < 100) {
    return text.trim()
  }

  // 移除链接
  text = text.replace(/https?:\/\/[^\s]+/g, '')

  // 按行分割
  const lines = text.split('\n').map(line => line.trim()).filter(line => line.length > 0)

  // 查找标题的策略
  for (let i = 0; i < Math.min(5, lines.length); i++) {
    const line = lines[i]

    // 跳过包含这些关键词的行（通常是描述）
    if (
      line.includes('本片') ||
      line.includes('描述：') ||
      line.includes('简介：') ||
      line.includes('链接：') ||
      line.includes('📁') ||
      line.includes('🏷') ||
      line.includes('🎉') ||
      line.includes('⚠️') ||
      line.includes('📢') ||
      line.includes('👥') ||
      line.includes('🤖') ||
      line.startsWith('大小') ||
      line.startsWith('标签') ||
      line.startsWith('来自') ||
      line.startsWith('版权') ||
      line.startsWith('频道') ||
      line.startsWith('群组') ||
      line.startsWith('投稿')
    ) {
      continue
    }

    // 跳过太短的行
    if (line.length < 2) {
      continue
    }

    // 找到了合适的标题
    if (line.length >= 2) {
      let title = line
        // 移除"名称："前缀
        .replace(/^名称[：:]\s*/g, '')
        .replace(/^标题[：:]\s*/g, '')
        // 移除年份括号（保留电影名）
        .replace(/\s*\(\d{4}\)\s*/g, ' ')
        .replace(/\s*（\d{4}）\s*/g, ' ')
        .trim()

      // 如果标题太长，截取到合理长度
      if (title.length > 60) {
        // 尝试在空格或标点处截断
        const cutIndex = title.substring(0, 60).lastIndexOf(' ')
        if (cutIndex > 30) {
          title = title.substring(0, cutIndex) + '...'
        } else {
          title = title.substring(0, 60) + '...'
        }
      }

      return title
    }
  }

  // 如果没找到合适的，返回第一行
  const firstLine = lines[0] || '未知标题'
  return firstLine.substring(0, 60) + (firstLine.length > 60 ? '...' : '')
}

// 从文本中提取大小
const extractSize = (text) => {
  if (!text) return '-'

  // 匹配各种大小格式
  const patterns = [
    /📁\s*大小[：:]\s*([^\n]+)/,
    /大小[：:]\s*([^\n]+)/,
    /(\d+\.?\d*\s*[KMGT]B)/i,
    /\((\d+\.?\d*\s*[KMGT]B)\)/i
  ]

  for (const pattern of patterns) {
    const match = text.match(pattern)
    if (match) {
      return match[1].trim()
    }
  }

  return '-'
}

// 从文本中提取分辨率
const extractResolution = (text) => {
  if (!text) return null

  // 匹配常见的分辨率标签
  const resolutions = [
    '4K', '2160p', '1080p', '720p', '480p',
    'REMUX', 'BluRay', 'Blu-ray', 'WEB-DL', 'WEBRip',
    '蓝光', '蓝光原盘', '超清', '高清', '标清',
    'HDR', 'SDR', 'Dolby', '杜比'
  ]

  for (const res of resolutions) {
    if (text.includes(res)) {
      return res
    }
  }

  return null
}

// 选择搜索结果
const handleSelectResult = (row) => {
  selectedResult.value = row
}

// 选择并下载
const handleSelectAndDownload = async (row) => {
  selectedResult.value = row

  try {
    await ElMessageBox.confirm(
      `确定要下载这个资源吗？\n\n标题: ${row.title || row.name}\n大小: ${row.size || '未知'}`,
      '确认下载',
      {
        confirmButtonText: '确定下载',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await handleCreateDownloadTask(row)
  } catch (error) {
    if (error !== 'cancel') {
      console.error('下载失败:', error)
    }
  }
}

// 转存到阿里云盘
// 自动转存最佳匹配的资源（尝试所有结果直到成功）
const autoTransferBestMatches = async (results, maxAttempts = null) => {
  // 如果没有指定最大尝试次数，则尝试所有结果（最多10个，避免过多低质量结果）
  const actualMaxAttempts = maxAttempts || Math.min(results.length, 10)

  console.log(`\n=== 开始自动转存 ===`)
  console.log(`候选资源数: ${results.length}`)
  console.log(`最多尝试: ${actualMaxAttempts} 次`)

  // 按云盘类型分组
  const groupedResults = {}

  results.forEach(result => {
    const cloudType = result.cloudType
    if (!groupedResults[cloudType]) {
      groupedResults[cloudType] = []
    }
    groupedResults[cloudType].push(result)
  })

  console.log(`\n=== 云盘资源分布 ===`)
  Object.keys(groupedResults).forEach(cloudType => {
    console.log(`${cloudType}: ${groupedResults[cloudType].length} 个`)
  })

  // 获取云盘配置并按优先级排序
  const cloudConfigsWithPriority = cloudConfigs.value
    .filter(config => groupedResults[config.cloudType]) // 只保留有资源的云盘
    .sort((a, b) => (a.priority || 999) - (b.priority || 999)) // 按优先级排序，数字越小越优先

  console.log(`\n=== 云盘优先级排序 ===`)
  cloudConfigsWithPriority.forEach((config, i) => {
    console.log(`${i + 1}. ${config.cloudType} (优先级: ${config.priority || '未设置'}) - ${groupedResults[config.cloudType].length} 个资源`)
  })

  // 按优先级交错选择资源
  const sortedResults = []
  const indices = {} // 记录每个云盘类型的当前索引

  cloudConfigsWithPriority.forEach(config => {
    indices[config.cloudType] = 0
  })

  // 循环选择，每轮按优先级顺序选择
  while (sortedResults.length < actualMaxAttempts) {
    let addedInThisRound = false

    for (const config of cloudConfigsWithPriority) {
      const cloudType = config.cloudType
      const resources = groupedResults[cloudType]

      if (indices[cloudType] < resources.length) {
        sortedResults.push(resources[indices[cloudType]++])
        addedInThisRound = true

        if (sortedResults.length >= actualMaxAttempts) {
          break
        }
      }
    }

    // 如果这一轮没有添加任何资源，说明所有云盘都用完了
    if (!addedInThisRound) {
      break
    }
  }

  console.log(`\n=== 优先级排序后 ===`)
  sortedResults.slice(0, 10).forEach((r, i) => {
    console.log(`${i + 1}. [${r.cloudType}] ${r.title.substring(0, 50)} - 评分: ${r.matchScore?.toFixed(1)}`)
  })

  transferring.value = true

  // 记录所有转存尝试
  const transferAttempts = []

  try {
    let attemptCount = 0 // 实际尝试转存的次数
    let successTransfer = null

    // 遍历排序后的资源
    for (let i = 0; i < sortedResults.length && attemptCount < actualMaxAttempts; i++) {
      const resource = sortedResults[i]

      console.log(`\n--- 检查资源 ${i + 1}/${sortedResults.length} ---`)
      console.log(`资源: ${resource.title}`)
      console.log(`评分: ${resource.matchScore?.toFixed(1)}`)
      console.log(`cloudType: ${resource.cloudType}`)

      // 检查链接
      const url = resource.url || resource.link || resource.share_url
      if (!url) {
        console.log(`✗ 跳过: 没有有效链接`)
        continue
      }

      // 获取云盘配置
      const cloudConfig = getCloudConfigByType(resource.cloudType)
      if (!cloudConfig) {
        console.log(`✗ 跳过: 未配置 ${resource.cloudType}`)
        continue
      }

      if (!cloudConfig.parentId) {
        console.log(`✗ 跳过: ${cloudConfig.name} 未设置目录ID`)
        continue
      }

      // 只有通过所有检查后，才算一次真正的尝试
      attemptCount++
      console.log(`\n>>> 开始第 ${attemptCount} 次转存尝试 <<<`)
      console.log(`使用云盘: ${cloudConfig.name} (${cloudConfig.cloudType})`)

      // 尝试转存
      try {
        ElMessage.info(`正在转存: ${resource.title}`)

        const res = await transferToAlipan(url, cloudConfig.parentId, cloudConfig.cloudType)

        console.log('转存响应:', res)

        // 判断转存是否成功
        const isSuccess = res.success || res.code === 0 || res.code === 200 || res.status === 'success'
        const errorMsg = res.message || res.msg || res.error || (isSuccess ? '转存成功' : '转存失败')

        // 记录转存历史
        const historyRecord = {
          embyItemId: downloadItem.value.id,
          embyItemName: downloadItem.value.name,
          embyItemYear: downloadItem.value.productionYear,
          resourceId: resource.id,
          resourceTitle: resource.title,
          resourceUrl: url,
          matchScore: resource.matchScore,
          cloudType: resource.cloudType,
          cloudName: cloudConfig.name,
          parentId: cloudConfig.parentId,
          transferStatus: isSuccess ? 'success' : 'failed',
          transferMessage: errorMsg
        }

        // 保存到数据库
        try {
          await saveTransferHistory(historyRecord)
        } catch (error) {
          console.error('保存转存历史失败:', error)
        }

        // 记录到本地数组
        transferAttempts.push({
          ...historyRecord,
          index: i + 1,
          total: results.length
        })

        if (isSuccess) {
          successTransfer = resource
          console.log(`✓✓✓ 转存成功！✓✓✓`)

          ElMessage.success({
            message: `转存成功！\n资源: ${resource.title}\n评分: ${resource.matchScore?.toFixed(1)}分\n云盘: ${cloudConfig.name}`,
            duration: 5000,
            showClose: true
          })

          // 更新转存状态
          transferStatusMap.value[downloadItem.value.id] = 'success'

          // 关闭对话框
          downloadDialogVisible.value = false

          // 成功后立即跳出循环，不再尝试其他资源
          break
        } else {
          console.log(`✗ 转存失败: ${errorMsg}`)

          ElMessage.warning({
            message: `转存失败: ${errorMsg}`,
            duration: 3000
          })

          // 如果还没达到最大尝试次数，继续下一个
          if (attemptCount < actualMaxAttempts) {
            console.log(`继续尝试下一个资源...`)
            await new Promise(resolve => setTimeout(resolve, 1000)) // 等待1秒
          }
        }
      } catch (error) {
        console.error(`✗ 转存异常:`, error)

        const errorMsg = error.response?.data?.message || error.message || '未知错误'

        // 记录转存历史（失败）
        const historyRecord = {
          embyItemId: downloadItem.value.id,
          embyItemName: downloadItem.value.name,
          embyItemYear: downloadItem.value.productionYear,
          resourceId: resource.id,
          resourceTitle: resource.title,
          resourceUrl: url,
          matchScore: resource.matchScore,
          cloudType: resource.cloudType,
          cloudName: cloudConfig.name,
          parentId: cloudConfig.parentId,
          transferStatus: 'failed',
          transferMessage: errorMsg
        }

        // 保存到数据库
        try {
          await saveTransferHistory(historyRecord)
        } catch (err) {
          console.error('保存转存历史失败:', err)
        }

        // 记录到本地数组
        transferAttempts.push({
          ...historyRecord,
          index: i + 1,
          total: results.length
        })

        ElMessage.warning({
          message: `转存失败: ${errorMsg}`,
          duration: 3000
        })

        // 如果还没达到最大尝试次数，继续下一个
        if (attemptCount < maxAttempts) {
          console.log(`继续尝试下一个资源...`)
          await new Promise(resolve => setTimeout(resolve, 1000)) // 等待1秒
        }
      }
    }

    // 最终结果
    console.log(`\n=== 转存记录 ===`)
    console.log(`尝试次数: ${transferAttempts.length}`)
    transferAttempts.forEach((attempt, index) => {
      const status = attempt.transferStatus === 'success' ? '✓ 成功' : '✗ 失败'
      console.log(`${index + 1}. ${status} - ${attempt.resourceTitle} - ${attempt.transferMessage}`)
    })

    if (successTransfer) {
      console.log(`\n=== 转存完成 ===`)
      console.log(`成功转存: ${successTransfer.title}`)
      console.log(`实际尝试次数: ${attemptCount}`)
    } else {
      console.log(`\n=== 转存失败 ===`)
      console.log(`已尝试 ${attemptCount} 次，全部失败`)

      // 显示详细的失败信息
      let failureDetails = `自动转存失败！已尝试 ${attemptCount} 个资源：\n\n`
      transferAttempts.forEach((attempt, index) => {
        failureDetails += `${index + 1}. ${attempt.resourceTitle}\n   状态: ${attempt.transferStatus === 'success' ? '成功' : '失败'}\n   原因: ${attempt.transferMessage}\n\n`
      })

      ElMessageBox.alert(failureDetails, '转存结果', {
        confirmButtonText: '确定',
        type: 'warning',
        dangerouslyUseHTMLString: false
      })
    }
  } catch (error) {
    console.error('自动转存异常:', error)
    ElMessage.error('自动转存失败: ' + error.message)
  } finally {
    transferring.value = false
  }
}

// 转存115资源
const transfer115Resource = async (resource) => {
  try {
    transferring.value = true

    ElMessage.info(`正在转存: ${resource.name}`)

    // 调用115转存接口
    const res = await transfer115(resource.url, resource.code)

    console.log('115资源转存结果:', res)

    // 判断转存是否成功
    const isSuccess = res.code === 200 || res.success === true
    const errorMsg = res.message || res.msg || res.error || (isSuccess ? '转存成功' : '转存失败')

    // 记录转存历史
    const historyRecord = {
      embyItemId: downloadItem.value.id,
      embyItemName: downloadItem.value.name,
      embyItemYear: downloadItem.value.productionYear,
      resourceId: resource.id?.toString(),
      resourceTitle: resource.name,
      resourceUrl: resource.url,
      matchScore: 100, // 115资源库匹配分数设为100
      cloudType: '115',
      cloudName: '115网盘',
      parentId: '0',
      transferStatus: isSuccess ? 'success' : 'failed',
      transferMessage: errorMsg
    }

    try {
      await saveTransferHistory(historyRecord)
    } catch (error) {
      console.error('保存转存历史失败:', error)
    }

    if (isSuccess) {
      ElMessage.success({
        message: `转存成功！\n资源: ${resource.name}\n来源: 115资源库`,
        duration: 5000,
        showClose: true
      })

      // 更新转存状态
      transferStatusMap.value[downloadItem.value.id] = 'success'

      // 关闭对话框
      downloadDialogVisible.value = false
    } else {
      ElMessage.error(`转存失败: ${errorMsg}`)

      // 转存失败，继续走搜索逻辑
      ElMessage.info('将使用搜索引擎继续搜索资源')

      // 自动填充搜索关键词
      let keyword = downloadItem.value.originalTitle || downloadItem.value.name
      keyword = cleanSearchKeyword(keyword)
      searchKeywordInput.value = keyword

      downloadDialogVisible.value = true

      // 自动搜索
      handleSearchByKeyword()
    }
  } catch (error) {
    console.error('115资源转存异常:', error)
    const errorMsg = error.response?.data?.message || error.message || '转存失败'

    // 记录转存历史（失败）
    const historyRecord = {
      embyItemId: downloadItem.value.id,
      embyItemName: downloadItem.value.name,
      embyItemYear: downloadItem.value.productionYear,
      resourceId: resource.id?.toString(),
      resourceTitle: resource.name,
      resourceUrl: resource.url,
      matchScore: 100,
      cloudType: '115',
      cloudName: '115',
      parentId: '',
      transferStatus: 'failed',
      transferMessage: errorMsg
    }

    try {
      await saveTransferHistory(historyRecord)
    } catch (err) {
      console.error('保存转存历史失败:', err)
    }

    ElMessage.error(`转存失败: ${errorMsg}`)

    // 转存失败，继续走搜索逻辑
    ElMessage.info('将使用搜索引擎继续搜索资源')

    // 自动填充搜索关键词
    let keyword = downloadItem.value.originalTitle || downloadItem.value.name
    keyword = cleanSearchKeyword(keyword)
    searchKeywordInput.value = keyword

    downloadDialogVisible.value = true

    // 自动搜索
    handleSearchByKeyword()
  } finally {
    transferring.value = false
  }
}

// 批量下载当前页
const handleBatchDownload = async () => {
  // 过滤出电影和剧集（使用过滤后的列表）
  const downloadableItems = libraryItems.value.filter(
    item => item.type === 'Movie' || item.type === 'Series'
  )

  if (downloadableItems.length === 0) {
    ElMessage.warning('当前页没有可下载的媒体项（仅支持电影和剧集）')
    return
  }

  // 确认操作
  try {
    await ElMessageBox.confirm(
      `确定要批量下载当前页的 ${downloadableItems.length} 个媒体项吗？\n\n此操作将依次搜索并下载每个媒体项，可能需要较长时间。`,
      '批量下载确认',
      {
        confirmButtonText: '开始下载',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return // 用户取消
  }

  batchDownloading.value = true
  let successCount = 0
  let failCount = 0
  let skipCount = 0

  ElMessage.info(`开始批量下载，共 ${downloadableItems.length} 个媒体项`)

  for (let i = 0; i < downloadableItems.length; i++) {
    const item = downloadableItems[i]

    try {
      ElMessage.info(`[${i + 1}/${downloadableItems.length}] 正在处理: ${item.name}`)

      // 检查是否已经转存过（只跳过成功的）
      if (transferStatusMap.value[item.id] === 'success') {
        ElMessage.info(`跳过已转存: ${item.name}`)
        skipCount++
        continue
      }

      // 提取 TMDB ID
      let tmdbId = null
      if (item.providerIds && item.providerIds.Tmdb) {
        tmdbId = item.providerIds.Tmdb
      }

      // 先尝试从115资源库智能匹配
      let found115Resource = false
      try {
        const res = await smartSearch115(
          tmdbId,
          item.name,
          item.originalTitle,
          item.productionYear
        )

        if (res.data) {
          // 找到匹配的115资源
          const resource = res.data
          ElMessage.success(`找到115资源: ${resource.name}`)

          // 设置当前下载项（用于转存历史记录）
          downloadItem.value = item

          // 直接调用115转存API（不使用transfer115Resource，避免打开对话框）
          try {
            ElMessage.info(`正在转存115资源: ${resource.name}`)
            const transferRes = await transfer115(resource.url, resource.code)

            // 判断转存是否成功
            const isSuccess = transferRes.code === 200 || transferRes.success === true
            const errorMsg = transferRes.message || transferRes.msg || transferRes.error || (isSuccess ? '转存成功' : '转存失败')

            // 记录转存历史
            const historyRecord = {
              embyItemId: item.id,
              embyItemName: item.name,
              embyItemYear: item.productionYear,
              resourceId: resource.id?.toString(),
              resourceTitle: resource.name,
              resourceUrl: resource.url,
              matchScore: 100,
              cloudType: '115',
              cloudName: '115网盘',
              parentId: '0',
              transferStatus: isSuccess ? 'success' : 'failed',
              transferMessage: errorMsg
            }

            try {
              await saveTransferHistory(historyRecord)
            } catch (error) {
              console.error('保存转存历史失败:', error)
            }

            if (isSuccess) {
              ElMessage.success(`转存成功: ${item.name}`)
              successCount++
              found115Resource = true

              // 标记为已转存
              transferStatusMap.value[item.id] = 'success'

              // 等待1秒，避免请求过快
              await new Promise(resolve => setTimeout(resolve, 1000))
              continue
            } else {
              // 115转存失败，记录日志，继续走搜索引擎逻辑
              console.log(`115转存失败: ${errorMsg}，将使用搜索引擎搜索`)
              ElMessage.warning(`115转存失败: ${errorMsg.substring(0, 30)}，尝试搜索引擎...`)
            }
          } catch (error) {
            console.error(`115转存异常 [${item.name}]:`, error)
            const errorMsg = error.response?.data?.message || error.message || '转存异常'

            // 记录转存历史（异常）
            try {
              await saveTransferHistory({
                embyItemId: item.id,
                embyItemName: item.name,
                embyItemYear: item.productionYear,
                resourceId: resource.id?.toString(),
                resourceTitle: resource.name,
                resourceUrl: resource.url,
                matchScore: 100,
                cloudType: '115',
                cloudName: '115网盘',
                parentId: '0',
                transferStatus: 'failed',
                transferMessage: `转存异常: ${errorMsg}`
              })
            } catch (err) {
              console.error('保存转存历史失败:', err)
            }

            ElMessage.warning(`115转存异常，尝试搜索引擎...`)
          }
        }
      } catch (error) {
        console.error(`搜索115资源失败 [${item.name}]:`, error)
      }

      // 如果115资源库没有找到，走搜索引擎逻辑
      if (!found115Resource) {
        ElMessage.info(`115资源库未找到，使用搜索引擎搜索: ${item.name}`)

        try {
          // 设置当前下载项
          downloadItem.value = item

          // 构建搜索关键词
          let keyword = item.originalTitle || item.name
          keyword = cleanSearchKeyword(keyword)

          // 如果有年份，加上年份
          if (item.productionYear) {
            keyword += ` ${item.productionYear}`
          }

          console.log(`搜索关键词: ${keyword}`)

          // 调用搜索接口
          const searchRes = await searchByKeyword(keyword)

          if (!searchRes || !searchRes.data) {
            ElMessage.warning(`搜索失败: ${item.name}`)
            failCount++
            await new Promise(resolve => setTimeout(resolve, 1000))
            continue
          }

          // 解析搜索结果
          let results = []
          const channels = searchRes.data?.channels || searchRes.data?.channel_info_list

          if (channels && Array.isArray(channels)) {
            channels.forEach((channel) => {
              const channelInfo = channel.channelInfo || {}

              // 处理 messages 数组（阿里云盘格式）
              if (channel.messages && Array.isArray(channel.messages)) {
                channel.messages.forEach((message) => {
                  if (message.cloudLinks && message.cloudLinks.length > 0) {
                    message.cloudLinks.forEach((link, linkIndex) => {
                      const rawTitle = message.title || message.text
                      const cleanTitle = extractTitle(rawTitle)

                      results.push({
                        id: `${message.messageId}-${linkIndex}`,
                        messageId: message.messageId,
                        title: cleanTitle,
                        rawTitle: rawTitle,
                        url: link,
                        link: link,
                        cloudType: message.cloudType,
                        channelName: channelInfo.name || '未知来源'
                      })
                    })
                  }
                })
              }

              // 处理 list 数组（天翼云盘格式）
              if (channel.list && Array.isArray(channel.list)) {
                channel.list.forEach((listItem) => {
                  if (listItem.cloudLinks && listItem.cloudLinks.length > 0) {
                    listItem.cloudLinks.forEach((link, linkIndex) => {
                      const rawTitle = listItem.title || listItem.content
                      const cleanTitle = extractTitle(rawTitle)

                      results.push({
                        id: `${listItem.messageId}-${linkIndex}`,
                        messageId: listItem.messageId,
                        title: cleanTitle,
                        rawTitle: rawTitle,
                        url: link,
                        link: link,
                        cloudType: listItem.cloudType,
                        channelName: channelInfo.name || '未知来源'
                      })
                    })
                  }
                })
              }
            })
          }

          if (results.length === 0) {
            ElMessage.warning(`未找到搜索结果: ${item.name}`)
            failCount++
            await new Promise(resolve => setTimeout(resolve, 1000))
            continue
          }

          // 智能排序
          results = smartSortResults(results, keyword, item)

          // 按云盘类型分组
          const groupedResults = {}

          results.forEach(result => {
            const cloudType = result.cloudType
            if (!groupedResults[cloudType]) {
              groupedResults[cloudType] = []
            }
            groupedResults[cloudType].push(result)
          })

          console.log(`\n=== 云盘资源分布 ===`)
          Object.keys(groupedResults).forEach(cloudType => {
            console.log(`${cloudType}: ${groupedResults[cloudType].length} 个`)
          })

          // 获取云盘配置并按优先级排序
          const cloudConfigsWithPriority = cloudConfigs.value
            .filter(config => groupedResults[config.cloudType]) // 只保留有资源的云盘
            .sort((a, b) => (a.priority || 999) - (b.priority || 999)) // 按优先级排序

          console.log(`\n=== 云盘优先级排序 ===`)
          cloudConfigsWithPriority.forEach((config, i) => {
            console.log(`${i + 1}. ${config.cloudType} (优先级: ${config.priority || '未设置'}) - ${groupedResults[config.cloudType].length} 个资源`)
          })

          // 按优先级交错选择资源
          const sortedResults = []
          const indices = {}
          const maxRetries = 10 // 最多尝试10次

          cloudConfigsWithPriority.forEach(config => {
            indices[config.cloudType] = 0
          })

          // 循环选择，每轮按优先级顺序选择
          while (sortedResults.length < maxRetries) {
            let addedInThisRound = false

            for (const config of cloudConfigsWithPriority) {
              const cloudType = config.cloudType
              const resources = groupedResults[cloudType]

              if (indices[cloudType] < resources.length) {
                sortedResults.push(resources[indices[cloudType]++])
                addedInThisRound = true

                if (sortedResults.length >= maxRetries) {
                  break
                }
              }
            }

            // 如果这一轮没有添加任何资源，说明所有云盘都用完了
            if (!addedInThisRound) {
              break
            }
          }

          console.log(`\n=== 优先级排序后，将尝试 ${sortedResults.length} 个资源 ===`)
          sortedResults.forEach((r, i) => {
            console.log(`${i + 1}. [${r.cloudType}] ${r.title.substring(0, 40)}`)
          })

          // 尝试转存，按优先级顺序尝试
          let transferSuccess = false
          let lastError = ''

          for (let retryIndex = 0; retryIndex < sortedResults.length; retryIndex++) {
            const currentResult = sortedResults[retryIndex]

            if (retryIndex === 0) {
              ElMessage.info(`找到最佳匹配 [${currentResult.cloudType}]: ${currentResult.title.substring(0, 30)}`)
            } else {
              ElMessage.info(`尝试备选方案 ${retryIndex + 1} [${currentResult.cloudType}]: ${currentResult.title.substring(0, 30)}`)
            }

            // 获取云盘配置
            const cloudConfig = getCloudConfigByType(currentResult.cloudType)

            if (!cloudConfig || !cloudConfig.parentId) {
              lastError = `未配置 ${currentResult.cloudType} 云盘`
              console.log(`跳过: ${lastError}`)
              continue
            }

            try {
              // 转存
              const transferRes = await transferToAlipan(
                currentResult.url,
                cloudConfig.parentId,
                cloudConfig.cloudType
              )

              // 判断转存是否成功（兼容多种返回格式）
              const isSuccess = transferRes && (
                transferRes.success === true ||
                transferRes.code === 200 ||
                transferRes.code === 0 ||
                transferRes.status === 'success'
              )

              if (isSuccess) {
                ElMessage.success(`转存成功: ${item.name}${retryIndex > 0 ? ` (第${retryIndex + 1}次尝试)` : ''}`)
                successCount++
                transferSuccess = true

                // 标记为已转存
                transferStatusMap.value[item.id] = true

                // 保存转存历史
                try {
                  await saveTransferHistory({
                    embyItemId: item.id,
                    embyItemName: item.name,
                    embyItemYear: item.productionYear,
                    resourceId: currentResult.id?.toString(),
                    resourceTitle: currentResult.title,
                    resourceUrl: currentResult.url,
                    matchScore: currentResult.matchScore || 0,
                    cloudType: currentResult.cloudType,
                    cloudName: cloudConfig.name,
                    parentId: cloudConfig.parentId,
                    transferStatus: 'success',
                    transferMessage: `转存成功${retryIndex > 0 ? ` (第${retryIndex + 1}次尝试)` : ''}`
                  })
                } catch (error) {
                  console.error('保存转存历史失败:', error)
                }

                break // 转存成功，跳出重试循环
              } else {
                // 转存失败，记录错误信息
                lastError = transferRes?.message || transferRes?.msg || '转存失败'
                console.log(`转存失败 (尝试 ${retryIndex + 1}/${sortedResults.length}): ${lastError}`)

                // 记录失败的转存历史
                try {
                  await saveTransferHistory({
                    embyItemId: item.id,
                    embyItemName: item.name,
                    embyItemYear: item.productionYear,
                    resourceId: currentResult.id?.toString(),
                    resourceTitle: currentResult.title,
                    resourceUrl: currentResult.url,
                    matchScore: currentResult.matchScore || 0,
                    cloudType: currentResult.cloudType,
                    cloudName: cloudConfig.name,
                    parentId: cloudConfig.parentId,
                    transferStatus: 'failed',
                    transferMessage: `${lastError} (第${retryIndex + 1}次尝试)`
                  })
                } catch (error) {
                  console.error('保存转存历史失败:', error)
                }

                // 如果不是最后一次尝试，继续下一个
                if (retryIndex < sortedResults.length - 1) {
                  ElMessage.warning(`转存失败: ${lastError.substring(0, 50)}，尝试下一个...`)
                  await new Promise(resolve => setTimeout(resolve, 500))
                }
              }
            } catch (error) {
              lastError = error.response?.data?.message || error.message || '转存异常'
              console.error(`转存异常 (尝试 ${retryIndex + 1}/${sortedResults.length}):`, error)

              // 记录异常的转存历史
              try {
                await saveTransferHistory({
                  embyItemId: item.id,
                  embyItemName: item.name,
                  embyItemYear: item.productionYear,
                  resourceId: currentResult.id?.toString(),
                  resourceTitle: currentResult.title,
                  resourceUrl: currentResult.url,
                  matchScore: currentResult.matchScore || 0,
                  cloudType: currentResult.cloudType,
                  cloudName: cloudConfig.name,
                  parentId: cloudConfig.parentId,
                  transferStatus: 'failed',
                  transferMessage: `转存异常: ${lastError} (第${retryIndex + 1}次尝试)`
                })
              } catch (err) {
                console.error('保存转存历史失败:', err)
              }

              // 如果不是最后一次尝试，继续下一个
              if (retryIndex < sortedResults.length - 1) {
                ElMessage.warning(`转存异常，尝试下一个...`)
                await new Promise(resolve => setTimeout(resolve, 500))
              }
            }
          }

          // 所有尝试都失败
          if (!transferSuccess) {
            ElMessage.error(`转存失败 (已尝试${sortedResults.length}次): ${item.name} - ${lastError}`)
            failCount++
          }

          await new Promise(resolve => setTimeout(resolve, 1000))

        } catch (error) {
          console.error(`搜索引擎搜索失败 [${item.name}]:`, error)
          ElMessage.error(`搜索失败: ${item.name}`)
          failCount++
          await new Promise(resolve => setTimeout(resolve, 1000))
        }
      }

      // 等待1秒，避免请求过快
      await new Promise(resolve => setTimeout(resolve, 1000))

    } catch (error) {
      console.error(`处理失败 [${item.name}]:`, error)
      ElMessage.error(`处理失败: ${item.name}`)
      failCount++
    }
  }

  batchDownloading.value = false

  // 显示汇总结果
  ElMessageBox.alert(
    `批量下载完成！\n\n成功: ${successCount} 个\n失败: ${failCount} 个\n跳过: ${skipCount} 个`,
    '批量下载结果',
    {
      confirmButtonText: '确定',
      type: successCount > 0 ? 'success' : 'info'
    }
  )
}

// 批量直接下载当前页
const handleBatchDirectDownload = async () => {
  // 过滤出电影和剧集
  const downloadableItems = libraryItems.value.filter(
    item => item.type === 'Movie' || item.type === 'Series'
  )

  if (downloadableItems.length === 0) {
    ElMessage.warning('当前页没有可下载的媒体项（仅支持电影和剧集）')
    return
  }

  // 过滤掉已下载的
  const needDownloadItems = downloadableItems.filter(
    item => downloadStatusMap.value[item.id] !== 'success'
  )

  if (needDownloadItems.length === 0) {
    ElMessage.info('当前页所有媒体项都已下载')
    return
  }

  // 确认操作
  try {
    await ElMessageBox.confirm(
      `确定要批量直接下载 ${needDownloadItems.length} 个媒体项吗？\n\n` +
      `（已跳过 ${downloadableItems.length - needDownloadItems.length} 个已下载项）\n\n` +
      `下载任务将在后端执行，可在"任务管理"页面查看进度。`,
      '批量直接下载确认',
      {
        confirmButtonText: '开始下载',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return // 用户取消
  }

  batchDirectDownloading.value = true

  try {
    const itemIds = needDownloadItems.map(item => item.id)
    const res = await batchDownloadToServer(itemIds)

    if (res.data && res.data.status === 'started') {
      const taskId = res.data.taskId
      ElMessageBox.alert(
        `批量下载任务已创建（任务ID: ${taskId}），共 ${itemIds.length} 个媒体项。\n\n请前往"任务管理"页面查看实时下载进度。`,
        '下载任务已启动',
        {
          confirmButtonText: '知道了',
          type: 'success'
        }
      )
    } else {
      ElMessage.error('启动批量下载失败')
    }
  } catch (error) {
    console.error('批量下载失败:', error)
    ElMessage.error('批量下载失败: ' + (error.message || '未知错误'))
  } finally {
    batchDirectDownloading.value = false
  }
}

// 等待下载完成
const waitForDownloadComplete = async (itemId, itemName, maxWaitTime = 600000) => {
  // maxWaitTime: 最大等待时间，默认10分钟
  const startTime = Date.now()
  const checkInterval = 5000 // 每5秒检查一次

  while (Date.now() - startTime < maxWaitTime) {
    // 等待5秒
    await new Promise(resolve => setTimeout(resolve, checkInterval))

    // 检查下载状态
    try {
      const statusRes = await batchCheckDownloadStatus([itemId])
      if (statusRes.data && statusRes.data[itemId]) {
        const status = statusRes.data[itemId]

        if (status === 'success') {
          // 下载成功
          return true
        } else if (status === 'failed') {
          // 下载失败
          return false
        }
        // 如果是 'none' 或其他状态，继续等待
      }

      // 显示等待进度
      const elapsedSeconds = Math.floor((Date.now() - startTime) / 1000)
      console.log(`等待下载完成: ${itemName} (已等待 ${elapsedSeconds} 秒)`)
    } catch (error) {
      console.error('检查下载状态失败:', error)
    }
  }

  // 超时
  console.warn(`下载超时: ${itemName} (等待了 ${maxWaitTime / 1000} 秒)`)
  return false
}

// 手动转存（从表格点击）
const handleTransferToAlipan = async (row) => {
  // 检查是否有有效的链接
  const url = row.url || row.link || row.share_url
  if (!url) {
    ElMessage.error('该资源没有有效的分享链接')
    return
  }

  // 检查链接类型
  const isAlipan = url.includes('alipan.com') || url.includes('aliyundrive.com')
  const is189 = url.includes('cloud.189.cn')

  if (!isAlipan && !is189) {
    ElMessage.warning('该链接不是阿里云盘或天翼云盘分享链接，无法转存')
    return
  }

  // 如果是天翼云盘链接，提示用户
  if (is189) {
    ElMessage.info('检测到天翼云盘链接，将尝试转存')
  }

  selectedResult.value = row
  transferring.value = true

  try {
    await ElMessageBox.confirm(
      `确定要转存这个资源吗？\n\n标题: ${row.title}\n大小: ${row.size || '未知'}\n来源: ${row.channelName}\n链接: ${url}`,
      '确认转存',
      {
        confirmButtonText: '确定转存',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    // 根据资源的cloudType获取对应的云盘配置
    const cloudConfig = getCloudConfigByType(selectedResult.value.cloudType)

    if (!cloudConfig) {
      ElMessage.error(`未找到 cloudType="${selectedResult.value.cloudType}" 的云盘配置，请在智能搜索配置中添加`)
      return
    }

    if (!cloudConfig.parentId) {
      ElMessage.warning(`云盘配置"${cloudConfig.name}"未设置目录ID，请在智能搜索配置中完善`)
      return
    }

    console.log('使用云盘配置:', cloudConfig)

    // 调用转存接口
    const res = await transferToAlipan(url, cloudConfig.parentId, cloudConfig.cloudType)

    console.log('转存结果:', res)

    // 根据实际返回结果判断成功
    if (res.success || res.code === 0 || res.status === 'success') {
      ElMessage.success('转存成功！资源已保存到阿里云盘')
      downloadDialogVisible.value = false
    } else {
      const errorMsg = res.message || res.msg || res.error || '转存失败'
      ElMessage.error('转存失败: ' + errorMsg)
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('转存失败:', error)
      const errorMsg = error.response?.data?.message || error.message || '未知错误'
      ElMessage.error('转存失败: ' + errorMsg)
    }
  } finally {
    transferring.value = false
  }
}

// 搜索订阅
const handleSearchSubscribe = async () => {
  if (!subscribeId.value) {
    ElMessage.warning('请输入订阅ID')
    return
  }

  searchingSubscribe.value = true
  subscribeResult.value = null

  try {
    const res = await searchSubscribe(subscribeId.value)
    subscribeResult.value = res
    ElMessage.success('搜索成功')
  } catch (error) {
    console.error('搜索订阅失败:', error)
    ElMessage.error('搜索失败: ' + (error.message || '未知错误'))
  } finally {
    searchingSubscribe.value = false
  }
}

// 创建下载任务
const handleCreateDownloadTask = async (result) => {
  if (!result && !selectedResult.value) {
    ElMessage.warning('请先选择要下载的资源')
    return
  }

  const downloadData = result || selectedResult.value

  try {
    const movieName = downloadItem.value.name
    const year = downloadItem.value.productionYear || ''
    const taskName = `${movieName}${year ? ' (' + year + ')' : ''} - ${downloadData.title || downloadData.name}`

    // 创建包含下载信息的任务
    const taskData = [{
      id: downloadData.id || downloadData.subscribe_id,
      name: movieName,
      year: year,
      title: downloadData.title || downloadData.name,
      size: downloadData.size,
      resolution: downloadData.resolution,
      url: downloadData.url || downloadData.link
    }]

    const res = await createBatchTask({
      taskName,
      jsonData: JSON.stringify(taskData),
      delayMin: 0,
      delayMax: 0
    })

    const taskId = res.data

    // 立即启动任务
    await startTask(taskId)

    ElMessage.success('下载任务已创建并启动')
    downloadDialogVisible.value = false

    // 询问是否跳转到订阅搜索页面查看任务
    ElMessageBox.confirm(
      '下载任务已在后台执行，是否前往订阅搜索页面查看任务进度？',
      '提示',
      {
        confirmButtonText: '前往查看',
        cancelButtonText: '留在此页',
        type: 'success'
      }
    ).then(() => {
      // 跳转到订阅搜索页面
      window.location.href = '/#/subscribe-search'
    }).catch(() => {
      // 用户选择留在当前页面
    })
  } catch (error) {
    console.error('创建下载任务失败:', error)
    ElMessage.error('创建任务失败: ' + (error.message || '未知错误'))
  }
}

// 格式化JSON
const formatJson = (data) => {
  return JSON.stringify(data, null, 2)
}

// 初始化
onMounted(async () => {
  loadSmartSearchConfig() // 加载智能搜索配置
  await loadServerInfo()
  await loadLibraries()
  await loadGenres()
  await loadTags()
  await loadStudios()
})
</script>

<style scoped lang="scss">
.emby-container {
  padding: 20px;
  max-width: 1600px;
  margin: 0 auto;

  .header-card {
    margin-bottom: 20px;

    .header-content {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 16px;

      .header-left {
        display: flex;
        align-items: center;
        gap: 16px;
        flex-wrap: wrap;
        min-width: 0;
        flex: 1;

        h2 {
          margin: 0;
          font-size: 24px;
          font-weight: 600;
          color: #303133;
          white-space: nowrap;
        }

        .el-tag {
          flex-shrink: 0;
        }
      }

      .header-actions {
        display: flex;
        gap: 12px;
        flex-wrap: wrap;

        .el-button {
          height: 36px;
          padding: 0 16px;
          font-size: 14px;
          border-radius: 6px;
          transition: all 0.3s;

          .el-icon {
            margin-right: 6px;
          }

          &:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
          }
        }
      }
    }
  }

  .config-alert {
    margin-bottom: 20px;
    border-radius: 8px;

    .config-example {
      background: #f5f5f7;
      padding: 12px;
      border-radius: 6px;
      margin: 10px 0;
      font-size: 13px;
      line-height: 1.6;
    }

    code {
      background: #e8e8ed;
      padding: 2px 6px;
      border-radius: 3px;
      font-family: 'Monaco', 'Menlo', monospace;
    }
  }

  .sync-alert {
    margin-bottom: 20px;
    border-radius: 8px;

    p {
      margin: 8px 0;
      line-height: 1.6;
    }

    strong {
      color: #f56c6c;
      font-size: 15px;
    }
  }

  .info-card {
    margin-bottom: 20px;
    border-radius: 8px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  }

  .library-card {
    margin-bottom: 20px;
    border-radius: 8px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 600;
    font-size: 16px;
  }

  .library-name,
  .item-name {
    display: flex;
    align-items: center;
    gap: 8px;

    .el-icon {
      font-size: 18px;
    }
  }

  .path-item {
    padding: 2px 0;
    font-size: 12px;
    color: #606266;
    display: flex;
    align-items: center;
    gap: 4px;

    .el-icon {
      font-size: 14px;
      color: #909399;
    }
  }

  .text-muted {
    color: #909399;
  }

  .category-row {
    margin-bottom: 20px;

    .category-list {
      max-height: 300px;
      overflow-y: auto;
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      padding: 4px;

      .category-tag {
        margin: 0;
        cursor: pointer;
        transition: all 0.3s;

        &:hover {
          transform: translateY(-2px);
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
        }

        .count {
          margin-left: 4px;
          color: #909399;
          font-size: 12px;
        }
      }
    }
  }

  .items-dialog-content {
    .dialog-toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 15px;
      flex-wrap: wrap;
      gap: 12px;

      .total-count {
        font-size: 14px;
        color: #606266;
        font-weight: 500;
      }
    }

    .genre-tag {
      margin-right: 4px;
      margin-bottom: 4px;
    }

    .pagination-container {
      display: flex;
      justify-content: center;
      margin-top: 20px;
      padding: 10px 0;
    }

    // 优化表格按钮样式
    .el-table {
      .el-button--link {
        padding: 4px 8px;
        height: auto;
        font-size: 13px;

        .el-icon {
          margin-right: 4px;
        }
      }
    }
  }

  .detail-tag {
    margin-right: 8px;
    margin-bottom: 8px;
  }

  .overview-text {
    line-height: 1.8;
    white-space: pre-wrap;
  }

  .media-source {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 0;
    border-bottom: 1px solid #f5f5f7;

    &:last-child {
      border-bottom: none;
    }
  }

  .download-dialog-content {
    .search-results {
      margin-top: 20px;
    }

    .selected-result {
      margin-top: 20px;

      .result-preview {
        background: #f5f5f7;
        border-radius: 8px;
        padding: 12px;

        .json-preview {
          margin: 0;
          padding: 12px;
          background: #1d1d1f;
          color: #34c759;
          border-radius: 6px;
          overflow-x: auto;
          font-family: 'Courier New', Courier, monospace;
          font-size: 12px;
          line-height: 1.5;
          max-height: 300px;
          overflow-y: auto;
        }
      }
    }
  }

  // 剧集列表样式
  .episode-list {
    padding: 16px 24px;
    background: #fafafa;

    .loading-episodes {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 24px;
      color: #909399;

      .el-icon {
        font-size: 20px;
      }
    }

    .episodes-container {
      .episodes-header {
        margin-bottom: 12px;
        padding-bottom: 8px;
        border-bottom: 2px solid #e5e5e7;

        .episodes-title {
          font-size: 15px;
          font-weight: 600;
          color: #303133;
        }
      }

      .episode-name {
        display: flex;
        align-items: center;
        gap: 8px;

        .el-icon {
          font-size: 16px;
        }
      }
    }
  }
}

// 滚动条样式
.category-list::-webkit-scrollbar,
.json-preview::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.category-list::-webkit-scrollbar-track,
.json-preview::-webkit-scrollbar-track {
  background: #f5f5f7;
  border-radius: 3px;
}

.category-list::-webkit-scrollbar-thumb,
.json-preview::-webkit-scrollbar-thumb {
  background: #d1d1d6;
  border-radius: 3px;

  &:hover {
    background: #b0b0b5;
  }
}

// 响应式设计
@media (max-width: 768px) {
  .emby-container {
    padding: 12px;

    .header-card .header-content {
      flex-direction: column;
      align-items: flex-start;

      .header-actions {
        width: 100%;

        .el-button {
          flex: 1;
          min-width: 0;
        }
      }
    }

    .items-dialog-content .dialog-toolbar {
      flex-direction: column;
      align-items: flex-start;
    }
  }
}

// 优化对话框样式
:deep(.el-dialog) {
  border-radius: 12px;

  .el-dialog__header {
    padding: 20px 24px;
    border-bottom: 1px solid #f0f0f0;
  }

  .el-dialog__body {
    padding: 24px;
  }
}

// 优化表格样式
:deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;

  th {
    background-color: #fafafa;
    font-weight: 600;
  }

  .el-table__row {
    transition: background-color 0.3s;

    &:hover {
      background-color: #f5f7fa;
    }
  }
}

// 优化描述列表样式
:deep(.el-descriptions) {
  .el-descriptions__label {
    font-weight: 600;
    color: #606266;
  }

  .el-descriptions__content {
    color: #303133;
  }
}
</style>
