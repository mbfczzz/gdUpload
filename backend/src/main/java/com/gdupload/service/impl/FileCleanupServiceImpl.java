package com.gdupload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gdupload.entity.FileInfo;
import com.gdupload.mapper.FileInfoMapper;
import com.gdupload.service.IFileCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件清理服务实现
 *
 * @author GD Upload Manager
 * @since 2026-01-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileCleanupServiceImpl implements IFileCleanupService {

    private final FileInfoMapper fileInfoMapper;

    @Override
    public Map<String, Object> cleanupUploadedFiles() {
        log.info("开始清理已上传文件的物理文件");

        // 查询所有已上传成功的文件（status=2）
        QueryWrapper<FileInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 2); // 2=已上传
        queryWrapper.orderByAsc("task_id", "id");

        List<FileInfo> uploadedFiles = fileInfoMapper.selectList(queryWrapper);

        int totalCount = uploadedFiles.size();
        int successCount = 0;
        int notExistCount = 0;
        int failCount = 0;
        long deletedSize = 0;

        // 遍历删除
        for (FileInfo fileInfo : uploadedFiles) {
            String filePath = fileInfo.getFilePath();
            try {
                Path path = Paths.get(filePath);
                if (Files.exists(path)) {
                    Files.delete(path);
                    successCount++;
                    deletedSize += fileInfo.getFileSize();
                    log.info("✓ [任务{}] 已删除: {} ({})",
                        fileInfo.getTaskId(), filePath, formatSize(fileInfo.getFileSize()));
                } else {
                    notExistCount++;
                    log.debug("⊘ [任务{}] 文件不存在（已被删除）: {}", fileInfo.getTaskId(), filePath);
                }
            } catch (Exception e) {
                failCount++;
                log.error("✗ [任务{}] 删除失败: {} - {}", fileInfo.getTaskId(), filePath, e.getMessage());
            }
        }

        // 返回统计结果
        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", totalCount);
        result.put("successCount", successCount);
        result.put("notExistCount", notExistCount);
        result.put("failCount", failCount);
        result.put("deletedSize", deletedSize);
        result.put("deletedSizeFormatted", formatSize(deletedSize));

        log.info("清理完成 - 总记录: {}, 成功删除: {}, 文件不存在: {}, 失败: {}, 释放空间: {}",
            totalCount, successCount, notExistCount, failCount, formatSize(deletedSize));

        return result;
    }

    /**
     * 格式化文件大小
     */
    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final long k = 1024;
        final String[] sizes = {"B", "KB", "MB", "GB", "TB"};
        int i = (int) (Math.log(bytes) / Math.log(k));
        if (i >= sizes.length) {
            i = sizes.length - 1;
        }
        return String.format("%.2f %s", bytes / Math.pow(k, i), sizes[i]);
    }
}
