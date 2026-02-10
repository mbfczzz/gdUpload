# 批量直接下载功能

## 功能说明

添加了批量直接下载功能，可以一键下载当前页的所有媒体项到服务器本地。

## 与批量搜索下载的区别

| 功能 | 批量搜索下载 | 批量直接下载 |
|------|------------|------------|
| 下载源 | 从云盘资源搜索并转存 | 直接从Emby服务器下载 |
| 适用场景 | 需要从云盘获取资源 | Emby服务器已有资源 |
| 速度 | 取决于云盘转存速度 | 取决于Emby服务器带宽 |
| 流量 | 云盘流量 | Emby服务器流量 |
| 执行方式 | 串行执行，间隔1秒 | 串行执行，间隔3秒 |

## 功能特点

### 1. 串行执行，避免流量异常

- 一个一个依次下载，不并发
- 每个下载之间间隔3秒
- 避免被识别为流量异常

### 2. 智能跳过

- 自动跳过已下载的媒体项
- 显示跳过原因

### 3. 自动刷新状态

- 下载任务启动后，每10秒自动刷新一次下载状态
- 持续10分钟（60次刷新）
- 下载完成后自动显示"已下载"标签

### 4. 详细进度提示

- 显示当前进度：`[1/10] 正在下载: xxx`
- 显示每个媒体项的下载结果
- 最后显示汇总结果

## 修改内容

### 1. 前端UI

**文件：** `frontend/src/views/EmbyManager.vue`

#### 1.1 添加按钮

```vue
<el-button
  type="warning"
  @click="handleBatchDirectDownload"
  :loading="batchDirectDownloading"
  :disabled="libraryItems.length === 0"
>
  <el-icon><Download /></el-icon>
  批量直接下载
</el-button>
```

**位置：** 在"批量搜索下载"按钮旁边

#### 1.2 添加状态变量

```javascript
const batchDirectDownloading = ref(false) // 批量直接下载状态
```

#### 1.3 添加处理方法

```javascript
const handleBatchDirectDownload = async () => {
  // 过滤出电影和剧集
  const downloadableItems = libraryItems.value.filter(
    item => item.type === 'Movie' || item.type === 'Series'
  )

  if (downloadableItems.length === 0) {
    ElMessage.warning('当前页没有可下载的媒体项（仅支持电影和剧集）')
    return
  }

  // 确认操作
  await ElMessageBox.confirm(
    `确定要批量直接下载当前页的 ${downloadableItems.length} 个媒体项吗？\n\n此操作将依次从Emby服务器下载每个媒体项到本地服务器，可能需要较长时间。\n\n为避免流量异常，将串行执行，每个下载之间间隔3秒。`,
    '批量直接下载确认',
    {
      confirmButtonText: '开始下载',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )

  batchDirectDownloading.value = true
  let successCount = 0
  let failCount = 0
  let skipCount = 0

  ElMessage.info(`开始批量直接下载，共 ${downloadableItems.length} 个媒体项`)

  for (let i = 0; i < downloadableItems.length; i++) {
    const item = downloadableItems[i]

    ElMessage.info(`[${i + 1}/${downloadableItems.length}] 正在下载: ${item.name}`)

    // 检查是否已经下载过
    if (downloadStatusMap.value[item.id] === 'success') {
      ElMessage.info(`跳过已下载: ${item.name}`)
      skipCount++
      continue
    }

    // 调用直接下载API
    try {
      const res = await downloadToServer(item.id)

      if (res.data && res.data.status === 'started') {
        ElMessage.success(`下载任务已启动: ${item.name}`)
        successCount++
      } else {
        ElMessage.error(`启动下载失败: ${item.name}`)
        failCount++
      }
    } catch (error) {
      ElMessage.error(`下载失败: ${item.name} - ${error.message || '未知错误'}`)
      failCount++
    }

    // 间隔3秒，避免流量异常
    if (i < downloadableItems.length - 1) {
      ElMessage.info('等待3秒后继续下一个...')
      await new Promise(resolve => setTimeout(resolve, 3000))
    }
  }

  batchDirectDownloading.value = false

  // 显示汇总结果
  ElMessageBox.alert(
    `批量直接下载完成！\n\n成功启动: ${successCount} 个\n失败: ${failCount} 个\n跳过: ${skipCount} 个\n\n下载任务已在后台执行，请查看后端日志了解进度。\n下载完成后状态会自动刷新（约10-30秒）。`,
    '批量直接下载结果',
    {
      confirmButtonText: '确定',
      type: successCount > 0 ? 'success' : 'info'
    }
  )

  // 启动定时器刷新下载状态
  if (successCount > 0) {
    ElMessage.info('已启动自动刷新，将每10秒检查一次下载状态')
    let refreshCount = 0
    const maxRefreshCount = 60 // 10分钟
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
        ElMessage.info('自动刷新已停止')
      }
    }, 10000) // 每10秒刷新一次
  }
}
```

## 部署步骤

### 1. 编译前端

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

### 1. 批量直接下载

1. 在媒体库列表中，选择要下载的媒体库
2. 可以使用筛选器筛选出未下载的媒体项
3. 点击"批量直接下载"按钮
4. 确认下载
5. 等待下载完成

### 2. 查看进度

