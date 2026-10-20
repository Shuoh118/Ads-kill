package io.github.shuoh118.adskill;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

// 拦截摇一摇广告的传感器注册
public final class ShakeBlock {

    // 只检查前 24 层调用栈
    private static final int STACK_DEPTH = 24;

    // 空监听器：注册成功但永远不触发
    private static final SensorEventListener EMPTY = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private ShakeBlock() {
    }

    public static void install(ClassLoader cl) {
        int n = 0;
        try {
            Method[] all = SensorManager.class.getDeclaredMethods();
            for (Method m : all) {
                if (!m.getName().equals("registerListener")) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (p.length < 2) {
                    continue;
                }
                if (p[0] != SensorEventListener.class) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                }
                String id = "shake.reg." + p.length;
                Xp.hook(id, m, SafeHook.view("shake", new SafeHook.Body() {
                    @Override
                    public Object run(XposedInterface.Chain chain) throws Throwable {
                        if (!Cfg.KILL_SHAKE) {
                            return chain.proceed();
                        }
                        if (!isAdRegister(chain)) {
                            return chain.proceed();
                        }
                        Xp.log("shake blocked");
                        // 换成空监听器：注册照常成功，但永远不会触发
                        Object[] args = Xp.args(chain).toArray();
                        args[0] = EMPTY;
                        return chain.proceed(args);
                    }
                }));
                n++;
            }
        } catch (Throwable t) {
            Xp.debug("shake hook fail :: " + t);
        }
        Xp.log("ShakeBlock methods=" + n);
    }

    // 监听器是广告类，或注册行为来自广告类，或注册的是加速度/重力传感器
    private static boolean isAdRegister(XposedInterface.Chain chain) {
        Object a0 = Xp.arg(chain, 0);
        if (a0 != null) {
            String name = a0.getClass().getName();
            if (Names.isAdClass(name)) {
                return true;
            }
            int d = name.indexOf('$');
            if (d > 0 && Names.isAdClass(name.substring(0, d))) {
                return true;
            }
        }
        // 传感器类型：加速度 / 重力 / 线性加速度，是摇一摇的判定依据
        Object a1 = Xp.arg(chain, 1);
        if (a1 instanceof Sensor) {
            int type = ((Sensor) a1).getType();
            if (type == Sensor.TYPE_ACCELEROMETER
                    || type == Sensor.TYPE_GRAVITY
                    || type == Sensor.TYPE_LINEAR_ACCELERATION) {
                return callerIsAd();
            }
        }
        return callerIsAd();
    }

    // 调用栈里出现广告类，说明是广告 SDK 注册的
    private static boolean callerIsAd() {
        try {
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            int limit = Math.min(st.length, STACK_DEPTH);
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
}
