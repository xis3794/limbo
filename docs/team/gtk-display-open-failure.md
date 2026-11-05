# Why `-display gtk` killed the app, and what was fixed

Field report (device): SDL and VNC work; pressing start on a GTK machine hangs the
UI and then the whole device screen goes black. Two log files came back:

`limbo_qemu_stderr.txt`

```
(libqemu-system-x86_64.so:16593): Gtk-WARNING **: 22:44:54.731: Failed to open display
Fontconfig error: Cannot load default config file: File not found
FORTIFY: pthread_mutex_lock called on a destroyed mutex (0x7a2f447ed0)
libunwind: Unsupported .eh_frame_hdr version
```

`limbo_native.txt` starts with `===== NATIVE CRASH sig=6 addr=0x2cb3000040d1 =====`
and its `[so-libs]` map proves `libgtk-4.so`, `libglib-2.0.so`,
`libvirglrenderer.so`, `libepoxy.so`, `liblimbo.so`, `libSDL2.so` all live in the
**same** process. There is no `android:process` attribute anywhere in the tree and
nothing in `limbo/` forks, so QEMU — and therefore GTK — runs inside the app
process. `exit(1)` from GTK is fatal for the whole application.

## Root cause, in three layers

### 1. GDK's android backend was never initialised

`gtk/gtkmain.c:737` (GTK master):

```c
void gtk_init (void) {
  if (!gtk_init_check ())
    {
      g_warning ("Failed to open display");
      exit (1);
    }
}
```

`gtk_init_check()` returns FALSE when no display backend can be opened. The only
candidate in our build is the android backend:
`_gdk_android_display_open()` in `gdk/android/gdkandroiddisplay.c:335`:

```c
if (gdk_android_display != NULL)
  return NULL;
if (!gdk_android_get_env ()) {
  g_debug ("Unable to open Android display, as it was unprepared");
  return NULL;
}
```

`gdk_android_get_env()` (`gdk/android/gdkandroidinit.c:906`) returns NULL
**silently** when `gdk_android_vm == NULL`, and only emits
`g_critical("Unable to get env for the current thread. Is is attached?")` when the
VM is set but the calling thread is not attached. The device log has the WARNING
and no CRITICAL, so `gdk_android_vm` was NULL: `gdk_android_initialize()` had never
run successfully in that process.

It never ran because:

* `libqemu-system-x86_64.so` has `libgtk-4.so` in its `DT_NEEDED`, so the linker
  loads Gtk and runs its `JNI_OnLoad` before QEMU's first line executes.
* That `JNI_OnLoad` (`gdk/android/gdkandroidruntime.c:353`) only calls
  `gdk_android_initialize()` **if `org/gtk/android/RuntimeApplication` exists on
  the class path**; otherwise it logs
  `Did not find "org/gtk/android/RuntimeApplication" in class path, skipping runtime initialization.`
  and continues. We never shipped that class.
* Our own `LimboGtk.nativeInit()` does call `gdk_android_initialize()`, but it was
  invoked from `LimboGtkActivity.onCreate` with its boolean **discarded**, and the
  device log contains no `limbo-gtk` lines at all — it never got that far or
  never succeeded.

So QEMU called `gtk_init()`, GDK found no VM, the android display opener returned
NULL, and GTK called `exit(1)` — matching both the WARNING and `sig=6`.

### 2. The activity deadlocked on GDK's main loop before that

`GlibContext.runOnMain()` is backed by
`gdk/android/gdkandroidutils.c:40`:

```c
g_idle_add_full (G_PRIORITY_DEFAULT, ...);
```

An idle source only runs when some thread iterates the **default GMainContext**.
That loop lives inside QEMU's GTK main loop, which only starts after
`gtk_init()` succeeds. `ToplevelActivity.onCreate` used
`GlibContext.blockForMain(...)` — `runOnMain()` followed by an unbounded
`CountDownLatch.await()` — on the Android UI thread. On a first launch the
latch can never be counted down: the UI thread is wedged forever. That is the
"press start, it hangs" half of the report.

### 3. A second `exit(1)` in the same file

`ui/gtk.c` calls GTK twice: `qemu_display_early_init()` →
`early_gtk_display_init()`, then `qemu_display_init()` → `gtk_display_init()`.
Our port patch had replaced the guarded `gtk_init_check()` in the first one with
a bare `gtk_init()`, and the second one still contained the stock

```c
if (!gtkinit) {
    fprintf(stderr, "gtk initialization failed\n");
    exit(1);
}
```

Even with a working backend, any display failure would still kill the process.

## What was changed

Seven files.

### `limbo-android-lib/src/main/jni/patches/qemu-11.0.0-gtk4-android.patch`

Two hunks in `ui/gtk.c`.

