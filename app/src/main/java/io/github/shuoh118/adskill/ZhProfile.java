package io.github.shuoh118.adskill;

/*
 * 知乎策略。
 *
 * 规则来自开源项目 shatyuka/Zhiliao（2829 星，MIT）。
 * 只取去广告部分。
 */
public final class ZhProfile {

    public static final String PKG = "com.zhihu.android";

    // ---- 启动广告 ----
    // 类名不确定，用包名前缀 + 字段特征定位
    public static final String LAUNCH_AD_PKG = "com.zhihu.android.sdk.launchad.";
    public static final String LAUNCH_HELPER_PKG = "com.zhihu.android.app.util.";
    public static final String LAUNCH_HELPER_METHOD = "isShowLaunchAd";

    // ---- 信息流 ----
    public static final String CLS_PAGING_FRAGMENT =
            "com.zhihu.android.app.ui.fragment.paging.BasePagingFragment";
    public static final String M_PAGING_REFRESH = "postRefreshSucceed";

    // 广告数据模型（两个包名，版本不同）
    public static final String[] AD_MODEL_CLASSES = {
            "com.zhihu.android.api.model.FeedAdvert",
            "com.zhihu.android.api.model.ListAd",
            "com.zhihu.android.api.model.Advert",
            "com.zhihu.android.api.model.Ad",
            "com.zhihu.android.adbase.model.FeedAdvert",
            "com.zhihu.android.adbase.model.ListAd",
            "com.zhihu.android.adbase.model.Advert",
            "com.zhihu.android.adbase.model.Ad"
    };

    public static final String CLS_FEED_LIST = "com.zhihu.android.api.model.FeedList";
    public static final String FIELD_FEED_DATA = "data";

    // ---- 回答列表广告 ----
    public static final String CLS_ANSWER_LIST_AD =
            "com.zhihu.android.api.model.AnswerListAd";
    public static final String CLS_ANSWER_LIST_AD_ALT =
            "com.zhihu.android.adbase.model.AnswerListAd";
    public static final String CLS_MORPH_AD_HELPER =
            "com.zhihu.android.adbase.helper.MorphAdHelper";
    public static final String M_RESOLVE_ANSWER_AD = "resolveAnswerAdParam";

    // ---- 回答底部 WebView 广告 ----
    public static final String CLS_ANSWER_APPVIEW =
            "com.zhihu.android.answer.module.content.appview.AnswerAppView";
    public static final String CLS_APPVIEW = "com.zhihu.android.appview.AppView";
    public static final String M_INTERCEPT = "shouldInterceptRequest";

    // 广告 URL 路径特征
    public static final String[] AD_PATH = {
            "recommendations", "brand", "hotmodule", "advert", "sponsor"
    };

    private ZhProfile() {
    }
}
