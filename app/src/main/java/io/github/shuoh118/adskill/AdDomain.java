package io.github.shuoh118.adskill;

import java.util.Locale;

/*
 * 通用广告域名表。
 *
 * === 4.1 重写原因 ===
 *
 * 旧版用 String.contains 比对，两个方向都错：
 *   1. 白名单里有 "log" / "beacon" 这种通用词，
 *      mobads-logs.baidu.com 因为含 "log" 被当成白名单放行 —— 漏拦。
 *   2. 黑名单里有 gtimg.cn / kuaishou.com / bytedance.com 这种整域，
 *      而作用域里有 QQ、微信、淘宝 —— 会把正常图片和接口一起改写 —— 误伤。
 *
 * 现在只做两件事：
 *   - 按 host 后缀精确匹配（a.b.com 命中 b.com，但不命中 notb.com）
 *   - 先排除当前 App 自家的域名，绝不动 App 自己的接口
 *
 * 路径类规则（CDN 上混着正常资源和广告素材）单独放在 HOST_PATHS，
 * 只有 host 和 path 同时命中才算广告。
 */
public final class AdDomain {

    /*
     * 纯广告 / 统计域名：host 等于它，或者是它的子域，才算命中。
     * 这里只能放"整个域都是广告业务"的域名，不放 CDN 和 App 接口。
     */
    private static final String[] HOSTS = {
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

            // ---- 腾讯 / 优量汇 ----
            "gdt.qq.com",
            "pgdt.gtimg.cn",
            "pingma.qq.com",
            "ad.qq.com",

            // ---- 字节 / 穿山甲 ----
            "pangolin-sdk-toutiao.com",
            "pangle.io",
            "pangle-sdk.net",
            "oceanengine.com",
            "gromore.com",

            // ---- 百度 ----
            "mobads.baidu.com",
            "mobads-logs.baidu.com",
            "pos.baidu.com",
            "cpro.baidu.com",
            "hm.baidu.com",

            // ---- 其他国内广告 / 统计平台 ----
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
            "appsflyer.com",
            "adjust.com"
    };

    /*
     * 当前 App 自家的域名：命中就立刻放行。
     *
     * 这些包本身的接口和 CDN 就在这些域上，
     * 一旦被改写成 127.0.0.1，App 直接白屏。
     */
    private static final String[][] OWN_HOSTS = {
            {"com.smile.gifmaker",
                    "kuaishou.com", "gifshow.com", "kwai.com", "kwaicdn.com", "yximgs.com"},
            {"com.kuaishou.nebula",
                    "kuaishou.com", "gifshow.com", "kwai.com", "kwaicdn.com", "yximgs.com"},
            {"com.ss.android.ugc.aweme",
                    "snssdk.com", "bytedance.com", "pstatp.com", "douyin.com", "amemv.com"},
            {"com.ss.android.ugc.aweme.lite",
                    "snssdk.com", "bytedance.com", "pstatp.com", "douyin.com", "amemv.com"},
            {"com.sina.weibo", "weibo.com", "weibo.cn", "sina.com.cn", "sinaimg.cn"},
            {"com.xingin.xhs", "xiaohongshu.com", "xhscdn.com", "xhs.cn"},
            {"com.zhihu.android", "zhihu.com", "zhimg.com"},
            {"com.coolapk.market", "coolapk.com", "coolapkmarket.com"},
            {"com.netease.cloudmusic", "music.126.net", "163.com", "127.net"},
            {"tv.danmaku.bili", "bilibili.com", "hdslb.com", "biliapi.net", "bilivideo.com"},
            {"com.taobao.taobao", "taobao.com", "tmall.com", "alicdn.com", "tbcdn.cn"},
            {"com.jingdong.app.mall", "jd.com", "360buyimg.com", "jd.hk"},
            {"com.xunmeng.pinduoduo", "pinduoduo.com", "yangkeduo.com", "pddpic.com"},
            {"com.eg.android.AlipayGphone", "alipay.com", "alipayobjects.com", "alipcdn.com"}
    };

    private AdDomain() {
    }

    /** URL 是不是广告请求（不知道包名时用这个） */
    public static boolean isAd(String url) {
        return isAd(url, null);
    }

    /** URL 是不是广告请求；pkg 用于排除 App 自家域名 */
    public static boolean isAd(String url, String pkg) {
        if (url == null || url.length() < 8) {
            return false;
        }
        String host = hostOf(url);
        if (host.isEmpty()) {
            return false;
        }
        // 1. 自家域名一律放行
        if (pkg != null && isOwnHost(host, pkg)) {
            return false;
        }
        // 2. 纯广告域名
        for (String h : HOSTS) {
            if (matchHost(host, h)) {
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

    // ---------- 内部 ----------

    private static boolean isOwnHost(String host, String pkg) {
        for (String[] row : OWN_HOSTS) {
            if (!row[0].equals(pkg)) {
                continue;
            }
            for (int i = 1; i < row.length; i++) {
                if (matchHost(host, row[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    /** host 等于 domain，或者是它的子域 */
    private static boolean matchHost(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    /** 从 URL 里取 host，取不到返回空串 */
    static String hostOf(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        String u = url.trim().toLowerCase(Locale.ROOT);
        int start = 0;
        int scheme = u.indexOf("://");
        if (scheme >= 0) {
            start = scheme + 3;
        } else if (u.startsWith("//")) {
            start = 2;
        }
        // 先切出 authority（到第一个 / ? # 为止），再依次去掉 userinfo 和端口
        int end = u.length();
        for (int i = start; i < u.length(); i++) {
            char c = u.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        if (end <= start) {
            return "";
        }
        String h = u.substring(start, end);
        int at = h.lastIndexOf('@');
        if (at >= 0) {
            h = h.substring(at + 1);
        }
        if (h.startsWith("[")) {
            // IPv6 字面量：[::1]:8080
            int close = h.indexOf(']');
            if (close > 0) {
                h = h.substring(0, close + 1);
            }
        } else {
            int colon = h.lastIndexOf(':');
            if (colon >= 0) {
                h = h.substring(0, colon);
            }
        }
        if (h.startsWith("www.")) {
            h = h.substring(4);
        }
        return h;
    }
}
