package com.gdupload.service.impl;

import com.gdupload.service.IWebSocketService;
import com.gdupload.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket消息推送服务实现
 *
 * @author GD Upload Manager
 * @since 2026-01-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketServiceImpl implements IWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void pushTaskProgress(Long taskId, Integer progress, Integer uploadedCount, Integer totalCount,
                                  Long uploadedSize, Long totalSize, String currentFileName) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "TASK_PROGRESS");
        message.put("taskId", taskId);
        message.put("progress", progress);
        message.put("uploadedCount", uploadedCount);
        message.put("totalCount", totalCount);
        message.put("uploadedSize", uploadedSize);
        message.put("totalSize", totalSize);
        message.put("currentFileName", currentFileName);
        message.put("timestamp", DateTimeUtil.now().format(TIME_FORMATTER));

        // 推送到特定任务的订阅者
        messagingTemplate.convertAndSend("/topic/task/" + taskId, message);

        // 推送到所有任务列表的订阅者
        messagingTemplate.convertAndSend("/topic/tasks", message);

        log.debug("推送任务进度: taskId={}, progress={}%, uploadedCount={}/{}",
            taskId, progress, uploadedCount, totalCount);
    }

    @Override
    public void pushTaskStatus(Long taskId, Integer status, String statusMessage) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "TASK_STATUS");
        message.put("taskId", taskId);
        message.put("status", status);
        message.put("message", statusMessage);
        message.put("timestamp", DateTimeUtil.now().format(TIME_FORMATTER));

        // 推送到特定任务的订阅者
        messagingTemplate.convertAndSend("/topic/task/" + taskId, message);

        // 推送到所有任务列表的订阅者
        messagingTemplate.convertAndSend("/topic/tasks", message);

        log.info("推送任务状态: taskId={}, status={}, message={}", taskId, status, statusMessage);
    }

    @Override
    public void pushFileStatus(Long taskId, Long fileId, String fileName, Integer status, String statusMessage) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "FILE_STATUS");
        message.put("taskId", taskId);
        message.put("fileId", fileId);
        message.put("fileName", fileName);
        message.put("status", status);
        message.put("message", statusMessage);
        message.put("timestamp", DateTimeUtil.now().format(TIME_FORMATTER));

        // 推送到特定任务的订阅者
        messagingTemplate.convertAndSend("/topic/task/" + taskId, message);

        log.debug("推送文件状态: taskId={}, fileId={}, fileName={}, status={}",
            taskId, fileId, fileName, status);
    }
}
