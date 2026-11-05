# 实验性新 UI 与 NPatch 真机解剖的差距清单

> 审查对象：`limbo-android-lib/src/main/java/com/max2idea/android/limbo/ui/exp/`（`LimboExperimentalActivity` / `ExpTopBar` / `ExpMachineListAdapter` / `ExpBottomBar` / `ExpTheme` / `ExpDrawables`）+ `res/values/colors_exp.xml`
>
> 基准：NPatch 真机源码 `/tmp/npatch/manager/src/main/java/top/nkbe/npatch/ui/**`（`HomeScreen.kt`、`AppItem.kt`、`FloatingGlassBottomBar.kt`、`NPatchTopAppBar.kt`）+ Miuix 0.9.4 `/tmp/miuix/miuix-ui/.../basic/{TopAppBar,Card}.kt`、`theme/{Colors,TextStyles}.kt`、`utils/{PressFeedback,MiuixIndication}.kt`
>
> 本仓库的规格文件 `docs/team/npatch-miuix-style-spec.md` 本身有若干处与真机源码不一致（见文末 §B）。下面的每一条都**以真机源码为准**，并同时指出 spec 是否也写错了——因为按错误的 spec 改代码只会越改越不像。
>
> 每条格式：**NPatch 做什么 / 我们做什么 / 位置 / 怎么改（含具体数字）**。

---

## A. 视觉骨架层面（最影响"一眼像不像"）

### 1. 页面底色用了 `background`，卡片用了 `surfaceContainer`，两者在浅色下完全同色 → 卡片全部"消失"

- **NPatch 做什么**：页面底色是 `MiuixTheme.colorScheme.background`（`MainActivity.kt:171`），卡片是 `backgroundAwareCardColors()` → `CardDefaults.defaultColors(color = surfaceContainer)`（`Card.kt:202`、`HomeScreen.kt:151`）。浅色下 `background = #FFFFFF`（`Colors.kt:373`）而 `surfaceContainer = #FFFFFF`（`Colors.kt:381`）——两者**确实同色**；深色下 `background = #242424`（`Colors.kt:482`）而 `surfaceContainer = #242424`（`Colors.kt:492`）——**也同色**。
- **我们做什么**：底色 `theme.background`（`LimboExperimentalActivity.java:175`、`colors_exp.xml:18` `#FFFFFF` / `:44` `#242424`），卡片 `theme.surfaceContainer`（`ExpMachineListAdapter.java:152`、`:261`、`:488`，`colors_exp.xml:20` `#FFFFFF` / `:46` `#242424`）。
- **位置**：`LimboExperimentalActivity.java:175`、`ExpMachineListAdapter.java:152`、`:261`、`:488`；`colors_exp.xml:18`、`:20`、`:44`、`:46`。
- **差距**：数值抄对了，但**视觉结构错了**。NPatch 的卡片不是靠"卡片色 ≠ 页面色"来区分的，而是靠**模糊/毛玻璃 + 阴影 + 极淡描边**（`FloatingGlassBottomBar.kt:430` 的 `dropShadow`、`:473` 的 `drawBackdrop`、`HomeScreen.kt` 的 `backgroundAwareCardColors` 在有背景图时才做透明度处理）。我们这里浅色模式给卡片描了 1px `outline`（`ExpMachineListAdapter.java:153`、`:262`、`:489-490`）来"制造"边界，这正是 HyperOS 明确不要的"传统卡片"观感；深色模式则**完全没有任何描边或阴影**，卡片与页面同色 `#242424`，在深色下就是一片糊。
- **怎么改**：
  1. 浅色：删掉卡片描边（`ExpMachineListAdapter.java:153`、`:262`、`:489-490` 的 `theme.dark ? 0 : theme.outline` 改为常量 `0`），改为 `Card` 上加**极淡阴影**：`setElevation(theme.dpf(1f))`（约 1dp，不要 Material 默认卡片阴影）。
  2. 深色：卡片底色改用 `surfaceContainerHighest = #2D2D2D`（`colors_exp.xml:47` 已定义但**从未被使用**），与页面 `#242424` 拉开一档；NPatch 深色下 `surfaceContainerHighest` 也正是 `0xFF2D2D2D`（`Colors.kt` dark 段）。
  3. 页面底色仍保持 `background`，不要改成 `surface`（`#F7F7F7` / `#000000`）——`theme.surface` 令牌在 `ExpTheme.java:69` 定义后**全项目零引用**，说明当初想用但没落地。

### 2. 卡片圆角用 `GradientDrawable` 直角矩形，NPatch 用超椭圆 squircle → 轮廓"不像圆"

- **NPatch 做什么**：`Card` 的背景是 `squircleSurface(color, cornerRadius)`（`Card.kt:177`），Miuix 的 squircle 是**超椭圆**（n≈4 的贝塞尔近似），四角比普通圆角矩形更"方"更饱满。
- **我们做什么**：`ExpDrawables.roundedRect()` 用 `GradientDrawable.setCornerRadius()`（`ExpDrawables.java:25-31`），是标准圆角矩形。规格 §0/§6.1 允许这种近似，但要求"若要更像，用 `Path` + 四段三次贝塞尔"。
- **位置**：`ExpDrawables.java:25-31`、`ExpMachineListAdapter.java:152`、`:261`、`:488`、`ExpBottomBar.java:62`。
- **差距**：这是"看起来还是普通 App"的一个显著来源，尤其 16dp 圆角在 12dp 内边距下会显得过于圆润。
- **怎么改**：在 `ExpDrawables` 里加一个 `squirclePath(int w, int h, float rPx)`，用**四段三次贝塞尔**构造超椭圆（控制点系数 ≈ 0.55·r 而不是圆角的 0.5523 圆弧近似，四角过渡更长），配合 `Path` + `canvas.drawPath` 的自定义 `Drawable` 取代 `GradientDrawable`。至少先给**列表卡片**（`ExpMachineListAdapter.java:488`）和**状态卡**（`:152`）换掉，胶囊底栏保持真圆（NPatch 底栏用的是 `CircleShape`，不是 squircle，见 `FloatingGlassBottomBar.kt:212`）。

### 3. 列表项图标是"文字首字"，且字重/颜色/尺寸都偏离 NPatch 的 AppItem

- **NPatch 做什么**：`AppItem` 的图标是 `Box(Modifier.size(48.dp))` 里放**真实应用图标**，`AppManagePage.kt:239-244` 用 `Image(bitmap = getIcon(appInfo), modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))`——**48dp 圆角 14dp**，不是 12dp。
- **我们做什么**：48dp 圆角 **12dp**（`ExpTheme.java:40` `ITEM_ICON_RADIUS_DP = 12f`，用于 `ExpMachineListAdapter.java:376-377`），里面是一个 22sp **Bold** 白色首字（`:504-506`）。
- **位置**：`ExpMachineListAdapter.java:376-377`、`:504-506`；`ExpTheme.java:40`。
- **差距**：① 圆角 12 vs 真实 14；② 22sp Bold 首字在 48dp 方块里非常"挤"，NPatch 的图标是图像、视觉重量轻；③ `Typeface.create("sans-serif", Typeface.BOLD)` 在 22sp 下几乎是一个黑色方块，远重于 NPatch 的图标。
- **怎么改**：
  - 圆角改 **14dp**（`ExpTheme.java:40`：`ITEM_ICON_RADIUS_DP = 12f` → `14f`）。
  - 首字字号从 22sp 降到 **18sp**，字重从 `BOLD` 改为 `Typeface.create("sans-serif-medium", Typeface.NORMAL)`（即 `theme.medium`），并保留白色 —— 与 NPatch 图标的视觉重量接近。
  - 更彻底的做法：给机器生成一个真正的矢量图标（按后端类型取 `ic_machine_box` / `ic_tune` 等已有 drawable，`res/drawable/ic_machine_box.xml` 现成），而不是文字首字。

