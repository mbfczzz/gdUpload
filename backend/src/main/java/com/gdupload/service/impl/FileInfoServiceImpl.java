package com.gdupload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gdupload.entity.FileInfo;
import com.gdupload.entity.UploadRecord;
import com.gdupload.mapper.FileInfoMapper;
import com.gdupload.mapper.UploadRecordMapper;
import com.gdupload.service.IFileInfoService;
import com.gdupload.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件信息服务实现
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileInfoServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo> implements IFileInfoService {

    private final UploadRecordMapper uploadRecordMapper;

    @Override
    public List<FileInfo> scanDirectory(String directoryPath, boolean recursive) {
        List<FileInfo> fileList = new ArrayList<>();
        Path directory = Paths.get(directoryPath);

        log.info("开始扫描目录: path={}, recursive={}", directoryPath, recursive);
        log.info("Path对象创建完成: {}", directory.toAbsolutePath());
        log.info("Files.exists(): {}", Files.exists(directory));
        log.info("Files.isDirectory(): {}", Files.isDirectory(directory));
        log.info("Files.isReadable(): {}", Files.isReadable(directory));

        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            log.error("目录不存在或不是目录: {}, exists={}, isDirectory={}",
                directoryPath, Files.exists(directory), Files.isDirectory(directory));
            return fileList;
        }

        // 使用源路径的父目录作为基础路径，这样源目录名会包含在相对路径中
        // 例如：源路径 /data/emby/藏海花 (2024)，basePath = /data/emby
        //   文件 /data/emby/藏海花 (2024)/ep01.mp4 → relativePath = "藏海花 (2024)"
        // 例如：源路径 /data/emby，basePath = /data
        //   文件 /data/emby/藏海花 (2024)/ep01.mp4 → relativePath = "emby/藏海花 (2024)"
        Path parentDir = directory.toAbsolutePath().getParent();
        String normalizedBasePath;
        if (parentDir != null) {
            normalizedBasePath = parentDir.toString().replaceAll("[/\\\\]+$", "");
        } else {
            // 根目录情况，不太可能出现，兜底用源路径本身
            normalizedBasePath = directoryPath.replaceAll("[/\\\\]+$", "");
        }
        log.info("规范化基础路径（父目录）: {}", normalizedBasePath);

        log.info("准备调用 scanDirectoryRecursive...");
        scanDirectoryRecursive(directory, normalizedBasePath, fileList, recursive);
        log.info("scanDirectoryRecursive 调用完成");

        log.info("扫描目录完成: path={}, recursive={}, fileCount={}", directoryPath, recursive, fileList.size());

        return fileList;
    }

    private void scanDirectoryRecursive(Path directory, String basePath, List<FileInfo> fileList, boolean recursive) {
        log.info("扫描目录: {}", directory.toAbsolutePath());

        int fileCount = 0;
        int dirCount = 0;
        int videoCount = 0;
        int skippedCount = 0;
        int totalItems = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            log.info("开始遍历目录内容");

            for (Path entry : stream) {
                totalItems++;
                try {
                    String fileName = entry.getFileName().toString();
                    log.info("处理第 {}: {}", totalItems, fileName);

                    if (Files.isRegularFile(entry)) {
                        fileCount++;
                        log.info("  -> 是文件");

                        // 只允许mp4和mkv文件
                        String fileNameLower = fileName.toLowerCase();
                        if (!fileNameLower.endsWith(".mp4") && !fileNameLower.endsWith(".mkv")) {
                            log.debug("跳过非视频文件: {}", fileName);
                            skippedCount++;
                            continue;
                        }

                        videoCount++;

                        // 计算相对路径
                        String parentPath = entry.getParent().toAbsolutePath().toString();
                        String relativePath = calculateRelativePath(basePath, parentPath);

                        FileInfo fileInfo = new FileInfo();
                        fileInfo.setFileName(fileName);
                        fileInfo.setFilePath(entry.toAbsolutePath().toString());
                        fileInfo.setRelativePath(relativePath);
                        fileInfo.setFileSize(Files.size(entry));
                        fileInfo.setStatus(0); // 待上传
                        fileInfo.setCreateTime(DateTimeUtil.now());

                        fileList.add(fileInfo);

                        log.info("扫描到视频文件: fileName={}, relativePath={}, size={}",
                            fileName, relativePath, Files.size(entry));
                    } else if (Files.isDirectory(entry)) {
                        dirCount++;
                        log.info("  -> 是目录");
                        if (recursive) {
                            log.info("递归扫描子目录: {}", entry.toAbsolutePath());
                            scanDirectoryRecursive(entry, basePath, fileList, recursive);
                        } else {
                            log.info("跳过子目录（递归扫描未开启）: {}", entry.toAbsolutePath());
                        }
                    }
                } catch (Exception e) {
                    log.error("处理文件/目录时出错: {}, 错误: {}", entry.getFileName(), e.getMessage(), e);
                }
            }

            log.info("目录扫描统计: path={}, 总项目数={}, 文件总数={}, 子目录数={}, 视频文件数={}, 跳过文件数={}",
                directory.toAbsolutePath(), totalItems, fileCount, dirCount, videoCount, skippedCount);
        } catch (IOException e) {
            log.error("无法读取目录内容: {}, 错误: {}", directory.toAbsolutePath(), e.getMessage(), e);
        }
    }

    /**
     * 计算相对路径
     *
     * @param basePath 基础路径（任务源路径）
     * @param filePath 文件所在目录的绝对路径
     * @return 相对路径，如果文件在根目录则返回空字符串
     */
    private String calculateRelativePath(String basePath, String filePath) {
        try {
            // 规范化路径（统一使用正斜杠）
            String normalizedBasePath = basePath.replace("\\", "/");
            String normalizedFilePath = filePath.replace("\\", "/");

            // 移除末尾的斜杠
            normalizedBasePath = normalizedBasePath.replaceAll("/+$", "");
            normalizedFilePath = normalizedFilePath.replaceAll("/+$", "");

            // 如果文件路径等于基础路径，说明文件在根目录
            if (normalizedFilePath.equals(normalizedBasePath)) {
                return "";
            }

            // 如果文件路径以基础路径开头，计算相对路径
            if (normalizedFilePath.startsWith(normalizedBasePath + "/")) {
                String relativePath = normalizedFilePath.substring(normalizedBasePath.length() + 1);
                log.debug("计算相对路径: basePath={}, filePath={}, relativePath={}",
                    normalizedBasePath, normalizedFilePath, relativePath);
                return relativePath;
            }

            // 否则返回空字符串
            log.warn("文件路径不在基础路径下: basePath={}, filePath={}", normalizedBasePath, normalizedFilePath);
            return "";
        } catch (Exception e) {
            log.error("计算相对路径失败: basePath={}, filePath={}", basePath, filePath, e);
            return "";
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchSaveFiles(Long taskId, List<FileInfo> fileList) {
        for (FileInfo fileInfo : fileList) {
            fileInfo.setTaskId(taskId);
        }

        boolean saved = this.saveBatch(fileList);

        if (saved) {
            log.info("批量保存文件信息成功: taskId={}, count={}", taskId, fileList.size());
        }

        return saved;
    }

    @Override
    public List<FileInfo> getTaskFiles(Long taskId) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileInfo::getTaskId, taskId)
               .orderByAsc(FileInfo::getStatus)  // 按状态排序：0待上传, 1上传中, 2已上传, 3失败
               .orderByAsc(FileInfo::getFileName);  // 同状态下按文件名排序

        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateFileStatus(Long fileId, Integer status, String errorMessage) {
        FileInfo fileInfo = this.getById(fileId);
        if (fileInfo == null) {
            return false;
        }

        fileInfo.setStatus(status);

        if (status == 1) {
            // 上传中
            fileInfo.setUploadStartTime(DateTimeUtil.now());
        } else if (status == 2) {
            // 已上传
            fileInfo.setUploadEndTime(DateTimeUtil.now());
        }

        if (errorMessage != null) {
            fileInfo.setErrorMessage(errorMessage);
        }

        return this.updateById(fileInfo);
    }

    @Override
    public List<FileInfo> getPendingFiles(Long taskId) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileInfo::getTaskId, taskId)
               .in(FileInfo::getStatus, 0, 3) // 待上传(0) 或 失败(3)
               .orderByAsc(FileInfo::getStatus)  // 按状态排序
               .orderByAsc(FileInfo::getFileName);  // 同状态下按文件名排序

        return this.list(wrapper);
    }

    @Override
    public List<FileInfo> getFailedFiles(Long taskId) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileInfo::getTaskId, taskId)
               .eq(FileInfo::getStatus, 3) // 失败
               .orderByAsc(FileInfo::getFileName);  // 按文件名排序

        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markFileAsUploaded(Long fileId, Long accountId) {
        FileInfo fileInfo = this.getById(fileId);
        if (fileInfo == null) {
            return false;
        }

        // 更新文件状态
        fileInfo.setStatus(2); // 已上传
        fileInfo.setUploadAccountId(accountId);
        fileInfo.setUploadEndTime(DateTimeUtil.now());
        boolean updated = this.updateById(fileInfo);

        if (updated) {
            // 插入上传记录（用于滚动24小时配额计算）
            UploadRecord record = new UploadRecord();
            record.setTaskId(fileInfo.getTaskId());
            record.setAccountId(accountId);
            record.setFileId(fileId);
            record.setUploadSize(fileInfo.getFileSize());
            record.setUploadTime(DateTimeUtil.now());
            record.setStatus(1); // 成功
            record.setCreateTime(DateTimeUtil.now());

            uploadRecordMapper.insert(record);
            log.info("插入上传记录: fileId={}, accountId={}, size={}", fileId, accountId, fileInfo.getFileSize());
        }

        return updated;
    }

    @Override
    public boolean checkFileExists(String filePath, String md5) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileInfo::getFilePath, filePath)
               .eq(FileInfo::getStatus, 2); // 已上传

        if (md5 != null && !md5.trim().isEmpty()) {
            wrapper.eq(FileInfo::getFileMd5, md5);
        }

        return this.count(wrapper) > 0;
    }
}
