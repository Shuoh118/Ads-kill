package io.github.shuoh118.adskill;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

// 广告文案判定
public final class SplashText {

        // 命中任意一条即判定为广告
    private static final String[] AD = {
            "跳过广告", "跳转详情", "跳转到详情", "跳转第三方", "跳转至第三方",
            "第三方应用", "了解详情", "点击查看详情", "查看详情", "点击跳转"
    };

        // 这些是正常功能，不能被当成广告
    private static final String[] SAFE = {
            "跳过前奏", "跳过片头", "跳过间奏", "跳过尾奏",
            "广告合作", "广告位招租"
    };

    private SplashText() {
    }

        // 注意：这里不把单独的"广告"两字当特征
    public static boolean isAd(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        if (t.isEmpty() || t.length() > 40) {
            return false;
        }
        for (String safe : SAFE) {
            if (t.contains(safe)) {
                return false;
            }
        }
        for (String k : AD) {
            if (t.contains(k)) {
                return true;
            }
        }
        return false;
    }

        // 深度优先找第一个广告文案
    public static String find(View root, int depth) {
        if (root == null || depth > 30) {
            return null;
        }
        if (root instanceof TextView) {
            CharSequence cs = ((TextView) root).getText();
            if (cs != null && isAd(cs.toString())) {
                return cs.toString();
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            int n = g.getChildCount();
            for (int i = 0; i < n; i++) {
                String h = find(g.getChildAt(i), depth + 1);
                if (h != null) {
                    return h;
                }
            }
        }
        return null;
    }

        // 找文字完全等于 text 的那个 View
    public static View node(View root, String text, int depth) {
        if (root == null || depth > 30 || text == null) {
            return null;
        }
        if (root instanceof TextView) {
            CharSequence cs = ((TextView) root).getText();
            if (cs != null && cs.toString().equals(text)) {
                return root;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            int n = g.getChildCount();
            for (int i = 0; i < n; i++) {
                View h = node(g.getChildAt(i), text, depth + 1);
                if (h != null) {
                    return h;
                }
            }
        }
        return null;
    }
}
