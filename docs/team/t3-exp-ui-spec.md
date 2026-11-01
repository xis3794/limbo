# T3 实验性新 UI —— Kimi K3.1 写作规范（Lead 冻结）

> 你正在为 Android 虚拟机前端 Limbo 写一套**全新的机器列表界面**。
> 目标设备：HUAWEI CTR-AL00，Android 12（API 31），arm64-v8a，屏幕 1080x2340 左右。

## 绝对红线
1. **旧界面一个字节都不许改**。你只能**新增**文件，只能在下面列出的两个既有文件里做**最小插入**。
2. 不要 import 旧界面的布局/风格（`R.layout.home_*`、`AppBackgroundView`、`AuroraBackgroundView`、`GlassBlurView` 一律不用）。自己做视觉。
3. 不要引入任何新 Gradle 依赖。**纯 Java + 原生 View**，禁止 Kotlin、Compose、Material3 组件库之外的东西（可以用 `com.google.android.material.*` 已有的 material 1.1.0）。
4. 目标部署到 `minSdk 21 / targetSdk 28 / compileSdk 29` 的模块，**不要用 API > 29 的 Android 框架方法**（`Build.VERSION_CODES.R` 之后的方法不要用），否则编译会红。Java 语言级别是 **Java 8**：不要用 `var`、不要用 switch 表达式。
5. 新 UI 只有在 SharedPreferences 里 `experimentalUi=true` 才生效，**默认 false**。

## 你要创建的文件

### 1) `limbo-android-lib/src/main/java/com/max2idea/android/limbo/ui/exp/ExpPrefs.java`
```java
package com.max2idea.android.limbo.ui.exp;
```
- `public static boolean experimentalUi(Context)`：读 `PreferenceManager.getDefaultSharedPreferences(context)` 的 `"experimentalUi"`，默认 **false**。
- `public static void setExperimentalUi(Context, boolean)`：写入并 `apply()`。
- 再给一个 `public static boolean gpuHints(Context)` 读 `"experimentalGpu"` 默认 false（可留作开关，UI 里可用于显示 GL 徽标）。

### 2) `.../ui/exp/LimboExperimentalActivity.java`
一个 `androidx.appcompat.app.AppCompatActivity` 子类，**不继承** `LimboActivity`（避免把旧 UI 的整套逻辑拖进来）。

必须实现的功能闭环：
- **机器列表**：用 `com.max2idea.android.limbo.machine.MachineController.getInstance().getStoredMachines()` 拿 `ArrayList<String>`。这是唯一数据源。
- 每张卡片显示：机器名（`MachineController.getInstance().getMachineConfig(name)` 拿 `Machine`，可读 `getName()`, `getArch()`, `getCpuNum()`, `getMemory()`）；`Machine` 可能返回 null，要判空。
- 点卡片 → **编辑**：`Intent i = new Intent(this, LimboActivity.class); i.putExtra("limbo_machine", name); startActivity(i);`
  （`LimboActivity.EXTRA_MACHINE` 的值就是字符串 `"limbo_machine"`；直接写字面量也行，但优先 `LimboActivity.EXTRA_MACHINE` 常量以免改坏。）
- 卡片上的「启动」按钮 → 同一个 Intent，额外 `putExtra("limbo_autostart", true)`（常量 `LimboActivity.EXTRA_START`）。注意：`LimboActivity` 启动后自己会进入 SDL/VNC 画面，你**不需要**处理显示。
- 长按卡片 → `AlertDialog` 确认删除 → `LimboApplication.getViewListener().onAction(MachineAction.DELETE_VM, machineController.getMachineConfig(name))`，然后 700ms 后刷新列表（照 `HomeController.confirmDelete` 的既有做法）。注意 `MachineController.isRunning()` 且同名时先 Toast「先停止虚拟机」（`R.string.stop_first`）。
- 右下角 FAB「新建」→ `AlertDialog` 里放 `EditText` 输入名字 → `LimboApplication.getViewListener().onAction(MachineAction.CREATE_VM, name)`，800ms 后刷新（照 `HomeController.promptNewMachine`）。
- 为空列表时显示一句空状态文案（自己写，不要用 `R.string` 里可能不存在键，直接硬编码中文字符串到 `strings_exp.xml` 并引用）。
- 顶部有个「返回旧版界面」按钮：`ExpPrefs.setExperimentalUi(this, false)` 然后 `Intent i = new Intent(this, Class.forName(下文 launcher 类))`… **更简单**：直接 `finish()` 并 `startActivity(new Intent(this, getPackageManager().getLaunchIntentForPackage(getPackageName()).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)))`，由 launcher 重新走旧路径。

