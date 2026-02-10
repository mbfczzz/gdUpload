# 扫描返回0的完整调试步骤

## 当前情况

- 目录：`/backdata/done/`
- 子目录：13个（外语电影、国产剧、国漫等）
- 文件：大量 mkv 文件
- 后端服务：root 用户运行
- 递归扫描：已开启
- **扫描结果：0 个文件**
- **日志显示：子目录数=0**

## 问题分析

日志显示 `子目录数=0`，说明 `directory.listFiles()` 返回了空数组或 null。

可能的原因：
1. 路径问题（路径中有特殊字符或空格）
2. Java 文件系统权限问题
3. 目录实际为空（但您已确认有文件）
4. 编码问题

## 立即执行的调试步骤

### 步骤1：测试 Java 能否读取目录

在服务器上执行：

```bash
# 创建测试程序
cat > /tmp/TestRead.java << 'EOF'
import java.io.File;
import java.nio.charset.StandardCharsets;

public class TestRead {
    public static void main(String[] args) {
        String path = "/backdata/done";
        System.out.println("========== 测试读取目录 ==========");
        System.out.println("测试路径: " + path);
        System.out.println();

        File dir = new File(path);

        System.out.println("1. 基本检查:");
        System.out.println("   exists(): " + dir.exists());
        System.out.println("   isDirectory(): " + dir.isDirectory());
        System.out.println("   canRead(): " + dir.canRead());
        System.out.println("   canExecute(): " + dir.canExecute());
        System.out.println("   getAbsolutePath(): " + dir.getAbsolutePath());
        System.out.println();

        System.out.println("2. listFiles() 测试:");
        File[] files = dir.listFiles();
        if (files == null) {
            System.out.println("   返回: null");
            System.out.println("   原因: 可能是权限问题或路径不是目录");
        } else {
            System.out.println("   返回: " + files.length + " 个文件/目录");
            System.out.println();
            System.out.println("3. 前10个项目:");
            for (int i = 0; i < Math.min(10, files.length); i++) {
                File f = files[i];
                System.out.println("   [" + (i+1) + "] " + f.getName());
                System.out.println("       isFile: " + f.isFile());
                System.out.println("       isDirectory: " + f.isDirectory());
                System.out.println("       canRead: " + f.canRead());
                System.out.println("       size: " + (f.isFile() ? f.length() : "N/A"));
            }
        }

        System.out.println();
        System.out.println("4. 测试带斜杠的路径:");
        File dir2 = new File("/backdata/done/");
        File[] files2 = dir2.listFiles();
        System.out.println("   /backdata/done/ listFiles(): " + (files2 == null ? "null" : files2.length));

        System.out.println();
        System.out.println("5. 测试子目录:");
        File subDir = new File("/backdata/done/国产剧");
        System.out.println("   /backdata/done/国产剧 exists(): " + subDir.exists());
        System.out.println("   /backdata/done/国产剧 isDirectory(): " + subDir.isDirectory());
        File[] subFiles = subDir.listFiles();
        System.out.println("   /backdata/done/国产剧 listFiles(): " + (subFiles == null ? "null" : subFiles.length));
        if (subFiles != null && subFiles.length > 0) {
            System.out.println("   第一个文件: " + subFiles[0].getName());
        }

        System.out.println("====================================");
    }
}
EOF

# 编译
javac /tmp/TestRead.java

# 运行
java -cp /tmp TestRead
```

### 步骤2：查看完整的后端日志

```bash
# 查看最近的扫描日志
tail -100 /work/nohup.out | grep -A 10 "收到扫描请求"

# 或者实时查看
tail -f /work/nohup.out
```

### 步骤3：检查路径中是否有隐藏字符

```bash
# 查看路径的十六进制表示
echo -n "/backdata/done" | xxd

# 查看目录是否真的存在
stat /backdata/done

# 尝试不同的路径格式
ls -la /backdata/done
ls -la /backdata/done/
ls -la "/backdata/done"
```

## 可能的解决方案

### 方案1：路径问题

如果路径中有特殊字符，尝试：

```bash
# 在前端输入时不要带末尾斜杠
/backdata/done

# 而不是
/backdata/done/
```

### 方案2：重新编译部署

我已经添加了详细的日志，需要重新编译部署：

```bash
# 在 Windows 本地编译
cd D:\mbfczzzz\claude\gdUpload\backend
mvn clean package -DskipTests

# 上传到服务器
scp target/gdupload-0.0.1-SNAPSHOT.jar root@server:/work/

# SSH 登录服务器
ssh root@server

# 停止服务
cd /work
./stop.sh

# 启动服务
./start.sh

# 查看日志
tail -f /work/nohup.out
```

### 方案3：直接测试扫描方法

在服务器上创建测试程序，直接调用扫描方法：

```bash
# 将测试代码放到后端项目中测试
# 或者使用 curl 测试 API
curl -X POST http://localhost:8099/api/file/scan \
  -H "Content-Type: application/json" \
  -d '{
    "directoryPath": "/backdata/done",
    "recursive": true,
    "limit": 1000
  }'
```

## 预期的正确日志

如果一切正常，应该看到：

```
========== 收到扫描请求 ==========
接收到的参数: {directoryPath=/backdata/done, recursive=true, limit=1000}
directoryPath: /backdata/done
recursive: true
limit: 1000
====================================
开始扫描目录: path=/backdata/done, recursive=true
规范化基础路径: /backdata/done
扫描目录: /backdata/done, 文件数: 13
递归扫描子目录: /backdata/done/外语电影
扫描目录: /backdata/done/外语电影, 文件数: 50
扫描到视频文件: fileName=xxx.mkv, relativePath=外语电影, size=1234567890
...
目录扫描统计: path=/backdata/done/外语电影, 文件总数=50, 子目录数=0, 视频文件数=45, 跳过文件数=5
...
扫描目录完成: path=/backdata/done, recursive=true, fileCount=500
```

## 下一步

请先执行**步骤1**的 Java 测试程序，把输出结果发给我，我们根据结果进一步分析！
