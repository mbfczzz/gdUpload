package com.gdupload.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gdupload.entity.StrmFileRecord;
import com.gdupload.entity.StrmWatchConfig;

import java.util.List;
import java.util.Map;

/**
 * STRM 监控服务接口
 */
public interface IStrmWatchService {

    /** 查询所有监控配置（分页） */
    IPage<StrmWatchConfig> listConfigs(int page, int size);

    /** 新增监控配置 */
    void addConfig(StrmWatchConfig config);

    /** 修改监控配置 */
    void updateConfig(StrmWatchConfig config);

    /** 删除监控配置（同时删除文件记录） */
    void deleteConfig(Long id);

    /** 启用 */
    void enableConfig(Long id);

    /** 禁用 */
    void disableConfig(Long id);

    /** 手动触发增量同步 */
    void triggerSync(Long id);

    /** 手动触发强制全量重刮（覆盖） */
    void triggerForceRescrape(Long id);

    /** 查询文件记录（分页，可按 status 过滤） */
    IPage<StrmFileRecord> listRecords(Long configId, String status, int page, int size);

    /** 获取某配置当前同步状态 */
    Map<String, Object> getSyncStatus(Long configId);

    /** 检查并触发到期的监控配置（由定时任务调用） */
    void checkAndTriggerScheduled();
}
