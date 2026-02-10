# 文件名乱码最终排查方案

## 当前情况

**JVM 编码正确：**
```
JVM 默认编码: UTF-8
JVM JNU 编码: UTF-8
系统默认字符集: UTF-8
系统 LANG: en_US.UTF-8
系统 LC_ALL: en_US.UTF-8
```

**但文件名显示为：** `/data/emby/第 1 集.mp4`

## 可能的原因

### 1. 这不是乱码，而是正确的文件名

如果原始文件名就是 "第 1 集"，那这是正确的！

**验证方法：**
```bash
# 在服务器上执行
ls -la /data/emby/

# 如果显示的是中文，说明文件名是正确的
# 如果显示的是 ????，才是真正的乱码
```

### 2. 日志中的中文显示问题

你看到的日志可能是通过 SSH 或 Web 界面查看的，如果查看工具不支持 UTF-8，会显示乱码。

**验证方法：**
```bash
# 直接在服务器上查看文件
cd /data/emby
ls -la

# 使用 file 命令查看文件名编码
file -bi *

# 使用 hexdump 查看文件名的实际字节
ls | od -An -tx1
```

### 3. 真正的乱码

如果在服务器上直接 `ls` 也显示 `????`，那才是真正的乱码。

## 解决方案

### 方案 1: 使用拼音或英文文件名（临时方案）

修改 `EmbyServiceImpl.java`，在构建文件名后添加：

```java
// 如果文件名包含中文，使用剧集编号
if (hasNonAscii && "Episode".equals(item.getType())) {
    // 获取季数和集数
    Integer seasonNumber = item.getParentIndexNumber();
    Integer episodeNumber = item.getIndexNumber();

    if (seasonNumber != null && episodeNumber != null) {
        filename = String.format("S%02dE%02d", seasonNumber, episodeNumber);
    } else if (episodeNumber != null) {
        filename = String.format("E%02d", episodeNumber);
    } else {
        filename = item.getId();
    }

    // 添加原始名称作为后缀（如果需要）
    // filename += " - " + originalName;

    filename += ".mp4";
    log.info("使用英文文件名: {}", filename);
}
```

### 方案 2: 检查文件系统挂载选项

```bash
# 查看 /data 的挂载选项
mount | grep /data

# 如果没有 utf8 选项，重新挂载
sudo mount -o remount,iocharset=utf8 /data

# 或者在 /etc/fstab 中添加 utf8 选项
sudo nano /etc/fstab
# 添加 iocharset=utf8 到挂载选项
```

### 方案 3: 使用 convmv 转换已有文件名

```bash
# 安装 convmv
sudo apt-get install convmv

# 预览转换（不实际执行）
convmv -f GBK -t UTF-8 -r /data/emby

# 实际执行转换
convmv -f GBK -t UTF-8 -r --notest /data/emby
```

## 测试步骤

### 1. 确认是否真的乱码

```bash
# SSH 登录服务器
ssh user@server

# 查看文件
cd /data/emby
ls -la

# 如果看到的是中文，说明文件名正确，只是日志显示问题
# 如果看到的是 ????，才是真正的乱码
```

### 2. 查看文件名的实际字节

```bash
# 查看文件名的十六进制表示
ls /data/emby | od -An -tx1

# 中文 "第" 的 UTF-8 编码是: e7 ac ac
# 如果看到这个，说明文件名是 UTF-8 编码的中文
```

### 3. 测试手动创建中文文件

```bash
cd /data/emby
touch "测试中文文件.txt"
ls -la

# 如果手动创建的文件也是乱码，说明是系统环境问题
```

## 新增的调试日志

重新部署后，下载文件时会看到：

```
原始文件名: 第 1 集
清理后的文件名: 第 1 集
文件名包含非ASCII字符（中文等），字符数: 4
UTF-8 编码测试 - 原始: 第 1 集, 解码: 第 1 集, 匹配: true
最终文件名: 第 1 集.mp4
目标文件路径: /data/emby/第 1 集.mp4
文件名长度: 8, 字符: 第 1 集.mp4
文件名 Unicode: '第'(U+7B2C) ' '(U+0020) '1'(U+0031) ' '(U+0020) '集'(U+96C6) '.'(U+002E) 'm'(U+006D) 'p'(U+0070)
```

**关键信息：**
- 如果看到 `'第'(U+7B2C)` 这样的 Unicode 编码，说明 Java 内部处理是正确的
- 如果文件系统中仍然是乱码，说明是文件系统不支持 UTF-8

## 最终建议

如果确认是真正的乱码（服务器上 `ls` 也显示 `????`），建议：

1. **临时方案：** 使用 S01E01 这样的英文文件名
2. **长期方案：** 检查并修复文件系统的 UTF-8 支持

如果只是日志显示问题（服务器上 `ls` 显示正常），那就不需要修复，文件名是正确的。
