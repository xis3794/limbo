# 静态验证报告 #1（T4 独立验证）

- 分支：`feat/gtk4-virgl-experimental-ui`
- 基线 commit：`ecbedd8`
- 采样时间：2026-11-01T07:53+08:00
- 验证者：T4（写入范围仅 `docs/verification/**`，未改任何源码/布局/manifest/CI）
- 说明：验证期间另外两个工作流仍在写入，本报告基于上述采样时刻的文件快照；末尾附被验证文件的 SHA1，便于判断是否已过期。

## 快照指纹（被验证文件）

```
7fedb65c  ExpPrefs.java
9d5d1bb4  LimboExperimentalActivity.java
072cc634  exp_activity_machines.xml
48edf716  exp_item_machine.xml
31a2cfc0  strings_exp.xml
```

---

## 0. 结论摘要

| 级别 | 数量 | 说明 |
|---|---|---|
| ✅ 通过 | 17 项 | 接口/资源/语法/重定向/旧文件未删改 |
| 🔴 阻断（功能不可用） | 3 项 | P1 新 UI 无法打开编辑页；P2 UI 后端不再落库；P3 GTK 机器永远不会启动 |
| 🟠 严重 | 2 项 | P4 GTK 原生桥接缺失；P5 CI 默认构建会关掉 GTK/VIRGL |
| 🟡 中 | 5 项 | P6~P10 |
| 🔵 低/信息 | 5 项 | P11~P15 |

**总体判定：实验性 UI 的「静态接口落地」基本正确（默认 false、资源齐全、Java 8 合规、重定向守卫正确）；但存在 3 个阻断级问题，其中 P1/P2 会直接影响现有旧 UI 用户，P3 使 GTK 显示后端完全不可用。当前状态不建议进入 APK 验收。**

---

## 1. 实验性新 UI 落地检查

### 1.1 `ExpPrefs.experimentalUi` 默认值 —— ✅ 通过

```bash
$ grep -n "getBoolean" limbo-android-lib/src/main/java/com/max2idea/android/limbo/ui/exp/ExpPrefs.java
16:                .getBoolean(KEY_EXPERIMENTAL_UI, false);
```
默认参数为 `false`，且 `setExperimentalUi` 使用 `apply()`。`gpuHints` 默认 `false` 同样正确。使用 `android.preference.PreferenceManager.getDefaultSharedPreferences`，与 `AppSettingsActivity`（第 61 行）/`LimboSettingsManager` 同一个默认 preferences 文件，开关写入与读取一致。

### 1.2 类名/包名/父类 —— ✅ 通过

```bash
$ grep -n "^package\|extends AppCompatActivity\|class LimboExperimentalActivity" .../ui/exp/LimboExperimentalActivity.java
1: package com.max2idea.android.limbo.ui.exp;
38: public class LimboExperimentalActivity extends AppCompatActivity {
```
符合冻结接口（`com.max2idea.android.limbo.ui.exp`，`AppCompatActivity`，不继承 `LimboActivity`）。`R` 引用为 `import com.limbo.emu.lib.R;`（第 28 行），与 lib 模块 manifest `package="com.limbo.emu.lib"` 一致 —— 正确（仓库内 34 处既有文件均如此 import）。

### 1.3 调用的 API 是否真实存在 —— ✅ 通过（全部存在）

| 被调 API | 证据 |
|---|---|
| `MachineController.getInstance()` | `MachineController.java:59` `public static MachineController getInstance()` |
| `.getStoredMachines()` | `MachineController.java:398` `public ArrayList<String> getStoredMachines()` |
| `.getMachineConfig(String)` | `MachineController.java:387` `public Machine getMachineConfig(String name)` |
| `.isRunning()` | `MachineController.java:282` `public boolean isRunning()` |
| `.getMachineName()` | `MachineController.java:298` `public String getMachineName()` |
| `ViewListener.onAction` | `main/ViewListener.java:26` `void onAction(MachineAction stopVm, Object value);` |
| `MachineAction.DELETE_VM` / `CREATE_VM` | `machine/MachineAction.java:23-24` 均在枚举内 |
| `LimboActivity.EXTRA_MACHINE` / `EXTRA_START` | `LimboActivity.java:213-214` = `"limbo_machine"` / `"limbo_autostart"` |
| `Machine.getName/getArch/getCpuNum/getMemory` | `Machine.java:105/167/203/215` 均为 public |
| `LimboApplication.getViewListener()` | `LimboApplication.java:172`，返回 `Dispatcher.getInstance()` |

`Machine` 判空：`getMachineConfig` 可能返回 null，代码在 `onBindViewHolder`（第 225/235 行）与 `confirmDelete`（第 140 行）均判空，符合规范。
`Dispatcher.requestAction` 中 `DELETE_VM` 走 `deleteVM((Machine) value)`、`CREATE_VM` 走 `createVM(String)`（`Dispatcher.java:346-350`），与调用方传参类型匹配。

### 1.4 布局资源引用完整性 —— ✅ 通过（0 缺失）

