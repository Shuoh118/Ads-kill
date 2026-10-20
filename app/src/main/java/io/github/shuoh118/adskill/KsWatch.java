package io.github.shuoh118.adskill;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/*
 * 快手信息流广告扫描器。
 *
 * === 为什么这个才是"全版本通杀"的关键 ===
 *
 * 快手每个版本都改类名、改字段名，靠 KsHook 那种按方法名匹配必然漏。
 * 但无论哪个版本，信息流里的广告都会带一个"广告"角标 ——
 * 那是给用户看的，不可能删掉。
 *
 * 所以这里反过来：不认类名，只认屏幕上的"广告"两个字。
 * 找到那个角标，把它所在的卡片整块隐藏。
 *
 * 触发方式：
 *   1. RecyclerView 滚动结束 / 数据更新时扫一次（主要）
 *   2. 兜底定时扫，1 秒一次，连扫 60 次（1 分钟）后停
 *
 * 开销控制：
 *   - 只查 TextView，不碰别的类型
 *   - 文本长度 > 6 的字直接跳过
 *   - 整棵树最多走 200 个节点
 */
public final class KsWatch {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // 广告角标的各种写法
    private static final String[] BADGE = {
            "广告", "推广", "赞助", "品牌广告"
    };

    // 绝不能误伤的
    private static final String[] SAFE = {
            "推广我的作品", "作品推广", "推广中心", "推广数据", "广告设置",
            "广告偏好", "个性化广告", "关闭广告", "广告合作", "广告反馈"
    };

    private static final int MAX_TICKS = 60;
    private static final long TICK_MS = 1000L;

    private KsWatch() {
    }

    public static void install() {
        hookOnCreate();
        hookScroll();
    }

    // Activity 起来后开始定时扫
    private static void hookOnCreate() {
        try {
            Method m = Activity.class.getDeclaredMethod("onCreate", android.os.Bundle.class);
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            Xp.hook("kswatch.onCreate", m, SafeHook.splash("kswatch", new SafeHook.Body() {
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
            Xp.debug("kswatch onCreate fail :: " + t);
        }
    }

    // 列表滚动时补扫，覆盖"滑出新广告"的场景
    private static void hookScroll() {
        String[] names = {"setAdapter", "onScrollStateChanged", "onScrolled"};
        try {
            Class<?> rv = Class.forName("androidx.recyclerview.widget.RecyclerView");
            Method[] all = rv.getDeclaredMethods();
            for (String name : names) {
                for (Method m : all) {
                    if (!m.getName().equals(name)) {
                        continue;
                    }
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {
                    }
                    Xp.hook("kswatch.rv." + name, m, SafeHook.view("kswatch.rv", new SafeHook.Body() {
                        @Override
                        public Object run(XposedInterface.Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            Object self = Xp.self(chain);
                            if (self instanceof View) {
                                final View root = ((View) self).getRootView();
                                MAIN.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        sweep(root);
                                    }
                                }, 120L);
                            }
                            return result;
                        }
                    }));
                }
            }
        } catch (Throwable t) {
            Xp.debug("kswatch rv fail :: " + t);
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
                    View decor = act.getWindow() == null ? null : act.getWindow().peekDecorView();
                    if (decor != null) {
                        sweep(decor);
                    }
                } catch (Throwable ignored) {
                }
                tick(act, n + 1);
            }
        }, TICK_MS);
    }

    // 扫一遍，找到角标就藏掉它的卡片
    private static void sweep(View root) {
        if (root == null) {
            return;
        }
        int[] budget = {200};
        walk(root, 0, budget);
    }

    private static void walk(View v, int depth, int[] budget) {
        if (v == null || depth > 24 || budget[0] <= 0) {
            return;
        }
        try {
            if (v.getVisibility() != View.VISIBLE) {
                return;
            }
            budget[0]--;

            if (v instanceof TextView) {
                CharSequence cs = ((TextView) v).getText();
                if (cs != null && isBadge(cs.toString())) {
                    hideCard(v);
                    return;
                }
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                int n = g.getChildCount();
                for (int i = 0; i < n && budget[0] > 0; i++) {
                    walk(g.getChildAt(i), depth + 1, budget);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /*
     * 从角标往上找广告卡片。
     * 找"孩子不多"的那层 —— 太多孩子的就是整个列表容器，不能动。
     */
    private static void hideCard(View badge) {
        try {
            View best = null;
            ViewParent p = badge.getParent();
            int depth = 0;
            while (p instanceof ViewGroup && depth < 8) {
                ViewGroup g = (ViewGroup) p;
                String n = g.getClass().getName();
                // 碰到列表本体就停
                if (n.contains("RecyclerView") || n.contains("ListView")) {
                    break;
                }
                if (g.getChildCount() <= 8) {
                    best = g;
                } else {
                    break;
                }
                p = g.getParent();
                depth++;
            }
            if (best != null && best.getVisibility() == View.VISIBLE) {
                best.setVisibility(View.GONE);
                Xp.log("ks feed ad hidden: " + best.getClass().getSimpleName());
            } else if (badge.getVisibility() == View.VISIBLE) {
                badge.setVisibility(View.GONE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isBadge(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        if (t.isEmpty() || t.length() > 6) {
            return false;
        }
        for (String safe : SAFE) {
            if (t.contains(safe)) {
                return false;
            }
        }
        for (String k : BADGE) {
            if (t.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
