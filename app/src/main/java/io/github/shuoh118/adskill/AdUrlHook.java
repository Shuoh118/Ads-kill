package io.github.shuoh118.adskill;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;

import io.github.libxposed.api.XposedInterface;

/*
 * 通用广告 URL 拦截。
 *
 * 思路来自 AdBlocker_Reborn（GitHub 170 星，Java 开源）。
 *
 * === 和旧 NetHook 的区别 ===
 *
 * 旧版 NetHook 命中广告域名后 **抛异常**，
 * SDK 会重试、卡住，反而导致开屏变慢，所以默认关掉了。
 *
 * AdBlocker 的做法是 **把 URL 改写成 127.0.0.1**：
 *   SDK 收到连接拒绝，走正常的"无广告"分支，
 *   立刻回调失败，不卡不等。
 *
 * 这就是它更可靠的原因。
 *
 * === 拦哪里 ===
 *
 *   java.net.URL        构造器（最底层，OkHttp/HttpURLConnection 都经过）
 *   java.net.URI        create
 *
 * 只改参数，不改返回值，所以不会崩。
 */
public final class AdUrlHook {

    /** 是否启用。默认开，因为改写比抛异常安全 */
    public static final boolean ENABLED = true;

    private AdUrlHook() {
    }

    public static void install() {
        if (!ENABLED) {
            Xp.log("AdUrlHook disabled");
            return;
        }
        int n = 0;
        n += hookUrl();
        n += hookUri();
        Xp.log("AdUrlHook methods=" + n);
    }

    // ---------- java.net.URL ----------
    private static int hookUrl() {
        int count = 0;
        try {
            Constructor<?>[] ctors = URL.class.getDeclaredConstructors();
            for (final Constructor<?> c : ctors) {
                Class<?>[] p = c.getParameterTypes();
                // 只挂第一个参数是 String 的
                if (p.length == 0 || p[0] != String.class) {
                    continue;
                }
                try {
                    c.setAccessible(true);
                } catch (Throwable ignored) {
                }
                final int idx = 0;
                String id = "adurl.ctor." + p.length;
                Xp.hook(id, c, SafeHook.view("adurl.ctor", new SafeHook.Body() {
                    @Override
                    public Object run(XposedInterface.Chain chain) throws Throwable {
                        Object[] args = Xp.args(chain).toArray();
                        if (args.length > idx && args[idx] instanceof String) {
                            String u = (String) args[idx];
                            if (AdDomain.isAd(u)) {
                                Xp.log("ad url blocked: " + shortUrl(u));
                                args[idx] = AdDomain.toLocal(u);
                                return chain.proceed(args);
                            }
                        }
                        return chain.proceed();
                    }
                }));
                count++;
            }
        } catch (Throwable t) {
            Xp.log("AdUrlHook URL fail: " + t);
        }
        return count;
    }

    // ---------- java.net.URI ----------
    private static int hookUri() {
        try {
            Method m = URI.class.getDeclaredMethod("create", String.class);
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            Xp.hook("adurl.uri", m, SafeHook.view("adurl.uri", new SafeHook.Body() {
                @Override
                public Object run(XposedInterface.Chain chain) throws Throwable {
                    Object a = Xp.arg(chain, 0);
                    if (a instanceof String) {
                        String u = (String) a;
                        if (AdDomain.isAd(u)) {
                            Xp.log("ad uri blocked: " + shortUrl(u));
                            Object[] args = Xp.args(chain).toArray();
                            args[0] = AdDomain.toLocal(u);
                            return chain.proceed(args);
                        }
                    }
                    return chain.proceed();
                }
            }));
            return 1;
        } catch (Throwable t) {
            Xp.debug("AdUrlHook URI fail :: " + t);
            return 0;
        }
    }

    private static String shortUrl(String u) {
        return u.length() <= 80 ? u : u.substring(0, 80) + "...";
    }
}
