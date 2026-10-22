# Android 视觉还原素材

参考：`approved-landscape.png`（用户确认稿）。使用内置 ImageGen 基于参考图提取场景与两种材质；完整、未经改写的提示词见 `native-v2-imagegen-prompts.json`。

| 资源 | 项目中的原件 | Android 资源 |
| --- | --- | --- |
| 无 UI 的马场背景 | `public/assets/native-v2/arena-backdrop.png` | `drawable-nodpi/arena_backdrop.png` |
| 沙地颗粒 | `public/assets/native-v2/sand-grain.png` | `drawable-nodpi/sand_grain.png` |
| 浅色木纹 | `public/assets/native-v2/wood-grain.png` | `drawable-nodpi/wood_grain.png` |

界面文字、工具栏、拼板、道路、障碍和动画由 Compose / 原生 Canvas 动态绘制。棋盘按实际规则为 5 × 4；右侧采用用户后续确认的小标题与单行图标布局，自动判定通关，无试跑按钮。
