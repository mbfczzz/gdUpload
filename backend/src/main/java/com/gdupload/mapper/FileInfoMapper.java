package com.gdupload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdupload.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文件信息Mapper
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {

    /**
     * 获取待上传文件列表
     */
    @Select("SELECT * FROM file_info WHERE task_id = #{taskId} AND status = 0 ORDER BY file_size ASC LIMIT #{limit}")
    List<FileInfo> selectPendingFiles(@Param("taskId") Long taskId, @Param("limit") Integer limit);

    /**
     * 统计任务文件状态
     */
    @Select("SELECT status, COUNT(*) as count, SUM(file_size) as total_size FROM file_info WHERE task_id = #{taskId} GROUP BY status")
    List<java.util.Map<String, Object>> selectTaskFileStats(@Param("taskId") Long taskId);
}
