package com.gdupload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdupload.entity.ArchiveHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 归档历史记录 Mapper
 */
@Mapper
public interface ArchiveHistoryMapper extends BaseMapper<ArchiveHistory> {
}