```bash
# @string 引用（2 个布局）
exp_add exp_back_legacy exp_empty exp_running exp_start exp_subtitle exp_title
# 逐个在 strings_exp.xml 中均有定义 → 7/7 OK

# R.id（Java ↔ 布局交叉核对）
java 侧: exp_add exp_back_legacy exp_badge exp_empty exp_machine_list exp_meta
         exp_name exp_running_dot exp_running_group exp_start
布局侧: 同上 + exp_card
MISSING(在 Java 中引用但布局未定义): 0
UNUSED (布局定义但 Java 未引用): exp_card —— 但 Java 第 293 行
         `card = (MaterialCardView) itemView;` 是直接强转根视图，属正常用法，非缺失。

# @drawable / @color 引用
@android:drawable/ic_input_add      → 系统内置，存在
@style/Widget.MaterialComponents.Button.TextButton → material 1.1.0 内置，存在
自定义 @drawable/ / @color/ 引用：0 个（全部用 #RRGGBB 字面色值）
```
`strings_exp.xml` 交叉核对：`R.string.*` 在 Java 中引用 12 个，11 个在 `strings_exp.xml`、1 个（`stop_first`）在既有 `strings.xml:497` —— 全部存在。
`R.layout.*` 引用 2 个，均存在。

### 1.5 `strings_exp.xml` 完整性与重复键 —— ✅ 通过

```bash
$ grep -o 'name="[^"]*"' strings_exp.xml | sort | uniq -d
（空 = 无重复键）

$ comm -12 <strings.xml 的 name> <strings_exp.xml 的 name>
（空 = 与既有 strings.xml 无同名冲突，不会触发 resource merge 失败）
```
共 23 个键，含新增的 3 个 `exp_settings_*`（供设置页使用）。`%1$s`/`%2$d` 格式符与 Java 实参类型匹配（`getString(exp_machine_meta, arch:String, cpuNum:int, memory:int)`）。

### 1.6 Java 8 语法 / API>29 检查 —— ✅ 通过

```bash
$ grep -nE '\bvar\s+[a-zA-Z_]'      ... → 0 条
$ grep -nE 'case .*-> |->'          ... → 0 条（无 switch 表达式 / 无 lambda）
$ grep -nE '\brecord\s+[A-Za-z]'    ... → 0 条
$ grep -n '"""'                     ... → 0 条（无 text block）
$ grep -nE 'new [A-Za-z0-9_.]+<>\(\) \{' ... → 0 条（无 Java 9 匿名类钻石）
$ grep -rn 'Build\.|VERSION_CODES'  .../ui/exp/ → 无输出
```
两个文件全部使用匿名内部类（`new View.OnClickListener(){...}`），未用 lambda（更稳妥）。**未发现任何 API>29 的 Android 方法**：所用 API 集合（`setStatusBarColor`/`setNavigationBarColor` API21、`GradientDrawable`、`RecyclerView`、`MaterialCardView`、`AlertDialog`、`Handler(Looper)`、`getLaunchIntentForPackage` 等）均在 compileSdk 29 内。
`.replace("\"", "")`、`isEmpty()`、`substring(0,1)` 等为 Java 6+/Android 1 级 API，无风险。

### 1.7 manifest 注册 —— ✅ 通过（但见 P1）

```bash
$ grep -n "LimboExperimentalActivity" -A3 limbo-android-lib/src/main/AndroidManifest.xml
63:  <activity android:name="com.max2idea.android.limbo.ui.exp.LimboExperimentalActivity"
64:      android:exported="false"  android:label="@string/exp_title"
66:      android:theme="@style/Theme.MaterialComponents.NoActionBar" />
```
未加 LAUNCHER intent-filter ✓（不能成为入口）。主题 `Theme.MaterialComponents.NoActionBar` 来自 material 1.1.0 依赖（本地 `styles.xml` 无该定义，但依赖提供，可用）。

---

## 2. 重定向正确性检查

### 2.1 `LimboActivity` 重定向块位置与守卫 —— ✅ 通过

```bash
$ grep -n "super.onCreate(savedInstanceState)" LimboActivity.java
1216:        super.onCreate(savedInstanceState);
$ grep -n "savedInstanceState == null" LimboActivity.java
1231:        if (savedInstanceState == null
```
重定向块位于 **1218–1239 行**，即 `super.onCreate`（1216）**之后** ✓；返回 `return` 前已 `finish()`。三个守卫齐全（`LimboActivity.java:1231-1234`）：

```java
1231: if (savedInstanceState == null
1232:     && getIntent() != null
1233:     && getIntent().getStringExtra(EXTRA_MACHINE) == null
1234:     && ...ExpPrefs.experimentalUi(this)) {
1235:     startActivity(new android.content.Intent(this, ...LimboExperimentalActivity.class));
1237:     finish();
1238:     return;
```

- `EXTRA_MACHINE == null` 守卫 ✓ —— 既防「编辑页误跳回列表」的循环，也保证新 UI 把机器交回旧编辑页时能真正打开编辑页。
- `savedInstanceState == null` 守卫 ✓（额外加固，避免旋转重建时再次跳转）。
- 位置正确：4 个架构 launcher（x86/arm/ppc/sparc 的 `LimboEmuActivity`）都 `extends LimboActivity` 并 `super.onCreate()`，因此一处钩子覆盖全部架构（已 grep 确认 4 个子类）。

### 2.2 `limbo-android-x86/.../LimboEmuActivity.java` 是否已回退 —— ✅ 通过

