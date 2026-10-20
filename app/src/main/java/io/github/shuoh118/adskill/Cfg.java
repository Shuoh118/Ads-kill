package io.github.shuoh118.adskill;

// 总配置
public final class Cfg {

    // 目标包名
    public static final String PKG = "com.netease.cloudmusic";

    // true 时每次 hook 都打日志，排查用
    public static final boolean DEBUG = false;

    // 安装摘要日志
    public static final boolean LOG = true;

    // ---------- 广告 ----------

    // 广告类方法级拦截：判定返回 false、加载展示跳过、取值返回 null
    public static final boolean KILL_AD_CLASSES = true;

    // 广告 SDK 入口拦截（穿山甲 / 优量汇 / 百度 / 快手 / Mintegral ...）
    public static final boolean KILL_AD_SDK = true;

    // 广告自定义 View 换成空 View
    public static final boolean KILL_AD_VIEWS = true;

    // 广告布局 inflate 后压成 0 尺寸
    public static final boolean KILL_AD_LAYOUTS = true;

    // 广告 View 直接被 addView 拒绝，根本不进视图树（最干净，无占位）
    public static final boolean KILL_AD_ADD = true;

    // 广告相关 MMKV / SharedPreferences 开关读成关闭
    public static final boolean KILL_PREFS = true;

    // 摇一摇广告：拦截广告类的传感器注册
    public static final boolean KILL_SHAKE = true;

    // 网络层拦截广告域名
    public static final boolean KILL_NET = false;

    // ---------- 开屏 ----------

    // 开屏广告处理
    public static final boolean KILL_SPLASH = true;

    // ---------- 本地会员 ----------

    // 本地会员（用于关掉会员广告位、去掉会员推广条）
    public static final boolean LOCAL_VIP = true;

    // 会员类型
    public static final int VIP_TYPE = 11;

    // 会员到期时间偏移（毫秒），默认 20 年
    public static final long VIP_EXPIRE_MS = 20L * 365L * 24L * 3600L * 1000L;

    // 会员标签文案
    public static final String VIP_LABEL = "黑胶VIP";

    // ---------- 性能 ----------

    // true = 主线程同步扫 dex
    public static final boolean SYNC_SCAN = false;

    private Cfg() {
    }
}
