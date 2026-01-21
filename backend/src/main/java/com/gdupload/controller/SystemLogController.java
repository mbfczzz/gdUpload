package com.gdupload.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.common.PageResult;
import com.gdupload.common.Result;
import com.gdupload.entity.SystemLog;
import com.gdupload.service.ISystemLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 系统日志控制器
 *
 * @author GD Upload Manager
 * @since 2026-01-19
 */
@Slf4j
@RestController
@RequestMapping("/log")
@RequiredArgsConstructor
public class SystemLogController {

    private final ISystemLogService systemLogService;

    /**
     * 分页查询日志
     */
    @GetMapping("/page")
    public Result<PageResult<SystemLog>> pageLogs(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Integer logType,
            @RequestParam(required = false) String logLevel,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String keyword) {

        Page<SystemLog> page = new Page<>(current, size);
        Page<SystemLog> result = systemLogService.pageLogs(page, logType, logLevel, module, keyword);

        PageResult<SystemLog> pageResult = new PageResult<>();
        pageResult.setRecords(result.getRecords());
        pageResult.setTotal(result.getTotal());
        pageResult.setCurrent(result.getCurrent());
        pageResult.setSize(result.getSize());

        return Result.success(pageResult);
    }

    /**
     * 清理过期日志
     */
    @DeleteMapping("/clean")
    public Result<Long> cleanExpiredLogs(@RequestParam(defaultValue = "30") Integer days) {
        long count = systemLogService.cleanExpiredLogs(days);
        return Result.success(count);
    }
}