### 4. 机器卡片缺 NPatch `AppItem` 的 12dp 尾槽间距，且多加了一个 NPatch 没有的"启动"胶囊

- **NPatch 做什么**：`AppItem` 的 `trailingContent` 前有 `Spacer(Modifier.width(12.dp))`（`AppItem.kt:121`），`topRightContent` 前是 8dp（`:128`）。`AppItem` **没有**任何"启动"按钮——点击整个卡片就是打开。
- **我们做什么**：`trailing` 前的间距是 `setMarginStart(theme.dp(8))`（`ExpMachineListAdapter.java:542`），且多出一个 32dp 高的"启动"文字胶囊（`:563-586`），点击胶囊会**直接启动**虚拟机（`LimboExperimentalActivity.java:341-343` → `openMachine(name, true)`）。
- **位置**：`ExpMachineListAdapter.java:539-543`、`:563-586`；`LimboExperimentalActivity.java:341-343`。
- **差距**：① 尾槽间距 8dp ≠ NPatch 12dp；② "启动"胶囊是 NPatch 完全没有的控件，它把一行卡片变成"两栏"，破坏 AppItem 的单行两文字结构；③ 更严重的是**交互语义冲突**：卡片点击 = 编辑（`ExpMachineListAdapter.java:382-387`），胶囊点击 = 启动，但两者外观相似（都是 primary 色的强调），用户很难预期。
- **怎么改**：
  - 尾槽间距改 **12dp**（`ExpMachineListAdapter.java:542`：`theme.dp(8)` → `theme.dp(12)`）。
  - 删除"启动"胶囊（`:562-586` 的 `start` 字段与 `bindMachine` 里 `:380` 的可见性切换、`LimboExperimentalActivity.java:341-343` 的 `onMachineStartClick`），改成 NPatch 的做法：**整卡点击 = 启动/进入**，长按 = 更多操作。或者保留胶囊但改成 **NPatch 的 `topRightContent` 位置**（8dp、右上角、`Align.Top`），且视觉降级为纯文字 13sp primary，不加底色。
  - 若必须保留启动能力，改成 NPatch `AppItem` 的 `labelTrailingContent`（在标题行内、标题右侧、6dp 间距），不是右侧独立一列。

### 5. "运行中"徽标的位置错了：NPatch 的徽标在**标题行内**，不在右侧尾槽

- **NPatch 做什么**：`AppItem` 的 `labelTrailingContent` 是**标题行内**的槽位（`AppItem.kt:81-97`），`Row` 内 `Arrangement.spacedBy(6.dp)`，在 label 右边。右侧的 `trailingContent` 是给**独立控件**（开关、按钮）用的。
- **我们做什么**：`badge` 放在右侧 `trailing` 容器里（`ExpMachineListAdapter.java:539-560`），与右侧的 `start` 胶囊互斥显示（`:379-380`）；且它整体撑开了 `trailing` 宽度，`trailingParams` 没有 `setMarginStart` 之外的处理。
- **位置**：`ExpMachineListAdapter.java:539-560`、`:546-560`、`:379-380`。
- **差距**：NPatch 的"运行中"状态标是**跟在机器名后面**的小标签（同一行、6dp 间距、12sp），我们把它做成了右侧独立列，导致卡片右边缘参差不齐，且运行/未运行两种机器的卡片**右对齐线不一致**——这在列表里非常显眼。
- **怎么改**：把 `badge` 移进中间的 `center` LinearLayout 的第一行，做成 `LinearLayout`（horizontal）容器：`name`（weight=1，`fill=false`）+ 6dp 间距 + `badge`。`badge` 保持 `tertiaryContainer` 底 + `onTertiaryContainer` 字 + 12sp + 圆角 999dp（当前 `ExpMachineListAdapter.java:551-553` 的尺寸是对的，12sp/24dp 高/8-3dp padding 符合 `colors_exp.xml` 令牌），只需改位置。

### 6. 状态卡标题没有用机器名，而是固定文案 "正在运行"

- **NPatch 做什么**：`StatusCard` 标题是 `stringResource(R.string.shizuku_available)`，**状态文案本身**就是标题（`HomeScreen.kt:205-210`），说明文字在第二行（`:211-223`）。标题色是 `onSurface`（正常态）或 `statusColor`（告警态），**不是 primary**。
- **我们做什么**：标题固定为 `R.string.exp_status_running` = "正在运行"（`ExpMachineListAdapter.java:203`、`strings_exp.xml:18`），机器名被拼到了**第二行**摘要里（`:222` `summary.setText(name + " · " + meta)`），且标题颜色用 `theme.primary`（`:202`）。
- **位置**：`ExpMachineListAdapter.java:202-203`、`:217-222`；`strings_exp.xml:18`；`LimboExperimentalActivity.java:265-276`（`statusTitle`/`statusMeta` 计算后 `statusTitle` 实际被**丢弃**——`:269-270` 赋值给 `statusTitle`，但 `:279` 传给 adapter 的是它，而 adapter 的 `bindHeader` 里 `runningName` 才是真正用的字段，`statusTitle` 字段在 adapter 中**从未被读取**，是死代码）。
- **差距**：三重问题：① 标题该是"机器名 + 状态"，我们把机器名降级到了摘要行；② 标题染成 `primary` 蓝色，NPatch 正常态是 `onSurface` 黑色（只有告警态才用 `statusColor`/`warningColor`）；③ `statusTitle` 死代码，说明数据流是"写了一半"。
- **怎么改**：
  - 标题改为 **`机器名`**（用 `runningName`），`ExpMachineListAdapter.java:203` 改成 `title.setText(running ? runningName : R.string.exp_status_idle)`。
  - 标题色改 `theme.onSurface`（`:202` 去掉 `running ? theme.primary :`），保留 `runningName` 为空时回落 `exp_unnamed_machine`。
  - 摘要行只放 **"SDL · 4 核 · 2048 MB"**（`:217-222` 去掉 `name + " · "` 前缀），这正是本仓库 spec §5 的原话，现在被 `name + " · "` 破坏了。
  - 删掉 `LimboExperimentalActivity.java:265-270` 的 `statusTitle` 死代码（或改成真正传给 adapter）。

### 7. 状态卡图标在"运行中"时是电源图标，NPatch 是对勾圆圈；颜色也不是 primary

- **NPatch 做什么**：`StatusCard` 用 `Icons.Outlined.CheckCircle`（正常）/ `Icons.Outlined.Warning`（告警），`tint = statusColor`（= `primary`），底色 `statusColor.copy(alpha = 0.16f)` 的 44dp 圆（`HomeScreen.kt:182-197`）。
- **我们做什么**：运行中用 `R.drawable.ic_power`（电源符号），未运行用 `R.drawable.ic_machine_box`；颜色 `running ? theme.primary : theme.onSurfaceVariantSummary`（`ExpMachineListAdapter.java:183-185`）；底色 `theme.translucent(theme.primary, 0.16f)`（`:176-178`）。
- **位置**：`ExpMachineListAdapter.java:180-186`；`res/drawable/ic_power.xml`。
- **差距**：① **电源符号**在语义上表示"开关/断电"，不是"正在运行"；NPatch 用的是**对勾**（CheckCircle）表示"可用/在跑"。这是最影响一眼识别的一个图标错误。② 未运行时 NPatch 用 **Warning** 图标 + `warningColor = 0xFFE65100`（`HomeScreen.kt:149`），我们用灰色机器盒子图标 + `onSurfaceVariantSummary`，颜色偏"中性"而非"提示"。
- **怎么改**：
  - 新增 `res/drawable/ic_check_circle.xml`（圆形勾，24dp 视口，stroke 2，`#FFFFFFFF` 填充供 `setColorFilter` 染色）。
  - `ExpMachineListAdapter.java:183`：`running ? R.drawable.ic_check_circle : R.drawable.ic_warning`。
  - 未运行分支颜色改为 `theme.warning`（`ExpTheme.java:82` 已定义 `warning = #E65100` / `#E65100`，**当前从未被使用**），底色 `theme.translucent(theme.warning, 0.16f)`，对齐 NPatch 的告警卡。

