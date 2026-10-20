package io.github.shuoh118.adskill;

import android.content.pm.ApplicationInfo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// 直接从 apk 里读 dex 字符串表，拿全部类名
public final class DexScan {

    private static final int STRING_IDS_SIZE_OFF = 0x38;
    private static final int STRING_IDS_OFF_OFF = 0x3C;

    private DexScan() {
    }

        // 扫描一个 apk（含 split），把类名塞进 out
    public static void scanApk(String path, Set<String> out) {
        ZipFile zip = null;
        try {
            zip = new ZipFile(path);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (!name.startsWith("classes") || !name.endsWith(".dex")) {
                    continue;
                }
                byte[] data = readAll(zip.getInputStream(e), (int) e.getSize());
                if (data != null) {
                    parseDex(data, out);
                }
            }
        } catch (Throwable t) {
            Xp.debug("apk scan fail " + path + " :: " + t);
        } finally {
            if (zip != null) {
                try {
                    zip.close();
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
            // 只取类描述符 L...;
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

        // dex 所在 apk：应用本体 + 所有 split
    public static List<String> apkPaths() {
        List<String> paths = new ArrayList<>(4);
        try {
            ApplicationInfo info = App.appInfo();
            if (info == null) {
                return paths;
            }
            if (info.sourceDir != null) {
                paths.add(info.sourceDir);
            }
            add(paths, info.splitSourceDirs);
            add(paths, info.splitPublicSourceDirs);
        } catch (Throwable t) {
            Xp.debug("apkPaths fail :: " + t);
        }
        return paths;
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
}
