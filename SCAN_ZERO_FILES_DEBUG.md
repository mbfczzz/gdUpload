# 扫描结果为0的问题排查

## 问题描述

扫描 `/backdata/done/` 目录时，结果为 0 个文件。

## 可能的原因

### 1. 递归扫描未开启 ⚠️

**检查方法：**
1. 打开前端"创建上传任务"页面
2. 查看"递归扫描"开关是否打开
3. 如果关闭，子目录中的文件不会被扫描

**解决方法：**
- 打开"递归扫描"开关
- 重新点击"扫描目录"

---

### 2. 子目录中没有 mp4 或 mkv 文件

**检查方法：**
```bash
# 查看子目录中的文件类型
ls /backdata/done/外语电影/ | head -10

# 查看所有 mp4 和 mkv 文件
find /backdata/done/ -type f \( -name "*.mp4" -o -name "*.mkv" \) | head -20
```

**解决方法：**
- 如果子目录中只有其他格式的文件（如 .avi, .rmvb 等），需要转换格式
- 或者修改代码支持更多格式

---

### 3. 目录权限问题

**检查方法：**
```bash
# 查看目录权限
ls -la /backdata/done/

# 查看子目录权限
ls -la /backdata/done/外语电影/
```

**解决方法：**
```bash
# 修改目录权限
chmod -R 755 /backdata/done/
```

---

### 4. 后端日志查看

**查看扫描日志：**
```bash
# 查看后端日志
tail -f /work/nohup.out | grep "扫描"

# 应该看到类似的日志：
# 开始扫描目录: path=/backdata/done/, recursive=true
# 规范化基础路径: /backdata/done
# 扫描目录: /backdata/done, 文件数: 13
# 目录扫描统计: path=/backdata/done, 文件总数=0, 子目录数=13, 视频文件数=0, 跳过文件数=0
# 递归扫描子目录: /backdata/done/外语电影
# 扫描目录: /backdata/done/外语电影, 文件数: 50
# 目录扫描统计: path=/backdata/done/外语电影, 文件总数=45, 子目录数=5, 视频文件数=40, 跳过文件数=5
# ...
# 扫描目录完成: path=/backdata/done/, recursive=true, fileCount=500
```

---

## 调试步骤

### 1. 重新编译并部署（添加了详细日志）

```bash
# 本地编译
cd D:\mbfczzzz\claude\gdUpload\backend
mvn clean package -DskipTests

# 上传到服务器
scp target/gdupload-0.0.1-SNAPSHOT.jar user@server:/work/

# SSH 登录服务器
ssh user@server

# 重启服务
cd /work
./stop.sh
./start.sh

# 查看日志
tail -f /work/nohup.out
```

### 2. 在前端创建任务

1. 打开前端页面
2. 进入"创建上传任务"
3. 输入：
   - 源路径：`/backdata/done/`
   - **递归扫描：开启** ⚠️
4. 点击"扫描目录"

### 3. 查看后端日志

```bash
# 实时查看扫描日志
tail -f /work/nohup.out | grep -E "扫描|recursive"

# 查看扫描统计
tail -f /work/nohup.out | grep "目录扫描统计"
```

### 4. 分析日志

**日志示例 1：递归扫描未开启**
```
开始扫描目录: path=/backdata/done/, recursive=false
扫描目录: /backdata/done, 文件数: 13
跳过子目录（递归扫描未开启）: /backdata/done/外语电影
跳过子目录（递归扫描未开启）: /backdata/done/国漫
...
目录扫描统计: path=/backdata/done, 文件总数=0, 子目录数=13, 视频文件数=0, 跳过文件数=0
扫描目录完成: path=/backdata/done/, recursive=false, fileCount=0
```
**原因：** 递归扫描未开启，子目录被跳过
**解决：** 打开递归扫描开关

---

**日志示例 2：子目录中没有视频文件**
```
开始扫描目录: path=/backdata/done/, recursive=true
扫描目录: /backdata/done, 文件数: 13
递归扫描子目录: /backdata/done/外语电影
扫描目录: /backdata/done/外语电影, 文件数: 50
跳过非视频文件: movie.avi
跳过非视频文件: video.rmvb
...
目录扫描统计: path=/backdata/done/外语电影, 文件总数=50, 子目录数=0, 视频文件数=0, 跳过文件数=50
...
扫描目录完成: path=/backdata/done/, recursive=true, fileCount=0
```
**原因：** 子目录中的文件都不是 mp4 或 mkv 格式
**解决：** 转换文件格式或修改代码支持更多格式

---

**日志示例 3：正常扫描**
```
开始扫描目录: path=/backdata/done/, recursive=true
扫描目录: /backdata/done, 文件数: 13
递归扫描子目录: /backdata/done/外语电影
扫描目录: /backdata/done/外语电影, 文件数: 50
扫描到视频文件: fileName=movie1.mp4, relativePath=外语电影, size=1234567890
扫描到视频文件: fileName=movie2.mkv, relativePath=外语电影, size=9876543210
...
目录扫描统计: path=/backdata/done/外语电影, 文件总数=50, 子目录数=0, 视频文件数=45, 跳过文件数=5
...
扫描目录完成: path=/backdata/done/, recursive=true, fileCount=500
```
**结果：** 扫描成功，找到 500 个视频文件

---

## 快速检查命令

### 检查子目录中的文件

```bash
# 查看第一个子目录的文件
ls -la /backdata/done/外语电影/ | head -20

# 查看文件扩展名统计
find /backdata/done/ -type f | sed 's/.*\.//' | sort | uniq -c | sort -rn

# 查看 mp4 和 mkv 文件数量
find /backdata/done/ -type f \( -name "*.mp4" -o -name "*.mkv" \) | wc -l

# 查看每个子目录的视频文件数量
for dir in /backdata/done/*/; do
    count=$(find "$dir" -maxdepth 1 -type f \( -name "*.mp4" -o -name "*.mkv" \) | wc -l)
    echo "$dir: $count 个视频文件"
done
```

---

## 常见问题

### Q1: 为什么只扫描 mp4 和 mkv？

**A:** 这是代码中的过滤逻辑：
```java
String fileNameLower = fileName.toLowerCase();
if (!fileNameLower.endsWith(".mp4") && !fileNameLower.endsWith(".mkv")) {
    log.debug("跳过非视频文件: {}", fileName);
    continue;
}
```

如果需要支持其他格式，可以修改这段代码。

### Q2: 递归扫描开关在哪里？

**A:** 在前端"创建上传任务"页面，"源路径"输入框下方有一个"递归扫描"开关。

### Q3: 如何查看扫描了哪些文件？

**A:** 查看后端日志：
```bash
tail -f /work/nohup.out | grep "扫描到视频文件"
```

---

## 总结

最可能的原因是：
1. **递归扫描未开启** - 检查前端开关
2. **子目录中没有 mp4/mkv 文件** - 检查文件格式

请按照上述步骤排查，并查看后端日志确定具体原因。
