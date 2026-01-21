package com.gdupload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdupload.entity.UploadRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 上传记录Mapper
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Mapper
public interface UploadRecordMapper extends BaseMapper<UploadRecord> {

    /**
     * 统计账号过去24小时上传量（已废弃，仅保留用于兼容）
     * 现在使用 selectTodayUploadSize 统计今日上传量
     */
    @Select("SELECT COALESCE(SUM(upload_size), 0) FROM upload_record WHERE account_id = #{accountId} AND upload_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) AND status = 1")
    Long selectLast24HoursUploadSize(@Param("accountId") Long accountId);

    /**
     * 统计账号今日上传量（从凌晨0点开始）
     * 用于配额计算和统计展示
     */
    @Select("SELECT COALESCE(SUM(upload_size), 0) FROM upload_record WHERE account_id = #{accountId} AND DATE(upload_time) = CURDATE() AND status = 1")
    Long selectTodayUploadSize(@Param("accountId") Long accountId);

    /**
     * 统计任务各账号上传量
     */
    @Select("SELECT account_id, SUM(upload_size) as total_size, COUNT(*) as file_count FROM upload_record WHERE task_id = #{taskId} AND status = 1 GROUP BY account_id")
    List<Map<String, Object>> selectTaskAccountStats(@Param("taskId") Long taskId);

    /**
     * 统计日期范围内的上传量
     */
    @Select("SELECT DATE(upload_time) as date, SUM(upload_size) as total_size, COUNT(*) as file_count FROM upload_record WHERE upload_time BETWEEN #{startDate} AND #{endDate} AND status = 1 GROUP BY DATE(upload_time)")
    List<Map<String, Object>> selectUploadStatsByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
