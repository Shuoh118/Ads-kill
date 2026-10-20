package io.github.shuoh118.adskill;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

// 开屏广告硬杀
public final class SplashKill {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile long lastActionAt;

    private SplashKill() {
    }

    public static void install() {
        hookSetText();
        hookOnCreate();
        hookImage();
    }

    /*
     * 广告图从缓存里读出来时，可能在 setText 之前就设进 ImageView。
     * 那一刻之前所有"隐藏"逻辑都还没触发，所以会闪过一帧。
     *
     * 这里在 setImageXxx 上拦一道：只在冷启动窗口内（3 秒）检查，
     * 调用栈里出现广告类才拦，避免误伤正常封面图。
     */
    private static void hookImage() {
        String[] names = {"setImageDrawable", "setImageBitmap", "setImageResource", "setImageURI"};
        Method[] all;
        try {
            all = android.widget.ImageView.class.getDeclaredMethods();
        } catch (Throwable t) {
            return;
        }
        for (String name : names) {
            for (Method m : all) {
                if (!m.getName().equals(name)) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 1) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                }
                Xp.hook("splashkill." + name, m, SafeHook.splash(name, new SafeHook.Body() {
                    @Override
                    public Object run(XposedInterface.Chain chain) throws Throwable {
                        if (!inSplashWindow()) {
                            return chain.proceed();
                        }
                        if (!isAdCaller() && !isAdSelf(chain)) {
                            return chain.proceed();
                        }
                        // 广告图：不加载，返回 null 就等于"设置失败"，ImageView 保持空白
                        Xp.debug("ad image blocked");
                        return null;
                    }
                }));
            }
        }
    }

    // 冷启动窗口：进程起来 3 秒内才做图片拦截
    // 窗口统一由 SplashWindow 管理，超过窗口一律停手
    private static boolean inSplashWindow() {
        return SplashWindow.active();
    }

    private static boolean isAdSelf(XposedInterface.Chain chain) {
        Object self = Xp.self(chain);
        if (self == null) {
            return false;
        }
        String name = self.getClass().getName();
        if (Names.isAdView(name)) {
            return true;
        }
        int i = name.indexOf('$');
        return i > 0 && Names.isAdView(name.substring(0, i));
    }

    private static boolean isAdCaller() {
        try {
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            int limit = Math.min(st.length, 28);
            for (int i = 0; i < limit; i++) {
                String cls = st[i].getClassName();
                if (cls == null || cls.startsWith("io.github.shuoh118.adskill")) {
                    continue;
                }
                if (Names.isAdClass(cls)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

        // 同步隐藏
    private static void hideNow(View v) {
        try {
            if (v == null || !inSplashWindow()) {
                return;
            }
            v.setVisibility(View.INVISIBLE);
            ViewParent p = v.getParent();
            // 往上两层一起隐藏，把倒计时和跳转按钮带上
            int depth = 0;
            while (p instanceof ViewGroup && depth < 2) {
                ViewGroup g = (ViewGroup) p;
                if (g.getChildCount() > 6) {
                    break;
                }
                g.setVisibility(View.INVISIBLE);
                p = g.getParent();
                depth++;
            }
        } catch (Throwable t) {
            Xp.debug("hideNow fail :: " + t);
        }
    }

        // 广告图片同步隐藏
    private static void hideAdImages(View decor) {
        try {
            if (!inSplashWindow()) {
                return;
            }
            if (!(decor instanceof ViewGroup) || decor.getWidth() <= 0) {
                return;
            }
            long screen = (long) decor.getWidth() * decor.getHeight();
            if (screen <= 0) {
                return;
            }
            hideBigImages((ViewGroup) decor, screen, 0);
        } catch (Throwable t) {
            Xp.debug("hideAdImages fail :: " + t);
        }
    }

    private static void hideBigImages(ViewGroup g, long screen, int depth) {
        if (depth > 14) {
            return;
        }
        try {
            int n = g.getChildCount();
            for (int i = 0; i < n; i++) {
                View c = g.getChildAt(i);
                if (c == null) {
                    continue;
                }
                if (c instanceof android.widget.ImageView) {
                    if (isFullSize(c, screen)) {
                        c.setVisibility(View.INVISIBLE);
                    }
                }
                if (c instanceof ViewGroup) {
                    hideBigImages((ViewGroup) c, screen, depth + 1);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /*
     * 判断是不是满屏图片。
     *
     * 之前用 w > 0 判断，会漏掉 MATCH_PARENT —— 它的值是 -1，
     * 于是满屏广告图被跳过，那一帧就画出来了 = 闪一下。
     * 现在把 MATCH_PARENT 也当作满屏处理。
     */
    private static boolean isFullSize(View c, long screen) {
        int w = c.getWidth();
        int h = c.getHeight();
        ViewGroup.LayoutParams lp = c.getLayoutParams();

        boolean wFill = w == ViewGroup.LayoutParams.MATCH_PARENT;
        boolean hFill = h == ViewGroup.LayoutParams.MATCH_PARENT;

        if (w <= 0 || h <= 0) {
            if (lp != null) {
                if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                    wFill = true;
                    w = (int) Math.max(screen, 1);
                } else if (lp.width > 0) {
                    w = lp.width;
                }
                if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                    hFill = true;
                    h = (int) Math.max(screen, 1);
                } else if (lp.height > 0) {
                    h = lp.height;
                }
            }
        }

        // 宽高都是 MATCH_PARENT，直接判定满屏
        if (wFill && hFill) {
            return true;
        }
        if (w > 0 && h > 0 && (long) w * h * 100L >= screen * 60L) {
            return true;
        }
        return false;
    }

    // ---------- setText ----------

    private static void hookSetText() {
        Method[] all;
        try {
            all = TextView.class.getDeclaredMethods();
        } catch (Throwable t) {
            return;
        }
        for (Method m : all) {
            if (!m.getName().equals("setText")) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 0) {
                continue;
            }
            if (p[0] != CharSequence.class && p[0] != String.class && p[0] != int.class) {
                continue;
            }
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            final boolean isRes = p[0] == int.class;
            Xp.hook("splashkill.setText." + p.length + "." + p[0].getSimpleName(),
                    m, SafeHook.splash("setText", new SafeHook.Body() {
                        @Override
                        public Object run(XposedInterface.Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            Object self = Xp.self(chain);
                            if (!(self instanceof TextView)) {
                                return result;
                            }
                            TextView tv = (TextView) self;
                            CharSequence text = null;
                            Object a0 = Xp.arg(chain, 0);
                            if (isRes && a0 instanceof Integer) {
                                try {
                                    text = tv.getResources().getString((Integer) a0);
                                } catch (Throwable ignored) {
                                }
                            } else if (a0 instanceof CharSequence) {
                                text = (CharSequence) a0;
                            }
                            if (text == null || !SplashText.isAd(text.toString())) {
                                return result;
                            }
                            // 只在冷启动窗口内动手，否则会误杀正常页面
                            if (!inSplashWindow()) {
                                return result;
                            }
                            final String s = text.toString();
                            final TextView marker = tv;
                            /*
                             * 关键：先同步隐藏，再异步摘除。
                             *
                             * 只 post 不隐藏 = 这一帧照样画出来 = 闪一下广告。
                             * setVisibility 不破坏布局状态，是安全的同步操作，
                             * 所以可以立刻做。
                             */
                            hideNow(marker);
                            hideAdImages(marker.getRootView());
                            MAIN.post(new Runnable() {
                                @Override
                                public void run() {
                                    handle(marker, s, "setText");
                                }
                            });
                            return result;
                        }
                    }));
        }
    }

    // ---------- onCreate ----------

    private static void hookOnCreate() {
        try {
            Method m = Activity.class.getDeclaredMethod("onCreate", android.os.Bundle.class);
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            Xp.hook("splashkill.onCreate", m, SafeHook.splash("onCreate", new SafeHook.Body() {
                @Override
                public Object run(XposedInterface.Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    Object self = Xp.self(chain);
                    if (!(self instanceof Activity)) {
                        return result;
                    }
                    final Activity act = (Activity) self;
                    /*
                     * 打开图片拦截窗口。冷启动第一屏是最可能出现广告图的时机，
                     * 这里记下起点，后面 3 秒内的 setImageXxx 会做广告判定。
                     */
                    SplashWindow.open();
                    /*
                     * 时间点前移，赶在第一帧绘制之前。
                     * 0ms 那一次跑在 onCreate 链尾，绝大多数版本此时布局已 inflate 完
                     * 但还没 draw，所以能无闪烁地摘掉。
                     */
                    long[] delays = {0, 16, 50, 120, 300};
                    for (long d : delays) {
                        MAIN.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                scan(act, "onCreate");
                            }
                        }, d);
                    }
                    return result;
                }
            }));
        } catch (Throwable t) {
            Xp.debug("onCreate hook fail :: " + t);
        }
    }

    // ---------- 扫描 ----------

    private static void scan(final Activity act, String stage) {
        try {
            if (act == null || act.isFinishing() || act.isDestroyed()) {
                return;
            }
            if (!inSplashWindow()) {
                return;
            }
            View decor = act.getWindow() == null ? null : act.getWindow().peekDecorView();
            if (decor == null) {
                return;
            }
            // 先清跳过按钮/倒计时，它们总是比广告图更早出现在树上
            SkipKiller.kill(decor);

            if (!decor.isAttachedToWindow()) {
                return;
            }
            String text = SplashText.find(decor, 0);
            if (text == null) {
                return;
            }
            Xp.log("ad text(" + stage + ") " + simple(act.getClass().getName()) + " = " + text);
            handle(decor, text, stage);
        } catch (Throwable t) {
            Xp.debug("scan fail :: " + t);
        }
    }

    // ---------- 摘除 ----------

        // 精确摘除
    private static void handle(View marker, String text, String stage) {
        try {
            Activity act = activityOf(marker.getContext());
            if (act == null || act.isFinishing() || act.isDestroyed()) {
                return;
            }
            View decor = act.getWindow() == null ? null : act.getWindow().peekDecorView();
            if (decor == null || !decor.isAttachedToWindow()) {
                return;
            }

            View target = SplashText.node(decor, text, 0);
            if (target == null) {
                return;
            }

            // 从命中点往上爬，找"最小可摘容器"：
            // 子 View 数 <= 6 且它的父容器已经不是它自己那种层级
            View best = null;
            View cur = target;
            ViewParent p = cur.getParent();
            int depth = 0;
            while (p instanceof ViewGroup && depth < 8) {
                ViewGroup vg = (ViewGroup) p;
                if (vg == decor) {
                    // 到 decor 就停：decor 的子 View 是整页内容，绝不能摘
                    break;
                }
                // 只认"孩子不多"的容器，说明它是广告自身的包装层
                if (vg.getChildCount() <= 6) {
                    best = vg;
                } else {
                    // 孩子一多就是页面容器，立刻停
                    break;
                }
                cur = vg;
                p = vg.getParent();
                depth++;
            }

            if (best != null && best.getParent() instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) best.getParent();
                if (parent != decor) {
                    parent.removeView(best);
                    lastActionAt = System.currentTimeMillis();
                    Xp.log("ad block removed(" + stage + "): "
                            + simple(best.getClass().getName()) + " text=" + text);
                    return;
                }
            }

            // 摘不到就什么都不做，绝不退而求其次去拆大容器
            Xp.log("ad text found but not removable(" + stage + "): " + text);
        } catch (Throwable t) {
            Xp.debug("handle fail :: " + t);
        }
    }

    private static void rescan(Activity act) {
        try {
            if (act == null || act.isFinishing() || act.isDestroyed()) {
                return;
            }
            View decor = act.getWindow() == null ? null : act.getWindow().peekDecorView();
            if (decor == null) {
                return;
            }
            String again = SplashText.find(decor, 0);
            if (again != null) {
                // 用 decor 当 marker，handle 内部会自己重新定位到文案节点
                View node = SplashText.node(decor, again, 0);
                if (node != null) {
                    handle(node, again, "rescan");
                }
            }
        } catch (Throwable t) {
            Xp.debug("rescan fail :: " + t);
        }
    }

        // 供 SplashWatch 调用
    public static void sweepPublic(View marker, String text, String stage) {
        handle(marker, text, stage);
    }

        // 供 SplashWatch 调用：按类名命中的大块广告 View，直接摘
    public static void killViewPublic(View v, String stage) {
        try {
            if (v == null) {
                return;
            }
            ViewParent p = v.getParent();
            if (p instanceof ViewGroup) {
                ((ViewGroup) p).removeView(v);
                Xp.log("ad view removed(" + stage + "): " + simple(v.getClass().getName()));
            }
        } catch (Throwable t) {
            Xp.debug("killView fail :: " + t);
        }
    }

        // 整页都是广告时的处理
    private static void finish(Activity act, String text, String stage) {
        if (System.currentTimeMillis() - lastActionAt < 1500) {
            return;
        }
        lastActionAt = System.currentTimeMillis();
        Xp.log("ad page detected(" + stage + "): "
                + simple(act.getClass().getName()) + " text=" + text);
        // 什么都不做。交给 handle() 里的精确摘除逻辑。
        // 这里再动任何一层都会把正常界面拆掉。
    }

    // ---------- 工具 ----------

    static Activity activityOf(Context ctx) {
        Context c = ctx;
        int guard = 0;
        while (c != null && guard++ < 12) {
            if (c instanceof Activity) {
                return (Activity) c;
            }
            if (c instanceof ContextWrapper) {
                c = ((ContextWrapper) c).getBaseContext();
            } else {
                break;
            }
        }
        return null;
    }

    private static String simple(String fqcn) {
        int i = fqcn.lastIndexOf('.');
        return i < 0 ? fqcn : fqcn.substring(i + 1);
    }
}
