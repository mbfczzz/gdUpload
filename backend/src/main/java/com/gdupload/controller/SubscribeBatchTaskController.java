package com.gdupload.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.common.PageResult;
import com.gdupload.common.Result;
import com.gdupload.entity.SubscribeBatchLog;
import com.gdupload.entity.SubscribeBatchTask;
import com.gdupload.mapper.SubscribeBatchLogMapper;
import com.gdupload.service.ISubscribeBatchTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订阅批量搜索任务Controller
 *
 * @author GD Upload Manager
 * @since 2026-01-25
 */
@Slf4j
@RestController
@RequestMapping("/subscribe-batch")
public class SubscribeBatchTaskController {

    @Autowired
    private ISubscribeBatchTaskService taskService;

    @Autowired
    private SubscribeBatchLogMapper logMapper;

    /**
     * 创建批量任务
     */
    @PostMapping("/create")
    public Result<Long> createTask(@RequestBody Map<String, Object> params) {
        String taskName = (String) params.get("taskName");
        String jsonData = (String) params.get("jsonData");
        Integer delayMin = (Integer) params.getOrDefault("delayMin", 1);
        Integer delayMax = (Integer) params.getOrDefault("delayMax", 2);

        Long taskId = taskService.createTask(taskName, jsonData, delayMin, delayMax);
        return Result.success(taskId);
    }

    /**
     * 启动任务
     */
    @PostMapping("/start/{taskId}")
    public Result<Void> startTask(@PathVariable Long taskId) {
        taskService.startTask(taskId);
        return Result.success();
    }

    /**
     * 暂停任务
     */
    @PostMapping("/pause/{taskId}")
    public Result<Void> pauseTask(@PathVariable Long taskId) {
        taskService.pauseTask(taskId);
        return Result.success();
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{taskId}")
    public Result<SubscribeBatchTask> getTaskDetail(@PathVariable Long taskId) {
        SubscribeBatchTask task = taskService.getTaskDetail(taskId);
        return Result.success(task);
    }

    /**
     * 分页查询任务列表
     */
    @GetMapping("/page")
    public Result<PageResult<SubscribeBatchTask>> getTaskPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {

        Page<SubscribeBatchTask> page = new Page<>(current, size);
        Page<SubscribeBatchTask> result = taskService.getTaskPage(page);

        PageResult<SubscribeBatchTask> pageResult = new PageResult<>();
        pageResult.setRecords(result.getRecords());
        pageResult.setTotal(result.getTotal());
        pageResult.setCurrent(result.getCurrent());
        pageResult.setSize(result.getSize());

        return Result.success(pageResult);
    }

    /**
     * 获取任务日志
     */
    @GetMapping("/{taskId}/logs")
    public Result<List<SubscribeBatchLog>> getTaskLogs(@PathVariable Long taskId) {
        LambdaQueryWrapper<SubscribeBatchLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubscribeBatchLog::getTaskId, taskId);
        wrapper.orderByDesc(SubscribeBatchLog::getExecuteTime);

        List<SubscribeBatchLog> logs = logMapper.selectList(wrapper);
        return Result.success(logs);
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{taskId}")
    public Result<Void> deleteTask(@PathVariable Long taskId) {
        taskService.removeById(taskId);
        return Result.success();
    }

    /**
     * 获取任务统计
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // 总任务数
        long totalTasks = taskService.count();
        stats.put("totalTasks", totalTasks);

        // 运行中的任务数
        LambdaQueryWrapper<SubscribeBatchTask> runningWrapper = new LambdaQueryWrapper<>();
        runningWrapper.eq(SubscribeBatchTask::getStatus, "RUNNING");
        long runningTasks = taskService.count(runningWrapper);
        stats.put("runningTasks", runningTasks);

        // 已完成的任务数
        LambdaQueryWrapper<SubscribeBatchTask> completedWrapper = new LambdaQueryWrapper<>();
        completedWrapper.eq(SubscribeBatchTask::getStatus, "COMPLETED");
        long completedTasks = taskService.count(completedWrapper);
        stats.put("completedTasks", completedTasks);

        // 失败的任务数
        LambdaQueryWrapper<SubscribeBatchTask> failedWrapper = new LambdaQueryWrapper<>();
        failedWrapper.eq(SubscribeBatchTask::getStatus, "FAILED");
        long failedTasks = taskService.count(failedWrapper);
        stats.put("failedTasks", failedTasks);

        return Result.success(stats);
    }
}