**重要**：`LimboApplication.getViewListener()` 返回 `ViewListener`，`onAction(MachineAction, Object)` 是旧代码的既有通路，可放心调用（`MachineAction`、`LimboApplication`、`MachineController`、`Machine` 都是 public API，上面列出的方法已确认存在）。

视觉风格（自己做，**不要**参考任何既有项目）：
- 深色底（`#0B0F14`），卡片圆角 20dp、微透明 `#141A22`、1dp 描边 `#22303C`。
- 标题「虚拟机」用 28sp 粗体，副标题小字 13sp 灰 `#8A97A5`。
- 卡片左侧一个圆形色块（按机器名 hash 取色，自己算，不要引库），里面放机器名首字。
- 运行中的机器：卡片描边变青色 `#33D6C0`，右上角一个小圆点 + 「运行中」。
- 全部用代码构建 View 或 XML 都可以，但**推荐 XML**（见下）。

### 3) `limbo-android-lib/src/main/res/layout/exp_activity_machines.xml`
根布局 `LinearLayout` 垂直：标题栏、`RecyclerView`（`androidx.recyclerview.widget.RecyclerView`，id `exp_machine_list`）、空状态 `TextView`（id `exp_empty`）、`FloatingActionButton`（`com.google.android.material.floatingactionbutton.FloatingActionButton`，id `exp_add`）。
（`androidx.recyclerview` 与 `material` 已是既有依赖。）

### 4) `limbo-android-lib/src/main/res/layout/exp_item_machine.xml`
一张卡片：`MaterialCardView`（`com.google.android.material.card.MaterialCardView`）内含横向 `LinearLayout`：圆形色块 `TextView`（id `exp_badge`）、纵向 `LinearLayout`（名称 `exp_name`、元信息 `exp_meta`）、启动按钮 `MaterialButton`（id `exp_start`）。
**注意**：`MaterialCardView` 在 material 1.1.0 存在；`MaterialButton` 也在。不要用 `ShapeableImageView`（1.1.0 没有稳定支持）。

### 5) `limbo-android-lib/src/main/res/values/strings_exp.xml`
所有新界面用到的中文文案（`exp_*` 前缀），**不要**引用不存在的既有 `R.string.*`。

## 你唯一允许修改的两个既有文件（最小插入）

### A. `limbo-android-x86/src/main/java/com/limbo/emu/main/LimboEmuActivity.java`
在 `onCreate` **最开头**（`LimboApplication.arch = ...` 之前）插入：
```java
if (com.max2idea.android.limbo.ui.exp.ExpPrefs.experimentalUi(this)) {
    startActivity(new android.content.Intent(this,
            com.max2idea.android.limbo.ui.exp.LimboExperimentalActivity.class));
    finish();
    return;
}
```
其余**一个字符不动**。

### B. `limbo-android-lib/src/main/AndroidManifest.xml`
只加一条 `<activity android:name="com.max2idea.android.limbo.ui.exp.LimboExperimentalActivity" android:label="@string/exp_title" android:theme="@style/Theme.AppCompat.NoActionBar" />`
放在已有 `<application>` 里。**不要**加 LAUNCHER intent-filter（它不能成为入口）。
注意：如果该 manifest 里没有 `Theme.AppCompat.NoActionBar` 可用（没有 appcompat 主题），改用 `android:theme="@android:style/Theme.Material.NoActionBar"`。

## 编译约束速查
- 包名 `com.max2idea.android.limbo.ui.exp`。
- 只能 import：`android.*`、`androidx.appcompat.*`、`androidx.recyclerview.*`、`com.google.android.material.*`、以及本仓库 `com.max2idea.android.limbo.*` 里**上面已列出的** public 类。
- 用 `R` 时是 `com.max2idea.android.limbo.R`（lib 模块的 R），不要写 `com.limbo.emu.R`。
- Lambda 可以用（Java 8 + desugar 已开）；但为稳妥优先写匿名内部类。

## 交付
把文件全部写进 `/root/workspace/limbo` 对应路径，然后回复：文件清单 + 你对「默认 false / 旧界面未改 / 无新依赖」的自证说明。