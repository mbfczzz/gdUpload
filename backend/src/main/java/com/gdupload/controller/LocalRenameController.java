package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.service.ILocalRenameService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地文件格式化重命名控制器
 */
@RestController
@RequestMapping("/local-rename")
@RequiredArgsConstructor
public class LocalRenameController {

    private final ILocalRenameService localRenameService;

    /** 启动任务 */
    @PostMapping("/start")
    public Result<Map<String, Object>> startTask(@RequestBody StartRequest req) {
        if (req.getDirPath() == null || req.getDirPath().trim().isEmpty()) {
            return Result.error("目录路径不能为空");
        }
        String taskId = localRenameService.startTask(req.getDirPath().trim());
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        return Result.success(result);
    }

    /** 查询任务状态 */
    @GetMapping("/status/{taskId}")
    public Result<Map<String, Object>> getStatus(@PathVariable String taskId) {
        return Result.success(localRenameService.getStatus(taskId));
    }

    /** 取消任务 */
    @PostMapping("/cancel/{taskId}")
    public Result<Void> cancelTask(@PathVariable String taskId) {
        localRenameService.cancelTask(taskId);
        return Result.success();
    }

    @Data
    public static class StartRequest {
        private String dirPath;
    }
}
