# 抖音植入广告跳段辅助插件

这是一个 Android 无障碍辅助 APK。开启服务后，它只在抖音相关包名内扫描当前屏幕的可访问性文本；当检测到视频进度条或章节里的“推广内容”“广告片段”“营销片段”等标记时，会尝试拖动进度条跳到该标记之后，从而跳过视频中的广告植入部分。

## 重要说明

- 本项目不修改抖音 APK，不 hook 进程，不破解接口，也不读取账号数据。
- 功能依赖 Android 无障碍服务，需要用户手动授权。
- 当前版本依赖抖音是否把进度条章节标记暴露给无障碍服务；如果安卓端没有暴露这些节点，系统可能无法识别。
- 跳段通过模拟拖动进度条实现。不同抖音版本、不同视频长度和不同设备比例下，快进幅度可能需要调整。
- 如果误跳或漏跳，可以调整 `DouyinAdSkipAccessibilityService.java` 里的章节关键词、冷却时间和拖动坐标。
- 如果无障碍主开关自己关闭，请先在系统应用设置里把本应用的电池策略改成“不限制/允许后台”，并确认无障碍页顶部“使用服务”主开关处于开启状态；蓝色快捷方式开关不代表服务正在运行。
- 应用首页会显示最近一次包名、扫描结果、动作和错误信息，用来判断服务是否真的读到了抖音里的章节标记。
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
4. 返回抖音刷视频；当进度条或章节出现推广内容、广告片段、营销片段等标记时，服务会尝试跳到该段之后。

## 支持的抖音包名

默认监听：

- `com.ss.android.ugc.aweme`
- `com.ss.android.ugc.aweme.lite`

如果你的客户端包名不同，可在 `app/src/main/res/xml/accessibility_service_config.xml` 和 `DouyinAdSkipAccessibilityService.java` 中补充。
