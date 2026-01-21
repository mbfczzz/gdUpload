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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

        // 实时计算每个账号的配额使用情况
        result.getRecords().forEach(account -> {
            Long usedQuota = uploadRecordMapper.selectLast24HoursUploadSize(account.getId());
            account.setUsedQuota(usedQuota);
            account.setRemainingQuota(account.getDailyLimit() - usedQuota);

            // 根据剩余配额自动更新状态
            if (account.getStatus() == 1 && account.getRemainingQuota() <= 0) {
                account.setStatus(2); // 已达上限
                this.updateById(account);
            } else if (account.getStatus() == 2 && account.getRemainingQuota() > 0) {
                account.setStatus(1); // 恢复启用
                this.updateById(account);
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
    @Transactional(rollbackFor = Exception.class)
    public boolean updateAccountQuota(Long accountId, Long size) {
        // 配额现在是实时计算的，不需要手动更新
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
        // 配额是滚动24小时窗口，不需要手动重置
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
        // 返回过去24小时的使用量
        Long usedSize = uploadRecordMapper.selectLast24HoursUploadSize(accountId);
        return usedSize != null ? usedSize : 0L;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleAccountStatus(Long accountId, Integer status) {
        GdAccount account = this.getById(accountId);
        if (account == null) {
            throw new BusinessException("账号不存在");
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
