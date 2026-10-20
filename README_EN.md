# Ads Kill

[简体中文](README.md)

An Android ad-blocking LSPosed module built on [libxposed](https://github.com/libxposed/api) API 102.

> Currently supports **NetEase Cloud Music** only — should work universally, but please verify on your own (tested on 8.7.76)

## Features

Removes splash and in-app ads

## Installation

1. Install [LSPosed](https://github.com/LSPosed/LSPosed)
2. Download from Releases and install
3. Enable **Ads Kill** in LSPosed, select the app in scope
4. Force stop the app you selected and reopen

## Build

Open the project in Android Studio, wait for Sync, then `Build → Build APK(s)`.

Debug signing is used by default — no configuration needed. To use your own key, copy `keystore.properties.example` to `keystore.properties` and fill it in.

## License

MIT
