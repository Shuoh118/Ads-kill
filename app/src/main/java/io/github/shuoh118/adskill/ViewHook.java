package io.github.shuoh118.adskill;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;

// 视图层
public final class ViewHook {

    private static final Map<Class<?>, Boolean> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> RES_CACHE = new ConcurrentHashMap<>();

    private ViewHook() {
    }

    public static void install() {
        if (Cfg.KILL_AD_LAYOUTS) {
            hookInflate();
        }
        if (Cfg.KILL_AD_ADD) {
            hookAddView();
        }
        hookAttach();
        if (Cfg.KILL_AD_VIEWS) {
            hookDialog();
            hookPopup();
        }
    }

    // ---------- 1. inflate：只压尺寸，不换类型 ----------

    private static void hookInflate() {
        Method m = findInflate();
        if (m == null) {
            return;
        }
        Xp.hook("view.inflate", m, SafeHook.view("inflate", new SafeHook.Body() {
            @Override
            public Object run(XposedInterface.Chain chain) throws Throwable {
                Object result = chain.proceed();
                // 必须是 View 才处理，否则原样返回
                if (!(result instanceof View)) {
                    return result;
                }
                Object id = Xp.arg(chain, 0);
                if (!(id instanceof Integer)) {
                    return result;
                }
                if (isAdRes(chain, (Integer) id)) {
                    collapse((View) result);
                }
                return result;
            }
        }));
    }

    private static Method findInflate() {
        Class<?>[][] sigs = {
                {int.class, ViewGroup.class, boolean.class},
                {int.class, ViewGroup.class}
        };
        for (Class<?>[] sig : sigs) {
            try {
                Method m = LayoutInflater.class.getDeclaredMethod("inflate", sig);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    // ---------- 2. addView：只在 after 摘，不改返回值 ----------

    private static void hookAddView() {
        try {
            Method m = ViewGroup.class.getMethod(
                    "addView", View.class, int.class, ViewGroup.LayoutParams.class);
            Xp.hook("view.addView", m, SafeHook.view("addView", new SafeHook.Body() {
                @Override
                public Object run(XposedInterface.Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    Object a0 = Xp.arg(chain, 0);
                    if (!(a0 instanceof View)) {
                        return result;
                    }
                    final View v = (View) a0;
                    if (!isAdViewCached(v)) {
                        return result;
                    }
                    SafeHook.onView(v, new Runnable() {
                        @Override
                        public void run() {
                            detach(v);
                        }
                    });
                    return result;
                }
            }));
        } catch (Throwable t) {
            Xp.debug("addView hook fail :: " + t);
        }
    }

    // ---------- 3. 挂载兜底 ----------

    private static void hookAttach() {
        try {
            Method m = View.class.getDeclaredMethod("onAttachedToWindow");
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            Xp.hook("view.attach", m, SafeHook.view("attach", new SafeHook.Body() {
                @Override
                public Object run(XposedInterface.Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    Object self = Xp.self(chain);
                    if (!(self instanceof View)) {
                        return result;
                    }
                    final View v = (View) self;
                    if (!isAdViewCached(v)) {
                        return result;
                    }
                    SafeHook.onView(v, new Runnable() {
                        @Override
                        public void run() {
                            detach(v);
                        }
                    });
                    return result;
                }
            }));
        } catch (Throwable t) {
            Xp.debug("attach hook fail :: " + t);
        }
    }

    // ---------- 4. 弹窗 ----------

    private static void hookDialog() {
        try {
            Method m = android.app.Dialog.class.getDeclaredMethod("show");
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            Xp.hook("view.dialog", m, SafeHook.view("dialog", new SafeHook.Body() {
                @Override
                public Object run(XposedInterface.Chain chain) throws Throwable {
                    Object self = Xp.self(chain);
                    if (!(self instanceof android.app.Dialog)) {
                        return chain.proceed();
                    }
                    if (!isAdOwner(self.getClass())) {
                        return chain.proceed();
                    }
                    Object r = chain.proceed();
                    try {
                        ((android.app.Dialog) self).dismiss();
                    } catch (Throwable ignored) {
                    }
                    return r;
                }
            }));
        } catch (Throwable t) {
            Xp.debug("dialog hook fail :: " + t);
        }
    }

    private static void hookPopup() {
        try {
            Method m = android.widget.PopupWindow.class.getDeclaredMethod(
                    "showAsDropDown", View.class);
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            Xp.hook("view.popup.dropdown", m, SafeHook.view("popup", new SafeHook.Body() {
                @Override
                public Object run(XposedInterface.Chain chain) throws Throwable {
                    Object self = Xp.self(chain);
                    if (!(self instanceof android.widget.PopupWindow)) {
                        return chain.proceed();
                    }
                    if (!isAdOwner(self.getClass())) {
                        return chain.proceed();
                    }
                    Object r = chain.proceed();
                    try {
                        ((android.widget.PopupWindow) self).dismiss();
                    } catch (Throwable ignored) {
                    }
                    return r;
                }
            }));
        } catch (Throwable t) {
            Xp.debug("popup hook fail :: " + t);
        }
    }

    private static boolean isAdOwner(Class<?> type) {
        Boolean cached = CLASS_CACHE.get(type);
        if (cached != null) {
            return cached;
        }
        boolean ad = Names.isAdClass(type.getName());
        CLASS_CACHE.put(type, ad);
        return ad;
    }

    // ---------- 处理 ----------

    static void collapse(View view) {
        try {
            view.setVisibility(View.GONE);
            view.setMinimumHeight(0);
            view.setMinimumWidth(0);
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp == null) {
                view.setLayoutParams(new ViewGroup.LayoutParams(0, 0));
            } else {
                lp.width = 0;
                lp.height = 0;
                view.setLayoutParams(lp);
            }
            view.requestLayout();
        } catch (Throwable t) {
            Xp.debug("collapse fail :: " + t);
        }
    }

    static void detach(View view) {
        try {
            collapse(view);
            ViewParent p = view.getParent();
            if (p instanceof ViewGroup) {
                ((ViewGroup) p).removeView(view);
            }
        } catch (Throwable t) {
            Xp.debug("detach fail :: " + t);
        }
    }

    // ---------- 工具 ----------

    static boolean isAdViewCached(View v) {
        Class<?> type = v.getClass();
        Boolean cached = CLASS_CACHE.get(type);
        if (cached != null) {
            return cached;
        }
        boolean ad = Names.isAdView(type.getName());
        CLASS_CACHE.put(type, ad);
        return ad;
    }

    private static boolean isAdRes(XposedInterface.Chain chain, int id) {
        Boolean cached = RES_CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        boolean hit = false;
        try {
            Context ctx = contextOf(chain);
            if (ctx != null) {
                hit = Names.isAdLayout(ctx.getResources().getResourceEntryName(id));
            }
        } catch (Throwable ignored) {
        }
        RES_CACHE.put(id, hit);
        return hit;
    }

    private static Context contextOf(XposedInterface.Chain chain) {
        Object self = Xp.self(chain);
        if (self instanceof LayoutInflater) {
            return ((LayoutInflater) self).getContext();
        }
        return null;
    }
}
