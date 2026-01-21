package com.gdupload.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.gdupload.util.DateTimeUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis Plus字段自动填充处理器
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, DateTimeUtil.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, DateTimeUtil.now());
    }

    /**
     * 更新时自动填充
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, DateTimeUtil.now());
    }
}
