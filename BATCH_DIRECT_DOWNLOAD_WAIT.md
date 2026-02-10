# 批量直接下载 - 等待完成版本

## 功能说明

修改了批量直接下载功能，现在会等待每个下载完成后再开始下一个，真正实现串行下载。

## 修改内容

### 1. 等待下载完成

**之前的行为：**
- 启动下载任务后立即继续下一个
- 所有下载任务几乎同时在后台执行
- 间隔3秒只是启动任务的间隔

**现在的行为：**
- 启动下载任务后，等待下载完成
- 每5秒检查一次下载状态
- 下载完成（成功或失败）后才开始下一个
- 最长等待10分钟，超时后继续下一个

### 2. 新增 waitForDownloadComplete 方法

```javascript
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
```

**工作原理：**
1. 启动下载任务
2. 每5秒检查一次下载状态
3. 如果状态变为 `success`，返回 true
4. 如果状态变为 `failed`，返回 false
5. 如果10分钟后还没完成，返回 false（超时）

### 3. 修改批量下载流程

```javascript
// 调用直接下载API
const res = await downloadToServer(item.id)

if (res.data && res.data.status === 'started') {
  ElMessage.success(`下载任务已启动: ${item.name}`)

  // 等待下载完成
  ElMessage.info(`等待下载完成: ${item.name}`)
  const downloadSuccess = await waitForDownloadComplete(item.id, item.name)

  if (downloadSuccess) {
    ElMessage.success(`✓ 下载完成: ${item.name}`)
    successCount++
    // 更新下载状态
    downloadStatusMap.value[item.id] = 'success'
  } else {
    ElMessage.error(`✗ 下载失败或超时: ${item.name}`)
    failCount++
    downloadStatusMap.value[item.id] = 'failed'
  }
}

// 间隔2秒后继续下一个
if (i < downloadableItems.length - 1) {
  ElMessage.info('等待2秒后继续下一个...')
  await new Promise(resolve => setTimeout(resolve, 2000))
}
```

### 4. 移除自动刷新定时器

因为现在是等待下载完成后才继续，所以不需要后台定时器刷新状态了。

## 使用流程

### 1. 批量直接下载

```
1. 点击"批量直接下载"按钮
2. 确认下载

前端提示：
开始批量直接下载，共 10 个媒体项
[1/10] 正在处理: 电影名称1
下载任务已启动: 电影名称1
等待下载完成: 电影名称1
(每5秒检查一次状态)
✓ 下载完成: 电影名称1
等待2秒后继续下一个...
[2/10] 正在处理: 电影名称2
跳过已下载: 电影名称2
[3/10] 正在处理: 电影名称3
下载任务已启动: 电影名称3
等待下载完成: 电影名称3
✓ 下载完成: 电影名称3
...
批量直接下载完成！
成功: 8 个
失败: 1 个
跳过: 1 个
```

### 2. 查看控制台日志

打开浏览器开发者工具（F12），查看 Console 标签：

```
等待下载完成: 电影名称1 (已等待 5 秒)
等待下载完成: 电影名称1 (已等待 10 秒)
等待下载完成: 电影名称1 (已等待 15 秒)
...
等待下载完成: 电影名称1 (已等待 120 秒)
```

### 3. 查看后端日志

```bash
# SSH 登录服务器
ssh user@server

# 查看下载日志
tail -f /work/nohup.out

# 应该看到：
# 开始下载 Movie: 电影名称1
# 下载进度: 10% (100 MB / 1000 MB)
# 下载进度: 20% (200 MB / 1000 MB)
# ...
# 下载完成！总大小: 1000 MB
# 保存下载历史记录: success
```

## 时间估算

假设有10个媒体项，每个文件1GB：

**之前的方式（并发）：**
- 启动时间：10个 × 3秒 = 30秒
- 下载时间：取决于最慢的那个（假设5分钟）
- 总时间：约5分30秒

**现在的方式（串行）：**
- 第1个：启动 + 下载 = 5分钟
- 第2个：间隔2秒 + 启动 + 下载 = 5分2秒
- ...
- 总时间：10个 × 5分钟 + 9个 × 2秒 = 约50分钟

