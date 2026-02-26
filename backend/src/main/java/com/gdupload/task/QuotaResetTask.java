package com.gdupload.task;

import com.gdupload.service.IGdAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 配额重置定时任务（已禁用）
 * 配额功能已移除，此类保留为空实现以保持向后兼容
 *
 * @author GD Upload Manager
 * @since 2026-01-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaResetTask {

    private final IGdAccountService gdAccountService;

    /**
     * 配额功能已移除，此方法保留为空实现
     */
    public void manualResetQuota() {
        log.info("配额功能已移除，此方法不再执行任何操作");
    }
}
