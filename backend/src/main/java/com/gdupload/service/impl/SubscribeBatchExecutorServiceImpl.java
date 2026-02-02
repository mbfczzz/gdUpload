package com.gdupload.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdupload.entity.SubscribeBatchLog;
import com.gdupload.entity.SubscribeBatchTask;
import com.gdupload.mapper.SubscribeBatchLogMapper;
import com.gdupload.mapper.SubscribeBatchTaskMapper;
import com.gdupload.service.ISubscribeBatchExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订阅批量搜索执行器Service实现
 *
 * @author GD Upload Manager
 * @since 2026-01-25
 */
@Slf4j
@Service
public class SubscribeBatchExecutorServiceImpl implements ISubscribeBatchExecutorService {

    @Autowired
    private SubscribeBatchTaskMapper taskMapper;

    @Autowired
    private SubscribeBatchLogMapper logMapper;

    // 存储需要停止的任务ID
    private static final Set<Long> STOP_FLAGS = ConcurrentHashMap.newKeySet();

    // API配置
    private static final String API_BASE_URL = "http://104.251.122.51:3000/api/v1/subscribe/search/";
    private static final String AUTH_TOKEN = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3Njk4NjQxNDEsImlhdCI6MTc2OTE3Mjk0MSwic3ViIjoiMSIsInVzZXJuYW1lIjoiYWRtaW4iLCJzdXBlcl91c2VyIjp0cnVlLCJsZXZlbCI6MiwicHVycG9zZSI6ImF1dGhlbnRpY2F0aW9uIn0.Iy35JG05DpMuHeMCIzhXV6uFRBkONhcQw3_bA5IFU4Q";

