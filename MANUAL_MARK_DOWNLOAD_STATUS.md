# 手动标记下载状态功能

## 功能说明

添加了手动标记下载状态的功能，允许用户手动将媒体项标记为"已下载"。

## 使用场景

1. **已经下载但没有记录**：文件已经通过其他方式下载到服务器，但系统中没有下载记录
2. **跳过下载**：不想下载某个媒体项，但想标记为已下载以便筛选
3. **测试功能**：测试下载状态显示和筛选功能

## 修改内容

### 1. 后端API

**文件：** `backend/src/main/java/com/gdupload/controller/EmbyDownloadHistoryController.java`

添加了 `markDownloadStatus` 接口：

```java
/**
 * 手动标记下载状态
 *
 * @param embyItemId 媒体项ID
 * @param status 下载状态（success/failed）
 * @return 操作结果
 */
@PostMapping("/mark-status")
public Result<String> markDownloadStatus(@RequestParam String embyItemId,
                                          @RequestParam String status) {
    log.info("手动标记下载状态: embyItemId={}, status={}", embyItemId, status);

    // 验证状态值
    if (!"success".equals(status) && !"failed".equals(status)) {
        return Result.error("无效的状态值，只能是 success 或 failed");
    }

    // 创建下载历史记录
    EmbyDownloadHistory history = new EmbyDownloadHistory();
    history.setEmbyItemId(embyItemId);
    history.setEmbyConfigId(1L); // 默认配置ID
    history.setDownloadStatus(status);
    history.setFilePath("手动标记");
    history.setFileSize(0L);
    history.setErrorMessage(null);

    int result = downloadHistoryMapper.insert(history);

    if (result > 0) {
        log.info("手动标记成功: embyItemId={}, status={}", embyItemId, status);
        return Result.success("标记成功");
    } else {
        log.error("手动标记失败: embyItemId={}, status={}", embyItemId, status);
        return Result.error("标记失败");
    }
}
```

**特点：**
- 支持标记为 `success` 或 `failed`
- 文件路径记录为"手动标记"，便于区分
- 文件大小记录为 0

### 2. 前端API

**文件：** `frontend/src/api/downloadHistory.js`

添加了 `markDownloadStatus` 方法：

```javascript
/**
 * 手动标记下载状态
 */
export function markDownloadStatus(embyItemId, status) {
  return request({
    url: '/emby-download-history/mark-status',
    method: 'post',
    params: {
      embyItemId,
      status
    }
  })
}
```

### 3. 前端UI

**文件：** `frontend/src/views/EmbyManager.vue`

#### 3.1 添加按钮

在操作列中添加"标记已下载"按钮：

```vue
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
```

**显示逻辑：**
- 只有当下载状态不是"已下载"时才显示按钮
- 已经标记为"已下载"的媒体项不显示按钮

#### 3.2 添加处理方法

```javascript
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
```

**工作流程：**
1. 弹出确认对话框
2. 用户确认后调用API标记状态
3. 标记成功后更新本地状态
4. 前端立即显示"已下载"标签

#### 3.3 导入图标

```javascript
import { Check } from '@element-plus/icons-vue'
```

## 部署步骤

### 1. 编译后端

```bash
cd backend
mvn clean package -DskipTests
```

### 2. 编译前端

```bash
cd frontend
npm run build
```

### 3. 上传到服务器

```bash
# 上传后端
scp backend/target/gdupload-0.0.1-SNAPSHOT.jar user@server:/work/

# 上传前端
scp -r frontend/dist/* user@server:/work/frontend/
```

### 4. 重启服务

```bash
# SSH 登录服务器
ssh user@server

# 重启后端
cd /work
./stop.sh
./start.sh

# 如果使用 nginx，重启 nginx
sudo systemctl restart nginx
```

## 使用说明

### 1. 标记单个媒体项

1. 在媒体列表中找到要标记的媒体项
2. 点击"标记已下载"按钮
3. 确认标记
4. 标记成功后，媒体项会显示"已下载"标签
5. "标记已下载"按钮会消失

### 2. 筛选已标记的媒体项