### 8. "全部虚拟机" 标题到第一张卡片的间距（8dp）和卡片到卡片的间距（8dp）相同 → 标题完全失去分隔作用

- **NPatch 做什么**：卡片与卡片之间是 **8dp**（`AppItem` 的 `padding(bottom = 8.dp)`，`AppItem.kt:50-52`），但**卡片组与卡片组之间是 12dp**（`StatusCard` 外层 Column 用 `Arrangement.spacedBy(12.dp)`，`HomeScreen.kt:164`；`InfoCard` / `SupportCard` 各自 `padding(bottom = 12.dp)`，`:349`、`:407`）。层级靠 12dp / 8dp 两档区分。
- **我们做什么**：状态卡下边距 12dp（`ExpMachineListAdapter.java:162`）；统计卡行 `buildCountRow()` **没有设任何下边距**（`:231-255` 整个方法）；机器卡下边距 8dp（`:497` `CARD_GAP_DP`）；标题 `setPadding(side, theme.dp(12), side, theme.dp(8))`（`:315`）。
- **位置**：`ExpMachineListAdapter.java:162`、`:231-255`、`:315`、`:497`。
- **差距**：实际渲染出来的间距是：状态卡→统计卡 **12dp** ✓、统计卡→标题 **12dp** ✓、**标题→第一张卡片 8dp** ✗、卡片→卡片 **8dp** ✓。问题在最后两项：**"全部虚拟机" 标题和它下面的卡片只隔 8dp，和卡片彼此之间的间距完全相同**，所以标题在视觉上塌进了列表里，根本起不到分组作用——NPatch 里任何"标题 + 内容"的分隔都比同级内容间距大一档。
- **怎么改**：
  - `ExpMachineListAdapter.java:315` 的下 padding 从 8dp 改 **12dp**（`theme.dp(8)` → `theme.dp(12)`），让标题→卡片 = 12dp，卡片→卡片保持 8dp，形成两档节奏。
  - `buildCountRow()`（`:231-255`）显式补 `LinearLayout.LayoutParams` 的 `bottomMargin = theme.dp(12)`，不要依赖标题的 top padding 兜底——一旦标题被删或换位置，间距就塌了。
  - 另：NPatch 的 `HomeScreen` **根本没有"全部虚拟机"这种分组标题**（`HomeScreen.kt:113-124` 只有 StatusCard / InfoCard / SupportCard 三个 item）。我们这个标题是 spec §5 自己加的，所以若要更像 NPatch，应该把标题去掉、改用 §30 的 `InfoCard` 来做节奏分隔。

### 9. 机器卡的说明文字缺 500 字重（13sp `packageName` 是 FontWeight(500)）

- **NPatch 做什么**：`AppItem` 的副文本 `packageName` 是 **13sp / FontWeight(500)**（`AppItem.kt:101-108`），`label` 是 **16sp / FontWeight(600)**（`:90-91`）。两行都有明确字重，不是默认 Regular。
- **我们做什么**：机器卡的 `name` 16sp 用了 `theme.medium`（`ExpMachineListAdapter.java:521`，= `sans-serif-medium` ≈ 500/600 ✓）；但 `meta` 13sp **完全没有设 typeface**（`:531-536` 只有 `setTextSize`），落回系统默认 Regular。
- **位置**：`ExpMachineListAdapter.java:531-536`（对比 `:521`）。
- **差距**：NPatch 的两行文字**都有字重**（600 + 500），层级靠"标题粗、副标题中粗"拉开。我们标题是 medium、副标题是 regular，副标题比 NPatch 轻一档，且和 `onSurfaceVariantSummary` 的低透明度叠加后显得发虚——这正是"卡片里的说明文字看起来糊"的原因。
- **怎么改**：`ExpMachineListAdapter.java:532` 之后补一行 `meta.setTypeface(theme.medium);`（`sans-serif-medium` ≈ FontWeight 500），让 13sp 说明文字达到 NPatch 的 500 字重。

---

## B. 顶栏层面

### 10. 收缩后的小标题是**左对齐**，NPatch/Miuix 是**居中**

- **NPatch 做什么**：Miuix `TopAppBarLayout` 把 title 放在 `baseX = (constraints.maxWidth - titlePlaceable.width) / 2`，即**水平居中**（`TopAppBar.kt:900-909`），并有 `TITLE_WIDTH_FRACTION = 0.9` 防止与 nav/action 撞车（`:1122`）。小标题字号 = `textStyles.title3.fontSize` = **20sp**，`FontWeight.Medium`（`TopAppBar.kt:732-733`）。
- **我们做什么**：`collapsedTitle` 放在 LinearLayout 的**最左**，前面只有 `band` 的 26dp padding，后面是 weight=1 的空白（`ExpTopBar.java:87-88`：`band.addView(collapsedTitle, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))`）。
- **位置**：`ExpTopBar.java:80-88`、`:73-74`（band padding）。
- **差距**：这是**顶栏最扎眼的差异**。NPatch 滚动后标题居中悬在 bar 正中，我们的是贴在 26dp 处靠左。视觉上一眼就"不是 HyperOS"。
- **怎么改**：
  - 改 `band` 的结构：把 `collapsedTitle` 放进一个 `weight=1, gravity=CENTER` 的容器，或直接给 `collapsedTitle` 设 `LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)` + `gravity = Gravity.CENTER`（文本自身居中）。
  - 但注意：居中后若左侧没有 navigation icon，标题会真的跑到屏幕正中；NPatch 首页 `HomeScreen` 的 top bar **没有 navigation icon 也没有 action**（`HomeScreen.kt:92-97` 只传了 title/scrollBehavior/haze），所以标题是真·居中。我们的 `+` 按钮在右侧（`ExpTopBar.java:90-101`），所以标题应该在"可用区"居中。可行的做法：把 `band` padding 设为左 = 26dp（`titlePadding`）、右 = 26dp + 48dp（action 占位），标题在剩余区居中；或者直接把 `+` 按钮去掉（见 §11），标题整条 bar 居中。

### 11. 顶栏的 `+` 按钮是 NPatch 首页没有的，且图标被裁切/着色方式不对

- **NPatch 做什么**：`HomeScreen` 的 `NPatchTopAppBar` **只有 title**，没有 `actions`、没有 `navigationIcon`（`HomeScreen.kt:92-97`）。新建入口在**页面内容里**（`+` 在卡片区）和底栏。
- **我们做什么**：band 右侧放了一个 48dp 的 `ImageView` 加载 `R.drawable.ic_plus`（`ExpTopBar.java:90-101`），padding 12dp（`:97`），`setColorFilter(theme.onSurface, SRC_IN)`（`:92`）。
- **位置**：`ExpTopBar.java:90-101`；`res/drawable/ic_plus.xml`。
- **差距**：① **结构差异**：NPatch 首页顶栏右侧是空的，我们的 `+` 让顶栏"左标题 + 右按钮"，与 NPatch 首页完全不同。② **着色 bug**：`ic_plus.xml` 的 path 是 `strokeColor="#FFFFFFFF"` + `fillColor="#00000000"`（只有描边，无填充）。用 `PorterDuff.Mode.SRC_IN` 染色时，`SRC_IN` 会取**源的 alpha** 乘目标色——描边是有 alpha 的所以能显示，但 `fillColor="#00000000"` 的透明部分也会把结果清成透明；实际表现是**图标偏细、边缘发虚**，且 2dp 描边在 24dp 下比 Miuix 的图标视觉重量轻得多。③ 尺寸：24dp 矢量被 `setPadding(12dp)` 塞进 48dp 目标 → 实际绘制区域 **24dp**（padding 挤掉了），但 `setPadding` 还会让 ImageView 的 `scaleType` 居中缩放，`adjustViewBounds` 未开，实际渲染 24×24 无 padding，等于图标**没有** 12dp 的视觉呼吸区。
- **怎么改**：
  - 删掉顶栏的 `+` 按钮（`ExpTopBar.java:90-101`），与 NPatch 首页对齐；"新建"入口已经在底栏（`ExpBottomBar.java:68-74`）和空状态里有。
  - 若必须保留：改用 `android:tint`（而非 `setColorFilter(SRC_IN)`），并给 `ic_plus.xml` 补 `fillColor`；图标绘制尺寸改 22dp（Miuix `Icon` 默认 24dp，`actionIconPadding` 16dp 已经是边距）。