```bash
$ git diff limbo-android-x86/ | wc -l
0
$ git status --short limbo-android-x86/
（空）
```
该模块无任何改动：既没有加入 `ExpPrefs` 跳转，也无残留。`Config.clientClass` 仍由它设置（`LimboEmuActivity.java:14-15`）—— 这一点对旧 UI 保持正确（见 P1 的连带影响）。

---

## 3. 旧 UI 未回归（diff 语义审查）

### 3.1 `git diff --stat` 总览（采样时刻）

```
 .github/workflows/build.yml                        | 17 ++++
 limbo-android-lib/src/main/AndroidManifest.xml     | 12 +++
 .../limbo/jni/VMExecutor.java                      | 52 ++++++++++++
 .../limbo/machine/ArchDefinitions.java             | 22 ++++-
 .../limbo/machine/Dispatcher.java                  |  9 +-
 .../limbo/machine/Machine.java                     | 68 +++++++++++++-
 .../limbo/machine/MachineImporter.java             |  7 +-
 .../limbo/machine/MachineOpenHelper.java           | 10 ++-
 .../limbo/main/AppSettingsActivity.java            | 32 ++++++++
 .../limbo/main/Config.java                         | 70 ++++++++++++++++
 .../limbo/main/HomeController.java                 | 17 +++-
 .../limbo/main/LimboActivity.java                  | 48 ++++++++++-
 .../limbo/main/LimboApplication.java               |  3 +
 .../res/values/strings.xml                         |  5 ++
 .../res/xml/settings.xml                           | 10 +++
 scripts/fix_qemu11.py                              | 96 ++++++++++++++++++++++
 16 files changed, 459 insertions(+), 19 deletions(-)
```

### 3.2 全部「删除/改写」行清单（19 行）

```bash
$ git diff -U0 | grep -E "^-[^-]"
-        arrList.add("VNC");                                    # ArchDefinitions
-            arrList.add("SDL");                                # ArchDefinitions
-        if (ui.equals("VNC"))                                  # Dispatcher
-            getMachine().setEnableVNC(1);                      # Dispatcher
-        else if (ui.equals("SDL"))                             # Dispatcher
-            getMachine().setEnableVNC(0);                      # Dispatcher
-        if (this.enableVNC != enableVNC) {                     # Machine
-            this.enableVNC = enableVNC;                        # Machine
-            notifyChanged(MachineProperty.UI, enableVNC);      # Machine  ← 见 P2
-                                String ui = machineAttr[i]...;  # MachineImporter
-                                if (ui.equals("VNC"))          # MachineImporter
-                                    mach.setEnableVNC(1);      # MachineImporter
-        stateValues.put(... "VNC" : "SDL");                    # MachineOpenHelper
-            myMachine.setEnableVNC(cur.getString(34).equals("VNC") ? 1 : 0);  # MachineOpenHelper
-                    activity.startActivity(new Intent(activity, LimboSDLActivity.class));  # HomeController ×2
-        Intent intent = new Intent(LimboActivity.this, LimboSDLActivity.class);     # LimboActivity
-            String text = getString(R.string.display) + ": " + (getMachine().getEnableVNC() == 1 ? "VNC" : "SDL");  # LimboActivity
-        SpinnerAdapter.setDiskAdapterValue(mUI, getMachine().getEnableVNC() == 1 ? "VNC" : "SDL");  # LimboActivity
```

逐项判定：

| 文件 | 改写内容 | 判定 |
|---|---|---|
| `ArchDefinitions` | 字面串 → `Config.UI_VNC/UI_SDL` 常量 | ✅ 等价（常量值即 `"VNC"`/`"SDL"`） |
| `Dispatcher.changeUi` | 两分支 `setEnableVNC` → `setUI` | ⚠️ 见 P2（连带破坏落库） |
| `Machine.setEnableVNC` | 载荷 Integer → String | 🔴 见 P2 |
| `MachineImporter` | 只处理 VNC → `setUI` | ✅ 语义等价并修正（见 P13） |
| `MachineOpenHelper` 写库 | 从 `enableVNC` 推导 → `getUI()` | ✅ 必要（否则 GTK 存不住） |
| `MachineOpenHelper` 读库 | `cur.getString(34).equals("VNC")` → `normalizeUI(...)` | ✅ 顺带修掉 NULL 列 NPE（见 P15） |
| `HomeController` ×2 | `LimboSDLActivity.class` → `localDisplay` 选择 | ✅ SDL 机器仍选 SDL（GTK 不可用时也回 SDL） |
| `LimboActivity.startSDL` | 同上 | ✅ 同上 |
| `LimboActivity` 摘要文案 | 二元 VNC/SDL → `getUI()` | ✅ 改对了（GTK 也能显示） |
| `LimboActivity` spinner 值 | 二元 → `getUI()` | ✅ 改对了 |

**旧 UI 未删改的强证据**：`limbo-android-x86/**` diff 为 0 行；`res/layout/` 既有 31 个布局文件全部未修改（`git status` 仅新增 2 个 `exp_*`）；`strings.xml` 只有 5 行新增、0 行删除。

---

## 4. 疑点清单（含 file:line）

### 🔴 P1 阻断：新 UI 的「编辑 / 启动」跳转到未在 manifest 声明的 Activity

`limbo-android-lib/src/main/java/com/max2idea/android/limbo/ui/exp/LimboExperimentalActivity.java:120`

```java
120:  Intent intent = new Intent(this, LimboActivity.class);
```

