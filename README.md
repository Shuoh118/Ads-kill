# Ads Kill

[English](README_EN.md)

Android 广告拦截 LSPosed 模块，基于 [libxposed](https://github.com/libxposed/api) API 102。

> 目前仅支持**网易云音乐**、**快手**、**快手极速版**理论通杀需自行实测

## 功能

移除开屏及内部广告

## 安装

1. 安装 [LSPosed](https://github.com/LSPosed/LSPosed)
2. 从 Release 下载安装
3. LSPosed 启用 **Ads Kill**，作用域勾应用
4. 强制停止你选择的应用，重新打开

## 构建

Android Studio 打开项目，Sync 后 `Build → Build APK(s)`。

默认使用 debug 签名，无需任何配置。想用自己的签名，复制 `keystore.properties.example` 为 `keystore.properties` 填入信息。

## License

MIT
