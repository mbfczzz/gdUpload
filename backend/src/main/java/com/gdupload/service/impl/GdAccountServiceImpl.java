package com.gdupload.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gdupload.common.BusinessException;
import com.gdupload.entity.GdAccount;
import com.gdupload.mapper.GdAccountMapper;
import com.gdupload.mapper.UploadRecordMapper;
import com.gdupload.service.IGdAccountService;
import com.gdupload.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Google Drive账号服务实现
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GdAccountServiceImpl extends ServiceImpl<GdAccountMapper, GdAccount> implements IGdAccountService {

    private final UploadRecordMapper uploadRecordMapper;

    // 用于轮询的账号索引缓存（taskId -> 上次使用的账号索引）
    private final ConcurrentHashMap<Long, Integer> taskAccountRotationIndex = new ConcurrentHashMap<>();

    @Override
    public Page<GdAccount> pageAccounts(Page<GdAccount> page, String keyword) {
        LambdaQueryWrapper<GdAccount> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(GdAccount::getAccountName, keyword)
                    .or().like(GdAccount::getAccountEmail, keyword));
        }
        wrapper.orderByDesc(GdAccount::getPriority)
                .orderByDesc(GdAccount::getCreateTime);

        Page<GdAccount> result = this.page(page, wrapper);

        // 实时计算每个账号的配额使用情况（今日从0点开始）
        result.getRecords().forEach(account -> {
            Long usedQuota = uploadRecordMapper.selectTodayUploadSize(account.getId());
            account.setUsedQuota(usedQuota);
            account.setRemainingQuota(account.getDailyLimit() - usedQuota);

            // 根据剩余配额自动更新状态
            if (account.getStatus() == 1 && account.getRemainingQuota() <= 0) {
                // 启用状态但配额用完 -> 已达上限
                account.setStatus(2);
                this.updateById(account);
                log.info("账号配额用完，自动更新为已达上限: accountId={}, accountName={}",
                    account.getId(), account.getAccountName());
            } else if (account.getStatus() == 2 && account.getRemainingQuota() > 0) {
                // 已达上限但配额恢复 -> 启用
                account.setStatus(1);
                this.updateById(account);
                log.info("账号配额恢复，自动更新为启用: accountId={}, accountName={}",
                    account.getId(), account.getAccountName());
            } else if (account.getStatus() == 0 && account.getRemainingQuota() > 0 && account.getQuotaResetTime() != null) {
                // 禁用状态但配额已恢复且已到解封时间 -> 启用
                LocalDateTime now = DateTimeUtil.now();
                if (now.isAfter(account.getQuotaResetTime()) || now.isEqual(account.getQuotaResetTime())) {
                    account.setStatus(1);
                    account.setQuotaResetTime(null);
                    this.updateById(account);
                    log.info("账号已到解封时间且配额恢复，自动更新为启用: accountId={}, accountName={}",
                        account.getId(), account.getAccountName());
                }
            }
        });

        return result;
    }

    @Override
    public List<GdAccount> getAvailableAccounts() {
        return baseMapper.selectAvailableAccounts();
    }

    @Override
    public GdAccount getBestAvailableAccount(Long requiredSize) {
        List<GdAccount> accounts = getAvailableAccounts();
        if (accounts.isEmpty()) {
            throw new BusinessException("没有可用的账号");
        }

        // 查找剩余配额足够的账号（按优先级排序）
        for (GdAccount account : accounts) {
            if (account.getRemainingQuota() >= requiredSize) {
                log.info("选择账号: accountName={}, remainingQuota={}, requiredSize={}",
                    account.getAccountName(), account.getRemainingQuota(), requiredSize);
                return account;
            }
        }

        // 如果没有任何账号有足够配额，返回 null
        // 让上层调用者决定如何处理（暂停任务或标记文件为失败）
        log.warn("没有账号有足够配额: requiredSize={}, 最大剩余配额={}",
            requiredSize, accounts.get(0).getRemainingQuota());
        return null;
    }

    @Override
    public GdAccount getNextAvailableAccountInRotation(Long taskId, Long requiredSize) {
        List<GdAccount> accounts = getAvailableAccounts();
        if (accounts.isEmpty()) {
            throw new BusinessException("没有可用的账号");
        }

        // 过滤出配额足够的账号
        List<GdAccount> eligibleAccounts = accounts.stream()
            .filter(account -> account.getRemainingQuota() >= requiredSize)
            .collect(java.util.stream.Collectors.toList());

        if (eligibleAccounts.isEmpty()) {
            log.warn("没有账号有足够配额: requiredSize={}", requiredSize);
            return null;
        }

        // 关键修复：使用账号列表大小作为模数，确保索引计算的一致性
        final int accountCount = eligibleAccounts.size();

        // 使用 compute 保证原子性操作
        // 获取并更新索引（原子操作）
        int nextIndex = taskAccountRotationIndex.compute(taskId, (key, currentIndex) -> {
            if (currentIndex == null) {
                return 0;  // 第一次使用，从0开始
            }
            // 使用绝对递增，然后对当前可用账号数取模
            // 这样即使账号列表变化，也能保证轮询的公平性
            return (currentIndex + 1) % accountCount;
        });

        GdAccount selectedAccount = eligibleAccounts.get(nextIndex);

        log.info("轮询选择账号: taskId={}, accountName={}, remainingQuota={}, requiredSize={}, index={}/{}, threadId={}",
            taskId, selectedAccount.getAccountName(), selectedAccount.getRemainingQuota(),
            requiredSize, nextIndex + 1, accountCount, Thread.currentThread().getName());

        return selectedAccount;
    }

    /**
     * 清理任务的轮询索引（任务完成或取消时调用）
     */
    @Override
    public void clearTaskRotationIndex(Long taskId) {
        taskAccountRotationIndex.remove(taskId);
        log.debug("清理任务轮询索引: taskId={}", taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateAccountQuota(Long accountId, Long size) {
        // 配额现在是实时计算的（今日从0点开始），不需要手动更新
        // 只需要在上传记录表中插入记录即可
        // 这个方法保留是为了兼容性，实际上传记录由上传服务负责插入

        // 检查账号当前配额
        GdAccount account = baseMapper.selectAccountWithRealTimeQuota(accountId);
        if (account == null) {
            throw new BusinessException("账号不存在");
        }

        // 如果配额不足，更新状态为已达上限
        if (account.getRemainingQuota() <= 0 && account.getStatus() == 1) {
            account.setStatus(2);
            this.updateById(account);
            log.warn("账号 {} 已达到每日上传限制", account.getAccountName());
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetAccountQuota(Long accountId) {
        // 配额每天凌晨0点重置，不需要手动重置
        // 这个方法改为检查并恢复账号状态
        GdAccount account = baseMapper.selectAccountWithRealTimeQuota(accountId);
        if (account == null) {
            throw new BusinessException("账号不存在");
        }

        // 如果配额已恢复，更新状态为启用
        if (account.getRemainingQuota() > 0 && account.getStatus() == 2) {
            account.setStatus(1);
            this.updateById(account);
            log.info("账号 {} 配额已恢复，状态更新为启用", account.getAccountName());
            return true;
        }

        return false;
    }

    @Override
    public boolean checkAccountQuota(Long accountId, Long requiredSize) {
        GdAccount account = baseMapper.selectAccountWithRealTimeQuota(accountId);
        if (account == null) {
            return false;
        }
        return account.getStatus() == 1 && account.getRemainingQuota() >= requiredSize;
    }

    @Override
    public Long getTodayUsedQuota(Long accountId) {
        // 返回今日（从0点开始）的使用量
        Long usedSize = uploadRecordMapper.selectTodayUploadSize(accountId);
        return usedSize != null ? usedSize : 0L;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleAccountStatus(Long accountId, Integer status) {
        GdAccount account = this.getById(accountId);
        if (account == null) {
            throw new BusinessException("账号不存在");
        }

        // 验证状态值是否合法（只允许手动设置0或1）
        if (status != 0 && status != 1) {
            throw new BusinessException("状态值不合法，只能设置为启用(1)或禁用(0)");
        }

        // 状态2（已达上限）只能由系统自动设置，不允许手动设置
        Integer currentStatus = account.getStatus();

        // 如果从禁用(0)切换到启用(1)，清空解封时间和禁用时间
        if (currentStatus == 0 && status == 1) {
            account.setQuotaResetTime(null);
            account.setDisabledTime(null);
            log.info("手动启用账号，清空解封时间和禁用时间: accountId={}, accountName={}",
                accountId, account.getAccountName());
        }

        // 如果从启用(1)切换到禁用(0)，记录禁用时间并设置解封时间为24小时后
        if (currentStatus == 1 && status == 0) {
            LocalDateTime now = DateTimeUtil.now();
            LocalDateTime resetTime = now.plusHours(24);
            account.setDisabledTime(now);
            account.setQuotaResetTime(resetTime);
            log.info("手动禁用账号，禁用时间: {}, 预计解封时间: {}, accountId={}, accountName={}",
                now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                resetTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                accountId, account.getAccountName());
        }

        account.setStatus(status);
        return this.updateById(account);
    }

    @Override
    public boolean validateRcloneConfig(String rcloneConfigName) {
        // TODO: 实际验证rclone配置是否存在
        // 可以通过执行 rclone listremotes 命令来验证
        return StrUtil.isNotBlank(rcloneConfigName);
    }
}
