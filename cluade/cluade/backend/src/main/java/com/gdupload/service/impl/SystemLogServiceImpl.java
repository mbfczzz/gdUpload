package com.gdupload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gdupload.entity.SystemLog;
import com.gdupload.mapper.SystemLogMapper;
import com.gdupload.service.ISystemLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 系统日志服务实现
 *
 * @author GD Upload Manager
 * @since 2026-01-19
 */
@Slf4j
@Service
public class SystemLogServiceImpl extends ServiceImpl<SystemLogMapper, SystemLog> implements ISystemLogService {

    @Override
    public Page<SystemLog> pageLogs(Page<SystemLog> page, Integer logType, String logLevel,
                                    String module, String keyword) {
        LambdaQueryWrapper<SystemLog> wrapper = new LambdaQueryWrapper<>();

        if (logType != null) {
            wrapper.eq(SystemLog::getLogType, logType);
        }

        if (logLevel != null && !logLevel.trim().isEmpty()) {
            wrapper.eq(SystemLog::getLogLevel, logLevel);
        }

        if (module != null && !module.trim().isEmpty()) {
            wrapper.eq(SystemLog::getModule, module);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(SystemLog::getMessage, keyword)
                    .or().like(SystemLog::getOperation, keyword)
                    .or().like(SystemLog::getDetail, keyword));
        }

        wrapper.orderByDesc(SystemLog::getCreateTime);

        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void log(Integer logType, String logLevel, String module, String operation,
                    String message, String detail) {
        SystemLog systemLog = new SystemLog();
        systemLog.setLogType(logType);
        systemLog.setLogLevel(logLevel);
        systemLog.setModule(module);
        systemLog.setOperation(operation);
        systemLog.setMessage(message);
        systemLog.setDetail(detail);
        systemLog.setCreateTime(LocalDateTime.now());

        this.save(systemLog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logTask(Long taskId, Integer logType, String logLevel, String operation, String message) {
        SystemLog systemLog = new SystemLog();
        systemLog.setLogType(logType);
        systemLog.setLogLevel(logLevel);
        systemLog.setModule("TASK");
        systemLog.setOperation(operation);
        systemLog.setTaskId(taskId);
        systemLog.setMessage(message);
        systemLog.setCreateTime(LocalDateTime.now());

        this.save(systemLog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logAccount(Long accountId, Integer logType, String logLevel, String operation, String message) {
        SystemLog systemLog = new SystemLog();
        systemLog.setLogType(logType);
        systemLog.setLogLevel(logLevel);
        systemLog.setModule("ACCOUNT");
        systemLog.setOperation(operation);
        systemLog.setAccountId(accountId);
        systemLog.setMessage(message);
        systemLog.setCreateTime(LocalDateTime.now());

        this.save(systemLog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logFileUpload(Long taskId, Long fileId, String fileName, Long fileSize,
                              Long accountId, String operation, String message, String detail) {
        SystemLog systemLog = new SystemLog();
        systemLog.setLogType(1); // 信息
        systemLog.setLogLevel("INFO");
        systemLog.setModule("FILE_UPLOAD");
        systemLog.setOperation(operation);
        systemLog.setTaskId(taskId);
        systemLog.setFileId(fileId);
        systemLog.setFileName(fileName);
        systemLog.setFileSize(fileSize);
        systemLog.setAccountId(accountId);
        systemLog.setMessage(message);
        systemLog.setDetail(detail);
        systemLog.setCreateTime(LocalDateTime.now());

        this.save(systemLog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logTaskOperation(Long taskId, String taskName, String operation, String message) {
        SystemLog systemLog = new SystemLog();
        systemLog.setLogType(1); // 信息
        systemLog.setLogLevel("INFO");
        systemLog.setModule("TASK_OPERATION");
        systemLog.setOperation(operation);
        systemLog.setTaskId(taskId);
        systemLog.setMessage(message);
        systemLog.setDetail("任务名称: " + taskName);
        systemLog.setCreateTime(LocalDateTime.now());

        this.save(systemLog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long cleanExpiredLogs(Integer days) {
        LocalDateTime expireTime = LocalDateTime.now().minusDays(days);

        LambdaQueryWrapper<SystemLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(SystemLog::getCreateTime, expireTime);

        long count = this.count(wrapper);

        if (count > 0) {
            this.remove(wrapper);
            log.info("清理过期日志: count={}", count);
        }

        return count;
    }
}
