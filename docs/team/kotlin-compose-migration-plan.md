# Kotlin + Compose + Miuix 迁移方案

> 目标用户诉求：「直接不用 Java，直接用 kt，解决所有问题，还是仿照 npatch」
>
> 本文档回答三件事：能不能真的用上 NPatch 的 Miuix、要动哪些文件、有什么代价。

---

## 0. 结论先说

**能做到「Kotlin + Compose + 真正的 Miuix 0.9.4」**，但必须付出一次**全仓库工具链升级**，而且不能一次性切换全部代码。

| 方案 | 结果 | 代价 |
|---|---|---|
| A. 只把新 UI 写成 Kotlin（无 Compose） | 观感不变，白改 | 无意义 |
| B. 新 UI 用 Kotlin + Compose + Miuix | **视觉与 NPatch 像素级一致** | 工具链升级 + 一个新模块 |
| C. 全项目 Kotlin 化（30k 行 Java 全转） | 无额外收益 | 极高风险，不建议 |

**建议 B**：新 UI 独立成 Kotlin 模块，用真正的 Miuix 组件；老代码（30,786 行 Java）原样保留，通过一个薄接口对接。

---

## 1. 硬约束（已核实的事实，非猜测）

### 1.1 Miuix 0.9.4 要求 compileSdk 37

```
$ unzip -p miuix-ui-android-0.9.4.aar \
    META-INF/com/android/build/gradle/aar-metadata.properties
minCompileSdk=37
minAndroidGradlePluginVersion=1.0.0
```

Miuix 所有版本（0.9.0 / 0.9.1 / 0.9.2 / 0.9.3 / 0.9.4）的 `minCompileSdk` **都是 37**，
没有更低的版本可退。也就是说：**想用真 Miuix，就必须 compileSdk 37**。

而 Miuix 的 manifest 是 `minSdkVersion="24"`（不是 37），所以**运行时最低仍是 Android 7.0**，
只是编译期需要 API 37 的 android.jar。

### 1.2 当前仓库的工具链

| 组件 | 当前 | 需要 |
|---|---|---|
| Gradle | 7.0.2 | 8.9+ |
| AGP | 4.1.1 | 8.6+（Compose 1.10 的 aar-metadata 要求） |
| compileSdk | 29 | 37 |
| build-tools | 29.0.3 | 37.0.0 |
| Kotlin | 无 | 2.0.21 |
| Compose | 无 | BOM 2025.12.01（ui 1.10.0 / material3 1.4.0） |

**已核实可下载**：

```
platform-37.0_r02.zip                 200  (67 MB)
build-tools_r37_linux.zip             200  (66 MB)   ← 注意是下划线 _linux，不是 -linux
gradle-8.9-bin.zip                    200
gradle-8.7.3.pom / 8.5.2.pom / 9.5.0.pom   200
kotlin-gradle-plugin 2.0.21           200
compose-compiler-gradle-plugin 2.0.21 200
compose-bom 2025.12.01                200
miuix-ui / miuix-core / miuix-squircle 0.9.4  200
```

### 1.3 关键风险：AGP 8.x 与 NDK 库打包

老项目的 `.so` 是靠 `jniLibs.srcDirs` 手工放进去的，**不走 AGP 的 NDK 构建**，
所以升级 AGP **不影响 C/C++ 编译产物**。这是最有利的一点——原生侧零改动。

但有两处必须验证：
1. AGP 8.x 默认开启 `android.useAndroidX=true`（仓库已有）且**强制** `enableJetifier` 相关校验；
2. AGP 8.x 对 `AndroidManifest.xml` 的 `package` 属性报错，要求改用 `namespace`。

第 2 点是**确定的破坏性变更**，5 个模块的 manifest 都要改。

---

## 2. 目标结构

```
limbo-android-lib/                 （保持 Java，不动）
  └─ com.max2idea.android.limbo.ui.exp.bridge
       └─ ExpUiBridge.java         ← 唯一对接口，含 @JvmStatic

limbo-android-exp/                 （新模块，纯 Kotlin + Compose + Miuix）
  ├─ build.gradle.kts
  └─ src/main/
       ├─ AndroidManifest.xml
       └─ kotlin/com/max2idea/android/limbo/ui/exp/
            ├─ ExperimentalActivity.kt      Compose 入口
            ├─ HomeScreen.kt                复刻 NPatch HomeScreen
            ├─ components/
            │    ├─ StatusCard.kt
            │    ├─ InfoCard.kt
            │    ├─ MachineCard.kt          ← 复刻 AppItem
            │    ├─ CountCard.kt
            │    └─ FloatingBottomBar.kt    ← 复刻 FloatingGlassBottomBar
            ├─ theme/LimboMiuixTheme.kt
            └─ vm/MachineListViewModel.kt
```

`limbo-android-x86` 依赖这个新模块。老 `ui/exp/*.java` 保留但在编译期排除
（或直接删除，由 `ExpPrefs` 的开关决定走哪条路）。

