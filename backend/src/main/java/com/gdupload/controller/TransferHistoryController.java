package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.entity.TransferHistory;
import com.gdupload.service.ITransferHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 转存历史记录控制器
 */
@Slf4j
@RestController
@RequestMapping("/transfer-history")
public class TransferHistoryController {

    @Autowired
    private ITransferHistoryService historyService;

    /**
     * 保存转存记录
     */
    @PostMapping("/save")
    public Result<String> saveHistory(@RequestBody TransferHistory history) {
        try {
            boolean success = historyService.saveHistory(history);
            if (success) {
                return Result.success("保存成功");
            } else {
                return Result.error("保存失败");
            }
        } catch (Exception e) {
            log.error("保存转存记录失败", e);
            return Result.error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 根据Emby媒体项ID获取转存历史
     */
    @GetMapping("/item/{embyItemId}")
    public Result<List<TransferHistory>> getHistoryByEmbyItemId(@PathVariable String embyItemId) {
        try {
            List<TransferHistory> history = historyService.getHistoryByEmbyItemId(embyItemId);
            return Result.success(history);
        } catch (Exception e) {
            log.error("获取转存历史失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 检查媒体项是否已成功转存
     */
    @GetMapping("/check/{embyItemId}")
    public Result<Boolean> hasSuccessfulTransfer(@PathVariable String embyItemId) {
        try {
            boolean hasTransfer = historyService.hasSuccessfulTransfer(embyItemId);
            return Result.success(hasTransfer);
        } catch (Exception e) {
            log.error("检查转存状态失败", e);
            return Result.error("检查失败: " + e.getMessage());
        }
    }

    /**
     * 批量检查媒体项的转存状态
     */
    @PostMapping("/batch-check")
    public Result<Map<String, String>> batchCheckTransferStatus(@RequestBody List<String> embyItemIds) {
        try {
            Map<String, String> statusMap = historyService.batchCheckTransferStatus(embyItemIds);
            return Result.success(statusMap);
        } catch (Exception e) {
            log.error("批量检查转存状态失败", e);
            return Result.error("检查失败: " + e.getMessage());
        }
    }

    /**
     * 获取最近的转存记录
     */
    @GetMapping("/recent")
    public Result<List<TransferHistory>> getRecentHistory(@RequestParam(defaultValue = "50") int limit) {
        try {
            List<TransferHistory> history = historyService.getRecentHistory(limit);
            return Result.success(history);
        } catch (Exception e) {
            log.error("获取最近转存记录失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取转存统计信息
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getTransferStatistics() {
        try {
            Map<String, Object> stats = historyService.getTransferStatistics();
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取转存统计失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }
}
