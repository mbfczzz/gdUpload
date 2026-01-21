# 重新部署检查清单

## 部署前准备

### 1. 数据库迁移
**必须执行** - 在启动新版本代码之前执行数据库迁移脚本

```bash
# 连接到MySQL数据库
mysql -u your_username -p gd_upload_manager

# 执行迁移脚本
source F:\cluade\database\migration_rolling_window.sql
```

**迁移脚本做了什么:**
- ✅ 删除固定时间重置的定时任务事件 `evt_reset_daily_quota`
- ✅ 删除固定时间重置的存储过程 `sp_reset_account_quota`
- ✅ 删除 `quota_reset_time` 字段(不再需要)
- ✅ 重置所有账号的 `used_quota` 和 `remaining_quota` 为初始值
- ✅ 添加性能优化索引 `idx_account_time_status`
- ✅ 重新创建 `v_account_usage` 视图使用滚动24小时窗口
- ✅ 自动恢复状态为"已达上限"但配额已释放的账号

### 2. 代码变更确认

**后端变更:**
- ✅ `FileInfoServiceImpl.java` - 添加了上传记录插入逻辑
- ✅ `GdAccountServiceImpl.java` - 改为实时计算配额
- ✅ `GdAccountMapper.java` - 使用滚动24小时窗口SQL查询
- ✅ `UploadRecordMapper.java` - 添加24小时窗口查询方法
- ✅ 删除了 `AccountQuotaScheduler.java` (不再需要)

**前端变更:**
- ✅ `Account.vue` - 移除重置时间列,添加滚动窗口说明
- ✅ 添加30秒自动刷新功能

## 部署步骤

### 1. 停止当前运行的服务
```bash
# 如果有正在运行的上传任务,建议等待任务完成或暂停任务
# 停止后端服务
# 停止前端服务
```

### 2. 备份数据库
```bash
mysqldump -u your_username -p gd_upload_manager > backup_before_migration_$(date +%Y%m%d_%H%M%S).sql
```

### 3. 执行数据库迁移
```bash
mysql -u your_username -p gd_upload_manager < F:\cluade\database\migration_rolling_window.sql
```

### 4. 部署新版本代码

**后端部署:**
```bash
cd F:\cluade\backend
mvn clean package -DskipTests
# 启动新版本后端服务
```

**前端部署:**
```bash
cd F:\cluade\frontend
npm run build
# 部署前端静态文件
```

### 5. 启动服务
```bash
# 启动后端服务
# 启动前端服务
```

## 部署后验证

### 1. 检查数据库迁移结果
```sql
-- 验证quota_reset_time字段已删除
DESCRIBE gd_account;

-- 验证索引已创建
SHOW INDEX FROM upload_record WHERE Key_name = 'idx_account_time_status';

-- 验证定时任务已删除
SHOW EVENTS WHERE Name = 'evt_reset_daily_quota';

-- 查看账号状态
SELECT
    account_name,
    status,
    daily_limit,
    used_quota,
    remaining_quota
FROM gd_account;
```

### 2. 检查账号管理页面
- ✅ 访问前端账号管理页面
- ✅ 确认"当前时间"显示正确
- ✅ 确认没有"重置时间"列
- ✅ 确认"已使用"和"剩余配额"显示正确
- ✅ 确认状态显示正确(启用/禁用/已达上限)
- ✅ 确认滚动24小时窗口说明显示正确
- ✅ 等待30秒,确认自动刷新功能正常

### 3. 测试上传功能
- ✅ 创建新的上传任务
- ✅ 上传几个测试文件
- ✅ 确认上传记录正确插入到 `upload_record` 表
- ✅ 确认账号的"已使用"配额实时更新
- ✅ 确认账号的"剩余配额"实时更新

### 4. 验证滚动窗口机制
```sql
-- 查看过去24小时的上传记录
SELECT
    a.account_name,
    COUNT(*) as upload_count,
    SUM(r.upload_size) as total_size,
    a.daily_limit,
    a.daily_limit - SUM(r.upload_size) as remaining
FROM upload_record r
JOIN gd_account a ON r.account_id = a.id
WHERE r.upload_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
  AND r.status = 1
GROUP BY a.id;
```

### 5. 验证状态自动更新
- ✅ 当账号配额用完时,状态自动变为"已达上限"(status=2)
- ✅ 当24小时窗口滚动后配额释放,状态自动恢复为"启用"(status=1)

## 兼容性说明

### 正在运行的任务
- ✅ **完全兼容** - 正在运行的上传任务不会受到影响
- ✅ `updateAccountQuota()` 方法保持向后兼容
- ✅ `markFileAsUploaded()` 现在会自动插入上传记录
- ✅ 配额计算自动切换到滚动24小时窗口

### 历史数据
- ✅ 现有的 `upload_record` 表数据保持不变
- ✅ 滚动窗口查询会自动使用历史数据
- ✅ 超过24小时的记录自动不计入配额

## 回滚方案

如果部署后发现问题,可以回滚:

### 1. 恢复数据库
```bash
mysql -u your_username -p gd_upload_manager < backup_before_migration_YYYYMMDD_HHMMSS.sql
```

### 2. 恢复旧版本代码
```bash
# 恢复到之前的代码版本
git checkout <previous_commit>
# 重新部署
```

## 常见问题

### Q: 账号显示"已达上限"但实际配额应该已释放?
**A:** 访问账号管理页面,点击该账号的"重置配额"按钮,系统会检查实时配额并自动恢复状态。或者等待30秒自动刷新。

### Q: 上传记录没有插入到upload_record表?
**A:** 检查 `FileInfoServiceImpl.markFileAsUploaded()` 方法是否正确注入了 `UploadRecordMapper`。

### Q: 配额计算不准确?
**A:** 检查 `upload_record` 表的 `upload_time` 字段是否正确,以及 `status` 字段是否为1(成功)。

### Q: 性能问题?
**A:** 确认索引 `idx_account_time_status` 已正确创建。可以使用 `EXPLAIN` 分析SQL查询性能。

## 监控建议

部署后建议监控以下指标:
- 账号配额使用情况
- 上传记录插入频率
- SQL查询性能(特别是24小时窗口查询)
- 账号状态自动更新是否正常

## 联系支持

如有问题,请检查:
1. 后端日志: 查看是否有异常或错误
2. 数据库日志: 查看 `system_log` 表
3. 前端控制台: 查看是否有API调用错误
