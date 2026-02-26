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
     * 获取可用账号列表（按创建时间排序）
     */
    @Select("SELECT * FROM gd_account WHERE status = 1 ORDER BY create_time DESC")
    List<GdAccount> selectAvailableAccounts();

    /**
     * 根据ID获取账号
     */
    @Select("SELECT * FROM gd_account WHERE id = #{accountId}")
    GdAccount selectAccountWithRealTimeQuota(@Param("accountId") Long accountId);
}
