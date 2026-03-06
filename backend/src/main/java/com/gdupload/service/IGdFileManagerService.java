package com.gdupload.service;

import com.gdupload.dto.GdFileItem;
import com.gdupload.dto.PagedResult;

import java.util.List;
import java.util.Map;

/**
 * GD文件管理服务接口
 */
public interface IGdFileManagerService {

    /**
     * 列出目录内容（服务端分页，目录优先按名称排序）
     *
     * @param rcloneConfigName rclone配置名称
     * @param path 目录路径（空字符串表示根目录）
     * @param page 页码（从1开始）
     * @param size 每页条数
     * @return 分页结果
     */
    PagedResult<GdFileItem> listFiles(String rcloneConfigName, String path, int page, int size);

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

    /**
     * 删除单个空目录（递归检查：子目录下也没有文件才算空）
     *
     * @return true=已删除, false=非空未删除
     */
    boolean deleteEmptyDirectory(String rcloneConfigName, String dirPath);

    /**
     * 批量清理指定路径下的所有空文件夹
     *
     * @return 删除结果：deleted(已删名称列表), skipped(非空跳过数), total(扫描总目录数)
     */
    Map<String, Object> cleanEmptyDirectories(String rcloneConfigName, String basePath);

    /**
     * 对指定路径去重：合并同名文件夹、清理重复文件（rclone dedupe）
     */
    Map<String, Object> deduplicatePath(String rcloneConfigName, String path);
}
