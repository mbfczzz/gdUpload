package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.entity.FileInfo;
import com.gdupload.service.IFileInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 修复FileInfo控制器
 */
@Slf4j
@RestController
@RequestMapping("/fix")
@RequiredArgsConstructor
public class FixFileInfoController {

    private final IFileInfoService fileInfoService;

    /**
     * 修复Emby下载任务的FileInfo记录
     * 根据实际文件路径更新relativePath和fileName
     */
    @PostMapping("/emby-file-info")
    public Result<Map<String, Object>> fixEmbyFileInfo(@RequestParam Long taskId) {
        log.info("开始修复任务 {} 的FileInfo记录", taskId);

        // 获取该任务的所有文件
        List<FileInfo> fileList = fileInfoService.getTaskFiles(taskId);
        if (fileList == null || fileList.isEmpty()) {
            return Result.error("任务没有文件记录");
        }

        int updatedCount = 0;
        int skippedCount = 0;
        int errorCount = 0;

        for (FileInfo fileInfo : fileList) {
            try {
                String filePath = fileInfo.getFilePath();

                // 跳过已经有relativePath的记录
                if (fileInfo.getRelativePath() != null && !fileInfo.getRelativePath().isEmpty()) {
                    log.debug("跳过已有relativePath的文件: {}", filePath);
                    skippedCount++;
                    continue;
                }

                // 如果filePath是embyItemId（不是真实路径），跳过
                if (filePath == null || !filePath.startsWith("/data/emby/")) {
                    log.debug("跳过非文件路径: {}", filePath);
                    skippedCount++;
                    continue;
                }

                // 检查文件是否存在
                Path filePathObj = Paths.get(filePath);
                if (!Files.exists(filePathObj)) {
                    log.warn("文件不存在: {}", filePath);
                    errorCount++;
                    continue;
                }

                // 提取相对路径和文件名
                if (Files.isDirectory(filePathObj)) {
                    // 如果是目录（电视剧），提取目录名作为relativePath
                    String folderName = filePathObj.getFileName().toString();
                    fileInfo.setRelativePath(folderName);
                    log.info("更新目录FileInfo: path={}, relativePath={}", filePath, folderName);
                } else {
                    // 如果是文件，提取文件名和相对路径
                    String fileName = filePathObj.getFileName().toString();
                    fileInfo.setFileName(fileName);

                    Path parentDir = filePathObj.getParent();
                    if (parentDir != null && !parentDir.toString().equals("/data/emby")) {
                        String parentDirStr = parentDir.toString();
                        if (parentDirStr.startsWith("/data/emby/")) {
                            String relativePath = parentDirStr.substring("/data/emby/".length());
                            fileInfo.setRelativePath(relativePath);
                            log.info("更新文件FileInfo: path={}, fileName={}, relativePath={}",
                                filePath, fileName, relativePath);
                        }
                    } else {
                        fileInfo.setRelativePath("");
                        log.info("更新文件FileInfo: path={}, fileName={}, relativePath=空", filePath, fileName);
                    }
                }

                // 保存更新
                fileInfoService.updateById(fileInfo);
                updatedCount++;

            } catch (Exception e) {
                log.error("更新FileInfo失败: fileInfoId={}, error={}", fileInfo.getId(), e.getMessage());
                errorCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", fileList.size());
        result.put("updatedCount", updatedCount);
        result.put("skippedCount", skippedCount);
        result.put("errorCount", errorCount);

        log.info("修复完成: 总数={}, 更新={}, 跳过={}, 错误={}",
            fileList.size(), updatedCount, skippedCount, errorCount);

        return Result.success(result);
    }
}
