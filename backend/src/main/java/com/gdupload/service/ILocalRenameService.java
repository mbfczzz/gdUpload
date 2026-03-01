package com.gdupload.service;

import java.util.Map;

public interface ILocalRenameService {
    /** 启动本地目录格式化重命名任务，返回 taskId */
    String startTask(String dirPath);

    /** 查询任务状态 */
    Map<String, Object> getStatus(String taskId);

    /** 取消任务 */
    void cancelTask(String taskId);
}
