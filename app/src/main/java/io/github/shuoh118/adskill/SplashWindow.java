package io.github.shuoh118.adskill;

import android.os.SystemClock;

/*
 * 开屏窗口。
 *
 * === 为什么需要窗口 ===
 *
 * SplashKill 的 setText 钩子如果在整个进程生命周期都生效，
 * 用户点进"发现"页后，页面里的推广文案会被当成开屏广告，
 * 大图被隐藏、内容被摘掉，整页就白了。
 *
 * 所以开屏逻辑必须限时。窗口外一律停手。
 *
 * === 后台切回来的问题 ===
 *
 * 原来 open() 只在进程启动时调用一次，close() 之后永久关闭。
 * 网易云从后台切回前台时会重新拉一次开屏广告，
 * 但窗口已经关了，所以拦不住。
 *
 * === 现在 ===
 *
 * 每次 Activity 进入前台（onResume）都重新开窗。
 * 开窗后 6 秒内有效，主页出现就关闭。
 * 这样冷启动、切回来、从通知栏点进来，都能拦。
 */
public final class SplashWindow {

    // 窗口长度：从一次进入前台算起，6 秒内有效
    private static final long WINDOW_MS = 6000L;

    private static volatile long openedAt;
    private static volatile boolean closed;

    private SplashWindow() {
    }

    /** 开启窗口。每次进前台都调用，会重置计时并解除关闭状态 */
    public static void open() {
        openedAt = SystemClock.uptimeMillis();
        closed = false;
    }

    /** 主页已就绪，关闭窗口 */
    public static void close() {
        closed = true;
    }

    /** 现在还在窗口内吗 */
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
