package io.github.shuoh118.adskill;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

// 本地会员
public final class VipHook {

    private static final String[] CLASS_HINT = {
            "user", "account", "vip", "member", "profile", "privilege", "right", "wallet", "asset"
    };

        // 名字里带这些的，是数据容器不是判定，绝不改
    private static final String[] SKIP_WORD = {
            "info", "data", "detail", "bean", "model", "entity", "response",
            "result", "list", "map", "json", "config", "setting", "cache"
    };

    private static final String[] NEGATIVE = {
            "expired", "expireover", "overdue", "invalid", "timeout", "banned",
            "forbidden", "needbuy", "needpay", "paywall", "locked", "outdate"
    };

    private static final String[] TYPE_KEY = {
            "viptype", "vipkind", "membertype", "viptier", "viprank"
    };

    private static final String[] LEVEL_KEY = {
            "viplevel", "vipgrade", "memberlevel", "vipstatus"
    };

    private static final String[] TIME_KEY = {
            "vipexpire", "vipend", "vipdeadline", "viptime",
            "memberexpire", "memberend", "vipvalidtime"
    };

    private static final String[] LABEL_KEY = {
            "viplabel", "vipname", "membername", "viptitle", "viptag", "membertag"
    };

    private static final String[] FREE_KEY = {
            "adfree", "noad", "noads", "freead", "withoutad", "adremoved", "removead"
    };

        // 布尔判定方法必须以这些开头
    private static final String[] BOOL_HEAD = {
            "is", "has", "can", "should", "need", "will", "support", "allow"
    };

    private static final Set<String> EXCLUDE = new HashSet<>(Arrays.asList(
            "toString", "hashCode", "equals", "clone", "writeToParcel", "describeContents",
            "getLayoutParams", "getContext", "getParent", "getResources", "getVisibility"
    ));

    private VipHook() {
    }

    public static void install(ClassLoader cl) {
        long t0 = System.currentTimeMillis();
        List<String> names = ClassScan.allClassNames(cl);
        List<String> targets = new ArrayList<>(512);
        for (String n : names) {
            if (n == null) {
                continue;
            }
            List<String> w = Names.words(n);
            for (String hint : CLASS_HINT) {
                if (w.contains(hint)) {
                    targets.add(n);
                    break;
                }
            }
        }
        int classes = 0;
        int methods = 0;
        for (String n : targets) {
            Class<?> type;
            try {
                type = Class.forName(n, false, cl);
            } catch (Throwable t) {
                continue;
            }
            int c = hookClass(type);
            if (c > 0) {
                classes++;
                methods += c;
            }
        }
        Xp.log("VipHook classes=" + classes + " methods=" + methods
                + " candidates=" + targets.size()
                + " in " + (System.currentTimeMillis() - t0) + "ms");
    }

    private static int hookClass(Class<?> owner) {
        // View 子类跳过
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
            if (m.getDeclaringClass() != owner || EXCLUDE.contains(m.getName())) {
                continue;
            }
            if (m.getParameterCount() > 1) {
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
        List<String> w = Names.words(m.getName());
        if (w.isEmpty()) {
            return false;
        }
        String flat = flatten(w);
        Class<?> ret = m.getReturnType();
        String id = "vip." + m.getDeclaringClass().getName() + "#" + m.getName();

        // 数据容器跳过
        for (String s : SKIP_WORD) {
            if (flat.contains(s)) {
                return false;
            }
        }

        // 免广告开关
        if (contains(flat, FREE_KEY)) {
            if (ret == boolean.class) {
                Xp.hook(id, m, new TailHook(Boolean.TRUE));
                return true;
            }
            return false;
        }

        boolean vipish = flat.contains("vip") || flat.contains("member")
                || flat.contains("privilege") || flat.contains("premium");
        if (!vipish) {
            return false;
        }

        // 过期 / 无效 -> false（必须优先）
        if (contains(flat, NEGATIVE)) {
            if (ret == boolean.class) {
                Xp.hook(id, m, new TailHook(Boolean.FALSE));
                return true;
            }
            return false;
        }

        // 布尔：必须是判定语义
        if (ret == boolean.class) {
            if (!startsAny(flat, BOOL_HEAD)) {
                return false;
            }
            Xp.hook(id, m, new TailHook(Boolean.TRUE));
            return true;
        }

        // 时间
        if (contains(flat, TIME_KEY)) {
            long expire = System.currentTimeMillis() + Cfg.VIP_EXPIRE_MS;
            if (ret == long.class) {
                Xp.hook(id, m, new TailHook(expire));
                return true;
            }
            if (ret == int.class) {
                Xp.hook(id, m, new TailHook((int) (expire / 1000L)));
                return true;
            }
            if (ret == String.class) {
                Xp.hook(id, m, new TailHook(String.valueOf(expire)));
                return true;
            }
            return false;
        }

        if (contains(flat, LABEL_KEY) && ret == String.class) {
            Xp.hook(id, m, new TailHook(Cfg.VIP_LABEL));
            return true;
        }

        if (contains(flat, LEVEL_KEY)) {
            if (ret == int.class) {
                Xp.hook(id, m, new TailHook(10));
                return true;
            }
            return false;
        }

        if (contains(flat, TYPE_KEY)) {
            if (ret == int.class) {
                Xp.hook(id, m, new TailHook(Cfg.VIP_TYPE));
                return true;
            }
            if (ret == long.class) {
                Xp.hook(id, m, new TailHook((long) Cfg.VIP_TYPE));
                return true;
            }
            return false;
        }

        return false;
    }

        // 先跑原方法（保持副作用），再返回改造值
    private static final class TailHook implements XposedInterface.Hooker {
        private final Object value;

        TailHook(Object v) {
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

    private static String flatten(List<String> w) {
        StringBuilder sb = new StringBuilder(32);
        for (String s : w) {
            sb.append(s);
        }
        return sb.toString();
    }

    private static boolean startsAny(String flat, String[] prefixes) {
        for (String p : prefixes) {
            if (flat.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String flat, String[] keys) {
        for (String k : keys) {
            if (flat.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