`com.max2idea.android.limbo.main.LimboActivity` **没有在任何 AndroidManifest.xml 中声明**：

```bash
$ for f in $(find . -name AndroidManifest.xml -not -path "./docs/*"); do grep -H "LimboActivity" "$f"; done
（无输出）
```
全部 6 个 manifest 中声明的 activity 只有：`LimboFileManager`、`LimboSDLActivity`、`VncActivity`、`QemuVersionsActivity`、`AppSettingsActivity`、`LimboSettingsManager`、`LimboExperimentalActivity`、`LimboGtkActivity`（lib），以及 4 个架构模块的 `LimboEmuActivity`（入口，相对名）。无 `activity-alias`。

**后果**：`startActivity` 抛 `ActivityNotFoundException`，新 UI 的「点卡片编辑」和「启动」按钮**两个核心功能全部失效**（进程未必崩溃，但功能 100% 不可用）。
旧 UI 规避了这个问题：`HomeController.java:592` 用 `new Intent(activity, activity.getClass())`，即用已声明的子类实例类。

**修复方向**（供 Lead 决定，我不改源码）：改用 `Config.clientClass`（由各架构 launcher 在 `LimboEmuActivity.onCreate` 第 15 行设置）或 `getComponentName()` 作为目标；或在该模块 manifest 里声明 `LimboActivity`（后者会改变旧 UI 的 Activity 结构，需谨慎）。
⚠️ 附带风险：若改用 `Config.clientClass`，要确认它已被赋值 —— `LimboEmuActivity.onCreate` 第 15 行在 `super.onCreate()` 之前设置，但**新 UI 是直接从 launcher 跳过来的**，此时 `Config.clientClass` 是否已设置取决于跳转路径（新 UI 由 `LimboActivity` 重定向拉起，而 `LimboActivity` 由架构 launcher 拉起，故通常已设置）。这条需在修复时实测确认，不能只看代码。
注：此写法来自规范文档 `docs/team/t3-exp-ui-spec.md:29`，即 spec 本身有误，不是 Kimi 的实现偏差。

---

### 🔴 P2 阻断：`MachineProperty.UI` 载荷由 Integer 改为 String，但 DB Observer 仍按 int 强转

- 载荷改为 String：`machine/Machine.java:143`、`:179`（`notifyChanged(MachineProperty.UI, value)`，`value` 是 `String`）
- 消费者仍强转 int：`machine/MachineOpenHelper.java:440`

```java
439:  case UI:
440:      updateMachineField((Machine) observable, property, ((int) params[1]) == 1 ? "VNC" : "SDL");
441:      return;
```

`MachineOpenHelper` 是 `Machine` 的注册 Observer（`MachineController.java:339` `this.machine.addObserver((Observer) machineDatabase)`）。因此**任何一次 UI 后端变更都会在 observer 里抛 `ClassCastException`**。

该异常被 `Dispatcher.java:137-141` 的 `catch (Throwable t)` 吞掉（有日志），所以不会崩进程，但后果是：

- **`UI` 列不再写进数据库**（`update()` 是既有机器改 UI 的唯一落库路径；另一条 `insertMachine` 只在新建时写。已 grep 确认 `updateMachineField(` 的调用方全部在 `MachineOpenHelper` 内部，无其他写入点）。
- 因此旧 UI 用户**切换 VNC ↔ SDL 后重启应用，选择会丢失**（回退到库里的旧值）—— 这是对既有行为的回归。
- 同理 GTK 选择也永远存不住。

**影响范围（精确到观察者顺序）**：`MachineController.setMachine()`（第 339 行）先注册 `machineDatabase`，`LimboActivity.loadMachine()`（第 3077 行）后注册自身。`java.util.Observable.notifyObservers` 按「后注册先通知」倒序遍历，因此 `LimboActivity.update` 先执行、`MachineOpenHelper.update` 最后执行 —— 抛出的异常不会阻断 UI 刷新，**只丢落库**。这使问题更隐蔽（界面看着是对的，重启才丢）。
注意：`Observable.notifyObservers` 本身不 catch 异常，异常会穿出 `notifyChanged` → `Dispatcher` 的 try/catch，仅被记日志（`Log.w`），无用户可见提示。

**修复方向**：`MachineOpenHelper.update` 的 `case UI` 改为直接使用 `(String) params[1]`（走下面已存在的 `(String) params[1]` 分支；顺带 `(int) params[1]` 那行的 `value instanceof Integer` 判断分支也该同步清理）。

---

### 🔴 P3 阻断：GTK 机器的 VM 永远不会启动（缺 `START_VM` 触发）

`LimboGtkActivity.java:46-66` 的 `onCreate` 只做 `LimboGtk.init(this)` 和「已运行则更新通知」，**从未发送 `MachineAction.START_VM`**：

```bash
$ grep -rn "MachineAction.START_VM" --include=*.java .
LimboActivity.java:2215        （startVNC() 内）
LimboActivityCommon.java:171
LimboSDLActivity.java:919      ← SDL 在 runSDLMain() 里发
```
`LimboGtkActivity` 不在其中。

启动链路是：`LimboActivity.onStartButton()` →(2155-2159) `enableVNC==1 ? startVNC() : startSDL()`；`startSDL()`（2174-2176）**只启动显示 Activity**，真正的 `START_VM` 由显示 Activity 发（SDL 的 `runSDLMain()`，`LimboSDLActivity.java:919`）。GTK 没有对应实现，所以：

