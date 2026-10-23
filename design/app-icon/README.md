# RacingClub 应用图标

由内置 ImageGen 生成，使用栗色马跃过绿白障碍、奶油色路线与木质拼板，深绿底色呼应游戏界面。图中无文字，应用名称由系统启动器显示。

- 原图：`racingclub-icon-source-v1.png`，保持生成结果原件。
- 完整提示词与工具来源：`imagegen-v1.json`。
- `python3 tools/package-app-icon.py` 使用 Pillow 仅做尺寸和格式适配，生成 Android 各密度图标、自适应图标资源及 iPhone/iPad/App Store 资源。
- Android 自适应图标将完整画面缩入前景中央 72/108 范围，配合深绿底色，给系统的圆形和圆角矩形裁切留出空间。
- iOS `AppIcon.appiconset` 已加入 Xcode 资源阶段及 AppIcon 构建设置。1024 图标为不透明 RGB，外圆角交给系统。
- 当前主机没有 iOS 模拟器运行时，`actool` 无法完成图集编译。本地未签名构建使用同源 PNG 与 `CFBundleIcons` 声明；正式 Xcode 工程使用资源目录。未声称完成 iOS 图标的设备运行验证。
