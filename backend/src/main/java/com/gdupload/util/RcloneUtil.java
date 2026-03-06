package com.gdupload.util;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Rclone工具类
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Slf4j
@Component
public class RcloneUtil {

    @Value("${app.rclone.path:/usr/bin/rclone}")
    private String rclonePath;

    @Value("${app.rclone.config-path:~/.config/rclone/rclone.conf}")
    private String rcloneConfigPath;

    @Value("${app.rclone.concurrent-transfers:64}")
    private Integer concurrentTransfers;

    @Value("${app.rclone.concurrent-transfers-resume:80}")
    private Integer concurrentTransfersResume;

    @Value("${app.rclone.checkers:128}")
    private Integer checkers;

    @Value("${app.rclone.checkers-resume:160}")
    private Integer checkersResume;

    @Value("${app.rclone.multi-thread-streams:24}")
    private Integer multiThreadStreams;

    @Value("${app.rclone.multi-thread-streams-resume:32}")
    private Integer multiThreadStreamsResume;

    @Value("${app.rclone.buffer-size:512M}")
    private String bufferSize;

    @Value("${app.rclone.drive-chunk-size:512M}")
    private String driveChunkSize;

    @Value("${app.rclone.timeout:3600}")
    private Integer timeout;

    /**
     * 目录创建去重：防止并发 mkdir 在 Google Drive 上产生多个同名文件夹。
     * key = "remoteName:path"，value 为 per-path 锁对象。
     * 已成功创建的路径记录在 createdDirs 中，后续请求直接跳过。
     */
    private final ConcurrentHashMap<String, Object>  dirLocks   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> createdDirs = new ConcurrentHashMap<>();

    /**
     * 上传文件到Google Drive
     *
     * @param sourcePath 源文件路径
     * @param remoteName rclone远程配置名称
     * @param targetPath 目标路径
     * @param logConsumer 日志消费者
     * @return 上传结果
     */
    public RcloneResult uploadFile(String sourcePath, String remoteName, String targetPath, Consumer<String> logConsumer) {
        // 预创建目标目录，防止并发上传时 GD 产生同名文件夹
        if (StrUtil.isNotBlank(targetPath)) {
            String dir = targetPath.endsWith("/") ? targetPath.substring(0, targetPath.length() - 1) : targetPath;
            if (!dir.isEmpty()) {
                makeDirectory(remoteName, dir);
            }
        }

        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        command.add("move");
        command.add(sourcePath);
        command.add(remoteName + ":" + targetPath);
        command.add("--config");
        command.add(rcloneConfigPath);

        // 针对高性能服务器的优化配置（13账号轮询 + 10Gbps带宽 + 28核56线程128G内存）
        command.add("--transfers");
        command.add(String.valueOf(concurrentTransfers));  // 使用配置：普通上传并发数

        command.add("--checkers");
        command.add(String.valueOf(checkers));  // 使用配置：文件检查并发数

        command.add("--buffer-size");
        command.add(bufferSize);  // 使用配置：缓冲区大小

        command.add("--drive-chunk-size");
        command.add(driveChunkSize);  // 使用配置：上传块大小

        command.add("--drive-upload-cutoff");
        command.add(driveChunkSize);  // 大文件分块上传阈值（与chunk-size保持一致）

        command.add("--multi-thread-streams");
        command.add(String.valueOf(multiThreadStreams));  // 使用配置：单文件多线程数

        command.add("--fast-list");  // 使用快速列表模式

        command.add("--ignore-checksum");  // 跳过校验和检查，避免因校验问题导致异常

        // 添加超时和重试配置，针对100G大文件调整
        command.add("--timeout");
        command.add("30m");  // 30分钟超时，适合大文件

        command.add("--contimeout");
        command.add("120s");  // 连接超时120秒

        command.add("--retries");
        command.add("3");  // 重试3次

        command.add("--low-level-retries");
        command.add("10");  // 底层重试10次

        command.add("--progress");
        command.add("--stats");
        command.add("1s");
        command.add("--stats-one-line");
        command.add("-v");

        return executeCommand(command, logConsumer);
    }

