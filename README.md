# RacingClub

马术路线谜题。Android 使用 Kotlin / Jetpack Compose / Canvas，iOS 使用 Swift / SwiftUI / Canvas，两端都不使用 WebView。浏览器页面只用来核对早期规则，不是移动端的运行容器。

仓库：[ZoomZhao/RacingClub](https://github.com/ZoomZhao/RacingClub)（私有）。80 关数据、参考照片和设计素材纳入版本管理；构建缓存、APK 和模拟器录屏不纳入 Git。

## 玩法

- 横屏。左侧 5×4 棋盘，右侧是本关拼板、障碍顺序和撤销 / 提示 / 重置。
- 拼板按题目首次出现的顺序排列，重复编号只显示一次。可以从右侧拖入，也可以拖动棋盘上的拼板。棋盘内允许临时重叠，后放的在上面；重叠未消除时不通关。
- 选中已放置的拼板后，旁边出现旋转和移回。旋转绕拼板中心转 90°，只有转出棋盘时才向内收；棋盘中部连续转 4 次回到原格。
- 首页或对局里的「设置」可以选择只显示顺时针、只显示逆时针，或两个方向都显示。同一页可以清除全部分关进度和完成标记，清除前会再确认一次；本机最佳成绩保留。
- 第 65–80 关的起点和终点由玩家点入口字母设置，两端可以相同。路线和端点都正确时自动停止计时并沿路跑动，可跳过动画。
- 最短边达到 600dp 的平板会加大棋盘和右侧托盘，十块拼板保持可点。

已接入 01–80 关和 10 块道路拼板：入门 01–16、初级 17–32、专家 33–48、大师 49–64、宗师 65–80。完成标记、未完成摆放和每关最佳成绩保存在本机。

## Android

工程在 `native/android/`。当前版本 0.1.7（versionCode 8），Android 8.0 / API 26 起。Release 关闭 debuggable，启用 R8 与资源压缩，使用固定签名。

```sh
cd native/android
./gradlew :app:assembleRelease :app:testReleaseUnitTest
```

需要本机 `JAVA_HOME` 与 Android SDK。签名文件在 `native/android/.signing/`，不纳入 Git；新环境参考 `release-signing.properties.example`。APK 位于 `app/build/outputs/apk/release/app-release.apk`。

推送到 GitHub 后，[Android APK](https://github.com/ZoomZhao/RacingClub/actions/workflows/android-apk.yml) 工作流会跑 Node 测试、Swift 核心测试、iOS SDK 编译，以及 Android 单元测试和签名 Release 打包。产物在该次运行的 Artifacts 里，保留 14 天。

## iOS

工程在 `native/ios/RacingClub.xcodeproj`，部署目标 iOS 17，横屏，支持 iPhone 与 iPad。版本 0.1.7（build 8）。

```sh
sh tools/verify-swift-core.sh
node tools/build-ios.mjs
```

命令行产物是未签名 `.app`。真机需要在 Xcode 选择开发团队并签名。

## 浏览器核对

```sh
npm start
```

打开 `http://localhost:4173`。默认只监听本机。浏览器仍只有挑战 03、04，用来核对早期交互，不代表原生界面。

## 数据

`references/level-catalog.json` 与 `references/piece-topology.json` 是题面和道路来源。`node tools/export-game.mjs` 导出 `shared/game-data.json`，并同步到两端原生资源。通关按真实端口和障碍顺序判断，不对照提示答案。

`node tools/generate-hints.mjs` 重新计算没有解答照片的 77–80 关提示。`docs/游戏规则.txt`、`docs/关卡索引.txt` 和 `docs/关卡核对报告.txt` 是逐关核对记录。`references/` 保留原始照片和裁切图。

`shared/service-contract.json` 预留了服务端开局、结算和排行榜接口。目前成绩只存在本机，不是经过服务端验证的线上成绩。
