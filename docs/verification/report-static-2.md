# 静态验证报告 #2（T4 独立验证，第二轮）

- 分支：`feat/gtk4-virgl-experimental-ui`
- 验证时 HEAD：`b2dd2c8`（`fix(native): drop dma-buf naming on Android`；本轮请求标称 `cc9b52e`，验证期间上游又前进了 2 个提交，见 §6 说明）
- 采样时间：2026-11-01T10:16+08:00
- 验证者：T4（写入范围仅 `docs/verification/**`，未改任何源码）
- 本轮首次使用**真实工具**：`javalang 0.13.0` 语法解析 + `javac 17.0.20.1` + `git apply --check`/`patch --dry-run` + GitHub Actions API 日志

## 快照指纹

```
7fedb65c  ExpPrefs.java
b48ae10e  LimboExperimentalActivity.java   ← 已修 P1（上轮是 9d5d1bb4）
5c6ef126  LimboGtk.java
54cd2fd6  LimboGtkActivity.java             ← 已修 P3（上轮无此逻辑）
```

---

## 0. 结论摘要

| 项目 | 结论 |
|---|---|
| Java 语法（javalang，83 文件） | ✅ **83/83 全部通过，0 失败** |
| P1 新 UI 跳转 | ✅ **已修复**（1 个残留风险） |
| P2 Observer 强转 | ✅ **已修复** |
| P3 GTK 启动 | ✅ **主要逻辑已修复**，但 P6 链路存在结构性缺口 |
| P4 GTK JNI 桥接 | ✅ **已修复**（`limbo_gtk.c` 三个符号齐全） |
| P5 CI 版本门禁 | ✅ **已修复**（默认 11.0.0，resolved gtk=true virgl=true） |
| P6 停止路径 | 🟠 **设计正确但与 START_VM 存在真实抢跑窗口**，且 `singleTask` 使停止链路可能走不到 |
| P7 mUI 守卫 | ✅ **防住改写且不误伤 VNC↔SDL**，但有一处未覆盖的变体 |
| 原生补丁 | ✅ **5 个补丁全部干净应用**；barrier 补丁**只改 barrier guard，未误伤另一处** |
| CI 构建 | 🔴 **4 轮全失败**；本轮失败点是 **GTK4 的 glib >= 2.84 依赖**（不是 virgl） |
| APK / dex 内容 | ⚠️ **无法验证**（无 artifact、本地 APK 全是功能分支之前的） |

**总体判定：Java/资源侧 P1~P5 已实质修复，静态质量明显提升；原生侧 virgl 已跨过 barrier 与 dma-buf 两道坎并成功产出 `libvirglrenderer.so`，现在卡在 GTK4 的 glib 版本依赖。验收标准 1（新 UI）具备进入构建验证的条件；验收标准 2/3（GTK/GPU 实机）仍不可达。**

---

## 1. 全仓 Java 语法解析（javalang）—— ✅ 83/83 通过

```bash
$ { git ls-files '*.java'; git ls-files --others --exclude-standard '*.java'; } | sort -u > /tmp/all_java.txt
$ wc -l /tmp/all_java.txt
83
$ sed 's#/src/.*##' /tmp/all_java.txt | sort | uniq -c
      1 limbo-android-arm
     79 limbo-android-lib
      1 limbo-android-ppc
      1 limbo-android-sparc
      1 limbo-android-x86
```

解析脚本（`/tmp/parse_check.py`）：

```python
import javalang
for p in files:
    javalang.parse.parse(open(p, encoding='utf-8', errors='replace').read())
```

输出：

```
TOTAL: 83
OK   : 83
FAIL : 0
```

关键新文件逐个复解析：

```bash
$ for f in .../ui/exp/*.java .../main/LimboGtk*.java .../main/DisplayBackendProbe.java .../org/gtk/android/*.java
OK   ExpPrefs.java
OK   LimboExperimentalActivity.java
OK   LimboGtk.java
OK   LimboGtkActivity.java
OK   DisplayBackendProbe.java
OK   org/gtk/android/{ClipboardProvider,GlibContext,ImContext,ToplevelActivity}.java
```

**说明（诚实边界）**：`javalang 0.13.0` 只做**语法**解析，不解析符号/不检查类型，因此它在 Java 8 下就能通过 Java 17 语法的文件。所以我**另外**做了上一轮那套 Java 8 违规扫描（`var`/`->`/`record`/`"""`/匿名类钻石），仍全部为 0。但这**不等于编译通过**：语义、类型、`R` 资源 ID、依赖 jar 均未验证（本机无 `android.jar`/`aapt2`，见 §6）。
另外 `javac` 可用但无 classpath（`android.jar`、androidx、material 全缺），因此**没有**跑真实 `javac` —— 这个限制我如实列出，不假装做了编译。

