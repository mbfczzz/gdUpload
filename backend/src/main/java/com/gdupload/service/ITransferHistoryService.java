package com.gdupload.service;

import com.gdupload.entity.TransferHistory;

import java.util.List;
import java.util.Map;

/**
 * 转存历史记录服务接口
 */
public interface ITransferHistoryService {

    /**
     * 保存转存记录
     *
     * @param history 转存记录
     * @return 是否成功
     */
    boolean saveHistory(TransferHistory history);

    /**
     * 根据Emby媒体项ID获取转存历史
     *
     * @param embyItemId Emby媒体项ID
     * @return 转存历史列表
     */
    List<TransferHistory> getHistoryByEmbyItemId(String embyItemId);

    /**
     * 检查媒体项是否已成功转存
     *
     * @param embyItemId Emby媒体项ID
     * @return 是否已转存
     */
    boolean hasSuccessfulTransfer(String embyItemId);

    /**
     * 获取最近的转存记录
     *
     * @param limit 数量限制
     * @return 转存记录列表
     */
    List<TransferHistory> getRecentHistory(int limit);

    /**
     * 批量检查媒体项的转存状态
     *
     * @param embyItemIds Emby媒体项ID列表
     * @return Map<embyItemId, status> status: "success"(成功), "failed"(失败), "none"(未转存)
     */
    Map<String, String> batchCheckTransferStatus(List<String> embyItemIds);

    /**
     * 获取转存统计信息
     *
     * @return 统计信息
     */
    Map<String, Object> getTransferStatistics();
}
