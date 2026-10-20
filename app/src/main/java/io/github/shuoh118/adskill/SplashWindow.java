package io.github.shuoh118.adskill;

import android.os.SystemClock;

/*
 * 冷启动窗口。
 *
 * === 为什么需要这个 ===
 *
 * 发现页变成空白的根因：SplashKill 的 setText 钩子和 AdHook 是整个进程
 * 生命周期都生效的。用户点进"发现"页后，页面里的推广文案、广告类名
 * 被当成开屏广告，于是大图被隐藏、内容被摘掉，整页就白了。
 *
 * 开屏广告只在进程刚起来那几秒存在。过了这个窗口，所有开屏相关逻辑
 * 都必须停手，否则就是在杀正常页面。
 *
 * 窗口长度：6 秒。正常冷启动到主页约 1.2 秒，给足余量。
 * 一旦有过 Activity 进入 resumed 且被确认是主页，窗口立即关闭。
 */
public final class SplashWindow {

    private static final long WINDOW_MS = 6000L;

    private static volatile long openedAt;
    private static volatile boolean closed;

    private SplashWindow() {
    }

    public static void open() {
        if (openedAt == 0) {
            openedAt = SystemClock.uptimeMillis();
        }
    }

    // 主页已就绪，立刻关闭窗口
    public static void close() {
        closed = true;
    }

    // 现在还在开屏窗口内吗
    public static boolean active() {
        if (closed) {
            return false;
        }
        long start = openedAt;
        if (start == 0) {
            return false;
        }
        return SystemClock.uptimeMillis() - start < WINDOW_MS;
    }

    public static long age() {
        long start = openedAt;
        return start == 0 ? 0 : SystemClock.uptimeMillis() - start;
    }
}