**后果**：选 GTK 后点启动 → 打开 GTK 空窗口，guest 永不 boot。验收标准 2（「选用 GTK 后 QEMU 以 `-display gtk` 启动，日志可见」）不可能达成。
另外 `LimboGtkActivity` 也未复用 SDL 的 `consumeVmShutdownByUser` 逻辑（`LimboSDLActivity.java:913`），没这道闸门还会在关机后被重复拉起。

---

### 🟠 P4 严重：GTK 的 Java→native 桥接符号在仓库内不存在

`LimboGtk.java:63,65,67` 声明 3 个 native 方法：

```java
63:  private static native boolean nativeHasInit();
65:  private static native boolean nativeInit(Object classLoader, Object activity);
67:  private static native void nativeSetEnv(String key, String value);
```

```bash
$ grep -rn "Java_com_max2idea_android_limbo_main_LimboGtk|gdk_android_initialize" limbo-android-lib/src/main/jni/
（无输出）
$ grep -n "Java_" limbo-android-lib/src/main/jni/limbo/vm-executor-jni.c
全部是 Java_com_max2idea_android_limbo_jni_VMExecutor_*（10 个，无 LimboGtk）
```
`jni/limbo/Android.mk` 的 `LOCAL_SRC_FILES` 只有 `vm-executor-jni.c`。

**后果**：`LimboGtk.init()` 里 `nativeInit(...)` 抛 `UnsatisfiedLinkError`，被 `catch (Throwable)`（`LimboGtk.java:110`）吞掉并返回 false；`LimboGtkActivity.java:52` 忽略返回值。于是 GDK android 后端从未绑定 → `-display gtk` 无宿主 surface → 极可能启动即崩。
（`org/gtk/android/*` 的 native 由 gtk4android 的 `libgtk-4.so` 提供，属正常；缺的是 `liblimbo.so` 里的 3 个桥接符号。）
`LimboGtk.isAvailable()`（`LimboGtk.java:74`）**无任何调用者**（grep 确认），所以连「不可用就回退 SDL」的兜底也没接上。

---

### 🟠 P5 严重：CI 默认输入会强制关掉 GTK/VIRGL

`.github/workflows/build.yml:9` 默认 `qemu_version = '11.1.2'`；但同一文件 75-88 行：

```yaml
75:  GTK_VIRGL_QEMU_VER="11.0.0"
82:  if [ "$USE_GTK" = "true" ] || [ "$USE_VIRGL" = "true" ]; then
83:    if [ "$QEMU_VER" != "$GTK_VIRGL_QEMU_VER" ]; then
84:      echo "::warning::GTK4/GPU are only built against QEMU 11.0.0 (requested $QEMU_VER); disabling both"
86:      USE_GTK=false ; USE_VIRGL=false
94:  if [ "$USE_GTK" != "true" ]; then USE_VIRGL=false; fi
```
即：**按默认参数（11.1.2 + use_gtk=true）跑 CI，反而会得到 USE_GTK=false/USE_VIRGL=false 的构建** —— 不产 `libgtk-4.so`/`libvirglrenderer.so`，`DisplayBackendProbe`（`DisplayBackendProbe.java:68-78`）据此把 `engineHasGtk/engineHasVirgl` 置 false，`Config.isGtkUsable()` 恒 false → 设置里 GTK/GPU 开关不出现、`GTK` 不在显示方式列表里。
**后果**：与验收标准 2/3 直接冲突；想让 GTK 生效必须手动把 qemu_version 改成 11.0.0。需 Lead 明确「验收用哪个版本」，或让默认值对齐。

附带缺口（同属 T2 未完成）：`limbo-android-lib/src/main/jni/Makefile` **完全没有** `USE_GTK`/`config-gtk`/`gtk`/`config-virgl`/`virgl` 目标（grep 0 命中，mtime 仍是 10-31 11:35，未被改动），且 CI 的 `USE_GTK/USE_VIRGL` 只写到 `GITHUB_ENV`、**没有** `export` 给 make/ndk-build —— 所以即使版本对齐，QEMU configure 也不会带 `-Dgtk=enabled -Dopengl=enabled -Dvirglrenderer=enabled`。`scripts/build_libepoxy.sh` 也未被 build.yml 引用（grep 计数 0）。

---

### 🟡 P6 中：GTK 机器继承不到 SDL 的 stop/pause 握手标志

`HomeController.java:375` 对 GTK 机器仍只设 SDL 的静态标志：

```java
375:  LimboSDLActivity.pendingStopImmediate = true;
376:  String ui = Config.normalizeUI(...getUI());
378:  Class<?> target = ... GTK ? LimboGtkActivity.class : LimboSDLActivity.class;
```
而该标志只在 `LimboSDLActivity.onResume()`（第 661-662 行）被消费。同理 `LimboActivity.java:1460`（`pendingPause`）与 `:2280`（`pendingStop`）也只被 SDL 消费。
**后果**：GTK 机器从首页「停止/暂停」按钮走的兜底路径失效（只能依赖 `requestStop()` 直通，`HomeController.java:357`）。
（严格说 P3 修好前这条路径不会被执行到，但修 P3 时需一并处理。）

### 🟡 P7 中：GTK 不可用时，旧编辑页可能把 GTK 机器静默改写成 VNC

