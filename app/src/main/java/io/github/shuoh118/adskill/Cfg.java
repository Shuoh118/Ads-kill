package io.github.shuoh118.adskill;

// 总配置
public final class Cfg {

    /*
     * 包名 → 策略的对照表在 Profile.of()，scope.list 由它对齐。
     * 这里不再维护第二份包名列表：4.0 时 Cfg.PKGS 只写了 3 个包，
     * scope.list 却有 26 个，两处早就不同步了。
     */

    // true 时每次 hook 都打日志，排查用
    public static final boolean DEBUG = true;

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

    /*
     * 快手广告拦截策略。
     *
     * KS_DATA  —— 数据层：让 isAd() 返回 false、getAdInfo() 返回 null
     * KS_VIEW  —— 视图层按 tag/id 隐藏（已停用，会误伤粉丝数）
     * KS_WATCH —— 角标扫描（已停用，会误伤获赞栏）
     *
     * 视图层两个方案都会把"统计数字"当成广告角标连带隐藏，
     * 而快手的数字栏和广告卡片结构无法可靠区分，所以只保留数据层。
     */
    public static final boolean KS_DATA = false;
    public static final boolean KS_VIEW = false;
    public static final boolean KS_WATCH = false;

    /*
     * 快手：FastSkip（拦广告加载 + 立刻回调失败）
     *
     * 默认关闭。它靠"类名含 ad + 方法名以 load 开头"判定，
     * 但快手的统计字段也走 load 方法（loadCount / loadStat / loadProfile），
     * 只要类名里恰好带 ad 就会被拦成 null，
     * 表现就是点赞/收藏/评论/粉丝数随机消失。
     *
     * 只有开屏卡住且确认不误伤时才打开。
     */
    public static final boolean KS_FASTSKIP = false;

    /*
     * 快手：SdkHook（拦广告 SDK 的 load/show/isReady）
     *
     * 默认关闭。快手 App 不用外部联盟 SDK，
     * 但这个 hook 会去加载 com.kuaishou.ad.* 一类，
     * 如果类名恰好和统计数据链有关，同样会把方法拦成 false。
     */
    public static final boolean KS_SDKHOOK = false;

    /*
     * 快手：KsTimer（压缩 2~10 秒延时）
     *
     * 默认关闭。它会把手柄延时压到 1ms，
     * 如果统计数据靠延时任务加载，也会被提前触发导致空数据。
     */
    public static final boolean KS_TIMER = false;

    // 网络层拦截广告域名
    public static final boolean KILL_NET = false;

    /*
     * 通用应用（无针对性规则的那些）是否启用视图层过滤。
     *
     * 只按"类名含 ad 词 + View 后缀"判定，命中才 detach，
     * 对混淆过的 App 基本不生效，对没混淆的能去掉一部分硬编码广告位。
     * 如果某个 App 出现异常，把它设成 false 重新打包即可。
     */
    public static final boolean GENERIC_VIEWS = true;

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