### 12. 大标题滚动时**整体平移**，NPatch 只做透明度渐隐

- **NPatch 做什么**：`TopAppBar.kt:745-765`：`largeTitle` 只有 `graphicsLayer { alpha = largeTitleAlpha() }`，**没有 translationY**。alpha = `1f - (frac * 3f).coerceIn(0f, 1f)`（`:647-652`）。
- **我们做什么**：`largeTitle.setAlpha(largeAlpha)`（`ExpTopBar.java:143`）**并且** `largeTitle.setTranslationY(-theme.dpf(LARGE_TITLE_BLOCK_DP) * 0.5f * fraction)`（`:144`）——最多上移 **20dp**。
- **位置**：`ExpTopBar.java:144`。
- **差距**：我们的大标题在滚动时会向上"滑走"，NPatch 是**原地淡出**。而且整个 `ExpTopBar` 是**固定高度**的（`:117-119`，`expandedHeight` 恒定），list 也按固定 `expandedHeight` padding（`LimboExperimentalActivity.java:221`），所以标题上移 20dp 会在原地留下一个 20dp 的空洞，且**内容不会跟着顶栏收缩**——NPatch 的 bar 是真的会收缩高度（`TopAppBar.kt:862-879` 的 `barHeight = lerp(collapsedHeight, collapsedHeight + expansion, 1f - collapseFraction)`），内容被顶起。
- **怎么改**：
  - 删除 `ExpTopBar.java:144` 的 `setTranslationY` 调用，只保留 alpha 渐隐。
  - 更彻底：让顶栏高度真的收缩（把 `expandedHeight` 从固定值改成随 `fraction` 变化的实际高度，并同步 `list.setPadding(top, ...)`），才能得到 NPatch 那种"内容从大标题下方滑过去"的观感。最低限度也要在 `onScrolled` 里同步更新 list 的 top padding。

### 13. 顶栏模糊被简化成"半透明色块"，且没有模糊

- **NPatch 做什么**：`NPatchTopAppBar` 用 **haze 模糊**：`blurEffect { style = hazeStyle; blurRadius = 30.dp; noiseFactor = 0f }`（`NPatchTopAppBar.kt:50-60`），且只在 `collapsedFraction > 0.01f` 时才启用（`:44-49`）。模糊的是**下面滚动上来的内容**，不是一块固定色。
- **我们做什么**：`scrim` 是一块 `theme.topBarScrim` 纯色（`ExpTopBar.java:63`），用 `setAlpha` 控制淡入（`:158`），淡入阈值是 **24dp** 滚动（`:152`）。
- **位置**：`ExpTopBar.java:62-67`、`:151-158`；`colors_exp.xml:34` `#F2FFFFFF` / `:60` `#F2242424`。
- **差距**：① 没有真正的模糊（`GradientDrawable`/纯色做不到 blur），滚动内容会从半透明色块下面**透出来**且被冲淡，而不是被磨砂；② 阈值 24dp vs NPatch 的 `> 0.01f`（几乎立刻）；③ NPatch 的模糊**覆盖整个 bar 高度**（含 status bar 区），我们的 `scrim` 高度是 `collapsedHeight`（含 status bar）——这一条其实是对的。
- **怎么改**：
  - 无新依赖的前提下，用 `RenderEffect`（API 31+）做不到（`compileSdk 29`）。可行替代：把 scrim 的 alpha 提到接近不透明（`#FAFFFFFF` / `#FA242424`），并让 `topBarScrim` 覆盖**整条 expanded 高度**而非只到 `collapsedHeight`——否则大标题区域没有底，内容从大标题后面透出来会很脏。
  - 真正的 blur 需要 `RenderScript`（API 17+，已废弃但 `compileSdk 29` 可用）或自绘 `RenderEffect`；若不做，把 scrim 改为**完全不透明**（`#FFFFFF` / `#242424`）会更接近 NPatch 的观感。
  - 淡入阈值从 24dp 改 **0dp**（立即生效），对齐 `collapsedFraction > 0.01f`。

---

## C. 底栏层面

### 14. 底栏用 `setElevation(8dp)` 做阴影，NPatch 用 `dropShadow` 且半径 10dp / alpha 只有 0.1–0.2

- **NPatch 做什么**：`dropShadow(shape = pillShape, shadow = Shadow(radius = 10.dp, color = Color.Black, alpha = if (isInDark) 0.2f else 0.1f))`（`FloatingGlassBottomBar.kt:430-437`）。
- **我们做什么**：`bar.setElevation(theme.dpf(8f))`（`ExpBottomBar.java:66`）。
- **位置**：`ExpBottomBar.java:66`。
- **差距**：Android 原生 `elevation` 阴影在 API 21+ 会被系统渲染成**灰色扩散阴影**（在浅色底上是明显的灰色光晕），和 NPatch 的 10dp/10% 黑色柔和阴影完全是两种东西；`setElevation` 还会给整个 bar 加一层 Material 的 elevation overlay 着色。视觉上我们的底栏"浮"得太重、太灰。
- **怎么改**：去掉 `setElevation`；改用 `setLayerType(LAYER_TYPE_SOFTWARE, null)` + `setShadowLayer(radius = 10dp, dx = 0, dy = 2dp, color = 0x1A000000)`（浅色 alpha 0.1 = `0x1A000000`，深色 0.2 = `0x33000000`），或者干脆不画阴影（NPatch 默认 `useFloatingGlassBottomBar = false`，即用 `NavigationBar`）。

### 15. 底栏容器是 `#F2FFFFFF`（95% 白），NPatch 是 `surfaceContainer.copy(0.4f)`——即**更透明**，且我们没有模糊

- **NPatch 做什么**：LiquidGlass 模式下 `containerColor = colors.containerColor.copy(0.4f)`（`FloatingGlassBottomBar.kt:217-220`），其中 `containerColor` 默认 = `surfaceContainer`（`:117`）。所以浅色下底栏是 **#FFFFFF @ 40% 不透明度**，靠 `drawBackdrop` 的真实模糊（`blur(4.dp)` + `vibrancy()` + `lens()`，`:446-449`）产生液态玻璃感。
- **我们做什么**：`exp_light_bottom_bar = #F2FFFFFF`（**95% 不透明**，`colors_exp.xml:39`），`exp_dark_bottom_bar = #F2242424`（95%，`:61`），纯色、无模糊。
- **位置**：`colors_exp.xml:39`、`:61`；`ExpBottomBar.java:62`。
- **差距**：95% 的白几乎就是纯白，**没有玻璃感**——底栏看起来像一个贴底的白色条，而不是悬浮胶囊。这是"底栏不像 NPatch"的头号原因。
- **怎么改**：把 alpha 降到 **0.4**（= 102/255）：浅色 `#66FFFFFF`，深色 `#66242424`（`colors_exp.xml:39`、`:61`）。没有模糊支撑的话，40% 的透明会让底栏几乎隐形——所以**同时**必须给它一个描边（浅色 `#1A000000` 20% 黑 / 深色 `#33FFFFFF`）来界定胶囊轮廓，否则边界不可见。

