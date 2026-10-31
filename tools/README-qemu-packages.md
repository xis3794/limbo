# QEMU version packages

The app can install QEMU builds that are not bundled with the APK
(Settings → Version manager). Each build is a `.tar.xz` containing

```
manifest.json
lib/libqemu-system-x86_64.so
lib/libglib-2.0.so   (and the other private dependencies)
```

`tools/package_qemu.py` produces such an archive.

## manifest.json

```json
{
  "id": "qemu-8.0.5-arm64-v8a",
  "version": "8.0.5",
  "series": "8",
  "display_name": "QEMU 8.0.5",
  "description": "QEMU 8.x series, the long-time stable choice for Windows 7/XP guests.",
  "abi": "arm64-v8a",
  "qemu_lib": "libqemu-system-x86_64.so",
  "load_order": ["libglib-2.0.so", "...", "libqemu-system-x86_64.so"],

  "capabilities": {
    "version_code": 80005,
    "machine_types": ["pc", "q35", "microvm", "isapc", "none", "pc-i440fx-8.0", "..."],
    "cpus": ["qemu64", "n270", "...", "max", "host"],
    "vga": ["std", "cirrus", "vmware", "nographic", "virtio-gpu-pci"],
    "soundcards": ["sb16", "ac97", "adlib", "cs4231a", "gus", "es1370", "hda", "pcspk", "all"],
    "nic_cards": ["e1000", "pcnet", "rtl8139", "ne2k_pci", "i82551", "i82557b", "i82559er", "virtio"],
    "displays": ["VNC", "SDL"]
  }
}
```

### Why `capabilities` matters

A QEMU build only knows the machine models and CPU models of its own release
(`-M help` / `-cpu help` of that binary):

* QEMU 8 has no `pc-q35-11.1`, and QEMU 11 has no `pc-q35-5.1` either — 11.x
  removed every versioned machine type older than ~6 years / 18 releases.
* CPU models are additive: `DiamondRapids` only exists from 11.x, `Icelake-Client`
  was dropped in 8.x.

Without this block the app shows the static lists shipped in the APK, and
picking a value the running core does not have makes QEMU print
`unsupported machine type` / `unable to find CPU model` and call `exit(1)` —
the VM just does not start (and older builds crashed the whole app because
`exit()` runs the static destructors of the loaded libraries under the UI thread).

With the block:

* the machine-type / CPU / VGA / sound-card / NIC spinners are filled from it,
  so they only offer what the active build can do;
* `VMExecutor` still validates the stored value at start time and falls back to
  `pc` / `q35` / `max` when a machine was created with a different build.

### Generating the block

`tools/qemu_capabilities.json` holds the table that was extracted from the
upstream sources (`hw/i386/pc_piix.c`, `hw/i386/pc_q35.c`,
`target/i386/cpu.c`, `hw/i386/microvm.c`, `hw/core/null-machine.c`) for every
QEMU version the project builds. `package_qemu.py` reads it automatically:

```sh
python3 tools/package_qemu.py <libs-dir> 8.0.5 out.tar.xz --series 8
```

Add a new version by adding an entry to `tools/qemu_capabilities.json`; keep
`machine_types` / `cpus` in the order QEMU lists them (newest first, `pc` and
`q35` first) and `version_code` = `major*10000 + minor*100 + patch`.

The bundled build gets the same table as the `qemu_capabilities.json` asset
(copied by `limbo-android-lib/src/main/jni/Makefile`, `COPY_CAPABILITIES`), so
the built-in QEMU and installed packages behave identically.
