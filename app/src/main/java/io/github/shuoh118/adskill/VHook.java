package io.github.shuoh118.adskill;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/*
 * 快手视图层。
 *
 * 快手广告 View 和普通视频卡片长得一样，靠类名认不出，
 * 只能靠它身上的广告标记判断。这里做三件事：
 *   1. addView 时检查 tag / contentDescription
 *   2. 命中就设 GONE，不移除（移除会让复用池错乱）
 *   3. 子树扫描，父容器带标记时整块隐藏
 */
public final class VHook {

    // 各种版本的广告标记写法
    private static final String[] MARK = {
            "广告", "推广", "赞助", "品牌广告", "了解更多",
            "AD", "ad", "Ad", "sponsor", "promoted", "brand"
    };

    private VHook() {
    }

    public static void install() {
        try {
            Method m = ViewGroup.class.getMethod(
                    "addView", View.class, int.class, ViewGroup.LayoutParams.class);
            Xp.hook("ksv.addView", m, SafeHook.view("ks.addView", new SafeHook.Body() {
                @Override
                public Object run(XposedInterface.Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    Object a0 = Xp.arg(chain, 0);
                    if (!(a0 instanceof View)) {
                        return result;
                    }
                    final View v = (View) a0;
                    v.post(new Runnable() {
                        @Override
                        public void run() {
                            markIfAd(v);
                        }
                    });
                    return result;
                }
            }));
        } catch (Throwable t) {
            Xp.debug("ksv addView fail :: " + t);
        }
    }

    private static void markIfAd(View v) {
        try {
            if (v == null) {
                return;
            }
            if (hasAdMark(v)) {
                v.setVisibility(View.GONE);
                Xp.log("ks ad view hidden: " + v.getClass().getSimpleName());
                return;
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                int n = g.getChildCount();
                for (int i = 0; i < n; i++) {
                    View c = g.getChildAt(i);
                    if (c != null && hasAdMark(c)) {
                        c.setVisibility(View.GONE);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // 快手给广告 View 打的标记，各种版本都有
    private static boolean hasAdMark(View v) {
        try {
            Object tag = v.getTag();
            if (tag instanceof String && isAdWord((String) tag)) {
                return true;
            }
            CharSequence desc = v.getContentDescription();
            if (desc != null && isAdWord(desc.toString())) {
                return true;
            }
            CharSequence hint = v.getTooltipText();
            if (hint != null && isAdWord(hint.toString())) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isAdWord(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        if (t.isEmpty() || t.length() > 24) {
            return false;
        }
        for (String k : MARK) {
            if (t.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
