package io.github.shuoh118.adskill;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.lang.reflect.Method;

// 隐藏 API 的统一入口
public final class App {

    private static volatile Object activityThread;
    private static volatile Object application;

    private App() {
    }

        // android.app.ActivityThread.currentActivityThread()
    public static Object activityThread() {
        Object t = activityThread;
        if (t != null) {
            return t;
        }
        try {
            Class<?> cls = Class.forName("android.app.ActivityThread");
            Method m = cls.getDeclaredMethod("currentActivityThread");
            m.setAccessible(true);
            t = m.invoke(null);
            activityThread = t;
        } catch (Throwable e) {
            Xp.debug("activityThread fail :: " + e);
        }
        return t;
    }

        // android.app.ActivityThread.currentApplication()
    public static Object application() {
        Object a = application;
        if (a != null) {
            return a;
        }
        try {
            Object t = activityThread();
            if (t != null) {
                Method m = t.getClass().getDeclaredMethod("getApplication");
                m.setAccessible(true);
                a = m.invoke(t);
            }
            if (a == null) {
                Class<?> cls = Class.forName("android.app.ActivityThread");
                Method m = cls.getDeclaredMethod("currentApplication");
                m.setAccessible(true);
                a = m.invoke(null);
            }
            application = a;
        } catch (Throwable e) {
            Xp.debug("application fail :: " + e);
        }
        return a;
    }

        // 当前进程包名
    public static String packageName() {
        try {
            Object t = activityThread();
            if (t != null) {
                Method m = t.getClass().getDeclaredMethod("currentPackageName");
                m.setAccessible(true);
                Object v = m.invoke(t);
                if (v instanceof String) {
                    return (String) v;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object app = application();
            if (app != null) {
                Method m = app.getClass().getMethod("getPackageName");
                Object v = m.invoke(app);
                if (v instanceof String) {
                    return (String) v;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

        // ApplicationInfo，拿 apk 路径和 split 路径
    public static ApplicationInfo appInfo() {
        try {
            Object app = application();
            if (app == null) {
                return null;
            }
            Method m = app.getClass().getMethod("getApplicationInfo");
            Object v = m.invoke(app);
            if (v instanceof ApplicationInfo) {
                return (ApplicationInfo) v;
            }
        } catch (Throwable e) {
            Xp.debug("appInfo fail :: " + e);
        }
        return null;
    }

    public static PackageManager pm() {
        try {
            Object app = application();
            if (app == null) {
                return null;
            }
            Method m = app.getClass().getMethod("getPackageManager");
            Object v = m.invoke(app);
            if (v instanceof PackageManager) {
                return (PackageManager) v;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static PackageInfo packageInfo(int flags) {
        try {
            PackageManager pm = pm();
            String pkg = packageName();
            if (pm == null || pkg == null) {
                return null;
            }
            return pm.getPackageInfo(pkg, flags);
        } catch (Throwable ignored) {
            return null;
        }
    }

        // 签名指纹，判断是不是官方包（防止改包名寄生）
    public static String signature() {
        try {
            PackageInfo info = packageInfo(PackageManager.GET_SIGNATURES);
            if (info == null || info.signatures == null || info.signatures.length == 0) {
                return null;
            }
            Signature s = info.signatures[0];
            return s == null ? null : String.valueOf(s.hashCode());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