**前端提示：**
```
开始批量直接下载，共 10 个媒体项
[1/10] 正在下载: 电影名称1
下载任务已启动: 电影名称1
等待3秒后继续下一个...
[2/10] 正在下载: 电影名称2
跳过已下载: 电影名称2
...
```

**后端日志：**
```bash
# SSH 登录服务器
ssh user@server

# 查看下载日志
tail -f /work/nohup.out

# 应该看到：
# 开始下载 Movie: 电影名称1
# 方式 1 成功！Emby响应成功，开始下载...
# 下载进度: 10% (100 MB / 1000 MB)
# 下载进度: 20% (200 MB / 1000 MB)
# ...
# 下载完成！总大小: 1000 MB
```

### 3. 查看结果

**汇总对话框：**
```
批量直接下载完成！

成功启动: 8 个
失败: 1 个
跳过: 1 个

下载任务已在后台执行，请查看后端日志了解进度。
下载完成后状态会自动刷新（约10-30秒）。
```

**自动刷新：**
- 下载任务启动后，前端会每10秒自动刷新一次下载状态
- 持续10分钟（60次刷新）
- 下载完成后，媒体项会自动显示"已下载"标签

## 注意事项

### 1. 串行执行

- 为避免流量异常，批量下载是串行执行的
- 每个下载之间间隔3秒
- 如果有10个媒体项，至少需要30秒才能全部启动

### 2. 后台下载

- 下载任务在后台执行
- 前端只是启动下载任务，不等待下载完成
- 实际下载进度需要查看后端日志

### 3. 自动刷新

- 下载任务启动后，前端会自动刷新下载状态
- 如果10分钟后还没下载完成，自动刷新会停止
- 可以手动刷新页面或折叠/展开剧集来刷新状态

### 4. 跳过已下载

- 已经下载过的媒体项会自动跳过
- 如果想重新下载，需要先删除服务器上的文件

### 5. 流量控制

- 间隔3秒是为了避免被识别为流量异常
- 如果需要更快的下载速度，可以减少间隔时间
- 但要注意可能会被限流或封禁

## 测试步骤

### 1. 筛选未下载的媒体项

```bash
# 1. 在前端选择一个媒体库
# 2. 使用下载状态筛选器选择"未下载"
# 3. 应该看到所有未下载的媒体项
```

### 2. 批量直接下载

```bash
# 1. 点击"批量直接下载"按钮
# 2. 确认下载
# 3. 观察前端提示信息
# 4. 等待所有下载任务启动完成
```

### 3. 查看后端日志

```bash
# SSH 登录服务器
ssh user@server

# 查看下载日志
tail -f /work/nohup.out

# 应该看到每个媒体项的下载进度
```

### 4. 验证下载结果

```bash
# 查看下载目录
ls -la /data/emby/

# 应该看到下载的文件
-rw-r--r-- 1 user user 1024000000 Feb  6 03:00 电影名称1 (2024).mp4
-rw-r--r-- 1 user user 1024000000 Feb  6 03:05 电影名称2 (2024).mp4
```

### 5. 验证下载状态

```bash
# 等待10-30秒，观察前端是否自动刷新
# 下载完成的媒体项应该显示"已下载"标签
```

## 性能优化建议

### 1. 调整间隔时间

如果服务器带宽充足，可以减少间隔时间：

```javascript
// 从3秒改为1秒
await new Promise(resolve => setTimeout(resolve, 1000))
```

### 2. 调整刷新频率

如果下载速度很快，可以增加刷新频率：

```javascript
// 从10秒改为5秒
}, 5000) // 每5秒刷新一次
```

### 3. 调整刷新时长

如果下载文件很大，可以延长刷新时长：

```javascript
// 从10分钟改为30分钟
const maxRefreshCount = 180 // 30分钟 = 180次 * 10秒
```

## 故障排查

### 1. 下载任务启动失败

**现象：** 提示"启动下载失败"

**原因：**
- Emby服务器连接失败
- 媒体项没有媒体源
- 权限不足

**解决方法：**
```bash
# 查看后端日志
tail -100 /work/nohup.out | grep "下载失败"

# 检查Emby连接
curl -I http://emby-server:8096
```

### 2. 下载状态不更新

**现象：** 下载完成后，前端仍显示"未下载"

**原因：**
- 自动刷新未生效
- 下载历史记录未保存

**解决方法：**
```bash
# 手动刷新页面
按 F5

# 或折叠/展开剧集
点击剧集行折叠，再展开

# 检查数据库
mysql -u root -p
USE gdupload;
SELECT * FROM emby_download_history ORDER BY create_time DESC LIMIT 10;
```

### 3. 文件下载不完整

**现象：** 文件大小为0或很小

**原因：**
- 下载过程中断
- 磁盘空间不足
- 网络问题

**解决方法：**
```bash
# 检查磁盘空间
df -h /data

# 检查文件大小
ls -lh /data/emby/*.mp4

# 删除不完整的文件
rm /data/emby/不完整的文件.mp4

# 重新下载
```

## 总结

这次添加的批量直接下载功能：

1. **串行执行**：一个一个下载，避免流量异常
2. **智能跳过**：自动跳过已下载的媒体项
3. **自动刷新**：下载完成后自动更新状态
4. **详细提示**：显示详细的进度和结果

现在用户可以一键批量下载当前页的所有媒体项，无需手动一个一个点击。