`LimboActivity.java:3211` 现在正确地用 `getUI()`：
```java
3211:  SpinnerAdapter.setDiskAdapterValue(mUI, getMachine().getUI());
```
但 `populateUI()`（3771-3777）的列表来自 `ArchDefinitions.getUIValues()`，GTK 不可用时该列表**不含 GTK**，于是 `getPositionFromSpinner` 返回 -1 → `setSelection(Math.max(-1,0))` = **0**，而 `getUIValues()` 的 0 号永远是 `VNC`（`ArchDefinitions.java:152-155`）。

```bash
$ grep -n "arrList.add" ArchDefinitions.java   # 顺序：VNC, [SDL], [GTK]
$ sed -n '89,100p' ui/SpinnerAdapter.java
94:  spinner.setSelection(Math.max(pos, 0));
```
而 `mUI` 的 `onItemSelected`（`LimboActivity.java:574-579`）会 `notifyFieldChange(MachineProperty.UI, "VNC")`；`mUI` **不在** `programmaticSpinners` 抑制名单内（`suppressAllDriveSpinners()` 470-482 行不含 mUI，grep `suppressSpinnerCallbacks(mUI)` 无命中）。

**后果**：在「引擎没编 GTK / 设备 <API31」的构建上打开一台 GTK 机器 → spinner 落到 VNC → 若回调生效则机器被改存为 VNC（配置静默变更）。风险条件性存在，但属数据损坏类，建议在 `loadMachineUI` 里对 mUI 加抑制或补一个临时列表项。

### 🟡 P8 中：GTK Activity 用 `launchMode="singleTask"`，破坏 `startActivityForResult` 契约

`AndroidManifest.xml:68-71`：
```xml
68:  <activity android:name="...main.LimboGtkActivity"
69:      android:configChanges="..."
70:      android:exported="false" ... android:launchMode="singleTask" .../>
```
而 `LimboActivity.startSDL()`（2174-2176）用 `startActivityForResult(intent, Config.SDL_REQUEST_CODE)`，`onActivityResult`（3402、3442）依赖 `SDL_QUIT_RESULT_CODE` 收尾。singleTask 下系统会丢弃 result（"Activity is in singleTask mode, results ignored"）。
**后果**：GTK 画面退出后的 `onActivityResult` 收尾逻辑（含 `SDL_QUIT_RESULT_CODE` 分支）不会执行。另注意：`LimboGtkActivity` 也完全没有 `setResult`/`finish` 的退出路径，与 SDL 不一致。

### 🟡 P9 中：旧 UI 仍有三处显示后端文案是二元 VNC/SDL（GTK 显示不出来）

```bash
$ grep -rn 'getEnableVNC() == 1 ? "VNC" : "SDL"' --include=*.java .
HomeController.java:562
  562: String display = machine != null && machine.getEnableVNC() == 1 ? "VNC" : "SDL";
LimboActivity.java:2703-2704  (状态胶囊)
  2703: boolean sdl = mUI == null || mUI.getSelectedItemPosition() != 0;
  2704: mStatusPill.setText(sdl ? "SDL" : "VNC");
LimboActivity.java:2715
  2715: if (running && (mUI == null || mUI.getSelectedItemPosition() != 0)) { ... status_sdl_background
```
（另有 `LimboActivity.java:2703/2715` 用「mUI 位置 !=0 即 SDL」的假设，GTK 位置是 2，仍被判成 SDL。）
**后果**：仅文案/状态显示错误（不阻断功能）：首页概览与状态胶囊会把 GTK 机器显示成 SDL。相对 2870 行与 3211 行已改用 `getUI()`，属遗漏不一致。

### 🟡 P10 中：新 UI「打开机器」缺少「已有 VM 运行中」守卫

`LimboExperimentalActivity.java:119-126` 直接 `startActivity`，未像参考实现那样先判运行态：

```bash
$ sed -n '585,595p' HomeController.java
587:  if (controller.isRunning() && !machineName.equals(controller.getMachineName())) {
589:      Toast.makeText(activity, R.string.stop_first, ...); return; }
```
**后果**：VM 正在运行时点另一台机器的卡片/启动，会进入编辑页并可能触发第二次启动请求（`Dispatcher`/service 层行为未验证）。删除路径（`confirmDelete`，129-133 行）倒是正确带了这个守卫。

---

### 🔵 P11 低：`res/xml/settings.xml` 新增的 `experimentalUi` 项是死代码

`settings.xml:130-135` 新增 `CheckBoxPreference key="experimentalUi"`，但它只被 `LimboSettingsManager.addPrefs()`（第 347 行）加载，而**没有任何代码启动 `LimboSettingsManager`**（`grep -rn "LimboSettingsManager.class"` 无命中）。真正可达且同样写 `experimentalUi` 的是 `AppSettingsActivity.java:216-218`（首页 `row_global_settings` → `HomeController.java:253`）。
两者都用默认 Preferences 且键名/默认值一致，因此**不会冲突**，只是一份多余声明。风险≈0，但会增加后续维护困惑。

### 🔵 P12 低：`HomeController` 新增代码未判 `getMachine()` 为空

`HomeController.java:294-296` 与 `:376-378` 直接 `MachineController.getInstance().getMachine().getUI()`。两处都在 `isRunning()` 为真的分支内（`286`、`371`），运行时 machine 非空，实际风险低；但同类代码在仓库其他位置普遍判空，建议补 null 守卫。

