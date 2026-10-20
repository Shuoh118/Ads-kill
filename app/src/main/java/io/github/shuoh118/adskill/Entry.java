package io.github.shuoh118.adskill;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

// libxposed api 102 入口
public final class Entry extends XposedModule {

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        Xp.bind(this);
        Xp.log("module loaded api=" + getApiVersion()
                + " framework=" + getFrameworkName() + " " + getFrameworkVersion()
                + " pid=" + android.os.Process.myPid());
        Bootstrap.schedule();
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        Xp.bind(this);
        Bootstrap.start(Xp.packageNameOf(param), Xp.classLoaderOf(param));
    }
}
