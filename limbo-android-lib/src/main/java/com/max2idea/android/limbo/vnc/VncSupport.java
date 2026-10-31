package com.max2idea.android.limbo.vnc;

import android.content.Context;
import android.util.Log;

import com.max2idea.android.limbo.main.LimboApplication;
import com.max2idea.android.limbo.main.LimboSettingsManager;
import com.max2idea.android.limbo.machine.ArchDefinitions;
import com.max2idea.android.limbo.qemu.QemuVersion;

/**
 * VNC capabilities of the QEMU build that is currently in use.
 *
 * <p>QEMU validates {@code -vnc ...,password=on} with
 * {@code qcrypto_cipher_supports(QCRYPTO_CIPHER_ALG_DES, ECB)} and, when the
 * build has no crypto backend, prints
 * <pre>
 *   libqemu-system-x86_64.so: -vnc :1,password: Cipher backend does not support DES algorithm
 * </pre>
 * and calls exit(1) before the VM ever starts. That is why VNC mode used to
 * "crash" on QEMU 10/11 while SDL (which never passes -vnc) kept working.
 *
 * <p>This project builds QEMU with {@code --disable-gnutls --disable-nettle}:
 * <ul>
 *   <li>5.1.0 still ships crypto/desrfb.c (built-in DES) -&gt; password works</li>
 *   <li>8.0.5 uses crypto/cipher-builtin.c.inc (AES only) -&gt; no DES</li>
 *   <li>10.x / 11.x use crypto/cipher-stub.c.inc (supports() == false)</li>
 * </ul>
 * so the password option must simply not be emitted for those builds.
 */
public final class VncSupport {
    private static final String TAG = "VncSupport";

    private VncSupport() {
    }

    /**
     * Whether the active QEMU build can actually do VNC password auth.
     *
     * <p>Uses the capability table of the active package when it declares one
     * (manifest {@code capabilities.vnc_password}), and otherwise falls back to
     * the QEMU version: only the 5.x line carries the built-in DES code in this
     * project's build configuration.
     */
    public static boolean canUsePassword(Context context) {
        try {
            QemuVersion.Capabilities caps = ArchDefinitions.getActiveCapabilities(context);
            if (caps != null) {
                return caps.supportsVncPassword();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not read the QEMU capability table: " + t);
        }
        int version = LimboApplication.getQemuVersion();
        boolean usable = version > 0 && version < 60000;
        if (!usable && version >= 80000) {
            Log.w(TAG, "VNC password is not supported by QEMU "
                    + LimboApplication.getQemuVersionString()
                    + " (no crypto backend in this build)");
        }
        return usable;
    }

    /**
     * The password to hand to the VNC server/viewer, or null when the user did
     * not enable one or the active build cannot use it.
     */
    public static String effectivePassword(Context context) {
        if (!LimboSettingsManager.getVNCEnablePassword(context)) {
            return null;
        }
        if (!canUsePassword(context)) {
            return null;
        }
        String password = LimboSettingsManager.getVNCPass(context);
        return password != null && password.length() > 0 ? password : null;
    }
}