---

## 2. P1 / P2 / P3 复验

### 2.1 P1 新 UI 跳转目标 —— ✅ 已修复

`LimboExperimentalActivity.java:120-149`：

```java
120:  private void openMachine(String name, boolean autostart) {
128:      Class<?> target = Config.clientClass;
129:      if (target == null) {
130:          Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
131:          if (launch != null && launch.getComponent() != null) {
132:              launch.putExtra(LimboActivity.EXTRA_MACHINE, name);
136:              launch.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
137:              startActivity(launch);
138:              return;
140:          Toast.makeText(this, R.string.exp_config_unavailable, ...).show();
141:          return;
142:      }
143:      Intent intent = new Intent(this, target);
```

复验通过：
- 不再使用未声明的 `LimboActivity.class`（已 grep 确认文件内 `LimboActivity.class` 仅出现在注释第 124 行）。
- 主路径改用 `Config.clientClass`，由架构 launcher 在 `LimboEmuActivity.onCreate` 设置（4 个模块均 `Config.clientClass = this.getClass()`）。
- **有 `getLaunchIntentForPackage` 兜底**，且兜底里把 extras 也带上了、并 `CLEAR_TOP`；兜底失败才 Toast 报错，不会静默失败。
- `Config` 已正确 import（第 32 行）。

⚠️ **残留风险 P1-a（低）**：兜底分支用了 `FLAG_ACTIVITY_NEW_TASK`。launcher 是 `singleTask`，加 `NEW_TASK` 后 extras 仍会送达（走 `onNewIntent`），但 `LimboActivity` 收到 extras 依赖 `getIntent()`；`singleTask` 重入时**若已有实例**会走 `onNewIntent` 而 `LimboActivity` 无 `onNewIntent` 覆写（grep 计数 0），extras 可能被丢弃。仅当「新 UI 打开编辑页时 launcher 已存活」才触发，需真机验证。

### 2.2 P2 `MachineOpenHelper.update` —— ✅ 已修复

`MachineOpenHelper.java:433-459`：

```java
439:  case UI:
446:      updateMachineField((Machine) observable, property,
447:              value instanceof Integer
448:                      ? (((Integer) value) == 1 ? Config.UI_VNC : Config.UI_SDL)
449:                      : (String) value);
450:      return;
```

复验通过：
- **不再有 `(int) params[1]` 强转**（这正是上轮 P2 的根因）。
- 同时兼容 Integer 旧编码（任何仍在发 Integer 的调用方不会炸），与 `Machine.setUI` 现在发 `String` 的现状匹配。
- `Config` 已在 `MachineOpenHelper` 内 import（第 29 行），`Config.UI_VNC/UI_SDL` 可解析。
- 落库链路恢复：`update()`（第 434 行）是既有机器改 UI 的唯一写库路径，现在能正常写 `"VNC"/"SDL"/"GTK"`。

⚠️ **I-4（信息，非缺陷）**：第 455 行仍保留 `((int) params[1])+""` 的旧写法，但**不会**触发上轮的 ClassCastException —— 它被第 454 行 `if(value instanceof Integer)` 保护，且 `UI` 已在第 450 行提前 `return`，因此该分支只对非 UI 的整型属性生效，安全。仅为风格冗余（可直接用 `((Integer) value).toString()`），不影响正确性，不建议为此单独改动。

### 2.3 P3 GTK 启动 —— ✅ 主要逻辑已修复（但见 P6）

`LimboGtkActivity.java`：

```java
79:  if (MachineController.getInstance().isRunning()) {
80:      notifyAction(MachineAction.UPDATE_NOTIFICATION, getString(...VMRunning));
89:      if (stopRequestedFromHome) { ... stopVmFromHome() ... }   // 停止路径
101:     return;                                                    // ← 运行中直接返回，不发 START_VM
102:  }
112: if (LimboSettingsManager.consumeVmShutdownByUser(this)) {
113:     Log.d(TAG, "VM was shut down by the user, not starting it again");
114:     finish();
115:     return;
116: }
122: new Thread(new Runnable() { ... notifyAction(MachineAction.START_VM, null); ... }, "limbo-gtk-start").start();
```

复验通过：
- 非 running 时**确实发 `MachineAction.START_VM`**（第 127 行），修掉了上轮「只出空窗口、guest 不 boot」。
- **遵守 `consumeVmShutdownByUser`**（第 112 行），与 SDL 的 `LimboSDLActivity:913` 一致，不会出现「关机后又被重开」。
- START_VM 放在**独立线程**（第 122-132 行），避免在 UI 线程创建 QEMU/service，这点比上轮更稳。
- `running` 时提前 `return`，**因此 STOP 路径与 START_VM 在同一 `onCreate` 里天然互斥**（这是 P6 抢跑分析的关键，见 §3）。

