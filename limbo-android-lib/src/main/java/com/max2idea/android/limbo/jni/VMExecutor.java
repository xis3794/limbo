/*
 Copyright (C) Max Kastanas 2012

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package com.max2idea.android.limbo.jni;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.files.FileUtils;
import com.max2idea.android.limbo.machine.MachineAction;
import com.max2idea.android.limbo.machine.MachineController;
import com.max2idea.android.limbo.machine.MachineExecutor;
import com.max2idea.android.limbo.machine.MachineProperty;
import com.max2idea.android.limbo.main.Config;
import com.max2idea.android.limbo.main.LimboApplication;
import com.max2idea.android.limbo.main.LimboSDLActivity;
import com.max2idea.android.limbo.main.LimboSettingsManager;
import com.max2idea.android.limbo.qmp.QmpClient;
import com.max2idea.android.limbo.toast.ToastUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;


/**
 * Class is used to start and stop the qemu process and communicate file descriptions, mouse,
 * and keyboard events.
 */
class VMExecutor extends MachineExecutor {
    private static final String TAG = "VMExecutor";

    private static final String cdDeviceName = "ide1-cd0";
    private static final String fdaDeviceName = "floppy0";
    private static final String fdbDeviceName = "floppy1";
    private static final String sdDeviceName = "sd0";
    private static int vm_width;
    private static int vm_height;
    //TODO: make this a proper singleton but the views should not be able to access it
    private static VMExecutor mInstance;

    VMExecutor(MachineController machineController) {
        super(machineController);
        mInstance = this;
    }

    /**
     * This function is called when the machine resolution changes. This is called from SDL compat
     * extensions, see folder jni/compat/sdl-extensions
     *
     * @param width  Width
     * @param height Height
     */
    public static void onVMResolutionChanged(int width, int height) {
        vm_width = width;
        vm_height = height;
        mInstance.onResolutionChanged(vm_width, vm_height);
    }

    //JNI Methods
    private native String start(String storage_dir, String base_dir,
                                String lib_filename, String lib_path,
                                int sdl_scale_hint, Object[] params);

    private native String stop(int restart);

    public native void setSDLRefreshRateDefault(int value);

    public native void setSDLRefreshRateIdle(int value);

    public native int getSDLRefreshRateDefault();

    public native int getSDLRefreshRateIdle();

    public native void nativeIgnoreBreakpointInvalidate(int value);

    public native void nativeMouseEvent(int button, int action, int relative, int x, int y);

    public native void nativeMouseBounds(int xmin, int xmax, int ymin, int ymax);

    public native void nativeFullscreen();

    public native void nativeRefreshScreen(int value);

    public native void nativeEnableAaudio(int value, String aaudioLibName, String aaudioLibPath);

    /**
     * Prints parameters in qemu format
     *
     * @param params Parameters to be printed
     */
    public void printParams(String[] params) {
        Log.d(TAG, "Params:");
        for (int i = 0; i < params.length; i++) {
            Log.d(TAG, i + ": " + params[i]);
        }
    }

    // Translate to QEMU format
    private String getSoundCard() {
        if (Config.enableSDLSound && getMachine().getSoundCard() != null
                && !getMachine().getSoundCard().toLowerCase().equals("none"))
            return getMachine().getSoundCard();
        return null;
    }

private String getQemuLibrary() {
        switch (LimboApplication.arch) {
            case x86:
                return "libqemu-system-i386.so";
            case x86_64:
                return "libqemu-system-x86_64.so";
            case arm:
                return "libqemu-system-arm.so";
            case arm64:
                return "libqemu-system-aarch64.so";
            case ppc:
                return "libqemu-system-ppc.so";
            case ppc64:
                return "libqemu-system-ppc64.so";
            case sparc:
                return "libqemu-system-sparc.so";
            case sparc64:
                return "libqemu-system-sparc64.so";
            default:
                throw new IllegalStateException("Unexpected value: " + LimboApplication.arch);
        }
    }

    private String getSaveStateName() {
        String machineSaveDirectory = MachineController.getInstance().getMachineSaveDir();
        return machineSaveDirectory + "/" + Config.stateFilename;
    }

    private String[] prepareParams(Context context) throws Exception {
        ArrayList<String> paramsList = new ArrayList<>();
        paramsList.add(getQemuLibrary());
        addUIOptions(context, paramsList);
        addCpuBoardOptions(paramsList);
        addDrives(paramsList);
        addRemovableDrives(paramsList);
        addBootOptions(paramsList);
        addGraphicsOptions(paramsList);
        addAudioOptions(paramsList);
        addNetworkOptions(paramsList);
        addGenericOptions(context, paramsList);
        addStateOptions(paramsList);
        addAdvancedOptions(paramsList);
        addAccelerationOptions(paramsList);
        return paramsList.toArray(new String[0]);
    }

    /**
     * Adds the vm state file description to the qemu parameters for resuming the vm
     *
     * @param paramsList Existing parameter list to be passed to qemu
     */
    private void addStateOptions(ArrayList<String> paramsList) {
        if (MachineController.getInstance().isPaused() && !getSaveStateName().equals("")) {
            int fd_tmp = FileUtils.get_fd(getSaveStateName());
            if (fd_tmp < 0) {
                Log.e(TAG, "Error while getting fd for: " + getSaveStateName());
            } else {
                Log.d(TAG, "Retrieved fd: " + fd_tmp + " for: " + getSaveStateName());
                paramsList.add("-incoming");
                paramsList.add("fd:" + fd_tmp);
            }
        }
    }

