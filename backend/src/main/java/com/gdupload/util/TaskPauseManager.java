package com.gdupload.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 通用任务暂停/恢复管理器。
 * <p>
 * 解决的核心问题：
 * <ol>
 *   <li>用户点击暂停后，工作线程还在跑 → 引入「暂停中」过渡状态</li>
 *   <li>各模块暂停机制不统一 → 提供统一 API</li>
 * </ol>
 * <p>
 * <strong>状态流转:</strong>  运行中(1) → 暂停中(6) → 已暂停(3)
 * <p>
 * <strong>使用方法:</strong>
 * <pre>
 *   // ① 任务启动时注册
 *   TaskPauseManager.register(taskId, activeThreadCount);
 *
 *   // ② 工作线程中检查是否应该停止
 *   if (TaskPauseManager.shouldStop(taskId)) { return; }
 *
 *   // ③ 工作线程完成一项工作后通知
 *   TaskPauseManager.onThreadFinished(taskId);
 *
 *   // ④ 暂停请求
 *   TaskPauseManager.requestPause(taskId, statusCallback);
 *
 *   // ⑤ 任务彻底结束时清理
 *   TaskPauseManager.unregister(taskId);
 * </pre>
 */
@Slf4j
public class TaskPauseManager {

    private TaskPauseManager() {}

    /** 每个任务的上下文 */
    private static final Map<Long, TaskContext> CONTEXTS = new ConcurrentHashMap<>();

    // ─── 内部上下文 ──────────────────────────────────────────��───

    private static class TaskContext {
        /** 暂停标志 — 工作线程检查此标志决定是否停止 */
        final AtomicBoolean stopFlag = new AtomicBoolean(false);
        /** 活跃工作线程数 */
        final AtomicInteger activeThreads;
        /** 所有线程都完成时触发的回调（用于把状态 6→3） */
        volatile Consumer<Long> onAllStopped;

        TaskContext(int threadCount) {
            this.activeThreads = new AtomicInteger(threadCount);
        }
    }

    // ─── 公开 API ───────────────────────────────────────────────

    /**
     * 注册任务（任务启动时调用）
     *
     * @param taskId      任务ID
     * @param threadCount 预计会有多少工作线程/工作项
     */
    public static void register(long taskId, int threadCount) {
        CONTEXTS.put(taskId, new TaskContext(threadCount));
        log.info("[TaskPauseManager] 注册任务: taskId={}, threadCount={}", taskId, threadCount);
    }

    /**
     * 注销任务（任务彻底结束时调用）
     */
    public static void unregister(long taskId) {
        CONTEXTS.remove(taskId);
        log.debug("[TaskPauseManager] 注销任务: taskId={}", taskId);
    }

    /**
     * 增加活跃线程计数（在提交工作项到线程池时调用）。
     * <p>
     * 适用于无法预知总工作项数的场景（如 BatchArchive 按需提交模式）：
     * 先 register(taskId, 0)，每提交一个任务调用 addActiveThread(taskId)，
     * 每完成一个任务调用 onThreadFinished(taskId)。
     *
     * @return 增加后的活跃线程数，如果任务未注册返回 -1
     */
    public static int addActiveThread(long taskId) {
        TaskContext ctx = CONTEXTS.get(taskId);
        if (ctx == null) return -1;
        return ctx.activeThreads.incrementAndGet();
    }

    /**
     * 工作线程检查是否应该停止。
     * <p>
     * 在循环或每项工作开始前调用，返回 true 表示应立即停止。
     */
    public static boolean shouldStop(long taskId) {
        TaskContext ctx = CONTEXTS.get(taskId);
        return ctx != null && ctx.stopFlag.get();
    }

    /**
     * 获取底层 AtomicBoolean 引用（兼容已有代码直接传 stopFlag 的场景）。
     * <p>
     * 如果任务未注册则返回 null。
     */
    public static AtomicBoolean getStopFlag(long taskId) {
        TaskContext ctx = CONTEXTS.get(taskId);
        return ctx != null ? ctx.stopFlag : null;
    }