`early_gtk_display_init` now keeps the guarded form, with GTK4's argument-less
prototype (`gtk_init_check()` takes no arguments since GTK4; the old
`gtk_init_check(NULL, NULL)` would not compile):

```c
gtkinit = gtk_init_check();
if (!gtkinit) {
    /* don't exit yet, that'll break -help */
    return;
}
```

`gtk_display_init` no longer exits:

```c
if (!gtkinit) {
    fprintf(stderr, "gtk initialization failed\n");
    return; /* Android: no exit(1), the Java side falls back to SDL */
}
```

Both hunks were re-counted; the patch still validates as `93 hunks, 0
mismatched`.

### `limbo-android-lib/src/main/java/com/max2idea/android/limbo/main/Config.java`

```java
public static boolean gtkInitFailed = false;

public static boolean isGtkUsable() {
    return enableGtk && engineHasGtk && !gtkInitFailed
            && android.os.Build.VERSION.SDK_INT >= 31;
}
```

`isGtkUsable()` previously meant "the build and the settings allow GTK". It now
also means "GDK was actually handed this process".

### `limbo-android-lib/src/main/java/com/max2idea/android/limbo/main/LimboGtk.java`

`init(Context)` records its outcome: `Config.gtkInitFailed = true` when the
bridge library is absent or the call throws, `Config.gtkInitFailed = !ok`
otherwise.

### `limbo-android-lib/src/main/java/com/max2idea/android/limbo/jni/VMExecutor.java`

```java
boolean gtkReady = Config.UI_GTK.equals(ui) && Config.isGtkUsable()
        && LimboGtk.isAvailable();
if (gtkReady) {
    paramsList.add("-display");
    paramsList.add(glGuest && Config.isVirglUsable() ? "gtk,gl=on" : "gtk");
}
```

Without this, a process whose GDK init failed would still hand QEMU
`-display gtk`; the patched QEMU would then survive the failure but the guest
would come up with no display at all.

### `limbo-android-lib/src/main/java/com/max2idea/android/limbo/main/LimboGtkActivity.java`

`LimboGtk.init(this)` must stay **before** `super.onCreate()` — `ToplevelActivity.onCreate()`
calls into GDK. What changed is that the result is now honoured:

```java
boolean gdkReady = LimboGtk.init(this);
ToplevelActivity.gdkUnavailable = !gdkReady;
super.onCreate(savedInstanceState);
if (!gdkReady) {
    startSdlFallback();
    return;
}
```

`startSdlFallback()` toasts `gtk_init_failed_sdl`, forwards the result to
`LimboSDLActivity` with `FLAG_ACTIVITY_FORWARD_RESULT` when there is a caller, and
finishes. Everything the user asked for still happens — just through SDL.

### `limbo-android-lib/src/main/java/org/gtk/android/ToplevelActivity.java`

* New `public static volatile boolean gdkUnavailable` plus a `gdkCallable()`
  guard used by `updateToplevelState()`, `onBackPressed()`,
  `onConfigurationChanged()`, `onDestroy()` and `onActivityResult()`. Every native
  in this class is registered by `gdk_android_initialize()`; calling one after a
  failure throws `UnsatisfiedLinkError`, and the SDL fallback's `finish()`
  reaches `onDestroy()`, so that was a guaranteed second crash.
* `onCreate` no longer calls `GlibContext.blockForMain(...)` unbounded. A restored
  surface identifier still rebinds through `runOnMain`, but the wait is bounded at
  two seconds. A first launch does not wait at all: it only calls
  `GdkContext._set_latest_activity(this)`.
* `GdkContext.activate()` / `open()` were removed from the path. They call
  `g_application_activate(g_application_get_default())`, and QEMU is not a
  `GApplication`, so that pointer is always NULL. The window is created the other
  way round: `gdk_android_toplevel_present()` sees an unbound cached activity
  (`native_identifier == 0`), logs `Claiming unbound activity`, calls
  `bindNative()` on it and skips spawning a stock `ToplevelActivity`.

### `limbo-android-lib/src/main/res/values/strings.xml`

`<string name="gtk_init_failed_sdl">"The GTK display could not start, switching this machine to SDL"</string>`

## What this does and does not do

It stops the process from being killed, stops the UI from wedging, and turns a
GTK failure into a working SDL session with a visible message. It does not by
itself make GTK work: that still needs `gdk_android_initialize()` to succeed
before QEMU's `gtk_init()` runs, which is what the `LimboGtk.init()` result and
the `LimboGtk.isAvailable()` gate now enforce instead of assume.

If GTK still fails on device, the log will now contain `limbo-gtk` lines
naming the failing step (`dlopen`, `dlsym`, or `gdk_android_initialize`), and the
machine will fall back to SDL rather than taking the app down.
