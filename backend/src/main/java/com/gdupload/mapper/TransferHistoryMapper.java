package com.gdupload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdupload.entity.TransferHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 转存历史记录Mapper
 */
@Mapper
public interface TransferHistoryMapper extends BaseMapper<TransferHistory> {
}
