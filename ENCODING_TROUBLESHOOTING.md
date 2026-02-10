# 文件名乱码排查指南

## 问题现象
下载到 `/data/emby/电影名 (2025)/文件名.mp4` 时，文件名显示为 `????.mp4`

## 已确认的配置
✅ 部署脚本已正确设置 JVM 参数：
```bash
-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8
```

## 排查步骤

### 1. 检查应用日志中的编码信息

重启应用后，查看日志开头的编码信息：

```bash
grep "JVM 默认编码" /work/nohup.out
grep "系统默认字符集" /work/nohup.out
```

**预期输出：**
```
JVM 默认编码: UTF-8
JVM JNU 编码: UTF-8
系统默认字符集: UTF-8
```

**如果不是 UTF-8，说明 JVM 参数没有生效！**

### 2. 检查系统环境

```bash
# 检查系统 locale
locale

# 检查环境变量
echo $LANG
echo $LC_ALL

# 检查文件系统编码
mount | grep /data
```

**预期输出：**
```
LANG=en_US.UTF-8
LC_ALL=en_US.UTF-8
```

### 3. 查看详细的文件名处理日志

下载一个文件后，查看日志：

```bash
tail -100 /work/nohup.out | grep -A 5 "原始文件名"
```

**应该看到类似输出：**
```
原始文件名: 电影名称
原始文件名字节数组: [-25, -108, -75, -27, -101, -79, ...]  (UTF-8 编码的字节)
清理后的文件名: 电影名称
文件名编码验证通过
最终文件名: 电影名称.mp4
最终文件名字节数组: [-25, -108, -75, -27, -101, -79, ...]
```

**如果看到问号或乱码，说明编码有问题！**

### 4. 测试文件系统是否支持中文

```bash
# 手动创建中文文件名
cd /data/emby
touch "测试中文文件名.txt"

# 查看文件名
ls -la

# 应该正确显示中文，而不是 ????
```

**如果手动创建也是乱码，说明是系统环境问题！**

### 5. 检查 SSH 终端编码

如果你通过 SSH 连接服务器：

```bash
# 在本地终端执行
echo $LANG

# 应该是 UTF-8
```

如果本地终端不是 UTF-8，即使服务器正确，你看到的也可能是乱码。

## 解决方案

### 方案 1: 修改部署脚本（推荐）

在启动命令前添加环境变量：

```bash
#!/bin/bash
cd /work

# 设置环境变量
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

mv /home/s1067/gd-upload-manager-1.0.0.jar ./

echo "========================================="
echo "  开始部署 GD Upload Manager"
echo "========================================="

# 停止服务
echo ""
echo "[2/4] 停止旧服务..."
if [ -f /work/app.pid ]; then
    OLD_PID=$(cat /work/app.pid)
    if ps -p $OLD_PID > /dev/null 2>&1; then
        kill $OLD_PID
        echo "✅ 已停止旧服务 (PID: $OLD_PID)"
        sleep 2
    else
        echo "⚠️  旧服务已停止"
    fi
else
    echo "⚠️  未找到 PID 文件"
fi

# 启动服务
echo ""
echo "启动新服务..."
cd /work
nohup java -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Xms512m -Xmx2048m -jar gd-upload-manager-1.0.0.jar > nohup.out 2>&1 &
echo $! > app.pid

echo "✅ 服务已启动 (PID: $(cat app.pid))"

# 等待服务启动
echo ""
echo "等待服务启动..."
sleep 5

# 检查编码设置
echo ""
echo "检查编码设置..."
sleep 2
grep "JVM 默认编码" /work/nohup.out
grep "系统默认字符集" /work/nohup.out

# 检查服务状态
if ps -p $(cat app.pid) > /dev/null 2>&1; then
    echo "✅ 服务运行正常"
else
    echo "❌ 服务启动失败，请查看日志: tail -100 /work/nohup.out"
fi
```

### 方案 2: 修改系统环境（永久）

```bash
# 编辑 /etc/environment
sudo nano /etc/environment

# 添加以下内容
LANG=en_US.UTF-8
LC_ALL=en_US.UTF-8

# 保存后重启系统或重新登录
```

### 方案 3: 修改用户环境

```bash
# 编辑 ~/.bashrc
nano ~/.bashrc

# 添加以下内容
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

# 保存后执行
source ~/.bashrc
```

## 验证修复

### 1. 重新部署应用

```bash
./deploy.sh
```

### 2. 查看编码日志

```bash
grep "JVM 默认编码" /work/nohup.out
```

**应该看到：**
```
JVM 默认编码: UTF-8
```

### 3. 测试下载

下载一个中文名称的电影，然后检查：

```bash
ls -la /data/emby/
```

**应该看到正确的中文文件名，而不是 ????**

### 4. 查看详细日志

```bash
tail -200 /work/nohup.out | grep -A 10 "原始文件名"
```

**应该看到完整的中文字符和正确的 UTF-8 字节数组**

## 常见问题

### Q1: 日志显示 JVM 默认编码是 UTF-8，但文件名还是乱码？

**可能原因：**
1. 文件系统不支持 UTF-8
2. SSH 终端编码不是 UTF-8
3. 文件名在传输过程中被转码

**解决方法：**
```bash
# 检查文件系统
mount | grep /data

# 如果没有 utf8 选项，重新挂载
sudo mount -o remount,utf8 /data
```

### Q2: 手动创建中文文件也是乱码？

**说明是系统环境问题，不是应用问题！**

**解决方法：**
```bash
# 安装中文语言包
sudo apt-get install language-pack-zh-hans

# 重新配置 locale
sudo dpkg-reconfigure locales

# 选择 en_US.UTF-8 和 zh_CN.UTF-8
```

### Q3: 日志中看到的字节数组是负数，正常吗？

**正常！** Java 的 byte 是有符号的（-128 到 127），UTF-8 中文字符的字节值通常大于 127，所以显示为负数。

例如："电" 的 UTF-8 编码是 `0xE7 0x94 0xB5`，在 Java 中显示为 `[-25, -108, -75]`

### Q4: 如何确认文件名真的是 UTF-8 编码？

```bash
# 使用 file 命令
file -bi /data/emby/电影名.mp4

# 使用 ls 配合 od 查看字节
ls /data/emby | od -An -tx1

# 使用 convmv 检查
convmv -f UTF-8 -t UTF-8 /data/emby/*
```

## 紧急修复方案

如果以上方法都不行，可以临时使用拼音或英文文件名：

修改 `EmbyServiceImpl.java`：

```java
// 在文件名处理后添加
if (filename.matches(".*[\\u4e00-\\u9fa5].*")) {
    // 如果包含中文，转换为拼音或使用 itemId
    log.warn("文件名包含中文，使用 itemId 代替: {}", filename);
    filename = item.getId() + ".mp4";
}
```

但这不是根本解决方案，只是临时规避！

## 联系支持

如果问题仍然存在，请提供以下信息：

1. 系统信息：
   ```bash
   uname -a
   cat /etc/os-release
   ```

2. Locale 信息：
   ```bash
   locale
   locale -a
   ```

3. 应用日志（包含编码信息）：
   ```bash
   head -50 /work/nohup.out
   tail -200 /work/nohup.out | grep -A 10 "原始文件名"
   ```

4. 文件系统信息：
   ```bash
   mount | grep /data
   df -h /data
   ```

5. 测试结果：
   ```bash
   cd /data/emby
   touch "测试中文.txt"
   ls -la
   ```
