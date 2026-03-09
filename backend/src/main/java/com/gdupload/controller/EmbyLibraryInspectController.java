package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.dto.EmbyLibraryFileNode;
import com.gdupload.service.IEmbyLibraryInspectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Emby库检查控制器
 */
@RestController
@RequestMapping("/emby-library")
@RequiredArgsConstructor
public class EmbyLibraryInspectController {

    private final IEmbyLibraryInspectService inspectService;

    /**
     * 扫描指定路径并返回文件树 + 验证结果
     */
    @GetMapping("/inspect")
    public Result<List<EmbyLibraryFileNode>> inspect(
            @RequestParam String rcloneRemote,
            @RequestParam(defaultValue = "/") String path) {
        List<EmbyLibraryFileNode> tree = inspectService.inspectLibrary(rcloneRemote, path);
        return Result.success(tree);
    }

    /**
     * 返回汇总统计
     */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(
            @RequestParam String rcloneRemote,
            @RequestParam(defaultValue = "/") String path) {
        Map<String, Object> result = inspectService.getInspectSummary(rcloneRemote, path);
        return Result.success(result);
    }
}
