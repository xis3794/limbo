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
    "displays": ["VNC", "SDL"],
    "vnc_password": false
  }
}
```

### Why `capabilities` matters

A QEMU build only knows the machine models, CPU models and features of its own
release (`-M help` / `-cpu help` of that binary):

* QEMU 8 has no `pc-q35-11.1`, and QEMU 11 has no `pc-q35-5.1` either — 11.x
  removed every versioned machine type older than ~6 years / 18 releases.
* CPU models are additive: `DiamondRapids` only exists from 11.x, while
  `Icelake-Client` was dropped in 8.x.
* `vnc_password` depends on the crypto backend the build was linked with.

Without this block the app shows the static lists shipped in the APK, and
picking a value the running core does not have makes QEMU print
`unsupported machine type` / `unable to find CPU model` and call `exit(1)` —
the VM just does not start (and older builds crashed the whole app because
`exit()` runs the static destructors of the loaded libraries under the UI thread).

With the block:

* the machine-type / CPU / VGA / sound-card / NIC spinners are filled from it,
  so they only offer what the active build can do;
* `VMExecutor` still validates the stored machine type and CPU against it at
  start time and falls back to `pc` / `q35` / `max` when a machine was created
  with a different build;
* `-vnc ...,password=on` is only emitted when `vnc_password` is true, so a build
  without a crypto backend cannot break VNC mode (see below).

### `vnc_password`

QEMU validates the VNC password with
`qcrypto_cipher_supports(QCRYPTO_CIPHER_ALG_DES, ECB)`. When the crypto backend
does not provide DES, `-vnc :1,password=on` fails immediately with

```
libqemu-system-x86_64.so: -vnc :1,password: Cipher backend does not support DES algorithm
```

and `exit(1)` — the VM never starts, which looks like "VNC mode crashes".
This project builds QEMU with `--disable-gnutls --disable-nettle`, so:

| QEMU  | cipher backend                     | `vnc_password` |
|-------|------------------------------------|----------------|
| 5.1.0 | `crypto/desrfb.c` (built-in DES)   | `true`         |
| 8.0.5 | `cipher-builtin.c.inc` (AES only)  | `false`        |
| 10.x  | `cipher-stub.c.inc` (`supports()` false) | `false`  |
| 11.x  | `cipher-stub.c.inc` (`supports()` false) | `false`  |

The app reads the flag through `VncSupport.canUsePassword()`. Packages that do
not declare it are treated as `true` so existing behaviour does not change.

If a future build links nettle/gnutls, flip the flag to `true`; no app change is
needed.

### Generating the block

`tools/qemu_capabilities.json` holds the table that was extracted from the
upstream sources (`hw/i386/pc_piix.c`, `hw/i386/pc_q35.c`,
`target/i386/cpu.c`, `hw/i386/microvm.c`, `hw/core/null-machine.c`,
`crypto/*`) for every QEMU version the project builds. `package_qemu.py` reads
it automatically:

```sh
python3 tools/package_qemu.py <libs-dir> 8.0.5 out.tar.xz --series 8
```

Add a new version by adding an entry to `tools/qemu_capabilities.json`; keep
`machine_types` / `cpus` in the order QEMU lists them (newest first, `pc` and
`q35` first) and `version_code` = `major*10000 + minor*100 + patch`.

The bundled build gets the same table as the `qemu_capabilities.json` asset
(copied by `limbo-android-lib/src/main/jni/Makefile`, `COPY_CAPABILITIES`, and
by `scripts/...` in CI), so the built-in QEMU and installed packages behave
identically.
