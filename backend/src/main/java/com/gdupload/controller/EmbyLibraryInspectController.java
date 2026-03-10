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
     * 扫描本地STRM目录并返回文件树 + 验证结果
     */
    @GetMapping("/inspect")
    public Result<List<EmbyLibraryFileNode>> inspect(
            @RequestParam String localPath) {
        List<EmbyLibraryFileNode> tree = inspectService.inspectLibrary(localPath);
        return Result.success(tree);
    }

    /**
     * 返回汇总统计
     */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(
            @RequestParam String localPath) {
        Map<String, Object> result = inspectService.getInspectSummary(localPath);
        return Result.success(result);
    }
}
