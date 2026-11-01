# GTK4 + GPU 加速 + 实验性新 UI —— 团队计划（Lead 冻结版 v1）

## 0. 目标（本次交付物）
1. **GTK4 显示后端**：应用里出现 `GTK` 显示方式，QEMU 走 `-display gtk`（gtk4android 交叉编译），能在设备上起窗口画面。
2. **GPU 加速**：`--enable-opengl` / `-Dvirglrenderer=enabled`，显卡列表出现 `virtio-gpu-gl-pci`，SDL/GTK 可用 GL 输出（`gl=on`），宿主侧 GPU 渲染而不是纯软件。
3. **实验性新 UI**：一套全新界面（Kimi K3.1 写），默认**不生效**；只在「设置 → 实验性功能 → 新版界面」打开后生效，旧 UI 保持原样可用、代码不动。
4. 全部改动经 GitHub Actions 构建出可安装 APK 并交付。

## 1. 环境事实（不要重复摸索）
- 本机是 Android proot Ubuntu，**没有** Java/Android SDK/NDK/Gradle：本地不能编 APK，验证靠 GitHub Actions。
- CI：`.github/workflows/build.yml`（workflow_dispatch），输入 `qemu_version`(默认 11.1.2) / `build_host`(arm64-v8a) / `build_guest`(x86_64-softmmu)；CI 自己下载 QEMU 源码、glib、libffi、pixman、SDL2、NDK r23b、SDK 29。
- 目标机：HUAWEI CTR-AL00，Android 12 / **API 31**，arm64-v8a，8 核，无 `/dev/kvm`。
- 仓库：`/root/workspace/limbo`，当前分支 `feat/gtk4-virgl-experimental-ui`（所有写入者共用这一份工作区，严禁跨 scope 改文件）。
- **参考实现**：`/tmp/slug-limbo`（SlugAir928437/limbo 的浅克隆，GPL 同源，可直接抄思路/补丁）。参考它时只当技术资料，**界面部分不许参考**。

## 2. 写入范围（硬边界，冲突自负）
| 工作流 | 允许写 | 禁止写 |
|---|---|---|
| A native-accel | `limbo-android-lib/src/main/jni/**`、`.github/workflows/build.yml`、`scripts/**`、`tools/**` | 任何 `*.java`、`res/**` |
| B java-runtime | `limbo-android-lib/src/main/java/**`（除 `.../ui/exp/**`）、`limbo-android-lib/src/main/res/**`（除 `exp_*`）、`limbo-android-lib/src/main/AndroidManifest.xml` | `jni/**`、`.github/**`、`limbo-android-x86/**`、`ui/exp/**` |
| C exp-ui | `limbo-android-lib/src/main/java/com/max2idea/android/limbo/ui/exp/**`、`res/layout/exp_*.xml`、`res/values/strings_exp.xml`、`limbo-android-x86/**` | 其它 Java/res |
| D verify | `docs/verification/**`、`/tmp/**` | 所有源码 |

## 3. 冻结接口（谁都不许改，除非 Lead 同意）
- **显示后端名**：新增字符串常量 `"GTK"`（与 `"VNC"`、`"SDL"` 同级），存进 `Machine.getUI()/setUI()` 原字段（DB 里是 TEXT，无需迁移）。
- **GL 开关**：`Config.enableGtk = true`（静态，默认 false 由 A 在构建期或 B 在 UI 侧判断）、`Config.enableVirgl = true`，放在 `com.max2idea.android.limbo.main.Config`（B 写）。
- **VGA 选项**：`virtio-gpu-gl-pci` 出现在 `ArchDefinitions.getVGAValues()`（B 写）。
- **实验性开关**：新 UI 自己解析默认 SharedPreferences 的 `experimentalUi`（boolean，默认 false），由 C 在自己的包里实现（`ui/exp/ExpPrefs.java`），**不要求** B 改 `LimboSettingsManager`。
- **新旧切换**：`limbo-android-x86` 的 launcher `LimboEmuActivity.onCreate()` 开头判断 `expPrefs.experimentalUi()` 为真 → `startActivity(new Intent(this, LimboExperimentalActivity.class)); finish(); return;`（C 写）。旧 UI 一行都不删。
- **新 UI 包名**：`com.max2idea.android.limbo.ui.exp`，主 Activity 类名 `LimboExperimentalActivity`。

## 4. 各工作流的技术要点

### A. native-accel（GTK4 + GPU）
1. `gtk4android`：`https://github.com/SlugAir928437/gtk4android.git` 分支 `master`；用法照 `/tmp/slug-limbo/limbo-app/src/main/jni/Makefile` 的 `config-gtk`/`gtk`/`CREATE_GTK_MESON_CROSS_FILE`（meson cross file、`-Dandroid-backend=true -Dx11-backend=false`、PKG_CONFIG_LIBDIR 指向 jni 自己编的 glib/libffi）。
2. `virglrenderer`：`https://gitlab.freedesktop.org/virgl/virglrenderer.git` tag `1.1.0`；meson 选项按仓库实际声明动态拼（venus 见 `USE_VIRGL_VENUS`）。
3. **epoxy**：不要依赖 GTK4 前缀。独立编 libepoxy（`https://github.com/anholt/libepoxy` 或发行 tarball）成 Android 库，产出 `epoxy.pc`，供 QEMU `-Dopengl=enabled` 与 virglrenderer EGL 使用；这样 GPU 不依赖 GTK4 是否编成功。
4. QEMU 侧补丁（从 `/tmp/slug-limbo/limbo-app/src/main/jni/patches/` 取，改成适配 **11.1.2**）：
   - `qemu-v11.0.0-android-egl.patch`（96 行）：Android 无 render node，EGL_DEFAULT_DISPLAY + GLES3。
   - `qemu-v11.0.0-gtk-spice-gl.patch`（263 行）：GtkGLArea / gl=on。
   - `virglrenderer-android-egl.patch`（633 行）：virglrenderer 在 Android 上的 EGL/无 gbm 适配。
   做法建议：本地 `wget https://download.qemu.org/qemu-11.1.2.tar.xz` 解包，`git apply --check` 试，冲突就 `-3`/手工 rebase，产出正式补丁文件放进 `jni/patches/`。
