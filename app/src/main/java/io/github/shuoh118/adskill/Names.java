package io.github.shuoh118.adskill;

import java.util.ArrayList;
import java.util.List;

// 名字指纹
public final class Names {

        // 视图类后缀，用来区分"广告类"和"广告 View"
    private static final String[] VIEW_TAIL = {
            "view", "layout", "widget", "container", "banner", "card", "image", "frame",
            "item", "holder", "flipper", "pager", "dialog", "popup", "window", "stub", "flip"
    };

        // 广告 SDK 包前缀
    public static final String[] SDK_PREFIX = {
            "com.bytedance.sdk.openadsdk",
            "com.byted.pangle",
            "com.qq.e.",
            "com.tencent.gdt",
            "com.baidu.mobads",
            "com.kwad.",
            "com.ciba.",
            "com.mbridge.",
            "com.mintegral.",
            "com.unity3d.ads",
            "com.applovin.",
            "com.google.android.gms.ads",
            "com.sigmob.",
            "com.anythink."
    };

        // 广告开关类配置键必须带这些语义才强制关闭
    private static final String[] SWITCH_WORD = {
            "switch", "enable", "enabled", "show", "open", "display", "count", "times",
            "interval", "delay", "duration", "frequency", "config", "json", "list", "info",
            "flag", "state", "status", "time", "level", "limit", "expire", "rule", "strategy"
    };

        // 反向语义：ad_free / no_ad / remove_ad 表示"不要广告"，必须放行
    private static final String[] REVERSE_WORD = {
            "free", "noad", "no", "none", "remove", "without", "disable", "disabled", "off", "block"
    };

        // 开屏 / 启动页语义
    private static final String[] SPLASH_WORD = {
            "splash", "welcome", "launch", "launcher", "startup"
    };

    private Names() {
    }

    // ---------- 分词 ----------

    public static List<String> words(String raw) {
        List<String> out = new ArrayList<>(12);
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        StringBuilder cur = new StringBuilder(20);
        int n = raw.length();
        for (int i = 0; i < n; i++) {
            char c = raw.charAt(i);
            if (c == '.' || c == '_' || c == '$' || c == '-' || c == '/' || c == ' ') {
                flush(cur, out);
                continue;
            }
            if (c >= 'A' && c <= 'Z') {
                boolean prevLower = i > 0 && isLower(raw.charAt(i - 1));
                boolean prevUpper = i > 0 && isUpper(raw.charAt(i - 1));
                boolean nextLower = i + 1 < n && isLower(raw.charAt(i + 1));
                if (prevLower || (prevUpper && nextLower)) {
                    flush(cur, out);
                }
                cur.append((char) (c + 32));
            } else if (c >= '\u00C0') {
                flush(cur, out);
            } else {
                cur.append(c);
            }
        }
        flush(cur, out);
        return out;
    }

    private static boolean isLower(char c) {
        return c >= 'a' && c <= 'z';
    }

    private static boolean isUpper(char c) {
        return c >= 'A' && c <= 'Z';
    }

    private static void flush(StringBuilder cur, List<String> out) {
        if (cur.length() == 0) {
            return;
        }
        out.add(cur.toString());
        cur.setLength(0);
    }

    // ---------- 判定 ----------

    public static boolean hasAd(List<String> w) {
        for (int i = 0; i < w.size(); i++) {
            String s = w.get(i);
            if (s.equals("ad") || s.equals("ads")) {
                return true;
            }
            if (s.startsWith("advert")) {
                return true;
            }
        }
        return false;
    }

    public static boolean startsWithAny(List<String> w, String[] prefixes) {
        if (w.isEmpty()) {
            return false;
        }
        String first = w.get(0);
        for (String p : prefixes) {
            if (first.equals(p)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsAny(List<String> w, String[] set) {
        for (int i = 0; i < w.size(); i++) {
            for (String s : set) {
                if (w.get(i).equals(s)) {
                    return true;
                }
            }
        }
        return false;
    }

        // 广告类：整词含 ad，或在 .ad
    public static boolean isAdClass(String fqcn) {
        if (fqcn == null || fqcn.isEmpty()) {
            return false;
        }
        if (isSdkClass(fqcn)) {
            return true;
        }
        String lower = fqcn.toLowerCase();
        if (!lower.contains("ad")) {
            return false;
        }
        if (lower.contains(".ad.") || lower.contains(".ads.") || lower.contains(".advert")) {
            return true;
        }
        return hasAd(words(fqcn));
    }

        // 广告自定义 View：整词含 ad，且末词是视图类后缀
    public static boolean isAdView(String name) {
        List<String> w = words(name);
        if (w.isEmpty() || !hasAd(w)) {
            return false;
        }
        String last = w.get(w.size() - 1);
        for (String t : VIEW_TAIL) {
            if (last.equals(t)) {
                return true;
            }
        }
        return false;
    }

        // 广告布局资源名，规则同上
    public static boolean isAdLayout(String resName) {
        return isAdView(resName);
    }

        // 广告 Activity：名字里带 ad，或者是开屏页
    public static boolean isAdActivity(String cls) {
        if (cls == null || cls.isEmpty()) {
            return false;
        }
        List<String> w = words(cls);
        if (w.isEmpty()) {
            return false;
        }
        return hasAd(w) || containsAny(w, SPLASH_WORD);
    }

        // 开屏 / 启动页
    public static boolean isSplash(String cls) {
        if (cls == null || cls.isEmpty()) {
            return false;
        }
        return containsAny(words(cls), SPLASH_WORD);
    }

        // 广告开关键：含 ad 词，带开关/配置语义，且不是反向语义
    public static boolean isAdSwitchKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        List<String> w = words(key);
        if (w.isEmpty() || !hasAd(w)) {
            return false;
        }
        // ad_free / no_ad / remove_ad 这类是"关广告"的键，拦了反而会重开广告
        if (containsAny(w, REVERSE_WORD)) {
            return false;
        }
        return containsAny(w, SWITCH_WORD) || w.contains("ad");
    }

    public static boolean isSdkClass(String fqcn) {
        if (fqcn == null) {
            return false;
        }
        for (String p : SDK_PREFIX) {
            if (fqcn.startsWith(p)) {
                return true;
            }
        }
        return false;
    }
}
