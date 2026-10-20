package io.github.shuoh118.adskill;

/*
 * 当前进程的策略持有者。
 * 每个应用进程只会初始化一次，之后所有模块从这里取策略。
 */
public final class Ctx {

    private static volatile Profile profile;

    private Ctx() {
    }

    public static void set(Profile p) {
        profile = p;
    }

    public static Profile get() {
        return profile;
    }

    // 当前是不是快手系
    public static boolean isKs() {
        Profile p = profile;
        return p != null && p.isKuaishou();
    }

    // 当前是不是网易云
    public static boolean isNetease() {
        Profile p = profile;
        return p != null && p.kind == Profile.Kind.NETEASE;
    }
}
