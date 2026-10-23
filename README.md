# RacingClub

马术路线谜题原型。Android 使用 Kotlin / Jetpack Compose 与原生 Canvas，iOS 使用 Swift / SwiftUI / Canvas；两端都不使用 WebView。浏览器版本是独立的交互与规则验证工具，不作为移动端的运行容器。

## GitHub 与验证记录

项目仓库：[ZoomZhao/RacingClub](https://github.com/ZoomZhao/RacingClub)（私有）。源代码、80 关数据、原始参考资料和设计素材纳入版本管理；构建缓存、APK 和模拟器录屏不纳入 Git。APK 可在本地构建，也可下载下方 GitHub Actions 自动生成的产物。

已归档的 80 关回放与测试报告见 [`docs/validation/`](docs/validation/)，最新列表见 [`design/list-redesign/android-home-clean-progress.png`](design/list-redesign/android-home-clean-progress.png)，设计记录见 [`design/list-redesign/`](design/list-redesign/)。游戏页、拖拽和重设计后的操作按钮见 [`design/gameplay-redesign/`](design/gameplay-redesign/)，包含 ImageGen 设计稿、完整提示词与原生截图。

## 当前功能

- 按确认的 ImageGen 设计稿还原马场插画、木纹倒角、颗粒沙地、奶油色路线和障碍支柱；A–F 六个字母入口均留有围栏开口。
- ImageGen 应用图标已接入 Android 自适应启动图标与 iOS AppIcon；原图、完整提示词和打包说明见 [`design/app-icon/`](design/app-icon/)。
- 首页难度导航、关卡卡片与游戏页统一显示原题难度，编号范围作为辅助信息；分类依据见 [`design/difficulty-levels.md`](design/difficulty-levels.md)。
- 列表页移除右上角帮助按钮，五档难度使用段位徽章牌；关卡按钮仅显示编号、统计或成绩，未开始/进行中保留奶油牌面，已完成改为整张深绿牌面、米白编号、金色大勾及“已完成”文字，进行中使用明确文字区分进度，不显示拼板缩略图。右侧上移到与左侧品牌同行，关卡按钮按剩余高度放大。默认横屏每档 16 关完整展示，放大字号时支持纵向滚动。
- 横屏主布局：左侧 5 × 4 棋盘尽量撑满可用高度，任务和计时移到右上；右侧取消整块背景卡片，底部仅撤销、提示、重置三个图标。
- 移除工作台标题、选中说明、放置教学和出发提示，只保留图标、选中描边与必要的叠放状态和越界反馈。
- 拼板按题目中首次出现的顺序排列，重复编号只显示一次；任务条完整展示所有障碍顺序。棋盘上选中的拼板附近显示逆时针 90°、顺时针 90°与移回按钮，随位置和旋转调整，优先避让其他拼板，空间不足时贴入当前拼板的底部或右侧；奶油色、铜边、高光和按压反馈统一操作按钮样式。
- 仅展示本关需要的拼板；超过 5 块时分两排，支持最多 10 块，移除全部库存入口。可从右侧拖入左侧，也可拖动棋盘上的拼板；绿色有效落点、琥珀色叠放预览和红色越界反馈；棋盘内允许临时重叠，后选中、放置或旋转的拼板置顶，点右侧编号可找回下层。顶层显示叠放数量，右侧以绿色“已放置”和琥珀色“被遮挡”状态区分；旋转在边缘自动向内移动最少格数。重叠未消除时不自动通关；越界或取消拖动保留原位，成功操作可撤销，叠放顺序可保存恢复。棋盘和右侧卡片按下即选中，轻微滑动保留为点选；再点当前拼板或其托盘编号可收起操作，点空格/非字母木框只取消已放置拼板的选择，不误移位。操作按钮上拖动也能移动当前拼板；拖动事务绑定自己的实体编号和首指，第二手指不能偷换或取消。
- ImageGen 八向马儿按路线切线切换视图，在入口内侧待命。A–F 字母固定在木框原位置，框外绿色箭头指向起点，俯视棋格标记表示终点；同位起终点可同时识别，取消立体拱门与大标牌。
- 统一中文自然行高、字距、挑战卡片间距及路线箭头；六个围栏开口两端均有收尾柱。顶部、托盘和棋盘编号采用深底白字/浅底黑字，计算对比度均不低于 4.5:1。
- 棋盘显示起点、终点门和马儿；实际连接与障碍顺序均正确时自动停止计时、播放沿路跑动与跳跃，再显示通关；可随时跳过动画进入结算。每跨过一次障碍，右侧对应目标编号短暂放大、金色外环亮起并保留完成环，重复编号按题目中的出现位置独立反馈；跳过不会重复记分或改动冻结的时间。
- 关卡计时与可持久保存的本机排行榜；提示成绩标为练习，独立完成成绩优先。离开应用或查看弹窗时暂停计时，暂停期间不启动待播放路线或提前写入成绩。
- 未完成关卡的摆放与时间会保留。完成关卡再次进入会开始新一轮。Android 通过 ViewModel 与 SavedStateHandle 保留对局页面、弹窗和选择，Activity 重建继续当前跑动，进程恢复时已记分的跑动直接进入结算。

已接入 **01–80 全部关卡和 10 块完整道路拼板**。关卡按原题五档难度浏览：入门 STARTER（01–16）、初级 JUNIOR（17–32）、专家 EXPERT（33–48）、大师 MASTER（49–64）、宗师 PUNDIT（65–80），全部可选择；完成标记、未完成摆放、起终点选择及每关最佳成绩保存在本机。

- 实际道路允许重复跨障、无障碍支路、7 号双路，以及 8–10 号的合法平顺分岔；不会把任意两个分支误连，也不会按访问拼板次数计障碍。
- 长指令优先两行折返、按箭头阅读，全部目标始终可见，没有滚动或省略；跑动时按真实跨障进度高亮。托盘只展示不重复的实体拼板，超过 5 块时用等宽两排展示，拼板缩略图统一以竖向展示；卡片以底色、边框、勾选与“已放置”标签共同表示棋盘中状态，被遮挡时改为琥珀色标签。
- 拼板编号融入道路：浅奶油小底、深绿粗体、障碍色短线，替代角落色牌；旋转时文字正向，满盘避开选中操作，托盘最低 11dp 字号。[设计与原生截图](design/road-number/README.md)。
- 第 65–80 关开局不预设马和门的位置。直接点入口字母选择设为起点或终点，也保留底部紧凑状态入口，可选择相同字母；完整路线与所选端点都正确才自动跑动。
- 第 1–76 关提示来自核对后的解答照片；77–80 没有解答照片，其提示摆法由真实端口和已确认题面计算，并通过独立的参考路线模型复核。计算摆法保存在 `shared/generated-hints.json`，没有回填或冒充原始照片资料。
- 本机排行榜每关保留前 10 名，完成后面的关卡不会挤掉前面关卡的最佳成绩。

## Android

- 工程：`native/android/`，可直接用 Android Studio 打开。
- Release 安装包：`output/RacingClub-Android-release.apk`，固定发布签名，0.1.5（versionCode 6），Android 8.0 / API 26 起；关闭 debuggable，启用 R8 与资源压缩。
- 本次使用 JDK 21、Gradle 8.14.3、SDK 36 构建；依赖和 Wrapper 版本已固定。

```sh
cd native/android
./gradlew :app:assembleRelease :app:testReleaseUnitTest
```

需配置本机 `JAVA_HOME` 与 Android SDK。Release 构建需要下文签名配置，APK 位于 `app/build/outputs/apk/release/app-release.apk`。开发调试仍可使用 `:app:assembleDebug`。已在独立的 `RacingClub_API35` 模拟器（Android 15 / API 35，arm64，1920 × 1080 横屏）验证 80 关的原生规则、真实端口与动画端点。原有选中旋转、撤销、越界保留原位、03/04 自动跑动流程通过；新增 80 关导航、两排九块托盘、完整折行目标、65+ 字母入口的起终点选择与恢复、80 关完整自动跑动和每关成绩保留检查。当前 22 项 Android 集成/界面用例与 39 项 Kotlin 单元测试通过，包含全 80 关目标完整可见、六个入口坐标不偏移，以及 620dp/1.35 倍字号检查。新增 124 组十块堆叠时的合法位置/双向旋转检查，确保旋转不丢拼板；跨障动效、跳过结算及不重复记分已验证。签名压缩 Release 另在独立 API 35 模拟器覆盖安装并通过外部 UI 检查。设备范围仍为这些模拟器，尚未进行实体手机适配。

本机专用 AVD 已设为硬件图形模式；启动命令为 `~/Library/Android/sdk/emulator/emulator -avd RacingClub_API35 -gpu host -feature -Vulkan -no-snapshot`。默认软件图形模式在此机明显变慢，应使用本次验证的 host GPU 配置。

设置 JDK 21 和 Android SDK 并启动模拟器后，可用 `bash tools/verify-android-emulator.sh` 复现（可通过 `ANDROID_SERIAL` 选择设备）。截图、03/04/80 原生录屏、80 关回放报告与日志在 `output/android-entry-v4/validation/`；当前画面见 `design/android-native-play-v4.png` 和 `design/android-native-goals-v4.png`。v3 素材预览与旧验收文件继续保留。

### GitHub Actions 自动打包

工作流见 [`.github/workflows/android-apk.yml`](.github/workflows/android-apk.yml)。推送分支或标签后，CI 分别运行 Node 数据/浏览器状态测试、macOS Swift 核心/会话测试与 iOS SDK 原生编译，以及 Android `testReleaseUnitTest` 与 `assembleRelease`，生成固定签名的 **Release APK**。仅本地 commit 不触发；同一分支/标签的新推送会取消旧构建，也支持手动 Run workflow。

CI 使用 Ubuntu 24.04、JDK 21、SDK 36。构建后检查 APK 签名证书与仓库中的公开证书一致、不可调试、包名正确且关卡数据与 shared 完全一致；缺少签名时构建失败，不回退到 debug 或未签名包。

下载：[Actions → Android APK](https://github.com/ZoomZhao/RacingClub/actions/workflows/android-apk.yml)，选择成功运行，在 Artifacts 下载 `RacingClub-Android-release-<提交 SHA>`，解压得到 `app-release.apk`。APK、Release 测试报告、签名验证摘要和 R8 mapping 保留 14 天。私有仓库需要登录后访问。

### 固定 Release 签名

本机签名文件在 `native/android/.signing/`，整个目录已排除出 Git。请单独妥善备份其中的 `racingclub-release.p12` 和 `release.properties`，后续升级使用同一身份。新环境可参考 [`release-signing.properties.example`](native/android/release-signing.properties.example) 配置；路径相对于 `native/android`。

GitHub Secrets 已配置：`ANDROID_RELEASE_KEYSTORE_BASE64`、`ANDROID_RELEASE_STORE_PASSWORD`、`ANDROID_RELEASE_KEY_ALIAS`、`ANDROID_RELEASE_KEY_PASSWORD`。CI 仅在临时目录恢复密钥，构建后删除临时文件。仓库仅保存不含私钥的 [`release-signing-certificate.pem`](native/android/release-signing-certificate.pem)，用于校验证书指纹。

本地验证：`python3 tools/verify-release-apk.py output/RacingClub-Android-release.apk`（需配置 JDK 与 Android SDK）。如已安装旧的 Debug 包，由于签名身份不同，首次切换 Release 无法直接覆盖；卸载旧包会清除该设备的本机进度。后续 Release 使用固定身份，可正常覆盖升级。

## iOS

- 工程：`native/ios/RacingClub.xcodeproj`。
- 部署目标：iOS 17，横屏，支持 iPhone 与 iPad。
- 原生 arm64 编译产物：`native/ios/build/unsigned/RacingClub.app`。

iOS 已接入同源马场背景、木纹/沙地、条纹障碍、六个围栏开口和八向马，使用 SwiftUI / Canvas 原生绘制。版本为 0.1.5（build 6），Release 启用编译优化。命令行可用 iOS SDK 完成全部源码编译、链接与资源打包，生成未签名 `.app`；Xcode Release 的编译/链接与资源复制已核对，本机 actool 图标步骤仍受缺少模拟器运行时限制。

```sh
sh tools/verify-swift-core.sh
node tools/build-ios.mjs
```

iOS 已同步 80 关数据、完整分支规则与起终点选择，Swift 核心通过全 80 关回放和动画端点检查；共享会话场景还覆盖暂停计时、端点提示幂等与撤销、非法入口；Swift 专项覆盖待启动路线与弹窗/后台、跳过单次记分、缓存和素材解码。本轮 UI 验收在 Android 完成，iOS 界面、手势和 VoiceOver 尚无设备/模拟器运行时验收。该产物不是可直接安装的签名 IPA。真机运行仍需在 Xcode 安装对应平台组件、选择开发团队并签名；本次未连接 iPhone，也没有可用的 iOS 模拟器运行时。

## 浏览器验证

```sh
npm start
```

浏览器仍保留 03、04 两关的早期验证页面；本轮 80 关接入的是原生 App。打开 `http://localhost:4173`。默认仅监听本机；需要局域网预览时可显式设置 `HOST`。静态服务只提供 index、src、public 和 design 的规范化公开路径，并拒绝符号链接越界。无需安装 npm 依赖。支持竖屏与横屏布局、点放和拖放。浏览器验证仅证明规则与交互流程，不代表 Android / iOS 原生 UI 已经通过设备验证。

已验证挑战 03 手动摆放、越界旋转保留原位、两关自动动画与通关、编号排序、本机榜与页面刷新后进度恢复。截图保存在 `design/browser-play-verified.png`、`design/browser-win-verified.png`。

## 会话和绘制边界

Android 的 `GameSession.kt` 与 iOS 的 `GameSession.swift` 分别持有对局状态，时钟、存档和排行榜均由外部注入；Android 平台存储位于 `AndroidStorage.kt`，Activity 只负责窗口、生命周期和 ViewModel。两端用同一份 JSON 会话场景验证暂停、端点提示及非法输入，保留独立原生渲染。

材质与八向马运行时降采样并按进程缓存，原始 ImageGen PNG 不改动。静态棋盘和拖动预览分层，iOS 时间文字用局部 TimelineView 刷新；排行榜和首页进度不在绘制时反复解码。拼板、托盘状态和自定义旋转/移回操作提供原生无障碍节点。

## 共用数据和服务端

`references/level-catalog.json` 与 `references/piece-topology.json` 是题面和道路资料来源。`node tools/export-game.mjs` 将其导出为 schema v2 的 `shared/game-data.json`，同步到两端原生资源。Kotlin、Swift 依据合法端口对和“入口端口 + 障碍进度”追踪完整路线，不靠比较提示答案判定成功。

运行 `node tools/generate-hints.mjs` 可重新计算无解答照片的 77–80 关提示（需要 clang++），然后运行导出和测试。该离线搜索为了生成提示要求所有画出的路口连通，App 通关规则只验证玩家实际走通的合法路线，没有额外强加这个搜索约束。

`shared/service-contract.json` 预留版本化关卡、服务端开局、顺序操作记录、结算和排行榜接口。原生端都有独立的 `LeaderboardRepository` 边界，目前使用本机存储实现。服务端尚未开发或部署，本机成绩不是经过服务端验证的线上成绩。将来接入服务端时，应由服务端控制时间、提示与暂停政策、操作回放及成绩验证；竞技客户端不应下发答案。

## 测试与设计

- `npm test`：26 项 JavaScript 规则、动画端点、关卡资料、弹窗计时/坏存档与静态文件边界检查。
- `:app:testDebugUnitTest` / `:app:testReleaseUnitTest`：39 项 Kotlin 单元测试，包含真实 80 关正反例、共享会话场景、存档/计时/恢复、选中/拖动事务、目标换行、配色和入口坐标。
- `EmulatorFlowTest`：2 项 Android UI Automator 流程测试，使用真实点击和墙钟动画；两关各采样 3 个不同的马儿位置与朝向，并验证计时冻结。另有 1 项方向素材与四边门预览，以及 `CatalogIntegrationTest` 的 3 项 80 关规则、完整操作和排行榜测试。`EntryAndGoalsTest` 的 2 项检查覆盖全部 80 关目标可见、窄屏大字号、六个同位入口的稳定坐标。
- `DragInteractionTest`：8 项原生拖拽、堆叠、置顶、边缘旋转、保存恢复与旋转不丢失检查；`RunAnimationTest` 验证真实跨障动效与跳过后时间、步数和单次成绩保持。报告见 [`docs/validation/native-stacking-validation.json`](docs/validation/native-stacking-validation.json)。
- `LifecycleRegressionTest`：4 项真实 Activity 重建、后台、弹窗与单次结算测试；`AccessibilityBoardTest` 检查拼板读屏选择、旋转/移回动作以及真实触摸穿透。
- `tests/SwiftCoreTests.swift`：读取共用数据验证 Swift 路线、非法摆法、边界和动画端点。`tests/SwiftSessionTests.swift` 与 Kotlin 解释同一份 `shared/session-scenarios.json`，并覆盖 iOS 待启动路线、弹窗、后台、跳过、存档恢复、缓存和资源。通过 `sh tools/verify-swift-core.sh` 使用 macOS SDK 编译运行，无需模拟器。
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

图标打包可通过 `python3 tools/package-app-icon.py` 复现（需要 Pillow）。当前本地 iOS 未签名构建使用同源 PNG 图标与 Info.plist 声明；Xcode 目标已接入 AppIcon 资源目录。

最新列表版本验收：`VALIDATION_DIR="$PWD/output/list-redesign/validation" bash tools/verify-android-emulator.sh`，仍需配置 JDK 21、Android SDK 并启动模拟器。该目录包含五档首页、真实进度与放大字号截图、全部目标检查及 03/04/80 关录屏。