### 16. 底栏 tab **永远不选中**，选中态只在按下瞬间出现——NPatch 有持久选中态

- **NPatch 做什么**：`FloatingBottomBar` 有 `currentIndex` 持久状态（`FloatingGlassBottomBar.kt:244`），选中 tab 有**常驻**的 `primary @ 15%` 胶囊指示器（`:591-594`）+ `primary` 色的图标和文字（`:685-703`、`:726-749`）；未选中是 `onSurfaceVariantSummary`。`MainScreen.kt:154-158` 里 `selected = settledPage == index` 是**持久**的。
- **我们做什么**：`indicator` 初始 `alpha = 0f`（`ExpBottomBar.java:122`），只在 `ACTION_DOWN` 时设 1、`ACTION_UP` 时设 0（`:157-179`）。代码注释自己承认了（`ExpBottomBar.java:36-38`：*"This screen has two action items rather than navigation tabs, so there is no persistent selection"*）。
- **位置**：`ExpBottomBar.java:115-123`、`:154-179`；`ExpBottomBar.java:36-38`（注释）。
- **差距**：底栏**永远是一灰到底**，没有任何强调色。NPatch 底栏永远有一个蓝色胶囊 + 蓝色图标文字。这一项对"像不像"的权重极高——是全屏唯一的高饱和蓝色，缺了它整个屏幕就没有视觉焦点。
- **怎么改**：给底栏一个持久的"当前页"状态。例如把"新建"设为默认选中（或加第三个"虚拟机" tab 作为首页），选中态用 `primary @ 15%` 胶囊 + `primary` 文字/图标常驻，未选中保持 `onSurfaceVariantSummary`；按下时额外加 scale/alpha 反馈。

### 17. 底栏 tab 文字用 `theme.medium`（SemiBold 感），NPatch 未选中是 Normal、选中才 SemiBold

- **NPatch 做什么**：`fontSize = 11.sp, lineHeight = 14.sp`，`fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal`（`FloatingGlassBottomBar.kt:739-748`）。
- **我们做什么**：11sp 正确（`ExpBottomBar.java:145`），但 `label.setTypeface(theme.medium)`（`:146`）——**两种状态都是 SemiBold**，且按下时只换颜色（`:176`），**不换字重**。
- **位置**：`ExpBottomBar.java:146`、`:174-176`。
- **差距**：未选中 tab 应该是 Normal 字重，我们的两个 tab 都是 Medium，底栏整体偏重。NPatch 靠字重差（Normal ↔ SemiBold）+ 颜色差（灰 ↔ 蓝）双通道表达状态。
- **怎么改**：`ExpBottomBar.java:146` 改为 `Typeface.create("sans-serif", Typeface.NORMAL)`；在按下/选中逻辑里（`:174-176`）同步切换 typeface 为 `theme.medium`。

### 18. 底栏 `11sp` 文字没有 `lineHeight 14.sp`

- **NPatch 做什么**：`fontSize = 11.sp, lineHeight = 14.sp`（`FloatingGlassBottomBar.kt:741-742`）。
- **我们做什么**：只设了 `setTextSize(11f)`，没有 `setLineSpacing`（`ExpBottomBar.java:145`）。
- **位置**：`ExpBottomBar.java:145`。
- **差距**：行高由系统默认决定（通常 1.3–1.4×），在 11sp 下约 14–15sp，接近但不受控；图标与文字的 `1.dp` 间距（`ExpBottomBar.java:140-142`，正确）在没有固定行高时视觉间距会漂。
- **怎么改**：`ExpBottomBar.java:145` 之后补 `label.setLineSpacing(theme.dpf(14f - 11f * lineHeightFactor), 1f)`，或用 `setLineSpacing(0, 14f/11f)` 把行高压到正好 14sp。

### 19. 底栏 tab 之间的 2dp 间隔是 NPatch 没有的，NPatch 是 `weight(1f)` 等分

- **NPatch 做什么**：每个 tab 是 `defaultMinSize(minWidth = 76.dp).weight(1f).fillMaxHeight()`（`FloatingGlassBottomBar.kt:346-370`）——**等宽平分**整个 bar 的内容宽度，之间**没有** gap。
- **我们做什么**：`newParams.setMargins(0, 0, theme.dp(2), 0)`、`settingsParams.setMargins(theme.dp(2), 0, 0, 0)`（`ExpBottomBar.java:85`、`:90`）——两个 tab 之间有 2dp 缝。
- **位置**：`ExpBottomBar.java:83-91`。
- **差距**：2dp 的缝会把胶囊视觉上切成两半，NPatch 的胶囊是一个连续的整体。另外我们的 tab 用 `WRAP_CONTENT` 宽度 + `minWidth 76dp`（`ExpTheme.java:56`），而 NPatch 是 `weight(1f)` 平分——两 tab 时视觉宽度会不同（内容多的那个更宽）。
- **怎么改**：`ExpBottomBar.java:83-91` 改成两个 `LinearLayout.LayoutParams(0, theme.dp(56), 1f)`（去掉 2dp margin），这样两个 tab 等宽。

### 20. 底栏整体左右没有 12dp 边距，NPatch 有

- **NPatch 做什么**：`FloatingGlassBottomBar(modifier = Modifier.padding(horizontal = 12.dp))`（`MainScreen.kt:144`），外层 Box 也有 `padding(bottom = 12.dp + navBarsBottomPadding)`（`:128-130`）。
- **我们做什么**：`barParams` 只有 `gravity = BOTTOM | CENTER_HORIZONTAL` 和 `bottomMargin`（`ExpBottomBar.java:93-96`），**没有水平边距**。
- **位置**：`ExpBottomBar.java:93-97`。
- **差距**：我们的胶囊宽度 = 内容宽度 + 4dp padding + 2×76dp minWidth ≈ 156dp，居中；NPatch 的胶囊是**横向撑满（减 12dp×2）**的通栏胶囊。视觉形态完全不同：一个是"小圆角药丸"，一个是"底部通栏胶囊"。
- **怎么改**：`ExpBottomBar.java:93-96` 的 `LayoutParams` 改成 `MATCH_PARENT` 宽 + `leftMargin/rightMargin = theme.dp(12)`，让胶囊通栏。

### 21. 底栏离底 12dp 是对的，但**没算导航栏 inset**，且导航栏被涂成了页面底色

- **NPatch 做什么**：`padding(bottom = 12.dp + navBarsBottomPadding)`（`MainScreen.kt:129`），`navBarsBottomPadding` 来自 `WindowInsets.navigationBars`（`:76`）。
- **我们做什么**：`barParams.bottomMargin = theme.dp(ExpTheme.BOTTOM_BAR_MARGIN_DP)` = 12dp（`ExpBottomBar.java:96`），**没有导航栏 inset**。同时 `window.setNavigationBarColor(theme.background)`（`LimboExperimentalActivity.java:134`）+ 只设了 `LAYOUT_FULLSCREEN`（没有 `LAYOUT_HIDE_NAVIGATION`），所以内容**不会**延伸到导航栏下面，12dp 是相对导航栏顶部的。
- **位置**：`ExpBottomBar.java:96`；`LimboExperimentalActivity.java:134`、`:137`。
- **差距**：技术上"能看"，但**导航栏被涂成页面色**（`theme.background`），而 NPatch 是**全透明导航栏**（`MainActivity.kt:131-133` `enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT))`），内容从导航栏后面穿过——这是 HyperOS 的标志性观感。我们这一版导航栏是一块**实心灰/白色横条**，视觉上把屏幕"截断"了。
- **怎么改**：
  - `LimboExperimentalActivity.java:134` 改成 `window.setNavigationBarColor(Color.TRANSPARENT)`。
  - `LimboExperimentalActivity.java:137` 的 flags 增加 `View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION`，让内容延伸到导航栏下方。
  - 底栏 bottomMargin 改为 `12dp + navBarHeight`（用 `getIdentifier("navigation_bar_height", "dimen", "android")` 读，和 `:160-169` 读 status bar 的方式一致）。
  - `updateListPadding()`（`LimboExperimentalActivity.java:222`）的 bottom 也要加上 nav bar inset。