---

## 3. P6 挑刺（重点）—— 🟠 逻辑正确，但有 2 个结构性缺口

### 3.1 停止路径已按后端分别 arm —— ✅ 正确

`HomeController.java:371-388`：

```java
371: if (MachineController.getInstance().isRunning()) {
375:     String ui = Config.normalizeUI(MachineController.getInstance().getMachine().getUI());
377:     boolean gtk = Config.UI_GTK.equals(ui) && Config.isGtkUsable();
378:     if (gtk) {
381:         LimboGtkActivity.requestStopOnNextOpen();
382:     } else {
384:         LimboSDLActivity.pendingStopImmediate = true;
385:     }
386:     Class<?> target = gtk ? LimboGtkActivity.class : LimboSDLActivity.class;
387:     activity.startActivity(new Intent(activity, target));
```

这是对的：GTK 与 SDL 各用各的握手通道，不再像上轮那样只设 SDL 的静态标志（`HomeController.java:375` 上轮 bug 已消除）。

### 3.2 「是否会与 onCreate 里的 START_VM 抢跑」—— ✅ **不抢跑**

`LimboGtkActivity.onCreate` 的结构是：

- `isRunning()` 为真 → 走第 79 行分支 → 仅更新通知 + 可选 stop → 第 101 行 `return`，**根本走不到第 122 行的 START_VM**。
- `isRunning()` 为假 → 走第 112 行 shutdown 守卫 → 第 122 行 START_VM；此时 `stopRequestedFromHome` 分支在第 89 行内（属 running 分支），**不会被执行**。

两个分支互斥且都 return，**没有抢跑**。这是本轮设计最扎实的一点。

### 3.3 🔴 P6-a（中）：停止链路在 `singleTask` 下可能整条不执行

`AndroidManifest.xml:68-73` GTK 是 `android:launchMode="singleTask"`。`HomeController` 用 `startActivity` 重新打开它时，**若该 Activity 实例仍存活**，系统走 `onNewIntent`，**不调用 `onCreate`**；而 `LimboGtkActivity` **没有 `onNewIntent` 覆写**（grep 计数 0），`ToplevelActivity` 也没有。

对比 SDL：`LimboSDLActivity` 把标志消费放在 **`onResume`**（第 656-671 行），`onResume` 在 `singleTask` 重入时**会**被调用，所以 SDL 路径稳。
GTK 把标志消费放在 **`onCreate`**（第 65/89 行），`onResume` 无覆写（grep 计数 0）。

**后果**：`HomeController` 的「停止」兜底（4s 后 reopening 并发 stop）在「GTK 窗口还在后台存活」这一最常见场景下**可能完全不触发**——只能依赖第 357 行的 `requestStop()` 直通（注释本身也承认那条可能被挂起的 display 线程挡住）。

**建议**（供 Lead 决定，我不改源码）：把 `stopRequestedFromHome` 的消费从 `onCreate` 移到 `onResume`（对齐 SDL），或补 `onNewIntent` 并在其中消费。这是**采用点错误**，不是逻辑错误。

### 3.4 🟡 P6-b（低）：`sPendingStopFromHome` 无生命周期的跨实例残留

`requestStopOnNextOpen()` 设的是 **static** 标志（`LimboGtkActivity.java:56,59`）。若用户点了「停止」但后续 Activity 因别的原因被销毁/未真正打开，该标志会残留，**下一次**打开 GTK 窗口会被误判成「要停止」。
条件较苛刻（需 4s 定时器已 arm 且 target 未启动），但与 SDL 的 `pendingStopImmediate` 同样有此性质，可归为「与既有行为一致的低风险」。

---

## 4. P7 挑刺（重点）—— ✅ 防住改写，不误伤正常路径

`LimboActivity.java:574-596`：

```java
578:  String ui = (String) ((ArrayAdapter<?>) mUI.getAdapter()).getItem(position);
586:  String stored = Config.normalizeUI(getMachine().getUI());
587:  if (ui == null || stored.equals(ui)) {
588:      return;                                   // ① 同值不写
589:  }
590:  if (Config.UI_GTK.equals(stored) && !Config.isGtkUsable()) {
593:      return;                                   // ② GTK 不可用时不动
594:  }
595:  notifyFieldChange(MachineProperty.UI, ui);
```

### 4.1 「打开配置页就把 GTK 改成 VNC」—— ✅ 已防住

