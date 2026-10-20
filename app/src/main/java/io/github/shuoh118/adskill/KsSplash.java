package io.github.shuoh118.adskill;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/*
 * 快手开屏广告。
 *
 * === 和网易云的区别 ===
 *
 * 网易云：开屏是独立的 Activity，finish 掉就行。
 * 快手  ：开屏就在 launcher 里，是盖在主界面上的一层 View。
 *         不能 finish 页面，只能把这一层摘掉。
 *
 * === 怎么认这一层 ===
 *
 * launcher 里，广告层有两个特征：
 *   1. 全屏 ImageView（logo 或广告图，占屏 > 50%）
 *   2. 有倒计时数字（1~2 位纯数字的 TextView）
 *
 * 同时满足就判定为开屏层，把它的父容器 GONE 掉。
 * 主页没有这种组合（主页有底部导航栏、有列表）。
 *
 * === 时间限制 ===
 *
 * 只在 SplashWindow 窗口内（进程启动 6 秒内）工作，
 * 主页一出现窗口就关闭，之后完全停手。
 */
public final class KsSplash {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // 检查间隔
    private static final long TICK_MS = 100L;
    // 最多检查 40 次
    private static final int MAX_TICKS = 40;

    private KsSplash() {
    }

    public static void install() {
        try {
            Method m = Activity.class.getDeclaredMethod("onResume");
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            Xp.hook("kssplash.onResume", m, SafeHook.splash("kssplash", new SafeHook.Body() {
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
            Xp.debug("kssplash hook fail :: " + t);
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
                    if (!SplashWindow.active()) {
                        return;
                    }
                    if (act.isFinishing() || act.isDestroyed()) {
                        return;
                    }
                    View decor = act.getWindow() == null ? null : act.getWindow().peekDecorView();
                    if (decor != null && decor.isAttachedToWindow()) {
                        if (strip(decor)) {
                            Xp.log("ks splash stripped at " + SplashWindow.age() + "ms");
                            SplashWindow.close();
                            return;
                        }
                    }
                } catch (Throwable ignored) {
                }
                tick(act, n + 1);
            }
        }, TICK_MS);
    }

    /*
     * 找开屏层并隐藏。返回 true 表示处理过了。
     */
    private static boolean strip(View decor) {
        try {
            if (!(decor instanceof ViewGroup)) {
                return false;
            }
            int dw = decor.getWidth();
            int dh = decor.getHeight();
            long screen = (long) Math.max(dw, 1) * Math.max(dh, 1);

            // 先找倒计时（1~2 位纯数字）
            View countdown = findCountdown((ViewGroup) decor, 0);
            if (countdown == null) {
                return false;
            }

            // 再找全屏大图
            View bigImage = findBigImage((ViewGroup) decor, screen, 0);
            if (bigImage == null) {
                return false;
            }

            // 两个都找到，说明是开屏层。往上找共同容器
            View layer = commonParent(countdown, bigImage);
            if (layer != null && layer.getParent() instanceof ViewGroup
                    && layer.getParent() != decor) {
                ((ViewGroup) layer.getParent()).removeView(layer);
                return true;
            }
            // 找不到共同容器就单独隐藏
            bigImage.setVisibility(View.GONE);
            countdown.setVisibility(View.GONE);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // 倒计时：纯数字，1~2 位
    private static View findCountdown(ViewGroup g, int depth) {
        if (depth > 16) {
            return null;
        }
        try {
            int n = g.getChildCount();
            for (int i = 0; i < n; i++) {
                View c = g.getChildAt(i);
                if (c == null || c.getVisibility() != View.VISIBLE) {
                    continue;
                }
                if (c instanceof TextView) {
                    CharSequence cs = ((TextView) c).getText();
                    if (cs != null && isCountdown(cs.toString())) {
                        return c;
                    }
                }
                if (c instanceof ViewGroup) {
                    View h = findCountdown((ViewGroup) c, depth + 1);
                    if (h != null) {
                        return h;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isCountdown(String s) {
        String t = s.trim();
        if (t.isEmpty() || t.length() > 2) {
            return false;
        }
        for (int i = 0; i < t.length(); i++) {
            if (!Character.isDigit(t.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // 全屏大图
    private static View findBigImage(ViewGroup g, long screen, int depth) {
        if (depth > 16) {
            return null;
        }
        try {
            int n = g.getChildCount();
            for (int i = 0; i < n; i++) {
                View c = g.getChildAt(i);
                if (c == null || c.getVisibility() != View.VISIBLE) {
                    continue;
                }
                if (c instanceof ImageView) {
                    int w = c.getWidth();
                    int h = c.getHeight();
                    if (w > 0 && h > 0 && screen > 0
                            && (long) w * h * 100L >= screen * 50L) {
                        return c;
                    }
                }
                if (c instanceof ViewGroup) {
                    View h = findBigImage((ViewGroup) c, screen, depth + 1);
                    if (h != null) {
                        return h;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // 找两个节点的共同父容器
    private static View commonParent(View a, View b) {
        try {
            ViewGroup pa = (ViewGroup) a.getParent();
            int guard = 0;
            while (pa != null && guard++ < 10) {
                if (isAncestor(pa, b)) {
                    return pa;
                }
                Object next = pa.getParent();
                pa = next instanceof ViewGroup ? (ViewGroup) next : null;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isAncestor(View maybe, View child) {
        try {
            Object p = child.getParent();
            int guard = 0;
            while (p != null && guard++ < 10) {
                if (p == maybe) {
                    return true;
                }
                p = ((View) p).getParent();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
