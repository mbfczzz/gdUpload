package com.gdupload.task;

import com.gdupload.service.IStrmWatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * STRM 监控定时任务
 *
 * 每 60 秒检查一次哪些监控配置已到期，并触发增量同步。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrmWatchTask {

    private final IStrmWatchService strmWatchService;

    @Scheduled(fixedDelay = 60_000)
    public void checkScheduled() {
        try {
            strmWatchService.checkAndTriggerScheduled();
        } catch (Exception e) {
            log.error("[StrmWatchTask] 定时检查异常", e);
        }
    }
}