### 🔵 P13 信息：`MachineImporter` 的 UI 解析语义扩展（非回归）

`MachineImporter.java:78-83`：原 `if (ui.equals("VNC")) setEnableVNC(1)` 改为 `setUI(raw)`。
旧行为对 `"SDL"` 是「什么都不做」（保持默认 0=SDL），新行为 `normalizeUI("SDL")` → SDL，等价；对未知/空值旧行为也是不动（=SDL），新行为 normalizeUI → SDL，等价。**因此不是回归**，还修掉了 GTK 导入后变 SDL 的问题。
唯一细微差异：旧代码对 `enableVNC` 已是 1 且导入 UI="SDL" 的实例不会清零，新代码会清零。此场景（新建 `Machine(machineAttr[0], false)` 后导入）初值恒 0，无影响。

### 🔵 P14 低：新 UI Activity 无 `configChanges`，旋转会重建

`AndroidManifest.xml:63-66` 该条目没有 `android:configChanges`。对比同 manifest 其他 Activity 与 4 个架构 launcher 都带完整 `configChanges`。
**后果**：旋转/深色模式切换时 Activity 重建（`onCreate` 重跑 `setContentView`）。因 `onResume` 会 `refreshMachines()`，功能上能恢复，仅丢失 RecyclerView 滚动位置；但会重建 Adapter 且有短暂闪烁。另 `handler.removeCallbacksAndMessages(null)`（`onDestroy` 第 96 行）可保证不泄漏。

### 🔵 P15 信息：`MachineOpenHelper` 读库改用 `normalizeUI` 顺带修掉潜在 NPE

`MachineOpenHelper.java:340` 原 `cur.getString(34).equals("VNC")` 在 UI 列为 NULL 时抛 NPE（旧库经 `ALTER TABLE ... ADD COLUMN UI TEXT`（第 145 行）迁移的老行就是 NULL）；新 `setUI(Config.normalizeUI(cur.getString(34)))` 把 NULL/未知归为 SDL。
**这是修复而非回归**，但需注意语义：老行若曾 `enableVNC=1`，现在一律读成 SDL。因该列在新库写入时总有值，仅影响「迁移后从未再改过 UI」的行 —— 属极边缘场景，记录备查。

---

## 5. GTK / GPU 静态检查逐项（T2 进行中，部分未完成）

| 检查项 | 结果 | 证据 |
|---|---|---|
| `Config` 有 `UI_GTK` | ✅ | `Config.java:134` `public static final String UI_GTK = "GTK";` |
| `Config` 有 `enableGtk` | ✅ | `Config.java:174` 默认 `false` |
| `Config` 有 `enableVirgl` | ✅ | `Config.java:177` 默认 `false` |
| `Config` 有 `isGtkUsable` | ✅ | `Config.java:184-187`：`enableGtk && engineHasGtk && SDK_INT >= 31` |
| `getUIValues()` 条件加 GTK | ✅ | `ArchDefinitions.java:159-162` `if (Config.isGtkUsable()) arrList.add(Config.UI_GTK);` —— 满足「SDK>=31 且 enableGtk」并额外要求引擎真带 GTK（更严，合理） |
| `getVGAValues()` 条件加 `virtio-gpu-gl-pci` | ✅ | `ArchDefinitions.java:112-118`；限 x86/x86_64/arm/arm64 且 `Config.isVirglUsable()`（= `enableVirgl && engineHasVirgl`） |
| `VMExecutor` GTK → `-display gtk` | ✅ | `VMExecutor.java:252-256`；`isVirglUsable` 且 guest 用 GL 设备时 → `gtk,gl=on` |
| SDL + virgl → `sdl,gl=on` | ✅ | `VMExecutor.java:257-267`；`glGuest && isVirglUsable` → `sdl,gl=on` |
| VNC 原行为未变 | ✅ | `addUIOptions` 的 VNC 分支（196-223）**diff 为 0 行**；`-vnc` 参数、`-monitor vc` 保持原样 |
| SDL 原行为未变 | ✅ 优 | 非 GL 的普通 SDL 机器**不输出 `-display`**（`VMExecutor.java:257` 条件不成立），命令行逐字节不变；virgl 存在也不无条件加 `gl=on`（用 `usesGlVideoDevice()`，第 877-881 行限定 `virtio-gpu-gl*`）。这一处明显比我最初担心的实现更保守，值得肯定 |
| GTK 模式不加 `-monitor vc` | ✅ | GTK 在非 VNC 分支，`-monitor none`（228 行），符合规范 |
| `virtio-gpu-gl-pci` 在 `addGraphicsOptions` 被识别 | ✅ | `VMExecutor.java:872` 有 `getVga().equals("virtio-gpu-gl-pci")` 分支 |
| `LimboGtkActivity` 存在 | ✅ | `main/LimboGtkActivity.java`，85 行，`extends ToplevelActivity` |
| `LimboGtkActivity` 在 lib manifest 注册 | ✅ | `AndroidManifest.xml:67-71` |
| `DisplayBackendProbe` 接入 | ✅ | `LimboApplication.java:232` 在 `onCreate` / `setupFolders()` 之后调用；`Config` 开关由 probe 依据 `libgtk-4.so` / `libvirglrenderer.so`+`libepoxy.so` 是否存在决定 |
| GTK Java 侧对 API>29 的处理 | ✅ | `org/gtk/android/ToplevelActivity.java:531`、`:806` 均 `SDK_INT >= 30` 守卫，API30 类（`WindowInsets.Type`/`getInsetsController`/`WindowInsetsController`）**全部用反射**（`Api30Insets`，565-582 行；`Class.forName`，810-816 行），compileSdk 29 可编译 |
| **GTK 原生桥接符号** | 🔴 缺 | 见 P4 |
| **Makefile gtk/virgl 目标** | 🔴 缺 | `jni/Makefile` 无 `USE_GTK`/`config-gtk`/`config-virgl`（mtime 未变）；CI 未 export 给 make |
| **CI 默认版本与 GTK 分支一致性** | 🔴 冲突 | 见 P5 |
| `scripts/build_libepoxy.sh` 接入 CI | 🔴 未接 | `grep -c "build_libepoxy" build.yml` = 0（脚本已存在，未调用） |
| QEMU 补丁文件 | ✅ 齐 | `jni/patches/` 有 `qemu-11.1.2-android-egl.patch`、`qemu-11.1.2-gtk-gl.patch`、`qemu-11.0.0-gtk4-android.patch`、`qemu-11.0.0-android-egl.patch`、`qemu-11.0.0-android-gl-scanout.patch`、`virglrenderer-android-egl.patch`；`scripts/fix_qemu11.py:553-570` 按版本选择补丁并在 `USE_GTK/USE_VIRGL` 开启时把不适用报 `FAIL`（不静默跳过）—— 设计正确 |

