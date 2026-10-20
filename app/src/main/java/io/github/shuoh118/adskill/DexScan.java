package io.github.shuoh118.adskill;

import android.content.pm.ApplicationInfo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/*
 * 拿目标应用的类名全集。
 *
 * === 为什么之前 apks=0 ===
 *
 * 原来靠 App.appInfo() 拿 ApplicationInfo，
 * 但它是通过 ActivityThread.currentApplication() 反射取的。
 * 在 onPackageLoaded 阶段 application 还没创建完，
 * currentApplication() 返回 null，于是 apk 路径为空，
 * 一个 dex 都没扫到 —— 所有按类名匹配的模块全部失效。
 *
 * === 现在 ===
 *
 * 三级回退，第一级最可靠：
 *   1. 从 ClassLoader 的 pathList.dexElements 直接取 dex 路径
 *   2. ApplicationInfo.sourceDir + splitSourceDirs
 *   3. 从 /proc/self/maps 里找 apk 路径
 */
public final class DexScan {

    private static final int STRING_IDS_SIZE_OFF = 0x38;
    private static final int STRING_IDS_OFF_OFF = 0x3C;

    private DexScan() {
    }

    // ---------- 取 apk 路径 ----------

    public static List<String> apkPaths(ClassLoader cl) {
        List<String> out = new ArrayList<>(8);

        // 1. ClassLoader 里的 dex 路径（最可靠）
        fromClassLoader(cl, out);

        // 2. ApplicationInfo
        if (out.isEmpty()) {
            fromAppInfo(out);
        }

        // 3. maps
        if (out.isEmpty()) {
            fromMaps(out);
        }

        if (out.isEmpty()) {
            Xp.log("DexScan: no apk path found");
        } else if (Cfg.DEBUG) {
            for (String p : out) {
                Xp.debug("DexScan apk: " + p);
            }
        }
        return out;
    }

    private static void fromClassLoader(ClassLoader cl, List<String> out) {
        if (cl == null) {
            return;
        }
        try {
            Class<?> bdcl = Class.forName("dalvik.system.BaseDexClassLoader");
            Field f = bdcl.getDeclaredField("pathList");
            f.setAccessible(true);
            Object pathList = f.get(cl);
            if (pathList == null) {
                return;
            }
            Field ef = pathList.getClass().getDeclaredField("dexElements");
            ef.setAccessible(true);
            Object[] elements = (Object[]) ef.get(pathList);
            if (elements == null) {
                return;
            }
            for (Object el : elements) {
                if (el == null) {
                    continue;
                }
                // DexFile 对象有 getPath，或字段有 path
                String p = dexPathOf(el);
                if (p != null && !out.contains(p)) {
                    out.add(p);
                }
            }
        } catch (Throwable t) {
            Xp.debug("fromClassLoader fail :: " + t);
        }
    }

