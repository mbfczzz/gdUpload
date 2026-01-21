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
import java.nio.charset.StandardCharsets;
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
        File directory = new File(directoryPath);

        if (!directory.exists() || !directory.isDirectory()) {
            log.error("目录不存在或不是目录: {}", directoryPath);
            return fileList;
        }

        scanDirectoryRecursive(directory, directoryPath, fileList, recursive);

        log.info("扫描目录完成: path={}, fileCount={}", directoryPath, fileList.size());

        return fileList;
    }

    private void scanDirectoryRecursive(File directory, String basePath, List<FileInfo> fileList, boolean recursive) {
        File[] files = directory.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile()) {
                // 获取文件名并确保使用UTF-8编码
                String fileName = fixEncoding(file.getName());
                String filePath = fixEncoding(file.getAbsolutePath());

                // 只允许mp4和mkv文件
                String fileNameLower = fileName.toLowerCase();
                if (!fileNameLower.endsWith(".mp4") && !fileNameLower.endsWith(".mkv")) {
                    log.debug("跳过非视频文件: {}", fileName);
                    continue;
                }

                FileInfo fileInfo = new FileInfo();
                fileInfo.setFileName(fileName);
                fileInfo.setFilePath(filePath);
                fileInfo.setFileSize(file.length());
                fileInfo.setStatus(0); // 待上传
                fileInfo.setCreateTime(DateTimeUtil.now());

                fileList.add(fileInfo);

                log.debug("扫描到文件: fileName={}, size={}", fileName, file.length());
            } else if (file.isDirectory() && recursive) {
                scanDirectoryRecursive(file, basePath, fileList, recursive);
            }
        }
    }

    /**
     * 修复文件名编码问题
     * 将错误编码的字符串转换为UTF-8
     */
    private String fixEncoding(String str) {
        try {
            // 检测是否是乱码（包含问号或其他异常字符）
            if (str.contains("�") || str.contains("?")) {
                // 尝试使用ISO-8859-1重新编码为UTF-8
                byte[] bytes = str.getBytes(StandardCharsets.ISO_8859_1);
                return new String(bytes, StandardCharsets.UTF_8);
            }

            // 尝试检测是否是错误的编码
            // 如果字符串可以用ISO-8859-1编码后再用UTF-8解码得到正确的中文，则进行转换
            byte[] bytes = str.getBytes(StandardCharsets.ISO_8859_1);
            String fixed = new String(bytes, StandardCharsets.UTF_8);

            // 简单验证：如果转换后的字符串看起来更合理（没有乱码字符），则使用转换后的
            if (!fixed.contains("�") && !fixed.contains("�")) {
                return fixed;
            }

            return str;
        } catch (Exception e) {
            log.warn("修复文件名编码失败: {}", str, e);
            return str;
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
