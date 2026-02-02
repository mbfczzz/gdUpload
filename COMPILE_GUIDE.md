# 编译问题排查指南

## 快速检查

所有必需的文件已创建：
- ✅ 4个实体类
- ✅ 4个Mapper接口
- ✅ 3个Service接口
- ✅ 3个ServiceImpl实现
- ✅ 2个Controller

## 如何编译

### 方法1：使用IDE（推荐）

1. 打开 IntelliJ IDEA 或 Eclipse
2. 右键点击项目 -> Maven -> Reimport
3. 等待依赖下载完成
4. Build -> Rebuild Project
5. 查看 Problems 面板中的错误

### 方法2：使用命令行

```bash
cd F:\cluade2\backend
mvn clean compile
```

## 常见错误及解决方案

### 错误1：找不到符号 PagedResult

**错误信息**：
```
cannot find symbol: class PagedResult
```

**解决方案**：
确认 `PagedResult.java` 存在于 `dto` 包中，并且在使用的类中添加导入：
```java
import com.gdupload.dto.PagedResult;
```

### 错误2：找不到符号 EmbyItemCache

**错误信息**：
```
cannot find symbol: class EmbyItemCache
```

**解决方案**：
确认实体类存在，并添加导入：
```java
import com.gdupload.entity.EmbyItemCache;
import com.gdupload.entity.EmbyLibraryCache;
```

### 错误3：方法返回类型不匹配

**错误信息**：
```
incompatible types: PagedResult<EmbyItem> cannot be converted to Map<String,Object>
```

**解决方案**：
已修复。`EmbyCacheServiceImpl` 中的方法会将 `PagedResult` 转换为 `Map`。

### 错误4：找不到 Mapper

**错误信息**：
```
Could not autowire. No beans of type 'EmbyItemCacheMapper' found.
```

**解决方案**：
确认 Mapper 接口上有 `@Mapper` 注解：
```java
@Mapper
public interface EmbyItemCacheMapper extends BaseMapper<EmbyItemCache> {
}
```

## 如果仍然有编译错误

请按以下步骤操作：

### 步骤1：清理项目

```bash
cd F:\cluade2\backend
mvn clean
```

### 步骤2：更新依赖

```bash
mvn dependency:resolve
```

### 步骤3：重新编译

```bash
mvn compile
```

### 步骤4：查看详细错误

```bash
mvn compile > compile_log.txt 2>&1
```

然后打开 `compile_log.txt` 查看详细错误信息。

## 提供错误信息

如果编译失败，请提供：

1. **错误类型**：找不到符号、类型不匹配、语法错误等
2. **错误文件**：哪个文件报错
3. **错误行号**：第几行
4. **完整错误信息**：从 compile_log.txt 复制

示例：
```
[ERROR] /F:/cluade2/backend/src/main/java/com/gdupload/service/impl/EmbyCacheServiceImpl.java:[100,20]
cannot find symbol
  symbol:   class PagedResult
  location: class com.gdupload.service.impl.EmbyCacheServiceImpl
```

## 已知的修复

以下问题已经修复：

1. ✅ `EmbyCacheServiceImpl` 添加了 `PagedResult` 导入
2. ✅ `EmbyController` 注入了 `IEmbyCacheService`
3. ✅ `getLibraryItemsPaged` 方法类型转换已修复
4. ✅ `getItemDetail` 方法使用缓存服务
5. ✅ `searchItems` 方法使用缓存服务

## 验证编译成功

编译成功后，你应该看到：

```
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  XX.XXX s
[INFO] Finished at: 2026-02-01T20:XX:XX+08:00
[INFO] ------------------------------------------------------------------------
```

## 下一步

编译成功后：

1. 执行数据库迁移脚本
2. 启动后端服务
3. 测试API接口
4. 编译前端
5. 测试完整功能

## 联系支持

如果遇到无法解决的编译错误，请：
1. 运行 `check_compile.bat` 生成错误日志
2. 提供 `compile_output.txt` 文件内容
3. 说明你的环境（JDK版本、Maven版本）
