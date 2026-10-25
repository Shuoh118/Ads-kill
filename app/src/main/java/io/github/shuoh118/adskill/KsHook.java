package io.github.shuoh118.adskill;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

/*
 * 快手专用去广告。
 *
 * 快手和网易云完全不同：
 *   网易云的广告是第三方 SDK 塞进来的独立 Activity / View，
 *   快手的广告是自家平台产出的数据，带着 isAd 标记混在信息流里。
 *
 * 所以快手必须从数据层下手：
 *   1. Photo.isAd() -> false           让条目失去广告身份
 *   2. Photo.getAdInfo() -> null       摘掉广告角标和跳转数据
 *   3. 列表组装时跳过广告项             从源头不进列表
 *
 * 只扫 com.kuaishou / com.yxcorp / com.kwai 三个包，控制范围。
 */
public final class KsHook {

    // 只扫这些包
    private static final String[] PKG_SCAN = {
            "com.kuaishou.",
            "com.yxcorp.",
            "com.kwai."
    };

    // 判定"这是广告"的方法，返回 false
    private static final String[] IS_AD = {
            "isad", "isadphoto", "isnativead", "isbrandad", "iscommercial",
            "isadvert", "issponsor", "ispromote", "isaditem", "isads"
    };

    // 取广告数据的方法，返回 null
    // 只保留 100% 是广告数据的 getter
    private static final String[] GET_AD = {
            "getadinfo", "getaddata", "getadmodel", "getadphoto"
    };

    // 广告位置 / 类型，返回 0
    private static final String[] AD_POS = {
            "getadtype", "getadposition", "getadstyle", "getadscene"
    };

    private KsHook() {
    }

    // 最多加载这么多个类，防止大应用 OOM
    private static final int MAX_LOAD = 3000;

    public static void install(ClassLoader cl) {
        long t0 = System.currentTimeMillis();
        List<String> names = ClassScan.allClassNames(cl);
        int classes = 0;
        int methods = 0;
        int loaded = 0;

        for (String name : names) {
            if (name == null || !inScan(name)) {
                continue;
            }
            // 先用名字粗筛，避免加载全部类
            if (!looksInteresting(name)) {
                continue;
            }
            if (loaded >= MAX_LOAD) {
                Xp.log("KsHook hit load limit " + MAX_LOAD);
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
        Xp.log("KsHook classes=" + classes + " methods=" + methods
                + " scanned=" + names.size()
                + " in " + (System.currentTimeMillis() - t0) + "ms");
    }

    private static boolean inScan(String name) {
        for (String p : PKG_SCAN) {
            if (name.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /*
     * 只加载真正像广告的类。
     *
     * 之前这里还有 model / bean / entity / feed，在快手 33 万个类里
     * 命中几万个，逐个 Class.forName 直接把内存打爆 —— 这就是闪退原因。
     *
     * 现在只保留广告强特征，并且必须同时满足"在广告包里"。
     */
    private static boolean looksInteresting(String name) {
        String low = name.toLowerCase();
        // 必须落在快手的广告相关包里
        if (low.contains(".ad.") || low.contains(".ads.") || low.contains("advert")
                || low.contains("commercial") || low.contains("ksad")) {
            return true;
        }
        // 或者类名以 Ad 开头/结尾
        int dot = low.lastIndexOf('.');
        String simple = dot < 0 ? low : low.substring(dot + 1);
        return simple.startsWith("ad") || simple.endsWith("ad")
                || simple.contains("adphoto") || simple.contains("adfeed");
    }

    private static int hookClass(Class<?> owner) {
        // View 子类跳过，避免影响布局
        if (android.view.View.class.isAssignableFrom(owner)) {
            return 0;
        }
        Method[] methods;
        try {
            methods = owner.getDeclaredMethods();
        } catch (Throwable t) {
            return 0;
        }
        int count = 0;
        for (Method m : methods) {
            if (m == null || m.isSynthetic() || m.isBridge()) {
                continue;
            }
            if (Modifier.isAbstract(m.getModifiers()) || Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            if (m.getDeclaringClass() != owner || m.getParameterCount() > 0) {
                continue;
            }
            try {
                if (hookMethod(m)) {
                    count++;
                }
            } catch (Throwable t) {
                Xp.debug("ks method fail " + m + " :: " + t);
            }
        }
        return count;
    }

    private static boolean hookMethod(Method m) {
        String flat = flatten(m.getName());
        Class<?> ret = m.getReturnType();
        String id = "ks." + m.getDeclaringClass().getName() + "#" + m.getName();

        // 1. isAd 系列 -> false
        if (ret == boolean.class && equalsAny(flat, IS_AD)) {
            Xp.hook(id, m, new Constant(Boolean.FALSE));
            Xp.log("ks hook " + m.getDeclaringClass().getSimpleName() + "#" + m.getName());
            return true;
        }

        // 2. getAdInfo 系列 -> null
        //    只在方法名明确是广告数据时动手，避免把正常数据置空
        if (!ret.isPrimitive() && equalsAny(flat, GET_AD)) {
            Xp.hook(id, m, new Constant(null));
            Xp.log("ks hook " + m.getDeclaringClass().getSimpleName() + "#" + m.getName());
            return true;
        }
        // 注意：不碰任何 getLikeCount / getCommentCount / getFollowerCount

        // 3. getAdType 系列 -> 0
        if (ret == int.class && equalsAny(flat, AD_POS)) {
            Xp.hook(id, m, new Constant(0));
            Xp.log("ks hook " + m.getDeclaringClass().getSimpleName() + "#" + m.getName());
            return true;
        }

        return false;
    }

    private static String flatten(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean equalsAny(String flat, String[] set) {
        for (String s : set) {
            if (flat.equals(s)) {
                return true;
            }
        }
        return false;
    }

    // 固定返回值
    private static final class Constant implements XposedInterface.Hooker {
        private final Object value;

        Constant(Object v) {
            this.value = v;
        }

        @Override
        public Object intercept(XposedInterface.Chain chain) {
            return value;
        }
    }
}
