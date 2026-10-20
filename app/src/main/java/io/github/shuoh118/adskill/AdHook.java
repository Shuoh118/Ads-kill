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
            "send", "post", "submit", "prepare", "download", "parse", "register"
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
            if (!startsAny(flat, ACTION)) {
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

    private static boolean startsAny(String flat, String[] prefixes) {
        for (String p : prefixes) {
            if (flat.startsWith(p) || flat.contains(p)) {
                return true;
            }
        }
        return false;
    }
}
