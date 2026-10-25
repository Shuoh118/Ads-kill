package io.github.shuoh118.adskill;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

/*
 * 酷安信息流广告 + 赞助卡片。
 *
 * 移植自 coolapk-purifier 的 EntityClassifier。
 *
 * 判定规则（来自原项目）：
 *   template / entityId / title 拼起来后包含：
 *     sponsor / feed_detail_reply_sponsor_card /
 *     nativead / advert / 以 adcard 结尾
 *   或 entityType 是 advertisement / native_ad
 *
 * 例外：feedDetailReplySponsorCard 是正常的回复区卡片，不能删。
 *
 * 做法：hook Entity 列表的返回，把命中的条目从列表里移除。
 */
public final class CoFeed {

    private CoFeed() {
    }

    public static void install(ClassLoader cl) {
        int n = 0;
        // 1. 列表过滤：hook EntityListFragment 返回列表的方法
        n += hookListFragment(cl);
        // 2. EntityAdHelper：广告判断直接返回 false
        n += hookAdHelper(cl);
        Xp.log("CoFeed hooks=" + n);
    }

    // ---------- 列表过滤 ----------
    private static int hookListFragment(ClassLoader cl) {
        int count = 0;
        try {
            Class<?> frag = Class.forName(CoProfile.CLS_ENTITY_LIST, false, cl);
            for (Method m : frag.getDeclaredMethods()) {
                String n = m.getName();
                // 只挂返回列表或数组的方法
                Class<?> ret = m.getReturnType();
                if (ret != List.class && !ret.isArray()) {
                    continue;
                }
                if (m.getParameterCount() > 2) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                }
                String id = "co.feed." + n + "#" + m.getParameterCount();
                Xp.hook(id, m, SafeHook.view("co.feed", new SafeHook.Body() {
                    @Override
                    public Object run(XposedInterface.Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        filter(result);
                        return result;
                    }
                }));
                count++;
            }
        } catch (Throwable t) {
            Xp.debug("CoFeed list miss :: " + t);
        }
        return count;
    }

    // 从列表里删掉广告
    @SuppressWarnings("unchecked")
    private static void filter(Object result) {
        if (!(result instanceof List)) {
            return;
        }
        try {
            List<?> list = (List<?>) result;
            if (list.isEmpty()) {
                return;
            }
            // 先判断有没有要删的，避免无谓的迭代器创建
            boolean any = false;
            for (Object o : list) {
                if (isSponsored(o)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return;
            }
            Iterator<?> it = list.iterator();
            int removed = 0;
            while (it.hasNext()) {
                Object o = it.next();
                if (isSponsored(o)) {
                    try {
                        it.remove();
                        removed++;
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (removed > 0) {
                Xp.log("co feed removed=" + removed);
            }
        } catch (Throwable t) {
            Xp.debug("CoFeed filter fail :: " + t);
        }
    }

    /*
     * 是不是广告条目。完全照搬原项目的指纹规则。
     */
    private static boolean isSponsored(Object entity) {
        if (entity == null) {
            return false;
        }
        try {
            String template = read(entity, "getEntityTemplate");
            if (template == null) {
                return false;
            }
            // 回复区赞助卡是正常内容
            if (CoProfile.REPLY_SPONSOR_KEEP.equals(template)) {
                return false;
            }
            String id = nullToEmpty(read(entity, "getEntityId"));
            String title = nullToEmpty(read(entity, "getTitle"));
            String type = nullToEmpty(read(entity, "getEntityType"));

            String tpl = template.toLowerCase();
            String fp = tpl + "\n" + id.toLowerCase() + "\n" + title.toLowerCase();

            return fp.contains("sponsor")
                    || fp.contains("feed_detail_reply_sponsor_card")
                    || tpl.contains("nativead")
                    || tpl.contains("advert")
                    || tpl.endsWith("adcard")
                    || "advertisement".equals(type.toLowerCase())
                    || "native_ad".equals(type.toLowerCase());
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String read(Object obj, String getter) {
        try {
            Method m = obj.getClass().getMethod(getter);
            Object v = m.invoke(obj);
            return v == null ? null : String.valueOf(v);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ---------- EntityAdHelper ----------
    private static int hookAdHelper(ClassLoader cl) {
        int count = 0;
        try {
            Class<?> helper = Class.forName(CoProfile.CLS_ENTITY_AD_HELPER, false, cl);
            for (Method m : helper.getDeclaredMethods()) {
                if (m.getReturnType() != boolean.class) {
                    continue;
                }
                String n = m.getName().toLowerCase();
                if (!n.contains("ad") && !n.contains("sponsor")) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                }
                String id = "co.adhelper." + m.getName();
                Xp.hook(id, m, SafeHook.view("co.adhelper", new SafeHook.Body() {
                    @Override
                    public Object run(XposedInterface.Chain chain) throws Throwable {
                        return Boolean.FALSE;
                    }
                }));
                count++;
            }
        } catch (Throwable t) {
            Xp.debug("CoFeed adhelper miss :: " + t);
        }
        return count;
    }
}
