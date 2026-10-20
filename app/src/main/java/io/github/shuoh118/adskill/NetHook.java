package io.github.shuoh118.adskill;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

// 网络层拦截
public final class NetHook {

    /*
     * 广告域名。只列确定是广告投放的，别把自家音乐 CDN 拉进来。
     * 用 contains 匹配，因为很多是子域。
     */
    private static final String[] AD_HOST = {
            // 网易自家广告位
            "admusicpic.music.126.net",
            "iadmat.nosdn.127.net",
            "iadmusicmat.music.126.net",
            "iadmatmusic.127.net",
            "adapi.music.126.net",
            "iad.g.163.com",
            // 穿山甲 / 巨量
            "pangolin-sdk-toutiao",
            "ad.oceanengine.com",
            "pglstatp-toutiao",
            "gromore",
            // 优量汇 / 广点通
            "gdt.qq.com",
            "pgdt.gtimg.cn",
            "ads.service.kugou.com",
            // 百度
            "mobads.baidu.com",
            "mobads-logs.baidu.com",
            // 快手
            "adkwai.com",
            "open.kwaixiaodian.com",
            // Mintegral
            "mbridge.mintegral.com",
            // Sigmob
            "sigmob.com",
            // TopOn
            "anythinktech.com"
    };

    private NetHook() {
    }

    public static void install(ClassLoader cl) {
        /*
         * 1.0.3 黑屏的第五个原因：这一层被默认开启了。
         *
         * 拦截全局 URL 构造器，命中广告域名就抛 MalformedURLException。
         * 问题在于：广告 SDK 内部会 catch 这个异常并重试，
         * 而宿主页面在等"广告加载完成"这个回调。
         * 回调永远不来 -> 开屏页一直转圈 -> 黑屏。
         *
         * 所以这一层默认关闭。需要时在 Cfg 里手动打开。
         */
        if (!Cfg.KILL_NET) {
            Xp.log("NetHook skipped (Cfg.KILL_NET=false)");
            return;
        }
        int n = 0;
        n += hookUrl();
        n += hookUri();
        Xp.log("NetHook methods=" + n);
    }

    private static int hookUrl() {
        int count = 0;
        try {
            Constructor<?>[] ctors = java.net.URL.class.getDeclaredConstructors();
            for (Constructor<?> c : ctors) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 0 || p[0] != String.class) {
                    continue;
                }
                c.setAccessible(true);
                String id = "net.url." + p.length;
                Xp.hook(id, c, new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        Object a0 = Xp.arg(chain, 0);
                        if (a0 instanceof String && isAdUrl((String) a0)) {
                            Xp.debug("blocked url: " + shorten((String) a0));
                            throw new java.net.MalformedURLException("blocked");
                        }
                        return chain.proceed();
                    }
                });
                count++;
            }
        } catch (Throwable t) {
            Xp.debug("url hook fail :: " + t);
        }
        return count;
    }

    private static int hookUri() {
        try {
            Method m = java.net.URI.class.getDeclaredMethod("create", String.class);
            m.setAccessible(true);
            Xp.hook("net.uri.create", m, new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object a0 = Xp.arg(chain, 0);
                    if (a0 instanceof String && isAdUrl((String) a0)) {
                        Xp.debug("blocked uri: " + shorten((String) a0));
                        throw new IllegalArgumentException("blocked");
                    }
                    return chain.proceed();
                }
            });
            return 1;
        } catch (Throwable t) {
            Xp.debug("uri hook fail :: " + t);
            return 0;
        }
    }

    public static boolean isAdUrl(String url) {
        if (url == null || url.length() < 8) {
            return false;
        }
        String low = url.toLowerCase();
        for (String h : AD_HOST) {
            if (low.contains(h)) {
                return true;
            }
        }
        return false;
    }

    private static String shorten(String url) {
        return url.length() <= 90 ? url : url.substring(0, 90) + "...";
    }
}