**优点：**
- 不会同时占用大量带宽
- 避免流量异常
- 更稳定可靠

**缺点：**
- 总时间更长
- 需要保持浏览器打开

## 参数调整

### 1. 调整检查间隔

如果想更快地检测到下载完成：

```javascript
const checkInterval = 3000 // 改为每3秒检查一次
```

### 2. 调整超时时间

如果文件很大，需要更长的等待时间：

```javascript
const downloadSuccess = await waitForDownloadComplete(item.id, item.name, 1800000) // 30分钟
```

### 3. 调整下载间隔

如果想更快地开始下一个下载：

```javascript
await new Promise(resolve => setTimeout(resolve, 1000)) // 改为1秒
```

## 注意事项

### 1. 保持浏览器打开

- 批量下载期间，必须保持浏览器标签页打开
- 如果关闭标签页，批量下载会中断
- 已经启动的下载任务会继续在后台执行

### 2. 超时处理

- 每个下载最长等待10分钟
- 超时后会标记为失败，继续下一个
- 可以根据文件大小调整超时时间

### 3. 网络中断

- 如果网络中断，检查状态会失败
- 但不会影响后端的下载任务
- 网络恢复后，状态检查会继续

### 4. 下载失败

- 如果下载失败，会立即继续下一个
- 不会等待10分钟超时
- 失败的媒体项可以手动重新下载

## 故障排查

### 1. 一直等待不继续

**现象：** 卡在"等待下载完成"，一直不继续下一个

**原因：**
- 后端下载任务卡住
- 下载历史记录没有保存
- 网络问题导致状态检查失败

**解决方法：**
```bash
# 查看后端日志
tail -f /work/nohup.out

# 如果下载卡住，重启后端
cd /work
./stop.sh
./start.sh

# 如果是网络问题，检查网络连接
ping emby-server
```

### 2. 显示超时但文件已下载

**现象：** 提示"下载失败或超时"，但文件已经下载成功

**原因：**
- 下载完成了，但下载历史记录保存失败
- 超时时间设置太短

**解决方法：**
```bash
# 检查文件是否存在
ls -la /data/emby/

# 如果文件存在，手动标记为已下载
# 在前端点击"标记已下载"按钮

# 或者调整超时时间
const downloadSuccess = await waitForDownloadComplete(item.id, item.name, 1800000) // 30分钟
```

### 3. 批量下载中断

**现象：** 批量下载进行到一半就停止了

**原因：**
- 浏览器标签页被关闭
- 浏览器崩溃
- 电脑休眠

**解决方法：**
```bash
# 查看哪些已经下载成功
# 使用下载状态筛选器选择"未下载"
# 重新批量下载剩余的媒体项
```

## 性能对比

### 并发下载（之前）

**优点：**
- 总时间短
- 充分利用带宽

**缺点：**
- 可能被识别为流量异常
- 占用大量带宽
- 可能导致其他服务变慢

### 串行下载（现在）

**优点：**
- 避免流量异常
- 带宽占用平稳
- 不影响其他服务

**缺点：**
- 总时间长
- 需要保持浏览器打开

## 建议

### 1. 小批量下载

- 每次下载5-10个媒体项
- 避免一次下载太多

### 2. 夜间下载

- 在网络空闲时段进行批量下载
- 避免影响白天的使用

### 3. 分批下载

- 先下载重要的媒体项
- 不重要的可以慢慢下载

### 4. 监控进度

- 打开浏览器控制台查看详细日志
- 定期查看后端日志了解下载进度

## 总结

这次修改的核心：

1. **真正的串行下载**：等待一个完成后再开始下一个
2. **轮询检查状态**：每5秒检查一次下载状态
3. **超时保护**：最长等待10分钟，避免无限等待
4. **实时反馈**：显示详细的下载进度和结果

现在批量下载会真正等待每个下载完成，避免同时占用大量带宽，更加稳定可靠！
