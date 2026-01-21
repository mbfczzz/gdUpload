package com.gdupload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdupload.entity.UploadTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 上传任务Mapper
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Mapper
public interface UploadTaskMapper extends BaseMapper<UploadTask> {
}
