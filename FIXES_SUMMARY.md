# 问题修复总结

## 修复的问题

### 1. 文件名乱码问题 ❌ 部分修复

**问题描述：** 下载到 `/data/emby/电影名 (2025)/文件名.mp4` 时，文件名显示为 `? 1 ?.mp4`

**已完成的修复：**
- ✅ 使用 `java.nio.file.Path` 和 `java.nio.channels` 进行文件操作
- ✅ 添加详细的编码日志，记录原始文件名、清理后文件名、UTF-8 编码测试
- ✅ 在 `EmbyServiceImpl` 添加 `@PostConstruct` 方法，启动时记录 JVM 编码信息
- ✅ 简化文件名处理逻辑，只替换非法字符

**仍需排查：**
由于你的部署脚本已经正确设置了 `-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8`，但仍然出现乱码，可能的原因：

1. **系统 locale 不是 UTF-8**
   ```bash
   # 检查
   locale
   echo $LANG

   # 如果不是 UTF-8，在部署脚本开头添加：
   export LANG=en_US.UTF-8
   export LC_ALL=en_US.UTF-8
   ```

2. **文件系统不支持 UTF-8**
   ```bash
   # 检查
   mount | grep /data

   # 测试
   cd /data/emby
   touch "测试中文.txt"
   ls -la
   ```

3. **SSH 终端编码问题**
   - 即使服务器正确，如果你的 SSH 客户端不是 UTF-8，看到的也是乱码

**下一步操作：**
1. 重新编译部署
2. 查看启动日志：`grep "JVM 默认编码" /work/nohup.out`
3. 下载测试文件，查看详细日志：`tail -200 /work/nohup.out | grep -A 10 "原始文件名"`
4. 检查实际创建的文件名：`ls -la /data/emby/`

### 2. 直接下载子剧集没有创建目录 ✅ 已修复

**问题描述：** 直接点击剧集的"直接下载"按钮时，文件下载到 `/data/emby/` 根目录，而不是 `/data/emby/电视剧名/` 目录

**修复方案：**
- ✅ 在 `EmbyItem.java` 添加 `seriesId` 和 `seriesName` 字段
- ✅ 创建 `downloadEpisodeWithSeriesDir()` 方法
- ✅ 修改 `downloadToServer()` 逻辑：
  - 如果是 Episode，调用 `downloadEpisodeWithSeriesDir()`
  - 获取剧集的 `seriesId`
  - 获取电视剧信息
  - 创建电视剧目录
  - 下载剧集到电视剧目录

**效果：**
- 现在下载单个剧集时，会自动创建 `/data/emby/电视剧名 (年份)/` 目录
- 剧集文件保存在对应的电视剧目录下

### 3. 子剧集没有成功/失败标识 ✅ 已修复

**问题描述：** 展开电视剧查看剧集列表时，看不到每一集的下载状态

**修复方案：**
- ✅ 在剧集名称列添加下载状态徽章（已下载/下载失败）
- ✅ 修改 `handleExpandChange()` 函数，展开时自动加载剧集的下载状态
- ✅ 使用 `batchCheckDownloadStatus()` 批量查询剧集下载状态
- ✅ 将剧集状态合并到 `downloadStatusMap`

**效果：**
- 展开电视剧时，每一集旁边会显示下载状态徽章
- 绿色"已下载"表示下载成功
- 红色"下载失败"表示下载失败
- 没有徽章表示未下载

## 修改的文件

### 后端文件

1. **EmbyServiceImpl.java**
   - 添加 `@PostConstruct init()` 方法，记录编码信息
   - 修改文件名处理逻辑，添加详细日志
   - 修改文件写入方式，使用 NIO Channels
   - 添加 `downloadEpisodeWithSeriesDir()` 方法
   - 修改 `downloadToServer()` 逻辑，支持 Episode 创建目录

2. **EmbyItem.java**
   - 添加 `seriesId` 字段
   - 添加 `seriesName` 字段

### 前端文件

3. **EmbyManager.vue**
   - 在剧集名称列添加下载状态徽章
   - 修改 `handleExpandChange()` 函数，加载剧集下载状态

## 测试步骤

### 测试 1: 检查编码设置

```bash
# 重新部署
./deploy.sh

# 查看编码日志
grep "JVM 默认编码" /work/nohup.out
grep "系统默认字符集" /work/nohup.out

# 应该看到：
# JVM 默认编码: UTF-8
# 系统默认字符集: UTF-8
```

### 测试 2: 测试文件名编码

```bash
# 下载一个中文名称的电影
# 查看详细日志
tail -200 /work/nohup.out | grep -A 10 "原始文件名"

# 应该看到完整的中文字符和编码测试结果

# 检查实际文件名
ls -la /data/emby/
```

### 测试 3: 测试剧集目录创建

1. 在前端找到一个电视剧
2. 展开查看剧集列表
3. 点击某一集的"下载"按钮
4. 检查文件是否保存在 `/data/emby/电视剧名/` 目录下

```bash
ls -la /data/emby/
ls -la /data/emby/电视剧名/
```

### 测试 4: 测试剧集下载状态显示

1. 在前端找到一个电视剧
2. 下载其中几集
3. 刷新页面
4. 展开电视剧
5. 检查已下载的剧集是否显示"已下载"徽章

## 如果文件名仍然乱码

### 方案 1: 修改部署脚本（推荐）

在部署脚本开头添加：

```bash
#!/bin/bash

# 设置环境变量
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

cd /work
# ... 其余代码
```

### 方案 2: 检查系统环境

```bash
# 检查 locale
locale

# 如果不是 UTF-8，安装语言包
sudo apt-get install language-pack-zh-hans

# 配置 locale
sudo dpkg-reconfigure locales
```

### 方案 3: 测试文件系统

```bash
# 手动创建中文文件
cd /data/emby
touch "测试中文文件.txt"
ls -la

# 如果手动创建也是乱码，说明是系统问题，不是应用问题
```

### 方案 4: 查看详细日志

重新部署后，下载一个文件，然后查看日志：

```bash
tail -300 /work/nohup.out | grep -E "(原始文件名|清理后的文件名|UTF-8 编码测试|实际文件名)"
```

日志会显示：
- 原始文件名（从 Emby 获取的）
- 清理后的文件名（替换非法字符后）
- UTF-8 编码测试结果
- 实际创建的文件名

如果日志中显示的中文是正确的，但文件系统中是乱码，说明是系统环境问题。

## 已创建的文档

1. **FILE_ENCODING_FIX.md** - 文件编码修复完整指南
2. **ENCODING_TROUBLESHOOTING.md** - 编码问题排查指南
3. **DOWNLOAD_STATUS_CHECKLIST.md** - 下载状态系统检查清单

## 下一步

1. **重新编译部署**
   ```bash
   cd backend
   mvn clean package
   # 上传 jar 文件并部署
   ```

2. **查看日志验证**
   ```bash
   # 查看编码设置
   grep "JVM 默认编码" /work/nohup.out

   # 下载测试文件后查看详细日志
   tail -200 /work/nohup.out | grep -A 10 "原始文件名"
   ```

3. **测试功能**
   - 测试电影下载（文件名编码）
   - 测试剧集下载（目录创建）
   - 测试剧集状态显示

4. **如果仍有问题**
   - 提供日志中的编码信息
   - 提供 `locale` 命令输出
   - 提供手动创建中文文件的测试结果
