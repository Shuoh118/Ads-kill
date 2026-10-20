package io.github.shuoh118.adskill;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/*
 * 拿到"目标应用"的 ClassLoader。
 *
 * === 之前为什么拿到错的 ===
 *
 * Bootstrap 里写的是：
 *     if (cl == null) cl = Entry.class.getClassLoader();
 * Entry 是模块自己的类，它的 ClassLoader 是 Xposed 给的模块 ClassLoader，
 * 里面只有模块自己的类和系统框架，没有目标应用的任何类。
 *
 * 用它去读 pathList.dexElements，拿到的全是 /system/... 下的 overlay apk，
 * 所以扫描结果永远是 0 个目标应用类。
 *
 * === 正确做法 ===
 *
 * 按优先级：
 *   1. PackageLoadedParam.getClassLoader() / getDefaultClassLoader()  —— 最准
 *   2. ActivityThread.currentApplication().getClassLoader()
 *   3. ActivityThread 里 mInitialApplication 的 ClassLoader
 *
 * 每一步都验证：这个 ClassLoader 能否加载目标包名下的类。
 * 验证不过就换下一个。
 */
public final class AppCl {

    private AppCl() {
    }

    /** 找目标应用的 ClassLoader，找不到返回 null */
    public static ClassLoader find(String pkg, Object param) {
        ClassLoader cl;

        // 1. 回调参数里的
        cl = fromParam(param);
        if (ok(cl, pkg)) {
            Xp.log("AppCl: from param");
            return cl;
        }

        // 2. Application 的
        cl = fromApplication();
        if (ok(cl, pkg)) {
            Xp.log("AppCl: from application");
            return cl;
        }

        // 3. ActivityThread.mInitialApplication
        cl = fromActivityThread();
        if (ok(cl, pkg)) {
            Xp.log("AppCl: from activityThread");
            return cl;
        }

        Xp.log("AppCl: NOT FOUND for " + pkg);
        return null;
    }

    private static ClassLoader fromParam(Object param) {
        if (param == null) {
            return null;
        }
        String[] names = {"getClassLoader", "getDefaultClassLoader"};
        for (String n : names) {
            try {
                Method m = param.getClass().getMethod(n);
                m.setAccessible(true);
                Object v = m.invoke(param);
                if (v instanceof ClassLoader) {
                    return (ClassLoader) v;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static ClassLoader fromApplication() {
        try {
            Object app = App.application();
            if (app != null) {
                Method m = app.getClass().getMethod("getClassLoader");
                Object v = m.invoke(app);
                if (v instanceof ClassLoader) {
                    return (ClassLoader) v;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ClassLoader fromActivityThread() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method cur = at.getDeclaredMethod("currentActivityThread");
            cur.setAccessible(true);
            Object thread = cur.invoke(null);
            if (thread == null) {
                return null;
            }
            Field f = at.getDeclaredField("mInitialApplication");
            f.setAccessible(true);
            Object app = f.get(thread);
            if (app != null) {
                Method m = app.getClass().getMethod("getClassLoader");
                Object v = m.invoke(app);
                if (v instanceof ClassLoader) {
                    return (ClassLoader) v;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /*
     * 验证这个 ClassLoader 确实属于目标应用。
     * 办法：尝试加载目标包名下的一个必然存在的类（用 Application 类名推不出来，
     * 所以改为检查 dexElements 里有没有该包名的 apk 路径）。
     */
    private static boolean ok(ClassLoader cl, String pkg) {
        if (cl == null || pkg == null) {
            return false;
        }
        // 模块自己的 ClassLoader 直接排除
        if (cl == AppCl.class.getClassLoader()) {
            return false;
        }
        try {
            // 看它能不能加载目标包名下的任意类：
            // 用一个几乎必然存在的类名去试，失败就说明不是目标 ClassLoader
            String probe = pkg + ".R";
            try {
                Class.forName(probe, false, cl);
                return true;
            } catch (ClassNotFoundException e) {
                // R 类可能被混淆掉，改用 dexElements 检查
            }
        } catch (Throwable ignored) {
        }
        return hasPkgDex(cl, pkg);
    }

    // 看 dexElements 里有没有该包名的 apk
    private static boolean hasPkgDex(ClassLoader cl, String pkg) {
        try {
            Class<?> bdcl = Class.forName("dalvik.system.BaseDexClassLoader");
            Field pf = bdcl.getDeclaredField("pathList");
            pf.setAccessible(true);
            Object pathList = pf.get(cl);
            if (pathList == null) {
                return false;
            }
            Field ef = pathList.getClass().getDeclaredField("dexElements");
            ef.setAccessible(true);
            Object[] els = (Object[]) ef.get(pathList);
            if (els == null) {
                return false;
            }
            for (Object el : els) {
                if (el == null) {
                    continue;
                }
                String p = pathOf(el);
                if (p != null && p.contains(pkg)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String pathOf(Object element) {
        Class<?> c = element.getClass();
        int guard = 0;
        while (c != null && guard++ < 5) {
            for (String fn : new String[]{"path", "name"}) {
                try {
                    Field f = c.getDeclaredField(fn);
                    f.setAccessible(true);
                    Object v = f.get(element);
                    if (v instanceof String) {
                        return (String) v;
                    }
                } catch (Throwable ignored) {
                }
            }
            try {
                Field f = c.getDeclaredField("zip");
                f.setAccessible(true);
                Object v = f.get(element);
                if (v instanceof java.io.File) {
                    return ((java.io.File) v).getAbsolutePath();
                }
            } catch (Throwable ignored) {
            }
            c = c.getSuperclass();
        }
        return null;
    }
}
