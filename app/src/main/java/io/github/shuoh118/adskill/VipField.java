package io.github.shuoh118.adskill;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

/*
 * 本地会员：不扫 dex，运行时抓对象。
 *
 * === 为什么要换方案 ===
 *
 * 原来靠 ClassScan 扫 dex 拿类名，再逐个 Class.forName。
 * 但在 LSPosed 环境里读 /data/app/ 下的 apk 经常失败
 * （日志里 scan classes=0 apks=31），于是 VipHook 永远匹配不到类。
 *
 * === 新方案 ===
 *
 * 不预先知道类名，改成在"对象流经的地方"动手：
 *
 *   1. hook JSON 反序列化（Gson / fastjson / org.json）
 *      网络返回的会员数据都要过这一步
 *   2. 在返回的对象里就地改写 boolean 字段
 *
 * 这样不需要任何类名，只要会员数据被解析成对象，就会被改掉。
 */
public final class VipField {

    // 字段名含这些 -> true
    private static final String[] TRUE_KEY = {
            "isvip", "vip", "isblackvip", "blackvip", "ismember", "member",
            "isvipuser", "vipuser", "ispremium", "premium", "isprivilege",
            "isbuy", "ispaid", "hasvip", "hasmember"
    };

    // 字段名含这些 -> false（优先）
    private static final String[] FALSE_KEY = {
            "expired", "isoverdue", "overdue", "invalid", "needpay", "needbuy",
            "paywall", "islock", "locked"
    };

    // 跳过这些，别改坏别的
    private static final String[] SKIP_KEY = {
            "ad", "ads", "advert", "show", "hide", "visible", "enable",
            "click", "loading", "selected", "checked", "playing", "mute"
    };

    // 已处理过的对象，避免重复
    private static final Map<Object, Boolean> DONE = new WeakHashMap<>();

    private VipField() {
    }

    public static void install(ClassLoader cl) {
        int n = 0;
        n += hookGson(cl);
        n += hookFastJson(cl);
        n += hookJsonObject(cl);
        Xp.log("VipField methods=" + n);
    }

    // ---------- Gson ----------
    private static int hookGson(ClassLoader cl) {
        int count = 0;
        try {
            Class<?> gson = Class.forName("com.google.gson.Gson", false, cl);
            for (Method m : gson.getDeclaredMethods()) {
                if (!m.getName().equals("fromJson")) {
                    continue;
                }
                if (m.getParameterCount() < 2) {
                    continue;
                }
                // 只挂 (String, Class) 和 (JsonReader, Type)
                Class<?>[] p = m.getParameterTypes();
                if (p[0] != String.class || p[1] != Class.class) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                }
                Xp.hook("vipfield.gson." + m.getParameterCount(), m,
                        SafeHook.view("vipfield.gson", new SafeHook.Body() {
                            @Override
                            public Object run(XposedInterface.Chain chain) throws Throwable {
                                Object result = chain.proceed();
                                if (result != null) {
                                    apply(result);
                                }
                                return result;
                            }
                        }));
                count++;
            }
        } catch (Throwable t) {
            Xp.debug("gson hook fail :: " + t);
        }
        return count;
    }

    // ---------- fastjson ----------
    private static int hookFastJson(ClassLoader cl) {
        int count = 0;
        try {
            Class<?> json = Class.forName("com.alibaba.fastjson.JSON", false, cl);
            for (Method m : json.getDeclaredMethods()) {
                if (!m.getName().equals("parseObject")) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (p.length < 2 || p[0] != String.class || p[1] != Class.class) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                }
                Xp.hook("vipfield.fastjson." + p.length, m,
                        SafeHook.view("vipfield.fastjson", new SafeHook.Body() {
                            @Override
                            public Object run(XposedInterface.Chain chain) throws Throwable {
                                Object result = chain.proceed();
                                if (result != null) {
                                    apply(result);
                                }
                                return result;
                            }
                        }));
                count++;
            }
        } catch (Throwable t) {
            Xp.debug("fastjson hook fail :: " + t);
        }
        return count;
    }

    // ---------- org.json ----------
    private static int hookJsonObject(ClassLoader cl) {
        int count = 0;
        try {
            Class<?> jo = Class.forName("org.json.JSONObject", false, cl);
            Method m = jo.getDeclaredMethod("put", String.class, Object.class);
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            Xp.hook("vipfield.jsonobject.put", m,
                    SafeHook.view("vipfield.json", new SafeHook.Body() {
                        @Override
                        public Object run(XposedInterface.Chain chain) throws Throwable {
                            Object key = Xp.arg(chain, 0);
                            if (key instanceof String && isVipKey((String) key)) {
                                // 直接改写入的值
                                Object[] args = Xp.args(chain).toArray();
                                args[1] = Boolean.TRUE;
                                return chain.proceed(args);
                            }
                            return chain.proceed();
                        }
                    }));
            count++;
        } catch (Throwable t) {
            Xp.debug("jsonobject hook fail :: " + t);
        }
        return count;
    }

    private static boolean isVipKey(String k) {
        String low = k.toLowerCase();
        for (String s : TRUE_KEY) {
            if (low.contains(s)) {
                return true;
            }
        }
        return false;
    }

    // ---------- 改字段 ----------

    public static void apply(Object obj) {
        if (obj == null) {
            return;
        }
        synchronized (DONE) {
            if (DONE.containsKey(obj)) {
                return;
            }
            if (DONE.size() > 2000) {
                DONE.clear();
            }
            DONE.put(obj, Boolean.TRUE);
        }
        try {
            Class<?> c = obj.getClass();
            // 只看本类和最近几层父类
            int guard = 0;
            while (c != null && c != Object.class && guard++ < 5) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType() != boolean.class) {
                        continue;
                    }
                    String n = f.getName().toLowerCase();
                    if (hasAny(n, SKIP_KEY)) {
                        continue;
                    }
                    boolean want;
                    if (hasAny(n, FALSE_KEY)) {
                        want = false;
                    } else if (hasAny(n, TRUE_KEY)) {
                        want = true;
                    } else {
                        continue;
                    }
                    try {
                        f.setAccessible(true);
                        if (f.getBoolean(obj) != want) {
                            f.setBoolean(obj, want);
                            Xp.log("vip field set " + c.getSimpleName()
                                    + "." + f.getName() + "=" + want);
                        }
                    } catch (Throwable ignored) {
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasAny(String name, String[] keys) {
        for (String k : keys) {
            if (name.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