`populateUI()`（第 3787-3793 行）的列表来自 `ArchDefinitions.getUIValues()`；GTK 不可用时列表只有 `[VNC, SDL]`（`ArchDefinitions.java:152-163`）。`SpinnerAdapter.setDiskAdapterValue` 对找不到的值 `setSelection(Math.max(pos,0))` = 位置 0 = **VNC**（`SpinnerAdapter.java:89-100`）。此时回调进来 `ui="VNC"`，但 `stored="GTK"` → 命中第 590 行守卫 → **return，不写库**。

关键改进确认：`mUI` 仍未进 `programmaticSpinners` 抑制名单（本轮仍 grep 无 `suppressSpinnerCallbacks(mUI)`），**所以正是这条业务守卫在兜底**——靠抑制会失效，靠语义守卫才稳。这点修复方向是对的。

### 4.2 「有没有误伤正常 VNC ↔ SDL 切换」—— ✅ 没有

正常路径推演：

| 场景 | ui（spinner） | stored | 判定 | 结果 |
|---|---|---|---|---|
| VNC→SDL，GTK 可用 | `"SDL"` | `"VNC"` | ①不等（stored≠ui）、②stored 非 GTK | 写入 `"SDL"` ✅ |
| SDL→VNC，GTK 可用 | `"VNC"` | `"SDL"` | 同上 | 写入 `"VNC"` ✅ |
| GTK→SDL，GTK 可用 | `"SDL"` | `"GTK"` | ①不等，②`isGtkUsable()`=true 不拦 | 写入 `"SDL"` ✅ |
| GTK→VNC，GTK 可用 | `"VNC"` | `"GTK"` | 同上 | 写入 `"VNC"` ✅ |
| 同值重选（任何） | `"SDL"` | `"SDL"` | ①命中 | return（无副作用）✅ |
| GTK 不可用、stored=GTK | `"VNC"`(回退 0) | `"GTK"` | ②命中 | return ✅ |

**结论：没有误伤。** 第 587 行 `stored.equals(ui)` 用归一化后的值比较，而 spinner 项来自同一套 `Config.UI_*` 常量，字符串严格相等，不存在大小写/空白导致的假不等。

### 4.3 🟡 P7-a（低）：`enable_SDL=false` 时会出现新边角

`Config.enable_SDL` 是 `public static final boolean enable_SDL = true`（`Config.java:67`），正常构建恒为 true，所以 `[VNC, SDL]` 都在。**若将来改成 false**：stored=`"SDL"` 的机器在列表里找不到 SDL → 回退 0=VNC → `ui="VNC"`，`stored="SDL"`，第 587 行不等、第 590 行不拦（stored 非 GTK）→ **会被改写为 VNC**。这是与 P7 同源的漏洞，只是当前 `enable_SDL=true` 使其不可达。记录备查，不建议现在改。

### 4.4 🟡 P7-b（低）：只防 GTK，不防「未知/未来后端」

守卫条件是 `stored == GTK && !isGtkUsable()`。若未来出现第四种后端（或 stored 是脏数据被 `normalizeUI` 归成 SDL），同样会被 spinner 回退改写。建议把守卫泛化成「stored 不在当前 ui 列表里 → 不动」，比枚举 GTK 更稳。

---

## 5. 原生补丁复验 —— ✅ 全部干净应用

### 5.1 环境说明（重要）

Lead 指定的 `/tmp/qemu1100/qemu-11.0.0` **是不完整的树**：

```bash
$ find /tmp/qemu1100/qemu-11.0.0 -type f | wc -l
14442
$ ls /tmp/qemu1100/qemu-11.0.0/meson.build
ls: cannot access 'meson.build': No such file or directory      # ← 根 meson.build 缺失
```

第一次在该树上试 `qemu-11.0.0-gtk4-android.patch` 得到 `error: meson.build: No such file or directory`（exit 1），这**是树不完整导致的假失败**，不是补丁问题。

改用完整的 pristine 树（`/tmp/qemu1100pristine/qemu-11.0.0`，78380 文件，`VERSION=11.0.0`）：

```bash
$ find /tmp/qemu1100pristine/qemu-11.0.0 -type f | wc -l
78380
$ test -f /tmp/qemu1100pristine/qemu-11.0.0/meson.build && echo YES
YES
```

### 5.2 QEMU 11.0.0 三个补丁 —— ✅ 全部 exit 0

在 `/tmp/qemu_ptest`（pristine 副本）上：

```bash
######## qemu-11.0.0-android-egl.patch
  git apply --check exit=0
######## qemu-11.0.0-android-gl-scanout.patch
  git apply --check exit=0
######## qemu-11.0.0-gtk4-android.patch
  git apply --check exit=0
```

### 5.3 virglrenderer 两个补丁 —— ✅ 全部 exit 0

