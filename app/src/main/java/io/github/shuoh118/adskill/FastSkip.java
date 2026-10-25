package io.github.shuoh118.adskill;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

/*
 * 广告加载拦截 + 立即回调失败。
 *
 * 之前只按硬编码类名找（com.kwad.sdk.* / com.kuaishou.ad.*），
 * 那些是接入方 SDK，快手 App 自己不用，所以一个都没找到。
 *
 * 现在直接扫本应用 dex，用类名词根找：
 *   splash / adload / admanager / feedad / ksad
 * 找到就拦 load/preload/request，并立刻回调失败。
 */
public final class FastSkip {

    /*
     * 类名必须明确是"广告"的，才拦它的 load。
     *
     * === 之前误伤评论/点赞的原因 ===
     *
     * 这里原来有 "splash" 和 "feedad" 两个宽泛词。
     * 快手的 FeedLoadManager、SplashLoader 这类正常业务类都会被命中，
     * 然后它的 loadCommentList(callback) 被拦成 null ——
     * 表现就是评论数、点赞数有时候有有时候没有。
     *
     * 现在只认"广告"语义的类名。
     */
    private static final String[] CLS_HINT = {
            "adload", "admanager", "adhelper", "adutil", "adproxy",
            "nativead", "ksad", "advert", "adrequest", "adcache"
    };

    // 类名里带这些的，即使命中了 CLS_HINT 也不动
    private static final String[] CLS_SKIP = {
            "comment", "like", "digg", "praise", "follow", "fans",
            "profile", "user", "feedload", "feedmanager", "feedcache",
            "splashloader", "splashmanager"
    };

    private static final String[] VERB = {
            "load", "preload", "request", "fetch"
    };

    private static final String[] FAIL = {
            "onError", "onAdError", "onFailed", "onLoadFailed", "onAdLoadFailed",
            "onNoAd", "onAdFailed", "onAdFailedToLoad", "onSplashAdFailToPresent",
            "onRenderFail", "onTimeout", "onAdShowError"
    };

    private static final int MAX_PER_CLASS = 3;

    private FastSkip() {
    }

    // 最多加载这么多个类，防止大应用 OOM
    private static final int MAX_LOAD = 2000;

    public static void install(ClassLoader cl) {
        long t0 = System.currentTimeMillis();
        List<String> names = ClassScan.allClassNames(cl);
        int classes = 0;
        int methods = 0;
        int loaded = 0;

        for (String name : names) {
            if (name == null || !interesting(name)) {
                continue;
            }
            if (loaded >= MAX_LOAD) {
                Xp.log("FastSkip hit load limit " + MAX_LOAD);
                break;
            }
            loaded++;
            Class<?> type;
            try {
                type = Class.forName(name, false, cl);
            } catch (Throwable t) {
                continue;
            }
            int n = hookClass(type);
            if (n > 0) {
                classes++;
                methods += n;
            }
        }
        Xp.log("FastSkip classes=" + classes + " methods=" + methods
                + " scanned=" + names.size()
                + " in " + (System.currentTimeMillis() - t0) + "ms");
    }

    private static boolean interesting(String name) {
        String low = name.toLowerCase();
        for (String skip : CLS_SKIP) {
            if (low.contains(skip)) {
                return false;
            }
        }
        for (String h : CLS_HINT) {
            if (low.contains(h)) {
                return true;
            }
        }
        return false;
    }

    private static int hookClass(Class<?> owner) {
        if (android.view.View.class.isAssignableFrom(owner)) {
            return 0;
        }
        Method[] ms;
        try {
            ms = owner.getDeclaredMethods();
        } catch (Throwable t) {
            return 0;
        }
        int count = 0;
        for (Method m : ms) {
            if (count >= MAX_PER_CLASS) {
                break;
            }
            if (m == null || m.isSynthetic() || m.isBridge()) {
                continue;
            }
            if (m.getDeclaringClass() != owner) {
                continue;
            }
            String flat = m.getName().toLowerCase();
            boolean wanted = false;
            for (String v : VERB) {
                if (flat.startsWith(v)) {
                    wanted = true;
                    break;
                }
            }
            if (!wanted || m.getParameterCount() == 0) {
                continue;
            }
            if (!hasCallbackParam(m)) {
                continue;
            }
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            String id = "fs." + owner.getSimpleName() + "#" + m.getName();
            Xp.hook(id, m, SafeHook.view("fastskip", new SafeHook.Body() {
                @Override
                public Object run(XposedInterface.Chain chain) throws Throwable {
                    fireFail(chain);
                    return null;
                }
            }));
            count++;
        }
        return count;
    }

    // 回调参数必须是"广告回调"，名字里要带 ad
    private static boolean hasCallbackParam(Method m) {
        for (Class<?> p : m.getParameterTypes()) {
            String n = p.getName().toLowerCase();
            if (n.startsWith("java.") || n.startsWith("android.os")
                    || n.startsWith("android.view")) {
                continue;
            }
            boolean isCallback = p.isInterface() || n.contains("listener")
                    || n.contains("callback") || n.contains("handler");
            if (!isCallback) {
                continue;
            }
            // 名字里必须有 ad 语义
            if (n.contains("ad")) {
                return true;
            }
        }
        return false;
    }

    private static void fireFail(XposedInterface.Chain chain) {
        try {
            List<Object> args = Xp.args(chain);
            for (Object a : args) {
                if (a == null) {
                    continue;
                }
                if (isCallbackObj(a)) {
                    invokeFail(a);
                    return;
                }
            }
        } catch (Throwable t) {
            Xp.debug("fireFail :: " + t);
        }
    }

    private static boolean isCallbackObj(Object a) {
        if (a instanceof String || a instanceof Number || a instanceof Boolean) {
            return false;
        }
        String n = a.getClass().getName();
        if (n.startsWith("android.view") || n.startsWith("android.content")
                || n.startsWith("android.os") || n.startsWith("java.")) {
            return false;
        }
        return a.getClass().isAnonymousClass()
                || n.contains("$")
                || a instanceof java.lang.reflect.Proxy
                || hasFailMethod(a.getClass());
    }

    private static boolean hasFailMethod(Class<?> t) {
        for (Method m : t.getMethods()) {
            String n = m.getName();
            for (String f : FAIL) {
                if (n.equals(f)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void invokeFail(Object cb) {
        Class<?> type = cb.getClass();
        for (String name : FAIL) {
            Method target = find(type, name);
            if (target == null) {
                continue;
            }
            try {
                target.setAccessible(true);
                target.invoke(cb, emptyArgs(target));
                Xp.log("fastskip fired: " + name);
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private static Method find(Class<?> type, String name) {
        try {
            for (Method m : type.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() <= 3) {
                    return m;
                }
            }
            for (Class<?> itf : type.getInterfaces()) {
                for (Method m : itf.getMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() <= 3) {
                        return m;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object[] emptyArgs(Method m) {
        Class<?>[] p = m.getParameterTypes();
        Object[] out = new Object[p.length];
        for (int i = 0; i < p.length; i++) {
            out[i] = zero(p[i]);
        }
        return out;
    }

    private static Object zero(Class<?> t) {
        if (t == int.class) {
            return 0;
        }
        if (t == long.class) {
            return 0L;
        }
        if (t == boolean.class) {
            return Boolean.FALSE;
        }
        if (t == float.class) {
            return 0F;
        }
        if (t == double.class) {
            return 0D;
        }
        if (t == short.class) {
            return (short) 0;
        }
        if (t == byte.class) {
            return (byte) 0;
        }
        if (t == char.class) {
            return (char) 0;
        }
        return null;
    }
}
