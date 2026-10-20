package io.github.shuoh118.adskill;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

// libxposed api 102 的唯一适配层
public final class Xp {

    private static final String TAG = "NCM-AdFree";

    private static volatile XposedModule module;

    private Xp() {
    }

    public static void bind(XposedModule m) {
        module = m;
    }

    public static XposedModule module() {
        return module;
    }

    // ---------- 日志 ----------

    public static void log(String msg) {
        if (!Cfg.LOG) {
            return;
        }
        try {
            android.util.Log.i(TAG, msg);
        } catch (Throwable ignored) {
        }
    }

    public static void debug(String msg) {
        if (!Cfg.DEBUG) {
            return;
        }
        log(msg);
    }

    public static void err(String msg, Throwable t) {
        try {
            android.util.Log.e(TAG, msg, t);
        } catch (Throwable ignored) {
            log(msg + " :: " + t);
        }
    }

    // ---------- hook ----------

    public static void hook(Executable target, XposedInterface.Hooker hooker) {
        XposedModule m = module;
        if (m == null || target == null || hooker == null) {
            return;
        }
        try {
            m.hook(target).intercept(hooker);
        } catch (Throwable t) {
            debug("hook fail " + target + " :: " + t);
        }
    }

        // 给 hook 一个稳定 id，重复装载时原子替换而不是叠加
    public static void hook(String id, Executable target, XposedInterface.Hooker hooker) {
        XposedModule m = module;
        if (m == null || target == null || hooker == null) {
            return;
        }
        try {
            m.hook(target).setId(id).intercept(hooker);
        } catch (Throwable t) {
            debug("hook fail " + target + " :: " + t);
        }
    }

    // ---------- Hooker 工具 ----------

        // 只放行不干预
    public static Object pass(XposedInterface.Chain chain) throws Throwable {
        return chain.proceed();
    }

        // 恒定返回值：不调 proceed，直接返回
    public static XposedInterface.Hooker constant(final Object value) {
        return new XposedInterface.Hooker() {
            @Override
            public Object intercept(XposedInterface.Chain chain) {
                return value;
            }
        };
    }

        // 先跑原方法，再改返回值
    public static XposedInterface.Hooker tail(final Object value) {
        return new XposedInterface.Hooker() {
            @Override
            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                try {
                    chain.proceed();
                } catch (Throwable ignored) {
                }
                return value;
            }
        };
    }

    // ---------- Chain 取值封装 ----------

    public static List<Object> args(XposedInterface.Chain chain) {
        try {
            List<Object> a = chain.getArgs();
            if (a == null) {
                return java.util.Collections.emptyList();
            }
            return a;
        } catch (Throwable t) {
            return java.util.Collections.emptyList();
        }
    }

    public static Object arg(XposedInterface.Chain chain, int index) {
        try {
            List<Object> a = args(chain);
            if (index < 0 || index >= a.size()) {
                return null;
            }
            return a.get(index);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object self(XposedInterface.Chain chain) {
        try {
            return chain.getThisObject();
        } catch (Throwable t) {
            return null;
        }
    }

    public static Executable executable(XposedInterface.Chain chain) {
        try {
            return chain.getExecutable();
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------- 参数反射 ----------

        // api 100 用 getDefaultClassLoader，101+ 也可能叫 getClassLoader
    public static ClassLoader classLoaderOf(Object param) {
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

    public static String packageNameOf(Object param) {
        if (param != null) {
            try {
                Method m = param.getClass().getMethod("getPackageName");
                m.setAccessible(true);
                Object v = m.invoke(param);
                if (v instanceof String) {
                    return (String) v;
                }
            } catch (Throwable ignored) {
            }
        }
        return App.packageName();
    }
}
