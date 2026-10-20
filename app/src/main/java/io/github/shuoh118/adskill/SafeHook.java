package io.github.shuoh118.adskill;

import android.view.View;

import io.github.libxposed.api.XposedInterface;

// 带兜底的 Hooker 包装
public final class SafeHook {

    public interface Body {
        Object run(XposedInterface.Chain chain) throws Throwable;
    }

    private SafeHook() {
    }

        // 视图类 hook 专用：出错就当作没 hook
    public static XposedInterface.Hooker view(final String tag, final Body body) {
        return new XposedInterface.Hooker() {
            @Override
            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                if (!BootGuard.viewAlive()) {
                    return chain.proceed();
                }
                try {
                    return body.run(chain);
                } catch (Throwable t) {
                    BootGuard.viewError(t);
                    Xp.debug("view hook error " + tag + " :: " + t);
                    // body 可能已经 proceed 过了，这时再 proceed 会重复执行。
                    // 所以约定：body 抛异常时，它自己保证还没 proceed。
                    return chain.proceed();
                }
            }
        };
    }

        // 开屏类 hook：出错不影响界面
    public static XposedInterface.Hooker splash(final String tag, final Body body) {
        return new XposedInterface.Hooker() {
            @Override
            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                if (!BootGuard.splashAlive()) {
                    return chain.proceed();
                }
                try {
                    return body.run(chain);
                } catch (Throwable t) {
                    BootGuard.splashError(t);
                    Xp.debug("splash hook error " + tag + " :: " + t);
                    return chain.proceed();
                }
            }
        };
    }

        // 后台任务包装：整个过程都不允许影响主线程
    public static void run(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            Xp.debug("task error :: " + t);
        }
    }

        // 视图操作包装：removeView 之类，失败就算了
    public static void onView(View v, Runnable r) {
        if (v == null) {
            return;
        }
        try {
            v.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        r.run();
                    } catch (Throwable t) {
                        Xp.debug("view task error :: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            Xp.debug("post error :: " + t);
        }
    }
}
