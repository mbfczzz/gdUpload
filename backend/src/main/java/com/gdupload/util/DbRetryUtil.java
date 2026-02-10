package com.gdupload.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.RecoverableDataAccessException;

import java.util.function.Supplier;

/**
 * 数据库操作重试工具
 * 用于处理数据库连接超时等可恢复的异常
 */
@Slf4j
public class DbRetryUtil {

    /**
     * 默认重试次数
     */
    private static final int DEFAULT_RETRY_TIMES = 3;

    /**
     * 默认重试间隔（毫秒）
     */
    private static final long DEFAULT_RETRY_INTERVAL = 1000;

    /**
     * 执行数据库操作，失败时自动重试
     *
     * @param operation 数据库操作
     * @param <T> 返回值类型
     * @return 操作结果
     */
    public static <T> T execute(Supplier<T> operation) {
        return execute(operation, DEFAULT_RETRY_TIMES, DEFAULT_RETRY_INTERVAL);
    }

    /**
     * 执行数据库操作，失败时自动重试
     *
     * @param operation 数据库操作
     * @param retryTimes 重试次数
     * @param retryInterval 重试间隔（毫秒）
     * @param <T> 返回值类型
     * @return 操作结果
     */
    public static <T> T execute(Supplier<T> operation, int retryTimes, long retryInterval) {
        Exception lastException = null;

        for (int i = 0; i <= retryTimes; i++) {
            try {
                return operation.get();
            } catch (RecoverableDataAccessException e) {
                lastException = e;
                if (i < retryTimes) {
                    log.warn("数据库操作失败，第 {} 次重试（共{}次）: {}", i + 1, retryTimes, e.getMessage());
                    try {
                        Thread.sleep(retryInterval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                } else {
                    log.error("数据库操作失败，已达到最大重试次数 {}", retryTimes, e);
                }
            } catch (Exception e) {
                // 非可恢复异常，直接抛出
                throw e;
            }
        }

        throw new RuntimeException("数据库操作失败，已重试 " + retryTimes + " 次", lastException);
    }

    /**
     * 执行无返回值的数据库操作，失败时自动重试
     *
     * @param operation 数据库操作
     */
    public static void executeVoid(Runnable operation) {
        execute(() -> {
            operation.run();
            return null;
        });
    }

    /**
     * 执行无返回值的数据库操作，失败时自动重试
     *
     * @param operation 数据库操作
     * @param retryTimes 重试次数
     * @param retryInterval 重试间隔（毫秒）
     */
    public static void executeVoid(Runnable operation, int retryTimes, long retryInterval) {
        execute(() -> {
            operation.run();
            return null;
        }, retryTimes, retryInterval);
    }
}
