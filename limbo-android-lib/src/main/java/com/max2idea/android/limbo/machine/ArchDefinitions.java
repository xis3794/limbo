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
package com.max2idea.android.limbo.machine;

import android.content.Context;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.install.Installer;
import com.max2idea.android.limbo.main.Config;
import com.max2idea.android.limbo.main.LimboApplication;
import com.max2idea.android.limbo.qemu.QemuVersion;
import com.max2idea.android.limbo.qemu.QemuVersionManager;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A simple utility class to retrieved often long architecture attribute lists
 */
public class ArchDefinitions {
    private static String TAG = "ArchDefinitions";

    public static ArrayList<String> getSoundcards(Context context) {
        ArrayList<String> commonSoundcards = new ArrayList<>();
        QemuVersion.Capabilities caps = getActiveCapabilities(context);
        if (caps != null && !caps.soundcards.isEmpty()) {
            commonSoundcards.addAll(caps.soundcards);
            return commonSoundcards;
        }
        commonSoundcards.addAll(Arrays.asList(Installer.getAttrs(context, R.raw.common_soundcards)));
        return commonSoundcards;
    }

    public static ArrayList<String> getNetworkDevices(Context context) {
        ArrayList<String> commonNetworkCards = new ArrayList<>();
        QemuVersion.Capabilities caps = getActiveCapabilities(context);
        if (caps != null && !caps.nicCards.isEmpty()) {
            commonNetworkCards.addAll(caps.nicCards);
        } else {
            commonNetworkCards.addAll(Arrays.asList(Installer.getAttrs(context, R.raw.common_nic_cards)));
        }

        ArrayList<String> networkCards = new ArrayList<>();
        switch (LimboApplication.arch) {
            case x86:
            case x86_64:
                networkCards.add("Default");
                networkCards.addAll(commonNetworkCards);
                break;
            case arm:
            case arm64:
                networkCards.add("Default");
                networkCards.addAll(commonNetworkCards);
                networkCards.addAll(Arrays.asList(Installer.getAttrs(context, R.raw.arm_nic_cards)));
                break;
            case ppc:
            case ppc64:
                networkCards.add("Default");
                networkCards.addAll(commonNetworkCards);
                break;
            case sparc:
            case sparc64:
                networkCards.add("Default");
                networkCards.addAll(Arrays.asList(Installer.getAttrs(context, R.raw.sparc_nic_cards)));
                break;
        }
        return networkCards;
    }

    public static ArrayList<String> getVGAValues(Context context) {
        ArrayList<String> vgaValues = new ArrayList<>();
        if (LimboApplication.arch == Config.Arch.x86 || LimboApplication.arch == Config.Arch.x86_64
                || LimboApplication.arch == Config.Arch.arm || LimboApplication.arch == Config.Arch.arm64
                || LimboApplication.arch == Config.Arch.ppc || LimboApplication.arch == Config.Arch.ppc64) {
            vgaValues.add("std");
        }

        if (LimboApplication.arch == Config.Arch.x86 || LimboApplication.arch == Config.Arch.x86_64) {
            vgaValues.add("cirrus");
            vgaValues.add("vmware");
        }

        //override vga for sun4m (32bit) machines to cg3
        if (LimboApplication.arch == Config.Arch.sparc || LimboApplication.arch == Config.Arch.sparc64) {
            vgaValues.add("cg3");
        }

        if (LimboApplication.arch == Config.Arch.arm || LimboApplication.arch == Config.Arch.arm64) {
            vgaValues.add("virtio-gpu-pci");
        }

        //XXX: some archs don't support vga on QEMU like SPARC64
        vgaValues.add("nographic");

        //TODO: Add XEN???
        // "xenfb"
        return vgaValues;
    }

    public static ArrayList<String> getFirmwareValues(Context context) {
        ArrayList<String> arrList = new ArrayList<>();
        // The stored value doubles as the spinner label; VMExecutor only looks
        // for the "EFI" prefix, so localizing the labels is safe.
        arrList.add(context.getString(R.string.firmwareDefault));
        arrList.add(context.getString(R.string.firmwareEfi64));
        arrList.add(context.getString(R.string.firmwareEfiVmware));
        return arrList;
    }

    public static ArrayList<String> getKeyboardValues(Context context) {
        ArrayList<String> arrList = new ArrayList<>();
        arrList.add("en-us");
        return arrList;
    }

