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
        if (installed) {
            return;
        }
        String real = pkg != null ? pkg : App.packageName();
        if (real == null || !Cfg.PKG.equals(real)) {
            return;
        }
        if (cl == null) {
            cl = Entry.class.getClassLoader();
        }
        synchronized (Bootstrap.class) {
            if (installed) {
                return;
            }
            installed = true;
        }
        Xp.log("install begin pkg=" + real + " pid=" + android.os.Process.myPid());

        if (Cfg.KILL_AD_SDK) {
            try {
                SdkHook.install(cl);
            } catch (Throwable t) {
                Xp.err("SdkHook failed", t);
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

        if (Cfg.KILL_SPLASH) {
            /*
             * 只装这两个。SplashHook 那个"按类名 finish 页面"的版本已停用：
             * 一旦主页解析错，就会把正常页面 finish 掉，表现就是启动黑屏。
             * 现在统一走"文案识别 + 摘视图"，不需要猜类名。
             */
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
                        VipHook.install(loader);
                    }
                    Xp.log("heavy hooks done");
                } catch (Throwable t) {
                    Xp.err("heavy hooks failed", t);
                }
            }
        };

        if (Cfg.SYNC_SCAN) {
            heavy.run();
        } else {
            Thread t = new Thread(heavy, "ncm-adfree-scan");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 2);
            t.start();
        }
    }
}
