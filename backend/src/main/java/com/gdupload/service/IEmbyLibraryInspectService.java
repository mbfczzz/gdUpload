package com.gdupload.service;

import com.gdupload.dto.EmbyLibraryFileNode;

import java.util.List;
import java.util.Map;

/**
 * Emby库检查服务接口
 */
public interface IEmbyLibraryInspectService {

    /**
     * 扫描指定 GD 路径，构建带验证结果的文件树
     *
     * @param rcloneRemote rclone远程配置名称
     * @param path         目录路径（如 /video2）
     * @return 文件树节点列表（第一层为分类目录）
     */
    List<EmbyLibraryFileNode> inspectLibrary(String rcloneRemote, String path);

    /**
     * 返回汇总统计
     *
     * @param rcloneRemote rclone远程配置名称
     * @param path         目录路径
     * @return 统计信息：totalFiles, totalDirs, errorCount, warningCount, infoCount, categoryStats
     */
    Map<String, Object> getInspectSummary(String rcloneRemote, String path);
}
