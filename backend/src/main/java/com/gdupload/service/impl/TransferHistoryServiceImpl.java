package com.gdupload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdupload.entity.TransferHistory;
import com.gdupload.mapper.TransferHistoryMapper;
import com.gdupload.service.ITransferHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 转存历史记录服务实现
 */
@Slf4j
@Service
public class TransferHistoryServiceImpl implements ITransferHistoryService {

    @Autowired
    private TransferHistoryMapper historyMapper;

    @Override
    public boolean saveHistory(TransferHistory history) {
        try {
            return historyMapper.insert(history) > 0;
        } catch (Exception e) {
            log.error("保存转存记录失败", e);
            return false;
        }
    }

    @Override
    public List<TransferHistory> getHistoryByEmbyItemId(String embyItemId) {
        LambdaQueryWrapper<TransferHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TransferHistory::getEmbyItemId, embyItemId)
                .orderByDesc(TransferHistory::getCreateTime);
        return historyMapper.selectList(wrapper);
    }

    @Override
    public boolean hasSuccessfulTransfer(String embyItemId) {
        LambdaQueryWrapper<TransferHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TransferHistory::getEmbyItemId, embyItemId)
                .eq(TransferHistory::getTransferStatus, "success");
        return historyMapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<TransferHistory> getRecentHistory(int limit) {
        LambdaQueryWrapper<TransferHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(TransferHistory::getCreateTime)
                .last("LIMIT " + limit);
        return historyMapper.selectList(wrapper);
    }

    @Override
    public Map<String, String> batchCheckTransferStatus(List<String> embyItemIds) {
        if (embyItemIds == null || embyItemIds.isEmpty()) {
            return new HashMap<>();
        }

        // 查询所有相关的转存记录
        LambdaQueryWrapper<TransferHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(TransferHistory::getEmbyItemId, embyItemIds)
                .orderByDesc(TransferHistory::getCreateTime);

        List<TransferHistory> allHistories = historyMapper.selectList(wrapper);

        // 按embyItemId分组，取最新的记录
        Map<String, TransferHistory> latestHistoryMap = new HashMap<>();
        for (TransferHistory history : allHistories) {
            String itemId = history.getEmbyItemId();
            if (!latestHistoryMap.containsKey(itemId)) {
                latestHistoryMap.put(itemId, history);
            }
        }

        // 构建结果Map
        Map<String, String> resultMap = new HashMap<>();
        for (String itemId : embyItemIds) {
            TransferHistory latestHistory = latestHistoryMap.get(itemId);
            if (latestHistory == null) {
                // 没有转存记录
                resultMap.put(itemId, "none");
            } else if ("success".equals(latestHistory.getTransferStatus())) {
                // 最新记录是成功
                resultMap.put(itemId, "success");
            } else {
                // 最新记录是失败
                resultMap.put(itemId, "failed");
            }
        }

        return resultMap;
    }

    @Override
    public Map<String, Object> getTransferStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // 总转存次数
        Long totalCount = historyMapper.selectCount(null);
        stats.put("totalCount", totalCount);

        // 成功次数
        LambdaQueryWrapper<TransferHistory> successWrapper = new LambdaQueryWrapper<>();
        successWrapper.eq(TransferHistory::getTransferStatus, "success");
        Long successCount = historyMapper.selectCount(successWrapper);
        stats.put("successCount", successCount);

        // 失败次数
        LambdaQueryWrapper<TransferHistory> failedWrapper = new LambdaQueryWrapper<>();
        failedWrapper.eq(TransferHistory::getTransferStatus, "failed");
        Long failedCount = historyMapper.selectCount(failedWrapper);
        stats.put("failedCount", failedCount);

        // 成功率
        if (totalCount > 0) {
            double successRate = (double) successCount / totalCount * 100;
            stats.put("successRate", String.format("%.2f%%", successRate));
        } else {
            stats.put("successRate", "0.00%");
        }

        return stats;
    }
}
