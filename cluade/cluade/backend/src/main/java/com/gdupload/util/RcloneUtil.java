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

    @Value("${app.rclone.concurrent-transfers:3}")
    private Integer concurrentTransfers;

    @Value("${app.rclone.buffer-size:16M}")
    private String bufferSize;

    @Value("${app.rclone.timeout:3600}")
    private Integer timeout;

    /**
     * 上传文件到Google Drive
     *
     * @param sourcePath 源文件路径
     * @param remoteName rclone远程配置名称
     * @param targetPath 目标路径
     * @param logConsumer 日志消费者
     * @return 是否成功
     */
    public boolean uploadFile(String sourcePath, String remoteName, String targetPath, Consumer<String> logConsumer) {
        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        command.add("copy");
        command.add(sourcePath);
        command.add(remoteName + ":" + targetPath);
        command.add("--config");
        command.add(rcloneConfigPath);

        // 针对28核56线程128G内存10g带宽的高性能配置
        command.add("--transfers");
        command.add("16");  // 并发传输数，充分利用多核和带宽

        command.add("--checkers");
        command.add("32");  // 文件检查并发数，利用多核CPU

        command.add("--buffer-size");
        command.add("256M");  // 增大缓冲区，利用大内存

        command.add("--drive-chunk-size");
        command.add("256M");  // 增大上传块大小，提高大文件上传效率

        command.add("--drive-upload-cutoff");
        command.add("256M");  // 大文件分块上传阈值

        command.add("--multi-thread-streams");
        command.add("8");  // 单文件多线程上传，充分利用带宽

        command.add("--fast-list");  // 使用快速列表模式

        // 移除 --use-mmap，可能在某些系统上导致卡住
        // command.add("--use-mmap");  // 使用内存映射，提高大文件读取性能

        command.add("--progress");
        command.add("--stats");
        command.add("1s");
        command.add("--stats-one-line");
        command.add("-v");

        return executeCommand(command, logConsumer);
    }

    /**
     * 使用rclone copy上传（支持断点续传）
     *
     * @param sourcePath 源文件路径
     * @param remoteName rclone远程配置名称
     * @param targetPath 目标路径
     * @param logConsumer 日志消费者
     * @return 是否成功
     */
    public boolean copyWithResume(String sourcePath, String remoteName, String targetPath, Consumer<String> logConsumer) {
        List<String> command = new ArrayList<>();
        command.add(rclonePath);
        command.add("copy");
        command.add(sourcePath);
        command.add(remoteName + ":" + targetPath);
        command.add("--config");
        command.add(rcloneConfigPath);

        // 针对28核56线程128G内存10g带宽的高性能配置
        command.add("--transfers");
        command.add("16");

        command.add("--checkers");
        command.add("32");

        command.add("--buffer-size");
        command.add("256M");

        command.add("--drive-chunk-size");
        command.add("256M");

        command.add("--drive-upload-cutoff");
        command.add("256M");

        command.add("--multi-thread-streams");
        command.add("8");

        command.add("--fast-list");

        // 移除 --use-mmap，可能在某些系统上导致卡住
        // command.add("--use-mmap");

        command.add("--ignore-existing"); // 跳过已存在的文件

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

            // 解析JSON输出获取大小
            // 简化处理，实际应使用JSON库解析
            if (StrUtil.isNotBlank(output) && output.contains("\"bytes\":")) {
                String sizeStr = output.substring(output.indexOf("\"bytes\":") + 8);
                sizeStr = sizeStr.substring(0, sizeStr.indexOf(","));
                return Long.parseLong(sizeStr.trim());
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
     * @return 是否成功
     */
    private boolean executeCommand(List<String> command, Consumer<String> logConsumer) {
        try {
            log.info("执行rclone命令: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                if (logConsumer != null) {
                    logConsumer.accept(line);
                }
                // 改为 info 级别，确保能看到输出
                log.info("rclone输出: {}", line);
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("rclone命令执行成功，退出码: {}", exitCode);
            } else {
                log.error("rclone命令执行失败，退出码: {}, 完整输出:\n{}", exitCode, output.toString());
            }

            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            log.error("执行rclone命令异常", e);
            if (logConsumer != null) {
                logConsumer.accept("错误: " + e.getMessage());
            }
            return false;
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
}
