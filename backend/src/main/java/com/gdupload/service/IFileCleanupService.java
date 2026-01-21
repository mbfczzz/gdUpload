package com.gdupload.service;

import java.util.Map;

/**
 * 文件清理服务接口
 *
 * @author GD Upload Manager
 * @since 2026-01-20
 */
public interface IFileCleanupService {

    /**
     * 清理已上传文件的物理文件
     *
     * @return 清理结果统计
     */
    Map<String, Object> cleanupUploadedFiles();
}
