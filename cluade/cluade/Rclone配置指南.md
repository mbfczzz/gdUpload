# Rclone 配置指南

## 一、安装 Rclone

### Linux 系统

```bash
# 使用官方安装脚本
curl https://rclone.org/install.sh | sudo bash

# 验证安装
rclone version
```

### Windows 系统

1. 访问 https://rclone.org/downloads/
2. 下载 Windows 版本（rclone-v1.xx.x-windows-amd64.zip）
3. 解压到 `C:\rclone`
4. 添加到系统 PATH：
   - 右键"此电脑" → 属性 → 高级系统设置 → 环境变量
   - 在"系统变量"中找到 Path，点击编辑
   - 添加 `C:\rclone`
5. 打开命令提示符，验证安装：
   ```cmd
   rclone version
   ```

## 二、配置 Google Drive

### 方法一：交互式配置（推荐）

在终端运行：

```bash
rclone config
```

按照以下步骤操作：

```
Current remotes:
# 如果是第一次配置，这里是空的

e) Edit existing remote
n) New remote
d) Delete remote
r) Rename remote
c) Copy remote
s) Set configuration password
q) Quit config

选择: n  # 输入 n 创建新配置

name> gdrive1
# 输入配置名称，建议使用 gdrive1, gdrive2, gdrive3 等

Type of storage to configure.
Enter a string value. Press Enter for the default ("").
Choose a number from below, or type in your own value
 1 / 1Fichier
   \ "fichier"
 2 / Alias for an existing remote
   \ "alias"
...
15 / Google Drive
   \ "drive"
...

Storage> 15  # 选择 15 (Google Drive)，或直接输入 drive

Google Application Client Id
Setting your own is recommended.
See https://rclone.org/drive/#making-your-own-client-id for how to create your own.
If you leave this blank, it will use an internal key which is low performance.
Enter a string value. Press Enter for the default ("").

client_id>  # 直接回车（使用默认，或填入自己的 Client ID）

OAuth Client Secret
Leave blank normally.
Enter a string value. Press Enter for the default ("").

client_secret>  # 直接回车（使用默认，或填入自己的 Client Secret）

Scope that rclone should use when requesting access from drive.
Enter a string value. Press Enter for the default ("").
Choose a number from below, or type in your own value
 1 / Full access all files, excluding Application Data Folder.
   \ "drive"
 2 / Read-only access to file metadata and file contents.
   \ "drive.readonly"
...

scope> 1  # 选择 1 (完全访问权限)

ID of the root folder
Leave blank normally.
Fill in to access "Computers" folders (see docs), or for rclone to use
a non root folder as its starting point.
Enter a string value. Press Enter for the default ("").

root_folder_id>  # 直接回车

Service Account Credentials JSON file path
Leave blank normally.
Needed only if you want use SA instead of interactive login.
Enter a string value. Press Enter for the default ("").

service_account_file>  # 直接回车

Edit advanced config? (y/n)
y) Yes
n) No (default)

y/n> n  # 输入 n

Remote config
Use auto config?
 * Say Y if not sure
 * Say N if you are working on a remote or headless machine

y) Yes (default)
n) No

y/n> n  # 如果是远程服务器输入 n，本地电脑输入 y

# 如果选择 n，会显示一个 URL
Please go to the following link: https://accounts.google.com/o/oauth2/auth?...

# 复制这个 URL 到浏览器打开
# 登录你的 Google 账号
# 点击"允许"授权
# 复制授权码

Enter verification code>
# 粘贴授权码

Configure this as a team drive?
y) Yes
n) No (default)

y/n> n  # 输入 n

--------------------
[gdrive1]
type = drive
scope = drive
token = {"access_token":"..."}
--------------------
y) Yes this is OK (default)
e) Edit this remote
d) Delete this remote

y/e/d> y  # 输入 y 确认

Current remotes:

Name                 Type
====                 ====
gdrive1              drive

e) Edit existing remote
n) New remote
d) Delete remote
r) Rename remote
c) Copy remote
s) Set configuration password
q) Quit config

e/n/d/r/c/s/q> q  # 输入 q 退出
```

### 方法二：使用配置文件

如果已有 rclone 配置，可以直接编辑配置文件：

**Linux/Mac:**
```bash
nano ~/.config/rclone/rclone.conf
```

**Windows:**
```cmd
notepad %USERPROFILE%\.config\rclone\rclone.conf
```

添加配置：

```ini
[gdrive1]
type = drive
scope = drive
token = {"access_token":"your_token_here","token_type":"Bearer","refresh_token":"your_refresh_token","expiry":"2024-01-01T00:00:00Z"}
```

## 三、测试配置

### 列出 Google Drive 根目录

```bash
rclone lsd gdrive1:
```

如果能看到目录列表，说明配置成功。

### 测试上传文件

```bash
# 创建测试文件
echo "test" > test.txt

# 上传到 Google Drive
rclone copy test.txt gdrive1:/test/

# 验证上传
rclone ls gdrive1:/test/
```

## 四、配置多个账号

重复"配置 Google Drive"步骤，使用不同的配置名称：

```bash
rclone config
# 选择 n (New remote)
# name> gdrive2  # 第二个账号
# ... 重复配置步骤

# name> gdrive3  # 第三个账号
# ... 重复配置步骤
```

最终配置文件示例：

```ini
[gdrive1]
type = drive
scope = drive
token = {...}

[gdrive2]
type = drive
scope = drive
token = {...}

[gdrive3]
type = drive
scope = drive
token = {...}
```

## 五、在系统中添加账号

