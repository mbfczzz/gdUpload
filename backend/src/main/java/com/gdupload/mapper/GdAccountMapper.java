package com.gdupload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdupload.entity.GdAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Google Drive账号Mapper
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Mapper
public interface GdAccountMapper extends BaseMapper<GdAccount> {

    /**
     * 获取可用账号列表（按优先级排序）
     * 使用子查询实时计算今日（从0点开始）的使用量
     * 优化：使用派生表避免子查询重复执行
     */
    @Select("SELECT a.*, " +
            "COALESCE(r.used_quota, 0) as used_quota, " +
            "a.daily_limit - COALESCE(r.used_quota, 0) as remaining_quota " +
            "FROM gd_account a " +
            "LEFT JOIN (" +
            "  SELECT account_id, SUM(upload_size) as used_quota " +
            "  FROM upload_record " +
            "  WHERE DATE(upload_time) = CURDATE() AND status = 1 " +
            "  GROUP BY account_id" +
            ") r ON a.id = r.account_id " +
            "WHERE a.status = 1 AND (a.daily_limit - COALESCE(r.used_quota, 0)) > 0 " +
            "ORDER BY a.priority DESC, (a.daily_limit - COALESCE(r.used_quota, 0)) DESC")
    List<GdAccount> selectAvailableAccounts();

    /**
     * 获取所有账号（实时计算今日从0点开始的使用量）
     */
    @Select("SELECT a.*, " +
            "COALESCE((SELECT SUM(r.upload_size) FROM upload_record r " +
            "WHERE r.account_id = a.id AND DATE(r.upload_time) = CURDATE() AND r.status = 1), 0) as used_quota, " +
            "a.daily_limit - COALESCE((SELECT SUM(r.upload_size) FROM upload_record r " +
            "WHERE r.account_id = a.id AND DATE(r.upload_time) = CURDATE() AND r.status = 1), 0) as remaining_quota " +
            "FROM gd_account a " +
            "WHERE a.id = #{accountId}")
    GdAccount selectAccountWithRealTimeQuota(@Param("accountId") Long accountId);
}