```bash
$ cd /tmp/virgl110 && git apply --check -p1 .../virglrenderer-android-egl.patch
exit=0
$ patch -p1 --dry-run -t < .../virglrenderer-android-egl.patch
checking file meson.build
checking file src/mesa/util/anon_file.c
checking file src/venus/vkr_buffer.c
checking file src/venus/vkr_device.c
checking file src/venus/vkr_device_memory.c
checking file src/venus/vkr_image.c
checking file src/venus/vkr_instance.c
checking file src/venus/vkr_physical_device.c
checking file src/vrend_renderer.c
patch exit=0
```

virglrenderer-android-barrier.patch：

```bash
$ git apply --check -p1 .../virglrenderer-android-barrier.patch
exit=0
$ patch -p1 --dry-run -t < .../virglrenderer-android-barrier.patch
checking file src/mesa/util/u_thread.h
checking file src/virgl_util.c
patch exit=0
```

### 5.4 ★ barrier 补丁「误伤另一处 guard」专项检查 —— ✅ **未误伤**

Lead 特别点出：`util_thread_get_time_nano` 与 `util_barrier` 两处 guard 原文**完全相同**，`patch` 按上下文定位，极易改错。

原始文件确实有两处完全相同的字符串：

```bash
$ grep -n "defined(HAVE_PTHREAD) && !defined(__APPLE__) && !defined(__HAIKU__)" /tmp/virgl110/src/mesa/util/u_thread.h
245:#if ...                                    ← util_thread_get_time_nano（pthread_getcpuclockid）
285:#if ...                                    ← util_barrier
$ grep -c "..." → 2
```

实际打补丁后 diff（唯一改动）：

```bash
$ diff -u /tmp/virgl110/src/mesa/util/u_thread.h /tmp/virgl_apply_test/src/mesa/util/u_thread.h
@@ -282,7 +282,7 @@
  * util_barrier
  */
-#if defined(HAVE_PTHREAD) && !defined(__APPLE__) && !defined(__HAIKU__)
+#if defined(HAVE_PTHREAD) && !defined(__APPLE__) && !defined(__HAIKU__) && !defined(__ANDROID__)
```

验证：

```bash
$ grep -n "__ANDROID__" /tmp/virgl_apply_test/src/mesa/util/u_thread.h
285:# ... && !defined(__ANDROID__)          ← 只有 285（barrier）
```

**第 245 行（`util_thread_get_time_nano`）保持原样**，一字节未动 ✓。补丁头部注释（第 18-20 行）也明确写了「Only the barrier guard is touched」，文档与行为一致。

### 5.5 附带发现：barrier 补丁已顺带并入 dma-buf 修复

`virglrenderer-android-barrier.patch` 现在包含 **两个 hunk**：`u_thread.h` 的 barrier guard + `virgl_util.c:366-372` 的 `DMA_BUF_SET_NAME_B` 守卫（`b2dd2c8` 提交加入）。两者都在 pristine 1.1.0 上 `dry-run` 通过（§5.3），且顺序无冲突。

---

## 6. CI 证据核对 —— 🔴 4 轮全失败（但失败点在推进）

### 6.1 Run 清单（GitHub API 实查）

```bash
$ GET /repos/xis3794/limbo/actions/runs?branch=feat/gtk4-virgl-experimental-ui
41263883718 | b2dd2c8 | failure | 2026-11-01T02:07:39Z
41263598909 | b2dd2c8 | failure | 2026-11-01T02:03:33Z
41263005015 | cc9b52e | failure | 2026-11-01T01:54:40Z   ← 本轮指定
41262208757 | 7a14501 | failure | 2026-11-01T01:42:38Z   ← 上轮
```

注意：Lead 标称当前 head 为 `cc9b52e`，但**实际工作区已是 `b2dd2c8`**，且 `b2dd2c8` 又触发了 2 次 run。本报告以 `b2dd2c8` 的最新证据为主，同时保留 `cc9b52e`/`7a14501` 的对照。

### 6.2 ✅「Resolve build options」—— 4 轮全部 resolved gtk=true virgl=true QEMU=11.0.0

```bash
$ grep "resolved:" run_41263005015_job.log
resolved: QEMU=11.0.0 host=arm64-v8a guest=x86_64-softmmu gtk=true virgl=true
$ grep "resolved:" run_41263883718.log
resolved: QEMU=11.0.0 host=arm64-v8a guest=x86_64-softmmu gtk=true virgl=true
$ grep "resolved:" run_41262208757.log
resolved: QEMU=11.0.0 host=arm64-v8a guest=x86_64-softmmu gtk=true virgl=true
```

上轮 P5（默认 11.1.2 会把 GTK 门禁关掉）**已修复**，`7a14501` 把默认改成 11.0.0 后门禁不再误杀。