    /**
     * 工作线程/工作项完成后调用。
     * <p>
     * 当 activeThreads 降为 0 且 stopFlag=true 时，自动触发 onAllStopped 回调
     * （回调负责把数据库状态从 6→3）。
     */
    public static void onThreadFinished(long taskId) {
        TaskContext ctx = CONTEXTS.get(taskId);
        if (ctx == null) return;

        int remaining = ctx.activeThreads.decrementAndGet();
        log.debug("[TaskPauseManager] 线程完成: taskId={}, remaining={}", taskId, remaining);

        if (remaining <= 0 && ctx.stopFlag.get()) {
            // 必须在 synchronized 内读取并置空回调，防止与 requestPause 产生双触发
            Consumer<Long> callback;
            synchronized (ctx) {
                callback = ctx.onAllStopped;
                ctx.onAllStopped = null;
            }
            if (callback != null) {
                log.info("[TaskPauseManager] 所有线程已停止: taskId={}, 触发暂停完成回调", taskId);
                try {
                    callback.accept(taskId);
                } catch (Exception e) {
                    log.error("[TaskPauseManager] 暂停完成回调异常: taskId={}", taskId, e);
                }
            }
        }
    }

    /**
     * 请求暂停任务。
     * <p>
     * 1. 立即设置 stopFlag = true，新的工作项不再启动<br>
     * 2. 注册 onAllStopped 回调，当所有活跃线程完成后自动触发<br>
     * 3. 调用方应立即将数据库状态设为 6（暂停中）<br>
     * 4. 回调中将状态改为 3（已暂停）
     *
     * @param taskId         任务ID
     * @param onAllStopped   所有工作线程停止后的回调（参数为 taskId）
     */
    public static void requestPause(long taskId, Consumer<Long> onAllStopped) {
        TaskContext ctx = CONTEXTS.get(taskId);
        if (ctx == null) {
            log.warn("[TaskPauseManager] 任务未注册或已结束: taskId={}", taskId);
            // 任务不在运行中，直接触发回调
            if (onAllStopped != null) onAllStopped.accept(taskId);
            return;
        }

        // 使用 synchronized 保证 stopFlag + onAllStopped + activeThreads 检查的原子性，
        // 防止与 onThreadFinished 中的回调提取产生 TOCTOU 竞态导致回调双触发
        Consumer<Long> fireNow = null;
        synchronized (ctx) {
            ctx.stopFlag.set(true);
            ctx.onAllStopped = onAllStopped;
            log.info("[TaskPauseManager] 暂停请求已发送: taskId={}, activeThreads={}", taskId, ctx.activeThreads.get());

            // 如果当前已经没有活跃线程了，直接触发
            if (ctx.activeThreads.get() <= 0 && onAllStopped != null) {
                ctx.onAllStopped = null;
                fireNow = onAllStopped;
            }
        }
        // 在 synchronized 外触发回调，避免持锁调用外部代码
        if (fireNow != null) {
            log.info("[TaskPauseManager] 无活跃线程，直接触发暂停完成: taskId={}", taskId);
            fireNow.accept(taskId);
        }
    }

    /**
     * 请求取消任务（与暂停相同的机制，但最终状态不同）。
     */
    public static void requestCancel(long taskId, Consumer<Long> onAllStopped) {
        // 取消和暂停的 stopFlag 机制完全一样，区别在回调内的数据库状态
        requestPause(taskId, onAllStopped);
    }

    /**
     * 判断任务是否已注册且在管理中。
     */
    public static boolean isManaged(long taskId) {
        return CONTEXTS.containsKey(taskId);
    }

    /**
     * 获取当前活跃线程数（调试/日志用）。
     */
    public static int getActiveThreadCount(long taskId) {
        TaskContext ctx = CONTEXTS.get(taskId);
        return ctx != null ? ctx.activeThreads.get() : 0;
    }
}
