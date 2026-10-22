# RacingClub

马术路线谜题原型。Android 使用 Kotlin / Jetpack Compose 与原生 Canvas，iOS 使用 Swift / SwiftUI / Canvas；两端都不使用 WebView。浏览器版本是独立的交互与规则验证工具，不作为移动端的运行容器。

## GitHub 与验证记录

项目仓库：[ZoomZhao/RacingClub](https://github.com/ZoomZhao/RacingClub)（私有）。源代码、80 关数据、原始参考资料和设计素材纳入版本管理；构建缓存、APK 和模拟器录屏保留在本地，克隆后按下方步骤重新构建。

已归档的 80 关回放与测试报告见 [`docs/validation/`](docs/validation/)，实际界面见 [`design/android-native-home-80.png`](design/android-native-home-80.png) 和 [`design/android-native-play-80.png`](design/android-native-play-80.png)。

## 当前功能

- 按确认的 ImageGen 设计稿还原马场插画、木纹倒角、颗粒沙地、奶油色路线和障碍支柱；A–F 六个字母入口均留有围栏开口。
- 横屏主布局：左侧 5 × 4 棋盘，右侧拼板工作台；标题与图标按钮放在同一行。
- 拼板按编号升序排列；挑战条保留要求的障碍顺序。已放置拼板可从棋盘或工作台重新选中，旋转、移动、移回与撤销。
- 共 10 块的库存视图，按关卡只展开需要的拼板；托盘支持分行与滚动。
- ImageGen 八向马儿按路线切线切换视图；四向终点门根据棋盘边缘定位。起终点说明使用配套标牌，字母标识与围栏收尾柱错开。
- 统一中文自然行高、字距、挑战卡片间距及路线箭头；六个围栏开口两端均有收尾柱。
- 棋盘显示起点、终点门和马儿；实际连接与障碍顺序均正确时自动停止计时、播放沿路跑动与跳跃，再显示通关。
- 关卡计时与可持久保存的本机排行榜；提示成绩标为练习，独立完成成绩优先。离开应用或查看弹窗时暂停计时。
- 未完成关卡的摆放与时间会保留。完成关卡再次进入会开始新一轮。

已接入 **01–80 全部关卡和 10 块完整道路拼板**。关卡按 5 页、每页 16 关浏览，全部可选择；完成标记、未完成摆放、起终点选择及每关最佳成绩保存在本机。

- 实际道路允许重复跨障、无障碍支路、7 号双路，以及 8–10 号的合法平顺分岔；不会把任意两个分支误连，也不会按访问拼板次数计障碍。
- 长指令可横向滑动；跑动时按真实跨障进度高亮、滚动。托盘只展示不重复的实体拼板，超过 5 块时用等宽两排展示。
- 第 65–80 关开局不预设马和门的位置。点击棋盘下方的起点、终点标牌选择 A–F，可选择相同字母；完整路线与所选端点都正确才自动跑动。
- 第 1–76 关提示来自核对后的解答照片；77–80 没有解答照片，其提示摆法由真实端口和已确认题面计算，并通过独立的参考路线模型复核。计算摆法保存在 `shared/generated-hints.json`，没有回填或冒充原始照片资料。
- 本机排行榜每关保留前 10 名，完成后面的关卡不会挤掉前面关卡的最佳成绩。

## Android

- 工程：`native/android/`，可直接用 Android Studio 打开。
- 安装包：`output/RacingClub-Android-debug.apk`，调试签名，Android 8.0 / API 26 起。
- 本次使用 JDK 21、Gradle 8.14.3、SDK 36 构建；依赖和 Wrapper 版本已固定。

```sh
cd native/android
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

需配置本机 `JAVA_HOME` 与 Android SDK。生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。已在独立的 `RacingClub_API35` 模拟器（Android 15 / API 35，arm64，1920 × 1080 横屏）验证 80 关的原生规则、真实端口与动画端点。原有选中旋转、撤销、越界保留原位、03/04 自动跑动流程通过；新增 80 关导航、两排九块托盘、长指令滑动、65+ 起终点选择与恢复、80 关完整自动跑动和每关成绩保留检查。本轮 6 项 Android 集成/界面检查与 6 项 Kotlin 单元测试全部通过。设备范围仍为该模拟器，尚未进行实体手机适配。

本机专用 AVD 已设为硬件图形模式；启动命令为 `~/Library/Android/sdk/emulator/emulator -avd RacingClub_API35 -gpu host -feature -Vulkan -no-snapshot`。默认软件图形模式在此机明显变慢，应使用本次验证的 host GPU 配置。

设置 JDK 21 和 Android SDK 并启动模拟器后，可用 `bash tools/verify-android-emulator.sh` 复现（可通过 `ANDROID_SERIAL` 选择设备）。截图、03/04/80 原生录屏、80 关回放报告与日志在 `output/android-emulator-80/validation/` 和 `output/level-integration/`；当前画面见 `design/android-native-play-80.png`。v3 素材预览与旧验收文件继续保留。

## iOS

- 工程：`native/ios/RacingClub.xcodeproj`。
- 部署目标：iOS 17，横屏，支持 iPhone 与 iPad。
- 原生 arm64 编译产物：`native/ios/build/unsigned/RacingClub.app`。

本机的 Xcode 能提供 iOS SDK，但 `xcodebuild` 的通用设备目标提示 iOS 平台组件尚未安装。已使用同一 SDK 的 Swift 编译器完成 SwiftUI 源码检查、原生链接，并生成未签名 `.app`。

```sh
node tools/build-ios.mjs
```

iOS 已同步 80 关数据、完整分支规则与起终点选择，Swift 核心通过全 80 关回放和动画端点检查；本轮 UI 验收在 Android 完成。该产物不是可直接安装的签名 IPA。真机运行仍需在 Xcode 安装对应平台组件、选择开发团队并签名；本次未连接 iPhone，也没有可用的 iOS 模拟器运行时。

## 浏览器验证

```sh
npm start
```

浏览器仍保留 03、04 两关的早期验证页面；本轮 80 关接入的是原生 App。打开 `http://localhost:4173`。无需安装 npm 依赖。支持竖屏与横屏布局、点放和拖放。浏览器验证仅证明规则与交互流程，不代表 Android / iOS 原生 UI 已经通过设备验证。

已验证挑战 03 手动摆放、越界旋转保留原位、两关自动动画与通关、编号排序、本机榜与页面刷新后进度恢复。截图保存在 `design/browser-play-verified.png`、`design/browser-win-verified.png`。

## 共用数据和服务端

`references/level-catalog.json` 与 `references/piece-topology.json` 是题面和道路资料来源。`node tools/export-game.mjs` 将其导出为 schema v2 的 `shared/game-data.json`，同步到两端原生资源。Kotlin、Swift 依据合法端口对和“入口端口 + 障碍进度”追踪完整路线，不靠比较提示答案判定成功。

运行 `node tools/generate-hints.mjs` 可重新计算无解答照片的 77–80 关提示（需要 clang++），然后运行导出和测试。该离线搜索为了生成提示要求所有画出的路口连通，App 通关规则只验证玩家实际走通的合法路线，没有额外强加这个搜索约束。

`shared/service-contract.json` 预留版本化关卡、服务端开局、顺序操作记录、结算和排行榜接口。原生端都有独立的 `LeaderboardRepository` 边界，目前使用本机存储实现。服务端尚未开发或部署，本机成绩不是经过服务端验证的线上成绩。将来接入服务端时，应由服务端控制时间、提示与暂停政策、操作回放及成绩验证；竞技客户端不应下发答案。

## 测试与设计

- `npm test`：21 项 JavaScript 规则、动画端点与关卡资料检查。
- `:app:testDebugUnitTest`：6 项 Kotlin 原生规则与八向/边界方向测试。
- `EmulatorFlowTest`：2 项 Android UI Automator 流程测试，使用真实点击和墙钟动画；两关各采样 3 个不同的马儿位置与朝向，并验证计时冻结。另有 1 项方向素材与四边门预览，以及 `CatalogIntegrationTest` 的 3 项 80 关规则、完整操作和排行榜测试。
- `tests/SwiftCoreTests.swift`：读取共用数据验证 Swift 路线、非法摆法、边界和动画端点。
- `design/`：ImageGen 页面设计及浏览器验证截图。`design/imagegen-prompts.txt` 记录早期素材提示词；`design/native-v2-imagegen-prompts.json` 保存场景与材质提示词；`design/native-v3-imagegen-prompts.json` 保存八向马儿、四向门和两张标牌共 14 张素材的完整提示词、路径和校验值。
- `public/assets/`：共用 ImageGen 插画原件。两端原生资源中各有相同副本。
- `references/`：61 张原始规则、拼卡及关卡图片，80 张编号裁切图（1–76 为解答，77–80 为题目）及 60 张补充题目裁切图（17–76）。原始文件和校验值保留。

## 关卡资料复核

全部 80 关已按规则、拼卡刻线、题目/解答图片的顺序复核。1–16 原有路线一致，补齐了 76 份参考布局的朝向与端点，并更正 57、59、72 的参考起点。1–16、66、68 可从图中唯一推导顺序；其余 58 张解答图存在多种合法顺序，候选证据与原题要求分开记录。

2026-10-23 已统一归档第 17–76 关题面，全部顺序均与参考道路匹配。80 关均有明确序列：66 关由题面确认，14 关（1、2、5–16）由解答图唯一推导，仍保留两种证据的区别。第 1–16 关再次逐图复核，原记录一致；原先 58 关的顺序或圈数歧义已由补图消除。77–80 题面完整，尚无解答照片，参考布局保持空值。

- `docs/游戏规则.txt`：包括同位起终点、岔路转向和无障碍支路的完整约定。
- `docs/关卡索引.txt`、`docs/关卡核对报告.txt`：逐关结果、来源和歧义说明。
- `references/piece-topology.json`、`references/route-audit.json`：资料用道路模型和端口追踪证据。
- `node tools/analyze-reference-routes.mjs /tmp/racingclub-route-audit.json`：重算参考图分析；`npm test` 包含资料一致性与关键路线复核。
- `node tools/render-reference-docs.mjs`：根据结构化资料重建索引和核对报告。
- `references/transcriptions/`：保留分批转录与正式原图的对应关系；`references/levels/challenges-overview-017-076.jpg` 为补充题目总览。

这些资料已导入两端原生数据，Android 已启用全部 80 关。第 65 关起仍由玩家推导两端，不将参考解答字母作为题目预设。

规则核对依据用户照片，以及 [SmartGames 产品说明](https://smartgames.com/uk/one-player-games/horse-academy)、[游戏设计者的规则说明](https://www.smartgamesandpuzzles.com/horse-academy.html)。照片编号和入口字母以用户版本为准。
