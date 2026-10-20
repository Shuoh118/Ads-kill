package io.github.shuoh118.adskill;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.BaseAdapter;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

// 列表广告
public final class ListHook {

    private ListHook() {
    }

    public static void install() {
        try {
            Method m = BaseAdapter.class.getDeclaredMethod(
                    "getView", int.class, View.class, ViewGroup.class);
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            Xp.hook("list.getView", m, SafeHook.view("getView", new SafeHook.Body() {
                @Override
                public Object run(XposedInterface.Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    if (!(result instanceof View)) {
                        return result;
                    }
                    Object self = Xp.self(chain);
                    if (!(self instanceof Adapter)) {
                        return result;
                    }
                    if (!isAdAdapter((Adapter) self)) {
                        return result;
                    }
                    ViewHook.collapse((View) result);
                    return result;
                }
            }));
        } catch (Throwable t) {
            Xp.debug("BaseAdapter hook fail :: " + t);
        }
    }

        // adapter 本身是广告类，或者是广告类的匿名内部类
    private static boolean isAdAdapter(Adapter adapter) {
        String cls = adapter.getClass().getName();
        if (Names.isAdClass(cls)) {
            return true;
        }
        int i = cls.indexOf('$');
        if (i > 0) {
            return Names.isAdClass(cls.substring(0, i));
        }
        return false;
    }
}
