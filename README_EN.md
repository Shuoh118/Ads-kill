# Ads Kill

[简体中文](README.md)

An Android ad-blocking LSPosed module based on [libxposed](https://github.com/libxposed/api) API 102

## Features

- **Splash ads**: text detection + oversized ad views + skip-button removal, active within a 6-second window after process start
- **Feed ads**: handled per app, removed at the data layer or detached at the view layer
- **Ad SDKs**: blocks the entry points of Pangle, GDT, Baidu, Kuaishou Union, Mintegral, Sigmob, TopOn and more
- **Domain blocking**: ad and tracking domains are rewritten to 127.0.0.1, so SDKs take their "no ad" path without stalling startup
- **Shake-to-jump ads**: sensor registration is blocked only when an ad class is on the call stack, leaving step counting and orientation unaffected

## Installation

1. Install an LSPosed build with libxposed API 102 support
2. Download from Releases and install
3. Enable **Ads Kill** in LSPosed and select the apps you need in scope
4. Force stop the selected apps and reopen them

## FAQ

**No apps to select in LSPosed scope?**
Make sure LSPosed is 1.10.0 or newer, then restart LSPosed once from the top-right corner of the module page. If the scope still does not appear, uninstall the module, reinstall it and select the scope again.

**The module is enabled but ads are still there?**
Check the LSPosed log for `install begin pkg=...`. If it is missing, the module never entered the process; if it is present, attach the `NCM-AdFree` log to an issue.

**An app misbehaves?**
Turn off the corresponding switch in `Cfg` and rebuild (`GENERIC_VIEWS` controls generic view filtering), and mention the app version in the issue.

## Build

Open the project in Android Studio, wait for Sync, then `Build → Build APK(s)`.

Debug signing is used by default — no configuration needed. To use your own key, copy `keystore.properties.example` to `keystore.properties` and fill it in.

You can also build from the command line with JDK 17 + Android SDK:

```bash
./build.sh            # debug + release
./build.sh release    # release only
```

## Credits

- [libxposed/api](https://github.com/libxposed/api)
- [shatyuka/Zhiliao](https://github.com/shatyuka/Zhiliao) (MIT, Zhihu rules)
- coolapk-purifier (MIT, Coolapk rules)
- AdBlocker_Reborn (domain rewriting approach)

## License

MIT
