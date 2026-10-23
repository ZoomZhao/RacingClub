## 补充审查结论（基线 670e820，只读）

我把指定的 10 个文件逐行读完了：`src/app.js`、`src/game.js`、`server.mjs`、`tests/references.test.mjs`、`tools/analyze-reference-routes.mjs`、`tools/render-reference-docs.mjs`、`tools/solve-levels.cpp`、`tools/generate-hints.mjs`、`tools/package-app-icon.py`、`tools/verify-release-ui.py`。

全程只读，没有改文件、没有提交，也没有起服务、跑完整测试、编译求解器或连设备。只用 node 在内存里做了几项一致性核对，结果都一致：
- 03、04 的内置 solution 用 `validate` 回放都能通过。
- `game.js` 和 `solve-levels.cpp` 的端口、路径、门坐标，与 `piece-topology.json` 和目录里的 gates 一致，旋转公式也对得上。
- 03、04 的起终点和布局与 `level-catalog.json` 相同。
- 编号 65 以前的关卡，`startGate` / `finishGate` 都不为空。
- `render-reference-docs.mjs` 里写死的数字（58 张有歧义的解答图、14 关唯一参考推导）和数据实算结果相同。

### 值得修复的新问题

**1. 在排行榜里切换关卡标签后，弹窗还开着计时就恢复了**（中低，置信度高）
- 位置：`src/app.js:209`、`src/app.js:199`
- 触发：游戏中打开排行榜，点另一关的标签。代码先 `d.close()`，再马上 `showRanking()` 开新弹窗。按 HTML 规范，`close` 事件是之后异步派发的。所以旧弹窗的 close 回调会在新弹窗已经打开后才执行 `startClock()` 和 `scheduleCheck()`。
- 影响：新排行榜弹窗还开着，计时已经在走，本机榜成绩会多算时间；如果棋盘此时已经连通，`run()` 可能在模态弹窗后面自动启动。另外，帮助或排行榜弹窗开着时切走标签页再切回来，`visibilitychange`（`src/app.js:100`）也会让计时恢复。
- 修复：在 `startClock()` 里加一个条件 `!document.querySelector('dialog[open]')`。各弹窗的 close 回调先 `remove()`，再判断有没有其他打开的弹窗，再决定是否 `scheduleCheck`。也可以改成切换标签时只替换同一个弹窗的内容，不关了重开。
- 验证：在 `startClock` 打桩，或用 Playwright：打开排行榜、切标签、等 1 秒，断言 `#elapsed-time` 没变、`clockStarted === null`；再模拟一次 hidden→visible，同样断言。

**2. 本地存档里有坏的成绩项时，页面初始化直接报错**（低，置信度高）
- 位置：`src/app.js:23`
- 触发：`racingclub:v1.__scores` 数组里有 `null` 或非对象元素。`s.level` 会抛 TypeError。这一行在模块顶层，而第 6 行的 try 只包住了 `JSON.parse`。
- 影响：整个网页验证工具白屏，只能手动清 localStorage。这里只有本应用会写入，所以触发概率低。
- 修复：过滤条件开头加 `s && typeof s === 'object' &&`。`saved` 本身不是对象时也回退成 `{}`。
- 验证：预置 `__scores:[null]`，确认页面能加载、坏项被丢弃。

**3. 发布 UI 验证脚本在优化模式下会空跑并写出 `passed: true`**（低，置信度中）
- 位置：`tools/verify-release-ui.py:73, 82, 97-116`，`tools/verify-release-ui.py:146`
- 触发：用 `python3 -O` 运行，或环境里设了 `PYTHONOPTIMIZE`。所有 `assert`（包括 `bounds()` 里的空节点检查）都会被去掉，报告照样写成通过。
- 影响：发布验收证据可能失真。
- 修复：把 `assert` 换成显式的 `if ...: raise SystemExit/AssertionError(...)`。
- 验证：用 `python3 -O` 跑一个缺节点的模拟输入，确认会失败退出。

### 考虑过但排除的项
- **`server.mjs`**：前缀检查和 `0.0.0.0` 绑定已在另一个 worktree 修复，不重复报。
- **`game.js` 的 `validate`、`canPlace`、`restorePlacements`**：重叠、越界、重复 id、提前到终点、绕回原处都处理正确。localStorage 恢复时会校验 id、整数坐标和范围。innerHTML 里只插内部常量和已校验的数字，没找到 XSS 路径。
- **`app.js` 的计时、`persist` 和 `stopClock` 的累加**：没有重复计时。撤销会恢复步数、提示数取最大值，看作设计如此。首页 "/ 2" 写死，在只有两关时不算问题。
- **`analyze-reference-routes.mjs`**：
  - 1–3 号只取一种朝向，因为它们 180° 对称。
  - 环检测和环上的障碍序列切片是对的。
  - "纯空支路的环会让 `repeatableObstacleCycle` 为 null"、"样例最多 32 条，或同一序列对应多条路径时，测试的 `routeCount === unique.size` 会失败"，这两点只是潜在问题，当前数据不触发，而且触发时测试会报出来，不会悄悄出错。
- **`solve-levels.cpp`**：`outsideCell` 的半格坐标、旋转、门编码、`compatible` 剪枝、`active` 的保存和恢复都核对过。约束比 App 严格属于既定约定。`seq[100]` 没有边界检查，但输入只来自目录（最长 18），不报。
- **`generate-hints.mjs`**：用相对路径，依赖从仓库根目录运行（属于开发体验问题）。`execFileSync(binary, options)` 这种写法 Node 支持。只为 77–80 关生成，符合 65 关以后端点由玩家推导的约定。
- **`package-app-icon.py`**：`drawable-nodpi` 没有 mkdir，但目录已经提交在仓库里，不影响。转 RGB、自适应图标的 inset 属于已认可的素材风格，按约定不讨论。iOS 同像素尺寸的文件名重复，但内容相同，无害。
- **`verify-release-ui.py`**：几处在 `snapshot()` 前没有等待、用的是旧节点树、"跳过动画"按钮有竞态，最多造成偶发失败，不会误报通过。脚本里的原生文案没去核对，因为原生部分不在本次范围。
- **`references.test.mjs`**：和 `route-audit.json` 全量 deepEqual，校验值和尺寸也有检查，覆盖充分。

### 未覆盖
- 原生 App 和 CI（按约定不审）。
- `tests/game.test.mjs`、`runtime-data.test.mjs`、`export-game.mjs` 等不在清单里的文件。
- 原始照片，以及 `level-catalog.json` 的逐字段人工核对（只做了上面列出的抽查）。