5. Makefile：加 `config-gtk`/`gtk`、`config-virgl`/`virgl`、`config-epoxy`/`epoxy` 目标，接进 `config-deps`/`deps`/`_build-qemu` 链路；QEMU configure 增 `-Dopengl=enabled -Dvirglrenderer=enabled`（GTK 只在 `USE_GTK=true` 时 `-Dgtk=enabled`）。`libqemu-system-*.so` 要带上新依赖（`libepoxy.so`、`libvirglrenderer.so`、GTK 相关 `.so`），并按现有 `fix_needed.py` 规则改名/清 verneed。
6. CI：新增输入 `use_gtk`、`use_virgl`（默认 `true`）；新增 fetch 步骤（gtk4android / virglrenderer / epoxy）；把 `USE_GTK`/`USE_VIRGL` 导出；**保持** `qemu_version` 默认 11.1.2、`build_guest=x86_64-softmmu`、host `arm64-v8a`。

### B. java-runtime（Java 侧接线）
1. `ArchDefinitions.getUIValues()`：`SDL` 之后加 `GTK`（仅在 `Config.enableGtk` 且 `Build.VERSION.SDK_INT >= 31` 时）。
2. `ArchDefinitions.getVGAValues()`：x86/x86_64 加 `virtio-gpu-gl-pci`（`Config.enableVirgl` 时）。
3. `VMExecutor.addUIOptions()`：
   - `GTK` → `-display gtk,gl=on`（`Config.enableVirgl` 时）否则 `-display gtk`；GTK 模式**不**加 `-monitor vc`。
   - SDL + virgl → `-display sdl,gl=on`。
   - 保持 VNC/SDL 现有行为不变（回归风险最高，改动要最小）。
4. 新增 `LimboGtkActivity`（`main/` 包）+ JNI 绑定（参考 `/tmp/slug-limbo/.../jni/LimboGtk.java` 与 `org/gtk/android/*.java`，但**按我们自己的命名/最小实现**）：表面/窗口交给 GTK，触摸与按键转发。
5. `Config`：加 `enableGtk`、`enableVirgl` 两个静态开关（默认 false，由 `BuildConfig`/构造器设置），并在设置页提供开关（**只加开关，不做新界面**）。

### C. exp-ui（Kimi K3.1 写新界面）
- 必须由 **Kimi K3.1** 写作：`provider=codearts model=kimi-k3.1-pro`（限流则 `buddy/kimi-k3.1-pro`）。
- 只写自己包 + `exp_*` 资源；旧界面一律不动、不 import 旧 Activity 的 UI 部分。
- 功能最小闭环（够用优先）：机器列表（读现有 DB）、新建/编辑跳旧页、启动、删除、回到旧版界面按钮；`ExpPrefs` 读写 `experimentalUi`。
- 视觉上做一套**独立风格**（深色玻璃卡片、圆角、大字号），不复刻旧 UI，也不复刻 SlugAir 的 MiuiX 界面。

### D. verify（独立验证）
- 静态：`grep`/脚本确认接口落地（`GTK` 在 UI 列表、`virtio-gpu-gl-pci` 在 VGA 列表、`experimentalUi` 默认 false、旧 UI 文件未被删改）。
- 构建：等 CI 跑完，检查日志（`USE_GTK/USE_VIRGL` 生效、`libqemu-system-x86_64.so` 的 `DT_NEEDED` 含 epoxy/virgl/libgtk-4）、产物 APK 存在且签名一致。
- APK 内容：`lib/arm64-v8a/` 下有 `libgtk-4.so`、`libepoxy.so`、`libvirglrenderer.so`；`classes.dex` 里有 `LimboExperimentalActivity`、`LimboGtkActivity`。
- 结论必须写进 `docs/verification/`，并明确列出未验证项与风险。

## 5. 模型路由（防限流）
- 复杂推理/原生构建/补丁 rebase：`codearts/deepseek-v4.1-flash`（团队默认路由）。
- 新 UI 写作：`codearts/kimi-k3.1-pro`，限流退 `buddy/kimi-k3.1-pro`。
- 简单机械活（文本、字符串、清单、脚本粘合、文档）：`buddy/minimax-m3.1-flash`，或免费档 `xdpool-aggregate/opencode/glm-5.3-flash-free`。
- **不要用 `codearts/openpangu-2.0-flash`**（用户明确否掉）。
- 多供应商交替：同一类活分给不同 provider，避免单账号限流。

## 6. 验收标准
1. 设置里打开实验性开关后，启动 App 进入**新界面**；关掉后回到旧界面（旧界面功能不回归）。
2. 机器配置里显示方式可选 `GTK`；选用后 QEMU 以 `-display gtk` 启动（日志可见）。
3. 显卡可选 `virtio-gpu-gl-pci`，且 QEMU 以 opengl/virgl 构建（`libqemu-system-x86_64.so` 链接 `libvirglrenderer.so`、`libepoxy.so`）。
4. CI 全绿，产出签名 APK 放到 `/sdcard/Download/DSH-Go/`。