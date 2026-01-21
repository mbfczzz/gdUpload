package com.gdupload.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gdupload.entity.GdAccount;

import java.util.List;

/**
 * Google Drive账号服务接口
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
public interface IGdAccountService extends IService<GdAccount> {

    /**
     * 分页查询账号列表
     */
    Page<GdAccount> pageAccounts(Page<GdAccount> page, String keyword);

    /**
     * 获取可用账号列表
     */
    List<GdAccount> getAvailableAccounts();

    /**
     * 获取最佳可用账号
     */
    GdAccount getBestAvailableAccount(Long requiredSize);

    /**
     * 轮询获取下一个可用账号（用于多账号轮询上传）
     */
    GdAccount getNextAvailableAccountInRotation(Long taskId, Long requiredSize);

    /**
     * 清理任务的轮询索引（任务完成或取消时调用）
     */
    void clearTaskRotationIndex(Long taskId);

    /**
     * 更新账号使用配额
     */
    boolean updateAccountQuota(Long accountId, Long size);

    /**
     * 重置账号配额
     */
    boolean resetAccountQuota(Long accountId);

    /**
     * 检查账号配额是否充足
     */
    boolean checkAccountQuota(Long accountId, Long requiredSize);

    /**
     * 获取账号今日已使用配额
     */
    Long getTodayUsedQuota(Long accountId);

    /**
     * 启用/禁用账号
     */
    boolean toggleAccountStatus(Long accountId, Integer status);

    /**
     * 验证rclone配置
     */
    boolean validateRcloneConfig(String rcloneConfigName);
}
