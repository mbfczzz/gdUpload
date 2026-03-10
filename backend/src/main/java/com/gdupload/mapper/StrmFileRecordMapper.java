package com.gdupload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdupload.entity.StrmFileRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * STRM 文件记录 Mapper
 */
@Mapper
public interface StrmFileRecordMapper extends BaseMapper<StrmFileRecord> {

    /**
     * 批量插入文件记录（MySQL 的 INSERT ... VALUES (...),(...)... 语法）
     * 相比逐条 insert，速度可提升 10-50x。
     */
    @Insert({
        "<script>",
        "INSERT INTO strm_file_record",
        "  (watch_config_id, gd_remote, rel_file_path, path_hash, file_mod_time,",
        "   strm_local_path, nfo_local_path, show_dir, tmdb_id, status, fail_reason)",
        "VALUES",
        "<foreach collection='list' item='r' separator=','>",
        "  (#{r.watchConfigId}, #{r.gdRemote}, #{r.relFilePath}, #{r.pathHash}, #{r.fileModTime},",
        "   #{r.strmLocalPath}, #{r.nfoLocalPath}, #{r.showDir}, #{r.tmdbId},",
        "   #{r.status}, #{r.failReason})",
        "</foreach>",
        "</script>"
    })
    int insertBatch(@Param("list") List<StrmFileRecord> list);
}
