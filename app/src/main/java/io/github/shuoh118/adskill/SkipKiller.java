package io.github.shuoh118.adskill;

import android.app.Activity;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

// 跳过按钮清除
public final class SkipKiller {

    // 带这些字的才算跳过按钮
    private static final String[] SKIP = {
            "跳过", "skip", "Skip", "SKIP", "关闭广告", "close"
    };

    // 独立窗口：进程启动后 8 秒内有效
    private static final long WINDOW_MS = 8000L;
    private static volatile long startAt;

    private SkipKiller() {
    }

    public static void open() {
        if (startAt == 0) {
            startAt = SystemClock.uptimeMillis();
        }
    }

    private static boolean inWindow() {
        long s = startAt;
        return s != 0 && SystemClock.uptimeMillis() - s < WINDOW_MS;
    }

    /*
     * === 为什么不能只依赖 SplashWindow ===
     *
     * SplashWindow 在"主页出现"时就关闭。
     * 但网易云的开屏页可能就叫 LoadingActivity，
     * 被 BootTimer 当成 launcher 直接触发 close()，
     * 之后 SkipKiller 全部返回 0，跳过按钮就留下来了。
     *
     * 所以这里用独立窗口：进程启动 8 秒内都有效。
     * 8 秒后自动失效，不会影响正常页面的"跳过前奏"这类按钮。
     */
    public static int kill(View root) {
        if (root == null || !inWindow()) {
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
        if (root == null || depth > 30 || out.size() >= 4) {
            return;
        }
        try {
            if (root instanceof TextView) {
                CharSequence cs = ((TextView) root).getText();
                if (cs != null && isSkipText(cs.toString())) {
                    out.add(root);
                    return;
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

    // 必须带跳过语义，纯数字一律不算
    private static boolean isSkipText(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        if (t.isEmpty() || t.length() > 20) {
            return false;
        }
        // "跳过前奏""跳过片头"这类是正常功能
        if (t.contains("跳过前奏") || t.contains("跳过片头")
                || t.contains("跳过间奏") || t.contains("跳过尾奏")) {
            return false;
        }
        for (String k : SKIP) {
            if (t.contains(k)) {
                return true;
            }
        }
        return false;
    }

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

    public static void killIn(Activity act) {
        try {
            if (act == null || act.isFinishing() || act.isDestroyed()) {
                return;
            }
            if (!inWindow()) {
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
