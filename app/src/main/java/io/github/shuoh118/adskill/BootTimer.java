package io.github.shuoh118.adskill;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

/*
 * 启动页加速。
 *
 * === 1.0.0 闪退的教训 ===
 *
 * 之前判断"是不是开屏页"只看大图/滚动/文字数。
 * 网易云的主页也满足（有封面大图、不是滚动容器、文字少），
 * 于是主页被当成开屏页 finish 掉，栈里没有可显示的 Activity，直接退出。
 *
 * === 现在加了两道保险 ===
 *
 *   1. 主页永不处理：先解析出 launcher Activity，遇到它直接跳过
 *   2. 必须有"这是启动页"的正面证据：
 *        页面从创建到现在没出现过任何可点控件，且没有列表
 *      主页一开始就会有底部导航栏（可点），所以不会被误判
 *
 * 再加一道：只处理进程启动后 3 秒内创建的 Activity。
 */
public final class BootTimer {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // 首次检查延迟
    private static final long FIRST_DELAY = 80L;
    // 复检间隔
    private static final long TICK_MS = 100L;
    // 最多盯 2.5 秒
    private static final long MAX_MS = 2500L;
    // 进程启动后多久内创建的 Activity 才可能是开屏页
    private static final long BOOT_WINDOW = 3000L;

    private static final Map<Activity, Long> START = new WeakHashMap<>();
    private static final Map<Activity, Boolean> EVER_CLICKABLE = new WeakHashMap<>();

    private static volatile String launcherCls;
    private static volatile boolean launcherResolved;
    private static volatile long processStart;

    private BootTimer() {
    }

    public static void install() {
        processStart = SystemClock.uptimeMillis();
        try {
            Method m = Activity.class.getDeclaredMethod("onCreate", android.os.Bundle.class);
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            Xp.hook("boottimer.onCreate", m, SafeHook.splash("boottimer", new SafeHook.Body() {
                @Override
                public Object run(XposedInterface.Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    Object self = Xp.self(chain);
                    if (!(self instanceof Activity)) {
                        return result;
                    }
                    final Activity act = (Activity) self;

                    // 只在进程刚起来那几秒内工作
                    if (SystemClock.uptimeMillis() - processStart > BOOT_WINDOW) {
                        return result;
                    }
                    // 主页：永不处理，并且立刻关闭开屏窗口
                    if (act.getClass().getName().equals(launcher(act))) {
                        SplashWindow.close();
                        Xp.log("main activity reached, splash window closed");
                        return result;
                    }

                    synchronized (START) {
                        START.put(act, SystemClock.uptimeMillis());
                        EVER_CLICKABLE.put(act, Boolean.FALSE);
                    }
                    MAIN.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            check(act, 0);
                        }
                    }, FIRST_DELAY);
                    return result;
                }
            }));
        } catch (Throwable t) {
            Xp.debug("boottimer onCreate fail :: " + t);
        }
    }

    private static void check(final Activity act, final int round) {
        try {
            if (act.isFinishing() || act.isDestroyed()) {
                return;
            }
            Long start;
            synchronized (START) {
                start = START.get(act);
            }
            if (start == null) {
                return;
            }
            long cost = SystemClock.uptimeMillis() - start;
            if (cost > MAX_MS) {
                return;
            }

            View decor = act.getWindow() == null ? null : act.getWindow().peekDecorView();
            if (decor == null || !decor.isAttachedToWindow()) {
                retry(act, round);
                return;
            }

            // 记录这个页面是否出现过可点控件
            boolean clickable = hasClickable(decor);
            synchronized (START) {
                if (clickable) {
                    EVER_CLICKABLE.put(act, Boolean.TRUE);
                }
            }

            // 关键：只要出现过可点控件，就认为这是正常页面，永不处理
            Boolean ever;
            synchronized (START) {
                ever = EVER_CLICKABLE.get(act);
            }
            if (Boolean.TRUE.equals(ever)) {
                return;
            }

            if (looksLikeSplash(decor)) {
                Xp.log("boot skip " + cost + "ms " + simple(act.getClass().getName()));
                skip(act);
                return;
            }
            retry(act, round);
        } catch (Throwable t) {
            Xp.debug("boottimer check fail :: " + t);
        }
    }

    private static void retry(final Activity act, final int round) {
        if (round >= 25) {
            return;
        }
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                check(act, round + 1);
            }
        }, TICK_MS);
    }

    /*
     * 开屏页判定：必须同时满足
     *   A. 有大图（占屏 > 40%）
     *   B. 没有滚动容器
     *   C. 文字 <= 5 个
     *   D. 没有出现过可点控件（由调用方保证）
     */
    private static boolean looksLikeSplash(View decor) {
        try {
            int[] st = new int[4];
            int w = decor.getWidth();
            int h = decor.getHeight();
            long screen = (long) Math.max(w, 1) * Math.max(h, 1);
            walk(decor, 0, screen, st);

            return st[2] > 0 && st[3] == 0 && st[1] <= 5;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasClickable(View root) {
        int[] st = new int[2];
        walkClick(root, 0, st);
        return st[1] > 0;
    }

    private static void walkClick(View v, int depth, int[] st) {
        if (v == null || depth > 18 || st[0] > 200) {
            return;
        }
        try {
            st[0]++;
            if (v.isClickable()) {
                st[1]++;
                return;
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                int n = g.getChildCount();
                for (int i = 0; i < n; i++) {
                    walkClick(g.getChildAt(i), depth + 1, st);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void walk(View v, int depth, long screen, int[] st) {
        if (v == null || depth > 18 || st[0] > 150) {
            return;
        }
        try {
            if (v.getVisibility() != View.VISIBLE) {
                return;
            }
            st[0]++;
            String cls = v.getClass().getName();

            if (v instanceof TextView) {
                CharSequence cs = ((TextView) v).getText();
                if (cs != null && cs.length() > 0) {
                    st[1]++;
                }
            }
            if (v instanceof ImageView) {
                int iw = v.getWidth();
                int ih = v.getHeight();
                if (iw > 0 && ih > 0 && screen > 0
                        && (long) iw * ih * 100L >= screen * 40L) {
                    st[2]++;
                }
            }
            if (cls.contains("RecyclerView") || cls.contains("ScrollView")
                    || cls.contains("ViewPager") || cls.contains("ListView")
                    || cls.contains("ViewFlipper")) {
                st[3]++;
            }

            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                int n = g.getChildCount();
                for (int i = 0; i < n; i++) {
                    walk(g.getChildAt(i), depth + 1, screen, st);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // launcher Activity，主页绝不处理
    private static String launcher(Activity act) {
        if (launcherResolved) {
            return launcherCls;
        }
        launcherResolved = true;
        try {
            PackageManager pm = act.getPackageManager();
            Intent i = pm.getLaunchIntentForPackage(act.getPackageName());
            if (i != null && i.getComponent() != null) {
                launcherCls = i.getComponent().getClassName();
            }
        } catch (Throwable ignored) {
        }
        if (launcherCls != null) {
            Xp.log("launcher = " + launcherCls);
        }
        return launcherCls;
    }

    private static void skip(Activity act) {
        try {
            SplashWindow.close();
            act.finish();
            act.overridePendingTransition(0, 0);
        } catch (Throwable t) {
            Xp.debug("boottimer skip fail :: " + t);
        }
    }

    private static String simple(String fqcn) {
        int i = fqcn.lastIndexOf('.');
        return i < 0 ? fqcn : fqcn.substring(i + 1);
    }
}
