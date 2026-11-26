package io.github.shuoh118.adskill;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedInterface;

// 广告类方法级拦截
public final class AdHook {

        // 这些动词开头的 void 方法才跳过：加载、展示、上报
    private static final String[] ACTION = {
            "load", "preload", "reload", "request", "fetch", "query",
            "show", "display", "present", "render", "play", "startad",
            "report", "track", "click", "expose", "monitor", "upload",
            "send", "submit", "prepare", "download"
    };

        // 这些动词开头的布尔方法才改成 false
    private static final String[] BOOL = {
            "isad", "isready", "isloaded", "isvalid", "isshow", "isdisplay",
            "canshow", "shouldshow", "isenable", "isavailable", "hasad", "needshow"
    };

        // 返回这些类型的对象方法，一律不碰
    private static final String[] NEVER = {
            "android.view.View", "android.view.ViewGroup", "android.content.Context",
            "android.graphics.drawable.Drawable", "android.view.ViewParent",
            "android.content.res.Resources", "android.os.IBinder"
    };

    private static final Set<String> EXCLUDE = new HashSet<>(Arrays.asList(
            "onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy",
            "onAttachedToWindow", "onDetachedFromWindow", "onMeasure", "onLayout",
            "onDraw", "onTouchEvent", "onInterceptTouchEvent", "onSaveInstanceState",
            "equals", "hashCode", "toString", "clone", "finalize",
            "getLayoutParams", "setLayoutParams", "getParent", "getRootView",
            "getVisibility", "setVisibility", "getMeasuredWidth", "getMeasuredHeight",
            "getWidth", "getHeight", "requestLayout", "invalidate", "post", "postDelayed",
            "getContext", "findViewById", "getResources"
    ));

    /*
     * 类名黑名单：这些类不是"广告播放入口"，而是广告模块自己的基础设施。
     *
     * issue #1 的崩溃就是 AdCookieStore 的 <clinit> 被误伤：
     * initMemoryCookieAndSyncLoadFromFile 含 load 被 hook 成空实现，
     * cookie 变 null，decodeCookie 直接 NPE。
     *
     * 原则：存储 / 配置 / 账号 / 初始化 这几类，宁可少杀广告，也不能拦。
     */
    private static final String[] PROTECT = {
            "cookie", "store", "storage", "cache", "mmkv", "prefs", "preference",
            "setting", "config", "account", "login", "session", "token", "auth",
            "database", "provider", "json", "parse", "codec", "serial",
            "init", "bootstrap", "preload", "boot"
    };

    private static final AtomicInteger HOOKED = new AtomicInteger();

    private AdHook() {
    }

    public static void install(ClassLoader cl) {
        long t0 = System.currentTimeMillis();
        List<String> names = ClassScan.allClassNames(cl);
        int classes = 0;
        int loaded = 0;
        for (String name : names) {
            if (name == null || name.isEmpty() || !Names.isAdClass(name)) {
                continue;
            }
            if (isProtected(name)) {
                continue;
            }
            if (loaded >= 3000) {
                break;
            }
            loaded++;
            Class<?> target;
            try {
                target = Class.forName(name, false, cl);
            } catch (Throwable t) {
                continue;
            }
            if (hookClass(target)) {
                classes++;
            }
        }
        Xp.log("AdHook classes=" + classes + " methods=" + HOOKED.get()
                + " scanned=" + names.size()
                + " in " + (System.currentTimeMillis() - t0) + "ms");
    }

    // 类名里带基础设施语义的一律不碰
    private static boolean isProtected(String fqcn) {
        String lower = fqcn.toLowerCase(java.util.Locale.ROOT);
        for (String p : PROTECT) {
            if (lower.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hookClass(Class<?> owner) {
        // View 子类整体跳过：它们的任何方法都可能被系统布局调用
        if (android.view.View.class.isAssignableFrom(owner)) {
            Xp.debug("skip view-ish ad class: " + owner.getName());
            return false;
        }
        Method[] methods;
        try {
            methods = owner.getDeclaredMethods();
        } catch (Throwable t) {
            return false;
        }
        boolean any = false;
        for (Method m : methods) {
            if (!usable(m, owner)) {
                continue;
            }
            try {
                if (hookMethod(m)) {
                    any = true;
                }
            } catch (Throwable t) {
                Xp.debug("method fail " + m + " :: " + t);
            }
        }
        return any;
    }

    private static boolean usable(Method m, Class<?> owner) {
        if (m == null || m.isSynthetic() || m.isBridge()) {
            return false;
        }
        if (Modifier.isAbstract(m.getModifiers())) {
            return false;
        }
        if (m.getDeclaringClass() != owner) {
            return false;
        }
        if (EXCLUDE.contains(m.getName())) {
            return false;
        }
        if (m.getName().startsWith("<")) {
            return false;
        }
        // 参数里有 View / Context 的，可能是系统回调，不碰
        for (Class<?> p : m.getParameterTypes()) {
            if (android.view.View.class.isAssignableFrom(p)
                    || android.content.Context.class.isAssignableFrom(p)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hookMethod(Method m) {
        List<String> w = Names.words(m.getName());
        if (w.isEmpty()) {
            return false;
        }
        String flat = flatten(w);
        Class<?> ret = m.getReturnType();
        String id = "ad." + m.getDeclaringClass().getName() + "#" + m.getName()
                + "#" + m.getParameterCount();

        // 1. 布尔：只改明确的广告状态判断
        if (ret == boolean.class) {
            if (!startsAny(flat, BOOL)) {
                return false;
            }
            Xp.hook(id, m, new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return Boolean.FALSE;
                }
            });
            HOOKED.incrementAndGet();
            return true;
        }

        // 2. void：只跳过明确是加载/展示/上报的动作
        if (ret == void.class) {
            if (!startsAnyWord(w, ACTION)) {
                return false;
            }
            Xp.hook(id, m, new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) {
                    return null;
                }
            });
            HOOKED.incrementAndGet();
            return true;
        }

        // 3. 其他返回类型：一律不碰。
        //    这一条是 1.0.3 黑屏的修复点 —— 之前会把对象改成 null，
        //    导致系统布局拿到 null 直接画不出来。
        return false;
    }

    private static String flatten(List<String> w) {
        StringBuilder sb = new StringBuilder(24);
        for (String s : w) {
            sb.append(s);
        }
        return sb.toString();
    }

    /*
     * 只看第一个词，且只允许前缀匹配。
     *
     * 旧版用 flat.contains(p)，于是 initMemoryCookieAndSyncLoadFromFile
     * 因为中间有 load 就被当成"加载广告"拦掉 —— issue #1 的根因。
     */
    private static boolean startsAnyWord(List<String> words, String[] prefixes) {
        if (words.isEmpty()) {
            return false;
        }
        String first = words.get(0);
        for (String p : prefixes) {
            if (first.equals(p) || first.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    // 布尔方法用整名匹配，且只允许从开头匹配
    private static boolean startsAny(String flat, String[] prefixes) {
        for (String p : prefixes) {
            if (flat.startsWith(p)) {
                return true;
            }
        }
        return false;
    }
}
