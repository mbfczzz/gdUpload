package com.gdupload.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.common.PageResult;
import com.gdupload.common.Result;
import com.gdupload.entity.GdAccount;
import com.gdupload.service.IGdAccountService;
import com.gdupload.service.ISystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Google Drive账号管理控制器
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class GdAccountController {

    private final IGdAccountService accountService;
    private final ISystemLogService systemLogService;

    @GetMapping("/page")
    public Result<PageResult<GdAccount>> page(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String keyword) {
        Page<GdAccount> page = new Page<>(current, size);
        Page<GdAccount> result = accountService.pageAccounts(page, keyword);
        return Result.success(PageResult.of(result));
    }

    @GetMapping("/list")
    public Result<List<GdAccount>> list() {
        List<GdAccount> accounts = accountService.list();
        return Result.success(accounts);
    }

    @GetMapping("/{id}")
    public Result<GdAccount> getById(@PathVariable Long id) {
        GdAccount account = accountService.getById(id);
        return Result.success(account);
    }

    @GetMapping("/available")
    public Result<List<GdAccount>> getAvailableAccounts() {
        List<GdAccount> accounts = accountService.getAvailableAccounts();
        return Result.success(accounts);
    }

    @PostMapping
    public Result<Void> add(@Validated @RequestBody GdAccount account) {
        // 设置默认值
        if (account.getDailyLimit() == null) {
            account.setDailyLimit(805306368000L); // 750GB
        }
        if (account.getRemainingQuota() == null) {
            account.setRemainingQuota(account.getDailyLimit());
        }
        if (account.getUsedQuota() == null) {
            account.setUsedQuota(0L);
        }
        if (account.getStatus() == null) {
            account.setStatus(1);
        }
        if (account.getPriority() == null) {
            account.setPriority(0);
        }

        boolean success = accountService.save(account);

        if (success) {
            // Log account creation
            systemLogService.logAccount(account.getId(), 1, "INFO", "ACCOUNT_CREATE",
                String.format("创建账号 - 账号名: %s, 配置名: %s, 每日限额: %d GB",
                    account.getAccountName(), account.getRcloneConfigName(),
                    account.getDailyLimit() / 1024 / 1024 / 1024));
        }

        return success ? Result.success("添加成功") : Result.error("添加失败");
    }

    @PutMapping
    public Result<Void> update(@Validated @RequestBody GdAccount account) {
        boolean success = accountService.updateById(account);

        if (success) {
            // Log account update
            systemLogService.logAccount(account.getId(), 1, "INFO", "ACCOUNT_UPDATE",
                String.format("更新账号 - 账号名: %s, 配置名: %s",
                    account.getAccountName(), account.getRcloneConfigName()));
        }

        return success ? Result.success("更新成功") : Result.error("更新失败");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // Get account details before deletion for logging
        GdAccount account = accountService.getById(id);

        boolean success = accountService.removeById(id);

        if (success && account != null) {
            // Log account deletion
            systemLogService.logAccount(id, 1, "INFO", "ACCOUNT_DELETE",
                String.format("删除账号 - 账号名: %s, 配置名: %s",
                    account.getAccountName(), account.getRcloneConfigName()));
        }

        return success ? Result.success("删除成功") : Result.error("删除失败");
    }

    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        // Get account details before deletion for logging
        List<GdAccount> accounts = accountService.listByIds(ids);

        boolean success = accountService.removeByIds(ids);

        if (success && accounts != null && !accounts.isEmpty()) {
            // Log each account deletion
            for (GdAccount account : accounts) {
                systemLogService.logAccount(account.getId(), 1, "INFO", "ACCOUNT_DELETE",
                    String.format("批量删除账号 - 账号名: %s, 配置名: %s",
                        account.getAccountName(), account.getRcloneConfigName()));
            }
        }

        return success ? Result.success("删除成功") : Result.error("删除失败");
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        GdAccount account = accountService.getById(id);
        boolean success = accountService.toggleAccountStatus(id, status);

        if (success && account != null) {
            // Log account status change
            String statusText = status == 1 ? "启用" : "禁用";
            systemLogService.logAccount(id, 1, "INFO", "ACCOUNT_STATUS_CHANGE",
                String.format("%s账号 - 账号名: %s, 新状态: %s",
                    statusText, account.getAccountName(), statusText));
        }

        return success ? Result.success("操作成功") : Result.error("操作失败");
    }

    @PutMapping("/{id}/reset-quota")
    public Result<Void> resetQuota(@PathVariable Long id) {
        GdAccount account = accountService.getById(id);
        boolean success = accountService.resetAccountQuota(id);

        if (success && account != null) {
            // Log quota reset
            systemLogService.logAccount(id, 1, "INFO", "ACCOUNT_QUOTA_RESET",
                String.format("重置账号配额 - 账号名: %s, 每日限额: %d GB",
                    account.getAccountName(), account.getDailyLimit() / 1024 / 1024 / 1024));
        }

        return success ? Result.success("重置成功") : Result.error("重置失败");
    }

    @GetMapping("/{id}/today-quota")
    public Result<Long> getTodayQuota(@PathVariable Long id) {
        Long usedQuota = accountService.getTodayUsedQuota(id);
        return Result.success(usedQuota);
    }

    @GetMapping("/validate-rclone")
    public Result<Boolean> validateRclone(@RequestParam String configName) {
        boolean valid = accountService.validateRcloneConfig(configName);
        return Result.success(valid);
    }
}