---

## 6. 未验证项与风险（能力边界）

本机是 Android proot Ubuntu，**无 Java/Android SDK/NDK/Gradle**，因此以下**无法**由我静态验证，需依赖 CI 或真机：

1. **编译验证**：未跑 `javac`/`aapt`/`gradle`。`R.*` 交叉核对是我用 grep 反推的，无法排除「资源 ID 存在但类型不符」（如 `setText(int)` 传错资源类型）类错误。`MachineOpenHelper.java:440` 的 `(int) params[1]` 是**运行时** ClassCastException，编译期不报。
2. **资源链接（aapt2）**：未验证 `strings_exp.xml` 与既有资源 merge 是否报错；`Theme.MaterialComponents.NoActionBar` 是否真的由 material 1.1.0 提供（依赖版本已从 build.gradle 确认，但未实际链接）。
3. **manifest merge**：`limbo-android-lib` 的 `package="com.limbo.emu.lib"` 与 x86 模块 `package="com.limbo.emu.main"` 的合并结果、`android:exported` 在 targetSdk 28 下的告警，均未验证。
4. **`MaterialCardView` 的 `app:strokeColor`/`app:strokeWidth` 是否在 material 1.1.0 生效**：1.1.0 已引入这两个属性（静态判断），但未实机确认渲染。
5. **GTK 运行时行为**：`-display gtk` 与 gtk4android 的 `libgtk-4.so` 是否真能在 API 31 设备上起窗、`gl=on` 是否可用、`vendor libEGL` 路径 —— 全部未验证（且 P3/P4 未解决前不可能验证）。
6. **virgl 运行时**：`virtio-gpu-gl-pci` 在真机 arm64-v8a + Android 12 无 KVM 环境下的实际性能/可用性未验证。
7. **DB 迁移**：老版本 APK 升级后 `UI` 列迁移（`ALTER TABLE ADD COLUMN`）的实际行为、以及 P2 修复后既有机器配置是否被写坏 —— 未验证。
8. **新旧 UI 切换闭环**：`returnToLegacyUi()`（`LimboExperimentalActivity.java:186-194`）用 `getLaunchIntentForPackage` + `FLAG_ACTIVITY_CLEAR_TOP` 重入 launcher，实际是否形成回环/是否残留新 UI 任务栈 —— 未实机验证。
9. **`LimboExperimentalActivity` 与旧编辑页共存时的进程内状态**：`MachineController` 单例、`Dispatcher` 单线程队列跨 Activity 行为未验证。
10. **本报告采样后仍有文件被改**：两个工作流仍在写入（`LimboActivity.java` mtime 06:56、`build.yml` 07:11、`strings_exp.xml` 06:36）。若后续改动触及 P1~P10 涉及行号，请以 SHA1 判断是否需要我复验。

---

## 7. 建议 Lead 优先处理顺序

1. **P1**（新 UI 跳转目标改成已声明 Activity）—— 否则新 UI 完全没法用，且这是 spec 里的错，需要 Lead 拍板改 spec + 通知 T3。
2. **P2**（`MachineOpenHelper.update` 的 `case UI` 去掉 int 强转）—— 否则旧 UI 切换显示方式不落库，属**既有功能回归**，优先级最高（影响所有用户）。
3. **P3 + P4**（GTK 启动触发 + native 桥接）—— 否则 GTK 后端不可用，验收标准 2 不达。
4. **P5**（CI 版本策略）—— 需 Lead 明确「验收构建用 11.0.0 还是把 GTK 端口到 11.1.2」；同时补 `Makefile` 的 gtk/virgl 目标与 `export`。
5. P6~P10 可随修复顺带处理；P11~P15 记录备查即可。
