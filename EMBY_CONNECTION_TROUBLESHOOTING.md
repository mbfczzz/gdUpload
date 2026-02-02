# 🔍 Emby 连接测试失败诊断

## ❌ 问题现象

测试 Emby 配置时一直显示"连接测试失败"。

## 🔬 诊断结果

### 1. 网络连接测试

```bash
ping 209.146.116.4
```

**结果**: ✅ 成功
- 服务器 IP 可达
- 网络连接正常
- 延迟约 161ms

### 2. 端口连接测试

```bash
curl http://209.146.116.4:8096/emby/System/Info
```

**结果**: ❌ 失败
```
Connection refused
Failed to connect to 209.146.116.4 port 8096
```

## 🎯 问题原因

**端口 8096 无法访问**

可能的原因：

### 1. Emby 服务器未运行 ⭐ 最可能

Emby 服务可能没有启动或已停止。

**解决方法**：
- 联系服务器管理员确认 Emby 是否运行
- 如果是你的服务器，启动 Emby 服务

### 2. 端口不是 8096

Emby 可能配置了不同的端口。

**解决方法**：
- 检查 Emby 实际使用的端口
- 常见端口：8096（HTTP）、8920（HTTPS）
- 尝试其他端口：
  ```
  http://209.146.116.4:8920
  http://209.146.116.4:80
  http://209.146.116.4:443
  ```

### 3. 防火墙阻止

服务器防火墙可能阻止了 8096 端口。

**解决方法**：
- 检查服务器防火墙规则
- 开放 8096 端口
- 或使用已开放的端口

### 4. 服务器配置问题

Emby 可能只监听本地地址（127.0.0.1）。

**解决方法**：
- 检查 Emby 配置文件
- 确保监听 0.0.0.0 或公网 IP

## 🔧 排查步骤

### 步骤 1：确认 Emby 服务状态

如果你有服务器访问权限：

```bash
# Linux
systemctl status emby-server
# 或
ps aux | grep emby

# Windows
# 检查服务管理器中的 Emby Server 服务
```

### 步骤 2：检查 Emby 端口

查看 Emby 配置文件：

**Linux**: `/etc/emby-server.conf`
**Windows**: `C:\ProgramData\Emby-Server\config\system.xml`

查找 `HttpServerPortNumber` 配置项。

### 步骤 3：测试其他端口

```bash
# 测试 HTTPS 端口
curl https://209.146.116.4:8920/emby/System/Info

# 测试标准 HTTP 端口
curl http://209.146.116.4:80/emby/System/Info

# 测试标准 HTTPS 端口
curl https://209.146.116.4:443/emby/System/Info
```

### 步骤 4：检查防火墙

**Linux**:
```bash
# 检查防火墙状态
sudo ufw status
sudo firewall-cmd --list-ports

# 开放端口
sudo ufw allow 8096
sudo firewall-cmd --add-port=8096/tcp --permanent
```

**Windows**:
```powershell
# 检查防火墙规则
netsh advfirewall firewall show rule name=all | findstr 8096

# 添加规则
netsh advfirewall firewall add rule name="Emby Server" dir=in action=allow protocol=TCP localport=8096
```

### 步骤 5：使用端口扫描

```bash
# 扫描常用端口
nmap -p 8096,8920,80,443 209.146.116.4
```

## 💡 临时解决方案

### 方案 1：使用本地 Emby

如果你有本地 Emby 服务器：

```yaml
server-url: http://localhost:8096
```

### 方案 2：使用其他可用的 Emby 服务器

如果有其他可访问的 Emby 服务器，更换服务器地址。

### 方案 3：联系管理员

如果是别人的服务器：
1. 联系服务器管理员
2. 确认服务器地址和端口
3. 确认服务是否运行
4. 确认你的 IP 是否被允许访问

## 🧪 测试工具

### 在线测试

使用浏览器访问：
```
http://209.146.116.4:8096
```

如果能看到 Emby 登录页面，说明服务正常。

### 命令行测试

```bash
# Windows
telnet 209.146.116.4 8096

# Linux/Mac
nc -zv 209.146.116.4 8096
```

如果连接成功，说明端口开放。

## 📝 配置示例

### 正确的配置（假设端口是 8920）

```javascript
{
  configName: "我的Emby",
  serverUrl: "http://209.146.116.4:8920",  // 使用正确的端口
  username: "mbfczzzz",
  password: "mbfczzzz@123",
  timeout: 30000,
  enabled: true
}
```

### 使用 HTTPS

```javascript
{
  configName: "我的Emby",
  serverUrl: "https://209.146.116.4:8920",  // 使用 HTTPS
  username: "mbfczzzz",
  password: "mbfczzzz@123",
  timeout: 30000,
  enabled: true
}
```

## ✅ 验证方法

### 1. 浏览器测试

访问：`http://209.146.116.4:8096`

**期望结果**：看到 Emby 登录页面

### 2. API 测试

```bash
curl http://209.146.116.4:8096/emby/System/Info
```

**期望结果**：返回 JSON 数据或 401 错误（需要认证）

### 3. 登录测试

```bash
curl -X POST http://209.146.116.4:8096/emby/Users/AuthenticateByName \
  -H "Content-Type: application/json" \
  -H "X-Emby-Authorization: MediaBrowser Client=\"Test\", Device=\"CLI\", DeviceId=\"test\", Version=\"1.0\"" \
  -d '{"Username":"mbfczzzz","Pw":"mbfczzzz@123"}'
```

**期望结果**：返回包含 AccessToken 的 JSON

## 🎯 下一步

1. **确认服务器地址和端口**
   - 联系管理员或检查配置

2. **确认服务运行状态**
   - 检查 Emby 服务是否启动

3. **检查防火墙**
   - 确保端口开放

4. **更新配置**
   - 使用正确的地址和端口

5. **重新测试**
   - 在配置页面点击"测试"按钮

## 📞 需要帮助？

如果问题仍未解决，请提供：
1. Emby 服务器的实际地址和端口
2. 是否能在浏览器中访问 Emby
3. 服务器日志（如果有访问权限）
4. 防火墙配置

---

**总结**：当前问题是 **端口 8096 无法访问**，需要确认 Emby 服务是否运行以及使用的正确端口。