    /**
     * 上传单个文件到远端指定路径（含文件名）。
     * 使用 rclone moveto，专为单文件设计；targetFilePath 必须包含目标文件名，
     * 例如 "/GD上传目录/SeriesName S01E01.mp4"。
     * 与 uploadFile (rclone move) 不同，moveto 不会把源路径当目录遍历，适合单文件上传。
     */
    public RcloneResult uploadSingleFileTo(String localFilePath, String remoteName, String targetFilePath, Consumer<String> logConsumer) {
        // 预创建目标父目录，防止并发上传时 GD 产生同名文件夹
        if (StrUtil.isNotBlank(targetFilePath) && targetFilePath.contains("/")) {
            String parentDir = targetFilePath.substring(0, targetFilePath.lastIndexOf('/'));
            if (!parentDir.isEmpty()) {
                makeDirectory(remoteName, parentDir);
            }
        }

        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        command.add("moveto");
        command.add(localFilePath);
        command.add(remoteName + ":" + targetFilePath);
        command.add("--config");
        command.add(rcloneConfigPath);

        command.add("--buffer-size");
        command.add(bufferSize);
        command.add("--drive-chunk-size");
        command.add(driveChunkSize);
        command.add("--drive-upload-cutoff");
        command.add(driveChunkSize);
        command.add("--multi-thread-streams");
        command.add(String.valueOf(multiThreadStreams));
        command.add("--ignore-checksum");
        command.add("--timeout");
        command.add("30m");
        command.add("--contimeout");
        command.add("120s");
        command.add("--retries");
        command.add("3");
        command.add("--low-level-retries");
        command.add("10");
        command.add("-v");

        return executeCommand(command, logConsumer);
    }

    /**
     * 使用rclone move上传（支持断点续传，上传成功后删除源文件）
     *
     * @param sourcePath 源文件路径
     * @param remoteName rclone远程配置名称
     * @param targetPath 目标路径
     * @param logConsumer 日志消费者
     * @return 上传结果
     */
    public RcloneResult copyWithResume(String sourcePath, String remoteName, String targetPath, Consumer<String> logConsumer) {
        // 预创建目标目录，防止并发上传时 GD 产生同名文件夹
        if (StrUtil.isNotBlank(targetPath)) {
            String dir = targetPath.endsWith("/") ? targetPath.substring(0, targetPath.length() - 1) : targetPath;
            if (!dir.isEmpty()) {
                makeDirectory(remoteName, dir);
            }
        }

        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        command.add("move");
        command.add(sourcePath);
        command.add(remoteName + ":" + targetPath);
        command.add("--config");
        command.add(rcloneConfigPath);

        // 针对高性能服务器的极限优化配置（13账号轮询 + 10Gbps带宽 + 28核56线程128G内存）
        // 断点续传使用更激进的并发配置
        command.add("--transfers");
        command.add(String.valueOf(concurrentTransfersResume));  // 使用配置：断点续传并发数

        command.add("--checkers");
        command.add(String.valueOf(checkersResume));  // 使用配置：文件检查并发数

        command.add("--buffer-size");
        command.add(bufferSize);  // 使用配置：缓冲区大小

        command.add("--drive-chunk-size");
        command.add(driveChunkSize);  // 使用配置：上传块大小

        command.add("--drive-upload-cutoff");
        command.add(driveChunkSize);  // 大文件分块上传阈值（与chunk-size保持一致）

        command.add("--multi-thread-streams");
        command.add(String.valueOf(multiThreadStreamsResume));  // 使用配置：单文件多线程数

        command.add("--fast-list");  // 使用快速列表模式

        command.add("--ignore-checksum");  // 跳过校验和检查

        // 添加超时和重试配置（与uploadFile保持一致）
        command.add("--timeout");
        command.add("30m");  // 30分钟超时

        command.add("--contimeout");
        command.add("120s");  // 连接超时120秒

        command.add("--retries");
        command.add("3");  // 重试3次

        command.add("--low-level-retries");
        command.add("10");  // 底层重试10次

        command.add("--ignore-existing");  // 跳过已存在的文件

        command.add("--progress");
        command.add("--stats");
        command.add("1s");
        command.add("--stats-one-line");
        command.add("-v");

        return executeCommand(command, logConsumer);
    }