    public static ArrayList<String> getMouseValues(Context context) {
        ArrayList<String> arrList = new ArrayList<>();
        arrList.add("ps2");
        arrList.add("usb-mouse");
        arrList.add("usb-tablet" + " " + context.getString(R.string.fixesMouseParen));
        return arrList;
    }

    public static ArrayList<String> getUIValues() {
        ArrayList<String> arrList = new ArrayList<>();
        arrList.add("VNC");
        if (Config.enable_SDL)
            arrList.add("SDL");
        return arrList;
    }

    public static ArrayList<String> getMachineValues(Context context) {
        ArrayList<String> machinesList = new ArrayList<>();
        machinesList.add("None");
        machinesList.add("New");
        return machinesList;
    }

    public static ArrayList<String> getCpuValues(Context context) {
        ArrayList<String> arrList = new ArrayList<>();
        QemuVersion.Capabilities caps = getActiveCapabilities(context);
        if (caps != null && caps.hasCpus()) {
            arrList.add("Default");
            arrList.addAll(caps.cpus);
            return arrList;
        }
        switch (LimboApplication.arch) {
            case x86:
            case x86_64:
                arrList.add("Default");
                arrList.addAll(Arrays.asList(Installer.getAttrs(context, R.raw.x86_cpu)));
                break;
            case arm:
            case arm64:
                arrList.add("Default");
                arrList.addAll(Arrays.asList(Installer.getAttrs(context, R.raw.arm_cpu)));
                break;
            case ppc:
            case ppc64:
                arrList.add("Default");
                arrList.addAll(Arrays.asList(Installer.getAttrs(context, R.raw.ppc_cpu)));
                break;
            case sparc:
            case sparc64:
                arrList.add("Default");
                arrList.addAll(Arrays.asList(Installer.getAttrs(context, R.raw.arm_cpu)));
                break;
        }

        if (LimboApplication.arch == Config.Arch.x86 || LimboApplication.arch == Config.Arch.x86_64
                || LimboApplication.arch == Config.Arch.arm || LimboApplication.arch == Config.Arch.arm64)
            arrList.add("host");
        return arrList;
    }

    /**
     * Capability table of the QEMU build that will actually run the VM, or null
     * for packages that do not describe themselves.
     *
     * A downloadable QEMU build only knows the machine models / CPU models of
     * its own release: QEMU 8 has no "pc-q35-11.1" and QEMU 11 dropped most of
     * the old versioned aliases. Offering the full APK list for every build made
     * it possible to pick a value the running core rejects ("unsupported
     * machine type" -> exit(1) -> the VM never starts), so everything the config
     * screen offers is filtered through this table.
     */
    public static QemuVersion.Capabilities getActiveCapabilities(Context context) {
        if (LimboApplication.arch != Config.Arch.x86
                && LimboApplication.arch != Config.Arch.x86_64) {
            // The published packages are x86_64-softmmu builds; their machine /
            // CPU lists say nothing about the ARM, PPC or SPARC cores.
            return null;
        }
        try {
            QemuVersion active = QemuVersionManager.getActive(context);
            return active != null ? active.capabilities : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Machine models the active QEMU build accepts, "Default" first. */
    public static ArrayList<String> getMachineTypeValues(Context context) {
        ArrayList<String> arrList = new ArrayList<>();
        arrList.add("Default");
        QemuVersion.Capabilities caps = getActiveCapabilities(context);
        if (caps != null && caps.hasMachineTypes()) {
            arrList.addAll(caps.machineTypes);
            return arrList;
        }
        switch (LimboApplication.arch) {
            case x86:
            case x86_64:
                arrList.addAll(Arrays.asList(Installer.getAttrs(context, R.raw.x86_machine_types)));
                break;
            case arm:
            case arm64:
                arrList.remove(0);
                arrList.addAll(Arrays.asList(Installer.getAttrs(context, R.raw.arm_machine_types)));
                break;
            case ppc:
            case ppc64:
                arrList.addAll(Arrays.asList(Installer.getAttrs(context, R.raw.ppc_machine_types)));
                break;
            case sparc:
            case sparc64:
                arrList.addAll(Arrays.asList(Installer.getAttrs(context, R.raw.sparc_machine_types)));
                break;
        }
        return arrList;
    }
}
