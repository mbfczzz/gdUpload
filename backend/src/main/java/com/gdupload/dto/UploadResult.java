package com.gdupload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 上传结果DTO
 *
 * @author GD Upload Manager
 * @since 2026-01-21
 */
@Data
@AllArgsConstructor
public class UploadResult {
    private boolean success;
    private boolean quotaExceeded;
    private boolean timeout;
    private String errorMessage;

    public static UploadResult success() {
        return new UploadResult(true, false, false, null);
    }

    public static UploadResult failure(String errorMessage) {
        return new UploadResult(false, false, false, errorMessage);
    }

    public static UploadResult quotaExceeded(String errorMessage) {
        return new UploadResult(false, true, false, errorMessage);
    }

    public static UploadResult timeout(String errorMessage) {
        return new UploadResult(false, false, true, errorMessage);
    }
}