### 6.3 各轮失败点演进（关键：确实在往前推进）

| Run | SHA | 失败步骤 | 失败点 |
|---|---|---|---|
| 41262208757 | 7a14501 | step 12 Build native libraries | `u_thread.h:287` **pthread_barrier_t 未定义** |
| 41263005015 | cc9b52e | step 12 Build native libraries | `virgl_util.c:371` **`u64` 未定义**（barrier 已修好） |
| 41263883718 | b2dd2c8 | step 12 Build native libraries | **GTK4 meson**：glib 依赖 2.84 未满足 |

`7a14501` 的 barrier 报错原文：

```
../src/mesa/util/u_thread.h:287:9: error: unknown type name 'pthread_barrier_t'; did you mean 'pthread_attr_t'?
../src/mesa/util/u_thread.h:291:4: error: implicit declaration of function 'pthread_barrier_init' is invalid in C99
```

`cc9b52e` 的 u64 报错原文（证明 barrier 修复**生效**，已经编过 barrier 那一段）：

```
FAILED: [code=1] src/libvirgl.a.p/virgl_util.c.o
../src/virgl_util.c:371:17: error: use of undeclared identifier 'u64'
      ioctl(fd, DMA_BUF_SET_NAME_B, name);
```

### 6.4 ✅「virgl 是否编过」—— **最新一轮（b2dd2c8）已编过**

```bash
$ grep "libvirglrenderer.so" run_41263883718.log
4523: [52/55] Linking static target src/libvirgl.a
4524: [53/55] Linking target src/libvirglrenderer.so
4525: [54/55] Generating symbol file src/libvirglrenderer.so.p/libvirglrenderer.so.symbols
4529: Installing src/libvirglrenderer.so to .../virglrenderer/install-android/lib
4534: [OK] virglrenderer built: .../obj/local/arm64-v8a/libvirglrenderer.so (venus=false)
```

**`libvirglrenderer.so` 已实际产出**。同时 `libepoxy.so` 也成功：

```bash
4257: [9/9] Linking target src/libepoxy.so
4267: [OK] libepoxy built: .../obj/local/arm64-v8a/libepoxy.so
4268: [OK] epoxy.pc at .../libepoxy/install/lib/pkgconfig/epoxy.pc
```

⚠️ 注意 `(venus=false)`：venus（Vulkan over virtio）未启用。这符合 `build_libepoxy.sh`/meson 的取舍，但意味着 GPU 加速只有 GL/virgl 路径，无 Vulkan 直通。

### 6.5 ❌「GTK4 是否编过」—— **没有，卡在 glib 版本**

```bash
$ grep -n "Dependency glib-2.0" run_41263883718.log
6059: Dependency glib-2.0 for host machine found: NO. Found 2.76.6 but need: '>= 2.84'
6065: Run-time dependency glib-2.0 found: NO  (tried pkg-config and cmake)
6066: Looking for a fallback subproject for the dependency glib-2.0
6067: Applying diff file "glib/hack.patch"
6068: ERROR: Subproject glib is buildable: NO
6070: meson.build:451:17: ERROR: Diff file ".../gtk4android/subprojects/packagefiles/glib/hack.patch" does not exist
6074: make[2]: *** [Makefile:352: gtk] Error 1
```

根因两个，都在 gtk4android 这一侧：

1. **glib 版本不足**：`gtk4android/meson.build:14-15` 要求 `glib_major_req=2, glib_minor_req=84` → `>= 2.84`；而 `scripts/build_gtk4android.sh:97-99` 只把 `PKG_CONFIG_LIBDIR` 指向 `$JNI/pc` 和 `libepoxy/install/lib/pkgconfig`，其中的 glib 是 QEMU 侧的 **2.76.6**（`build.yml:222` 下载）。**2.76.6 < 2.84**，无法满足。
2. **wrap 的 hack.patch 缺失**：`subprojects/glib.wrap` 声明 `diff_files=glib/hack.patch` 且 `revision=main`（gnome gitlab），但**该文件在 gtk4android 仓库里不存在**（`/tmp/gtk4android/subprojects/packagefiles/glib/` 不存在），meson 直接 `ERROR: ... does not exist` 放弃 subproject fallback。

**后果**：`make gtk` 失败 → `deps` 失败 → 无 `libgtk-4.so` → CI 的 `Verify GTK4 / GPU artifacts` 步骤（第 339-386 行）**全部 skipped**（step 13-18 均 skipped）。
即使修好 glib，`build_gtk4android.sh` 里 `--wrap-mode=default` 仍会在找不到本地 glib 时尝试抓 `revision=main` 的 glib —— 拉一个未固定的 main 分支做交叉编译风险很高，建议同时钉住 revision 并补齐 hack.patch（或改用本地 glib 前缀 + 显式禁用该 wrap）。

