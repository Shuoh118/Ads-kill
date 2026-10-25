package io.github.shuoh118.adskill;

import android.app.Activity;
import android.content.Intent;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/*
 * 酷安开屏广告。
 *
 * 移植自 coolapk-purifier 的 SplashGate + SplashHooks。
 *
 * 做法：
 *   1. 记住启动阶段的 Activity
 *   2. 遇到三个开屏类名之一，直接 finish
 *   3. 见到 MainActivity 后停止工作（避免误伤后续页面）
 *
 * 不做提示，不弹 Toast。
 */
public final class CoSplash {

    private static volatile boolean mainSeen;
    private static volatile boolean firstSeen;

    private CoSplash() {
    }

    public static void install() {
        try {
            Method m = Activity.class.getDeclaredMethod("onCreate", android.os.Bundle.class);
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            Xp.hook("co.splash", m, SafeHook.splash("co.splash", new SafeHook.Body() {
                @Override
                public Object run(XposedInterface.Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    Object self = Xp.self(chain);
                    if (!(self instanceof Activity)) {
                        return result;
                    }
                    Activity act = (Activity) self;
                    String cls = act.getClass().getName();

                    if (!firstSeen) {
                        firstSeen = true;
                    }

                    // 主页：标记后停手
                    if (CoProfile.MAIN_ACTIVITY.equals(cls)) {
                        mainSeen = true;
                        return result;
                    }

                    // 已经见过主页，后续页面不动
                    if (mainSeen) {
                        return result;
                    }

                    if (isSplash(cls)) {
                        Xp.log("co splash finish: " + simple(cls));
                        jumpToMain(act, cls);
                    }
                    return result;
                }
            }));
        } catch (Throwable t) {
            Xp.log("CoSplash hook fail: " + t);
        }
    }

    // 三个已知类名，或落在 splash 包下且类名含 Splash
    private static boolean isSplash(String cls) {
        for (String s : CoProfile.SPLASH_ACTIVITIES) {
            if (s.equals(cls)) {
                return true;
            }
        }
        // 兜底：同包下类名含 Splash/Ad
        if (cls.startsWith(CoProfile.SPLASH_PACKAGE + ".")) {
            String simple = simple(cls).toLowerCase();
            return simple.contains("splash") || simple.contains("fullscreenad");
        }
        return false;
    }

    // 拉主页并结束开屏页
    private static void jumpToMain(Activity act, String cls) {
        try {
            if (CoProfile.MAIN_ACTIVITY.equals(cls)) {
                return;
            }
            Intent intent = new Intent();
            intent.setClassName(act.getPackageName(), CoProfile.MAIN_ACTIVITY);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            act.startActivity(intent);
            act.overridePendingTransition(0, 0);
            act.finish();
        } catch (Throwable t) {
            Xp.debug("co jump fail :: " + t);
            try {
                act.finish();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String simple(String fqcn) {
        int i = fqcn.lastIndexOf('.');
        return i < 0 ? fqcn : fqcn.substring(i + 1);
    }
}
