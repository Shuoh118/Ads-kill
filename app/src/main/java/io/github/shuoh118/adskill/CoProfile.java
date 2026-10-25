package io.github.shuoh118.adskill;

/*
 * 酷安策略。
 *
 * 规则来自开源项目 coolapk-purifier（MIT），
 * 只取去广告部分，去掉设置入口和提示。
 */
public final class CoProfile {

    public static final String PKG = "com.coolapk.market";

    // 开屏页（三个版本用不同的名字）
    public static final String[] SPLASH_ACTIVITIES = {
            "com.coolapk.market.view.splash.SplashActivity",
            "com.coolapk.market.view.splash.SplashAdActivity",
            "com.coolapk.market.view.splash.FullScreenAdActivity"
    };

    // 开屏包名前缀，用于兜底匹配
    public static final String SPLASH_PACKAGE = "com.coolapk.market.view.splash";

    // 主页
    public static final String MAIN_ACTIVITY =
            "com.coolapk.market.view.main.MainActivity";

    // 信息流列表
    public static final String CLS_ENTITY_LIST =
            "com.coolapk.market.view.cardlist.EntityListFragment";

    // 广告辅助类
    public static final String CLS_ENTITY_AD_HELPER =
            "com.coolapk.market.view.ad.EntityAdHelper";

    // 数据实体
    public static final String CLS_ENTITY = "com.coolapk.market.model.Entity";

    // 回复区赞助卡（这个不能删，正常内容）
    public static final String REPLY_SPONSOR_KEEP = "feedDetailReplySponsorCard";

    private CoProfile() {
    }
}
