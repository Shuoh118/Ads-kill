package io.github.shuoh118.adskill;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

// 广告 SDK 入口拦截
public final class SdkHook {

    private static final String[] ENTRY = {
            // 穿山甲
            "com.bytedance.sdk.openadsdk.TTAdNative",
            "com.bytedance.sdk.openadsdk.TTAdSdk",
            "com.bytedance.sdk.openadsdk.TTSplashAd",
            "com.bytedance.sdk.openadsdk.TTNativeAd",
            "com.bytedance.sdk.openadsdk.TTFeedAd",
            "com.bytedance.sdk.openadsdk.TTRewardVideoAd",
            "com.bytedance.sdk.openadsdk.TTAdManager",
            "com.qq.e.ads.splash.SplashAD",
            "com.qq.e.ads.nativ.NativeUnifiedAD",
            "com.qq.e.ads.interstitial.InterstitialAD",
            "com.qq.e.ads.rewardvideo.RewardVideoAD",
            "com.qq.e.comm.managers.GDTAdSdk",
            "com.baidu.mobads.sdk.api.SplashAd",
            "com.baidu.mobads.sdk.api.BDAdView",
            // 快手广告联盟（外部接入方用的 SDK）
            "com.kwad.sdk.api.KsSplashScreenAd",
            "com.kwad.sdk.api.KsNativeAd",
            "com.kwad.sdk.api.KsRewardVideoAd",
            "com.kwad.sdk.api.KsFullScreenVideoAd",
            "com.kwad.sdk.api.KsAdSDK",
            "com.kwad.sdk.core.response.model.AdTemplate",
            // 快手 App 内部广告
            "com.kuaishou.ad.KSAdManager",
            "com.kuaishou.ad.KSAdDataManager",
            "com.kuaishou.ad.KSSplashAdManager",
            "com.kuaishou.ad.KSFeedAdManager",
            "com.kuaishou.ad.KSAdConfigManager",
            "com.kuaishou.ads.KSAdManager",
            "com.kuaishou.ads.KSSplashAd",
            "com.yxcorp.kuaishou.addfp.KWAdsdkUtils",
            // Mintegral
            "com.mbridge.msdk.out.MBridgeSDK",
            "com.mbridge.msdk.out.MBSplashHandler",
            "com.sigmob.windad.WindAdSDK",
            "com.anythink.core.api.ATAdManager",
            "com.anythink.splashad.api.ATSplashAd"
    };

        // 只挂这些：加载 / 展示 / 就绪判断
    private static final String[] VERB = {
            "load", "show", "preload", "cache", "isready", "isloaded", "isvalid"
    };

    private SdkHook() {
    }

    public static void install(ClassLoader cl) {
        installFiltered(cl, null);
    }

    // 给 FastSkip 用：拿到所有入口类名
    public static String[] entryClasses() {
        return ENTRY;
    }

    // 按当前应用的 SDK 前缀过滤，避免装无关的类
    public static void installFiltered(ClassLoader cl, String[] prefixes) {
        long t0 = System.currentTimeMillis();
        int classes = 0;
        int methods = 0;
        for (String name : ENTRY) {
            if (prefixes != null && !matchAny(name, prefixes)) {
                continue;
            }
            Class<?> type;
            try {
                type = Class.forName(name, false, cl);
            } catch (Throwable ignored) {
                continue;
            }
            int n = hookClass(type);
            if (n > 0) {
                classes++;
                methods += n;
            }
        }
        Xp.log("SdkHook classes=" + classes + " methods=" + methods
                + " in " + (System.currentTimeMillis() - t0) + "ms");
    }

    private static boolean matchAny(String name, String[] prefixes) {
        for (String p : prefixes) {
            if (name.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private static int hookClass(Class<?> owner) {
        // View 子类整体跳过
        if (android.view.View.class.isAssignableFrom(owner)) {
            return 0;
        }
        Method[] methods;
        try {
            methods = owner.getDeclaredMethods();
        } catch (Throwable t) {
            return 0;
        }
        int count = 0;
        for (Method m : methods) {
            if (m == null || m.isSynthetic() || m.isBridge()) {
                continue;
            }
            if (m.getDeclaringClass() != owner) {
                continue;
            }
            List<String> w = Names.words(m.getName());
            if (w.isEmpty()) {
                continue;
            }
            String flat = w.get(0);
            boolean wanted = false;
            for (String v : VERB) {
                if (flat.startsWith(v)) {
                    wanted = true;
                    break;
                }
            }
            if (!wanted) {
                continue;
            }
            Class<?> ret = m.getReturnType();
            // 只处理这两种，对象返回一律放过
            if (ret != boolean.class && ret != void.class) {
                continue;
            }
            String id = "sdk." + owner.getName() + "#" + m.getName() + "#" + m.getParameterCount();
            try {
                if (ret == boolean.class) {
                    Xp.hook(id, m, new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) {
                            return Boolean.FALSE;
                        }
                    });
                } else {
                    Xp.hook(id, m, new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) {
                            return null;
                        }
                    });
                }
                count++;
            } catch (Throwable t) {
                Xp.debug("sdk hook fail " + m + " :: " + t);
            }
        }
        return count;
    }
}
