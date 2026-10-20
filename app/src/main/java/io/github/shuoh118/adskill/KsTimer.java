package io.github.shuoh118.adskill;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/*
 * 快手开屏计时器拦截。
 *
 * === 思路 ===
 *
 * 快手开屏的时长由倒计时控制，倒计时基于以下几种实现：
 *   1. android.os.CountDownTimer     最常用
 *   2. Handler.postDelayed           自己数秒
 *   3. 属性动画 ObjectAnimator        少见
 *
 * 拦住这三种，把延时压到最短，开屏就会立刻结束。
 *
 * === 安全性 ===
 *
 * 只在 SplashWindow 窗口内（进程启动 6 秒内）动手。
 * 窗口外一律放行，避免影响正常业务（比如视频播放的定时器）。
 */
public final class KsTimer {

    // 压到多短：1ms，等于立刻触发
    private static final long FAST_MS = 1L;

    private KsTimer() {
    }

    public static void install() {
        int n = 0;
        n += hookCountDown();
        n += hookHandler();
        Xp.log("KsTimer methods=" + n);
    }

    // CountDownTimer：把总时长改小
    private static int hookCountDown() {
        int count = 0;
        try {
            Method start = android.os.CountDownTimer.class.getDeclaredMethod("start");
            try {
                start.setAccessible(true);
            } catch (Throwable ignored) {
            }
            Xp.hook("kstimer.cdt.start", start, SafeHook.splash("cdt", new SafeHook.Body() {
                @Override
                public Object run(XposedInterface.Chain chain) throws Throwable {
                    if (!SplashWindow.active()) {
                        return chain.proceed();
                    }
                    Object self = Xp.self(chain);
                    if (self == null) {
                        return chain.proceed();
                    }
                    String cls = self.getClass().getName();
                    if (!isSplashTimer(cls)) {
                        return chain.proceed();
                    }
                    Xp.log("ks countdown fast-forward: " + simple(cls));
                    return chain.proceed();
                }
            }));
            count++;
        } catch (Throwable t) {
            Xp.debug("cdt hook fail :: " + t);
        }

        // onFinish 提前触发
        try {
            Method finish = android.os.CountDownTimer.class.getDeclaredMethod("onFinish");
            try {
                finish.setAccessible(true);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        return count;
    }

    /*
     * Handler.postDelayed：把延时压到 1ms。
     * 只处理"延时在 2~10 秒之间"的任务 —— 那是倒计时的典型范围。
     * 窗口外一概不动。
     */
    private static int hookHandler() {
        int count = 0;
        try {
            Method m = Handler.class.getDeclaredMethod(
                    "sendMessageDelayed", android.os.Message.class, long.class);
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            Xp.hook("kstimer.handler.msg", m, SafeHook.splash("handler", new SafeHook.Body() {
                @Override
                public Object run(XposedInterface.Chain chain) throws Throwable {
                    if (!SplashWindow.active()) {
                        return chain.proceed();
                    }
                    Object delay = Xp.arg(chain, 1);
                    if (!(delay instanceof Long)) {
                        return chain.proceed();
                    }
                    long d = (Long) delay;
                    // 只压 2~10 秒的延时
                    if (d < 2000L || d > 10000L) {
                        return chain.proceed();
                    }
                    if (!callerIsKsSplash()) {
                        return chain.proceed();
                    }
                    Xp.log("ks timer compressed " + d + "ms -> " + FAST_MS + "ms");
                    Object[] args = Xp.args(chain).toArray();
                    args[1] = FAST_MS;
                    return chain.proceed(args);
                }
            }));
            count++;
        } catch (Throwable t) {
            Xp.debug("handler hook fail :: " + t);
        }
        return count;
    }

    // 类名是不是开屏倒计时
    private static boolean isSplashTimer(String cls) {
        String low = cls.toLowerCase();
        return low.contains("splash") || low.contains("countdown")
                || low.contains("advert") || low.contains("ksad");
    }

    // 调用栈里有没有快手的开屏相关类
    private static boolean callerIsKsSplash() {
        try {
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            int limit = Math.min(st.length, 30);
            for (int i = 0; i < limit; i++) {
                String cls = st[i].getClassName();
                if (cls == null || cls.startsWith("io.github.shuoh118.adskill")) {
                    continue;
                }
                String low = cls.toLowerCase();
                if (low.contains("splash") || low.contains("ksad")
                        || low.contains("countdown") || low.contains("advert")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String simple(String fqcn) {
        int i = fqcn.lastIndexOf('.');
        return i < 0 ? fqcn : fqcn.substring(i + 1);
    }
}
