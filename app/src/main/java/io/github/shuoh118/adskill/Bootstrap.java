package io.github.shuoh118.adskill;

import android.os.Handler;
import android.os.Looper;

// 装载调度
public final class Bootstrap {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int MAX_RETRY = 15;
    private static final long RETRY_MS = 300L;

    private static volatile boolean installed;
    private static volatile boolean scheduled;

    private Bootstrap() {
    }

    public static void schedule() {
        if (scheduled) {
            return;
        }
        scheduled = true;
        retry(0);
    }

    private static void retry(final int attempt) {
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (installed) {
                    return;
                }
                String pkg = App.packageName();
                ClassLoader cl = null;
                try {
                    Object app = App.application();
                    if (app != null) {
                        cl = app.getClass().getClassLoader();
                    }
                } catch (Throwable ignored) {
                }
                if (pkg != null) {
                    start(pkg, cl);
                    if (installed) {
                        return;
                    }
                }
                if (attempt < MAX_RETRY) {
                    retry(attempt + 1);
                } else {
                    Xp.log("bootstrap gave up pkg=" + pkg);
                }
            }
        }, attempt == 0 ? 0L : RETRY_MS);
    }

    public static void start(String pkg, ClassLoader cl) {
        start(pkg, cl, null);
    }

    public static void start(String pkg, ClassLoader cl, Object param) {
        if (installed) {
            return;
        }
        String real = pkg != null ? pkg : App.packageName();
        Profile profile = Profile.of(real);
        if (profile == null) {
            return;
        }
        // 必须是目标应用的 ClassLoader，不能用模块自己的
        if (cl == null || !looksLikeTarget(cl, real)) {
            cl = AppCl.find(real, param);
        }
        if (cl == null) {
            Xp.log("no target ClassLoader, class scan will fail");
        }
        synchronized (Bootstrap.class) {
            if (installed) {
                return;
            }
            installed = true;
        }
        Ctx.set(profile);
        SplashWindow.open();
        SkipKiller.open();

        // 通用层：广告域名拦截，对所有应用生效
        try {
            AdUrlHook.install();
        } catch (Throwable t) {
            Xp.err("AdUrlHook failed", t);
        }
        Xp.log("install begin pkg=" + real + " kind=" + profile.kind
                + " pid=" + android.os.Process.myPid());

        // 快手走独立路径，不用网易云那套
        if (profile.isGeneric()) {
            installGeneric(cl, real);
        } else if (profile.isKuaishou()) {
            installKuaishou(cl, profile);
        } else if (profile.isCoolapk()) {
            installCoolapk(cl, profile);
        } else if (profile.isZhihu()) {
            installZhihu(cl, profile);
        } else {
            installNetease(cl, profile);
        }
    }

    /*
     * 这几个是 IM / 支付类，界面一旦被误摘就是聊天记录或付款页出问题，
     * 代价远大于去掉一个广告位，所以只做域名 + SDK + 开屏，不做通用视图过滤。
     */
    private static final String[] VIEW_SKIP = {
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "com.eg.android.AlipayGphone"
    };

    private static boolean skipViewHook(String pkg) {
        for (String p : VIEW_SKIP) {
            if (p.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    /*
     * 通用应用：没有针对性规则的那些（scope.list 里其余 30 多个包）。
     *
     * 4.1 之前这里只装了域名拦截，等于"报了支持其实没干活"。
     * 现在装四层跨 App 也成立的通用规则：
     *   1. 广告 SDK 入口 —— 穿山甲 / 优量汇 / 百度 / 快手联盟 / Mintegral ...
     *      绝大多数 App 的开屏、插屏、激励视频都走这些 SDK，不依赖 App 内部类名
     *   2. 开屏看门狗 —— 文案识别 + 大尺寸广告 View，6 秒窗口内有效
     *   3. 摇一摇 —— 调用栈里出现广告类才拦传感器，不碰正常计步/横竖屏
     *   4. 视图层 —— 类名含 ad 词的 View 直接 detach，混淆过的 App 自然无效
     *
     * 原则：宁可少杀，不制造崩溃。通用 App 里不装 AdHook 那种按类名扫全包的钩子。
     */
    private static void installGeneric(ClassLoader cl, String pkg) {
        if (Cfg.KILL_AD_SDK) {
            try {
                // 通用 App 用安全模式：只改 isReady 这类查询，不吞 load/show
                SdkHook.installSafe(cl);
                Xp.log("generic sdk ok");
            } catch (Throwable t) {
                Xp.err("generic SdkHook failed", t);
            }
        }
        if (Cfg.KILL_SPLASH) {
            try {
                SplashWatch.install();
                Xp.log("generic splashwatch ok");
            } catch (Throwable t) {
                Xp.err("generic SplashWatch failed", t);
            }
        }
        if (Cfg.KILL_SHAKE) {
            try {
                ShakeBlock.install(cl);
                Xp.log("generic shake ok");
            } catch (Throwable t) {
                Xp.err("generic ShakeBlock failed", t);
            }
        }
        if (Cfg.GENERIC_VIEWS && !skipViewHook(pkg)) {
            try {
                ViewHook.install();
                Xp.log("generic view ok");
            } catch (Throwable t) {
                Xp.err("generic ViewHook failed", t);
            }
        }
    }

    /*
     * 快手：数据层为主。
     *   广告是自家平台产出的数据，带 isAd 标记混在信息流里，
     *   所以先让 isAd() 返回 false，再兜住视图层。
     */
    private static void installKuaishou(ClassLoader cl, Profile p) {
        // 视图层方案已移除：会误伤粉丝数、获赞栏等统计数字

        if (Cfg.KILL_SPLASH) {
            try {
                SplashWatch.install();
                Xp.log("ks splashwatch ok");
            } catch (Throwable t) {
                Xp.err("SplashWatch failed", t);
            }
        }

        // 快手开屏：摘掉覆盖在主界面上的一层
        try {
            KsSplash.install();
            Xp.log("ks splash ok");
        } catch (Throwable t) {
            Xp.err("KsSplash failed", t);
        }

        // 快手开屏：压缩倒计时（默认关闭，会误伤延时加载的数据）
        if (Cfg.KS_TIMER) {
            try {
                KsTimer.install();
                Xp.log("ks timer ok");
            } catch (Throwable t) {
                Xp.err("KsTimer failed", t);
            }
        }

        // 角标扫描已移除：会把数字栏连带隐藏

        if (Cfg.KILL_AD_SDK && Cfg.KS_SDKHOOK) {
            try {
                SdkHook.installFiltered(cl, p.sdkPrefix);
            } catch (Throwable t) {
                Xp.err("SdkHook failed", t);
            }
        }
        // FastSkip 默认关闭，会误伤统计数据的 load 方法
        if (Cfg.KS_FASTSKIP) {
            try {
                FastSkip.install(cl);
            } catch (Throwable t) {
                Xp.err("FastSkip failed", t);
            }
        }

        final ClassLoader loader = cl;
        Runnable heavy = new Runnable() {
            @Override
            public void run() {
                try {
                    if (Cfg.KS_DATA) {
                        KsHook.install(loader);
                    }
                    Xp.log("ks hooks done");
                } catch (Throwable t) {
                    Xp.err("KsHook failed", t);
                }
            }
        };
        runHeavy(heavy);
    }

    /*
     * 酷安：开屏 + 信息流。
     * 规则移植自开源项目 coolapk-purifier（MIT）。
     * 不做设置页，不弹任何提示。
     */
    private static void installCoolapk(ClassLoader cl, Profile p) {
        try {
            CoSplash.install();
            Xp.log("co splash ok");
        } catch (Throwable t) {
            Xp.err("CoSplash failed", t);
        }

        final ClassLoader loader = cl;
        Runnable heavy = new Runnable() {
            @Override
            public void run() {
                try {
                    CoFeed.install(loader);
                    Xp.log("co feed done");
                } catch (Throwable t) {
                    Xp.err("CoFeed failed", t);
                }
            }
        };
        runHeavy(heavy);
    }

    /*
     * 知乎：启动广告 + 信息流 + 回答列表。
     * 规则移植自开源项目 Zhiliao（MIT）。
     */
    private static void installZhihu(ClassLoader cl, Profile p) {
        final ClassLoader loader = cl;
        Runnable heavy = new Runnable() {
            @Override
            public void run() {
                try {
                    ZhAd.install(loader);
                    Xp.log("zh hooks done");
                } catch (Throwable t) {
                    Xp.err("ZhAd failed", t);
                }
            }
        };
        runHeavy(heavy);
    }

    /*
     * 网易云：SDK 层 + 文案识别。
     *   广告是第三方 SDK 塞进来的独立 Activity / View，类名带 ad。
     */
    private static void installNetease(ClassLoader cl, Profile p) {
        if (Cfg.KILL_AD_SDK) {
            try {
                SdkHook.install(cl);
            } catch (Throwable t) {
                Xp.err("SdkHook failed", t);
            }
            // 立刻回掉失败回调，避免开屏等超时
            try {
                FastSkip.install(cl);
            } catch (Throwable t) {
                Xp.err("FastSkip failed", t);
            }
            try {
                NetHook.install(cl);
            } catch (Throwable t) {
                Xp.err("NetHook failed", t);
            }
        }
        try {
            ViewHook.install();
            Xp.log("view hooks ok");
        } catch (Throwable t) {
            Xp.err("ViewHook failed", t);
        }
        try {
            ListHook.install();
            Xp.log("list hooks ok");
        } catch (Throwable t) {
            Xp.err("ListHook failed", t);
        }
        if (Cfg.KILL_SHAKE) {
            try {
                ShakeBlock.install(cl);
            } catch (Throwable t) {
                Xp.err("ShakeBlock failed", t);
            }
        }
        // 启动页卡住时主动放行
        try {
            BootTimer.install();
            Xp.log("boottimer ok");
        } catch (Throwable t) {
            Xp.err("BootTimer failed", t);
        }

        if (Cfg.KILL_SPLASH) {
            try {
                SplashKill.install();
                Xp.log("splashkill hooks ok");
            } catch (Throwable t) {
                Xp.err("SplashKill failed", t);
            }
            try {
                SplashWatch.install();
                Xp.log("splashwatch hooks ok");
            } catch (Throwable t) {
                Xp.err("SplashWatch failed", t);
            }
        }

        final ClassLoader loader = cl;
        Runnable heavy = new Runnable() {
            @Override
            public void run() {
                try {
                    if (Cfg.KILL_PREFS) {
                        PrefsHook.install(loader);
                    }
                    if (Cfg.KILL_AD_CLASSES) {
                        AdHook.install(loader);
                    }
                    if (Cfg.LOCAL_VIP) {
                        // 不依赖 dex 扫描，直接 hook 反序列化
                        VipField.install(loader);
                        // 同时尝试类扫描方案，扫到了就用
                        VipHook.install(loader);
                    }
                    Xp.log("heavy hooks done");
                } catch (Throwable t) {
                    Xp.err("heavy hooks failed", t);
                }
            }
        };
        runHeavy(heavy);
    }

    // 粗验一下这个 ClassLoader 是不是目标应用的
    private static boolean looksLikeTarget(ClassLoader cl, String pkg) {
        if (cl == null) {
            return false;
        }
        if (cl == Bootstrap.class.getClassLoader()) {
            return false;
        }
        return true;
    }

    private static void runHeavy(Runnable task) {
        if (Cfg.SYNC_SCAN) {
            task.run();
        } else {
            Thread t = new Thread(task, "adskill-scan");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 2);
            t.start();
        }
    }
}