1. 使用下载状态筛选器
2. 选择"已下载"
3. 列表会显示所有已下载的媒体项（包括手动标记的）

### 3. 查看标记记录

手动标记的记录会保存到数据库中：

```sql
SELECT * FROM emby_download_history
WHERE file_path = '手动标记'
ORDER BY create_time DESC;
```

**字段说明：**
- `emby_item_id`: 媒体项ID
- `download_status`: `success`
- `file_path`: `手动标记`
- `file_size`: `0`
- `create_time`: 标记时间

## 测试步骤

### 1. 标记功能测试

```bash
# 1. 在前端找一个未下载的媒体项
# 2. 点击"标记已下载"按钮
# 3. 确认标记
# 4. 观察是否显示"已下载"标签
# 5. 观察"标记已下载"按钮是否消失
```

### 2. 筛选功能测试

```bash
# 1. 标记几个媒体项为已下载
# 2. 使用下载状态筛选器选择"已下载"
# 3. 观察列表是否只显示已下载的媒体项
```

### 3. 数据库验证

```bash
# SSH 登录服务器
ssh user@server

# 连接数据库
mysql -u root -p

# 查询手动标记的记录
USE gdupload;
SELECT * FROM emby_download_history
WHERE file_path = '手动标记'
ORDER BY create_time DESC
LIMIT 10;

# 应该看到：
# | id | emby_item_id | download_status | file_path | file_size | create_time |
# |----|--------------|-----------------|-----------|-----------|-------------|
# | 1  | abc123       | success         | 手动标记  | 0         | 2026-02-06  |
```

## 注意事项

### 1. 标记不可撤销

- 标记后无法直接撤销
- 如果需要撤销，需要手动删除数据库记录：
  ```sql
  DELETE FROM emby_download_history
  WHERE emby_item_id = 'xxx'
  AND file_path = '手动标记';
  ```

### 2. 标记不会创建文件

- 手动标记只是在数据库中创建记录
- 不会在服务器上创建实际文件
- `file_path` 字段记录为"手动标记"，便于区分

### 3. 标记会影响筛选

- 手动标记的媒体项会出现在"已下载"筛选结果中
- 与实际下载的媒体项没有区别

### 4. 可以重复标记

- 同一个媒体项可以多次标记
- 系统会取最新的标记记录
- 旧的标记记录仍然保留在数据库中

## 扩展功能

### 1. 添加"标记失败"按钮

如果需要标记为"下载失败"，可以添加类似的按钮：

```vue
<el-button
  v-if="downloadStatusMap[row.id] !== 'failed'"
  type="danger"
  link
  size="small"
  @click="handleMarkDownloadFailed(row)"
>
  <el-icon><Close /></el-icon>
  标记失败
</el-button>
```

```javascript
const handleMarkDownloadFailed = async (item) => {
  // 类似 handleMarkDownloadSuccess，但 status 参数为 'failed'
  await markDownloadStatus(item.id, 'failed')
}
```

### 2. 批量标记

可以添加批量标记功能：

```javascript
const handleBatchMarkSuccess = async (items) => {
  for (const item of items) {
    await markDownloadStatus(item.id, 'success')
  }
}
```

### 3. 撤销标记

可以添加撤销标记功能：

```java
@DeleteMapping("/unmark")
public Result<String> unmarkDownloadStatus(@RequestParam String embyItemId) {
    LambdaQueryWrapper<EmbyDownloadHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(EmbyDownloadHistory::getEmbyItemId, embyItemId)
            .eq(EmbyDownloadHistory::getFilePath, "手动标记");

    int result = downloadHistoryMapper.delete(wrapper);
    return result > 0 ? Result.success("撤销成功") : Result.error("撤销失败");
}
```

## 总结

这次添加的手动标记功能：

1. **简单易用**：一键标记，立即生效
2. **可追溯**：所有标记记录保存在数据库中
3. **可筛选**：标记后的媒体项可以通过筛选器查看
4. **可扩展**：可以轻松添加标记失败、批量标记、撤销标记等功能

现在用户可以手动管理下载状态，不再依赖实际的下载操作。