---

## D. 颜色 / 主题令牌层面

### 22. `ExpTheme` 里有 6 个令牌是死代码，说明当初想抄 NPatch 的语义但没落地

- **NPatch 做什么**：这些令牌在真机里都有实际用途（`dividerLine` 用于 `HorizontalDivider`、`onSurfaceContainerVariant` 用于卡片内次要文字、`warning` 用于 StatusCard 告警态、`onSurfaceVariantActions` 用于可点击项、`surface` 用于 Scaffold 底色、`surfaceContainerHighest` 用于高对比表面）。
- **我们做什么**：`surface`（`ExpTheme.java:69`）、`onSurfaceSecondary`（`:72`）、`onSurfaceVariantActions`（`:74`）、`onSurfaceContainerVariant`（`:75`）、`dividerLine`（`:77`）、`warning`（`:82`）——**全部零引用**（`grep -rn "theme\.surface[^C]"` 等验证：只有赋值，无任何读取）。此外 `surfaceContainerHighest`（`colors_exp.xml:47` `#2D2D2D`）虽然被 `ExpTheme` 声明为 public 字段，但 `ExpTheme` 自身也从未从 `R.color.exp_dark_surface_container_highest` 读它。
- **位置**：`ExpTheme.java:69`、`:72`、`:74`、`:75`、`:77`、`:82`；`colors_exp.xml:19`、`:22`、`:24`、`:25`、`:27`、`:32`、`:47`。
- **差距**：这直接对应 §1（深色卡片底）、§7（告警图标）、§9（次要文字字重）等条目——**令牌有了但没人用**，说明实现是"贴皮"式的：只贴了能贴的部分。
- **怎么改**：逐个落地——`warning` → §7 告警状态卡（图标 tint + 底色）；`surfaceContainerHighest` → §1 深色卡片底（需在 `ExpTheme.java` 补 `surfaceContainerHighest = res.getColor(R.color.exp_dark_surface_container_highest);`）；`surface` → 若改页面底色用它（但 NPatch 用的是 `background`，见 §1，**建议直接删掉这个令牌**）；`onSurfaceContainerVariant` → 卡片内次要文字；`onSurfaceVariantActions` → "启动"等次要操作文字；`dividerLine` → NPatch `AppItem` 用 8dp 间距代替分隔线，**可以不落地，但要删掉死令牌**；`onSurfaceSecondary` → 若无使用场景则删。

### 23. 浅色模式给所有卡片描 1px 描边，深色模式完全没有描边 —— 两套都不对

- **NPatch 做什么**：Miuix `Card` **没有任何描边**（`Card.kt:173-178` 的 `squircleSurface` 只有 `color` + `cornerRadius`）。描边感来自模糊/阴影。
- **我们做什么**：`lightThemeStroke()` 返回 `theme.dark ? 0 : theme.outline`（`ExpMachineListAdapter.java:415-417`），用于状态卡（`:153`）和统计卡（`:262`）；机器卡同理（`:489-490`）。
- **位置**：`ExpMachineListAdapter.java:153`、`:262`、`:489-490`、`:415-417`。
- **差距**：浅色下 `#D9D9D9` 的 1px 描边是典型"Material Card"观感，与 HyperOS 的"素色 + 极淡模糊"正好相反；深色下 `#404040` 描边被去掉后，卡片与页面同色 `#242424`，彻底失去边界。
- **怎么改**：**两侧都去掉描边**（`lightThemeStroke()` 直接返回 0，或删掉该方法），改用**极淡阴影**统一处理：`setElevation(theme.dpf(1f))`（浅色）/ 卡片底色改 `surfaceContainerHighest`（深色，见 §1）。

### 24. 机器卡片的"启动中/运行中"语义颜色用了 `primary @ 12%`，NPatch 的 AppItem 不用自造色

- **NPatch 做什么**：`AppItem` 的副文本是 `onSurfaceVariantSummary`（`AppItem.kt:105`）；唯一的强调色是 `summaryRow` 里的 `primary` 文字（`AppManagePage.kt:248`），**没有**额外的胶囊底色。
- **我们做什么**："启动"胶囊底色 `ExpDrawables.alpha(theme.primary, 0.12f)`（`ExpMachineListAdapter.java:578-579`），文字 `theme.primary`（`:573`），13sp（`:571`）。
- **位置**：`ExpMachineListAdapter.java:570-585`。
- **差距**：`primary @ 12%` 不是任何令牌（NPatch 只有底栏的 `primary @ 15%`），且这与底栏选中态的 `primary @ 15%` 撞色，导致**两处不相干的强调色用同一个非标准比例**，视觉上互相干扰。整页出现 3 种强调（状态卡标题的 primary、启动胶囊的 primary@12、底栏的 primary@15）——NPatch 全屏只有 1 处持续强调。
- **怎么改**：按 §4 的建议删掉启动胶囊；若保留，改用 NPatch 的标准 `primary @ 15%`（与底栏一致），或降级为纯文字 `primary` 13sp 无底色（`AppManagePage.kt:248` 的做法）。

---

## E. 交互 / 反馈层面

### 25. `sink()` 按压反馈用 `setAlpha` 覆盖整个 view，RecyclerView 复用时残留

- **NPatch 做什么**：`SinkFeedback` 是 Compose 的 `graphicsLayer` 缩放 + `MiuixIndication` 的 alpha 增量（`PressFeedback.kt:32` `sinkAmount = 0.94f`、`MiuixIndication.kt:27` `PRESS_ALPHA_DELTA = 0.10f`、`MiuixIndication.kt:70` `if (isPressed) targetAlpha += PRESS_ALPHA_DELTA`）。注意是**叠加**，不是替换。
- **我们做什么**：`ExpDrawables.sink()` 在 DOWN 时 `v.setAlpha(1f - PRESS_ALPHA_DELTA)` = **0.90f**（`ExpDrawables.java:73`），UP 时 `v.setAlpha(1f)`（`:78`）——**直接覆盖**了 view 自身的 alpha。
- **位置**：`ExpDrawables.java:65-88`、`:73`、`:78`。
- **差距**：① **直接覆盖 alpha**，若 view 本身有 alpha（RecyclerView 复用、动画中），会被踩成 0.90；② UP 时硬设为 `1f`，如果 view 本来就该是半透明（例如禁用态），会被错误重置。代码在 `bindMachine` 里已经不得不手动复位（`ExpMachineListAdapter.java:370-372`：`setScaleX(1f); setScaleY(1f); setAlpha(1f)`）——这正是该缺陷的**症状**，而不是修复。
- **怎么改**：
  - `ExpDrawables.java:73` 改为**相对叠加**：`v.setAlpha(Math.max(0f, v.getAlpha() - PRESS_ALPHA_DELTA))`；`:78` 改为恢复按下前记录的值（DOWN 时 `final float base = v.getAlpha();` 存起来，UP 时 `v.setAlpha(base)`）。
  - 保留 `ExpMachineListAdapter.java:370-372` 的复位作为兜底（RecyclerView 复用确实需要），但它不该是唯一防线。

