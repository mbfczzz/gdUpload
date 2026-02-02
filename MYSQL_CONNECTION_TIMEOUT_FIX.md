# MySQL连接超时问题修复说明

## 问题描述

```
CommunicationsException: Communications link failure
```

这个错误表示MySQL连接在空闲一段时间后被服务器关闭，但Druid连接池还认为连接是有效的。

## 原因分析

1. **MySQL默认超时时间**：MySQL的 `wait_timeout` 默认是8小时（28800秒）
2. **连接池配置不当**：Druid连接池的 `test-on-borrow` 设置为 `false`，导致获取到已失效的连接
3. **空闲连接未检测**：虽然 `test-while-idle` 为 `true`，但检测间隔可能不够频繁

## 解决方案

### 1. 修改Druid连接池配置（已完成）

在 `application.yml` 中添加/修改以下配置：

```yaml
druid:
  # 开启借用时检测（重要！）
  test-on-borrow: true

  # 开启空闲检测
  test-while-idle: true

  # 保持连接活跃（重要！）
  keep-alive: true

  # 最大空闲时间（15分钟）
  max-evictable-idle-time-millis: 900000

  # 检测间隔（1分钟）
  time-between-eviction-runs-millis: 60000

  # 最小空闲时间（5分钟）
  min-evictable-idle-time-millis: 300000

  # 验证查询
  validation-query: SELECT 1
```

### 2. 调整MySQL服务器配置（可选）

如果需要更长的连接超时时间，可以修改MySQL配置：

**临时修改（重启后失效）：**
```sql
SET GLOBAL wait_timeout = 28800;
SET GLOBAL interactive_timeout = 28800;
```

**永久修改（推荐）：**

编辑 MySQL 配置文件（`my.cnf` 或 `my.ini`）：

```ini
[mysqld]
wait_timeout = 28800
interactive_timeout = 28800
```

然后重启MySQL服务。

### 3. 重启后端应用

修改配置后，重启Spring Boot应用：

```bash
cd backend
mvn clean package
java -jar target/gd-upload-manager.jar
```

## 配置参数说明

| 参数 | 说明 | 推荐值 |
|------|------|--------|
| `test-on-borrow` | 获取连接时检测是否有效 | `true` ✅ |
| `test-while-idle` | 空闲时检测连接 | `true` ✅ |
| `keep-alive` | 保持连接活跃 | `true` ✅ |
| `validation-query` | 验证SQL | `SELECT 1` |
| `time-between-eviction-runs-millis` | 检测间隔 | 60000 (1分钟) |
| `min-evictable-idle-time-millis` | 最小空闲时间 | 300000 (5分钟) |
| `max-evictable-idle-time-millis` | 最大空闲时间 | 900000 (15分钟) |

## 性能影响

### test-on-borrow 的影响

- **优点**：确保每次获取的连接都是有效的，避免 `CommunicationsException`
- **缺点**：每次获取连接时都会执行 `SELECT 1`，略微增加开销
- **结论**：对于后台任务系统，稳定性比性能更重要，建议开启

### keep-alive 的作用

`keep-alive` 会定期向MySQL发送心跳包，保持连接活跃，防止被服务器关闭。

## 验证修复

### 1. 查看Druid监控

访问：`http://localhost:8099/api/druid/index.html`

- 用户名：`admin`
- 密码：`admin`

查看连接池状态，确认：
- Active Connections（活跃连接）
- Idle Connections（空闲连接）
- Test On Borrow（借用时检测）是否开启

### 2. 测试长时间运行

创建一个批量任务，让它运行超过1小时，观察是否还会出现连接超时错误。

### 3. 查看日志

```bash
tail -f logs/gd-upload-manager.log | grep -i "communications"
```

如果没有再出现 `CommunicationsException`，说明问题已解决。

## 其他可能的原因

如果修改配置后仍然出现问题，检查：

1. **防火墙**：确保防火墙没有关闭长时间空闲的TCP连接
2. **网络设备**：某些路由器或负载均衡器会关闭空闲连接
3. **MySQL版本**：确保使用的MySQL驱动版本与MySQL服务器版本兼容
4. **连接数限制**：检查MySQL的 `max_connections` 配置

## 查看MySQL超时配置

```sql
SHOW VARIABLES LIKE '%timeout%';
```

重点关注：
- `wait_timeout`：非交互式连接超时时间
- `interactive_timeout`：交互式连接超时时间

## 总结

通过以下三个关键配置解决连接超时问题：

1. ✅ `test-on-borrow: true` - 借用时检测
2. ✅ `keep-alive: true` - 保持连接活跃
3. ✅ `max-evictable-idle-time-millis: 900000` - 合理的空闲时间

这样可以确保后台任务长时间运行时不会出现数据库连接问题。
