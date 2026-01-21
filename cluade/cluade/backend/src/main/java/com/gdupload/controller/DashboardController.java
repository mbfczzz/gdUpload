package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.service.IGdAccountService;
import com.gdupload.service.IUploadTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据统计控制器
 *
 * @author GD Upload Manager
 * @since 2026-01-19
 */
@Slf4j
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final IGdAccountService gdAccountService;
    private final IUploadTaskService uploadTaskService;

    /**
     * 获取统计数据
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // 账号统计
        long totalAccounts = gdAccountService.count();
        long availableAccounts = gdAccountService.getAvailableAccounts().size();

        stats.put("totalAccounts", totalAccounts);
        stats.put("availableAccounts", availableAccounts);

        // 任务统计
        long runningTasks = uploadTaskService.getRunningTasks().size();
        long totalTasks = uploadTaskService.count();

        stats.put("runningTasks", runningTasks);
        stats.put("totalTasks", totalTasks);

        // 今日上传量统计
        long todayUploadSize = 0;
        try {
            todayUploadSize = gdAccountService.list().stream()
                    .mapToLong(account -> gdAccountService.getTodayUsedQuota(account.getId()))
                    .sum();
        } catch (Exception e) {
            log.error("计算今日上传量失败", e);
        }

        stats.put("todayUploadSize", todayUploadSize);

        return Result.success(stats);
    }
}