    private void addUIOptions(Context context, ArrayList<String> paramsList) {
        if (MachineController.getInstance().isVNCEnabled()) {
            paramsList.add("-vnc");
            String vncParam = "";
            if (LimboSettingsManager.getVNCEnablePassword(context)) {
                //TODO: Allow connections from External
                // Use with x509 auth and TLS for encryption
                vncParam += ":1";
            } else {
                // Allow connections only from localhost using localsocket without
                // a password
                vncParam += Config.defaultVNCHost + ":" + Config.defaultVNCPort;
            }
            if (LimboSettingsManager.getVNCEnablePassword(context))
                vncParam += ",password";

            paramsList.add(vncParam);

            //Allow monitor console though it's only supported for VNC, SDL for android doesn't support
            // more than 1 window
            paramsList.add("-monitor");
            paramsList.add("vc");

        } else {
            //XXX: monitor, serial, and parallel display crashes cause SDL doesn't support more than 1 window
            paramsList.add("-monitor");
            paramsList.add("none");

            paramsList.add("-serial");
            paramsList.add("none");

            paramsList.add("-parallel");
            paramsList.add("none");
        }

        if (getMachine().getKeyboard() != null) {
            paramsList.add("-k");
            paramsList.add(getMachine().getKeyboard());
        }

        // The mouse spinner shows "usb-tablet (Fixes Mouse)" / "usb-tablet（修正鼠标）"
        // i.e. the QEMU device name plus a localized hint. Only the first token is
        // a valid -device argument, otherwise QEMU fails with "invalid device".
        String mouseDevice = getMachine().getMouse();
        if (mouseDevice != null) {
            int hintStart = mouseDevice.indexOf(' ');
            if (hintStart < 0) {
                hintStart = mouseDevice.indexOf('(');
            }
            if (hintStart < 0) {
                hintStart = mouseDevice.indexOf('（');
            }
            if (hintStart > 0) {
                mouseDevice = mouseDevice.substring(0, hintStart).trim();
            }
        }
        if (mouseDevice != null && !mouseDevice.equals("ps2")) {
            paramsList.add("-usb");
            paramsList.add("-device");
            paramsList.add(mouseDevice);
        }

        // Boot firmware: when an EFI image is selected (Limbo Plus style),
        // boot the guest from OVMF / VMware EFI instead of SeaBIOS.
        // The firmware files are shipped in the APK assets and extracted to the
        // base file dir (the same directory that is passed to QEMU as -L).
        String firmware = getMachine().getFirmware();
        if (firmware != null && firmware.toUpperCase(java.util.Locale.ROOT).contains("EFI")) {
            String fwName;
            boolean vmware = firmware.toUpperCase(java.util.Locale.ROOT).contains("VMWARE");
            if (vmware) {
                fwName = "VMWARE_EFI.fd";
            } else if (LimboApplication.arch == Config.Arch.x86) {
                fwName = "OVMF-pure-efi.fd";
            } else if (LimboApplication.arch == Config.Arch.arm) {
                fwName = "OVMF-arm.fd";
            } else if (LimboApplication.arch == Config.Arch.arm64) {
                fwName = "OVMF-arm64.fd";
            } else {
                fwName = "OVMF-pure-efi64.fd";
            }
            java.io.File fw = new java.io.File(LimboApplication.getBasefileDir(), fwName);
            if (fw.exists()) {
                paramsList.add("-bios");
                paramsList.add(fw.getAbsolutePath());
                Log.d(TAG, "EFI firmware: " + fw.getAbsolutePath());
            } else {
                Log.e(TAG, "EFI firmware file not found: " + fw.getAbsolutePath());
            }
        }
    }

    private void addAdvancedOptions(ArrayList<String> paramsList) {

        if (getMachine().getExtraParams() != null && !getMachine().getExtraParams().trim().equals("")) {
            String[] paramsTmp = getMachine().getExtraParams().split(" ");
            paramsList.addAll(Arrays.asList(paramsTmp));
        }
    }

    private void addAudioOptions(ArrayList<String> paramsList) {
        // QEMU 6.0+ removed -soundhw; use -audiodev + -device instead
        String card = getSoundCard();
        if (card == null || card.equals("None"))
            return;
        // QEMU's "sdl" audio backend needs SDL's Java side (it calls back into
        // SDLAudioManager from its audio thread). If that bridge could not be
        // installed - which can only happen in VNC mode, see
        // LimboApplication.prepareSdlBridge() - fall back to the null backend
        // instead of letting QEMU's audio thread crash the whole process.
        String audioDriver = "sdl";
        if (MachineController.getInstance().isVNCEnabled()
                && !LimboApplication.isSdlBridgeReady()) {
            Log.w(TAG, "SDL bridge unavailable, using the null audio backend");
            audioDriver = "none";
        }
        paramsList.add("-audiodev");
        paramsList.add(audioDriver + ",id=snd0");
        if (card.equals("hda")) {
            paramsList.add("-device");
            paramsList.add("intel-hda");
            paramsList.add("-device");
            paramsList.add("hda-duplex,audiodev=snd0");
        } else if (card.equals("ac97")) {
            // QEMU device name for ac97 is "AC97"
            paramsList.add("-device");
            paramsList.add("AC97,audiodev=snd0");
        } else if (card.equals("all")) {
            // "all" has no direct equivalent; use es1370 as representative
            paramsList.add("-device");
            paramsList.add("es1370,audiodev=snd0");
        } else {
            paramsList.add("-device");
            paramsList.add(card + ",audiodev=snd0");
        }
    }

