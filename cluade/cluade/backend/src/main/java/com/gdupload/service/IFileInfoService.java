package com.gdupload.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gdupload.entity.FileInfo;

import java.util.List;

/**
 * 文件信息服务接口
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
public interface IFileInfoService extends IService<FileInfo> {

    /**
     * 扫描指定目录下的文件
     */
    List<FileInfo> scanDirectory(String directoryPath, boolean recursive);

    /**
     * 批量保存文件信息
     */
    boolean batchSaveFiles(Long taskId, List<FileInfo> fileList);

    /**
     * 获取任务的文件列表
     */
    List<FileInfo> getTaskFiles(Long taskId);

    /**
     * 更新文件上传状态
     */
    boolean updateFileStatus(Long fileId, Integer status, String errorMessage);

    /**
     * 获取任务中待上传的文件
     */
    List<FileInfo> getPendingFiles(Long taskId);

    /**
     * 获取任务中失败的文件
     */
    List<FileInfo> getFailedFiles(Long taskId);

    /**
     * 标记文件为已上传
     */
    boolean markFileAsUploaded(Long fileId, Long accountId);

    /**
     * 检查文件是否已存在
     */
    boolean checkFileExists(String filePath, String md5);
}
