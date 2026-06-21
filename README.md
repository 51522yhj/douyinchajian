# 抖音植入广告跳段辅助插件

这是一个 Android 无障碍辅助 APK。开启服务后，它只在抖音相关包名内扫描当前屏幕的可访问性文本；当检测到普通视频里的口播、赞助、下单、领券、小黄车等植入广告信号时，会尝试拖动底部进度条向后快进，从而跳过视频中的广告片段。

## 重要说明

- 本项目不修改抖音 APK，不 hook 进程，不破解接口，也不读取账号数据。
- 功能依赖 Android 无障碍服务，需要用户手动授权。
- 当前版本依赖抖音是否把字幕、标题、贴纸或按钮文案暴露给无障碍服务；如果广告植入只存在于视频画面或音频里，系统可能无法识别。
- 跳段通过模拟拖动底部进度条实现。不同抖音版本、不同视频长度和不同设备比例下，快进幅度可能需要调整。
- 如果误跳或漏跳，可以调整 `DouyinAdSkipAccessibilityService.java` 里的关键词、冷却时间和拖动坐标。
- 请仅用于个人辅助场景，并遵守相关应用服务条款和当地法律法规。

## 构建

需要 JDK 17 和 Android SDK 36。

```bash
./gradlew assembleDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

生成的调试包位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 使用

1. 安装 APK。
2. 打开应用，点击“打开无障碍设置”。
3. 找到“抖音植入广告跳段辅助”，开启服务。
4. 返回抖音刷视频；当视频字幕、标题或贴纸出现口播/赞助/下单等植入广告信号时，服务会尝试向后快进一段。

## 支持的抖音包名

默认监听：

- `com.ss.android.ugc.aweme`
- `com.ss.android.ugc.aweme.lite`

如果你的客户端包名不同，可在 `app/src/main/res/xml/accessibility_service_config.xml` 和 `DouyinAdSkipAccessibilityService.java` 中补充。
