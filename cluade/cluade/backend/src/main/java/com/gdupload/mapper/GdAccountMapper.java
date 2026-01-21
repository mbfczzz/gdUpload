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
     * 使用子查询实时计算过去24小时的使用量
     */
    @Select("SELECT a.*, " +
            "COALESCE((SELECT SUM(r.upload_size) FROM upload_record r " +
            "WHERE r.account_id = a.id AND r.upload_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) AND r.status = 1), 0) as used_quota, " +
            "a.daily_limit - COALESCE((SELECT SUM(r.upload_size) FROM upload_record r " +
            "WHERE r.account_id = a.id AND r.upload_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) AND r.status = 1), 0) as remaining_quota " +
            "FROM gd_account a " +
            "WHERE a.status = 1 " +
            "HAVING remaining_quota > 0 " +
            "ORDER BY a.priority DESC, remaining_quota DESC")
    List<GdAccount> selectAvailableAccounts();

    /**
     * 获取所有账号（实时计算过去24小时使用量）
     */
    @Select("SELECT a.*, " +
            "COALESCE((SELECT SUM(r.upload_size) FROM upload_record r " +
            "WHERE r.account_id = a.id AND r.upload_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) AND r.status = 1), 0) as used_quota, " +
            "a.daily_limit - COALESCE((SELECT SUM(r.upload_size) FROM upload_record r " +
            "WHERE r.account_id = a.id AND r.upload_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) AND r.status = 1), 0) as remaining_quota " +
            "FROM gd_account a " +
            "WHERE a.id = #{accountId}")
    GdAccount selectAccountWithRealTimeQuota(@Param("accountId") Long accountId);
}
