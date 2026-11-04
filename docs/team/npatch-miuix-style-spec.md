# 新界面设计规范：复刻 NPatch（Miuix / HyperOS 风格）

> 本文件是**唯一视觉基准**。写代码前必须读完，所有颜色/圆角/间距/字号都从这里取值，**不要凭感觉调**。
> 数据来源：`7723mod/NPatch` 的 `manager/src/main/java/top/nkbe/npatch/ui/**` 与
> `miuix-kotlin-multiplatform/miuix` 的 `theme/Colors.kt`、`basic/Card.kt`（Miuix 0.9.4）。
> 本地副本：`/tmp/npatch`、`/tmp/miuix`（可再查）。

## 0. 硬约束（我们的工程现实）

- 目标模块 `limbo-android-lib` 是 **Java 8 + 原生 View**，`compileSdk 29 / minSdk 21`。
- **禁止** Kotlin、Compose、任何新增 Gradle 依赖。只有：`android.*`、`androidx.appcompat`、`androidx.recyclerview`、`com.google.android.material`（1.1.0）、本仓库既有类。
- NPatch 用 Compose 写，**只能复刻它的视觉结果**，不能照搬代码结构。
- 不要用 `MaterialCardView` 自带的 elevation/阴影风格，HyperOS 是"扁平卡片 + 极淡描边/无阴影"。
- 圆角要**接近 squircle**：Miuix 的 `squircleSurface` 是超椭圆。用 `GradientDrawable` 做圆角矩形即可近似；若要更像，用 `Path` + 四段三次贝塞尔（见 §6）。

## 1. 颜色令牌（**必须逐字使用**）

### 浅色（Miuix `lightColorScheme`）
| 名称 | 值 | 用途 |
|---|---|---|
| primary | `#3482FF` | 主色、开关、选中态、强调数字 |
| primaryContainer | `#5D9BFF` | 主色容器 |
| onPrimary | `#FFFFFF` | 主色上的文字 |
| background | `#FFFFFF` | 窗口底色 |
| surface | `#F7F7F7` | 页面背景（比白色略灰） |
| surfaceContainer | `#FFFFFF` | **卡片底色** |
| onSurface | `#000000` | 主文字 |
| onSurfaceSecondary | `#CC000000` | 次要文字（80% 黑） |
| onSurfaceVariantSummary | `#99000000` | **说明文字**（60% 黑），NPatch 大量使用 |
| onSurfaceVariantActions | `#66000000` | 更弱的文字（40% 黑） |
| onSurfaceContainerVariant | `#959595` | 卡片内次要文字 |
| outline | `#D9D9D9` | 描边 |
| dividerLine | `#E0E0E0` | 分割线 |
| secondaryContainer | `#F0F0F0` | 次级容器（图标底、chip） |
| tertiaryContainer | `#EAF2FF` | 主色浅底（高亮块） |
| onTertiaryContainer | `#3482FF` | 主色浅底上的文字 |
| error | `#E94634` | 错误 |
| 警告色 | `#E65100` | NPatch 的 StatusCard 直接用死值 |

### 深色（Miuix `darkColorScheme`）
| 名称 | 值 |
|---|---|
| primary | `#277AF7` |
| primaryContainer | `#338FE4` |
| background | `#242424` |
| surface | `#000000` |
| surfaceContainer | `#242424` |
| surfaceContainerHighest | `#2D2D2D` |
| onSurface | `#F2F2F2` |
| onSurfaceSecondary | `#CCFFFFFF` |
| onSurfaceVariantSummary | `#80FFFFFF` |
| onSurfaceVariantActions | `#66FFFFFF` |
| onSurfaceContainerVariant | `#737373` |
| outline | `#404040` |
| dividerLine | `#393939` |
| secondaryContainer | `#434343` |
| tertiaryContainer | `#2B3B54` |
| onTertiaryContainer | `#4788ff` |
| error | `#F12522` |

**深色模式是默认目标**（HyperOS 用户多数用深色，且我们旧 UI 也是深色）。两套都要做，跟随系统 `uiMode`。

## 2. 尺寸与形状

| 令牌 | 值 | 说明 |
|---|---|---|
| 卡片圆角 `CardDefaults.CornerRadius` | **16.dp** | 所有卡片统一 16dp |
| 卡片 `insideMargin` | **12.dp** | 卡片内边距（AppItem） |
| 大卡片 `insideMargin` | **20.dp / 16.dp** | StatusCard 用 20，CountCard 用 16 |
| 卡片外边距 | 水平 **12.dp**，卡片间距 **8.dp** | `padding(horizontal=12.dp).padding(bottom=8.dp)` |
| 页面内容上边距 | innerPadding + **12.dp** | |
| 页面内容下边距 | innerPadding + **24.dp** + 悬浮栏高度 | |
| 图标尺寸（列表项） | **48.dp** | AppItem |
| 图标尺寸（状态卡圆形底） | **44.dp** 圆，内含 **26.dp** 图标 | StatusCard |
| 图标与文字间距 | **12.dp**（AppItem）/ **16.dp**（StatusCard） | |
| 分组间距 | **12.dp** | 卡片列之间 |
| 顶栏模糊半径 | **30.dp** | |
| 触摸反馈 | PressFeedbackType.Sink | 按下时整体**轻微缩小 + 变暗** |