### 6.6 ❌「Verify GTK4 / GPU artifacts 的 readelf 断言」—— **未能验证（步骤 skipped）**

该步骤（`build.yml:339-386`）会做三件事：
- `check libgtk-4.so / libepoxy.so / libvirglrenderer.so` 是否在 `obj/local/$BUILD_HOST`
- `readelf -d libqemu-system-x86_64.so` 断言 `libepoxy` / `libvirglrenderer` / `libgtk-4` 出现在 NEEDED
- 检查 `jniLibs` 里是否有 `libgtk-4.so`

但本轮在 step 12 就失败，step 15 状态是 **skipped**，所以**这三个断言一条都没跑**。我无法给出「readelf 断言通过」的证据——**没有**。

顺带核查该断言本身的健壮性（代码层面）：`readelf ... | grep -q 'libgtk-4'` 若将来 `fix_needed.py` 把 NEEDED 改名成无版本名会被匹配到，尚可；但 `check()` 只查 `obj/` 不查 `jniLibs`，`jniLibs` 另由 381-386 行单独检查，逻辑覆盖尚可。

### 6.7 ❌ APK / classes.dex 内容 —— **完全无法验证**

- CI `artifacts` 为 **0**（`GET /runs/41263883718/artifacts` → `total_count: 0`），APK 从未上传（step 18 skipped）。
- 本地 APK 全是**功能分支之前**的产物，不含任何 GTK/exp 内容：

```bash
/tmp/apk2/Limbo.apk        2026-10-31 11:27
/tmp/apk3/Limbo.apk        2026-10-31 15:36
/tmp/apkdes/Limbo.apk      2026-10-31 16:36
/tmp/apk102/Limbo.apk      2026-10-31 16:39
$ unzip -l /tmp/apk2/Limbo.apk | grep -E "libgtk-4|libepoxy|libvirglrenderer"
（空）
```

因此以下三项**全部无法确认**，我不做任何假设性结论：
- `lib/arm64-v8a/{libgtk-4.so, libepoxy.so, libvirglrenderer.so}` 是否存在
- `classes.dex` 是否含 `LimboExperimentalActivity` / `LimboGtkActivity` / `LimboGtk`
- APK 签名与可安装性

**另外发现一个 CI 缺口**：`build.yml` 里**没有任何**针对 `classes.dex` 的断言（grep `classes.dex|dexdump|LimboExperimentalActivity|LimboGtk` 在 workflow 中 **0 命中**）。也就是说，即使构建成功，也**不会**自动校验 dex 里是否真有这三个类——验收标准里「classes.dex 里有 LimboExperimentalActivity、LimboGtkActivity」这一条目前**没有 CI 守门**，需要人工 `unzip + dexdump` 或补一个断言步骤。这是个真实的可测性缺口。

---

## 7. 疑点分级汇总

| 级别 | 编号 | 内容 | file:line |
|---|---|---|---|
| 🔴 阻断 | C-1 | GTK4 编译被 glib >= 2.84 挡住；glib.wrap 的 hack.patch 缺失 | `build_gtk4android.sh:97-99`、`gtk4android/meson.build:14-15`、`glib.wrap` |
| 🟠 严重 | P6-a | GTK 停止标志在 `onCreate` 消费，但 manifest 是 `singleTask`，重入走 `onNewIntent`（未实现）→ 停止链路可能不执行 | `LimboGtkActivity.java:65,89`、`AndroidManifest.xml:72`、对照 `LimboSDLActivity.java:656-671` |
| 🟠 严重 | C-2 | CI 无 `classes.dex` 断言，验收标准 3 无守门 | `.github/workflows/build.yml`（grep 0 命中） |
| 🟡 中 | C-3 | gtk4android `revision=main` 未固定 + `--wrap-mode=default`，将来可能拉未固定 glib main 分支 | `gtk4android/subprojects/glib.wrap:4`、`build_gtk4android.sh:106` |
| 🟡 中 | C-4 | README 侧：`/tmp/qemu1100` 不是完整树（缺根 `meson.build`），用它验补丁会得假失败 | `/tmp/qemu1100/qemu-11.0.0`（14442 文件 vs pristine 78380） |
| 🟡 中 | P1-a | 兜底分支用 `FLAG_ACTIVITY_NEW_TASK`，`singleTask` 重入可能丢 extras（`LimboActivity` 无 `onNewIntent`） | `LimboExperimentalActivity.java:136` |
| 🟡 中 | P6-b | `sPendingStopFromHome` 是 static 且无生命周期清理，可能跨实例残留 | `LimboGtkActivity.java:56,59,65` |
| 🔵 低 | P7-a | `enable_SDL=false` 时 stored=SDL 会被回退改写成 VNC（当前不可达，`enable_SDL=true`） | `Config.java:67`、`LimboActivity.java:587-595` |
| 🔵 低 | P7-b | 守卫只枚举 GTK，未泛化成「stored 不在列表内就不动」 | `LimboActivity.java:590` |
| 🔵 信息 | I-1 | venus=false，无 Vulkan 路径 | CI `libvirglrenderer.so (venus=false)` |
| 🔵 信息 | I-2 | `LimboGtk.isAvailable()` 全仓无调用者（上轮 P4 遗留），回退 SDL 的兜底仍没接 | `LimboGtk.java:74` |
| 🔵 信息 | I-3 | `javac` 存在但无 `android.jar`/androidx classpath，未做真实编译 | 见 §1、§8 |
| 🔵 信息 | I-4 | `MachineOpenHelper.java:455` 仍留 `(int) params[1]`，但有 `instanceof` 守卫且 UI 已提前 return，**安全**，仅冗余 | `MachineOpenHelper.java:454-455` |

