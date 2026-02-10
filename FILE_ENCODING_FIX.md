# 文件名乱码问题解决方案

## 问题描述
下载到服务器本地 `/data/emby/电影名 (2025)/文件名.mp4` 时出现乱码，显示为 `????.mp4`

## 根本原因
1. **JVM 默认编码**：JVM 可能使用系统默认编码（如 ISO-8859-1），而不是 UTF-8
2. **文件系统编码**：Linux 文件系统需要 UTF-8 编码的文件名
3. **字符串到字节转换**：Java 字符串转换为文件路径时编码不匹配

## 解决方案

### 1. 代码层面修复（已完成）

#### 修改 1: 使用 java.nio.file.Path 和 UTF-8 编码
```java
// 原代码（可能乱码）
String filePath = downloadDir + "/" + filename;
java.io.File targetFile = new java.io.File(filePath);

// 新代码（UTF-8 安全）
java.nio.file.Path targetPath = java.nio.file.Paths.get(downloadDir, filename);
String filePath = targetPath.toString();
java.io.File targetFile = targetPath.toFile();
```

#### 修改 2: 显式使用 UTF-8 编码
```java
// 确保文件名使用UTF-8编码
try {
    byte[] bytes = filename.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    filename = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
} catch (Exception e) {
    log.warn("文件名编码转换失败: {}", e.getMessage());
}
```

#### 修改 3: 增强文件名验证
```java
// 检查文件名是否为空
if (filename == null || filename.isEmpty()) {
    filename = "unknown";
}
```

### 2. JVM 启动参数配置

#### 方式 1: 在启动脚本中添加
```bash
# 在启动 Spring Boot 应用时添加
java -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar your-app.jar
```

#### 方式 2: 在 application.yml 中配置（不推荐，不生效）
```yaml
# 这个配置不会影响 JVM 编码，仅供参考
spring:
  mandatory-file-encoding: UTF-8
```

#### 方式 3: 在 Dockerfile 中配置
```dockerfile
# 如果使用 Docker
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
```

#### 方式 4: 在 systemd 服务中配置
```ini
# /etc/systemd/system/your-app.service
[Service]
Environment="JAVA_OPTS=-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
ExecStart=/usr/bin/java $JAVA_OPTS -jar /path/to/your-app.jar
```

### 3. 系统环境配置

#### 检查系统 locale
```bash
# 查看当前 locale
locale

# 应该看到类似输出：
# LANG=en_US.UTF-8
# LC_ALL=en_US.UTF-8

# 如果不是 UTF-8，设置为 UTF-8
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8
```

#### 永久设置 locale
```bash
# 编辑 /etc/environment
sudo nano /etc/environment

# 添加以下内容
LANG=en_US.UTF-8
LC_ALL=en_US.UTF-8

# 或者编辑 ~/.bashrc
echo 'export LANG=en_US.UTF-8' >> ~/.bashrc
echo 'export LC_ALL=en_US.UTF-8' >> ~/.bashrc
source ~/.bashrc
```

### 4. 验证和测试

#### 测试 1: 检查 JVM 编码
在应用启动时添加日志：
```java
log.info("JVM 默认编码: {}", System.getProperty("file.encoding"));
log.info("JVM JNU 编码: {}", System.getProperty("sun.jnu.encoding"));
log.info("系统默认字符集: {}", java.nio.charset.Charset.defaultCharset());
```

**预期输出：**
```
JVM 默认编码: UTF-8
JVM JNU 编码: UTF-8
系统默认字符集: UTF-8
```

#### 测试 2: 创建测试文件
```java
// 测试代码
String testFilename = "测试文件名.txt";
java.nio.file.Path testPath = java.nio.file.Paths.get("/data/emby", testFilename);
java.nio.file.Files.createFile(testPath);
log.info("测试文件创建成功: {}", testPath);
```

#### 测试 3: 在服务器上验证
```bash
# 创建测试文件
cd /data/emby
touch "测试文件.txt"

# 查看文件名
ls -la

# 应该正确显示中文，而不是 ????
```

### 5. 完整的启动脚本示例

```bash
#!/bin/bash

# 设置环境变量
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

# 设置 JVM 参数
JAVA_OPTS="-Dfile.encoding=UTF-8"
JAVA_OPTS="$JAVA_OPTS -Dsun.jnu.encoding=UTF-8"
JAVA_OPTS="$JAVA_OPTS -Xms512m -Xmx2g"

# 启动应用
java $JAVA_OPTS -jar /path/to/gdupload-backend.jar
```

