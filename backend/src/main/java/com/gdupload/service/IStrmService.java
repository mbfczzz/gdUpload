package com.gdupload.service;

import java.util.Map;

/**
 * STRM 文件生成服务
 * 扫描 GD 目录 → 解析文件名 → TMDB 刮削 → 生成 .strm + .nfo + 封面
 */
public interface IStrmService {

    /**
     * 异步启动 STRM 生成任务
     * 配置（输出路径、播放URL）从 SmartSearchConfig 读取
     *
     * @param gdRemote     rclone 远程名称（如 media2）
     * @param gdSourcePath GD 源目录路径（相对于远程根，如 video/dramas/ShowName）
     */
    void startGenerate(String gdRemote, String gdSourcePath);

    /** 获取当前任务状态 */
    Map<String, Object> getStatus();
}
