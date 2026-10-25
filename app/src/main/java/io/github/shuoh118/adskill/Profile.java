package io.github.shuoh118.adskill;

/*
 * 每个应用一套独立的去广告策略。
 *
 * 为什么必须分开：
 *   网易云 —— 广告来自第三方 SDK（穿山甲、优量汇），包在独立 Activity 里，
 *             类名带 ad，开屏有"跳过广告 N"。
 *   快手   —— 广告是自家平台产出的，数据里就带 isAd 字段，
 *             混在信息流里当普通条目渲染，没有跳过按钮。
 *
 * 两者的判定依据、拦截点、文案完全不一样，用一套规则必然一边失灵。
 */
public final class Profile {

    // 应用类型
    public enum Kind {
        NETEASE,
        KUAISHOU,
        COOLAPK
    }

    public final Kind kind;
    public final String pkg;
    // 广告 SDK 包前缀
    public final String[] sdkPrefix;
    // 广告文案
    public final String[] adText;
    // 不能误伤的正常文案
    public final String[] safeText;
    // 是否启用开屏文案识别（快手没有跳过按钮，关掉）
    public final boolean useSplashText;
    // 是否启用数据层过滤（快手专用）
    public final boolean useDataFilter;

    private Profile(Kind kind, String pkg, String[] sdk, String[] adText, String[] safeText,
                    boolean useSplashText, boolean useDataFilter) {
        this.kind = kind;
        this.pkg = pkg;
        this.sdkPrefix = sdk;
        this.adText = adText;
        this.safeText = safeText;
        this.useSplashText = useSplashText;
        this.useDataFilter = useDataFilter;
    }

    // ---------- 网易云 ----------
    public static final Profile NETEASE = new Profile(
            Kind.NETEASE,
            "com.netease.cloudmusic",
            new String[]{
                    "com.bytedance.sdk.openadsdk",
                    "com.byted.pangle",
                    "com.qq.e.",
                    "com.tencent.gdt",
                    "com.baidu.mobads",
                    "com.kwad.",
                    "com.mbridge.",
                    "com.mintegral.",
                    "com.sigmob.",
                    "com.anythink."
            },
            new String[]{
                    "跳过广告", "跳转详情", "跳转到详情", "跳转第三方", "跳转至第三方",
                    "第三方应用", "了解详情", "点击查看详情", "查看详情", "点击跳转"
            },
            new String[]{
                    "跳过前奏", "跳过片头", "跳过间奏", "跳过尾奏",
                    "广告合作", "广告位招租"
            },
            true,
            false
    );

    // ---------- 快手 ----------
    public static final Profile KUAISHOU = new Profile(
            Kind.KUAISHOU,
            "com.smile.gifmaker",
            new String[]{
                    // 快手自家广告
                    "com.kuaishou.ad",
                    "com.kuaishou.ads",
                    "com.kuaishou.advert",
                    "com.kuaishou.union",
                    "com.kuaishou.commercial",
                    "com.kwai.ad",
                    "com.kwai.monitor",
                    "com.yxcorp.kuaishou.addfp",
                    // 快手联盟对外 SDK
                    "com.kwad.sdk"
            },
            new String[]{
                    // 快手广告标签
                    "广告", "推广", "赞助", "品牌广告", "了解更多",
                    "立即下载", "点击下载", "一键安装", "去应用"
            },
            new String[]{
                    // 创作者相关，绝不能误伤
                    "推广我的作品", "作品推广", "推广中心", "推广数据",
                    "创作者", "创作者中心", "广告设置", "广告偏好",
                    "个性化广告", "关闭广告", "广告合作", "广告反馈"
            },
            false,   // 快手不靠文案识别，走数据层
            true
    );

    // ---------- 快手极速版 ----------
    public static final Profile KUAISHOU_LITE = new Profile(
            Kind.KUAISHOU,
            "com.kuaishou.nebula",
            KUAISHOU.sdkPrefix,
            KUAISHOU.adText,
            KUAISHOU.safeText,
            false,
            true
    );

    // ---------- 酷安 ----------
    public static final Profile COOLAPK = new Profile(
            Kind.COOLAPK,
            "com.coolapk.market",
            new String[]{"com.coolapk."},
            new String[]{"广告", "赞助"},
            new String[]{"feedDetailReplySponsorCard"},
            false,
            true
    );

    // 按包名取策略
    public static Profile of(String pkg) {
        if (pkg == null) {
            return null;
        }
        if (pkg.equals(NETEASE.pkg)) {
            return NETEASE;
        }
        if (pkg.equals(KUAISHOU.pkg)) {
            return KUAISHOU;
        }
        if (pkg.equals(KUAISHOU_LITE.pkg)) {
            return KUAISHOU_LITE;
        }
        if (pkg.equals(COOLAPK.pkg)) {
            return COOLAPK;
        }
        return null;
    }

    public boolean isKuaishou() {
        return kind == Kind.KUAISHOU;
    }

    public boolean isCoolapk() {
        return kind == Kind.COOLAPK;
    }

}
