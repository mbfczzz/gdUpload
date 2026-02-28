package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.service.IStrmService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * STRM 生成 Controller
 */
@RestController
@RequestMapping("/strm")
@RequiredArgsConstructor
public class StrmController {

    private final IStrmService strmService;

    /**
     * 启动 STRM 生成任务
     * Body: { "gdRemote": "media2", "gdSourcePath": "video/dramas/ShowName" }
     */
    @PostMapping("/generate")
    public Result<String> generate(@RequestBody Map<String, String> body) {
        String gdRemote     = body.get("gdRemote");
        String gdSourcePath = body.get("gdSourcePath");

        if (gdRemote == null || gdRemote.trim().isEmpty()) {
            return Result.error("gdRemote 不能为空");
        }
        if (gdSourcePath == null || gdSourcePath.trim().isEmpty()) {
            return Result.error("gdSourcePath 不能为空");
        }

        try {
            strmService.startGenerate(gdRemote.trim(), gdSourcePath.trim());
            return Result.success("STRM 生成任务已启动");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询任务状态（前端轮询）
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        return Result.success(strmService.getStatus());
    }
}
