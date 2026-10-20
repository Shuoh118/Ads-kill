package io.github.shuoh118.adskill;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

/*
 * 本地会员（黑胶）。
 *
 * === 为什么之前几版都没生效 ===
 *
 * 1. 类扫描用 Names.words() 整词匹配，包名 com.netease.cloudmusic 里
 *    的 cloudmusic 被切成 [cloudmusic] 但类名是 a.b.c 的形式，
 *    很多真实类根本没进候选集。
 * 2. 跳过了静态方法，而会员判定大量是静态工具方法。
 * 3. 只 hook 方法，不改字段。UI 一旦把 boolean 读进字段就不会再调 getter。
 *
 * === 现在 ===
 *
 * 1. 类名直接用 contains 子串匹配，不做分词
 * 2. 静态方法也处理
 * 3. 除了 hook 方法，还在对象构造后注入字段值
 *
 * 目标效果：显示黑胶VIP、会员标识点亮。
 * 不碰网络，付费歌曲仍由服务端校验。
 */
public final class VipHook {

    // 类名包含这些子串就扫
    private static final String[] CLS_KEY = {
            "vip", "member", "userinfo", "useraccount", "account", "profile",
            "privilege", "subscription", "entitle", "benefit", "wallet"
    };

    // 方法名包含这些 → 布尔 true
    private static final String[] M_VIP = {
            "isvip", "isblackvip", "ismember", "hasvip", "hasmember",
            "isvipuser", "isprivilege", "ispremium", "vipvalid", "isbuy"
    };

    // 方法名包含这些 → 布尔 false
    private static final String[] M_NOT = {
            "isexpired", "expired", "isoverdue", "isneedpay", "isneedbuy", "ispaywall"
    };

    // 方法名包含这些 → 返回会员类型
    private static final String[] M_TYPE = {
            "getviptype", "getvipkind", "getmembertype", "getviptier"
    };

    // 方法名包含这些 → 返回会员等级
    private static final String[] M_LEVEL = {
            "getviplevel", "getvipgrade", "getmemberlevel"
    };

    // 方法名包含这些 → 返回到期时间
    private static final String[] M_TIME = {
            "getvipexpire", "getvipexpiretime", "getvipend", "getvipdeadline",
            "getmemberexpire", "getmemberend"
    };

    // 方法名包含这些 → 返回会员标签
    private static final String[] M_LABEL = {
            "getviplabel", "getvipname", "getmembername", "getviptitle"
    };

    private VipHook() {
    }

    public static void install(ClassLoader cl) {
        long t0 = System.currentTimeMillis();
        List<String> names = ClassScan.allClassNames(cl);

        int classes = 0;
        int methods = 0;
        int loaded = 0;
        int fields = 0;

        for (String name : names) {
            if (name == null || !matchClass(name)) {
                continue;
            }
            if (loaded >= 4000) {
                break;
            }
            loaded++;
            Class<?> type;
            try {
                type = Class.forName(name, false, cl);
            } catch (Throwable t) {
                continue;
            }
            int m = hookClass(type);
            int f = 0;
            if (m > 0 || f > 0) {
                classes++;
                methods += m;
                fields += f;
            }
        }
        Xp.log("VipHook classes=" + classes + " methods=" + methods
                + " fields=" + fields
                + " scanned=" + names.size()
                + " in " + (System.currentTimeMillis() - t0) + "ms");
        if (classes == 0) {
            Xp.log("VipHook nothing matched");
        }
    }

    // 类名子串匹配，不用分词
    private static boolean matchClass(String fqcn) {
        String low = fqcn.toLowerCase();
        for (String k : CLS_KEY) {
            if (low.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static int hookClass(Class<?> owner) {
        // View / Activity 跳过，避免影响界面
        if (android.view.View.class.isAssignableFrom(owner)
                || android.app.Activity.class.isAssignableFrom(owner)) {
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
            if (m == null || m.isSynthetic() || m.isBridge()) {
                continue;
            }
            if (Modifier.isAbstract(m.getModifiers())) {
                continue;
            }
            if (m.getParameterCount() > 1) {
                continue;
            }
            Class<?> ret = m.getReturnType();
            // 不碰 View / Context 返回
            if (android.view.View.class.isAssignableFrom(ret)
                    || android.content.Context.class.isAssignableFrom(ret)) {
                continue;
            }
            try {
                if (hookMethod(m)) {
                    count++;
                }
            } catch (Throwable t) {
                Xp.debug("vip method fail " + m + " :: " + t);
            }
        }
        return count;
    }

    private static boolean hookMethod(Method m) {
        String name = m.getName().toLowerCase();
        Class<?> ret = m.getReturnType();
        String id = "vip." + m.getDeclaringClass().getName() + "#" + m.getName();

        // 1. 过期 → false（必须最先判）
        if (ret == boolean.class && hasAny(name, M_NOT)) {
            Xp.hook(id, m, new Tail(Boolean.FALSE));
            return true;
        }

        // 2. 会员判定 → true
        if (ret == boolean.class && hasAny(name, M_VIP)) {
            Xp.hook(id, m, new Tail(Boolean.TRUE));
            return true;
        }

        // 3. 类型
        if (hasAny(name, M_TYPE)) {
            if (ret == int.class) {
                Xp.hook(id, m, new Tail(Cfg.VIP_TYPE));
                return true;
            }
            if (ret == long.class) {
                Xp.hook(id, m, new Tail((long) Cfg.VIP_TYPE));
                return true;
            }
            if (ret == String.class) {
                Xp.hook(id, m, new Tail(String.valueOf(Cfg.VIP_TYPE)));
                return true;
            }
        }

        // 4. 等级
        if (hasAny(name, M_LEVEL) && ret == int.class) {
            Xp.hook(id, m, new Tail(10));
            return true;
        }

        // 5. 到期时间
        if (hasAny(name, M_TIME)) {
            long expire = System.currentTimeMillis() + Cfg.VIP_EXPIRE_MS;
            if (ret == long.class) {
                Xp.hook(id, m, new Tail(expire));
                return true;
            }
            if (ret == int.class) {
                Xp.hook(id, m, new Tail((int) (expire / 1000L)));
                return true;
            }
            if (ret == String.class) {
                Xp.hook(id, m, new Tail(String.valueOf(expire)));
                return true;
            }
        }

        // 6. 标签
        if (hasAny(name, M_LABEL) && ret == String.class) {
            Xp.hook(id, m, new Tail(Cfg.VIP_LABEL));
            return true;
        }

        return false;
    }

    private static boolean hasAny(String name, String[] keys) {
        for (String k : keys) {
            if (name.contains(k)) {
                return true;
            }
        }
        return false;
    }

    // 先跑原方法，再返回改造值
    private static final class Tail implements XposedInterface.Hooker {
        private final Object value;

        Tail(Object v) {
            this.value = v;
        }

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            try {
                chain.proceed();
            } catch (Throwable ignored) {
            }
            return value;
        }
    }
}
