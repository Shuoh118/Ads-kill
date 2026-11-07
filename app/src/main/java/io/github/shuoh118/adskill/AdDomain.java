package io.github.shuoh118.adskill;

/*
 * 通用广告域名表。
 *
 * 思路来自 AdBlocker_Reborn：不依赖类名，只看 URL。
 * 但它的 hosts 库偏欧美，国内覆盖不够，这里手工整理。
 *
 * 用法：URL 命中列表里的域名就改写成本地地址，
 * SDK 拿到连接失败后会走"无广告"分支，不会卡死。
 */
public final class AdDomain {

    /*
     * 广告 / 统计域名。按平台分组，方便以后增删。
     */
    public static final String[] HOSTS = {
            // ---- 友盟 / 阿里系 ----
            "umeng.com",
            "umengcloud.com",
            "umeng.co",
            "mmstat.com",
            "cnzz.com",
            "cnzz.net",
            "alimama.com",
            "alimama.cn",
            "tanx.com",
            "mmstat.com",
            "aliapp.org",

            // ---- 腾讯 / 优量汇 ----
            "gdt.qq.com",
            "pgdt.gtimg.cn",
            "pingma.qq.com",
            "gtimg.cn",
            "qq.com/gdt",
            "ad.qq.com",

            // ---- 字节 / 穿山甲 ----
            "pangolin-sdk-toutiao.com",
            "pangle.io",
            "oceanengine.com",
            "gromore.com",
            "bytedance.com",
            "snssdk.com",
            "toutiao.com/api/ad",
            "pstatp.com",

            // ---- 快手 ----
            "kuaishou.com",
            "kwai.com",
            "kwaicdn.com",
            "gifshow.com",
            "yximgs.com",

            // ---- 百度 ----
            "mobads.baidu.com",
            "mobads-logs.baidu.com",
            "pos.baidu.com",
            "cpro.baidu.com",
            "hm.baidu.com",

            // ---- 其他国内平台 ----
            "miaozhen.com",
            "admaster.net",
            "growingio.com",
            "sensorsdata.cn",
            "getui.com",
            "igexin.com",
            "jpush.cn",
            "talkingdata.com",
            "adhub.com",
            "adview.cn",
            "domob.cn",
            "youmi.net",
            "youmiad.com",
            "51.la",
            "irs01.com",
            "irs01.net",
            "irs09.com",

            // ---- 国际主流 ----
            "doubleclick.net",
            "doubleclick.com",
            "googlesyndication.com",
            "googleadservices.com",
            "admob.com",
            "applovin.com",
            "unityads.unity3d.com",
            "vungle.com",
            "chartboost.com",
            "ironsrc.com",
            "mintegral.com",
            "mobvista.com",
            "sigmob.com",
            "anythinktech.com",
            "tradplusad.com",
            "windmill.com",
            "appsflyer.com",
            "adjust.com",
            "facebook.com/audience_network",
            "graph.facebook.com/ads"
    };

    /*
     * 白名单：这些不是广告，绝不能拦。
     * 音乐 CDN、图片、接口都在这里。
     */
    public static final String[] WHITE = {
            "music.126.net", "nosdn.127.net", "126.net/imgextra",
            "p1.music.126.net", "p2.music.126.net",
            "gifshow.com/rest", "kuaishou.com/rest",
            "cookieless", "beacon", "log",
            ".mp3", ".mp4", ".flac", ".m4a", ".webp", ".jpg", ".png",
            "lyric", "artist", "album", "playlist", "song"
    };

    private AdDomain() {
    }

    /** URL 是不是广告请求 */
    public static boolean isAd(String url) {
        if (url == null || url.length() < 8) {
            return false;
        }
        String low = url.toLowerCase();
        // 先过白名单
        for (String w : WHITE) {
            if (low.contains(w)) {
                return false;
            }
        }
        for (String h : HOSTS) {
            if (low.contains(h)) {
                return true;
            }
        }
        return false;
    }

    /** 把广告 URL 改写成不可达地址 */
    public static String toLocal(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("https")) {
            return "https://127.0.0.1";
        }
        return "http://127.0.0.1";
    }
}
