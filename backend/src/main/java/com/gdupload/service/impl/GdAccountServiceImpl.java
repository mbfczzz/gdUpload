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
        wrapper.orderByDesc(GdAccount::getCreateTime);

        return this.page(page, wrapper);
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

        // 返回第一个可用账号
        GdAccount account = accounts.get(0);
        log.info("选择账号: accountName={}", account.getAccountName());
        return account;
    }

    @Override
    public GdAccount getNextAvailableAccountInRotation(Long taskId, Long requiredSize) {
        List<GdAccount> accounts = getAvailableAccounts();
        if (accounts.isEmpty()) {
            throw new BusinessException("没有可用的账号");
        }

        // 使用所有可用账号进行轮询
        final int accountCount = accounts.size();

        // 使用 compute 保证原子性操作
        // 获取并更新索引（原子操作）
        int nextIndex = taskAccountRotationIndex.compute(taskId, (key, currentIndex) -> {
            if (currentIndex == null) {
                return 0;  // 第一次使用，从0开始
            }
            // 使用绝对递增，然后对当前可用账号数取模
            return (currentIndex + 1) % accountCount;
        });

        GdAccount selectedAccount = accounts.get(nextIndex);

        log.info("轮询选择账号: taskId={}, accountName={}, index={}/{}, threadId={}",
            taskId, selectedAccount.getAccountName(),
            nextIndex + 1, accountCount, Thread.currentThread().getName());

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
        // 不再使用配额检查，此方法保留为空实现以保持接口兼容性
        GdAccount account = this.getById(accountId);
        if (account == null) {
            throw new BusinessException("账号不存在");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetAccountQuota(Long accountId) {
        // 不再使用配额功能，此方法保留为空实现以保持接口兼容性
        GdAccount account = this.getById(accountId);
        if (account == null) {
            throw new BusinessException("账号不存在");
        }
        log.info("配额重置功能已移除，账号: {}", account.getAccountName());
        return true;
    }

    @Override
    public boolean checkAccountQuota(Long accountId, Long requiredSize) {
        // 不再检查配额，只检查账号是否启用
        GdAccount account = this.getById(accountId);
        if (account == null) {
            return false;
        }
        return account.getStatus() == 1;
    }

    @Override
    public Long getTodayUsedQuota(Long accountId) {
        // 配额功能已移除，返回 0
        return 0L;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleAccountStatus(Long accountId, Integer status) {
        GdAccount account = this.getById(accountId);
        if (account == null) {
            throw new BusinessException("账号不存在");
        }

        // 验证状态值是否合法（只允许设置0或1）
        if (status != 0 && status != 1) {
            throw new BusinessException("状态值不合法，只能设置为启用(1)或禁用(0)");
        }

        Integer currentStatus = account.getStatus();

        // 如果从启用(1)切换到禁用(0)，记录禁用时间
        if (currentStatus == 1 && status == 0) {
            LocalDateTime now = DateTimeUtil.now();
            account.setDisabledTime(now);
            log.info("禁用账号，记录禁用时间: {}, accountId={}, accountName={}",
                now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                accountId, account.getAccountName());
        }

        // 如果从禁用(0)切换到启用(1)，清空禁用时间
        if (currentStatus == 0 && status == 1) {
            account.setDisabledTime(null);
            log.info("启用账号，清空禁用时间: accountId={}, accountName={}",
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
