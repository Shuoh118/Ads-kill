package io.github.shuoh118.adskill;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

// 开屏看门狗
public final class SplashWatch {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final int MAX_TICKS = 20;
    private static final long TICK_MS = 150L;

    private SplashWatch() {
    }

    public static void install() {
        try {
            Method m = Activity.class.getDeclaredMethod("onCreate", android.os.Bundle.class);
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            Xp.hook("splashwatch.onCreate", m, SafeHook.splash("watch.onCreate", new SafeHook.Body() {
                @Override
                public Object run(XposedInterface.Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    Object self = Xp.self(chain);
                    if (!(self instanceof Activity)) {
                        return result;
                    }
                    tick((Activity) self, 0);
                    return result;
                }
            }));
        } catch (Throwable t) {
            Xp.debug("SplashWatch hook fail :: " + t);
        }
    }

    private static void tick(final Activity act, final int n) {
        if (n >= MAX_TICKS) {
            return;
        }
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (act.isFinishing() || act.isDestroyed()) {
                        return;
                    }
                    if (hit(act)) {
                        return;
                    }
                } catch (Throwable t) {
                    Xp.debug("watch tick fail :: " + t);
                }
                tick(act, n + 1);
            }
        }, TICK_MS);
    }

    private static boolean hit(Activity act) {
        View decor = act.getWindow() == null ? null : act.getWindow().peekDecorView();
        if (decor == null) {
            return false;
        }
        // 跳过按钮清除有独立窗口，不受 SplashWindow 限制
        SkipKiller.kill(decor);

        // 广告识别只在开屏窗口内
        if (!SplashWindow.active()) {
            return true;
        }
        if (!decor.isAttachedToWindow()) {
            return false;
        }

        // A. 文案
        String text = SplashText.find(decor, 0);
        if (text != null) {
            Xp.log("watch: ad text in " + simple(act.getClass().getName()) + " = " + text);
            SplashKill.sweepPublic(decor, text, "watch");
            return true;
        }

        // B. 类名像广告且占屏够大的 View
        View big = bigAdView(decor, decor.getWidth(), decor.getHeight(), 0);
        if (big != null) {
            Xp.log("watch: big ad view " + simple(big.getClass().getName()));
            SplashKill.killViewPublic(big, "watch-class");
            return true;
        }

        return false;
    }

    private static View bigAdView(View root, int dw, int dh, int depth) {
        if (root == null || depth > 28) {
            return null;
        }
        try {
            long screen = (long) Math.max(dw, 1) * Math.max(dh, 1);
            if (root.getVisibility() == View.VISIBLE && screen > 0
                    && Names.isAdView(root.getClass().getName())) {
                android.graphics.Rect r = new android.graphics.Rect();
                root.getGlobalVisibleRect(r);
                long area = (long) r.width() * r.height();
                if (area * 100L >= screen * 40L) {
                    return root;
                }
            }
            if (root instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) root;
                int n = g.getChildCount();
                for (int i = 0; i < n; i++) {
                    View h = bigAdView(g.getChildAt(i), dw, dh, depth + 1);
                    if (h != null) {
                        return h;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String simple(String fqcn) {
        int i = fqcn.lastIndexOf('.');
        return i < 0 ? fqcn : fqcn.substring(i + 1);
    }
}
