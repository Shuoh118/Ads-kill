package io.github.shuoh118.adskill;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

// 跳过按钮 + 倒计时清除
public final class SkipKiller {

    private SkipKiller() {
    }

        // 在给定根节点里清除所有跳过/倒计时控件，返回清掉了几个
    public static int kill(View root) {
        if (root == null) {
            return 0;
        }
        List<View> hits = new ArrayList<>(4);
        collect(root, hits, 0);
        int n = 0;
        for (View v : hits) {
            if (disable(v)) {
                n++;
            }
        }
        return n;
    }

    private static void collect(View root, List<View> out, int depth) {
        if (root == null || depth > 30) {
            return;
        }
        try {
            if (root instanceof TextView) {
                TextView tv = (TextView) root;
                CharSequence cs = tv.getText();
                if (cs != null && isSkipText(cs.toString())) {
                    out.add(tv);
                }
            }
            if (root instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) root;
                int n = g.getChildCount();
                for (int i = 0; i < n; i++) {
                    collect(g.getChildAt(i), out, depth + 1);
                }
            }
        } catch (Throwable ignored) {
        }
    }

        // 只认跳过语义，不能误伤正常按钮
    private static boolean isSkipText(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        if (t.isEmpty() || t.length() > 20) {
            return false;
        }
        if (t.contains("跳过")) {
            return true;
        }
        // 纯数字倒计时：1-2 位数字，可能是 "4" / "04" / "4s"
        String digits = t.replaceAll("[^0-9]", "");
        if (!digits.isEmpty() && digits.length() <= 2 && t.length() <= 4) {
            return true;
        }
        return false;
    }

        // 隐藏 + 禁用 + 摘掉点击监听，让它彻底失效
    private static boolean disable(View v) {
        try {
            v.setVisibility(View.GONE);
            v.setEnabled(false);
            v.setClickable(false);
            v.setOnClickListener(null);
            v.setFocusable(false);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

        // 给 SplashKill / SplashWatch 用：清当前页面的跳过控件
    public static void killIn(Activity act) {
        try {
            if (act == null || act.isFinishing() || act.isDestroyed()) {
                return;
            }
            View decor = act.getWindow() == null ? null : act.getWindow().peekDecorView();
            if (decor == null) {
                return;
            }
            int n = kill(decor);
            if (n > 0) {
                Xp.log("skip widgets removed: " + n);
            }
        } catch (Throwable t) {
            Xp.debug("killIn fail :: " + t);
        }
    }
}
