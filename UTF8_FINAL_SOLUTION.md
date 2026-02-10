# 文件名 UTF-8 编码最终方案

## 修改内容

### 1. 完全使用 Java NIO 文件操作

**原因：** `java.io.File` 在某些系统上可能不正确处理 UTF-8 文件名，而 `java.nio.file.Files` 和 `Path` 是专门为 UTF-8 设计的。

**修改：**
- ✅ 使用 `Files.createDirectories()` 创建目录
- ✅ 使用 `Path.resolve()` 构建文件路径
- ✅ 使用 `Files.newOutputStream()` 创建文件
- ✅ 使用 `Files.exists()` 检查文件
- ✅ 使用 `Files.delete()` 删除文件
- ✅ 使用 `Files.size()` 获取文件大小

### 2. 保持原始文件名

**不做任何转换：**
- ❌ 不转换为拼音
- ❌ 不转换为 S01E01 格式
- ❌ 不转换为英文
- ✅ 保持 Emby 返回的原始中文名称

### 3. 简化文件名处理

```java
// 只做必要的清理
filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
```

不做任何编码转换，让 Java NIO 自动处理 UTF-8。

## 为什么这样做

### Java NIO 的优势

1. **原生 UTF-8 支持**
   - `java.nio.file.Path` 内部使用 UTF-8 编码
   - 不依赖系统默认编码
   - 跨平台一致性更好

2. **更好的文件系统交互**
   - 直接使用操作系统的文件系统 API
   - 避免 `java.io.File` 的编码转换问题

3. **Java 8+ 标准**
   - 从 Java 7 开始推荐使用 NIO.2
   - 更现代、更可靠

## 关键代码对比

### 旧代码（可能乱码）
```java
java.io.File dir = new java.io.File(downloadDir);
dir.mkdirs();

java.io.File targetFile = new java.io.File(downloadDir + "/" + filename);
java.io.FileOutputStream outputStream = new java.io.FileOutputStream(targetFile);
```

### 新代码（UTF-8 安全）
```java
java.nio.file.Path dirPath = java.nio.file.Paths.get(downloadDir);
java.nio.file.Files.createDirectories(dirPath);

java.nio.file.Path targetPath = dirPath.resolve(filename);
java.io.OutputStream outputStream = java.nio.file.Files.newOutputStream(targetPath);
```

## 测试步骤

### 1. 重新编译部署

```bash
cd backend
mvn clean package
# 上传并部署
```

### 2. 运行验证脚本

```bash
# 上传 check_encoding.sh 到服务器
chmod +x check_encoding.sh
./check_encoding.sh
```

### 3. 下载测试文件

1. 在前端下载一个中文名称的剧集
2. 查看日志：
   ```bash
   tail -100 /work/nohup.out | grep -A 5 "原始文件名"
   ```
3. 检查文件：
   ```bash
   ls -la /data/emby/
   ```

### 4. 验证结果

**成功标志：**
```bash
$ ls -la /data/emby/
-rw-r--r-- 1 user user 1024000000 Feb  6 01:30 第 1 集.mp4
```

**失败标志：**
```bash
$ ls -la /data/emby/
-rw-r--r-- 1 user user 1024000000 Feb  6 01:30 ? 1 ?.mp4
```

## 如果仍然乱码

### 可能的原因

1. **文件系统不支持 UTF-8**
   ```bash
   # 检查文件系统类型
   df -T /data

   # 如果是 NTFS 或 FAT32，可能需要特殊挂载选项
   mount | grep /data
   ```

2. **SSH 终端编码问题**
   - 即使文件名正确，SSH 客户端不支持 UTF-8 也会显示乱码
   - 在服务器本地终端查看（不通过 SSH）

3. **系统 locale 配置问题**
   ```bash
   # 检查
   locale

   # 如果不是 UTF-8，重新配置
   sudo dpkg-reconfigure locales
   ```

### 最后的手段

如果确实无法支持中文文件名，可以在 `application.yml` 中添加配置：

```yaml
app:
  emby:
    use-ascii-filename: true  # 使用 ASCII 文件名（S01E01 格式）
```

然后在代码中读取这个配置来决定是否转换文件名。

## 日志说明

部署后，下载文件时会看到：

```
原始文件名: 第 1 集
清理后的文件名: 第 1 集
最终文件名: 第 1 集.mp4
目标文件路径: /data/emby/第 1 集.mp4
文件名: 第 1 集.mp4
开始写入文件: /data/emby/第 1 集.mp4
下载完成！总大小: 980 MB
文件创建成功，实际文件名: 第 1 集.mp4
文件路径: /data/emby/第 1 集.mp4
文件大小: 980 MB
```

**关键点：**
- 如果日志中显示的是正确的中文，说明 Java 内部处理正确
- 如果文件系统中是乱码，说明是系统环境问题，不是代码问题

## 总结

这次修改的核心思想：

1. **信任 Java NIO** - 使用专门为 UTF-8 设计的 API
2. **不做转换** - 保持原始文件名，不做任何编码转换
3. **让系统处理** - 让 JVM 和操作系统自动处理 UTF-8 编码

如果这样还是乱码，那就是系统环境问题，需要从系统层面解决（locale、文件系统等）。