配置好 rclone 后，在系统的"账号管理"页面添加账号：

### 步骤：

1. **访问账号管理页面**
   - 点击左侧菜单的"账号管理"

2. **点击"新增账号"按钮**

3. **填写账号信息**：
   - **账号名称**：自定义名称，如"账号1"、"主账号"
   - **账号邮箱**：Google 账号邮箱，如 `user1@gmail.com`
   - **Rclone配置**：填写 rclone 配置名称，如 `gdrive1`（必须与 rclone config 中的名称一致）
   - **每日限制(GB)**：默认 750GB（Google Drive 每日上传限制）
   - **优先级**：数字越大优先级越高，系统会优先使用高优先级账号
   - **状态**：选择"启用"
   - **备注**：可选，如"个人账号"、"工作账号"

4. **点击"确定"保存**

### 示例配置：

**账号1：**
- 账号名称：主账号
- 账号邮箱：user1@gmail.com
- Rclone配置：`gdrive1`
- 每日限制：750 GB
- 优先级：10
- 状态：启用

**账号2：**
- 账号名称：备用账号
- 账号邮箱：user2@gmail.com
- Rclone配置：`gdrive2`
- 每日限制：750 GB
- 优先级：5
- 状态：启用

**账号3：**
- 账号名称：紧急账号
- 账号邮箱：user3@gmail.com
- Rclone配置：`gdrive3`
- 每日限制：750 GB
- 优先级：1
- 状态：启用

## 六、配置系统参数

编辑后端配置文件 `application.yml`：

```yaml
app:
  rclone:
    # rclone 可执行文件路径
    path: /usr/bin/rclone  # Linux
    # path: C:\rclone\rclone.exe  # Windows

    # rclone 配置文件路径
    config-path: ~/.config/rclone/rclone.conf  # Linux/Mac
    # config-path: C:\Users\YourUsername\.config\rclone\rclone.conf  # Windows

    # 并发传输数
    concurrent-transfers: 3

    # 缓冲区大小
    buffer-size: 16M

    # 超时时间（秒）
    timeout: 3600
```

## 七、验证配置

### 1. 检查 rclone 配置

```bash
# 列出所有配置
rclone listremotes

# 应该显示：
# gdrive1:
# gdrive2:
# gdrive3:
```

### 2. 测试每个配置

```bash
# 测试 gdrive1
rclone lsd gdrive1:

# 测试 gdrive2
rclone lsd gdrive2:

# 测试 gdrive3
rclone lsd gdrive3:
```

### 3. 在系统中测试

1. 进入"账号管理"页面
2. 查看所有账号的状态是否为"启用"
3. 创建一个测试任务上传几个小文件
4. 观察任务是否正常执行

## 八、常见问题

### Q1: 提示 "Failed to configure token"

**解决方法：**
- 确保网络连接正常
- 检查是否正确复制了授权码
- 重新运行 `rclone config` 重新授权

### Q2: 提示 "command not found: rclone"

**解决方法：**
- 检查 rclone 是否正确安装
- 检查 PATH 环境变量是否包含 rclone 路径
- 重新安装 rclone

### Q3: 上传失败，提示 "remote not found"

**解决方法：**
- 检查系统中填写的"Rclone配置"名称是否与 `rclone listremotes` 显示的一致
- 注意配置名称不要包含冒号（:）
- 例如：填写 `gdrive1` 而不是 `gdrive1:`

### Q4: 提示 "User rate limit exceeded"

**解决方法：**
- 这是 Google Drive API 限制
- 等待一段时间后重试
- 或者创建自己的 Google API Client ID（参考 https://rclone.org/drive/#making-your-own-client-id）

### Q5: Windows 上找不到配置文件

**解决方法：**
配置文件位置：
```
C:\Users\你的用户名\.config\rclone\rclone.conf
```

如果不存在，运行 `rclone config` 会自动创建。

## 九、高级配置

### 创建自己的 Google API Client ID（推荐）

使用默认的 Client ID 会有速率限制，创建自己的可以提高性能：

1. 访问 https://console.cloud.google.com/
2. 创建新项目或选择现有项目
3. 启用 Google Drive API
4. 创建 OAuth 2.0 凭据
5. 在 rclone config 中填入 Client ID 和 Client Secret

详细步骤：https://rclone.org/drive/#making-your-own-client-id

### 使用 Service Account（适合无人值守）

如果需要无人值守自动上传，可以使用 Service Account：

1. 在 Google Cloud Console 创建 Service Account
2. 下载 JSON 密钥文件
3. 在 rclone config 中指定 `service_account_file` 路径

## 十、完整示例

### 场景：配置 3 个 Google Drive 账号

```bash
# 1. 安装 rclone
curl https://rclone.org/install.sh | sudo bash

# 2. 配置第一个账号
rclone config
# n -> gdrive1 -> 15 -> ... -> 授权

# 3. 配置第二个账号
rclone config
# n -> gdrive2 -> 15 -> ... -> 授权

# 4. 配置第三个账号
rclone config
# n -> gdrive3 -> 15 -> ... -> 授权

# 5. 验证配置
rclone listremotes
# 输出：
# gdrive1:
# gdrive2:
# gdrive3:

# 6. 测试上传
echo "test" > test.txt
rclone copy test.txt gdrive1:/test/
rclone copy test.txt gdrive2:/test/
rclone copy test.txt gdrive3:/test/

# 7. 在系统中添加账号
# 访问 http://localhost:8080/account
# 点击"新增账号"
# 填写信息并保存
```

现在你可以开始使用系统上传文件了！
