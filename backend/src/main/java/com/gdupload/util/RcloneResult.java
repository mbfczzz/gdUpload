package com.gdupload.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rclone执行结果
 *
 * @author GD Upload Manager
 * @since 2026-01-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RcloneResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 是否是配额超限错误
     */
    private boolean quotaExceeded;

    /**
     * 是否是超时错误
     */
    private boolean timeout;

    public static RcloneResult success() {
        return new RcloneResult(true, null, false, false);
    }

    public static RcloneResult failure(String errorMessage, boolean quotaExceeded) {
        return new RcloneResult(false, errorMessage, quotaExceeded, false);
    }

    public static RcloneResult timeout(String errorMessage) {
        return new RcloneResult(false, errorMessage, false, true);
    }
}
