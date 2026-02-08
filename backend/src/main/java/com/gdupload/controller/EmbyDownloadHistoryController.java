package com.gdupload.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdupload.common.Result;
import com.gdupload.entity.EmbyDownloadHistory;
import com.gdupload.mapper.EmbyDownloadHistoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Emby下载历史控制器
 */
@Slf4j
@RestController
@RequestMapping("/emby-download-history")
public class EmbyDownloadHistoryController {

    @Autowired
    private EmbyDownloadHistoryMapper downloadHistoryMapper;

    /**
     * 批量检查媒体项的下载状态
     * 返回每个媒体项的最新下载状态
     *
     * @param embyItemIds 媒体项ID列表
     * @return Map<embyItemId, downloadStatus>
     */
    @PostMapping("/batch-check")
    public Result<Map<String, String>> batchCheckDownloadStatus(@RequestBody List<String> embyItemIds) {
        log.info("批量检查下载状态，共 {} 个媒体项", embyItemIds.size());

        Map<String, String> statusMap = new HashMap<>();

        for (String itemId : embyItemIds) {
            // 查询该媒体项的最新下载记录
            LambdaQueryWrapper<EmbyDownloadHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(EmbyDownloadHistory::getEmbyItemId, itemId)
                    .orderByDesc(EmbyDownloadHistory::getCreateTime)
                    .last("LIMIT 1");

            EmbyDownloadHistory history = downloadHistoryMapper.selectOne(wrapper);

            if (history != null) {
                statusMap.put(itemId, history.getDownloadStatus());
            } else {
                statusMap.put(itemId, "none");
            }
        }

        log.info("下载状态检查完成，成功: {}, 失败: {}, 未下载: {}",
                statusMap.values().stream().filter(s -> "success".equals(s)).count(),
                statusMap.values().stream().filter(s -> "failed".equals(s)).count(),
                statusMap.values().stream().filter(s -> "none".equals(s)).count());

        return Result.success(statusMap);
    }

    /**
     * 根据Emby媒体项ID获取下载历史
     *
     * @param embyItemId 媒体项ID
     * @return 下载历史列表
     */
    @GetMapping("/item/{embyItemId}")
    public Result<List<EmbyDownloadHistory>> getHistoryByEmbyItemId(@PathVariable String embyItemId) {
        log.info("获取媒体项下载历史: {}", embyItemId);

        LambdaQueryWrapper<EmbyDownloadHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmbyDownloadHistory::getEmbyItemId, embyItemId)
                .orderByDesc(EmbyDownloadHistory::getCreateTime);

        List<EmbyDownloadHistory> histories = downloadHistoryMapper.selectList(wrapper);
        return Result.success(histories);
    }

    /**
     * 手动标记下载状态
     *
     * @param embyItemId 媒体项ID
     * @param status 下载状态（success/failed）
     * @return 操作结果
     */
    @PostMapping("/mark-status")
    public Result<String> markDownloadStatus(@RequestParam String embyItemId,
                                              @RequestParam String status) {
        log.info("手动标记下载状态: embyItemId={}, status={}", embyItemId, status);

        // 验证状态值
        if (!"success".equals(status) && !"failed".equals(status)) {
            return Result.error("无效的状态值，只能是 success 或 failed");
        }

        // 创建下载历史记录
        EmbyDownloadHistory history = new EmbyDownloadHistory();
        history.setEmbyItemId(embyItemId);
        history.setEmbyConfigId(1L); // 默认配置ID
        history.setDownloadStatus(status);
        history.setFilePath("手动标记");
        history.setFileSize(0L);
        history.setErrorMessage(null);

        int result = downloadHistoryMapper.insert(history);

        if (result > 0) {
            log.info("手动标记成功: embyItemId={}, status={}", embyItemId, status);
            return Result.success("标记成功");
        } else {
            log.error("手动标记失败: embyItemId={}, status={}", embyItemId, status);
            return Result.error("标记失败");
        }
    }
}