## 3. 字号（Miuix `TextStyles.kt` 实测值）

| Miuix 令牌 | 字号 | 用在哪 |
|---|---|---|
| `title1` | **32.sp** | 顶栏大标题 largeTitle |
| `title2` | **24.sp** | 次级大标题 |
| `title3` | **20.sp** | 收缩后的小标题 |
| `title4` | **18.sp** | — |
| `headline1` | **17.sp** | 卡片主标题（StatusCard 用 17sp Bold） |
| `headline2` | **16.sp** | 列表项标题 |
| `subtitle` | **14.sp Bold** | 副标题 |
| `body1` | **15.sp** | 统计卡标题（CountCard 用 15sp Medium） |
| `body2` | **14.sp** | 普通正文 |
| — | **13.sp** | 卡片说明文字（NPatch 自定义） |
| — | **26.sp SemiBold** | 统计卡数字（NPatch 自定义） |

字重：Bold→`Typeface.BOLD`；SemiBold/Medium 在原生 View 下用 `Typeface.DEFAULT_BOLD` 或
`Typeface.create("sans-serif-medium", Typeface.NORMAL)`（后者更接近 Medium，优先用它）。

## 3.1 顶栏尺寸（`TopAppBarDefaults`）

| 令牌 | 值 |
|---|---|
| `CollapsedHeight` | **52.dp**（收缩后顶栏高） |
| `TitlePadding` | **26.dp** |
| `NavigationIconPadding` | **16.dp** |
| `ActionIconPadding` | **16.dp** |
| `LargeTitleBottomPadding` | **4.dp** |
| `SubtitleBottomPadding` | **8.dp** |

展开态顶栏高度 = statusBarHeight + 52dp + 大标题高度（约 32sp 行高 40dp）+ LargeTitleBottomPadding。
滚动后：大标题淡出，52dp 顶栏保留，标题以约 18–20sp 显示在中部。

字重对应：Bold→`Typeface.BOLD`，SemiBold→`Typeface.DEFAULT_BOLD`（或 `create(Typeface.DEFAULT, 600)` 不一定支持，用 BOLD），Medium→`Typeface.DEFAULT_BOLD` 偏重，可接受。

## 4. 页面结构（**这是"看起来一样"的核心**）

NPatch 首页 `HomeScreen` 从上到下：

```
[ TopAppBar ]  标题 "NPatch"，可折叠大标题，滚动后模糊（haze blur 30dp），透明底
   ↓ 12dp
[ StatusCard ]        大卡片，内含：44dp 圆形状态图标 + (17sp Bold 标题 / 13sp 说明)
   ↓ 12dp
[ Row: CountCard x2 ] 两张等宽卡片，各含 (15sp Medium 标题 / 26sp SemiBold 数字) + 右侧箭头
   ↓ 12dp
[ InfoCard ]          信息卡片
   ↓ 12dp
[ SupportCard ]       支持/宣传卡片
   ↓ (底部留白 + 悬浮栏高度)
[ FloatingGlassBottomBar ] 悬浮胶囊导航栏：圆角胶囊、毛玻璃、2 个 tab（首页/管理），选中项有主色底
```

要点：
1. **顶栏透明**，内容滚到顶栏下方时顶栏才出现模糊背景（`blurVisible = isScrolled`）。
2. **大标题**在顶栏里，滚动时收缩成小标题。
3. 所有卡片 **16dp 圆角、12dp 外边距、8dp 间距**。
4. 底部是**悬浮胶囊**，不是贴底的传统 tab bar；胶囊有阴影 + 毛玻璃。
5. 列表（ManageScreen 等）用 `AppItem`：48dp 图标 + 两行文字 + 可选右侧内容。

## 5. 我们要复刻的界面（Limbo 语境映射）

新 UI（`ui/exp`）是**虚拟机列表**。按 NPatch 首页结构映射：

