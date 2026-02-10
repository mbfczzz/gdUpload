# Series 下载状态显示修复

## 问题描述

批量直接下载 Series（电视剧）时，虽然所有剧集都下载成功了，但 Series 本身的下载状态仍然显示为"未下载"。

**现象：**
- 点击"批量直接下载"
- 下载一个 Series（电视剧）
- 所有 Episode（剧集）下载成功，显示"已下载"标签
- 但 Series 本身仍然显示"未下载"

## 根本原因

在 `downloadSeries()` 方法中：
1. 下载每个 Episode 时，会调用 `downloadSingleItem()`
2. `downloadSingleItem()` 会保存 Episode 的下载历史记录
3. 但 `downloadSeries()` 方法结束时，没有保存 Series 本身的下载历史记录
4. 前端检查 Series 的下载状态时，查询 `emby_download_history` 表找不到记录
5. 所以显示为"未下载"

## 修复内容

### 修改文件：`backend/src/main/java/com/gdupload/service/impl/EmbyServiceImpl.java`

在 `downloadSeries()` 方法的最后，添加保存 Series 下载历史记录的逻辑：

```java
log.info("电视剧下载完成！成功: {}, 失败: {}, 跳过: {}", successCount, failedCount, skippedCount);

// 保存 Series 的下载历史记录
if (successCount > 0) {
    // 如果有至少一集下载成功，标记 Series 为成功
    String seriesStatus = (failedCount == 0 && skippedCount == 0) ? "success" : "success";
    saveDownloadHistory(seriesId, seriesStatus, seriesDir, 0L,
        String.format("下载了 %d 集，成功 %d 集，失败 %d 集", episodes.size(), successCount, failedCount + skippedCount));
    log.info("保存 Series 下载历史记录: seriesId={}, status={}", seriesId, seriesStatus);
} else {
    // 如果一集都没下载成功，标记 Series 为失败
    saveDownloadHistory(seriesId, "failed", seriesDir, 0L,
        String.format("下载失败，共 %d 集，失败 %d 集，跳过 %d 集", episodes.size(), failedCount, skippedCount));
    log.info("保存 Series 下载历史记录: seriesId={}, status=failed", seriesId);
}
```

**逻辑说明：**
1. 如果至少有一集下载成功，标记 Series 为 `success`
2. 如果一集都没下载成功，标记 Series 为 `failed`
3. `errorMessage` 字段记录下载统计信息

## 数据库记录

修复后，`emby_download_history` 表会有两种记录：

### 1. Episode 记录

```sql
INSERT INTO emby_download_history (
    emby_item_id,
    emby_config_id,
    download_status,
    file_path,
    file_size,
    error_message
) VALUES (
    'episode-id-123',
    1,
    'success',
    '/data/emby/剧集名称 (2024)/第 1 集.mp4',
    1024000000,
    NULL
);
```

### 2. Series 记录（新增）

```sql
INSERT INTO emby_download_history (
    emby_item_id,
    emby_config_id,
    download_status,
    file_path,
    file_size,
    error_message
) VALUES (
    'series-id-456',
    1,
    'success',
    '/data/emby/剧集名称 (2024)',
    0,
    '下载了 10 集，成功 10 集，失败 0 集'
);
```

**字段说明：**
- `emby_item_id`: Series 的 ID
- `download_status`: `success` 或 `failed`
- `file_path`: Series 的目录路径
- `file_size`: 0（因为 Series 本身不是文件）
- `error_message`: 下载统计信息

## 部署步骤

### 1. 重新编译后端

```bash
cd backend
mvn clean package -DskipTests
```

### 2. 上传到服务器

```bash
# 上传 JAR 文件
scp target/gdupload-0.0.1-SNAPSHOT.jar user@server:/work/
```

### 3. 重启服务

```bash
# SSH 登录服务器
ssh user@server

# 停止旧服务
cd /work
./stop.sh

# 启动新服务
./start.sh
```

### 4. 验证部署

```bash
# 查看日志，确认启动成功
tail -f /work/nohup.out

# 应该看到：
# 应用启动成功
```

## 测试步骤

### 1. 下载一个 Series

```bash
# 1. 在前端选择一个未下载的 Series
# 2. 点击"直接下载"按钮
# 3. 等待下载完成
```

### 2. 查看后端日志