    private void addGenericOptions(Context context, ArrayList<String> paramsList) {
        paramsList.add("-L");
        paramsList.add(LimboApplication.getBasefileDir());
        if (LimboSettingsManager.getEnableQmp(context)) {
            paramsList.add("-qmp");
            if (getQMPAllowExternal()) {
                String qmpParams = "tcp:";
                qmpParams += (":" + Config.QMPPort);
                qmpParams += ",server,nowait";
                paramsList.add(qmpParams);
            } else {
                //Specify a unix local domain as localhost to limit to local connections only
                String qmpParams = "unix:";
                qmpParams += LimboApplication.getLocalQMPSocketPath();
                qmpParams += ",server,nowait";
                paramsList.add(qmpParams);
            }
        }

        //Enable Tracing log
        if (Config.enableTracingLog) {
            paramsList.add("-D");
            paramsList.add(Config.traceLogFile);
            paramsList.add("--trace");
            paramsList.add("events=" + Config.traceEventsFile);
            paramsList.add("--trace");
            paramsList.add("file=" + Config.traceDir);
        }
        if (Config.overrideTbSize && LimboApplication.getQemuVersion() < 80000) {
            // Legacy fallback for QEMU < 8.0, which still has the standalone
            // -tb-size option.
            //
            // DO NOT use this on QEMU >= 8.0! There the option was removed and
            // "tb-size" became a property of the tcg accelerator
            // (-accel tcg,tb-size=N, N in MiB). Feeding "-tb-size N" to
            // QEMU 8/11 makes it print "invalid option" and call exit(1). See
            // addAccelerationOptions() below, which emits the accel property.
            paramsList.add("-tb-size");
            paramsList.add(Config.tbSize); //Don't increase it crashes
        }


        if (LimboApplication.getQemuVersion() == 20901) {
            paramsList.add("-realtime");
            paramsList.add("mlock=off");
        } else {
            paramsList.add("-overcommit");
            paramsList.add("mem-lock=off");
        }

        paramsList.add("-rtc");
        paramsList.add("base=localtime");

        if (!Config.enableDefaultDevices)
            paramsList.add("-nodefaults");
    }

    private void addCpuBoardOptions(ArrayList<String> paramsList) {

        // Plus feature: force multicore when MTTCG is enabled (no need to
        // manually type "-smp n,cores=n")
        if (getMachine().getEnableMTTCG() != 0 && getMachine().getCpuNum() <= 1) {
            paramsList.add("-smp");
            paramsList.add("2");
        } else if (getMachine().getCpuNum() > 1) {
            paramsList.add("-smp");
            paramsList.add(getMachine().getCpuNum() + "");
        }
        if (getMachineType() != null && !getMachineType().equals("Default")) {
            paramsList.add("-M");
            paramsList.add(getMachineType());
        }

        //FIXME: something is wrong with quoting that doesn't let sparc qemu find the cpu def
        // for now we remove the cpu drop downlist items for sparc
        String cpu = getMachine().getCpu();
        if (getMachine().getCpu() != null && getMachine().getCpu().contains(" "))
            cpu = "'" + getMachine().getCpu() + "'"; // XXX: needed for sparc cpu names

        //XXX: we disable tsc feature for x86 since some guests are kernel panicking
        // if the cpu has not specified by user we use the internal qemu32/64
        if (getMachine().getDisableTSC() == 1 && (LimboApplication.arch == Config.Arch.x86 || LimboApplication.arch == Config.Arch.x86_64)) {
            if (cpu == null || cpu.equals("Default")) {
                if (LimboApplication.arch == Config.Arch.x86)
                    cpu = "qemu32";
                else if (LimboApplication.arch == Config.Arch.x86_64)
                    cpu = "qemu64";
            }
            cpu += ",-tsc";
        }

        // NOTE: for QEMU 9+ these are emitted as -M properties instead (see
        // getMachineType) because the standalone options were removed.
        if (LimboApplication.getQemuVersion() < 90000) {
            if (getMachine().getDisableAcpi() != 0) {
                paramsList.add("-no-acpi"); //disable ACPI
            }
            if (getMachine().getDisableHPET() != 0) {
                paramsList.add("-no-hpet"); //        disable HPET
            }
        }

        if (cpu != null && !cpu.equals("Default")) {
            // Plus feature: AVX enablement + use "max" CPU for best TCG
            // performance on x86_64 guests.
            //
            // NOTE: QEMU drops the AVX bits when TCG runs multi-threaded (Limbo
            // Plus hit the same limitation and renamed this option "ISA
            // optimization"), so only ask for them when MTTCG is off.
            if (LimboApplication.arch == Config.Arch.x86_64) {
                if (Config.enableAVX && getMachine().getEnableMTTCG() == 0) {
                    cpu += ",+avx,+xsave,+xsaveopt,+f16c";
                }
            }
            paramsList.add("-cpu");
            paramsList.add(cpu);
        } else if (LimboApplication.arch == Config.Arch.x86_64) {
            // Default CPU: use "max" (all TCG-supported features) for
            // significantly better performance (CPU-Z friendly)
            paramsList.add("-cpu");
            paramsList.add("max");
        }

        paramsList.add("-m");
        paramsList.add(getMachine().getMemory() + "");
    }


    private void addAccelerationOptions(ArrayList<String> paramsList) {

        // XXX: we add the acceleration options after the extra params
        // this is due to QEMU applying the first instance of this option
        // so the extra params cannot override it.
        if (getMachine().getEnableKVM() != 0) {
            paramsList.add("-enable-kvm");
        } else {
            paramsList.add("-accel");
            String tcgParams = "tcg";
            if (getMachine().getEnableMTTCG() != 0) {
                tcgParams += ",thread=multi";
            } else {
                tcgParams += ",thread=single";
            }
            // Performance: a bigger TCG translation-block cache means far fewer
            // retranslations once the guest is warm, which is the single biggest
            // TCG speed-up available on Android.
            String tbSize = getTbSizeParam();
            if (tbSize != null) {
                tcgParams += ",tb-size=" + tbSize;
            }
            paramsList.add(tcgParams);
        }
    }

    private static int sAutoTbSizeMiB = -1;

