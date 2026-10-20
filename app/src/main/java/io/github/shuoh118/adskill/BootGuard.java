package io.github.shuoh118.adskill;

import java.util.concurrent.atomic.AtomicBoolean;

// 安全阀
public final class BootGuard {

        // 单类异常计数，超过就熔断
    private static final int MAX_ERRORS = 5;

    private static final AtomicBoolean SPLASH_OFF = new AtomicBoolean(false);
    private static final AtomicBoolean VIEW_OFF = new AtomicBoolean(false);

    private static volatile int splashErrors;
    private static volatile int viewErrors;

    private BootGuard() {
    }

        // 开屏模块是否还活着
    public static boolean splashAlive() {
        return !SPLASH_OFF.get();
    }

    public static boolean viewAlive() {
        return !VIEW_OFF.get();
    }

        // 记录一次开屏异常，超阈值直接关掉开屏模块
    public static void splashError(Throwable t) {
        int n = ++splashErrors;
        if (n >= MAX_ERRORS && SPLASH_OFF.compareAndSet(false, true)) {
            Xp.log("!!! splash module disabled after " + n + " errors: " + t);
        }
    }

    public static void viewError(Throwable t) {
        int n = ++viewErrors;
        if (n >= MAX_ERRORS && VIEW_OFF.compareAndSet(false, true)) {
            Xp.log("!!! view module disabled after " + n + " errors: " + t);
        }
    }

    public static void reset() {
        splashErrors = 0;
        viewErrors = 0;
        SPLASH_OFF.set(false);
        VIEW_OFF.set(false);
    }
}
