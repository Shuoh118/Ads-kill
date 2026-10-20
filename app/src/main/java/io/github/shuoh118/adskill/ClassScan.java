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
            List<String> names = doScan();
            cache = names;
            cachedFor = cl;
            return names;
        }
    }

    private static List<String> doScan() {
        long t0 = System.currentTimeMillis();
        Set<String> out = new LinkedHashSet<>(16384);

        List<String> apks = DexScan.apkPaths();
        for (String p : apks) {
            DexScan.scanApk(p, out);
        }

        if (out.isEmpty()) {
            Xp.log("dex parse empty, falling back to DexFile");
            fallback(out, apks);
        }

        List<String> result = new ArrayList<>(out);
        Xp.log("scan classes=" + result.size() + " apks=" + apks.size()
                + " in " + (System.currentTimeMillis() - t0) + "ms");
        return result;
    }

    @SuppressWarnings("deprecation")
    private static void fallback(Set<String> out, List<String> apks) {
        for (String p : apks) {
            dalvik.system.DexFile df = null;
            try {
                df = new dalvik.system.DexFile(new java.io.File(p));
                java.util.Enumeration<String> en = df.entries();
                while (en.hasMoreElements()) {
                    String n = en.nextElement();
                    if (n != null && !n.startsWith("[")) {
                        out.add(n);
                    }
                }
            } catch (Throwable t) {
                Xp.debug("DexFile fail " + p + " :: " + t);
            } finally {
                if (df != null) {
                    try {
                        df.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }
}