```
[ TopAppBar ] 大标题 "虚拟机"，右侧 + 按钮（新建）
   ↓ 12dp
[ StatusCard ] 等价物：当前运行状态卡
   - 有 VM 在跑：44dp 圆形主色底 + 绿色/主色对勾图标
                 17sp Bold "正在运行"（机器名）
                 13sp "SDL · 4 核 · 2048 MB"
   - 没在跑：    44dp 圆形灰色底 + 图标
                 17sp Bold "没有运行中的虚拟机"
                 13sp "从下方选择一台机器启动"
   卡片可点击 → 跳到该机器的 SDL/VNC 界面
   ↓ 12dp
[ Row: CountCard x2 ]
   左：15sp "虚拟机" / 26sp 数量        → 点击滚动到列表
   右：15sp "运行中" / 26sp 数量        → 点击滚动到运行中
   ↓ 12dp
[ 列表标题 ] "全部虚拟机"（headline 字重 Medium）
   ↓ 8dp
[ 每台机器一张卡片 ]  16dp 圆角 / 12dp 水平外边距 / 8dp 间距 / insideMargin 12dp
   ├ 48dp 圆角方形图标（取机器名首字，底色按名字 hash 从一组柔和色中取）
   ├ 12dp
   ├ (16sp Medium 机器名 / 13sp onSurfaceVariantSummary "x86_64 · 4 核 · 2048 MB")
   └ 右侧：16sp 箭头 或 "运行中" 小徽标（tertiaryContainer 底 + onTertiaryContainer 字，圆角 999dp）
   点击 = 编辑；长按 = 弹出删除确认
   ↓ 底部留白
[ FloatingGlassBottomBar ] 胶囊：左"新建"（+ 图标 + 文字），右"设置"（齿轮 + 文字）
```

空列表时：不显示 StatusCard 之外的内容，中间放 13sp onSurfaceVariantSummary 的提示文案 + 主色 FAB/胶囊按钮"新建虚拟机"。

## 6. 实现要点（原生 View）

### 6.1 卡片
```java
GradientDrawable bg = new GradientDrawable();
bg.setShape(GradientDrawable.RECTANGLE);
bg.setCornerRadius(dp(16));                 // 16dp
bg.setColor(cardColor);                     // surfaceContainer
// 深色不需要描边；浅色可加 1px outline 可选
view.setBackground(bg);
view.setClipToOutline(false);
```
按下反馈：`view.setOnTouchListener` 或 `View.OnClickListener` + `StateListAnimator` 简化版：
```java
// 按下：alpha 0.92 + scale 0.98；抬起恢复
```

### 6.2 顶栏
- 高度 = statusBarHeight + 56dp；`fitsSystemWindows` 处理。
- 透明背景；内容顶部 PaddingTop = statusBarHeight。
- 用 `RecyclerView.OnScrollListener` 监听滚动：`dy > 8` 时给顶栏加半透明模糊背景（`#F2FFFFFF` / `#F2242424`），滚回顶部时移除。
- 大标题：`TextView` 34sp Bold，随滚动 `setScaleX/Y` 或改变 padding 收缩；简单做法是滚动后 `setVisibility(GONE)` 显示小标题 20sp。

### 6.3 悬浮胶囊底栏
```java
LinearLayout bar;                 // horizontal，gravity center
GradientDrawable pill = new GradientDrawable();
pill.setCornerRadius(dp(28));     // 高 56dp → 半径 28 做成胶囊
pill.setColor(isDark ? 0xF2242424 : 0xF2FFFFFF);
bar.setBackground(pill);
bar.setElevation(dp(8));
// 距底部 24dp + navigationBar 高度；水平居中，宽度 wrap_content，内边距 8dp
// 每个 tab：图标 22dp + 文字 14sp，选中项背景 = primary 圆角胶囊(#3482FF)，文字白色；未选中 = 透明，文字 onSurfaceVariantSummary
```

### 6.4 圆角方形图标（机器首字）
```java
GradientDrawable icon = new GradientDrawable();
icon.setShape(GradientDrawable.RECTANGLE);
icon.setCornerRadius(dp(12));     // 48dp 方块 → 12dp 圆角
icon.setColor(badgeColor);        // 按 name.hashCode() 从 6 色中取
// 里面一个 TextView 22sp Bold 白色，居中
```

### 6.5 状态徽标
```java
GradientDrawable badge = new GradientDrawable();
badge.setCornerRadius(dp(999));   // 全圆
badge.setColor(tertiaryContainer);   // #EAF2FF / #2B3B54
// TextView 12sp，文字色 onTertiaryContainer，padding H 8dp V 3dp
```

## 7. 禁止事项

- ❌ 不要用 Material 的 `CardView` 默认阴影（HyperOS 是平的）。
- ❌ 不要用纯黑/纯白硬编码代替令牌（必须用上面表里的值）。
- ❌ 不要做"渐变按钮"、"玻璃拟态大背景"——NPatch 是**素色 + 极轻模糊**。
- ❌ 不要引入旧 UI 的 `GlassBlurView` / `AuroraBackgroundView` / `AppBackgroundView`。
- ❌ 不要改 `limbo-android-lib/src/main/java/com/max2idea/android/limbo/main/**` 里旧界面的任何文件。

## 8. 验收

1. 深色/浅色下都符合 §1 令牌。
2. 卡片圆角 16dp、水平 12dp 外边距、卡片间 8dp。
3. 首页结构与 §5 一致：顶栏 → 状态卡 → 两张统计卡 → 列表 → 悬浮胶囊。
4. 底栏是悬浮胶囊而非贴底 tab。
5. 所有触摸目标 ≥ 48dp。
6. 不含任何新增 Gradle 依赖，纯 Java 8。
