package com.gdupload.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gdupload.common.BusinessException;
import com.gdupload.entity.SubscribeBatchTask;
import com.gdupload.mapper.SubscribeBatchTaskMapper;
import com.gdupload.service.ISubscribeBatchExecutorService;
import com.gdupload.service.ISubscribeBatchTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 订阅批量搜索任务Service实现
 *
 * @author GD Upload Manager
 * @since 2026-01-25
 */
@Slf4j
@Service
public class SubscribeBatchTaskServiceImpl extends ServiceImpl<SubscribeBatchTaskMapper, SubscribeBatchTask>
        implements ISubscribeBatchTaskService {

    @Autowired
    private ISubscribeBatchExecutorService executorService;

    @Override
    public Long createTask(String taskName, String jsonData, Integer delayMin, Integer delayMax) {
        // 验证JSON数据
        try {
            Object json = JSONUtil.parse(jsonData);
            if (!(json instanceof cn.hutool.json.JSONArray)) {
                throw new BusinessException("JSON数据格式错误，必须是数组格式");
            }
        } catch (Exception e) {
            throw new BusinessException("JSON数据解析失败: " + e.getMessage());
        }

        // 创建任务
        SubscribeBatchTask task = new SubscribeBatchTask();
        task.setTaskName(taskName);
        task.setJsonData(jsonData);
        task.setDelayMin(delayMin);
        task.setDelayMax(delayMax);
        task.setStatus("PENDING");
        task.setTotalCount(JSONUtil.parseArray(jsonData).size());
        task.setCompletedCount(0);
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());

        baseMapper.insert(task);

        log.info("创建批量任务成功: taskId={}, taskName={}, totalCount={}",
                task.getId(), task.getTaskName(), task.getTotalCount());

        return task.getId();
    }

    @Override
    public void startTask(Long taskId) {
        SubscribeBatchTask task = baseMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }

        if ("RUNNING".equals(task.getStatus())) {
            throw new BusinessException("任务正在执行中");
        }

        if ("COMPLETED".equals(task.getStatus())) {
            throw new BusinessException("任务已完成，无法重新启动");
        }

        log.info("启动批量任务: taskId={}, taskName={}", taskId, task.getTaskName());

        // 异步执行任务
        executorService.executeTask(task);
    }

    @Override
    public void pauseTask(Long taskId) {
        SubscribeBatchTask task = baseMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }

        if (!"RUNNING".equals(task.getStatus())) {
            throw new BusinessException("任务未在执行中");
        }

        log.info("暂停批量任务: taskId={}, taskName={}", taskId, task.getTaskName());

        // 请求停止任务
        executorService.stopTask(taskId);
    }

    @Override
    public SubscribeBatchTask getTaskDetail(Long taskId) {
        SubscribeBatchTask task = baseMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return task;
    }

    @Override
    public Page<SubscribeBatchTask> getTaskPage(Page<SubscribeBatchTask> page) {
        LambdaQueryWrapper<SubscribeBatchTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SubscribeBatchTask::getCreateTime);
        return baseMapper.selectPage(page, wrapper);
    }
}
