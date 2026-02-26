package com.gdupload.service;

import com.gdupload.dto.GdFileItem;

import java.util.List;

/**
 * GD文件管理服务接口
 */
public interface IGdFileManagerService {

    /**
     * 列出目录内容
     *
     * @param rcloneConfigName rclone配置名称
     * @param path 目录路径（空字符串表示根目录）
     * @return 文件/目录列表
     */
    List<GdFileItem> listFiles(String rcloneConfigName, String path);

    /**
     * 删除文件
     *
     * @param rcloneConfigName rclone配置名称
     * @param filePath 文件路径
     */
    void deleteFile(String rcloneConfigName, String filePath);

    /**
     * 删除目录（递归）
     *
     * @param rcloneConfigName rclone配置名称
     * @param dirPath 目录路径
     */
    void deleteDirectory(String rcloneConfigName, String dirPath);

    /**
     * 移动/重命名文件或目录
     *
     * @param rcloneConfigName rclone配置名称
     * @param oldPath 原路径
     * @param newPath 新路径
     * @param isDir 是否为目录
     */
    void moveItem(String rcloneConfigName, String oldPath, String newPath, boolean isDir);

    /**
     * 创建目录
     *
     * @param rcloneConfigName rclone配置名称
     * @param path 目录路径
     */
    void makeDirectory(String rcloneConfigName, String path);
}