---

## 8. 未验证项与风险（诚实边界）

1. **未做真实编译**。`javac 17` 可用，但本机**没有** `android.jar`、androidx、material、recyclerview 的任何 jar（`find / -name android.jar` 无结果），没有 `aapt2`/`d8`。`javalang` 只验语法不验语义。因此：类型错误、`R.*` 资源 ID 的存在性、`@style/@attr` 是否被 material 1.1.0 提供、方法签名不匹配等，**全未验证**。
2. **`javalang` 的局限**：它对 Java 8 之后语法也接受，所以「83/83 通过」只证明括号/结构合法，**不能**替代「Java 8 语言级别合规」。Java 8 违规我另用 regex 扫描（`var`/`->`/`record`/`"""`/匿名类钻石）仍为 0，但 regex 本身不是完备的语法分析。
3. **CI 只到 step 12**。GTK/GPU artifact 断言（step 15）、Gradle 构建（step 16）、签名（17）、上传（18）**在这 4 轮里全部 skipped**，无一执行。
4. **无 APK 可验**。artifacts=0；本地 4 个 APK 均为 10-31 的旧产物，不含 GTK/exp。
5. **GTK/GPU 真机行为**：`-display gtk` 是否真起窗、`gl=on` 在 HUAWEI CTR-AL00 (API 31, 无 KVM) 是否可用、GDK android 后端 `AMotionEvent_fromJava` 是否被 NDK r23b + API 31 sysroot 正确导出 —— 全部未验证，且被 C-1 阻断。
6. **DB 迁移**：`UI` 列在升级后的实际表现、P2 修复后既有机器配置是否被正确重写 —— 未验证（需真机）。
7. **P6/P7 的运行时行为**：单线程分析不能证明「无 ANR/无时序竞争」；`singleTask` 下 `onNewIntent` 是否真被调用、extras 是否丢，需真机 logcat 确认。
8. **文件仍在变动**：验证期间 HEAD 从 `cc9b52e` → `b2dd2c8`，且 `b2dd2c8` 又触发 2 个新 run。若 Lead 继续提交，本报告 §3~§4 的行号可能失效，请以 §快照指纹 的 SHA1 判断。
9. **`/tmp/qemu1100` 的坑**：它不是 pristine 完整树，**任何用它的补丁复验都会得到假阴性**。建议后续统一用 `/tmp/qemu1100pristine/qemu-11.0.0`（78380 文件）。

---

## 9. 建议 Lead 的处理顺序

1. **C-1（GTK4 glib）** —— 唯一阻断 GTK 的构建问题。两条都要做：把 glib 提到 ≥2.84 交给 gtk4android（或设 `-Dglib=...` 指向新前缀），并补齐 `subprojects/packagefiles/glib/hack.patch`；顺手把 `glib.wrap` 的 `revision=main` 钉成 tag（C-3）。
2. **P6-a（停止标志改到 `onResume` 或补 `onNewIntent`）** —— 一行级别的结构性修正，但对「后台 GTK 窗口能否从首页停止」影响很大。
3. **C-2（补 `classes.dex` 断言）** —— 验收标准 3 现在完全没有守门，建议在 APK 步骤后加 `unzip -p classes*.dex | grep` 或 `dexdump` 断言。
4. P1-a / P6-b / P7-a / P7-b 可随上述修复顺带处理；I-2（`isAvailable()` 无调用者）建议补进 `localDisplayActivityClass()` 作为双重保险。
5. 验收前请把 `/tmp/qemu1100` 换成 pristine 树，避免后续补丁复验再出假失败。