    /**
     * TB (translation block) cache size in MiB, or null to keep QEMU's own
     * default.
     *
     * QEMU 8.0 turned "tb-size" into a uint32 property of the tcg accelerator
     * (-accel tcg,tb-size=N) and removed the standalone -tb-size option, so
     * the value must be a plain number of MiB - a "256M" style suffix is
     * rejected ("invalid option" -> exit(1) -> the app would vanish silently).
     *
     * QEMU's default is MIN(1 GiB, guest_ram / 8), i.e.128 MiB for the usual
     * 1 GiB guest. The buffer is only committed as translated code is written
     * into it, so when the phone still has plenty of free RAM we can hand TCG
     * a somewhat larger cache and win real speed.
     */
    private String getTbSizeParam() {

        if (Config.overrideTbSize) {
            String v = normalizeTbSize(Config.tbSize);
            if (v != null) {
                return v;
            }
        }
        if (LimboApplication.getQemuVersion() < 80000) {
            return null;
        }
        int auto = autoTbSizeMiB();
        return auto > 0 ? ("" + auto) : null;
    }

    /** "32M" / "1G" / "256" -> "32" / "1024" / "256" (MiB, no suffix). */
    private static String normalizeTbSize(String raw) {

        if (raw == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^\\s*(\\d+)\\s*([a-zA-Z]*)\\s*$").matcher(raw);
        if (!m.matches()) {
            return null;
        }
        long value = Long.parseLong(m.group(1));
        String unit = m.group(2).toLowerCase(java.util.Locale.ROOT);
        if (unit.startsWith("g")) {
            value = value * 1024;
        } else if (unit.length() > 0 && !unit.startsWith("m")) {
            return null; // unknown unit (e.g. "K"): QEMU would reject it
        }
        if (value < 1 || value > 1024) {
            return null;
        }
        return "" + value;
    }

    /**
     * Pick the TB cache size from the memory the device actually has free.
     * Reading MemAvailable from /proc/meminfo needs no permission and is far
     * more reliable than asking ActivityManager from a non-UI thread.
     */
    private static int autoTbSizeMiB() {

        if (sAutoTbSizeMiB >= 0) {
            return sAutoTbSizeMiB;
        }
        sAutoTbSizeMiB = 0;
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/meminfo"));
            String line;
            long availKb = 0;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemAvailable:")) {
                    availKb = Long.parseLong(line.replaceAll("[^0-9]", ""));
                    break;
                }
            }
            br.close();
            long availMiB = availKb / 1024;
            // QEMU's own default is MIN(1 GiB, guest_ram / 8), i.e.128 MiB for
            // the usual1 GiB guest. Limbo Plus simply pins the TCG cache to
            // 4 GiB; because the code buffer is only committed as translated
            // code is written into it, a much larger cache mainly means far
            // fewer retranslations (the biggest single TCG speed-up). Size it
            // from the memory the phone can actually spare.
            if (availMiB >= 4096) {
                sAutoTbSizeMiB = 1024;
            } else if (availMiB >= 2048) {
                sAutoTbSizeMiB = 512;
            } else if (availMiB >= 1024) {
                sAutoTbSizeMiB = 256;
            }
            Log.d(TAG, "Auto TB cache: " + sAutoTbSizeMiB + " MiB (free " + availMiB + " MiB)");
        } catch (Throwable t) {
            sAutoTbSizeMiB = 0;
            Log.w(TAG, "Could not read /proc/meminfo: " + t);
        }
        return sAutoTbSizeMiB;
    }

    private String getMachineType() {
        String machineType = getMachine().getMachineType();
        // "Default" is a UI placeholder, not a QEMU machine name: passing it
        // through makes QEMU fail with 'unsupported machine type: "Default"'.
        // Drop it and let QEMU (or the x86 default below) decide.
        if (machineType != null && machineType.equalsIgnoreCase("Default")) {
            machineType = null;
        }
        if ((LimboApplication.arch == Config.Arch.x86 || LimboApplication.arch == Config.Arch.x86_64)
                && machineType == null) {
            machineType = "pc";
        }
        // QEMU 9/10/11 dropped most of the old versioned machine aliases
        // (the picker still offers names like "pc-q35-5.0"). Passing one makes
        // QEMU print
        //   libqemu-system-x86_64.so: unsupported machine type: "pc-q35-5.0"
        // and call exit(1). That exit runs __cxa_finalize, i.e. the static
        // destructors of every loaded library, which destroy the global mutexes
        // of libhwui.so / libgrallocutils.so while the UI thread is still
        // rendering -> "FORTIFY: pthread_mutex_lock called on a destroyed
        // mutex" -> the app aborts. Map such names onto the unversioned alias.
        if (machineType != null && LimboApplication.getQemuVersion() >= 90000) {
            machineType = normalizeVersionedMachineType(machineType);
        }
        // QEMU 9+ also removed the standalone -no-acpi / -no-hpet options
        // (same class of breakage as the machine aliases above: QEMU would
        // print "invalid option" and exit(1)). They are machine properties
        // now, so fold them into the -M argument here.
        if (machineType != null && LimboApplication.getQemuVersion() >= 90000) {
            StringBuilder sb = new StringBuilder(machineType);
            if (getMachine().getDisableAcpi() != 0) {
                sb.append(",acpi=off");
            }
            if (getMachine().getDisableHPET() != 0) {
                sb.append(",hpet=off");
            }
            machineType = sb.toString();
        }
        return machineType;
    }

    /**
     * Turn a versioned QEMU machine name into the unversioned alias that
     * current QEMU releases still provide:
     *   pc-q35-5.0     -> q35
     *   pc-i440fx-5.0  -> pc
     *   pc-1.0 / pc-2.9-> pc
     * Unversioned names (q35, pc, microvm, isapc, none, virt, ...) are kept.
     */
    private static String normalizeVersionedMachineType(String mt) {
        if (mt == null) {
            return null;
        }
        // strip a trailing "-<major>.<minor>" (or "-<major>) version suffix
        String base = mt.replaceAll("-\\d+(\\.\\d+)?$", "");
        if (base.equals("pc-q35") || base.equals("q35")) {
            return "q35";
        }
        if (base.equals("pc-i440fx") || base.equals("pc")) {
            return "pc";
        }
        if (base.startsWith("virt") || base.equals("none") || base.equals("microvm")
                || base.equals("isapc")) {
            return base;
        }
        return base;
    }

    private void addNetworkOptions(ArrayList<String> paramsList) throws Exception {

        String network = getNetCfg();
        if (network != null) {
            paramsList.add("-net");
            if (network.equals("user")) {
                String netParams = network;
                String hostFwd = getHostFwd();
                if (hostFwd != null) {

                    //hostfwd=[tcp|udp]:[hostaddr]:hostport-[guestaddr]:guestport{,hostfwd=...}
                    // example forward ssh from guest port 2222 to guest port 22:
                    // hostfwd=tcp::2222-:22
                    if (hostFwd.startsWith("hostfwd")) {
                        throw new Exception("Invalid format for Host Forward, should be: tcp:hostport1:guestport1,udp:hostport2:questport2,...");
                    }
                    String[] hostFwdParams = hostFwd.split(",");
                    for (int i = 0; i < hostFwdParams.length; i++) {
                        netParams += ",";
                        String[] hostfwdparam = hostFwdParams[i].split(":");
                        netParams += ("hostfwd=" + hostfwdparam[0] + "::" + hostfwdparam[1] + "-:" + hostfwdparam[2]);
                    }
                }
                paramsList.add(netParams);
            } else if (network.equals("tap")) {
                paramsList.add("tap,vlan=0,ifname=tap0,script=no");
            } else if (network.equals("none")) {
                paramsList.add("none");
            } else {
                //Unknown interface
                paramsList.add("none");
            }
        }

        String networkCard = getNicCard();
        if (networkCard != null) {
            paramsList.add("-net");
            String nicParams = "nic";
            if (network.equals("tap"))
                nicParams += ",vlan=0";
            if (!networkCard.equals("Default"))
                nicParams += (",model=" + networkCard);
            paramsList.add(nicParams);
        }
    }

    private String getHostFwd() {
        if (getMachine().getNetwork().equals("User")) {
            if (getMachine().getHostFwd() != null && !getMachine().getHostFwd().equals(""))
                return getMachine().getHostFwd();
        }
        return null;
    }

    private String getNicCard() {
        if (getMachine().getNetwork() == null || getMachine().getNetwork().equals("None")) {
            return null;
        } else if (getMachine().getNetwork().equals("User")) {
            return getMachine().getNetworkCard();
        } else if (getMachine().getNetwork().equals("TAP")) {
            return getMachine().getNetworkCard();
        }
        return null;
    }

    private String getNetCfg() {
        if (getMachine().getNetwork() == null || getMachine().getNetwork().equals("None")) {
            return "none";
        } else if (getMachine().getNetwork().equals("User")) {
            return "user";
        } else if (getMachine().getNetwork().equals("TAP")) {
            return "tap";
        }
        return null;
    }

    private void addGraphicsOptions(ArrayList<String> paramsList) {
        if (getMachine().getVga() != null) {
            if (getMachine().getVga().equals("Default")) {
                //do nothing
            } else if (getMachine().getVga().equals("virtio-gpu-pci")) {
                paramsList.add("-device");
                paramsList.add(getMachine().getVga());
            } else if (getMachine().getVga().equals("nographic")) {
                paramsList.add("-nographic");
            } else {
                paramsList.add("-vga");
                paramsList.add(getMachine().getVga());
            }
        }
    }

    private void addBootOptions(ArrayList<String> paramsList) {
        if (getBootDevice() != null) {
            paramsList.add("-boot");
            paramsList.add(getBootDevice());
        }

        String kernel = getKernel();
        if (kernel != null && !kernel.equals("")) {
            paramsList.add("-kernel");
            paramsList.add(kernel);
        }

        String initrd = getInitRd();
        if (initrd != null && !initrd.equals("")) {
            paramsList.add("-initrd");
            paramsList.add(initrd);
        }

        if (getMachine().getAppend() != null && !getMachine().getAppend().equals("")) {
            paramsList.add("-append");
            paramsList.add(getMachine().getAppend());
        }
    }

    private String getBootDevice() {
        if (LimboApplication.arch == Config.Arch.arm || LimboApplication.arch == Config.Arch.arm64) {
            return null;
        } else if (getMachine().getBootDevice().equals("Default")) {
            return null;
        } else if (getMachine().getBootDevice().equals("CDROM")) {
            return "d";
        } else if (getMachine().getBootDevice().equals("Floppy")) {
            return "a";
        } else if (getMachine().getBootDevice().equals("Hard Disk")) {
            return "c";
        }
        return null;
    }

    private String getInitRd() {
        return FileUtils.encodeDocumentFilePath(getMachine().getInitRd());
    }

    private String getKernel() {
        return FileUtils.encodeDocumentFilePath(getMachine().getKernel());
    }

    public String getDriveFilePath(String driveFilePath) {
        String imgPath = driveFilePath;
        if (imgPath == null || imgPath.equals("None"))
            return null;
        imgPath = FileUtils.encodeDocumentFilePath(imgPath);
        return imgPath;
    }

    public void addDrives(ArrayList<String> paramsList) {
        addHardDisk(paramsList, getDriveFilePath(getMachine().getHdaImagePath()),
                0, getMachine().getHdaInterface());
        addHardDisk(paramsList, getDriveFilePath(getMachine().getHdbImagePath()),
                1, getMachine().getHdbInterface());
        addHardDisk(paramsList, getDriveFilePath(getMachine().getHdcImagePath()),
                2, getMachine().getHdcInterface());
        addHardDisk(paramsList, getDriveFilePath(getMachine().getHddImagePath()),
                3, getMachine().getHddInterface());
        addSharedFolder(paramsList, getDriveFilePath(getMachine().getSharedFolderPath()));
    }

    public void addHardDisk(ArrayList<String> paramsList, String imagePath, int index, String hdInterface) {
        if (imagePath != null && !imagePath.trim().equals("")) {
            if (Config.legacyDrives) {
                switch (index) {
                    case 0:
                        paramsList.add("-hda");
                        break;
                    case 1:
                        paramsList.add("-hdb");
                        break;
                    case 2:
                        paramsList.add("-hdc");
                        break;
                    case 3:
                        paramsList.add("-hdd");
                        break;
                }
                paramsList.add(imagePath);
            } else {
                paramsList.add("-drive");
                String param = "index=" + index;
                param += ",if=";
                param += hdInterface;
                param += ",media=disk";
                if (!imagePath.equals("")) {
                    param += ",file=" + imagePath;
                }
                String cache = LimboSettingsManager.getDiskCache(LimboApplication.getInstance());
                if(cache != null && !cache.equals("default"))
                    param += ",cache=" + cache;
                paramsList.add(param);
            }
        }
    }

    public void addSharedFolder(ArrayList<String> paramsList, String sharedFolderPath) {
        if (Config.enableSharedFolder && sharedFolderPath != null) {
            //XXX; We use hdd to mount any virtual fat drives
            paramsList.add("-drive"); //empty
            String driveParams = "index=3";
            driveParams += ",media=disk";
            driveParams += ",if=ide";
            driveParams += ",format=raw";
            driveParams += ",file=fat:";
            driveParams += "rw:"; //Always Read/Write
            driveParams += sharedFolderPath;
            paramsList.add(driveParams);
        }

    }

    public void addRemovableDrives(ArrayList<String> paramsList) {
        String cdImagePath = getDriveFilePath(getMachine().getCdImagePath());
        if (cdImagePath != null) {
            if (Config.legacyDrives) {
                paramsList.add("-cdrom");
                paramsList.add(cdImagePath);
            } else {
                paramsList.add("-drive"); //empty
                String param = "index=2";
                param += ",if=";
                param += getMachine().getCDInterface();
                param += ",media=cdrom";
                if (!cdImagePath.equals("")) {
                    param += ",file=" + cdImagePath;
                }
                paramsList.add(param);
            }
        }

        String fdaImagePath = getDriveFilePath(getMachine().getFdaImagePath());
        if (Config.enableEmulatedFloppy && fdaImagePath != null) {
            if (Config.legacyDrives) {
                paramsList.add("-fda");
                paramsList.add(fdaImagePath);
            } else {
                paramsList.add("-drive"); //empty
                String param = "index=0,if=floppy";
                if (!fdaImagePath.equals("")) {
                    param += ",file=" + fdaImagePath;
                }
                paramsList.add(param);
            }
        }

        String fdbImagePath = getDriveFilePath(getMachine().getFdbImagePath());
        if (Config.enableEmulatedFloppy && fdbImagePath != null) {
            if (Config.legacyDrives) {
                paramsList.add("-fdb");
                paramsList.add(fdbImagePath);
            } else {
                paramsList.add("-drive"); //empty
                String param = "index=1,if=floppy";
                if (!fdbImagePath.equals("")) {
                    param += ",file=" + fdbImagePath;
                }
                paramsList.add(param);
            }
        }

        String sdImagePath = getDriveFilePath(getMachine().getSdImagePath());
        if (Config.enableEmulatedSDCard && sdImagePath != null) {
            if (Config.legacyDrives) {
                paramsList.add("-sd");
                paramsList.add(sdImagePath);
            } else {
                paramsList.add("-device");
                paramsList.add("sd-card,drive=sd0,bus=sd-bus");
                paramsList.add("-drive");
                String param = "if=none,id=sd0";
                if (!sdImagePath.equals("")) {
                    param += ",file=" + sdImagePath;
                }
                paramsList.add(param);
            }
        }

    }


    /**
     * change the vnc password before we connect
     * The user is also prompted to create a certificate
     *
     * @param vncPassword The VNC password to be send to QEMU
     */
    protected void vncchangepassword(String vncPassword) throws Exception {
        String res = QmpClient.sendCommand(QmpClient.getChangeVncPasswdCommand(vncPassword));
        String desc = null;
        if (res != null && !res.equals("")) {
            JSONObject resObj = new JSONObject(res);
            if (resObj != null && !resObj.equals("") && res.contains("error")) {
                String resInfo = resObj.getString("error");
                if (resInfo != null && !resInfo.equals("")) {
                    JSONObject resInfoObj = new JSONObject(resInfo);
                    desc = resInfoObj.getString("desc");
                    Log.e(TAG, desc);
                }
            }
        }
    }

    protected String changedev(String dev, String value) {
        String response = QmpClient.sendCommand(QmpClient.getChangeDeviceCommand(dev, value));
        String displayDevValue = FileUtils.getFullPathFromDocumentFilePath(value);
        if (Config.debug)
            ToastUtils.toastLong(LimboApplication.getInstance(), Gravity.BOTTOM,
                    LimboApplication.getInstance().getString(R.string.ChangedDevice) + ": "
                            + dev + ": " + displayDevValue);
        return response;
    }

    protected String ejectdev(String dev) {
        String response = QmpClient.sendCommand(QmpClient.getEjectDeviceCommand(dev));
        if (Config.debug)
            ToastUtils.toastLong(LimboApplication.getInstance(), Gravity.BOTTOM,
                    LimboApplication.getInstance().getString(R.string.EjectedDevice) + ": " + dev);
        return response;
    }


    /**
     * Starts the service that will later start the qemu process
     */
    public void startService() {
        Intent i = new Intent(Config.ACTION_START, null, LimboApplication.getInstance(),
                MachineController.getInstance().getServiceClass());
        Bundle b = new Bundle();
        i.putExtras(b);
        Log.d(TAG, "Starting VM service");
        LimboApplication.getInstance().startService(i);
    }

    /**
     * Starts the native process. This should be called from a background thread from a
     * foreground service in order to prevent the process from being killed
     *
     * @return String from the native code vm-executor-jni.cpp
     */
    public String start() {
        String res = null;
        try {
            String[] params = prepareParams(LimboApplication.getInstance());
            printParams(params);
            // XXX: for VNC we need to resume manually after a reasonable amount of time
            if (getMachine().getPaused() == 1 && MachineController.getInstance().isVNCEnabled()) {
                continueVM(5000);
            }

            if (MachineController.getInstance().isVNCEnabled() && LimboSettingsManager.getVNCEnablePassword(LimboApplication.getInstance())) {
                changeVncPass(LimboApplication.getInstance(), 2000);
            }

            ignoreBreakpointInvalidation(LimboSettingsManager.getIgnoreBreakpointInvalidation(LimboApplication.getInstance())?1:0, 2000);
            QmpClient.setExternal(LimboSettingsManager.getEnableExternalQMP(LimboApplication.getInstance()));
            String libFilename = getQemuLibrary();
            res = start(Config.storagedir, LimboApplication.getBasefileDir(),
                    libFilename, FileUtils.getNativeLibDir(LimboApplication.getInstance()) + "/" + libFilename,
                    Config.SDLHintScale, params);
        } catch (Exception ex) {
            ToastUtils.toastLong(LimboApplication.getInstance(), ex.getMessage());
            return res;
        }
        return res;
    }

    private void changeVncPass(final Context context, final long delay) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                try {
                    vncchangepassword(LimboSettingsManager.getVNCPass(context));
                } catch (Exception e) {
                    ToastUtils.toastLong(LimboApplication.getInstance(),
                            context.getString(R.string.CouldNotSetVNCPass) + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void continueVM(final int delay) {
        // TODO: We shouldn't have to go through the view dispatcher
        LimboApplication.getViewListener().onAction(MachineAction.CONTINUE_VM, delay);
    }

    public void stopvm(final int restart) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (restart != 0) {
                    QmpClient.sendCommand(QmpClient.getResetCommand());
                } else {
                    //XXX: Qmp command only halts the VM but doesn't exit so we use force close
//            QmpClient.sendCommand(QmpClient.powerDown());
                    stop(restart);
                }
            }
        }).start();
    }

    @Override
    public int getSdlRefreshRate(boolean idle) {
        if (idle)
            return getSDLRefreshRateIdle();
        else
            return getSDLRefreshRateDefault();
    }

    @Override
    public void setSdlRefreshRate(int value, boolean idle) {
        if (idle)
            setSDLRefreshRateIdle(value);
        else
            setSDLRefreshRateDefault(value);
    }

    @Override
    public String getDeviceName(MachineProperty driveProperty) {
        switch (driveProperty) {
            case CDROM:
                return cdDeviceName;
            case FDA:
                return fdaDeviceName;
            case FDB:
                return fdbDeviceName;
            case SD:
                return sdDeviceName;
        }
        return null;
    }

    @Override
    public synchronized void updateDisplay(int width, int height, int orientation) {
        if (!LimboSettingsManager.getPreventMouseOutOfBounds(LimboApplication.getInstance())) {
            return;
        }
        String mouse = getMachine().getMouse();
        // If we use absolute pointer devices in the guest os (usb-tablet) we need to prevent
        // the mouse from going out of bounds. This case happens when we use trackpad and when the
        // guest display doesn't fit inside the Android Surface which is pretty much all the time.
        // we could use SurfaceHolder.setFixedSize() to bound the surfaceview but it creates
        // problems with refreshing the surfaceview plus we would still need this fix for trackpad
        if (mouse != null && mouse.equals("usb-tablet")) {
            int xmin = 0;
            int xmax = width;
            int ymin = 0;
            int ymax = height;
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                ymin = (int) (height - width * vm_height / (float) vm_width) / 2;
                ymax = (int) (height + width * vm_height / (float) vm_width) / 2;
            } else {
                xmin = (int) (width - height * vm_width / (float) vm_height) / 2;
                xmax = (int) (width + height * vm_width / (float) vm_height) / 2;
            }
            nativeMouseBounds(xmin, xmax, ymin, ymax);
        }
    }

    @Override
    public void setFullscreen() {
        nativeFullscreen();
        //TODO: sparc doesn't not have vga so we need to
        // see if we can apply similar call to the cg3
        if(LimboApplication.arch == Config.Arch.x86
                || LimboApplication.arch == Config.Arch.x86_64
                || LimboApplication.arch == Config.Arch.arm
                || LimboApplication.arch == Config.Arch.arm64
                || LimboApplication.arch == Config.Arch.ppc
                || LimboApplication.arch == Config.Arch.ppc64
        ) {
            nativeRefreshScreen(1);
        }
    }

    @Override
    public void enableAaudio(int value) {
        nativeEnableAaudio(value, Config.aaudioLibName,
                FileUtils.getNativeLibDir(LimboApplication.getInstance())
                        + "/" + Config.aaudioLibName);
    }

    @Override
    public void ignoreBreakpointInvalidation(int value){
        ignoreBreakpointInvalidation(value, 0);
    }

    private void ignoreBreakpointInvalidation(final int value, final long delay) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                nativeIgnoreBreakpointInvalidate(value);
            }
        }).start();
    }

    //TODO: re-enable getting status from the vm
    public String getVmState() {
        String res = QmpClient.sendCommand(QmpClient.getStateCommand());
        String state = "";
        if (res != null && !res.equals("")) {
            try {
                JSONObject resObj = new JSONObject(res);
                String resInfo = resObj.getString("return");
                JSONObject resInfoObj = new JSONObject(resInfo);
                state = resInfoObj.getString("status");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return state;
    }

    /**
     * Function sends a command via qmp to change or eject the removable device
     *
     * @param drive     The device to be changed
     * @param imagePath If its null it ejects the drive otherwise it uses the disk file at that path
     */
    public boolean changeRemovableDevice(final MachineProperty drive, final String imagePath) {
        if (!LimboSettingsManager.getEnableQmp(LimboApplication.getInstance())) {
            ToastUtils.toastShort(LimboApplication.getInstance(), LimboApplication.getInstance().getString(R.string.EnableQMPForChangingDrives));
            return false;
        }
        String dev = getDeviceName(drive);

        //XXX: first we eject any previous media
        String response = VMExecutor.this.ejectdev(dev);

        // if there is no media there is nothing else to do
        if (imagePath == null || imagePath.trim().equals("")) {
            return true;
        }

        //XXX: we encode some characters from the document file path so it's processed
        // correctly by qemu
        String imagePathConverted = FileUtils.encodeDocumentFilePath(imagePath);

        if (!FileUtils.fileValid(imagePathConverted)) {
            String msg = LimboApplication.getInstance().getString(R.string.CouldNotOpenDocFile) + " "
                    + FileUtils.getFullPathFromDocumentFilePath(imagePathConverted)
                    + "\n" + LimboApplication.getInstance().getString(R.string.PleaseReassingYourDiskFiles);
            ToastUtils.toastLong(LimboApplication.getInstance(), msg);
            return false;
        }
        response = VMExecutor.this.changedev(dev, imagePathConverted);
        if (response == null)
            return false;

        return true;
    }

    /**
     * Fuction is a pass thru from the c get_fd() function called from native code
     * This is bridged to the java code because it's the only way to open a file descriptor
     * from the native code
     *
     * @param path File path
     * @return Return value of FileUtils.get_fd()
     */
    public int get_fd(String path) {
        return FileUtils.get_fd(path);
    }

    /**
     * Fuction is a pass thru from the c close_fd() function called from native code
     * This is similar to the above get_fd but perhaps not needed.
     *
     * @param fd File Descriptor to be closed
     * @return Return value of FileUtils.close_fd()
     */
    public int close_fd(int fd) {
        return FileUtils.close_fd(fd);
    }

    @Override
    public String saveVM() {

        // Delete any previous state file
        File file = new File(getSaveStateName());
        if (file.exists()) {
            if (!file.delete()) {
                return LimboApplication.getInstance().getString(R.string.CannotDeletePreviousStateFile);
            }
        }

        if (Config.showToast)
            ToastUtils.toastShort(LimboApplication.getInstance(), LimboApplication.getInstance().getString(R.string.PleaseWaitSavingVMState));

        int currentFd = get_fd(getSaveStateName());
        String uri = "fd:" + currentFd;
        String command = QmpClient.getStopVMCommand();
        String msg = QmpClient.sendCommand(command);
        command = QmpClient.getMigrateCommand(false, false, uri);
        msg = QmpClient.sendCommand(command);
        if (msg != null) {
            return processMigrationResponse(msg);
        }
        return null;
    }

    @Override
    public void continueVM() {
        String command = QmpClient.getContinueVMCommand();
        QmpClient.sendCommand(command);
    }

    @Override
    public MachineController.MachineStatus getSaveVMStatus() {
        String pauseState = "";
        String command = QmpClient.getQueryMigrationCommand();
        String res = QmpClient.sendCommand(command);

        if (res != null && !res.equals("")) {
            try {
                JSONObject resObj = new JSONObject(res);
                String resInfo = resObj.getString("return");
                JSONObject resInfoObj = new JSONObject(resInfo);
                pauseState = resInfoObj.getString("status");
            } catch (JSONException e) {
                if (Config.debug)
                    Log.e(TAG, "Error while checking saving vm: " + e.getMessage());
            }
            if (pauseState.toUpperCase().equals("FAILED")) {
                Log.e(TAG, "Error: " + res);
            }
        }
        if (pauseState.toUpperCase().equals("ACTIVE")) {
            return MachineController.MachineStatus.Saving;
        } else if (pauseState.toUpperCase().equals("COMPLETED")) {
            return MachineController.MachineStatus.SaveCompleted;
        } else if (pauseState.toUpperCase().equals("FAILED")) {
            return MachineController.MachineStatus.SaveFailed;
        }
        //TODO: proper error handling with user messages
        return MachineController.MachineStatus.Unknown;
    }

    private String processMigrationResponse(String response) {
        String errorStr = null;
        try {
            JSONObject object = new JSONObject(response);
            errorStr = object.getString("error");
        } catch (Exception ex) {
            if (Config.debug)
                ex.printStackTrace();
        }
        if (errorStr != null) {
            String descStr = null;

            try {
                JSONObject descObj = new JSONObject(errorStr);
                descStr = descObj.getString("desc");
            } catch (Exception ex) {
                if (Config.debug)
                    ex.printStackTrace();
            }
            return descStr;
        }
        return null;
    }

    public void sendMouseEvent(int button, int action, int relative, float x, float y) {
        //XXX: Make sure that mouse motion is not triggering crashes in SDL while resizing
        if (LimboSDLActivity.isResizing) {
            return;
        }

        nativeMouseEvent(button, action, relative, (int) x, (int) y);
    }

    public boolean getQMPAllowExternal() {
        return LimboSettingsManager.getEnableExternalQMP(LimboApplication.getInstance());
    }
}