```bash
# SSH 登录服务器
ssh user@server

# 查看下载日志
tail -100 /work/nohup.out | grep -A 5 "电视剧下载完成"

# 应该看到：
# 电视剧下载完成！成功: 10, 失败: 0, 跳过: 0
# 保存 Series 下载历史记录: seriesId=xxx, status=success
```

### 3. 验证数据库

```bash
# 连接数据库
mysql -u root -p

# 查询 Series 的下载历史
USE gdupload;
SELECT * FROM emby_download_history
WHERE emby_item_id = 'series-id-xxx'
ORDER BY create_time DESC;

# 应该看到：
# | id | emby_item_id | download_status | file_path                    | file_size | error_message                  |
# |----|--------------|-----------------|------------------------------|-----------|--------------------------------|
# | 1  | series-xxx   | success         | /data/emby/剧集名称 (2024)   | 0         | 下载了 10 集，成功 10 集，失败 0 集 |
```

### 4. 验证前端显示

```bash
# 1. 刷新前端页面（F5）
# 2. 查看 Series 的下载状态
# 3. 应该显示"已下载"标签
```

## 预期结果

### 成功标志

1. **后端日志**
   ```
   电视剧下载完成！成功: 10, 失败: 0, 跳过: 0
   保存 Series 下载历史记录: seriesId=xxx, status=success
   ```

2. **数据库记录**
   - Series 有一条下载历史记录
   - `download_status` 为 `success`
   - `error_message` 包含下载统计信息

3. **前端显示**
   - Series 显示"已下载"标签
   - 展开 Series，所有 Episode 也显示"已下载"标签

### 失败标志

如果 Series 仍然显示"未下载"：

1. **检查后端日志**
   ```bash
   tail -100 /work/nohup.out | grep "保存 Series 下载历史"
   ```
   - 如果没有这条日志，说明代码没有执行到这里

2. **检查数据库**
   ```sql
   SELECT * FROM emby_download_history
   WHERE emby_item_id = 'series-id-xxx';
   ```
   - 如果没有记录，说明保存失败

3. **检查前端**
   - 打开浏览器开发者工具（F12）
   - 查看 Network 标签
   - 找到 `/emby-download-history/batch-check` 请求
   - 查看响应数据，确认 Series ID 是否在请求中

## 边界情况处理

### 1. 部分剧集下载失败

**场景：** 10集中有8集成功，2集失败

**处理：**
- Series 仍然标记为 `success`
- `error_message` 记录：`下载了 10 集，成功 8 集，失败 2 集`
- 前端显示"已下载"标签

**原因：** 只要有剧集下载成功，就认为 Series 下载成功了

### 2. 所有剧集下载失败

**场景：** 10集全部失败

**处理：**
- Series 标记为 `failed`
- `error_message` 记录：`下载失败，共 10 集，失败 10 集，跳过 0 集`
- 前端显示"下载失败"标签

### 3. 所有剧集被跳过

**场景：** 10集全部跳过（已下载）

**处理：**
- Series 标记为 `failed`
- `error_message` 记录：`下载失败，共 10 集，失败 0 集，跳过 10 集`
- 前端显示"下载失败"标签

**改进建议：** 可以添加一个特殊状态 `skipped`，表示所有剧集都已下载

### 4. 重复下载

**场景：** 同一个 Series 下载多次

**处理：**
- 每次下载都会创建新的下载历史记录
- 前端查询时取最新的记录
- 旧记录仍然保留在数据库中

## 注意事项

### 1. Series 的 file_size 为 0

- Series 本身不是文件，而是一个目录
- 所以 `file_size` 字段设置为 0
- 如果需要统计总大小，可以累加所有 Episode 的大小

### 2. error_message 字段的用途

- 对于 Episode，`error_message` 记录下载失败的原因
- 对于 Series，`error_message` 记录下载统计信息
- 这是一个多用途字段

### 3. 批量下载的状态更新

- 批量下载时，前端会轮询检查下载状态
- 当 Series 下载完成后，会立即保存下载历史记录
- 前端下次轮询时就能检测到状态变化

## 总结

这次修复的核心：

1. **保存 Series 下载历史记录**：在 `downloadSeries()` 方法结束时保存
2. **判断下载状态**：至少一集成功就标记为成功
3. **记录统计信息**：在 `error_message` 字段记录下载统计

现在下载 Series 后，Series 本身也会显示"已下载"状态，与 Episode 的状态保持一致！