---

## 3. 需要改的文件清单

### 3.1 根构建

| 文件 | 改动 |
|---|---|
| `build.gradle` → `build.gradle.kts` | AGP 4.1.1 → 8.7.3；加 Kotlin 2.0.21 插件；加 compose-compiler 插件 |
| `gradle/wrapper/gradle-wrapper.properties` | 7.0.2 → 8.9 |
| `gradle.properties` | 加 `android.useAndroidX=true`（已有）、`kotlin.code.style=official` |
| `settings.gradle` → `.kts` | 加 `include ':limbo-android-exp'`；加 `pluginManagement` |

### 3.2 五个模块的 build.gradle

全部要改：

```kotlin
// 旧
apply plugin: 'com.android.library'
android {
    compileSdkVersion 29
    buildToolsVersion '29.0.3'
    defaultConfig { minSdkVersion 21; targetSdkVersion 28 }
}
// 新
plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.limbo.emu.lib"
    compileSdk = 37
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
```

> **注意**：`sourceCompatibility` 从 1.8 提到 17 是必须的——AGP 8.x + Compose 编译出的字节码
> 需要 Java 17 目标。老 Java 代码本身不需要改，JDK 17 编译 1.8 语法的源码没问题。

### 3.3 五个 AndroidManifest.xml

`package="..."` 必须删掉，改到 build.gradle 的 `namespace`。
这是 AGP 8 的**硬错误**（不是 warning）。

### 3.4 CI

`.github/workflows/build.yml` 需要：
- JDK 11 → **JDK 17**（AGP 8 要求）
- `sdkmanager "platforms;android-29"` → `"platforms;android-37" "build-tools;37.0.0"`
- Gradle 7.0.2 → 8.9
- 新增 step：`chmod +x gradlew`（如果用 wrapper）

---

## 4. 实施顺序（每步都必须可编译）

1. **先升工具链，不改任何 UI**。目标：现有 Java UI 在 AGP 8 / compileSdk 37 下编译通过、APK 能装能跑。
   这一步是纯粹的风险隔离——如果 APK 起不来，问题一定在工具链，与 UI 无关。
2. **加空 Kotlin 模块**，只放一个 `class Ping`，确认 Kotlin 编译链通。
3. **加 Compose + Miuix 依赖**，只渲染一个空的 `MiuixTheme { Surface {} }`，确认 Miuix 能解析、能打包进 APK。
4. **实现 HomeScreen**，先做静态布局（假数据），确认视觉。
5. **接真实数据**，通过 `ExpUiBridge`。
6. **删掉旧 Java 版 exp UI**，把开关指向新的。

---

## 5. 代价与风险

| 项 | 评估 |
|---|---|
| APK 体积 | Compose 运行时 + Miuix ≈ **+3–5 MB**（当前 30 MB → 约 34 MB） |
| 首次启动 | Compose 初始化约 +200 ms |
| 最低 Android | Miuix minSdk **24**（Android 7.0）。当前项目 minSdk 21，需提到 24 或做兼容分支 |
| 构建时间 | 冷构建 +2–4 分钟（Kotlin 编译 + Compose 编译器） |
| 破坏面积 | 5 个模块 build.gradle + 5 个 manifest + CI + 根构建，**共 12 个文件** |
| 不可逆性 | 工具链升级后无法轻易回退到 AGP 4 |

**最大风险**：AGP 8 + compileSdk 37 + NDK r23b 的组合从未在本项目验证过。
`jniLibs` 是手工目录，理论上不受影响，但必须在 CI 上实测。

---

## 6. 关于「不用 Java」

需要澄清一个事实：**Kotlin 编译后跑在同一个 JVM/ART 上，和 Java 是同一套字节码**。
「改用 Kotlin」不会让程序更快、更稳，也不会解决任何运行时问题。
这次 SDL 起不来的根因（`free_sized` 符号缺失）是**原生库打包问题，与语言无关**——
换成 Kotlin 重写一遍，那个 bug 会一模一样地存在。

所以「用 Kotlin」的价值只在两点：
1. 新 UI 可以用 Compose + Miuix，真正做到**视觉与 NPatch 一致**（这是 Java View 手写永远差一截的根本原因——squircle、真实模糊、形状系统都做不出来）；
2. 后续维护更简洁。

这两点成立，所以方案 B 值得做。但它是**为了 UI**，不是因为 Kotlin 本身能"解决所有问题"。

---

## 7. 与「解决所有问题」的关系

这次交付分两条独立的线，不要混：

| 线 | 内容 | 状态 |
|---|---|---|
| 崩溃修复 | `free_sized` → `copy_file_range`/`lchmod` → 符号门禁 | 已修复，CI 验证中 |
| UI 重写 | Kotlin + Compose + Miuix | 需要先完成工具链升级 |

**先让 SDL 能起来**，再谈界面。两个一起改，出问题就无法归因。
