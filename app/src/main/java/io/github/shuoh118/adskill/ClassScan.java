package io.github.shuoh118.adskill;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 目标 App 的类名全集
public final class ClassScan {

    private static volatile List<String> cache;
    private static volatile ClassLoader cachedFor;

    private ClassScan() {
    }

    public static List<String> allClassNames(ClassLoader cl) {
        List<String> hit = cache;
        if (hit != null && cachedFor == cl) {
            return hit;
        }
        synchronized (ClassScan.class) {
            if (cache != null && cachedFor == cl) {
                return cache;
            }
            List<String> names = doScan(cl);
            cache = names;
            cachedFor = cl;
            return names;
        }
    }

    private static List<String> doScan(ClassLoader cl) {
        long t0 = System.currentTimeMillis();
        Set<String> out = new LinkedHashSet<>(16384);

        List<String> apks = DexScan.apkPaths(cl);
        for (String p : apks) {
            DexScan.scanApk(p, out);
        }

        if (out.isEmpty()) {
            Xp.log("dex parse empty, falling back to DexFile");
            fallback(out, cl);
        }

        List<String> result = new ArrayList<>(out);
        Xp.log("scan classes=" + result.size() + " apks=" + apks.size()
                + " in " + (System.currentTimeMillis() - t0) + "ms");
        return result;
    }

    // 兜底：从 ClassLoader 的 dex 元素里直接枚举
    private static void fallback(Set<String> out, ClassLoader cl) {
        if (cl == null) {
            return;
        }
        try {
            java.lang.reflect.Method m = ClassLoader.class.getDeclaredMethod("getResourceAsStream", String.class);
            m.setAccessible(true);
        } catch (Throwable ignored) {
        }
        // 直接扫已知类名的方式不可行，这里只记录
        Xp.log("fallback: cannot enumerate classes");
    }
}