### 26. `sink()` 在 `ACTION_MOVE` 时不更新，快速滑动会"卡住"在缩小态

- **NPatch 做什么**：Compose 的 interaction source 会正确处理 MOVE 移出边界 → 触发 `onPressEnd`。
- **我们做什么**：`ExpDrawables.java:80-82` 的 `default: break;`——MOVE 事件被忽略；若手指按下后**滑出卡片再抬起**，抬起时是 ACTION_UP，仍会恢复。但如果按下后滑出后**系统接管**（ACTION_CANCEL）…… 实际上 `ACTION_CANCEL` 是被处理了的（`:75`）。真正的漏洞是：**在 ACTION_DOWN 之后、ACTION_UP 之前，视口滚动的 RecyclerView 会收到 CANCEL**，这条已覆盖。
- **位置**：`ExpDrawables.java:66-86`。
- **差距**：风险较低（实现基本正确），但 `default: break` 意味着 `ACTION_POINTER_DOWN`（多指）等事件不清状态。
- **怎么改**：在 `default` 分支里，对 `MotionEvent.ACTION_POINTER_DOWN` 也执行一次"复位"，或直接改为记录 `pressed` 布尔并在 `ACTION_UP`/`CANCEL` 统一复位（与 `ExpBottomBar.java:157-179` 已有的写法一致）。

### 27. RecyclerView 关闭了 overscroll，但 NPatch 用 `overScrollVertical()` 的弹性反馈

- **NPatch 做什么**：`LazyColumn(... .scrollEndHaptic().overScrollVertical().nestedScroll(...))`（`HomeScreen.kt:100-106`）。
- **我们做什么**：`list.setOverScrollMode(View.OVER_SCROLL_NEVER)`（`LimboExperimentalActivity.java:180`），且没有 scrollEnd haptic。
- **位置**：`LimboExperimentalActivity.java:180`。
- **差距**：没有超滚回弹和滚动到底的触感——这是"手感"，用户说不出但能感觉到。原生 View 无法完全复刻 Compose 的 `overScrollVertical`（它是自定义绘制的拉伸效果），但可以：去掉 `OVER_SCROLL_NEVER` 恢复系统 glow（Android 12+ 的 stretch overscroll 已经很接近），并在滚动到底时调 `performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)`。
- **怎么改**：`LimboExperimentalActivity.java:180` 改为 `list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS)`；在 `onScrolled` 里检测 `!canScrollVertically(1)` 且 `dy > 0` 时 `performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)`。

### 28. 空状态的"新建虚拟机"是 Material FAB 风格药丸，NPatch 里根本没有这种控件

- **NPatch 做什么**：`HomeScreen` 无空状态（NPatch 至少有 StatusCard/InfoCard/SupportCard）。本仓库 spec §5 末行明确要求：*"空列表时：不显示 StatusCard 之外的内容，中间放 13sp onSurfaceVariantSummary 的提示文案 + 主色 FAB/胶囊按钮"新建虚拟机""*。
- **我们做什么**：`getItemCount()` 空时返回 2（`ExpMachineListAdapter.java:102`），`bindHeader` 空时**只加状态卡就 return**（`:141-143`），空状态在**第二个 item**（`EmptyHolder`）。所以实际顺序是：状态卡 → 空状态提示 → 按钮。
- **位置**：`ExpMachineListAdapter.java:102`、`:137-146`、`:323-364`。
- **差距**：这基本**符合** spec（提示文案 13sp `onSurfaceVariantSummary` ✓、`ExpMachineListAdapter.java:327-335`；primary 胶囊按钮 ✓、`:341-363`）。但按钮是 **15sp medium + 24/12dp padding + 48dp 圆角**（`:342-352`），spec 没给按钮尺寸，NPatch 里也没有对应控件（NPatch 用 `ArrowPreference` 列表项）。真正的问题是**这个按钮的形态在 NPatch 里根本不存在**——它是 Material FAB 风格的药丸，和整个 HyperOS 风格不搭。
- **怎么改**：把空状态按钮改成 **NPatch 的 `ArrowPreference` 形态**：一行 `16sp Medium` 标题 + `13sp onSurfaceVariantSummary` 副标题 + 右侧 18dp 箭头（`onSurface @ 40%`），包在 `surfaceContainer` 卡片里、`16dp` 圆角、`12dp` 水平边距、`8dp` 下边距（照抄 `ExpMachineListAdapter.java:299-305` 已有的箭头实现）。如果一定要保留药丸，则去掉 `primary` 实心底，改成 tertiaryContainer 底 + onTertiaryContainer 字。

### 29. `scrollToList()` 滚到的是 position 1，而 position 1 在空列表时不存在

- **NPatch 做什么**：统计卡的 `onClick` 是**切换 tab**（`HomeScreen.kt:244-255` → `onManageShortcut(0/1)` → `navigator.navigate(Route.Manage)`），不是滚动。
- **我们做什么**：`onTotalCountClick()` / `onRunningCountClick()` 都调 `scrollToList()`（`LimboExperimentalActivity.java:321-328`），后者 `list.smoothScrollToPosition(1)`（`:355`）。
- **位置**：`LimboExperimentalActivity.java:320-328`、`:350-356`。
- **差距**：① 语义不同（NPatch 是导航，我们是滚动）——但这是 Limbo 语境的合理映射，**可接受**；② 真 bug：`smoothScrollToPosition(1)` 在**空列表**时 position 1 是空状态 item，滚动目标错误；虽然 `getItemCount() <= 1` 的守卫（`:352`）在空列表时 itemCount 是 2，**守卫失效**，会滚到空状态。③ "运行中"计数卡的点击也滚到"全部虚拟机"列表，没有过滤到运行中的机器——**功能上是空的**。
- **怎么改**：
  - `:352` 的守卫改为 `adapter.getItemCount() <= 2`（有机器时才有列表 item）。
  - `onRunningCountClick()` 应过滤列表到运行中的机器，而不是滚到全部：给 adapter 增加 `setShowRunningOnly(boolean)`，在 `getItemViewType`/`getItemCount` 里过滤 `entries`（`ExpMachineListAdapter.java:99-111`），点击时切到该模式并滚动到 position 1。

---

## F. 结构性缺失（不是 bug，而是"少了 NPatch 的东西"）

### 30. 我们没有 NPatch 的 `InfoCard` 和 `SupportCard`

- **NPatch 做什么**：`HomeScreen` 的 LazyColumn 有 **3 个 item**：`StatusCard` / `InfoCard` / `SupportCard`（`HomeScreen.kt:113-124`）。`InfoCard` 是设备/版本信息卡（`HomeScreen.kt:345-398`），`SupportCard` 是关于/宣传卡（`:403-427`）。
- **我们做什么**：只有 `StatusCard` + `CountCard×2` + 机器列表（`ExpMachineListAdapter.java:137-146`）。
- **位置**：`ExpMachineListAdapter.java:137-146`。
- **差距**：NPatch 首页在两张统计卡**下面**还有两张内容卡，页面因此有"上中下"三段式节奏。我们的页面在统计卡后直接是列表标题，**中间没有"呼吸"**，这是"看起来像一张普通设置列表"的原因之一。
- **怎么改**：在 `bindHeader`（`:137-146`）的 `buildCountRow()` 之后、`buildSectionTitle()` 之前插入一张 Limbo 语境的 `InfoCard`：显示 Limbo 版本、引擎版本、宿主 ABI 等（对应 NPatch 的 API Version / NPatch Version / Framework Version 三行），用 `InfoText` 的结构：**标题 `headline1` 17sp Medium `onSurface`** + **内容 `body2` 14sp `onSurfaceVariantSummary`**（`HomeScreen.kt:306-321`）。每行间距 2dp，行底 10dp，卡片内边距 16dp（`:369-370`）。