    private static String dexPathOf(Object element) {
        // 先试字段
        Class<?> c = element.getClass();
        int guard = 0;
        while (c != null && guard++ < 5) {
            try {
                Field f = c.getDeclaredField("path");
                f.setAccessible(true);
                Object v = f.get(element);
                if (v instanceof String) {
                    return (String) v;
                }
            } catch (Throwable ignored) {
            }
            // 试 zip 字段，里面是 File
            try {
                Field f = c.getDeclaredField("zip");
                f.setAccessible(true);
                Object v = f.get(element);
                if (v instanceof java.io.File) {
                    return ((java.io.File) v).getAbsolutePath();
                }
                if (v != null) {
                    try {
                        Field name = v.getClass().getDeclaredField("name");
                        name.setAccessible(true);
                        Object n = name.get(v);
                        if (n instanceof String) {
                            return (String) n;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
            c = c.getSuperclass();
        }
        // 最后试方法
        try {
            java.lang.reflect.Method m = element.getClass().getMethod("getName");
            Object v = m.invoke(element);
            if (v instanceof String) {
                return (String) v;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void fromAppInfo(List<String> out) {
        try {
            ApplicationInfo info = App.appInfo();
            if (info == null) {
                Xp.debug("fromAppInfo: appInfo null");
                return;
            }
            if (info.sourceDir != null && !out.contains(info.sourceDir)) {
                out.add(info.sourceDir);
            }
            add(out, info.splitSourceDirs);
        } catch (Throwable t) {
            Xp.debug("fromAppInfo fail :: " + t);
        }
    }

    private static void fromMaps(List<String> out) {
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/self/maps"));
            String line;
            while ((line = r.readLine()) != null) {
                int i = line.indexOf(".apk");
                if (i < 0) {
                    continue;
                }
                int start = line.indexOf('/');
                if (start < 0) {
                    continue;
                }
                String p = line.substring(start, i + 4);
                if (!out.contains(p)) {
                    out.add(p);
                }
            }
            r.close();
        } catch (Throwable t) {
            Xp.debug("fromMaps fail :: " + t);
        }
    }

    private static void add(List<String> out, String[] arr) {
        if (arr == null) {
            return;
        }
        for (String s : arr) {
            if (s != null && !out.contains(s)) {
                out.add(s);
            }
        }
    }

    // ---------- 解析 dex ----------

    public static void scanApk(String path, Set<String> out) {
        if (path == null) {
            return;
        }
        java.io.File f = new java.io.File(path);
        if (!f.exists()) {
            Xp.log("scanApk: not exist " + path);
            return;
        }
        if (!f.canRead()) {
            Xp.log("scanApk: not readable " + path);
            return;
        }
        if (f.isDirectory()) {
            return;
        }
        // 非 zip（比如已经到了 dex 文件）直接读
        if (path.endsWith(".dex")) {
            byte[] data = readFile(path);
            if (data != null) {
                parseDex(data, out);
            }
            return;
        }
        ZipFile zip = null;
        int dexCount = 0;
        int before = out.size();
        try {
            zip = new ZipFile(path);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (!name.startsWith("classes") || !name.endsWith(".dex")) {
                    continue;
                }
                dexCount++;
                byte[] data = readAll(zip.getInputStream(e), (int) e.getSize());
                if (data != null) {
                    parseDex(data, out);
                }
            }
        } catch (Throwable t) {
            Xp.log("scanApk fail " + path + " :: " + t);
        } finally {
            if (dexCount > 0) {
                Xp.log("scanApk " + new java.io.File(path).getName()
                        + " dex=" + dexCount + " classes+=" + (out.size() - before));
            }
            if (zip != null) {
                try {
                    zip.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static byte[] readFile(String path) {
        java.io.FileInputStream in = null;
        try {
            in = new java.io.FileInputStream(path);
            return readAll(in, (int) new java.io.File(path).length());
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static byte[] readAll(InputStream in, int hint) {
        if (in == null) {
            return null;
        }
        try {
            int cap = hint > 0 ? Math.min(hint, 1 << 26) : 8192;
            ByteArrayOutputStream bos = new ByteArrayOutputStream(cap);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void parseDex(byte[] d, Set<String> out) {
        if (d == null || d.length < 0x70) {
            return;
        }
        if (d[0] != 'd' || d[1] != 'e' || d[2] != 'x' || d[3] != '\n') {
            return;
        }
        int count = readInt(d, STRING_IDS_SIZE_OFF);
        int off = readInt(d, STRING_IDS_OFF_OFF);
        if (count <= 0 || off <= 0 || (long) off + (long) count * 4 > d.length) {
            return;
        }
        for (int i = 0; i < count; i++) {
            int idOff = off + i * 4;
            if (idOff + 4 > d.length) {
                break;
            }
            int dataOff = readInt(d, idOff);
            if (dataOff <= 0 || dataOff >= d.length) {
                continue;
            }
            int p = dataOff;
            int guard = 0;
            while (p < d.length && guard < 5) {
                byte b = d[p];
                p++;
                guard++;
                if ((b & 0x80) == 0) {
                    break;
                }
            }
            if (p >= d.length) {
                continue;
            }
            int end = p;
            while (end < d.length && d[end] != 0) {
                end++;
            }
            if (end - p < 3) {
                continue;
            }
            if (d[p] != 'L' || d[end - 1] != ';') {
                continue;
            }
            String desc = new String(d, p + 1, end - p - 2, StandardCharsets.UTF_8);
            if (desc.isEmpty() || desc.indexOf(';') >= 0 || desc.indexOf('[') >= 0) {
                continue;
            }
            out.add(desc.replace('/', '.'));
        }
    }

    private static int readInt(byte[] d, int off) {
        return (d[off] & 0xFF)
                | ((d[off + 1] & 0xFF) << 8)
                | ((d[off + 2] & 0xFF) << 16)
                | ((d[off + 3] & 0xFF) << 24);
    }
}