### 6. Maven/Gradle 配置

#### Maven (pom.xml)
```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
</properties>

<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <jvmArguments>
                    -Dfile.encoding=UTF-8
                    -Dsun.jnu.encoding=UTF-8
                </jvmArguments>
            </configuration>
        </plugin>
    </plugins>
</build>
```

#### Gradle (build.gradle)
```gradle
tasks.withType(JavaCompile) {
    options.encoding = 'UTF-8'
}

bootRun {
    systemProperty 'file.encoding', 'UTF-8'
    systemProperty 'sun.jnu.encoding', 'UTF-8'
}
```

### 7. 排查步骤

如果问题仍然存在，按以下步骤排查：

1. **检查 JVM 编码**
   ```bash
   # 在应用日志中查找
   grep "file.encoding" /path/to/app.log
   ```

2. **检查系统 locale**
   ```bash
   locale
   echo $LANG
   echo $LC_ALL
   ```

3. **检查文件系统**
   ```bash
   # 查看文件系统挂载选项
   mount | grep /data

   # 应该看到 utf8 选项
   ```

4. **测试文件创建**
   ```bash
   # 手动创建中文文件名
   cd /data/emby
   touch "测试中文.txt"
   ls -la
   ```

5. **查看应用日志**
   ```bash
   # 查看下载日志
   tail -f /path/to/app.log | grep "处理后的文件名"
   ```

### 8. 常见问题

#### Q1: 为什么需要同时设置 file.encoding 和 sun.jnu.encoding？
**A:**
- `file.encoding`: 控制 Java 字符串和字节流的转换
- `sun.jnu.encoding`: 控制 JNI (Java Native Interface) 调用时的编码，影响文件系统操作

#### Q2: 已经设置了 UTF-8，但还是乱码？
**A:** 检查以下几点：
1. JVM 是否真的使用了 UTF-8（查看日志）
2. 系统 locale 是否为 UTF-8
3. 文件系统是否支持 UTF-8
4. 是否在 Docker 容器中运行（需要在容器中设置）

#### Q3: 旧文件已经乱码，如何修复？
**A:**
```bash
# 使用 convmv 工具转换文件名编码
sudo apt-get install convmv

# 预览转换（不实际执行）
convmv -f GBK -t UTF-8 -r /data/emby

# 实际执行转换
convmv -f GBK -t UTF-8 -r --notest /data/emby
```

### 9. 推荐配置（最佳实践）

#### 启动脚本 (start.sh)
```bash
#!/bin/bash

# 设置环境
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

# JVM 参数
JAVA_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
JAVA_OPTS="$JAVA_OPTS -Xms512m -Xmx2g"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"

# 应用参数
APP_OPTS="--spring.profiles.active=prod"

# 启动
nohup java $JAVA_OPTS -jar gdupload-backend.jar $APP_OPTS > app.log 2>&1 &

echo "应用已启动，PID: $!"
echo "查看日志: tail -f app.log"
```

#### Systemd 服务 (gdupload.service)
```ini
[Unit]
Description=GD Upload Service
After=network.target

[Service]
Type=simple
User=your-user
WorkingDirectory=/opt/gdupload
Environment="LANG=en_US.UTF-8"
Environment="LC_ALL=en_US.UTF-8"
Environment="JAVA_OPTS=-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Xms512m -Xmx2g"
ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/gdupload/gdupload-backend.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### 10. 验证修复

修复后，执行以下测试：

1. **重启应用**
   ```bash
   # 停止应用
   pkill -f gdupload-backend.jar

   # 使用新的启动脚本启动
   ./start.sh
   ```

2. **查看编码设置**
   ```bash
   # 查看日志中的编码信息
   grep "JVM 默认编码" app.log
   ```

3. **测试下载**
   - 下载一个中文名称的电影
   - 检查 `/data/emby` 目录
   - 文件名应该正确显示中文

4. **验证文件名**
   ```bash
   ls -la /data/emby
   # 应该看到正确的中文文件名，而不是 ????
   ```

## 总结

修复文件名乱码需要三个层面的配置：
1. ✅ **代码层面**：使用 `java.nio.file.Path` 和显式 UTF-8 编码（已完成）
2. ⚠️ **JVM 层面**：启动时设置 `-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8`
3. ⚠️ **系统层面**：确保 `LANG=en_US.UTF-8` 和 `LC_ALL=en_US.UTF-8`

**最重要的是第 2 步：在 JVM 启动时设置编码参数！**