### 31. 机器列表没有用 `AppItem` 的 `basicMarquee`（滚动跑马灯），长名字被截断

- **NPatch 做什么**：`label` 用 `Modifier.weight(1f, fill = false).basicMarquee()`（`AppItem.kt:85-95`），长名字**横向滚动**而不是省略号；`packageName` 用 `softWrap = false, maxLines = 1`（`:101-108`）也是跑马灯。
- **我们做什么**：`name.setEllipsize(TextUtils.TruncateAt.END)` + `maxLines(1)`（`ExpMachineListAdapter.java:523-524`），`meta` 同样 END 省略（`:534-535`）。
- **位置**：`ExpMachineListAdapter.java:519-536`。
- **差距**：长机器名直接被"…"，而 NPatch 是滑动显示。原生 View 可以用 `android:ellipsize="marquee"` + `android:focusable="true"` + `android:selected="true"`（或 `TextView.setSelected(true)`）实现跑马灯。
- **怎么改**：`ExpMachineListAdapter.java:523-524` 与 `:534-535` 改为 `setEllipsize(TextUtils.TruncateAt.MARQUEE)`，并在 `bindMachine`（`:373-374` 附近）调 `holder.name.setSelected(true); holder.meta.setSelected(true);`（`TextView` 需要 `isSelected()` 为 true 才播放 marquee）。

### 32. 卡片点击是"编辑"，NPatch `AppItem` 点击也是打开，但我们的**长按删除**没有任何视觉提示

- **NPatch 做什么**：`AppItem` 的 `onLongPress` 传下去（`AppItem.kt:57`），但 NPatch **没有**任何"删除确认弹窗"的实现——删除在 `AppDetailScreen` 里。
- **我们做什么**：长按卡片直接弹 `AlertDialog` 确认删除（`ExpMachineListAdapter.java:388-394` → `LimboExperimentalActivity.java:499-522`）。
- **位置**：`ExpMachineListAdapter.java:388-394`；`LimboExperimentalActivity.java:499-522`。
- **差距**：① **发现性**：用户不知道长按会删除（NPatch 里没有这个手势）；② **弹窗样式**：`new AlertDialog.Builder(this)` 用的是 **Material/AppCompat 默认 AlertDialog**（`:505`、`:472`），带 Material 的圆角、按钮文字大写/蓝色——**与整个 HyperOS 素色风格冲突**，这是"点一下弹出来的东西完全不像 NPatch"的最大单点；③ 弹窗按钮的文字颜色取自 activity theme，与 exp 主题 token 无关（代码注释 `:462-464` 自己也承认了）。
- **怎么改**：
  - 用 `AlertDialog.Builder` 的自定义 `setView()` 造一个 NPatch 风格弹窗：内容用 `surfaceContainer` 圆角 16dp 卡片、标题 17sp Bold `onSurface`、正文 14sp `onSurfaceVariantSummary`、按钮文字 `primary` 15sp（**不用 Material 默认按钮**）。
  - 或者改掉手势：把删除放进"机器详情"页（点击卡片 → 详情页 → 顶部栏菜单里删除），这才是 NPatch 的做法。

### 33. "新建虚拟机" 弹窗同样是 Material AlertDialog

- **NPatch 做什么**：NPatch 的新建页是**独立页面**（`Route.NewPatch`，`HomeScreen.kt:85`），不是弹窗。
- **我们做什么**：`promptNewMachine()` 用 `new AlertDialog.Builder(this).setTitle(...).setView(container)`（`LimboExperimentalActivity.java:472-496`），输入框是原生 `EditText`（`:458`）。
- **位置**：`LimboExperimentalActivity.java:457-497`。
- **差距**：同一个问题——Material 弹窗的圆角/配色/按钮与 HyperOS 素色风格不符；`EditText` 的下划线输入框也不是 HyperOS 风格（应该无边框、扁平）。
- **怎么改**：至少自定义 `setView()` 用 `ExpTheme` 的 token（`surfaceContainer` / `outline` 描边 / `primary` 焦点色），把 `EditText` 的 `background` 换成 `bg_row`-style 的扁平输入条。

---

## G. 规格文件自身的错误（建议一并修正，否则会照着改错）

| spec 位置 | spec 写的 | NPatch/Miuix 真机 | 证据 |
|---|---|---|---|
| §6.3 底栏 | "每个 tab 最小宽度 76dp" + "位置 Alignment.BottomCenter（不是距底 24dp 悬浮）" | tab 是 `weight(1f)` **等分**，不是固定 76dp；外层是 `padding(bottom = 12.dp + navBarsBottomPadding)` | `FloatingGlassBottomBar.kt:346-370`、`MainScreen.kt:128-130` |
| §6.3 底栏 | 未提左右边距 | `padding(horizontal = 12.dp)`，胶囊**通栏** | `MainScreen.kt:144` |
| §6.3 底栏 | "选中指示器 primary.copy(alpha = 0.15f)" ✓ | ✓ 正确，但**容器**是 `surfaceContainer.copy(0.4f)`（40% 透明），spec 未提 | `FloatingGlassBottomBar.kt:219` |
| §6.3 底栏 | "阴影：不用 setElevation… 干脆不加" | 真机用 `dropShadow(radius = 10.dp, alpha = 0.1/0.2)` | `FloatingGlassBottomBar.kt:430-437` |
| §4 结构 | "顶栏 → 状态卡 → 两张统计卡 → 列表" | 真机是"顶栏 → 状态卡 + 统计卡 → InfoCard → SupportCard" | `HomeScreen.kt:113-124` |
| §3.1 顶栏 | "滚动后：标题以约 18–20sp 显示在中部" | **居中** ✓，20sp ✓，Medium ✓（spec 表述含糊，导致我们实现成了左对齐） | `TopAppBar.kt:900-909`、`:732-733` |
| §6.2 顶栏 | "dy > 8 时给顶栏加半透明模糊背景" | 真机 `collapsedFraction > 0.01f` 即启用 **haze blur 30dp**（不是半透明色块） | `NPatchTopAppBar.kt:44-60` |
| §2 尺寸 | "图标尺寸（列表项）48.dp" ✓ | ✓，但 NPatch 的 AppItem 图标是 **14dp** 圆角的真图标，spec §6.4 写 12dp | `AppManagePage.kt:239-244` |
| §3 字号 | "headline1 17.sp 卡片主标题（StatusCard 用 17sp Bold）" | ✓ 正确 | `HomeScreen.kt:205-210` |

---

## 优先级建议

**P0（一眼可见，杀伤力最大）**
1. §10 小标题居中 — 顶栏
2. §6 状态卡标题 = 机器名、标题色改 `onSurface`
3. §7 状态卡图标改对勾 + 告警色
4. §16 底栏持久选中态（全屏唯一的蓝色焦点）
5. §15 底栏容器 95% → 40% 透明 + 描边
6. §20 底栏通栏（左右 12dp）

**P1（结构与节奏）**
7. §1 + §23 卡片去描边、深色用 `surfaceContainerHighest`
8. §4 + §5 机器卡尾槽改 12dp、徽标移到标题行、删"启动"胶囊
9. §2 卡片改 squircle
10. §30 补 InfoCard（页面三段式节奏）
11. §13 + §12 顶栏真收缩 + 不平移大标题

**P2（细节与手感）**
12. §3 图标圆角 14dp + 首字 18sp medium
13. §25 + §26 sink 按压反馈叠加而非覆盖
14. §31 跑马灯
15. §32 + §33 自定义 NPatch 风格弹窗
16. §27 overscroll 回弹 + scroll-end haptic
17. §29 计数卡点击真的过滤运行中列表
18. §22 清理死令牌（落地或删除）
