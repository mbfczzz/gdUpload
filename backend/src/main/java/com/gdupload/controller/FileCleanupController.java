package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.service.IFileCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 文件清理控制器
 *
 * @author GD Upload Manager
 * @since 2026-01-20
 */
@Slf4j
@RestController
@RequestMapping("/file-cleanup")
@RequiredArgsConstructor
public class FileCleanupController {

    private final IFileCleanupService fileCleanupService;

    /**
     * 清理已上传文件的物理文件
     */
    @PostMapping("/cleanup")
    public Result<Map<String, Object>> cleanupUploadedFiles() {
        try {
            Map<String, Object> result = fileCleanupService.cleanupUploadedFiles();
            return Result.success(result);
        } catch (Exception e) {
            log.error("清理文件失败", e);
            return Result.error("清理文件失败: " + e.getMessage());
        }
    }
}
