package com.gdupload.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gdupload.entity.GdAccount;
import com.gdupload.service.IGdAccountService;
import com.gdupload.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 配额重置定时任务
 * 每天凌晨0点自动启用已到解封时间的账号
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
     * 每半小时检查并启用已到解封时间的账号
     * cron表达式: 秒 分 时 日 月 周
     * 每半小时执行（0分和30分）
     */
    @Scheduled(cron = "0 */30 * * * ?")
    public void checkAndEnableAccounts() {
        log.info("开始检查账号解封状态...");

        try {
            LocalDateTime now = DateTimeUtil.now();

            // 查询所有被禁用且已到解封时间的账号
            LambdaQueryWrapper<GdAccount> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(GdAccount::getStatus, 0)  // 禁用状态
                .isNotNull(GdAccount::getQuotaResetTime)  // 有重置时间
                .le(GdAccount::getQuotaResetTime, now);  // 重置时间已到

            List<GdAccount> accountsToEnable = gdAccountService.list(queryWrapper);

            if (accountsToEnable.isEmpty()) {
                log.info("没有需要解封的账号");
                return;
            }

            int enabledCount = 0;
            for (GdAccount account : accountsToEnable) {
                // 保存原始重置时间用于日志
                LocalDateTime originalResetTime = account.getQuotaResetTime();

                // 重置配额
                account.setUsedQuota(0L);
                account.setRemainingQuota(account.getDailyLimit());
                account.setQuotaResetTime(null);  // 清空重置时间
                account.setDisabledTime(null);  // 清空禁用时间

                gdAccountService.updateById(account);
                enabledCount++;

                // 计算被禁用时长
                long disabledHours = originalResetTime != null
                    ? java.time.Duration.between(account.getCreateTime() != null ? account.getCreateTime() : originalResetTime.minusDays(1), now).toHours()
                    : 0;

                log.info("自动解封账号: accountId={}, accountName={}, 解封时间={}",
                    account.getId(), account.getAccountName(),
                    originalResetTime != null ? originalResetTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "未知");
            }

            log.info("账号解封检查完成: 解封了 {} 个账号", enabledCount);

        } catch (Exception e) {
            log.error("账号解封检查任务执行失败", e);
        }
    }

    /**
     * 手动触发配额重置（用于测试或紧急情况）
     * 立即重置所有账号配额并启用
     */
    public void manualResetQuota() {
        log.info("手动触发配额重置...");

        try {
            List<GdAccount> accounts = gdAccountService.list();

            int resetCount = 0;
            for (GdAccount account : accounts) {
                account.setUsedQuota(0L);
                account.setRemainingQuota(account.getDailyLimit());
                account.setQuotaResetTime(null);  // 清空重置时间

                gdAccountService.updateById(account);
                resetCount++;
            }

            log.info("手动配额重置完成: 重置了 {} 个账号", resetCount);

        } catch (Exception e) {
            log.error("手动配额重置失败", e);
            throw new RuntimeException("配额重置失败: " + e.getMessage());
        }
    }
}
