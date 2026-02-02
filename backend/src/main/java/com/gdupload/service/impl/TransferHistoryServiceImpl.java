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
    public Map<String, Boolean> batchCheckTransferStatus(List<String> embyItemIds) {
        if (embyItemIds == null || embyItemIds.isEmpty()) {
            return new HashMap<>();
        }

        LambdaQueryWrapper<TransferHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(TransferHistory::getEmbyItemId, embyItemIds)
                .eq(TransferHistory::getTransferStatus, "success")
                .select(TransferHistory::getEmbyItemId)
                .groupBy(TransferHistory::getEmbyItemId);

        List<TransferHistory> successList = historyMapper.selectList(wrapper);

        // 转换为Map
        Map<String, Boolean> resultMap = new HashMap<>();
        for (String itemId : embyItemIds) {
            resultMap.put(itemId, false);
        }
        for (TransferHistory history : successList) {
            resultMap.put(history.getEmbyItemId(), true);
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
