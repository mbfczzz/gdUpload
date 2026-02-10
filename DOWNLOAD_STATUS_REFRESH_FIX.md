# 下载状态刷新修复

## 问题描述

批量下载剧集成功后，前端列表中仍然显示"未下载"状态，需要手动刷新页面才能看到更新。

**日志显示：**
```
2026-02-06 02:25:57.176 [Emby-Download-2495158] INFO  com.gdupload.service.impl.EmbyServiceImpl - 电视剧: 整形过后
2026-02-06 02:25:57.176 [Emby-Download-2495158] INFO  com.gdupload.service.impl.EmbyServiceImpl - 保存目录: /data/emby/整形过后 (2025)
2026-02-06 02:25:57.176 [Emby-Download-2495158] INFO  com.gdupload.service.impl.EmbyServiceImpl - 总集数: 10
2026-02-06 02:25:57.176 [Emby-Download-2495158] INFO  com.gdupload.service.impl.EmbyServiceImpl - 成功: 10 集
2026-02-06 02:25:57.176 [Emby-Download-2495158] INFO  com.gdupload.service.impl.EmbyServiceImpl - 失败: 0 集
```

**问题：** 下载成功了，但列表里还是显示未下载。

## 根本原因

1. **后端正常保存下载历史**：`downloadSingleItem()` 方法在下载成功后会调用 `saveDownloadHistory()` 保存记录
2. **前端没有自动刷新**：下载是异步的（在后台进行），前端启动下载任务后就结束了，没有定期刷新下载状态
3. **展开剧集不刷新状态**：如果剧集列表已经加载过，再次展开时不会刷新下载状态

## 修复内容

### 1. 下载任务启动后自动刷新状态

**文件：** `frontend/src/views/EmbyManager.vue`

在 `handleDirectDownload()` 方法中，下载任务启动后添加定时器：

```javascript
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
}
```

**工作原理：**
- 下载任务启动后，每10秒自动刷新一次下载状态
- 持续5分钟（30次刷新）
- 自动更新 `downloadStatusMap`，前端会自动显示最新状态

### 2. 展开剧集时刷新下载状态

**文件：** `frontend/src/views/EmbyManager.vue`

修改 `handleExpandChange()` 方法：

```javascript
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

  // ... 首次加载剧集列表的逻辑
}
```

**工作原理：**
- 如果剧集列表已经加载过，再次展开时会刷新下载状态
- 用户可以通过折叠/展开剧集来手动刷新状态

## 部署步骤

### 1. 重新编译前端

```bash
cd frontend
npm run build
```

### 2. 上传到服务器

```bash
# 上传前端构建文件
scp -r dist/* user@server:/work/frontend/

# 或者如果使用 nginx
scp -r dist/* user@server:/var/www/html/
```

### 3. 清除浏览器缓存

```
Ctrl + Shift + R (强制刷新)
或
Ctrl + F5
```

## 使用说明

### 自动刷新

1. 点击"下载到服务器"按钮
2. 确认下载
3. 看到提示："下载任务已启动！下载完成后将自动刷新状态。"
4. **等待10-30秒**，下载状态会自动更新
5. 无需手动刷新页面

### 手动刷新

如果自动刷新没有生效，可以：

1. **折叠/展开剧集**：点击剧集行折叠，再展开，会刷新下载状态
2. **切换筛选条件**：切换下载状态筛选器（全部/已下载/未下载）
3. **刷新页面**：按 F5 刷新整个页面

## 测试步骤

### 1. 下载单个剧集

```bash
# 1. 在前端点击某个剧集的"下载到服务器"
# 2. 等待10秒
# 3. 观察剧集的下载状态是否自动变为"已下载"
```

### 2. 批量下载剧集

```bash
# 1. 在前端点击电视剧的"下载到服务器"（下载所有剧集）
# 2. 等待下载完成（查看后端日志）
# 3. 展开电视剧，查看各剧集的下载状态
# 4. 如果状态没更新，折叠再展开，应该会刷新状态
```

### 3. 验证后端日志

```bash
# SSH 登录服务器
ssh user@server

# 查看下载日志
tail -100 /work/nohup.out | grep -A 5 "下载完成"

# 应该看到：
# 下载完成！总大小: 980 MB
# 文件创建成功，实际文件名: 第 1 集.mp4
# 保存下载历史记录: success
```

### 4. 验证数据库

```bash
# 连接数据库
mysql -u root -p

# 查询下载历史
USE gdupload;
SELECT * FROM emby_download_history ORDER BY create_time DESC LIMIT 10;

# 应该看到最近的下载记录
# download_status 应该是 'success'
```

## 预期结果

### 成功标志

1. **下载任务启动**
   - 提示："下载任务已启动！下载完成后将自动刷新状态。"

2. **自动刷新状态**
   - 10-30秒后，下载状态自动变为"已下载"
   - 剧集名称旁边显示绿色的"已下载"标签

3. **展开剧集刷新**
   - 折叠/展开剧集时，下载状态会刷新
   - 控制台显示："刷新剧集下载状态完成，共 X 集"

### 失败标志

如果下载状态一直不更新：

1. **检查后端日志**
   ```bash
   tail -100 /work/nohup.out | grep "保存下载历史"
   ```
   - 如果没有"保存下载历史"日志，说明下载失败或没有保存记录

2. **检查数据库**
   ```sql
   SELECT * FROM emby_download_history WHERE emby_item_id = 'xxx';
   ```
   - 如果没有记录，说明下载历史没有保存

3. **检查浏览器控制台**
   - 打开浏览器开发者工具（F12）
   - 查看 Console 标签
   - 应该看到："刷新剧集下载状态完成"日志
   - 如果有错误，会显示错误信息

## 刷新机制说明

### 自动刷新时机

1. **下载任务启动后**：每10秒刷新一次，持续5分钟
2. **展开剧集时**：每次展开都会刷新下载状态

### 刷新范围

1. **当前页面的所有媒体项**：刷新当前显示的所有媒体项的下载状态
2. **展开的剧集**：刷新展开的剧集中所有Episode的下载状态

### 性能优化

1. **批量查询**：使用 `batchCheckDownloadStatus` 批量查询，减少请求次数
2. **定时器限制**：最多刷新30次（5分钟），避免无限刷新
3. **按需刷新**：只刷新当前页面显示的媒体项

## 总结

这次修复的核心：

1. **自动刷新机制**：下载任务启动后，每10秒自动刷新一次下载状态
2. **展开刷新**：每次展开剧集时都会刷新下载状态
3. **用户体验**：无需手动刷新页面，下载状态会自动更新

现在下载完成后，用户可以在10-30秒内看到下载状态的更新，无需手动刷新页面。