    /**
     * 检查文件是否存在
     *
     * @param remoteName rclone远程配置名称
     * @param filePath 文件路径
     * @return 是否存在
     */
    public boolean checkFileExists(String remoteName, String filePath) {
        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        command.add("lsf");
        command.add(remoteName + ":" + filePath);
        command.add("--config");
        command.add(rcloneConfigPath);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.error("检查文件是否存在失败", e);
            return false;
        }
    }

    /**
     * 获取远程文件大小
     *
     * @param remoteName rclone远程配置名称
     * @param filePath 文件路径
     * @return 文件大小（字节）
     */
    public Long getRemoteFileSize(String remoteName, String filePath) {
        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        command.add("size");
        command.add(remoteName + ":" + filePath);
        command.add("--config");
        command.add(rcloneConfigPath);
        command.add("--json");

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String output = IoUtil.read(reader);
            process.waitFor();

            // 解析JSON输出获取大小（{"count":N,"bytes":M} 或 {"bytes":M,"count":N} 两种顺序均支持）
            if (StrUtil.isNotBlank(output) && output.contains("\"bytes\":")) {
                String sizeStr = output.substring(output.indexOf("\"bytes\":") + 8).trim();
                // 取到最近的 , } 空格 或字符串末尾
                int end = sizeStr.length();
                for (int i = 0; i < sizeStr.length(); i++) {
                    char c = sizeStr.charAt(i);
                    if (c == ',' || c == '}' || c == ' ' || c == '\n' || c == '\r') {
                        end = i;
                        break;
                    }
                }
                return Long.parseLong(sizeStr.substring(0, end).trim());
            }
        } catch (Exception e) {
            log.error("获取远程文件大小失败", e);
        }
        return 0L;
    }

    /**
     * 列出远程配置
     *
     * @return 远程配置列表
     */
    public List<String> listRemotes() {
        List<String> remotes = new ArrayList<>();
        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        command.add("listremotes");
        command.add("--config");
        command.add(rcloneConfigPath);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (StrUtil.isNotBlank(line)) {
                    remotes.add(line.replace(":", "").trim());
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.error("列出远程配置失败", e);
        }
        return remotes;
    }

    /**
     * 执行rclone命令
     *
     * @param command 命令列表
     * @param logConsumer 日志消费者
     * @return 执行结果
     */
    private RcloneResult executeCommand(List<String> command, Consumer<String> logConsumer) {
        Process process = null;
        try {
            String commandStr = String.join(" ", command);
            log.info("执行rclone命令: {}", commandStr);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();

            log.info("rclone进程已启动，等待输出...");

            // 读取输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String line;
            StringBuilder output = new StringBuilder();

            // 记录最后一次输出的时间
            final long[] lastOutputTime = {System.currentTimeMillis()};
            final boolean[] hasAnyOutput = {false};

            // 使用单独的线程读取输出，避免阻塞
            final Process finalProcess = process;
            final StringBuilder finalOutput = output;
            final boolean[] quotaExceededDetected = {false};
            Thread outputThread = new Thread(() -> {
                try {
                    String outputLine;
                    while ((outputLine = reader.readLine()) != null) {
                        finalOutput.append(outputLine).append("\n");
                        lastOutputTime[0] = System.currentTimeMillis();
                        hasAnyOutput[0] = true;
                        if (logConsumer != null) {
                            logConsumer.accept(outputLine);
                        }
                        log.info("rclone输出: {}", outputLine);

                        // 实时检测配额超限错误
                        if (!quotaExceededDetected[0] &&
                            (outputLine.contains("User rate limit exceeded") ||
                             outputLine.contains("userRateLimitExceeded") ||
                             outputLine.contains("quota exceeded"))) {
                            quotaExceededDetected[0] = true;
                            log.error("========== 实时检测到配额超限，立即终止rclone进程 ==========");
                            log.error("触发行: {}", outputLine);
                            log.error("===========================================================");
                            // 立即终止进程，不等待重试
                            if (finalProcess.isAlive()) {
                                finalProcess.destroyForcibly();
                                log.error("已强制终止rclone进程");
                            }
                        }
                    }
                } catch (IOException e) {
                    log.error("读取rclone输出异常", e);
                }
            });
            outputThread.start();

            // 等待进程完成（不设置超时，让rclone自己处理）
            process.waitFor();

            // 等待输出线程完成
            outputThread.join(5000);

            int exitCode = process.exitValue();
            String outputStr = output.toString();

            log.info("rclone命令执行完成，退出码: {}, 输出长度: {} 字符", exitCode, outputStr.length());

            // 只检查是否是配额超限错误（无论exitCode是什么）
            boolean quotaExceeded = outputStr.contains("User rate limit exceeded")
                || outputStr.contains("userRateLimitExceeded")
                || outputStr.contains("quota exceeded");

            log.info("配额检测结果: quotaExceeded={}, exitCode={}", quotaExceeded, exitCode);

            // 如果检测到配额超限，返回失败并标记为配额问题
            if (quotaExceeded) {
                log.error("========== RcloneUtil: 检测到配额超限 ==========");
                log.error("退出码: {}", exitCode);
                log.error("输出内容包含配额超限关键字");
                log.error("完整输出:\n{}", outputStr);
                log.error("失败的命令: {}", commandStr);
                log.error("返回 RcloneResult.failure(outputStr, true)");
                log.error("================================================");
                return RcloneResult.failure(outputStr, true);
            }

            // 根据退出码判断成功或失败（不再检测IP封禁）
            if (exitCode == 0) {
                log.info("rclone命令执行成功，退出码: {}", exitCode);
                return RcloneResult.success();
            } else {
                log.error("rclone命令执行失败，退出码: {}, 完整输出:\n{}", exitCode, outputStr);
                log.error("失败的命令: {}", commandStr);
                // 非配额问题的失败，不封禁账号
                return RcloneResult.failure(outputStr, false);
            }
        } catch (IOException | InterruptedException e) {
            log.error("执行rclone命令异常", e);
            if (logConsumer != null) {
                logConsumer.accept("错误: " + e.getMessage());
            }

            // 确保进程被终止
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }

            return RcloneResult.failure(e.getMessage(), false);
        }
    }

    /**
     * 测试rclone配置
     *
     * @param remoteName 远程配置名称
     * @return 是否可用
     */
    public boolean testRemote(String remoteName) {
        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        command.add("lsd");
        command.add(remoteName + ":");
        command.add("--config");
        command.add(rcloneConfigPath);
        command.add("--max-depth");
        command.add("1");

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.error("测试rclone配置失败", e);
            return false;
        }
    }

    /**
     * 探测账号是否可用（上传小文件测试）
     * 在每次成功上传后调用，检测账号是否被封禁
     *
     * @param remoteName rclone远程配置名称
     * @param targetPath 目标路径
     * @return 探测结果（包含是否可用和是否配额超限）
     */
    public ProbeResult probeAccount(String remoteName, String targetPath) {
        log.info("开始探测账号: remoteName={}, targetPath={}", remoteName, targetPath);

        java.io.File tempFile = null;
        try {
            // 创建1MB临时测试文件
            tempFile = java.io.File.createTempFile("probe_", ".tmp");
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(tempFile, "rw")) {
                raf.setLength(1024 * 1024); // 1MB
            }

            String probeFileName = ".probe_" + System.currentTimeMillis() + ".tmp";
            String remoteProbeFile = remoteName + ":" + targetPath + "/" + probeFileName;

            // 上传测试文件
            List<String> uploadCommand = new ArrayList<>();
            uploadCommand.add(rclonePath);
            uploadCommand.add("copy");
            uploadCommand.add(tempFile.getAbsolutePath());
            uploadCommand.add(remoteProbeFile);
            uploadCommand.add("--config");
            uploadCommand.add(rcloneConfigPath);
            uploadCommand.add("--timeout");
            uploadCommand.add("2m");
            uploadCommand.add("--contimeout");
            uploadCommand.add("60s");
            uploadCommand.add("--retries");
            uploadCommand.add("1");
            uploadCommand.add("-v");

            log.info("执行探测上传命令: {}", String.join(" ", uploadCommand));

            ProcessBuilder pb = new ProcessBuilder(uploadCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.debug("探测输出: {}", line);
            }

            int exitCode = process.waitFor();
            String outputStr = output.toString();

            log.info("探测上传完成，退出码: {}", exitCode);

            // 检查是否配额超限
            boolean quotaExceeded = outputStr.contains("User rate limit exceeded")
                || outputStr.contains("userRateLimitExceeded")
                || outputStr.contains("quota exceeded");

            if (quotaExceeded) {
                log.warn("探测检测到配额超限: remoteName={}", remoteName);
                return new ProbeResult(false, true, "配额超限");
            }

            if (exitCode != 0) {
                log.warn("探测上传失败，但非配额问题: remoteName={}, exitCode={}", remoteName, exitCode);
                return new ProbeResult(false, false, "上传失败: " + outputStr.substring(0, Math.min(200, outputStr.length())));
            }

            // 上传成功，删除测试文件
            try {
                List<String> deleteCommand = new ArrayList<>();
                deleteCommand.add(rclonePath);
                deleteCommand.add("delete");
                deleteCommand.add(remoteProbeFile);
                deleteCommand.add("--config");
                deleteCommand.add(rcloneConfigPath);

                ProcessBuilder deletePb = new ProcessBuilder(deleteCommand);
                Process deleteProcess = deletePb.start();
                deleteProcess.waitFor();
                log.info("已删除探测文件: {}", probeFileName);
            } catch (Exception e) {
                log.warn("删除探测文件失败，但不影响探测结果", e);
            }

            log.info("探测成功: 账号可用");
            return new ProbeResult(true, false, "账号可用");

        } catch (Exception e) {
            log.error("探测账号异常", e);
            return new ProbeResult(false, false, "探测异常: " + e.getMessage());
        } finally {
            // 清理本地临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * 探测结果
     */
    public static class ProbeResult {
        private final boolean available;
        private final boolean quotaExceeded;
        private final String message;

        public ProbeResult(boolean available, boolean quotaExceeded, String message) {
            this.available = available;
            this.quotaExceeded = quotaExceeded;
            this.message = message;
        }

        public boolean isAvailable() {
            return available;
        }

        public boolean isQuotaExceeded() {
            return quotaExceeded;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 列出目录内容（返回 rclone lsjson 原始 JSON 字符串）
     *
     * @param remoteName rclone远程配置名称
     * @param path 目录路径（空字符串表示根目录）
     * @return JSON字符串
     */
    public String listJson(String remoteName, String path) {
        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        command.add("lsjson");
        String remotePath = StrUtil.isBlank(path) ? remoteName + ":" : remoteName + ":" + path;
        command.add(remotePath);
        command.add("--fast-list");
        command.add("--config");
        command.add(rcloneConfigPath);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            // 单独读 stderr，避免阻塞
            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
            StringBuilder errOutput = new StringBuilder();
            Thread errThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = errReader.readLine()) != null) {
                        errOutput.append(line).append("\n");
                    }
                } catch (IOException ignored) {}
            });
            errThread.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String output = IoUtil.read(reader);
            int exitCode = process.waitFor();
            errThread.join(3000);

            if (exitCode != 0 || StrUtil.isBlank(output)) {
                // exitCode=3 表示目录不存在，属于可预期的情况（如路径含特殊字符），降为 WARN
                if (exitCode == 3) {
                    log.warn("lsjson目录不存在(跳过): remoteName={}, path={}", remoteName, path);
                } else {
                    log.error("lsjson执行失败: remoteName={}, path={}, exitCode={}, stderr={}", remoteName, path, exitCode, errOutput.toString().trim());
                }
                return "[]";
            }
            return output;
        } catch (Exception e) {
            log.error("列出目录内容失败: remoteName={}, path={}", remoteName, path, e);
            return "[]";
        }
    }

    /**
     * 递归列出目录内所有文件（只含文件，不含目录），返回 rclone lsjson 原始 JSON 字符串
     *
     * @param remoteName rclone远程配置名称
     * @param path 目录路径（空字符串表示根目录）
     * @return JSON字符串，元素 Path 相对于 path 参数
     */
    public String listJsonRecursive(String remoteName, String path) {
        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        command.add("lsjson");
        String remotePath = StrUtil.isBlank(path) ? remoteName + ":" : remoteName + ":" + path;
        command.add(remotePath);
        command.add("--recursive");
        command.add("--files-only");
        command.add("--fast-list");
        command.add("--config");
        command.add(rcloneConfigPath);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
            StringBuilder errOutput = new StringBuilder();
            Thread errThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = errReader.readLine()) != null) {
                        errOutput.append(line).append("\n");
                    }
                } catch (IOException ignored) {}
            });
            errThread.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String output = IoUtil.read(reader);
            int exitCode = process.waitFor();
            errThread.join(3000);

            if (exitCode != 0) {
                if (exitCode == 3) {
                    log.warn("lsjson recursive 目录不存在(跳过): remoteName={}, path={}", remoteName, path);
                } else {
                    log.error("lsjson recursive 失败: remoteName={}, path={}, exitCode={}, stderr={}",
                            remoteName, path, exitCode, errOutput.toString().trim());
                }
                return "[]";
            }
            return StrUtil.isBlank(output) ? "[]" : output;
        } catch (Exception e) {
            log.error("递归列出目录失败: remoteName={}, path={}", remoteName, path, e);
            return "[]";
        }
    }

    /**
     * 删除远程文件
     *
     * @param remoteName rclone远程配置名称
     * @param filePath 文件路径
     * @return 是否成功
     */
    public boolean deleteFile(String remoteName, String filePath) {
        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        command.add("deletefile");
        command.add(remoteName + ":" + filePath);
        command.add("--config");
        command.add(rcloneConfigPath);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.error("删除文件失败: remoteName={}, filePath={}", remoteName, filePath, e);
            return false;
        }
    }

    /**
     * 删除远程目录（递归）
     *
     * @param remoteName rclone远程配置名称
     * @param dirPath 目录路径
     * @return 是否成功
     */
    public boolean purgeDirectory(String remoteName, String dirPath) {
        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        command.add("purge");
        command.add(remoteName + ":" + dirPath);
        command.add("--config");
        command.add(rcloneConfigPath);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.error("删除目录失败: remoteName={}, dirPath={}", remoteName, dirPath, e);
            return false;
        }
    }

    /**
     * 移动/重命名文件或目录
     *
     * @param remoteName rclone远程配置名称
     * @param oldPath 原路径
     * @param newPath 新路径
     * @param isDir 是否为目录
     * @return 是否成功
     */
    public boolean moveItem(String remoteName, String oldPath, String newPath, boolean isDir) {
        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        if (isDir) {
            command.add("move");
            command.add(remoteName + ":" + oldPath);
            command.add(remoteName + ":" + newPath);
            command.add("--delete-empty-src-dirs");
        } else {
            // 先确保目标父目录存在，再用 server-side moveto（同盘重命名，无需下载/上传）
            String targetParent = newPath.contains("/") ? newPath.substring(0, newPath.lastIndexOf('/')) : "";
            if (!targetParent.isEmpty()) {
                makeDirectory(remoteName, targetParent);
            }
            command.add("moveto");
            command.add(remoteName + ":" + oldPath);
            command.add(remoteName + ":" + newPath);
        }
        command.add("--config");
        command.add(rcloneConfigPath);

        // 使用 executeCommand 统一处理：读取输出流（防止大文件时缓冲区满死锁）并打印日志
        RcloneResult result = executeCommand(command, line -> log.debug("rclone moveto: {}", line));
        if (!result.isSuccess()) {
            log.error("移动/重命名失败: remoteName={}, oldPath={}, newPath={}, err={}",
                    remoteName, oldPath, newPath, result.getErrorMessage());
        }
        return result.isSuccess();
    }

    /**
     * 创建远程目录（带去重：同一 remote:path 只会调一次 rclone mkdir，防止 GD 产生同名文件夹）
     *
     * @param remoteName rclone远程配置名称
     * @param path 目录路径
     * @return 是否成功
     */
    public boolean makeDirectory(String remoteName, String path) {
        if (StrUtil.isBlank(path)) return true;

        String key = remoteName + ":" + path;

        // 快速路径：已创建过则直接返回
        if (createdDirs.containsKey(key)) {
            log.debug("目录已在缓存中，跳过创建: {}", key);
            return true;
        }

        // per-path 锁：同一路径只有一个线程执行 mkdir
        Object lock = dirLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            // double-check
            if (createdDirs.containsKey(key)) {
                log.debug("目录已在缓存中(double-check)，跳过创建: {}", key);
                return true;
            }

            log.info("========== 开始创建目录 ==========");
            log.info("远程名称: {}", remoteName);
            log.info("目录路径: {}", path);

            // 先检查目录是否已存在（使用lsd检查目录本身）
            try {
                log.info(">>> 步骤1: 检查目录是否已存在");
                List<String> checkCmd = new ArrayList<>();
                checkCmd.add(rclonePath);
                checkCmd.add("lsd");
                checkCmd.add(remoteName + ":" + path);
                checkCmd.add("--config");
                checkCmd.add(rcloneConfigPath);
                checkCmd.add("--max-depth");
                checkCmd.add("1");

                log.info("检查命令: {}", String.join(" ", checkCmd));
                ProcessBuilder checkPb = new ProcessBuilder(checkCmd);
                checkPb.redirectErrorStream(true);
                Process checkProcess = checkPb.start();

                BufferedReader checkReader = new BufferedReader(new InputStreamReader(checkProcess.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder checkOutput = new StringBuilder();
                String line;
                while ((line = checkReader.readLine()) != null) {
                    checkOutput.append(line).append("\n");
                }

                int checkExit = checkProcess.waitFor();
                log.info("检查结果 - 退出码: {}, 输出: {}", checkExit, checkOutput.toString().trim());

                if (checkExit == 0) {
                    // 目录已存在
                    log.info("✓ 目录已存在，无需创建");
                    createdDirs.put(key, Boolean.TRUE);
                    log.info("=====================================");
                    return true;
                } else {
                    log.info("目录不存在，需要创建");
                }
            } catch (Exception e) {
                log.warn("检查目录存在性失败，继续创建: {}", e.getMessage());
            }

            log.info(">>> 步骤2: 创建目录");
            List<String> command = new ArrayList<>();
            command.add(rclonePath);
            command.add("mkdir");
            command.add(remoteName + ":" + path);
            command.add("--config");
            command.add(rcloneConfigPath);

            try {
                log.info("创建命令: {}", String.join(" ", command));
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder output = new StringBuilder();
                String mkdirLine;
                while ((mkdirLine = reader.readLine()) != null) {
                    output.append(mkdirLine).append("\n");
                    log.info("mkdir输出: {}", mkdirLine);
                }

                int exitCode = process.waitFor();
                log.info("创建结果 - 退出码: {}", exitCode);

                if (exitCode == 0) {
                    log.info("✓ 目录创建成功");
                    createdDirs.put(key, Boolean.TRUE);
                    log.info("=====================================");
                    return true;
                } else {
                    log.error("✗ 目录创建失败 - 退出码: {}, 输出: {}", exitCode, output.toString().trim());
                    log.info("=====================================");
                    return false;
                }
            } catch (Exception e) {
                log.error("✗ 目录创建异常: remoteName={}, path={}", remoteName, path, e);
                log.info("=====================================");
                return false;
            }
        }
    }

    /**
     * 重命名远程文件（使用 moveto 命令）
     *
     * @param remoteName rclone远程配置名称
     * @param oldPath 原文件路径
     * @param newPath 新文件路径
     * @return 重命名结果
     */
    public RcloneResult renameFile(String remoteName, String oldPath, String newPath) {
        // 预创建目标父目录，防止跨目录重命名时 GD 产生同名文件夹
        if (StrUtil.isNotBlank(newPath) && newPath.contains("/")) {
            String parentDir = newPath.substring(0, newPath.lastIndexOf('/'));
            if (!parentDir.isEmpty()) {
                makeDirectory(remoteName, parentDir);
            }
        }

        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        command.add("moveto");
        command.add(remoteName + ":" + oldPath);
        command.add(remoteName + ":" + newPath);
        command.add("--config");
        command.add(rcloneConfigPath);
        command.add("-v");

        log.info("执行rclone重命名: {} -> {}", oldPath, newPath);
        return executeCommand(command, line -> log.debug("重命名输出: {}", line));
    }

    /**
     * 对 Google Drive 去重：合并同名文件夹、清理重复文件。
     * rclone dedupe 会将同名目录的内容合并到一个目录中，删除空的重复目录。
     * 对于同名文件使用 newest 策略（保留最新的）。
     *
     * @param remoteName rclone远程配置名称
     * @param path       目录路径（空字符串表示对整个远端去重）
     * @param logConsumer 日志消费者（可为 null）
     * @return 执行结果
     */
    public RcloneResult dedupe(String remoteName, String path, Consumer<String> logConsumer) {
        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        command.add("dedupe");
        command.add("--dedupe-mode");
        command.add("newest");
        String remotePath = StrUtil.isBlank(path) ? remoteName + ":" : remoteName + ":" + path;
        command.add(remotePath);
        command.add("--config");
        command.add(rcloneConfigPath);
        command.add("-v");

        log.info("执行rclone dedupe: remotePath={}", remotePath);
        return executeCommand(command, logConsumer != null ? logConsumer : line -> log.info("dedupe: {}", line));
    }
}
