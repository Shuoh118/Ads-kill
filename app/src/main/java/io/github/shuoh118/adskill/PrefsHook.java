package io.github.shuoh118.adskill;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;

// 配置层拦截
public final class PrefsHook {

    private static final String[] MMKV_NAMES = {
            "com.tencent.mmkv.MMKV",
            "com.netease.mmkv.MMKV",
            "com.netease.cloudmusic.mmkv.MMKV",
            "com.netease.cloudmusic.utils.MMKVUtils"
    };

    private static final Map<String, Boolean> KEY_CACHE = new ConcurrentHashMap<>();

    private PrefsHook() {
    }

    public static void install(ClassLoader cl) {
        int count = 0;
        for (String name : MMKV_NAMES) {
            try {
                Class<?> type = Class.forName(name, false, cl);
                count += hookMmkv(type);
            } catch (Throwable ignored) {
            }
        }
        count += hookSharedPreferences();
        Xp.log("PrefsHook methods=" + count);
    }

    private static int hookMmkv(Class<?> type) {
        int count = 0;
        Method[] methods;
        try {
            methods = type.getDeclaredMethods();
        } catch (Throwable t) {
            return 0;
        }
        for (Method m : methods) {
            String n = m.getName();
            if (!n.startsWith("decode") && !n.startsWith("get")) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (p.length < 2 || p[0] != String.class) {
                continue;
            }
            final Class<?> ret = m.getReturnType();
            String id = "prefs.mmkv." + type.getSimpleName() + "#" + n + "#" + p.length;
            try {
                Xp.hook(id, m, new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        if (!Cfg.KILL_PREFS) {
                            return chain.proceed();
                        }
                        Object key = Xp.arg(chain, 0);
                        if (!(key instanceof String) || !isAdKey((String) key)) {
                            return chain.proceed();
                        }
                        return emptyOf(ret);
                    }
                });
                count++;
            } catch (Throwable t) {
                Xp.debug("mmkv hook fail " + m + " :: " + t);
            }
        }
        return count;
    }

    private static int hookSharedPreferences() {
        Class<?> impl;
        try {
            impl = Class.forName("android.app.SharedPreferencesImpl");
        } catch (Throwable t) {
            Xp.debug("SharedPreferencesImpl not found :: " + t);
            return 0;
        }
        int count = 0;
        count += hookSp(impl, "getBoolean", boolean.class, Boolean.FALSE);
        count += hookSp(impl, "getInt", int.class, 0);
        count += hookSp(impl, "getLong", long.class, 0L);
        count += hookSp(impl, "getFloat", float.class, 0F);
        count += hookSp(impl, "getString", String.class, null);
        return count;
    }

    private static int hookSp(Class<?> owner, String name, Class<?> ret, final Object fallback) {
        try {
            Method m = owner.getDeclaredMethod(name, String.class, ret);
            m.setAccessible(true);
            String id = "prefs.sp." + name;
            Xp.hook(id, m, new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    if (!Cfg.KILL_PREFS) {
                        return chain.proceed();
                    }
                    Object key = Xp.arg(chain, 0);
                    if (key instanceof String && isAdKey((String) key)) {
                        return fallback;
                    }
                    return chain.proceed();
                }
            });
            return 1;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean isAdKey(String key) {
        Boolean cached = KEY_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        boolean hit = Names.isAdSwitchKey(key);
        if (KEY_CACHE.size() < 4096) {
            KEY_CACHE.put(key, hit);
        }
        if (hit) {
            Xp.debug("ad key blocked: " + key);
        }
        return hit;
    }

    private static Object emptyOf(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == String.class) {
            return "";
        }
        if (type == byte[].class) {
            return new byte[0];
        }
        return null;
    }
}