    @Override
    @Async("taskExecutor")
    public void executeTask(SubscribeBatchTask task) {
        log.info("开始执行批量任务: taskId={}, taskName={}", task.getId(), task.getTaskName());

        try {
            // 更新任务状态为执行中
            task.setStatus("RUNNING");
            task.setStartTime(LocalDateTime.now());
            taskMapper.updateById(task);

            // 解析JSON数据
            JSONArray jsonArray = JSONUtil.parseArray(task.getJsonData());
            if (jsonArray == null || jsonArray.isEmpty()) {
                log.error("任务JSON数据为空: taskId={}", task.getId());
                updateTaskStatus(task.getId(), "FAILED", "JSON数据为空");
                return;
            }

            // 获取已执行的订阅ID
            Set<Integer> executedIds = getExecutedSubscribeIds(task.getId());

            int totalCount = jsonArray.size();
            int completedCount = executedIds.size();
            int successCount = 0;
            int failedCount = 0;

            Random random = new Random();

            // 遍历执行
            for (int i = 0; i < jsonArray.size(); i++) {
                // 检查是否需要停止
                if (STOP_FLAGS.contains(task.getId())) {
                    log.info("任务被手动停止: taskId={}", task.getId());
                    updateTaskStatus(task.getId(), "PAUSED", null);
                    STOP_FLAGS.remove(task.getId());
                    return;
                }

                JSONObject item = jsonArray.getJSONObject(i);
                Integer subscribeId = item.getInt("id");

                // 跳过已执行的订阅
                if (executedIds.contains(subscribeId)) {
                    log.info("跳过��执行的订阅: subscribeId={}", subscribeId);
                    continue;
                }

                // 计算延迟时间（第一个不延迟）
                int delaySeconds = 0;
                if (i > 0 || completedCount > 0) {
                    int delayMinMs = task.getDelayMin() * 60 * 1000;
                    int delayMaxMs = task.getDelayMax() * 60 * 1000;
                    int delayMs = random.nextInt(delayMaxMs - delayMinMs + 1) + delayMinMs;
                    delaySeconds = delayMs / 1000;

                    log.info("等待{}秒后执行下一个请求...", delaySeconds);
                    Thread.sleep(delayMs);
                }

                // 再次检查是否需要停止
                if (STOP_FLAGS.contains(task.getId())) {
                    log.info("任务被手动停止: taskId={}", task.getId());
                    updateTaskStatus(task.getId(), "PAUSED", null);
                    STOP_FLAGS.remove(task.getId());
                    return;
                }

                // 执行搜索请求
                SubscribeBatchLog batchLog = new SubscribeBatchLog();
                batchLog.setTaskId(task.getId());
                batchLog.setSubscribeId(subscribeId);
                batchLog.setSubscribeName(item.getStr("name", "未知"));
                batchLog.setDelaySeconds(delaySeconds);
                batchLog.setExecuteTime(LocalDateTime.now());

                String requestUrl = API_BASE_URL + subscribeId;
                batchLog.setRequestUrl(requestUrl);
                batchLog.setRequestData(item.toString());

                try {
                    // 发送HTTP请求
                    HttpResponse response = HttpRequest.get(requestUrl)
                            .header("Accept", "application/json, text/plain, */*")
                            .header("Accept-Language", "zh-CN,zh;q=0.9")
                            .header("Authorization", AUTH_TOKEN)
                            .timeout(30000)
                            .execute();

                    String responseBody = response.body();
                    batchLog.setResponseData(responseBody);

                    if (response.isOk()) {
                        batchLog.setStatus("SUCCESS");
                        successCount++;
                        log.info("订阅搜索成功: subscribeId={}, name={}", subscribeId, batchLog.getSubscribeName());
                    } else {
                        batchLog.setStatus("FAILED");
                        batchLog.setErrorMessage("HTTP状态码: " + response.getStatus());
                        failedCount++;
                        log.error("订阅搜索失败: subscribeId={}, status={}", subscribeId, response.getStatus());
                    }
                } catch (Exception e) {
                    batchLog.setStatus("FAILED");
                    batchLog.setErrorMessage(e.getMessage());
                    failedCount++;
                    log.error("订阅搜索异常: subscribeId={}, error={}", subscribeId, e.getMessage(), e);
                }

                // 保存日志
                logMapper.insert(batchLog);

                // 更新任务进度
                completedCount++;
                task.setCompletedCount(completedCount);
                task.setSuccessCount(successCount);
                task.setFailedCount(failedCount);
                taskMapper.updateById(task);
            }

            // 任务完成
            task.setStatus("COMPLETED");
            task.setEndTime(LocalDateTime.now());
            task.setCompletedCount(completedCount);
            task.setSuccessCount(successCount);
            task.setFailedCount(failedCount);
            taskMapper.updateById(task);

            log.info("批量任务执行完成: taskId={}, total={}, success={}, failed={}",
                    task.getId(), totalCount, successCount, failedCount);

        } catch (Exception e) {
            log.error("批量任务执行异常: taskId={}, error={}", task.getId(), e.getMessage(), e);
            updateTaskStatus(task.getId(), "FAILED", e.getMessage());
        }
    }

    @Override
    public void stopTask(Long taskId) {
        log.info("请求停止任务: taskId={}", taskId);
        STOP_FLAGS.add(taskId);
    }

    /**
     * 获取已执行的订阅ID集合
     */
    private Set<Integer> getExecutedSubscribeIds(Long taskId) {
        LambdaQueryWrapper<SubscribeBatchLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubscribeBatchLog::getTaskId, taskId);
        wrapper.select(SubscribeBatchLog::getSubscribeId);

        Set<Integer> executedIds = new HashSet<>();
        logMapper.selectList(wrapper).forEach(log -> executedIds.add(log.getSubscribeId()));

        return executedIds;
    }

    /**
     * 更新任务状态
     */
    private void updateTaskStatus(Long taskId, String status, String errorMessage) {
        SubscribeBatchTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(status);
            if ("FAILED".equals(status) || "COMPLETED".equals(status)) {
                task.setEndTime(LocalDateTime.now());
            }
            taskMapper.updateById(task);
        }
    }
}
