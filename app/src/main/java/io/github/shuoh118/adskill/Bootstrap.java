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
        Xp.log("install begin pkg=" + real + " kind=" + profile.kind
                + " pid=" + android.os.Process.myPid());

        // 快手走独立路径，不用网易云那套
        if (profile.isKuaishou()) {
            installKuaishou(cl, profile);
        } else {
            installNetease(cl, profile);
        }
    }

    /*
     * 快手：数据层为主。
     *   广告是自家平台产出的数据，带 isAd 标记混在信息流里，
     *   所以先让 isAd() 返回 false，再兜住视图层。
     */
    private static void installKuaishou(ClassLoader cl, Profile p) {
        try {
            VHook.install();
            Xp.log("ks view hooks ok");
        } catch (Throwable t) {
            Xp.err("VHook failed", t);
        }

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

        // 快手开屏：压缩倒计时
        try {
            KsTimer.install();
            Xp.log("ks timer ok");
        } catch (Throwable t) {
            Xp.err("KsTimer failed", t);
        }

        // 信息流广告：靠"广告"角标识别，不依赖任何类名
        try {
            KsWatch.install();
            Xp.log("ks watch ok");
        } catch (Throwable t) {
            Xp.err("KsWatch failed", t);
        }

        if (Cfg.KILL_AD_SDK) {
            try {
                SdkHook.installFiltered(cl, p.sdkPrefix);
            } catch (Throwable t) {
                Xp.err("SdkHook failed", t);
            }
            // 广告不加载了，但 App 在等失败回调，这里立刻回掉，避免开屏卡住
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
                    KsHook.install(loader);
                    Xp.log("ks hooks done");
                } catch (Throwable t) {
                    Xp.err("KsHook failed", t);
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
