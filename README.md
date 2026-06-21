# 抖音广告自动跳过辅助插件

这是一个 Android 无障碍辅助 APK。开启服务后，它只在抖音相关包名内扫描当前屏幕的可访问性文本；当检测到广告、推广、赞助等商业标识时，优先点击“跳过/关闭”类按钮，否则模拟一次上滑以跳过当前广告视频。

## 重要说明

- 本项目不修改抖音 APK，不 hook 进程，不破解接口，也不读取账号数据。
- 功能依赖 Android 无障碍服务，需要用户手动授权。
- 文案识别基于界面文本，不保证覆盖所有广告样式；如果误跳或漏跳，可以调整 `DouyinAdSkipAccessibilityService.java` 里的关键词和冷却时间。
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
3. 找到“抖音广告跳过辅助”，开启服务。
4. 返回抖音刷视频；当界面出现广告/推广标识时，服务会尝试跳过。

## 支持的抖音包名

默认监听：

- `com.ss.android.ugc.aweme`
- `com.ss.android.ugc.aweme.lite`

如果你的客户端包名不同，可在 `app/src/main/res/xml/accessibility_service_config.xml` 和 `DouyinAdSkipAccessibilityService.java` 中补充。
