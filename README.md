# Ads Kill

[English](README_EN.md)

Android 广告拦截 LSPosed 模块，基于 [libxposed](https://github.com/libxposed/api) API 102

## 功能

- **开屏广告**：文案识别 + 大尺寸广告 View + 跳过按钮清除，进程启动 6 秒窗口内生效
- **信息流广告**：按 App 分别处理，数据层剔除 / 视图层摘除
- **广告 SDK**：穿山甲、优量汇、百度、快手联盟、Mintegral、Sigmob、TopOn 等入口拦截
- **域名拦截**：广告与统计域名改写为 127.0.0.1，SDK 走"无广告"分支，不卡启动
- **摇一摇广告**：只在调用栈出现广告类时拦截传感器注册，不影响正常计步和横竖屏

## 安装

1. 安装支持 libxposed API 102的 LSPosed
2. 从 Release 下载安装
3. LSPosed 启用 **Ads Kill** 作用域勾选需要的应用
4. 强制停止选中的应用 重新打开

## 常见问题

**LSPosed 里看不到可选的作用域？**
先确认 LSPosed 版本 ≥ 1.10.0，然后在模块页面右上角重启一次 LSPosed；仍不显示时，把模块卸载重装并重新勾选。

**开了模块但广告还在？**
看 LSPosed 日志里有没有 `install begin pkg=...`。没有说明模块没进进程；有的话把 `NCM-AdFree` 标签的日志贴到 issue。

**某个 App 出现异常？**
先把 `Cfg` 里对应的开关关掉重新打包（`GENERIC_VIEWS` 管通用视图过滤），并在 issue 里说明 App 版本。

## 构建

Android Studio 打开项目，Sync 后 `Build → Build APK(s)`。

默认使用 debug 签名，无需任何配置。想用自己的签名，复制 `keystore.properties.example` 为 `keystore.properties` 填入信息。

也可以只用 JDK 17 + Android SDK 命令行构建：

```bash
./build.sh            # debug + release
./build.sh release    # 只出 release
```

## 致谢

- [libxposed/api](https://github.com/libxposed/api)
- [shatyuka/Zhiliao](https://github.com/shatyuka/Zhiliao)（MIT，知乎规则）
- coolapk-purifier（MIT，酷安规则）
- AdBlocker_Reborn（域名改写思路）

## License

MIT
