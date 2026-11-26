package io.github.shuoh118.adskill;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

/*
 * 知乎去广告。
 *
 * 移植自 shatyuka/Zhiliao（MIT）。
 *
 * 四个拦截点：
 *   1. LaunchAdHelper.isShowLaunchAd()  -> false
 *   2. BasePagingFragment.postRefreshSucceed() -> 列表里剔掉广告对象
 *   3. MorphAdHelper.resolveAnswerAdParam() -> false
 *   4. AppView$ZhihuWebViewClient.shouldInterceptRequest() -> 拦广告路径
 *
 * 全部用 Class.forName 加载，类不存在就跳过。
 */
public final class ZhAd {

    private ZhAd() {
    }

    public static void install(ClassLoader cl) {
        int n = 0;
        n += hookLaunchAd(cl);
        n += hookFeed(cl);
        n += hookAnswerListAd(cl);
        Xp.log("ZhAd total=" + n);
    }

    // ---------- 1. 启动广告 ----------
    private static int hookLaunchAd(ClassLoader cl) {
        int count = 0;
        // isShowLaunchAd 在 com.zhihu.android.app.util 包下的某个类里
        try {
            Method m = findMethodInPackage(cl, ZhProfile.LAUNCH_HELPER_PKG,
                    ZhProfile.LAUNCH_HELPER_METHOD, 0);
            if (m != null) {
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                }
                Xp.hook("zh.launch", m, SafeHook.view("zh.launch", new SafeHook.Body() {
                    @Override
                    public Object run(XposedInterface.Chain chain) throws Throwable {
                        Xp.log("zh launch ad blocked");
                        return Boolean.FALSE;
                    }
                }));
                count++;
                Xp.log("ZhAd hook isShowLaunchAd");
            }
        } catch (Throwable t) {
            Xp.debug("zh launch fail :: " + t);
        }
        return count;
    }

    // 在包里找指定名字零参方法
    private static Method findMethodInPackage(ClassLoader cl, String pkg,
                                              String method, int argCount) {
        try {
            List<String> names = ClassScan.allClassNames(cl);
            for (String n : names) {
                if (!n.startsWith(pkg) || n.contains("$")) {
                    continue;
                }
                Class<?> c;
                try {
                    c = Class.forName(n, false, cl);
                } catch (Throwable t) {
                    continue;
                }
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals(method)
                            && m.getParameterCount() == argCount) {
                        return m;
                    }
                }
            }
        } catch (Throwable t) {
            Xp.debug("findMethodInPackage fail :: " + t);
        }
        return null;
    }

    // ---------- 2. 信息流广告 ----------
    private static int hookFeed(ClassLoader cl) {
        int count = 0;
        try {
            Class<?> frag = Class.forName(ZhProfile.CLS_PAGING_FRAGMENT, false, cl);
            for (Method m : frag.getDeclaredMethods()) {
                if (!m.getName().equals(ZhProfile.M_PAGING_REFRESH)) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                }
                String id = "zh.feed." + m.getParameterCount();
                Xp.hook(id, m, SafeHook.view("zh.feed", new SafeHook.Body() {
                    @Override
                    public Object run(XposedInterface.Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        stripFeed(chain);
                        return result;
                    }
                }));
                count++;
                Xp.log("ZhAd hook postRefreshSucceed");
            }
        } catch (Throwable t) {
            Xp.debug("zh feed fail :: " + t);
        }
        return count;
    }

    /*
     * 从 Fragment 的 FeedList.data 里剔掉广告对象。
     * 判定不看类名，看类名里有没有 Advert / ListAd / FeedAd。
     */
    private static void stripFeed(XposedInterface.Chain chain) {
        try {
            Object self = Xp.self(chain);
            if (self == null) {
                return;
            }
            Object feedList = readField(self, "feedList");
            if (feedList == null) {
                // 兼容：直接找 List 字段
                return;
            }
            Object data = readField(feedList, ZhProfile.FIELD_FEED_DATA);
            if (!(data instanceof List)) {
                return;
            }
            removeAds((List<?>) data);
        } catch (Throwable ignored) {
        }
    }

    private static Object readField(Object obj, String name) {
        try {
            Class<?> c = obj.getClass();
            int guard = 0;
            while (c != null && c != Object.class && guard++ < 5) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(obj);
                } catch (Throwable ignored) {
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // 从列表里删广告对象
    private static void removeAds(List<?> list) {
        try {
            int removed = 0;
            Iterator<?> it = list.iterator();
            while (it.hasNext()) {
                Object o = it.next();
                if (isAdObject(o)) {
                    try {
                        it.remove();
                        removed++;
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (removed > 0) {
                Xp.log("zh feed removed=" + removed);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isAdObject(Object o) {
        if (o == null) {
            return false;
        }
        String n = o.getClass().getName();
        if (n.contains("$")) {
            n = n.substring(0, n.indexOf('$'));
        }
        // 只认知乎自己的模型类
        if (!n.startsWith("com.zhihu.")) {
            return false;
        }
        return n.endsWith("FeedAdvert")
                || n.endsWith("ListAd")
                || n.endsWith("Advert")
                || n.endsWith(".Ad")
                || n.endsWith("AnswerListAd");
    }

    // ---------- 3. 回答列表广告 ----------
    private static int hookAnswerListAd(ClassLoader cl) {
        int count = 0;
        try {
            Class<?> helper = Class.forName(ZhProfile.CLS_MORPH_AD_HELPER, false, cl);
            for (Method m : helper.getDeclaredMethods()) {
                if (!m.getName().equals(ZhProfile.M_RESOLVE_ANSWER_AD)) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                }
                String id = "zh.answerlist." + m.getParameterCount();
                Xp.hook(id, m, SafeHook.view("zh.answerlist", new SafeHook.Body() {
                    @Override
                    public Object run(XposedInterface.Chain chain) throws Throwable {
                        return Boolean.FALSE;
                    }
                }));
                count++;
                Xp.log("ZhAd hook resolveAnswerAdParam");
            }
        } catch (Throwable t) {
            Xp.debug("zh answerlist fail :: " + t);
        }
        return count;
    }
}
