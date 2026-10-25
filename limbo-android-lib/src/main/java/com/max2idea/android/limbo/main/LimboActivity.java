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
package com.max2idea.android.limbo.main;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.dialog.DialogUtils;
import com.max2idea.android.limbo.files.FileInstaller;
import com.max2idea.android.limbo.files.FileUtils;
import com.max2idea.android.limbo.help.Help;
import com.max2idea.android.limbo.install.Installer;
import com.max2idea.android.limbo.keyboard.KeyboardUtils;
import com.max2idea.android.limbo.links.LinksManager;
import com.max2idea.android.limbo.log.Logger;
import com.max2idea.android.limbo.machine.ArchDefinitions;
import com.max2idea.android.limbo.machine.BIOSImporter;
import com.max2idea.android.limbo.machine.Machine;
import com.max2idea.android.limbo.machine.Machine.FileType;
import com.max2idea.android.limbo.machine.MachineAction;
import com.max2idea.android.limbo.machine.MachineController;
import com.max2idea.android.limbo.machine.MachineController.MachineStatus;
import com.max2idea.android.limbo.machine.MachineExporter;
import com.max2idea.android.limbo.machine.MachineFilePaths;
import com.max2idea.android.limbo.machine.MachineImporter;
import com.max2idea.android.limbo.machine.MachineProperty;
import com.max2idea.android.limbo.network.NetworkUtils;
import com.max2idea.android.limbo.toast.ToastUtils;
import com.max2idea.android.limbo.ui.AppBackgroundView;
import com.max2idea.android.limbo.ui.GlassBlurView;
import com.max2idea.android.limbo.ui.SpinnerAdapter;
import com.max2idea.android.limbo.ui.SteplessSlider;
import com.max2idea.android.limbo.updates.UpdateChecker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;

public class LimboActivity extends AppCompatActivity
        implements MachineController.OnMachineStatusChangeListener,
        MachineController.OnEventListener, Observer {

    private static final String TAG = "LimboActivity";

    private static final int HELP = 0;
    private static final int QUIT = 1;
    private static final int INSTALL = 2;
    private static final int DELETE = 3;
    private static final int EXPORT = 4;
    private static final int IMPORT = 5;
    private static final int CHANGELOG = 6;
    private static final int LICENSE = 7;
    private static final int VIEWLOG = 8;
    private static final int CREATE = 9;
    private static final int DISCARD_VM_STATE = 11;
    private static final int SETTINGS = 13;
    private static final int TOOLS = 14;
    private static final int IMPORT_BIOS_FILE = 15;
    private static final int VNC_CLIENT = 16;

    // disk mapping
    private static final Hashtable<FileType, DiskInfo> diskMapping = new Hashtable<>();

    private static boolean libLoaded;
    public View parent;
    private boolean machineLoaded;
    private FileType browseFileType = null;

    //Widgets
    private ImageView mStatus;
    private EditText mDNS;
    private EditText mHOSTFWD;
    private EditText mAppend;
    private EditText mExtraParams;
    private TextView mStatusText;
    private Spinner mMachine;
    private Spinner mCPU;
    private Spinner mMachineType;
    private Spinner mCPUNum;
    private Spinner mKernel;
    private Spinner mInitrd;
    // HDD
    private ImageView mHDAOptions;
    private Spinner mHDA;
    private ImageView mHDBOptions;
    private Spinner mHDB;
    private ImageView mHDCOptions;
    private Spinner mHDC;
    private ImageView mHDDOptions;
    private Spinner mHDD;
    private Spinner mSharedFolder;

    //removable
    private Spinner mCD;
    private Spinner mFDA;
    private Spinner mFDB;
    private Spinner mSD;
    private CompoundButton mCDenable;
    private CompoundButton mFDAenable;
    private CompoundButton mFDBenable;
    private CompoundButton mSDenable;
    private ImageView mCDOptions;

    // misc
    private Spinner mRamSize;
    private Spinner mBootDevices;
    private Spinner mFirmware;
    private Spinner mNetworkCard;
    private Spinner mNetConfig;
    private Spinner mVGAConfig;
    private Spinner mSoundCard;
    private Spinner mUI;
    private CompoundButton mDisableACPI;
    private CompoundButton mDisableHPET;
    private CompoundButton mDisableTSC;
    private CompoundButton mEnableKVM;
    private CompoundButton mEnableMTTCG;
    private Spinner mKeyboard;
    private Spinner mMouse;

    // buttons
    private ImageButton mStart;
    private ImageButton mPause;
    private ImageButton mStop;
    private ImageButton mRestart;

    //sections
    private LinearLayout mCPUSectionDetails;
    private LinearLayout mStorageSectionDetails;
    private LinearLayout mUserInterfaceSectionDetails;
    private LinearLayout mAdvancedSectionDetails;
    private LinearLayout mBootSectionDetails;
    private LinearLayout mGraphicsSectionDetails;
    private LinearLayout mRemovableStorageSectionDetails;
    private LinearLayout mNetworkSectionDetails;
    private LinearLayout mAudioSectionDetails;

    //summary
    private TextView mUISectionSummary;
    private TextView mCPUSectionSummary;
    private TextView mStorageSectionSummary;
    private TextView mRemovableStorageSectionSummary;
    private TextView mGraphicsSectionSummary;
    private TextView mAudioSectionSummary;
    private TextView mNetworkSectionSummary;
    private TextView mBootSectionSummary;
    // Home / config modes
    public static final String EXTRA_MACHINE = "limbo_machine";
    public static final String EXTRA_START = "limbo_autostart";
    private boolean pendingAutoStart;
    private HomeController homeController;
    private boolean homeMode;
    private String pendingMachineName;
    /** Non-null while the configuration page is still loading the machine from the intent. */
    private String loadingMachineName;
    /** Keeps the drive/settings listeners disabled until all async disk adapters settled. */
    private int pendingDiskAdapters;
    private boolean machineHydrated;
    private boolean listenersEnabled;
    /**
     * Spinners whose current selection was set by code. The synthetic
     * onItemSelected() callback must never be written back to the machine, or a
     * "None" placeholder ends up replacing a real disk image path.
     */
    private final java.util.IdentityHashMap<Object, Integer> programmaticSpinners =
            new java.util.IdentityHashMap<>();
    private boolean storagePermissionAsked;
    private boolean lastErrorShown;
    private int autoStartAttempts;
    private static final int REQ_STORAGE_PERMISSION = 9103;

    // Glass UI
    private GlassBlurView mToolbarGlass;
    private GlassBlurView mNavGlass;
    private LinearLayout mTabOverview, mTabHardware, mTabStorage, mTabDisplay, mTabNetwork;
    private final View[] mNavRoots = new View[5];
    private final ImageView[] mNavIcons = new ImageView[5];
    private final TextView[] mNavLabels = new TextView[5];
    private int mCurrentTab = 0;
    private SteplessSlider mCpuSlider, mRamSlider;
    private TextView mCpuValue, mRamValue;
    private TextView mStatusPill, mStatusDetail;
    private boolean mSyncingSliders;

    private TextView mAdvancedSectionSummary;

    //layouts
    private NestedScrollView mScrollView;
    private boolean firstMTTCGCheck;
    private ViewListener viewListener;

    public void changeStatus(final MachineStatus status_changed) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (MachineController.getInstance().isRunning() || status_changed == MachineStatus.Running) {
                    mStatus.setImageResource(R.drawable.on);
                    if (mUI.getSelectedItemPosition() == 0) {
                        // VNC
                        mStatusText.setText(R.string.Running);
                        //XXX: we block the user from changing the drives
                        // from this activitybecause sdl is suspended and the thread will block
                        // so they have to change it from within the SDL Activity
                        enableRemovableDiskValues(true);
                    } else {
                        // The SDL surface is suspended while this page is open, but
                        // the machine itself is running.
                        mStatusText.setText(R.string.Running);
                        enableRemovableDiskValues(false);
                    }
                    unlockRemovableDevices(false);
                    enableNonRemovableDeviceOptions(false);
                    mMachine.setEnabled(false);
                } else if (status_changed == MachineStatus.Ready || status_changed == MachineStatus.Stopped) {
                    mStatus.setImageResource(R.drawable.off);
                    mStatusText.setText(R.string.Stopped);
                    unlockRemovableDevices(true);
                    enableRemovableDiskValues(true);
                    enableNonRemovableDeviceOptions(true);
                } else if (status_changed == MachineStatus.Saving) {
                    mStatus.setImageResource(R.drawable.on);
                    mStatusText.setText(R.string.savingState);
                    unlockRemovableDevices(false);
                    enableRemovableDiskValues(false);
                    enableNonRemovableDeviceOptions(false);
                } else if (status_changed == MachineStatus.Paused) {
                    mStatus.setImageResource(R.drawable.on);
                    mStatusText.setText(R.string.paused);
                    unlockRemovableDevices(false);
                    enableRemovableDiskValues(false);
                    enableNonRemovableDeviceOptions(false);
                }
                applyStatusVisuals(status_changed);
            }
        });
    }

    private void onTap() {
        String userid = LimboApplication.getUserId(this);
        if (!(new File("/dev/net/tun")).exists()) {
            LimboActivityCommon.tapNotSupported(this, userid);
            return;
        }
        LimboActivityCommon.promptTap(this, userid);
    }

    public void setUserPressed(boolean pressed) {
        listenersEnabled = pressed;
        if (pressed) {
            // Attaching a listener immediately fires onItemSelected() for the
            // current selection; swallow those synthetic callbacks.
            suppressAllDriveSpinners();
            setupMiscOptions();
            setupNonRemovableDiskListeners();
            enableRemovableDiskListeners();
        } else {
            disableListeners();
            disableRemovableDiskListeners();
        }
    }

    /**
     * Disk adapters are rebuilt on background threads and replacing an adapter resets
     * the spinner selection. If the change listeners are attached at that moment the
     * synthetic "None" selection is written back to the machine and wipes its disk
     * paths. Only re-enable editing once every adapter has settled.
     */
    private void maybeEnableEditors() {
        if (pendingDiskAdapters == 0 && machineHydrated && !listenersEnabled) {
            setUserPressed(true);
        }
    }

    // ---------------------------------------------------------------------
    // Startup safety net: storage permission + no silently wiped drives
    // ---------------------------------------------------------------------

    /**
     * Android only grants READ/WRITE_EXTERNAL_STORAGE at runtime. Without it the
     * emulator can see neither the disk images nor the log directory, QEMU exits
     * immediately and the app looks like it "does nothing" when Start is pressed.
     */
    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        try {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return true;
        }
    }

    private void ensureStoragePermission(boolean showDialog) {
        if (hasStoragePermission()) {
            return;
        }
        if (!showDialog) {
            if (storagePermissionAsked) {
                return;
            }
            storagePermissionAsked = true;
        }
        try {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQ_STORAGE_PERMISSION);
        } catch (Throwable t) {
            Log.w(TAG, "Could not request storage permission", t);
        }
    }

    private void promptStoragePermissionMissing() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.storage_permission_title)
                .setMessage(R.string.storage_permission_message)
                .setPositiveButton(R.string.storage_permission_grant, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        ensureStoragePermission(true);
                    }
                })
                .setNegativeButton(R.string.storage_permission_settings, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Throwable ignored) {
                        }
                    }
                })
                .setCancelable(true)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_STORAGE_PERMISSION) {
            return;
        }
        if (hasStoragePermission()) {
            ToastUtils.toastShort(this, getString(R.string.storage_permission_granted));
        } else {
            promptStoragePermissionMissing();
        }
    }

    /** Shows why the previous run died; the VM process cannot report it itself. */
    private void showLastVmErrorIfAny() {
        if (lastErrorShown || homeMode && MachineController.getInstance().isRunning()) {
            return;
        }
        lastErrorShown = true;
        String error = null;
        try {
            error = LimboSettingsManager.getLastVmError(this);
        } catch (Throwable ignored) {
        }
        if (error == null || error.trim().length() == 0) {
            return;
        }
        try {
            LimboSettingsManager.setLastVmError(this, null);
        } catch (Throwable ignored) {
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.last_vm_error_title)
                .setMessage(error)
                .setPositiveButton(R.string.last_vm_error_ok, null)
                .setCancelable(true)
                .show();
    }

    /** Marks a spinner as changed by code: its next onItemSelected() must be ignored. */
    private void suppressSpinnerCallbacks(final View spinner) {
        if (spinner == null) {
            return;
        }
        Integer depth = programmaticSpinners.get(spinner);
        programmaticSpinners.put(spinner, depth == null ? 1 : depth + 1);
        spinner.postDelayed(new Runnable() {
            public void run() {
                Integer current = programmaticSpinners.get(spinner);
                if (current == null) {
                    return;
                }
                if (current <= 1) {
                    programmaticSpinners.remove(spinner);
                } else {
                    programmaticSpinners.put(spinner, current - 1);
                }
            }
        }, 700);
    }

    private boolean spinnerCallbacksSuppressed(View spinner) {
        return spinner != null && programmaticSpinners.containsKey(spinner);
    }

    private void suppressAllDriveSpinners() {
        suppressSpinnerCallbacks(mHDA);
        suppressSpinnerCallbacks(mHDB);
        suppressSpinnerCallbacks(mHDC);
        suppressSpinnerCallbacks(mHDD);
        suppressSpinnerCallbacks(mSharedFolder);
        suppressSpinnerCallbacks(mCD);
        suppressSpinnerCallbacks(mFDA);
        suppressSpinnerCallbacks(mFDB);
        suppressSpinnerCallbacks(mSD);
        suppressSpinnerCallbacks(mKernel);
        suppressSpinnerCallbacks(mInitrd);
    }

    private void disableRemovableDiskListeners() {
        disableRemovableDiskListener(mCDenable, mCD);
        disableRemovableDiskListener(mCDenable, mCD);
        disableRemovableDiskListener(mCDenable, mCD);
        disableRemovableDiskListener(mCDenable, mCD);
    }

    private void disableRemovableDiskListener(CompoundButton enableDrive, Spinner spinner) {
        enableDrive.setOnCheckedChangeListener(null);
        spinner.setOnItemSelectedListener(null);
    }

    private void enableRemovableDiskListeners() {
        enableRemovableDiskListener(mCD, mCDenable, mCDOptions, MachineProperty.CDROM, FileType.CDROM);
        enableRemovableDiskListener(mFDA, mFDAenable, null, MachineProperty.FDA, FileType.FDA);
        enableRemovableDiskListener(mFDB, mFDBenable, null, MachineProperty.FDB, FileType.FDB);
        enableRemovableDiskListener(mSD, mSDenable, null, MachineProperty.SD, FileType.SD);
    }

    private void enableRemovableDiskListener(final Spinner spinner, final CompoundButton driveEnable,
                                             final ImageView driveOptions,
                                             final MachineProperty driveName,
                                             final FileType fileType) {
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null || spinnerCallbacksSuppressed(spinner))
                    return;
                String value = (String) ((ArrayAdapter<?>) spinner.getAdapter()).getItem(position);
                if (position == 1 && driveEnable.isChecked()) {
                    browseFileType = fileType;
                    LimboFileManager.browse(LimboActivity.this, browseFileType, Config.OPEN_IMAGE_FILE_REQUEST_CODE);
                    spinner.setSelection(0);
                } else if (position == 0) {
                    notifyFieldChange(MachineProperty.REMOVABLE_DRIVE, new Object[]{driveName, null});
                } else {
                    notifyFieldChange(MachineProperty.REMOVABLE_DRIVE, new Object[]{driveName, value});
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });
        driveEnable.setOnCheckedChangeListener(
                new OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton viewButton, boolean isChecked) {
                        spinner.setEnabled(isChecked);
                        notifyFieldChange(MachineProperty.DRIVE_ENABLED, new Object[]{driveName, isChecked});
                        triggerUpdateSpinner(spinner);
                    }

                }
        );
        if(driveOptions!=null) {
            driveOptions.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(driveEnable.isChecked())
                        promptDriveInterface(driveName);
                }
            });
        }
    }


    private void setupMiscOptions() {

        mCPU.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String cpu = (String) ((ArrayAdapter<?>) mCPU.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.CPU, cpu);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mMachineType.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String machineType = (String) ((ArrayAdapter<?>) mMachineType.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.MACHINETYPE, machineType);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mUI.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String ui = (String) ((ArrayAdapter<?>) mUI.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.UI, ui);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mCPUNum.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                syncCpuSliderFromSpinner();
                if (getMachine() == null)
                    return;
                final String cpuNum = (String) ((ArrayAdapter<?>) mCPUNum.getAdapter()).getItem(position);
                if (position > 0 && getMachine().getEnableMTTCG() != 1 && getMachine().getEnableKVM() != 1 && !firstMTTCGCheck) {
                    firstMTTCGCheck = true;
                    promptMultiCPU(cpuNum);
                } else {
                    notifyFieldChange(MachineProperty.CPUNUM, cpuNum);
                }
                mDisableTSC.setChecked(position > 0 && (LimboApplication.arch == Config.Arch.x86 ||
                        LimboApplication.arch == Config.Arch.x86_64));
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mRamSize.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                syncRamSliderFromSpinner();
                if (getMachine() == null)
                    return;
                String ram = (String) ((ArrayAdapter<?>) mRamSize.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.MEMORY, ram);
            }

            public void onNothingSelected(AdapterView<?> parentView) {

            }
        });

        mKernel.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null || spinnerCallbacksSuppressed(mKernel))
                    return;
                String kernel = (String) ((ArrayAdapter<?>) mKernel.getAdapter()).getItem(position);
                if (position == 0) {
                    notifyFieldChange(MachineProperty.KERNEL, null);
                } else if (position == 1) {
                    browseFileType = FileType.KERNEL;
                    LimboFileManager.browse(LimboActivity.this, browseFileType, Config.OPEN_IMAGE_FILE_REQUEST_CODE);
                    mKernel.setSelection(0);
                } else if (position > 1) {
                    notifyFieldChange(MachineProperty.KERNEL, kernel);
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mInitrd.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null || spinnerCallbacksSuppressed(mInitrd))
                    return;
                String initrd = (String) ((ArrayAdapter<?>) mInitrd.getAdapter()).getItem(position);
                if (position == 0) {
                    notifyFieldChange(MachineProperty.INITRD, null);
                } else if (position == 1) {
                    browseFileType = FileType.INITRD;
                    LimboFileManager.browse(LimboActivity.this, browseFileType, Config.OPEN_IMAGE_FILE_REQUEST_CODE);
                    mInitrd.setSelection(0);
                } else if (position > 1) {
                    notifyFieldChange(MachineProperty.INITRD, initrd);
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mBootDevices.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;

                String bootDev = (String) ((ArrayAdapter<?>) mBootDevices.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.BOOT_CONFIG, bootDev);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        if (mFirmware != null) {
            mFirmware.setOnItemSelectedListener(new OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                    if (getMachine() == null)
                        return;

                    String firmware = (String) ((ArrayAdapter<?>) mFirmware.getAdapter()).getItem(position);
                    notifyFieldChange(MachineProperty.FIRMWARE, firmware);
                }

                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });
        }

        mNetConfig.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;

                String netcfg = (String) ((ArrayAdapter<?>) mNetConfig.getAdapter()).getItem(position);
                // Re-attaching this listener while the page is populated triggers a
                // synthetic selection callback for the current value; only the actual
                // change should be stored and warned about, otherwise the external
                // network dialog pops up every time a machine is opened.
                String currentNetwork = getMachine().getNetwork();
                boolean networkChanged = currentNetwork == null || !currentNetwork.equals(netcfg);
                if (networkChanged) {
                    notifyFieldChange(MachineProperty.NETCONFIG, netcfg);
                }
                if (position > 0 && getMachine().getPaused() == 0
                        && MachineController.getInstance().getCurrStatus() != MachineStatus.Running) {
                    mNetworkCard.setEnabled(true);
                    mDNS.setEnabled(true);
                    mHOSTFWD.setEnabled(true);
                } else {
                    mNetworkCard.setEnabled(false);
                    mDNS.setEnabled(false);
                    mHOSTFWD.setEnabled(false);
                }

                if (networkChanged) {
                    if (netcfg.equals("TAP")) {
                        onTap();
                    } else if (netcfg.equals("User")) {
                        LimboActivityCommon.onNetworkUser(LimboActivity.this);
                    }
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mNetworkCard.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                if (position < 0 || position >= mNetworkCard.getCount()) {
                    mNetworkCard.setSelection(0);
                    return;
                }
                String niccfg = (String) ((ArrayAdapter<?>) mNetworkCard.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.NICCONFIG, niccfg);
            }

            public void onNothingSelected(final AdapterView<?> parentView) {
            }
        });

        mVGAConfig.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String vgacfg = (String) ((ArrayAdapter<?>) mVGAConfig.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.VGA, vgacfg);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mSoundCard.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String sndcfg = (String) ((ArrayAdapter<?>) mSoundCard.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.SOUNDCARD, sndcfg);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mDisableACPI.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton viewButton, boolean isChecked) {
                if (getMachine() == null)
                    return;
                notifyFieldChange(MachineProperty.DISABLE_ACPI, isChecked);
            }
        });

        mDisableHPET.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton viewButton, boolean isChecked) {
                if (getMachine() == null)
                    return;
                notifyFieldChange(MachineProperty.DISABLE_HPET, isChecked);
            }
        });

        mDisableTSC.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton viewButton, boolean isChecked) {
                if (getMachine() == null)
                    return;
                notifyFieldChange(MachineProperty.DISABLE_TSC, isChecked);
            }
        });

        mDNS.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (getMachine() == null)
                    return;
                if (!hasFocus) {
                    setDNSServer(mDNS.getText().toString());
                    LimboSettingsManager.setDNSServer(LimboActivity.this, mDNS.getText().toString());
                }
            }
        });

        mHOSTFWD.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (getMachine() == null)
                    return;
                if (!hasFocus) {
                    notifyFieldChange(MachineProperty.HOSTFWD, mHOSTFWD.getText().toString());
                }
            }
        });

        mAppend.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (getMachine() == null)
                    return;
                if (!hasFocus) {
                    notifyFieldChange(MachineProperty.APPEND, mAppend.getText().toString());
                }
            }
        });

        mExtraParams.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (getMachine() == null)
                    return;
                if (!hasFocus) {
                    notifyFieldChange(MachineProperty.EXTRA_PARAMS, mExtraParams.getText().toString());
                }
            }
        });

        OnClickListener resetClickListener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                view.setFocusableInTouchMode(true);
                view.setFocusable(true);
            }
        };

        mDNS.setOnClickListener(resetClickListener);
        mAppend.setOnClickListener(resetClickListener);
        mHOSTFWD.setOnClickListener(resetClickListener);
        mExtraParams.setOnClickListener(resetClickListener);
        mEnableKVM.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton viewButton, boolean isChecked) {
                if (getMachine() == null)
                    return;
                if (isChecked) {
                    promptKVM();
                } else {
                    notifyFieldChange(MachineProperty.ENABLE_KVM, isChecked);
                }

            }

        });

        mEnableMTTCG.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton viewButton, boolean isChecked) {
                if (getMachine() == null)
                    return;
                if (isChecked) {
                    promptEnableMTTCG();
                } else {
                    notifyFieldChange(MachineProperty.ENABLE_MTTCG, isChecked);
                }
            }
        });

        mKeyboard.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String keyboardCfg = (String) ((ArrayAdapter<?>) mKeyboard.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.KEYBOARD, keyboardCfg);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mMouse.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                String mouseCfg = (String) ((ArrayAdapter<?>) mMouse.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.MOUSE, mouseCfg);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });
    }

    private void setCPUOptions() {
        if (MachineController.getInstance().getCurrStatus() != MachineStatus.Running &&
                (LimboApplication.arch == Config.Arch.x86 || LimboApplication.arch == Config.Arch.x86_64)) {
            mDisableACPI.setEnabled(true);
            mDisableHPET.setEnabled(true);
            mDisableTSC.setEnabled(true);
        } else {
            mDisableACPI.setEnabled(false);
            mDisableHPET.setEnabled(false);
            mDisableTSC.setEnabled(false);
        }
    }

    private void setArchOptions() {
        if (!machineLoaded) {
            populateMachineType(getMachine().getMachineType());
            populateCPUs(getMachine().getCpu());
            populateNetDevices(getMachine().getNetworkCard());
        }
    }

    private void promptKVM() {
        DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                notifyFieldChange(MachineProperty.ENABLE_KVM, true);
                mEnableMTTCG.setChecked(false);
            }
        };

        DialogInterface.OnClickListener cancelListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mEnableKVM.setChecked(false);
                        notifyFieldChange(MachineProperty.ENABLE_KVM, false);
                    }
                };

        DialogInterface.OnClickListener helpListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mEnableKVM.setChecked(false);
                        notifyFieldChange(MachineProperty.ENABLE_KVM, false);
                        LimboActivityCommon.goToURL(LimboActivity.this, Config.kvmLink);
                    }
                };

        DialogUtils.UIAlert(LimboActivity.this, getString(R.string.EnableKVM),
                getString(R.string.EnableKVMWarning),
                16, false, getString(android.R.string.ok),
                okListener, getString(android.R.string.cancel),
                cancelListener, getString(R.string.KVMHelp), helpListener);
    }

    private void promptEnableMTTCG() {
        DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                notifyFieldChange(MachineProperty.ENABLE_MTTCG, true);
                mEnableKVM.setChecked(false);
            }
        };
        DialogInterface.OnClickListener cancelListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        notifyFieldChange(MachineProperty.ENABLE_MTTCG, false);
                        mEnableMTTCG.setChecked(false);
                    }
                };
        DialogInterface.OnClickListener helpListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mEnableMTTCG.setChecked(false);
                        notifyFieldChange(MachineProperty.ENABLE_MTTCG, false);
                        LimboActivityCommon.goToURL(LimboActivity.this, Config.faqLink);
                    }
                };
        DialogUtils.UIAlert(LimboActivity.this, getString(R.string.enableMTTCG),
                getString(R.string.enableMTTCGWarning),
                16, false, getString(android.R.string.ok), okListener,
                getString(android.R.string.cancel)
                , cancelListener, getString(R.string.mttcgHelp), helpListener);
    }

    private void promptMultiCPU(final String cpuNum) {
        DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                notifyFieldChange(MachineProperty.CPUNUM, cpuNum);
            }
        };
        DialogInterface.OnClickListener cancelListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mCPUNum.setSelection(0);
                    }
                };
        DialogInterface.OnClickListener helpListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mCPUNum.setSelection(0);
                        LimboActivityCommon.goToURL(LimboActivity.this, Config.faqLink);
                    }
                };
        DialogUtils.UIAlert(LimboActivity.this, getString(R.string.multipleVCPU),
                getString(R.string.multipleVCPUWarning)
                        + ((LimboApplication.arch == Config.Arch.x86_64) ?
                        getString(R.string.disableTSCInstructions) : "")
                        + " " + getString(R.string.DoYouWantToContinue),
                16, false, getString(android.R.string.ok), okListener,
                getString(android.R.string.cancel), cancelListener, getString(R.string.vCPUHelp), helpListener);
    }

    private void setupNonRemovableDiskListeners() {
        setupNonRemovableDiskListener(mHDA, mHDAOptions, MachineProperty.HDA, FileType.HDA);
        setupNonRemovableDiskListener(mHDB, mHDBOptions, MachineProperty.HDB, FileType.HDB);
        setupNonRemovableDiskListener(mHDC, mHDCOptions, MachineProperty.HDC, FileType.HDC);
        setupNonRemovableDiskListener(mHDD, mHDDOptions, MachineProperty.HDD, FileType.HDD);
        setupSharedFolderDisk();
    }

    private void setupNonRemovableDiskListener(final Spinner diskSpinner, final ImageView diskImage,
                                               final MachineProperty machineDriveName,
                                               final FileType diskFileType) {
        diskSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null || spinnerCallbacksSuppressed(diskSpinner))
                    return;

                String hdName = (String) ((ArrayAdapter<?>) diskSpinner.getAdapter()).getItem(position);
                if (position == 1) {
                    promptImageName(LimboActivity.this, diskFileType);
                    diskSpinner.setSelection(0);
                } else if (position == 2) {
                    browseFileType = diskFileType;
                    LimboFileManager.browse(LimboActivity.this, browseFileType, Config.OPEN_IMAGE_FILE_REQUEST_CODE);
                    diskSpinner.setSelection(0);
                } else if (position == 0) {
                    notifyFieldChange(MachineProperty.NON_REMOVABLE_DRIVE, new Object[]{machineDriveName, null});
                } else {
                    notifyFieldChange(MachineProperty.NON_REMOVABLE_DRIVE, new Object[]{machineDriveName, hdName});
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        diskImage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                promptDriveInterface(machineDriveName);
            }
        });
    }

    private void promptDriveInterface(final MachineProperty machineDriveName) {
        if(getMachine() == null)
            return;

        final String[] items = {
                "ide",
                "scsi",
                "virtio"
        };
        final AlertDialog.Builder mBuilder = new AlertDialog.Builder(this);
        mBuilder.setTitle(machineDriveName + " " + getString(R.string.Interface));
        int driveInterface = getMachineInterface(machineDriveName, items);
        mBuilder.setSingleChoiceItems(items, driveInterface, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                notifyFieldChange(MachineProperty.MEDIA_INTERFACE, new Object[] {machineDriveName, items[i]});
                dialog.dismiss();
            }
        });
        final AlertDialog alertDialog = mBuilder.create();
        alertDialog.show();
    }

    private int getMachineInterface(MachineProperty machineDriveName, String[] items) {
        String hdInterfaceStr = null;
        switch(machineDriveName) {
            case HDA:
                hdInterfaceStr = getMachine().getHdaInterface();
                break;
            case HDB:
                hdInterfaceStr = getMachine().getHdbInterface();
                break;
            case HDC:
                hdInterfaceStr = getMachine().getHdcInterface();
                break;
            case HDD:
                hdInterfaceStr = getMachine().getHddInterface();
                break;
            case CDROM:
                hdInterfaceStr = getMachine().getCDInterface();
                break;
        }
        for(int i=0; i<items.length; i++) {
            if(items[i].equals(hdInterfaceStr))
                return i;
        }
        return 0;
    }

    public void setupSharedFolderDisk() {
        mSharedFolder.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {

                if (getMachine() == null || spinnerCallbacksSuppressed(mSharedFolder))
                    return;

                String shared_folder = (String) ((ArrayAdapter<?>) mSharedFolder.getAdapter()).getItem(position);
                if (position == 0) {
                    notifyFieldChange(MachineProperty.SHARED_FOLDER, null);
                } else if (position == 1) {
                    browseFileType = FileType.SHARED_DIR;
                    LimboFileManager.browse(LimboActivity.this, browseFileType, Config.OPEN_SHARED_DIR_REQUEST_CODE);
                    mSharedFolder.setSelection(0);
                } else if (position > 1) {
                    notifyFieldChange(MachineProperty.SHARED_FOLDER, shared_folder);
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });
    }

    protected synchronized void setDNSServer(String string) {

        File resolvConf = new File(LimboApplication.getBasefileDir() + "/etc/resolv.conf");
        FileOutputStream fileStream = null;
        try {
            fileStream = new FileOutputStream(resolvConf);
            String str = "nameserver " + string + "\n\n";
            byte[] data = str.getBytes();
            fileStream.write(data);
        } catch (Exception ex) {
            Log.e(TAG, "Could not write DNS to file: " + ex);
        } finally {
            if (fileStream != null)
                try {
                    fileStream.close();
                } catch (IOException e) {

                    e.printStackTrace();
                }
        }
    }

    private void disableListeners() {
        if (mMachine == null)
            return;
        mUI.setOnItemSelectedListener(null);
        mKeyboard.setOnItemSelectedListener(null);
        mMouse.setOnItemSelectedListener(null);
        mMachineType.setOnItemSelectedListener(null);
        mCPU.setOnItemSelectedListener(null);
        mCPUNum.setOnItemSelectedListener(null);
        mRamSize.setOnItemSelectedListener(null);
        mDisableACPI.setOnCheckedChangeListener(null);
        mDisableHPET.setOnCheckedChangeListener(null);
        mDisableTSC.setOnCheckedChangeListener(null);
        mEnableKVM.setOnCheckedChangeListener(null);
        mEnableMTTCG.setOnCheckedChangeListener(null);
        mHDA.setOnItemSelectedListener(null);
        mHDB.setOnItemSelectedListener(null);
        mHDC.setOnItemSelectedListener(null);
        mHDD.setOnItemSelectedListener(null);
        mSharedFolder.setOnItemSelectedListener(null);
        mBootDevices.setOnItemSelectedListener(null);
        mKernel.setOnItemSelectedListener(null);
        mInitrd.setOnItemSelectedListener(null);
        mAppend.setOnFocusChangeListener(null);
        mVGAConfig.setOnItemSelectedListener(null);
        mSoundCard.setOnItemSelectedListener(null);
        mNetConfig.setOnItemSelectedListener(null);
        mNetworkCard.setOnItemSelectedListener(null);
        mDNS.setOnFocusChangeListener(null);
        mHOSTFWD.setOnFocusChangeListener(null);
        mExtraParams.setOnFocusChangeListener(null);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Global crash logger for debugging (writes to /sdcard/Download/limbo_crash.txt)
        try {
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable throwable) {
                    try {
                        String trace = Log.getStackTraceString(throwable);
                        // write via MediaStore (public Downloads, no permission needed on 10+)
                        try {
                            if (Build.VERSION.SDK_INT >= 29) {
                                android.content.ContentValues values = new android.content.ContentValues();
                                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "limbo_crash.txt");
                                values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                                        android.os.Environment.DIRECTORY_DOWNLOADS);
                                android.net.Uri uri = getContentResolver().insert(
                                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                                if (uri != null) {
                                    java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                                    os.write(trace.getBytes());
                                    os.close();
                                }
                            } else {
                                FileUtils.saveFileContents("/sdcard/Download/limbo_crash.txt", trace);
                            }
                        } catch (Exception ignored) {
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (Exception ignored) {
        }
        super.onCreate(savedInstanceState);
        setupAppEnvironment();
        clearNotifications();
        setupStrictMode();

        final String machineExtra = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_MACHINE) : null;
        if (machineExtra == null) {
            homeMode = true;
            setContentView(R.layout.activity_home);
            setupController();
            setupHome();
            checkFirstLaunch();
            // QEMU cannot reach the disk images without the runtime storage grant.
            ensureStoragePermission(false);
            showLastVmErrorIfAny();
            return;
        }
        pendingMachineName = machineExtra;
        pendingAutoStart = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_START, false);

        setContentView(R.layout.limbo_main);
        setupWidgets();
        setupController();
        // Register the model listeners before the machine load is requested so the
        // MachineLoaded event can never get lost.
        setupListeners();
        setupDiskMapping();
        createListeners();
        prepareConfigMachine();
        if (isFinishing()) {
            return;
        }
        populateAttributesUI();
        checkFirstLaunch();
        setupToolbar();
        setupModernUI();
        checkUpdate();
        checkLog();
        checkAndLoadLibs();
        restore();
        addGenericOperatingSystems();
        showLastVmErrorIfAny();
    }

    private void setupAppEnvironment() {
        LimboApplication.setupEnv(this);
    }

    private void setupController() {
        setViewListener(LimboApplication.getViewListener());
    }

    public void setViewListener(ViewListener viewListener) {
        this.viewListener = viewListener;
    }

    private void setupListeners() {
        MachineController.getInstance().addOnStatusChangeListener(this);
        MachineController.getInstance().addOnEventListener(this);
    }

    private void restoreUI(final String machine) {
        int position = SpinnerAdapter.getItemPosition(mMachine, machine);
        mMachine.setSelection(position);
    }

    private void restore() {
        if (pendingMachineName != null) {
            // The user explicitly opened a machine configuration; never override it
            // with whatever machine happens to be running.
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (MachineController.getInstance().isRunning()) {
                    restoreUI(MachineController.getInstance().getMachineName());
                }
            }
        }, 1000);
    }

    private void checkAndLoadLibs() {
        if (Config.loadNativeLibsEarly)
            if (Config.loadNativeLibsMainThread)
                setupNativeLibs();
            else
                setupNativeLibsAsync();
    }

    private void clearNotifications() {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    private void setupDiskMapping() {
        diskMapping.clear();
        addDiskMapping(FileType.HDA, mHDA, null, MachineProperty.HDA);
        addDiskMapping(FileType.HDB, mHDB, null, MachineProperty.HDB);
        addDiskMapping(FileType.HDC, mHDC, null, MachineProperty.HDC);
        addDiskMapping(FileType.HDD, mHDD, null, MachineProperty.HDD);
        addDiskMapping(FileType.SHARED_DIR, mSharedFolder, null, MachineProperty.SHARED_FOLDER);

        addDiskMapping(FileType.CDROM, mCD, mCDenable, MachineProperty.CDROM);
        addDiskMapping(FileType.FDA, mFDA, mFDAenable, MachineProperty.FDA);
        addDiskMapping(FileType.FDB, mFDB, mFDBenable, MachineProperty.FDB);
        addDiskMapping(FileType.SD, mSD, mSDenable, MachineProperty.SD);

        addDiskMapping(FileType.KERNEL, mKernel, null, MachineProperty.KERNEL);
        addDiskMapping(FileType.INITRD, mInitrd, null, MachineProperty.INITRD);
    }

    private void addDiskMapping(FileType fileType, Spinner spinner,
                                CompoundButton enableCheckBox, MachineProperty dbColName) {
        spinner.setTag(fileType);

        diskMapping.put(fileType, new DiskInfo(spinner, enableCheckBox, dbColName));
    }

    private void setupNativeLibsAsync() {

        Thread thread = new Thread(new Runnable() {
            public void run() {
                setupNativeLibs();
            }
        });
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();

    }

    private void createListeners() {

        mMachine.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {

                if (position == 0) {
                    enableNonRemovableDeviceOptions(false);
                    enableRemovableDeviceOptions(false);
                    // While the initial machine is still loading the spinner is being
                    // populated and fires a transient "None" selection - ignore it.
                    if (!MachineController.getInstance().isRunning()
                            && loadingMachineName == null)
                        notifyAction(MachineAction.LOAD_VM, null);
                } else if (position == 1) {
                    mMachine.setSelection(0);
                    promptMachineName(LimboActivity.this);
                } else {
                    final String machine = (String) ((ArrayAdapter<?>) mMachine.getAdapter()).getItem(position);
                    // The machine requested from the intent is already being loaded.
                    if (loadingMachineName != null && loadingMachineName.equals(machine))
                        return;
                    Machine loaded = MachineController.getInstance().getMachine();
                    // Repopulating the adapter re-selects the active machine: no reload.
                    if (loaded != null && machine.equals(loaded.getName()))
                        return;
                    setUserPressed(false);
                    machineLoaded = true;
                    notifyAction(MachineAction.LOAD_VM, machine);
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mScrollView.setOnScrollChangeListener(new NestedScrollView.OnScrollChangeListener() {
            @Override
            public void onScrollChange(NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                savePendingEditText();
                if (mToolbarGlass != null) {
                    mToolbarGlass.refresh();
                }
                if (mNavGlass != null) {
                    mNavGlass.refresh();
                }
            }
        });

        mStart.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                if (!Config.loadNativeLibsEarly && Config.loadNativeLibsMainThread) {
                    setupNativeLibs();
                }
                Thread thread = new Thread(new Runnable() {
                    public void run() {
                        if (!Config.loadNativeLibsEarly && !Config.loadNativeLibsMainThread) {
                            setupNativeLibs();
                        }
                        onStartButton();
                    }
                });
                thread.setPriority(Thread.MIN_PRIORITY);
                thread.start();
            }
        });
        mPause.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                onPauseButton();
            }
        });
        mStop.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                onStopButton(false);
            }
        });
        mRestart.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                onRestartButton();
            }
        });
    }

    private void onPauseButton() {
        if (MachineController.getInstance().isRunning()) {
            if (MachineController.getInstance().isVNCEnabled())
                LimboActivityCommon.promptPause(this, viewListener);
            else {
                LimboSDLActivity.pendingPause = true;
                startSDL();
            }
        }

    }

    private void savePendingEditText() {
        View currentView = getCurrentFocus();
        if (currentView instanceof EditText) {
            currentView.setFocusable(false);
        }
    }

    private void checkFirstLaunch() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                if (LimboSettingsManager.isFirstLaunch(LimboActivity.this)) {
                    onFirstLaunch();
                }
            }
        });
        t.start();
    }

    private void checkLog() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                // While the VM is alive the stored exit code is intentionally
                // EXIT_UNKNOWN (it is only finalized when QEMU exits). Prompting here
                // made every tap on a machine card in the home screen show the crash
                // log dialog again.
                if (MachineController.getInstance().isRunning())
                    return;
                if (LimboSettingsManager.getExitCode(LimboActivity.this) != Config.EXIT_SUCCESS) {
                    LimboSettingsManager.setExitCode(LimboActivity.this, Config.EXIT_SUCCESS);
                    // Starting a machine straight from its card should not be
                    // interrupted by a modal dialog; the log is still available from
                    // the menu/settings.
                    if (pendingAutoStart)
                        return;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            com.max2idea.android.limbo.log.Logger.promptShowLog(LimboActivity.this);
                        }
                    });
                }
            }
        });
        t.start();
    }
    //XXX: this needs to be called from the main thread otherwise
    //  qemu crashes when it is started later
    private final StringBuilder diagLog = new StringBuilder();

    protected void saveDiagnostic(String name, String content, boolean append) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                // MediaStore: write to public Downloads without storage permission
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS);
                android.net.Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                    os.write(content.getBytes());
                    os.close();
                }
            } else {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(
                        "/sdcard/Download/" + name, append);
                fos.write(content.getBytes());
                fos.close();
            }
        } catch (Exception e) {
            try {
                Log.e("LimboDiag", "saveDiagnostic failed: " + e);
            } catch (Exception ignored) {
            }
        }
    }

    protected void logNative(String msg) {
        diagLog.append(msg).append("\n");
        // File logging DISABLED (was flooding Downloads with limbo_load_*.txt).
        // Progress still visible in logcat under the LimboDiag tag.
        Log.e("LimboDiag", msg);
    }

    public void setupNativeLibs() {
        if (libLoaded)
            return;
        // A downloadable QEMU build takes precedence over the bundled one. Its
        // libraries must be mapped before any APK glib/SDL copy is loaded.
        try {
            com.max2idea.android.limbo.qemu.QemuVersion active =
                    com.max2idea.android.limbo.qemu.QemuVersionManager.getActive(this);
            if (active != null && !active.isBuiltin()) {
                try {
                    loadExternalQemuVersion(active);
                    libLoaded = true;
                    return;
                } catch (Throwable t) {
                    logNative("external QEMU failed: " + t);
                    try {
                        LimboSettingsManager.setLastVmError(this,
                                getString(R.string.qemu_load_failed) + "\n" + t);
                    } catch (Throwable ignored) {
                    }
                    com.max2idea.android.limbo.qemu.QemuVersionManager.setActive(this,
                            com.max2idea.android.limbo.qemu.QemuVersion.BUILTIN_ID);
                    try {
                        ToastUtils.toastLong(this, getString(R.string.qemu_load_failed));
                        Thread.sleep(900);
                    } catch (Exception ignored) {
                    }
                    // libs may be half-mapped: a clean process restart is the only
                    // safe way back to the bundled build.
                    android.os.Process.killProcess(android.os.Process.myPid());
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            //Compatibility lib
            logNative("loading compat-limbo");
            System.loadLibrary("compat-limbo");
            logNative("OK compat-limbo");

            //Glib deps
            logNative("loading compat-musl");
            System.loadLibrary("compat-musl");
            logNative("OK compat-musl");

            //Glib
            logNative("loading glib-2.0");
            System.loadLibrary("glib-2.0");
            logNative("OK glib-2.0");

            //Pixman for qemu
            logNative("loading pixman-1");
            System.loadLibrary("pixman-1");
            logNative("OK pixman-1");

            // SDL library
            if (Config.enable_SDL) {
                if (Build.VERSION.SDK_INT >= 26)
                    System.loadLibrary("compat-SDL2-addons");
                logNative("loading SDL2");
                System.loadLibrary("SDL2");
                logNative("OK SDL2");
            }

            logNative("loading compat-SDL2-ext");
            System.loadLibrary("compat-SDL2-ext");
            logNative("OK compat-SDL2-ext");

            //Limbo needed for vmexecutor
            logNative("loading limbo");
            System.loadLibrary("limbo");
            logNative("OK limbo");
        } catch (Throwable e) {
            logNative("CRASH: " + e.toString());
            if (e instanceof Error) {
                throw (Error) e;
            }
            throw (RuntimeException) e;
        }

        // qemu arch specific lib
        loadQEMULib();

        libLoaded = true;
    }

    /**
     * Maps an installed QEMU build from app storage. Only works when the installed
     * APK targets API <= 28 (W^X/linker namespace restriction on newer targets).
     */
    private void loadExternalQemuVersion(com.max2idea.android.limbo.qemu.QemuVersion version)
            throws Throwable {
        logNative("loading external QEMU " + version.version + " from " + version.dir);
        System.loadLibrary("compat-limbo");
        System.loadLibrary("compat-musl");
        if (Config.enable_SDL) {
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    System.loadLibrary("compat-SDL2-addons");
                }
            } catch (Throwable t) {
                logNative("compat-SDL2-addons failed: " + t);
            }
        }
        // Libraries that stay in the APK: the emulator display/2D stack and
        // the Android compatibility shims. Preload them so the DT_NEEDED
        // entries of the downloaded QEMU resolve against the already mapped
        // copies instead of relying on the linker search path.
        try {
            System.loadLibrary("pixman-1");
            logNative("OK pixman-1");
        } catch (Throwable t) {
            logNative("pixman-1 failed: " + t);
        }
        if (Config.enable_SDL) {
            try {
                System.loadLibrary("SDL2");
                logNative("OK SDL2");
            } catch (Throwable t) {
                logNative("SDL2 failed: " + t);
            }
        }
        java.io.File libDir = version.libDir();
        if (libDir == null || !libDir.isDirectory()) {
            throw new java.io.IOException("version directory missing");
        }
        for (String name : version.loadOrder) {
            if (name.equals(version.qemuLib)) {
                continue;
            }
            java.io.File lib = new java.io.File(libDir, name);
            if (lib.isFile()) {
                System.load(lib.getAbsolutePath());
                logNative("OK " + name);
            }
        }
        java.io.File qemu = new java.io.File(libDir, version.qemuLib);
        if (!qemu.isFile()) {
            throw new java.io.IOException("qemu library missing: " + qemu);
        }
        System.load(qemu.getAbsolutePath());
        logNative("OK " + version.qemuLib);
        System.loadLibrary("compat-SDL2-ext");
        System.loadLibrary("limbo");
        logNative("OK limbo");
    }

    protected void loadQEMULib() {

    }

    public void setupToolbar() {
        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);

        final ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayShowHomeEnabled(false);
            ab.setDisplayHomeAsUpEnabled(false);
            ab.setDisplayShowCustomEnabled(false);
            ab.setDisplayShowTitleEnabled(true);
            ab.setTitle(pendingMachineName != null ? getString(R.string.config_title) : getString(R.string.app_short_name));
            Toolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null && pendingMachineName != null) {
                toolbar.setNavigationIcon(R.drawable.ic_back);
                if (toolbar.getNavigationIcon() != null) {
                    toolbar.getNavigationIcon().setTint(
                            ContextCompat.getColor(this, R.color.limbo_text));
                }
                toolbar.setNavigationContentDescription(R.string.back);
                toolbar.setNavigationOnClickListener(new OnClickListener() {
                    public void onClick(View view) {
                        finish();
                    }
                });
            }
        }
    }

    public void checkUpdate() {
        Thread tsdl = new Thread(new Runnable() {
            public void run() {
                UpdateChecker.checkNewVersion(LimboActivity.this);
            }
        });
        tsdl.start();
    }

    private void setupStrictMode() {
        if (Config.debugStrictMode) {
            StrictMode.setThreadPolicy(
                    new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork()
                            .penaltyLog().build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects().penaltyLog()
                    .build());
        }
    }

    /**
     * Configuration pages always open for a concrete machine. Load it before the
     * first frame is drawn instead of waiting for the background spinner population,
     * otherwise the page briefly shows the default (empty) configuration.
     */
    private void prepareConfigMachine() {
        if (pendingMachineName == null) {
            return;
        }
        Machine stored = MachineController.getInstance().getMachineConfig(pendingMachineName);
        if (stored == null) {
            pendingMachineName = null;
            pendingAutoStart = false;
            ToastUtils.toastShort(this, getString(R.string.machine_not_found));
            finish();
            return;
        }
        loadingMachineName = pendingMachineName;
        populateMachinesSync();
        showConfigLoading(true);
        notifyAction(MachineAction.LOAD_VM, loadingMachineName);
        // Failsafe: never leave the page hidden if the model never answers.
        if (mScrollView != null) {
            mScrollView.postDelayed(new Runnable() {
                public void run() {
                    if (loadingMachineName != null) {
                        loadingMachineName = null;
                        hideConfigLoading();
                    }
                }
            }, 6000);
        }
    }

    private void populateMachinesSync() {
        ArrayList<String> machinesList = ArchDefinitions.getMachineValues(this);
        machinesList.addAll(MachineController.getInstance().getStoredMachines());
        ArrayAdapter<String> machineAdapter = new ArrayAdapter<>(this,
                R.layout.custom_spinner_item, machinesList);
        machineAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mMachine.setAdapter(machineAdapter);
        mMachine.invalidate();
        String target = pendingMachineName;
        if (target == null && MachineController.getInstance().getMachine() != null) {
            target = MachineController.getInstance().getMachine().getName();
        }
        if (target != null) {
            SpinnerAdapter.setDiskAdapterValue(mMachine, target);
        }
    }

    private void showConfigLoading(boolean show) {
        if (mScrollView == null) {
            return;
        }
        View loading = findViewById(R.id.config_loading);
        if (loading == null) {
            return;
        }
        loading.animate().cancel();
        if (show) {
            loading.setAlpha(1f);
            loading.setVisibility(View.VISIBLE);
            mScrollView.setAlpha(0f);
        }
    }

    private void hideConfigLoading() {
        if (mScrollView == null) {
            return;
        }
        final View loading = findViewById(R.id.config_loading);
        if (loading != null) {
            loading.animate().cancel();
            loading.animate().alpha(0f).setDuration(180).withEndAction(new Runnable() {
                public void run() {
                    View current = findViewById(R.id.config_loading);
                    if (current != null) {
                        current.setVisibility(View.GONE);
                    }
                }
            }).start();
        }
        mScrollView.animate().cancel();
        mScrollView.setAlpha(0f);
        mScrollView.animate().alpha(1f).setDuration(240).start();
        refreshGlassLater(320);
    }

    private String getDriveValue(FileType fileType) {
        Machine machine = getMachine();
        if (machine == null) {
            return null;
        }
        switch (fileType) {
            case CDROM:
                return machine.getCdImagePath();
            case FDA:
                return machine.getFdaImagePath();
            case FDB:
                return machine.getFdbImagePath();
            case SD:
                return machine.getSdImagePath();
            case HDA:
                return machine.getHdaImagePath();
            case HDB:
                return machine.getHdbImagePath();
            case HDC:
                return machine.getHdcImagePath();
            case HDD:
                return machine.getHddImagePath();
            case SHARED_DIR:
                return machine.getSharedFolderPath();
            case KERNEL:
                return machine.getKernel();
            case INITRD:
                return machine.getInitRd();
            default:
                return null;
        }
    }

    private void populateAttributesUI() {
        if (loadingMachineName == null) {
            populateMachines(null);
        }
        populateMachineType(null);
        populateCPUs(null);
        populateCPUNum();
        populateRAM();
        populateDisks();
        populateBootDevices();
        populateNet();
        populateNetDevices(null);
        populateVGA();
        populateFirmware();
        populateSoundcardConfig();
        populateUI();
        populateKeyboardLayout();
        populateMouse();
    }

    private void populateDisks() {

        //disks
        populateDiskAdapter(mHDA, FileType.HDA, true);
        populateDiskAdapter(mHDB, FileType.HDB, true);
        populateDiskAdapter(mHDC, FileType.HDC, true);
        populateDiskAdapter(mHDD, FileType.HDD, true);
        populateDiskAdapter(mSharedFolder, FileType.SHARED_DIR, false);

        //removables drives
        populateDiskAdapter(mCD, FileType.CDROM, false);
        populateDiskAdapter(mFDA, FileType.FDA, false);
        populateDiskAdapter(mFDB, FileType.FDB, false);
        populateDiskAdapter(mSD, FileType.SD, false);

        //boot
        populateDiskAdapter(mKernel, FileType.KERNEL, false);
        populateDiskAdapter(mInitrd, FileType.INITRD, false);

    }

    public void onFirstLaunch() {
        promptLicense();
    }

    private void createMachine(String machineName) {
        notifyAction(MachineAction.CREATE_VM, machineName);
    }

    private void machineCreated() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showOperatingSystems();
                populateMachines(getMachine().getName());
                enableNonRemovableDeviceOptions(true);
                enableRemovableDeviceOptions(true);
                setArchOptions();
            }
        });
    }

    protected void showOperatingSystems() {
        if (!Config.osImages.isEmpty()) {
            LinksManager manager = new LinksManager(this);
            manager.show();
        }
    }

    private void onDeleteMachine() {
        if (getMachine() == null) {
            ToastUtils.toastShort(this, getString(R.string.SelectAMachineFirst));
            return;
        }
        Thread t = new Thread(new Runnable() {
            public void run() {
                final String name = getMachine().getName();
                notifyAction(MachineAction.DELETE_VM, getMachine());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        disableListeners();
                        disableRemovableDiskListeners();
                        mMachine.setSelection(0);
                        notifyAction(MachineAction.LOAD_VM, null);
                        populateAttributesUI();
                        ToastUtils.toastShort(LimboActivity.this, getString(R.string.MachineDeleted) + ": " + name);
                        setupMiscOptions();
                        setupNonRemovableDiskListeners();
                        enableRemovableDiskListeners();
                    }
                });
            }
        });
        t.start();

    }

    public void importMachines(String importFilePath) {
        disableListeners();
        disableRemovableDiskListeners();
        mMachine.setSelection(0);
        notifyAction(MachineAction.IMPORT_VMS, importFilePath);
    }


    private void promptLicense() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    LimboActivityCommon.promptLicense(LimboActivity.this,
                            Config.APP_NAME + " " + LimboApplication.getLimboVersionString()
                            + " " + "QEMU" + " " + LimboApplication.getQemuVersionString() ,
                            FileUtils.LoadFile(LimboActivity.this, "LICENSE", false));
                } catch (IOException e) {

                    e.printStackTrace();
                }
            }
        });

    }

    public void exit() {
        if (MachineController.getInstance().isRunning()) {
            onStopButton(true);
            return;
        }
        // IMPORTANT: do NOT use System.exit(0) here.
        // System.exit() synchronously runs __cxa_finalize for every loaded
        // library, which calls pthread_mutex_destroy() on static mutexes of
        // libhwui.so / libgrallocutils.so while the UI and render threads are
        // still alive. Those threads then hit
        //   "FORTIFY: pthread_mutex_lock called on a destroyed mutex"
        // and the app aborts (SIGABRT) -> looks like a random crash on exit.
        // Finishing the task and SIGKILLing ourselves unloads the native libs
        // without running any destructors.
        try {
            finishAndRemoveTask();
        } catch (Throwable t) {
            finish();
        }
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private void unlockRemovableDevices(boolean flag) {
        mCDenable.setEnabled(flag);
        mFDAenable.setEnabled(flag);
        mFDBenable.setEnabled(flag);
        mSDenable.setEnabled(flag);
    }

    private void enableRemovableDeviceOptions(boolean flag) {
        unlockRemovableDevices(flag);
        enableRemovableDiskValues(flag);
    }

    private void enableRemovableDiskValues(boolean flag) {
        mCD.setEnabled(flag && mCDenable.isChecked());
        mFDA.setEnabled(flag && mFDAenable.isChecked());
        mFDB.setEnabled(flag && mFDBenable.isChecked());
        mSD.setEnabled(flag && mSDenable.isChecked());
    }

    private void enableNonRemovableDeviceOptions(boolean flag) {
        if (MachineController.getInstance().isRunning())
            flag = false;

        //ui
        mUI.setEnabled(flag);
        mKeyboard.setEnabled(Config.enableKeyboardLayoutOption && flag);
        mMouse.setEnabled(Config.enableMouseOption && flag);

        // Enable everything except removable devices
        mMachineType.setEnabled(flag);
        mCPU.setEnabled(flag);
        mCPUNum.setEnabled(flag);
        mRamSize.setEnabled(flag);
        mEnableKVM.setEnabled(flag && Config.enableKVM);
        mEnableMTTCG.setEnabled(flag && Config.enableMTTCG);

        //drives
        mHDA.setEnabled(flag);
        mHDAOptions.setEnabled(flag);
        mHDB.setEnabled(flag);
        mHDBOptions.setEnabled(flag);
        mHDC.setEnabled(flag);
        mHDCOptions.setEnabled(flag);
        mHDD.setEnabled(flag);
        mHDDOptions.setEnabled(flag);
        mSharedFolder.setEnabled(flag);

        //boot
        mBootDevices.setEnabled(flag);
        if (mFirmware != null) {
            mFirmware.setEnabled(flag);
        }
        mKernel.setEnabled(flag);
        mInitrd.setEnabled(flag);
        mAppend.setEnabled(flag);

        //graphics
        mVGAConfig.setEnabled(flag);

        //audio
        if (Config.enableSDLSound && getMachine() != null
                && getMachine().getEnableVNC() != 1
                && getMachine().getPaused() == 0)
            mSoundCard.setEnabled(flag);
        else
            mSoundCard.setEnabled(false);

        //net
        mNetConfig.setEnabled(flag);
        mNetworkCard.setEnabled(flag && mNetConfig.getSelectedItemPosition() > 0);
        mDNS.setEnabled(flag && mNetConfig.getSelectedItemPosition() > 0);
        mHOSTFWD.setEnabled(flag && mNetConfig.getSelectedItemPosition() > 0);

        //advanced
        mDisableACPI.setEnabled(flag);
        mDisableHPET.setEnabled(flag);
        mDisableTSC.setEnabled(flag);
        mExtraParams.setEnabled(flag);

    }

    // Main event function
    // Retrives values from saved preferences
    private void onStartButton() {

        if (getMachine() == null) {
            ToastUtils.toastShort(LimboActivity.this, getString(R.string.SelectOrCreateVirtualMachineFirst));
            return;
        }
        // QEMU opens the images from shared storage; without the runtime grant the
        // disk paths look missing and the VM exits before it can show anything.
        if (!hasStoragePermission()) {
            ToastUtils.toastLong(LimboActivity.this, getString(R.string.storage_permission_needed));
            runOnUiThread(new Runnable() {
                public void run() {
                    promptStoragePermissionMissing();
                }
            });
            return;
        }
        // focus out of edit texts to make sure they are applied to the db
        mStart.requestFocus();

        if (!validateFiles()) {
            return;
        }
        warnIfNoBootMedia();

        try {
            createMachineDir(MachineController.getInstance().getMachineSaveDir());
        } catch (Exception ex) {
            ToastUtils.toastLong(LimboActivity.this, getString(R.string.Error) + ": " + ex);
            return;
        }

        // XXX: save the user defined dns server before we start the vm
        LimboSettingsManager.setDNSServer(this, mDNS.getText().toString());

        //XXX: make sure that bios files are installed in case we ran out of space in the last run
        FileInstaller.installFiles(LimboActivity.this, false);

        if (getMachine().getEnableVNC() == 1) {
            startVNC();
        } else {
            startSDL();
        }
    }

    private void createMachineDir(String dir) throws Exception {
        File destDir = new File(dir);
        if (!destDir.exists()) {
            if (!destDir.mkdirs())
                throw new Exception(getString(R.string.failToCreateMachineDirError));
        }
    }

    /**
     * Starts the SDL Activity that will later start the native process via the service.
     * This is done so that the java SDL part is initialized prior to starting the vm.
     */
    public void startSDL() {
        Intent intent = new Intent(LimboActivity.this, LimboSDLActivity.class);
        startActivityForResult(intent, Config.SDL_REQUEST_CODE);
    }

    /**
     * Start the vm with VNC Suport via the Controller which will later call the native process
     * via the service. We do this since we don't have a built-in VNC client anymore.
     */
    public void startVNC() {
        if (LimboSettingsManager.getEnableExternalVNC(this)) {
            // VNC external connections
            LimboActivityCommon.promptVNCServer(this,
                    getString(R.string.ExternalVNCEnabledWarning), viewListener);
        } else if (!LimboSettingsManager.getVNCEnablePassword(this)) {
            // VNC Password is not enabled
            LimboActivityCommon.promptVNCServer(this,
                    getString(R.string.VNCPasswordNotEnabledWarning), viewListener);
        } else if (LimboSettingsManager.getVNCEnablePassword(this)
                && LimboSettingsManager.getVNCPass(this) == null) {
            // VNC Password is missing
            ToastUtils.toastShort(this, getString(R.string.VNCPasswordMissing));
        } else {
            notifyAction(MachineAction.START_VM, null);
        }
    }

    private boolean isRealMedia(String path) {
        return path != null && path.length() > 0 && !"None".equals(path);
    }

    /** A machine whose drives were wiped by an old UI bug would otherwise just show a black screen. */
    private void warnIfNoBootMedia() {
        Machine machine = getMachine();
        if (machine == null) {
            return;
        }
        boolean hasMedia = isRealMedia(machine.getHdaImagePath())
                || isRealMedia(machine.getHdbImagePath())
                || isRealMedia(machine.getHdcImagePath())
                || isRealMedia(machine.getHddImagePath())
                || isRealMedia(machine.getCdImagePath())
                || isRealMedia(machine.getFdaImagePath())
                || isRealMedia(machine.getFdbImagePath())
                || isRealMedia(machine.getSdImagePath())
                || isRealMedia(machine.getKernel());
        if (!hasMedia) {
            ToastUtils.toastLong(this, getString(R.string.no_boot_media_warning));
        }
    }

    private boolean validateFiles() {
        String[] paths = new String[]{
                getMachine().getHdaImagePath(),
                getMachine().getHdbImagePath(),
                getMachine().getHdcImagePath(),
                getMachine().getHddImagePath(),
                getMachine().getFdaImagePath(),
                getMachine().getFdbImagePath(),
                getMachine().getSdImagePath(),
                getMachine().getCdImagePath(),
                getMachine().getKernel(),
                getMachine().getInitRd()
        };
        for (String path : paths) {
            if (path == null || path.length() == 0 || "None".equals(path)) {
                continue;
            }
            if (!FileUtils.fileValid(path)) {
                // Never fail silently: the user has to know why Start did nothing.
                String message = getString(R.string.drive_file_missing, path);
                ToastUtils.toastLong(this, message);
                try {
                    LimboSettingsManager.setLastVmError(this, message);
                } catch (Throwable ignored) {
                }
                return false;
            }
        }
        return true;
    }

    private void onStopButton(boolean exitApp) {
        KeyboardUtils.hideKeyboard(this, mScrollView);
        if (MachineController.getInstance().isRunning()) {
            if (MachineController.getInstance().isVNCEnabled())
                LimboActivityCommon.promptStopVM(this, viewListener);
            else {
                LimboSDLActivity.pendingStop = true;
                startSDL();
            }
        } else {
            if (getMachine() != null
                    && MachineController.getInstance().isPaused() && !exitApp) {
                promptDiscardVMState();
            } else {
                ToastUtils.toastShort(LimboActivity.this, getString(R.string.vmNotRunning));
            }
        }
    }

    private void onRestartButton() {
        if (!MachineController.getInstance().isRunning()) {
            if (getMachine() != null && getMachine().getPaused() == 1) {
                promptDiscardVMState();
            } else {
                ToastUtils.toastShort(LimboActivity.this, getString(R.string.VMNotRunning));
            }
        }
        LimboActivityCommon.promptResetVM(this, viewListener);
    }

    public void toggleSectionVisibility(View view) {
        if (view.getVisibility() == View.VISIBLE) {
            view.setVisibility(View.GONE);
        } else if (view.getVisibility() == View.GONE || view.getVisibility() == View.INVISIBLE) {
            view.setVisibility(View.VISIBLE);
        }
    }

    public void setupWidgets() {
        setupSections();
        mScrollView = findViewById(R.id.scroll_view);
        mStatus = findViewById(R.id.statusVal);
        mStatus.setImageResource(R.drawable.off);
        mStatusText = findViewById(R.id.statusStr);

        mStart = findViewById(R.id.startvm);
        mPause = findViewById(R.id.pausevm);
        mStop = findViewById(R.id.stopvm);
        mRestart = findViewById(R.id.restartvm);

        //Machine
        mMachine = findViewById(R.id.machineval);
        if (MachineController.getInstance().isRunning())
            mMachine.setEnabled(false);

        //ui
        if (!Config.enable_SDL)
            mUI.setEnabled(false);

        mKeyboard = findViewById(R.id.keyboardval);
        mMouse = findViewById(R.id.mouseval);

        //cpu/board
        mCPU = findViewById(R.id.cpuval);
        mMachineType = findViewById(R.id.machinetypeval);
        mCPUNum = findViewById(R.id.cpunumval);
        mUI = findViewById(R.id.uival);
        mRamSize = findViewById(R.id.rammemval);
        mEnableKVM = findViewById(R.id.enablekvmval);
        mEnableMTTCG = findViewById(R.id.enablemttcgval);
        mDisableACPI = findViewById(R.id.acpival);
        mDisableHPET = findViewById(R.id.hpetval);
        mDisableTSC = findViewById(R.id.tscval);

        //disks
        mHDA = findViewById(R.id.hdaimgval);
        mHDAOptions = findViewById(R.id.hdaoptions);
        mHDB = findViewById(R.id.hdbimgval);
        mHDBOptions = findViewById(R.id.hdboptions);
        mHDC = findViewById(R.id.hdcimgval);
        mHDCOptions = findViewById(R.id.hdcoptions);
        mHDD = findViewById(R.id.hddimgval);
        mHDDOptions = findViewById(R.id.hddoptions);

        LinearLayout sharedFolderLayout = findViewById(R.id.sharedfolderl);
        if (!Config.enableSharedFolder)
            sharedFolderLayout.setVisibility(View.GONE);
        mSharedFolder = findViewById(R.id.sharedfolderval);

        //Removable storage
        mCD = findViewById(R.id.cdromimgval);
        mFDA = findViewById(R.id.floppyimgval);
        mFDB = findViewById(R.id.floppybimgval);
        mCDOptions = findViewById(R.id.cdromoptions);
        if (!Config.enableEmulatedFloppy) {
            LinearLayout mFDALayout = findViewById(R.id.floppyimgl);
            mFDALayout.setVisibility(View.GONE);
            LinearLayout mFDBLayout = findViewById(R.id.floppybimgl);
            mFDBLayout.setVisibility(View.GONE);
        }
        mSD = findViewById(R.id.sdcardimgval);
        if (!Config.enableEmulatedSDCard) {
            LinearLayout mSDCardLayout = findViewById(R.id.sdcardimgl);
            mSDCardLayout.setVisibility(View.GONE);
        }
        mCDenable = findViewById(R.id.cdromimgcheck);
        mFDAenable = findViewById(R.id.floppyimgcheck);
        mFDBenable = findViewById(R.id.floppybimgcheck);
        mSDenable = findViewById(R.id.sdcardimgcheck);

        //boot
        mBootDevices = findViewById(R.id.bootfromval);
        mFirmware = findViewById(R.id.firmwareval);
        mKernel = findViewById(R.id.kernelval);
        mInitrd = findViewById(R.id.initrdval);
        mAppend = findViewById(R.id.appendval);

        //display
        mVGAConfig = findViewById(R.id.vgacfgval);

        //sound
        mSoundCard = findViewById(R.id.soundcfgval);

        //network
        mNetConfig = findViewById(R.id.netcfgval);
        mNetworkCard = findViewById(R.id.netDevicesVal);
        mDNS = findViewById(R.id.dnsval);
        setDefaultDNServer();
        mHOSTFWD = findViewById(R.id.hostfwdval);

        // advanced
        mExtraParams = findViewById(R.id.extraparamsval);

        disableFeatures();
        enableRemovableDeviceOptions(false);
        enableNonRemovableDeviceOptions(false);
    }

    private void disableFeatures() {

        LinearLayout mAudioSectionLayout = findViewById(R.id.audiosectionl);
        if (!Config.enableSDLSound) {
            mAudioSectionLayout.setVisibility(View.GONE);
        }

        LinearLayout mDisableTSCLayout = findViewById(R.id.tscl);
        LinearLayout mDisableACPILayout = findViewById(R.id.acpil);
        LinearLayout mDisableHPETLayout = findViewById(R.id.hpetl);
        LinearLayout mEnableKVMLayout = findViewById(R.id.kvml);

        if (LimboApplication.arch != Config.Arch.x86 && LimboApplication.arch != Config.Arch.x86_64) {
            mDisableTSCLayout.setVisibility(View.GONE);
            mDisableACPILayout.setVisibility(View.GONE);
            mDisableHPETLayout.setVisibility(View.GONE);
        }
        if (LimboApplication.arch != Config.Arch.x86 && LimboApplication.arch != Config.Arch.x86_64
                && LimboApplication.arch != Config.Arch.arm && LimboApplication.arch != Config.Arch.arm64) {
            mEnableKVMLayout.setVisibility(View.GONE);
        }
    }

    private void setDefaultDNServer() {

        Thread thread = new Thread(new Runnable() {
            public void run() {
                final String defaultDNSServer = LimboSettingsManager.getDNSServer(LimboActivity.this);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        // Code here will run in UI thread
                        mDNS.setText(defaultDNSServer);
                    }
                });
            }
        });
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();

    }

    // ============================================================
    // Aurora Glass UI
    // ============================================================

    private void setupHome() {
        homeController = new HomeController(this);
        homeController.setup();
        MachineController.getInstance().addOnStatusChangeListener(homeController);
        MachineController.getInstance().addOnEventListener(homeController);
    }

    private void setupModernUI() {
        View background = findViewById(R.id.config_background);
        if (background instanceof AppBackgroundView) {
            String wallpaper = UiPrefs.getWallpaper(this);
            ((AppBackgroundView) background).setWallpaperUri(
                    wallpaper == null ? null : Uri.parse(wallpaper));
        }
        mStatusPill = findViewById(R.id.status_pill);
        mStatusDetail = findViewById(R.id.status_detail);
        setupSteplessSliders();
        applyStatusVisuals(MachineController.getInstance().getCurrStatus());
        updateMachineHeader();
    }

    private void updateMachineHeader() {
        final Machine machine = getMachine();
        if (machine == null) {
            return;
        }
        TextView name = findViewById(R.id.machineStr);
        TextView meta = findViewById(R.id.machine_meta);
        ImageView icon = findViewById(R.id.machine_icon);
        TextView letter = findViewById(R.id.machine_letter);
        if (name != null) {
            name.setText(machine.getName());
        }
        if (meta != null) {
            meta.setText(getString(R.string.machine_meta,
                    UiUtils.formatMemory(machine.getMemory()), machine.getCpuNum()));
        }
        String iconUri = UiPrefs.getMachineIcon(this, machine.getName());
        Bitmap bitmap = iconUri == null ? null
                : UiUtils.loadSampled(this, Uri.parse(iconUri), 160);
        if (icon != null && letter != null) {
            icon.setClipToOutline(true);
            if (bitmap != null) {
                icon.setImageBitmap(bitmap);
                icon.setVisibility(View.VISIBLE);
                letter.setVisibility(View.GONE);
            } else {
                icon.setVisibility(View.GONE);
                letter.setVisibility(View.VISIBLE);
                letter.setText(UiUtils.firstLetter(machine.getName()));
            }
        }
    }

    private void refreshGlassLater(long delay) {
        if (mScrollView == null) {
            return;
        }
        mScrollView.postDelayed(new Runnable() {
            public void run() {
                if (mToolbarGlass != null) {
                    mToolbarGlass.refresh();
                }
                if (mNavGlass != null) {
                    mNavGlass.refresh();
                }
            }
        }, delay);
    }

    private void setupSteplessSliders() {
        mCpuSlider = findViewById(R.id.cpunum_slider);
        mCpuValue = findViewById(R.id.cpunum_value);
        mRamSlider = findViewById(R.id.rammem_slider);
        mRamValue = findViewById(R.id.rammem_value);
        if (mCpuSlider == null || mRamSlider == null) {
            return;
        }

        mCpuSlider.setRange(1, Math.max(1, mCPUNum != null ? mCPUNum.getCount() : Config.MAX_CPU_NUM), 1);
        mCpuSlider.setOnValueChangedListener(new SteplessSlider.OnValueChangedListener() {
            public void onValueChanged(int value, boolean fromUser) {
                if (mCpuValue != null) {
                    mCpuValue.setText(getString(R.string.coresValue, value));
                }
                if (!fromUser || mSyncingSliders || mCPUNum == null) {
                    return;
                }
                final int index = value - 1;
                if (index >= 0 && index < mCPUNum.getCount()
                        && mCPUNum.getSelectedItemPosition() != index) {
                    mCPUNum.setSelection(index);
                }
            }

            public void onValueCommitted(int value) {
            }
        });

        mRamSlider.setRange(0, Math.max(8, mRamSize != null ? (mRamSize.getCount() - 1) * 8 : 8184), 8);
        mRamSlider.setOnValueChangedListener(new SteplessSlider.OnValueChangedListener() {
            public void onValueChanged(int value, boolean fromUser) {
                if (mRamValue != null) {
                    mRamValue.setText(formatRam(value));
                }
            }

            public void onValueCommitted(int value) {
                if (mSyncingSliders || mRamSize == null) {
                    return;
                }
                final int index = ramToIndex(value);
                if (index >= 0 && index < mRamSize.getCount()
                        && mRamSize.getSelectedItemPosition() != index) {
                    mRamSize.setSelection(index);
                }
            }
        });

        syncSlidersFromMachine();
    }

    private int ramToIndex(int mb) {
        return mb <= 0 ? 0 : Math.round(mb / 8f);
    }

    private String formatRam(int mb) {
        if (mb <= 0) {
            return getString(R.string.ramValueMb, 4);
        }
        if (mb >= 1024) {
            return getString(R.string.ramValueGb, mb / 1024f);
        }
        return getString(R.string.ramValueMb, mb);
    }

    private void syncCpuSliderFromSpinner() {
        if (mCpuSlider == null || mCPUNum == null || mSyncingSliders) {
            return;
        }
        final int position = mCPUNum.getSelectedItemPosition();
        if (position < 0) {
            return;
        }
        mSyncingSliders = true;
        try {
            final int cores = position + 1;
            mCpuSlider.setValue(cores);
            if (mCpuValue != null) {
                mCpuValue.setText(getString(R.string.coresValue, cores));
            }
        } finally {
            mSyncingSliders = false;
        }
    }

    private void syncRamSliderFromSpinner() {
        if (mRamSlider == null || mRamSize == null || mSyncingSliders) {
            return;
        }
        final int position = mRamSize.getSelectedItemPosition();
        if (position < 0) {
            return;
        }
        mSyncingSliders = true;
        try {
            final int mb = position <= 0 ? 0 : position * 8;
            mRamSlider.setValue(mb);
            if (mRamValue != null) {
                mRamValue.setText(formatRam(mb));
            }
        } finally {
            mSyncingSliders = false;
        }
    }

    private void syncSlidersFromMachine() {
        if (mSyncingSliders) {
            return;
        }
        syncCpuSliderFromSpinner();
        syncRamSliderFromSpinner();
    }

    private void selectTab(int index) {
        final LinearLayout[] tabs = {mTabOverview, mTabHardware, mTabStorage, mTabDisplay, mTabNetwork};
        if (index < 0 || index >= tabs.length) {
            return;
        }
        mCurrentTab = index;
        for (int i = 0; i < tabs.length; i++) {
            final LinearLayout tab = tabs[i];
            if (tab == null) {
                continue;
            }
            if (i == index) {
                tab.setVisibility(View.VISIBLE);
                tab.setAlpha(0f);
                tab.animate().alpha(1f).setDuration(220).start();
            } else {
                tab.setVisibility(View.GONE);
            }
        }
        updateNavState();
        if (mScrollView != null) {
            mScrollView.scrollTo(0, 0);
        }
        if (mToolbarGlass != null) {
            mToolbarGlass.refresh();
        }
        if (mNavGlass != null) {
            mNavGlass.refresh();
        }
    }

    private void updateNavState() {
        final int activeColor = ContextCompat.getColor(this, R.color.limbo_accent);
        final int inactiveColor = ContextCompat.getColor(this, R.color.limbo_text_muted);
        for (int i = 0; i < mNavRoots.length; i++) {
            final boolean active = i == mCurrentTab;
            if (mNavIcons[i] != null) {
                mNavIcons[i].setBackgroundResource(active ? R.drawable.bg_nav_active : 0);
                mNavIcons[i].setColorFilter(active ? activeColor : inactiveColor);
                mNavIcons[i].animate().scaleX(active ? 1.12f : 1f).scaleY(active ? 1.12f : 1f)
                        .setDuration(180).start();
            }
            if (mNavLabels[i] != null) {
                mNavLabels[i].setTextColor(active ? activeColor : inactiveColor);
            }
        }
    }

    private void applyStatusVisuals(MachineStatus status) {
        if (mStatus == null) {
            return;
        }
        final boolean running = MachineController.getInstance().isRunning();
        final boolean paused = status == MachineStatus.Paused;
        final int color = paused ? ContextCompat.getColor(this, R.color.limbo_warning)
                : running ? ContextCompat.getColor(this, R.color.limbo_success)
                : ContextCompat.getColor(this, R.color.limbo_text_muted);

        mStatus.setImageResource(R.drawable.ic_power);
        mStatus.setColorFilter(color);

        if (mStatusPill != null) {
            boolean sdl = mUI == null || mUI.getSelectedItemPosition() != 0;
            mStatusPill.setText(sdl ? "SDL" : "VNC");
            mStatusPill.setTextColor(running
                    ? color : ContextCompat.getColor(this, R.color.limbo_text_dim));
            mStatusPill.setBackgroundResource(running
                    ? R.drawable.bg_chip_accent : R.drawable.bg_chip_neutral);
        }

        if (mStatusDetail != null) {
            final Machine machine = getMachine();
            String detail = machine != null && machine.getName() != null
                    ? machine.getName() : getString(R.string.appSubtitle);
            if (running && (mUI == null || mUI.getSelectedItemPosition() != 0)) {
                detail = detail + " · " + getString(R.string.status_sdl_background);
            }
            mStatusDetail.setText(detail);
        }
    }

    private void setupSections() {

        // Summaries are always bound: tab pages show every card expanded.
        mCPUSectionSummary = findViewById(R.id.cpusectionsummaryStr);
        mStorageSectionSummary = findViewById(R.id.storagesectionsummaryStr);
        mUISectionSummary = findViewById(R.id.uisectionsummaryStr);
        mRemovableStorageSectionSummary = findViewById(R.id.removablesectionsummaryStr);
        mGraphicsSectionSummary = findViewById(R.id.graphicssectionsummaryStr);
        mAudioSectionSummary = findViewById(R.id.audiosectionsummaryStr);
        mNetworkSectionSummary = findViewById(R.id.networksectionsummaryStr);
        mBootSectionSummary = findViewById(R.id.bootsectionsummaryStr);
        mAdvancedSectionSummary = findViewById(R.id.advancedsectionsummaryStr);

        if (Config.collapseSections) {
            mCPUSectionDetails = findViewById(R.id.cpusectionDetails);
            mCPUSectionDetails.setVisibility(View.GONE);
            mCPUSectionSummary = findViewById(R.id.cpusectionsummaryStr);
            LinearLayout mCPUSectionHeader = findViewById(R.id.cpusectionheaderl);
            mCPUSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mCPUSectionDetails);
                    enableListenersDelayed();
                }
            });

            mStorageSectionDetails = findViewById(R.id.storagesectionDetails);
            mStorageSectionDetails.setVisibility(View.GONE);
            mStorageSectionSummary = findViewById(R.id.storagesectionsummaryStr);
            LinearLayout mStorageSectionHeader = findViewById(R.id.storageheaderl);
            mStorageSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mStorageSectionDetails);
                    enableListenersDelayed();
                }
            });

            mUserInterfaceSectionDetails = findViewById(R.id.userInterfaceDetails);
            mUserInterfaceSectionDetails.setVisibility(View.GONE);
            mUISectionSummary = findViewById(R.id.uisectionsummaryStr);
            LinearLayout mUserInterfaceSectionHeader = findViewById(R.id.userinterfaceheaderl);
            mUserInterfaceSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mUserInterfaceSectionDetails);
                    enableListenersDelayed();
                }
            });


            mRemovableStorageSectionDetails = findViewById(R.id.removableStoragesectionDetails);
            mRemovableStorageSectionDetails.setVisibility(View.GONE);
            mRemovableStorageSectionSummary = findViewById(R.id.removablesectionsummaryStr);
            LinearLayout mRemovableStorageSectionHeader = findViewById(R.id.removablestorageheaderl);
            mRemovableStorageSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mRemovableStorageSectionDetails);
                    enableListenersDelayed();
                }
            });

            mGraphicsSectionDetails = findViewById(R.id.graphicssectionDetails);
            mGraphicsSectionDetails.setVisibility(View.GONE);
            mGraphicsSectionSummary = findViewById(R.id.graphicssectionsummaryStr);
            LinearLayout mGraphicsSectionHeader = findViewById(R.id.graphicsheaderl);
            mGraphicsSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mGraphicsSectionDetails);
                    enableListenersDelayed();
                }
            });
            mAudioSectionDetails = findViewById(R.id.audiosectionDetails);
            mAudioSectionDetails.setVisibility(View.GONE);
            mAudioSectionSummary = findViewById(R.id.audiosectionsummaryStr);
            LinearLayout mAudioSectionHeader = findViewById(R.id.audioheaderl);
            mAudioSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mAudioSectionDetails);
                    enableListenersDelayed();
                }
            });

            mNetworkSectionDetails = findViewById(R.id.networksectionDetails);
            mNetworkSectionDetails.setVisibility(View.GONE);
            mNetworkSectionSummary = findViewById(R.id.networksectionsummaryStr);
            View mNetworkSectionHeader = findViewById(R.id.networkheaderl);
            mNetworkSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mNetworkSectionDetails);
                    enableListenersDelayed();
                }
            });

            mBootSectionDetails = findViewById(R.id.bootsectionDetails);
            mBootSectionDetails.setVisibility(View.GONE);
            mBootSectionSummary = findViewById(R.id.bootsectionsummaryStr);
            View mBootSectionHeader = findViewById(R.id.bootheaderl);
            mBootSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mBootSectionDetails);
                    enableListenersDelayed();
                }
            });

            mAdvancedSectionDetails = findViewById(R.id.advancedSectionDetails);
            mAdvancedSectionDetails.setVisibility(View.GONE);
            mAdvancedSectionSummary = findViewById(R.id.advancedsectionsummaryStr);
            LinearLayout mAdvancedSectionHeader = findViewById(R.id.advancedheaderl);
            mAdvancedSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mAdvancedSectionDetails);
                    enableListenersDelayed();
                }
            });
        }
    }

    private void enableListenersDelayed() {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                setupMiscOptions();
                setupNonRemovableDiskListeners();
                enableRemovableDiskListeners();
            }
        }, 500);
    }

    public void updateUISummary(boolean clear) {
        if (clear || getMachine() == null || mMachine.getSelectedItemPosition() < 2)
            mUISectionSummary.setText("");
        else {
            String text = getString(R.string.display) + ": " + (getMachine().getEnableVNC() == 1 ? "VNC" : "SDL");
            if (getMachine().getEnableVNC() == 1) {
                text += ", " + getString(R.string.server);
                text += ": " + NetworkUtils.getVNCAddress(this) + ":" + Config.defaultVNCPort;
            }
            if (getMachine().getKeyboard() != null) {
                text += ", " + getString(R.string.keyboard) + ": " + getMachine().getKeyboard();
            }
            if (getMachine().getMouse() != null) {
                text += ", " + getString(R.string.mouse) + ": " + getMachine().getMouse();
            }
            mUISectionSummary.setText(text);
        }
    }

    private Machine getMachine() {
        return MachineController.getInstance().getMachine();
    }

    public void updateCPUSummary(boolean clear) {
        if (clear || getMachine() == null || mMachine.getSelectedItemPosition() < 2)
            mCPUSectionSummary.setText("");
        else {
            String text = getString(R.string.MachineTypeLabel) + " " + getMachine().getMachineType()
                    + ", " + getString(R.string.CpuModelLabel) + getMachine().getCpu()
                    + ", " + getMachine().getCpuNum() + getString(R.string.CpuCoresSuffix)
                    + ((getMachine().getCpuNum() > 1) ? getString(R.string.CpuCoresPlural) : "")
                    + ", " + getMachine().getMemory() + " MB";
            if (mEnableMTTCG.isChecked())
                text = appendOption(getString(R.string.enableMTTCG), text);
            if (mEnableKVM.isChecked())
                text = appendOption(getString(R.string.EnableKVM), text);
            if (mDisableACPI.isChecked())
                text = appendOption(getString(R.string.DisableAcpiLabel), text);
            if (mDisableHPET.isChecked())
                text = appendOption(getString(R.string.DisableHpetLabel), text);
            if (mDisableTSC.isChecked())
                text = appendOption(getString(R.string.DisableTscLabel), text);
            mCPUSectionSummary.setText(text);
        }
    }

    public void updateStorageSummary(boolean clear) {
        if (clear || getMachine() == null || mMachine.getSelectedItemPosition() < 2)
            mStorageSectionSummary.setText("");
        else {
            String text = null;
            text = appendDriveFilename(getMachine().getHdaImagePath(), text, "HDA", false);
            text = appendDriveFilename(getMachine().getHdbImagePath(), text, "HDB", false);
            text = appendDriveFilename(getMachine().getHdcImagePath(), text, "HDC", false);
            text = appendDriveFilename(getMachine().getHddImagePath(), text, "HDD", false);

            if (Config.enableSharedFolder)
                text = appendDriveFilename(getMachine().getSharedFolderPath(), text,
                        getString(R.string.SharedFolder), false);

            if (text == null || text.equals("'"))
                text = getString(R.string.NoneLabel);
            mStorageSectionSummary.setText(text);
        }
    }

    public void updateRemovableStorageSummary(boolean clear) {
        if (clear || getMachine() == null || mMachine.getSelectedItemPosition() < 2)
            mRemovableStorageSectionSummary.setText("");
        else {
            String text = null;

            text = appendDriveFilename(getMachine().getCdImagePath(), text, "CDROM", true);
            text = appendDriveFilename(getMachine().getFdaImagePath(), text, "FDA", true);
            text = appendDriveFilename(getMachine().getFdbImagePath(), text, "FDB", true);
            text = appendDriveFilename(getMachine().getSdImagePath(), text, "SD", true);


            if (text == null || text.equals(""))
                text = getString(R.string.NoneLabel);
            mRemovableStorageSectionSummary.setText(text);
        }
    }

    public void updateBootSummary(boolean clear) {
        if (clear || getMachine() == null || mMachine.getSelectedItemPosition() < 2)
            mBootSectionSummary.setText("");
        else {
            String text = getString(R.string.BootFromLabel) + getMachine().getBootDevice();
            text = appendDriveFilename(getMachine().getKernel(), text, getString(R.string.BareKernel), false);
            text = appendDriveFilename(getMachine().getInitRd(), text, getString(R.string.BareInitrd), false);
            text = appendDriveFilename(getMachine().getAppend(), text, getString(R.string.BareAppend), false);
            mBootSectionSummary.setText(text);
        }
    }

    private String appendDriveFilename(String driveFile, String text, String drive, boolean allowEmptyDrive) {

        String file = null;
        if (driveFile != null) {
            if ((driveFile.equals("") || driveFile.equals("None")) && allowEmptyDrive) {
                file = drive + getString(R.string.DriveEmptyLabel);
            } else if (!driveFile.equals("") && !driveFile.equals("None"))
                file = drive + ": " + FileUtils.getFilenameFromPath(driveFile);
        }
        if (text == null && file != null)
            text = file;
        else if (file != null)
            text += (", " + file);
        return text;
    }

    public void updateGraphicsSummary(boolean clear) {
        if (clear || getMachine() == null || mMachine.getSelectedItemPosition() < 2)
            mGraphicsSectionSummary.setText("");
        else {
            String text = getString(R.string.VideoDisplayLabel) + getMachine().getVga();
            mGraphicsSectionSummary.setText(text);
        }
    }

    public void updateAudioSummary(boolean clear) {
        if (clear || getMachine() == null
                || mMachine.getSelectedItemPosition() < 2)
            mAudioSectionSummary.setText("");
        else {
            String soundCard = getMachine().getSoundCard();
            String text = getString(R.string.SoundCardLabel)
                    + (soundCard != null ? soundCard : getString(R.string.NoneLabel));
            mAudioSectionSummary.setText(text);
        }
    }

    public void updateNetworkSummary(boolean clear) {
        if (clear || getMachine() == null
                || mMachine.getSelectedItemPosition() < 2)
            mNetworkSectionSummary.setText("");
        else {
            String netCfg = getMachine().getNetwork();
            String text = getString(R.string.Network) + ": "
                    + (netCfg != null ? netCfg : getString(R.string.NoneLabel));
            if (netCfg != null && !netCfg.equals("None")) {
                String nicCard = getMachine().getNetworkCard();
                text += ", " + getString(R.string.NicCard) + ": "
                        + (nicCard != null ? nicCard : getString(R.string.NoneLabel));
                text += ", " + getString(R.string.DNSServer) + ": " + mDNS.getText();
                String hostFWD = getMachine().getHostFwd();
                if (hostFWD != null && !hostFWD.equals(""))
                    text += ", " + getString(R.string.HostForward) + ": " + hostFWD;
            }
            mNetworkSectionSummary.setText(text);
        }
    }

    public void updateAdvancedSummary(boolean clear) {
        if (clear || getMachine() == null || mMachine.getSelectedItemPosition() < 2)
            mAdvancedSectionSummary.setText("");
        else {
            String text = "";
            if (getMachine().getExtraParams() != null
                    && !getMachine().getExtraParams().equals(""))
                text = getString(R.string.ExtraParams) + ": " + getMachine().getExtraParams();
            mAdvancedSectionSummary.setText(text);
        }
    }

    private String appendOption(String option, String text) {

        if (text == null && option != null)
            text = option;
        else if (option != null)
            text += (", " + option);
        return text;
    }

    private void triggerUpdateSpinner(final Spinner spinner) {

        final int position = (int) spinner.getSelectedItemId();
        spinner.setSelection(0);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                spinner.setSelection(position);
            }
        }, 100);
    }

    private void loadMachine() {
        // Called from the model dispatcher: everything that touches views has to
        // happen on the UI thread.
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                setUserPressed(false);
                if (getMachine() == null) {
                    loadingMachineName = null;
                    changeStatus(MachineStatus.Ready);
                    hideConfigLoading();
                    return;
                }
                loadMachineUI();
                // Apply the machine values in the same frame: the previous 1s delay
                // made the sliders/header/checkboxes show the defaults for a moment.
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        postLoadMachineUI();
                        hideConfigLoading();
                    }
                });
                setCPUOptions();
                getMachine().addObserver(LimboActivity.this);
            }
        });
    }

    private void postLoadMachineUI() {

        if (getMachine() == null) {
            loadingMachineName = null;
            hideConfigLoading();
            return;
        }
        loadingMachineName = null;

        mFDAenable.setChecked(getMachine().getFdaImagePath() != null);
        mFDBenable.setChecked(getMachine().getFdbImagePath() != null);
        mCDenable.setChecked(getMachine().getCdImagePath() != null);
        mSDenable.setChecked(getMachine().getSdImagePath() != null);

        changeStatus(MachineController.getInstance().getCurrStatus());
        if (getMachine().getPaused() == 1) {
            enableNonRemovableDeviceOptions(false);
            enableRemovableDeviceOptions(false);
        } else {
            enableNonRemovableDeviceOptions(true);
            enableRemovableDeviceOptions(true);
        }
        machineHydrated = true;
        maybeEnableEditors();
        if (!listenersEnabled && mScrollView != null) {
            // Failsafe if an adapter thread never reports back: after the page has
            // settled expose the controls again. The drive spinners ignore the
            // synthetic selection events, so late adapters cannot wipe the config.
            mScrollView.postDelayed(new Runnable() {
                public void run() {
                    if (!listenersEnabled) {
                        setUserPressed(true);
                    }
                }
            }, 3000);
        }
        machineLoaded = false;
        syncSlidersFromMachine();
        updateMachineHeader();

        if (pendingAutoStart && !MachineController.getInstance().isRunning()
                && getMachine() != null
                && pendingMachineName != null
                && pendingMachineName.equals(getMachine().getName())) {
            scheduleAutoStart();
        }
        mMachine.setEnabled(!MachineController.getInstance().isRunning());
    }

    /**
     * Starts the machine requested from a card only after every disk adapter has
     * settled. Clicking earlier used to run QEMU against a configuration that was
     * still being rewritten by the adaptive spinner callbacks.
     */
    private void scheduleAutoStart() {
        if (!pendingAutoStart) {
            return;
        }
        autoStartAttempts++;
        if (autoStartAttempts > 60) {
            pendingAutoStart = false;
            return;
        }
        boolean ready = machineHydrated && listenersEnabled && pendingDiskAdapters == 0
                && getMachine() != null && pendingMachineName != null
                && pendingMachineName.equals(getMachine().getName());
        if (ready && !MachineController.getInstance().isRunning()) {
            pendingAutoStart = false;
            autoStartAttempts = 0;
            mStart.performClick();
            return;
        }
        mStart.postDelayed(new Runnable() {
            public void run() {
                scheduleAutoStart();
            }
        }, 150);
    }

    private void loadMachineUI() {
        populateMachineType(getMachine().getMachineType());
        populateCPUs(getMachine().getCpu());
        populateNetDevices(getMachine().getNetworkCard());
        SpinnerAdapter.setDiskAdapterValue(mCPUNum, getMachine().getCpuNum() + "");
        SpinnerAdapter.setDiskAdapterValue(mRamSize, getMachine().getMemory() + "");
        seMachineDriveValue(FileType.KERNEL, getMachine().getKernel());
        seMachineDriveValue(FileType.INITRD, getMachine().getInitRd());
        if (getMachine().getAppend() != null)
            mAppend.setText(getMachine().getAppend());
        else
            mAppend.setText("");

        if (getMachine().getHostFwd() != null)
            mHOSTFWD.setText(getMachine().getHostFwd());
        else
            mHOSTFWD.setText("");

        if (getMachine().getExtraParams() != null)
            mExtraParams.setText(getMachine().getExtraParams());
        else
            mExtraParams.setText("");

        // CDROM
        seMachineDriveValue(FileType.CDROM, getMachine().getCdImagePath());

        // Floppy
        seMachineDriveValue(FileType.FDA, getMachine().getFdaImagePath());
        seMachineDriveValue(FileType.FDB, getMachine().getFdbImagePath());

        // SD Card
        seMachineDriveValue(FileType.SD, getMachine().getSdImagePath());

        // HDD
        seMachineDriveValue(FileType.HDA, getMachine().getHdaImagePath());
        seMachineDriveValue(FileType.HDB, getMachine().getHdbImagePath());
        seMachineDriveValue(FileType.HDC, getMachine().getHdcImagePath());
        seMachineDriveValue(FileType.HDD, getMachine().getHddImagePath());

        //sharedfolder
        seMachineDriveValue(FileType.SHARED_DIR, getMachine().getSharedFolderPath());

        // Advance
        SpinnerAdapter.setDiskAdapterValue(mBootDevices, getMachine().getBootDevice());
        if (mFirmware != null) {
            SpinnerAdapter.setDiskAdapterValue(mFirmware, getMachine().getFirmware());
        }
        SpinnerAdapter.setDiskAdapterValue(mNetConfig, getMachine().getNetwork());
        SpinnerAdapter.setDiskAdapterValue(mVGAConfig, getMachine().getVga());
        SpinnerAdapter.setDiskAdapterValue(mSoundCard, getMachine().getSoundCard());
        SpinnerAdapter.setDiskAdapterValue(mUI, getMachine().getEnableVNC() == 1 ? "VNC" : "SDL");
        SpinnerAdapter.setDiskAdapterValue(mMouse, fixMouseValue(getMachine().getMouse()));
        SpinnerAdapter.setDiskAdapterValue(mKeyboard, getMachine().getKeyboard());

        // motherboard settings
        mDisableACPI.setChecked(getMachine().getDisableAcpi() == 1);
        mDisableHPET.setChecked(getMachine().getDisableHPET() == 1);
        if (LimboApplication.arch == Config.Arch.x86 || LimboApplication.arch == Config.Arch.x86_64)
            mDisableTSC.setChecked(getMachine().getDisableTSC() == 1);
        mEnableKVM.setChecked(getMachine().getEnableKVM() == 1);
        mEnableMTTCG.setChecked(getMachine().getEnableMTTCG() == 1);

        enableNonRemovableDeviceOptions(true);
        enableRemovableDeviceOptions(!MachineController.getInstance().isRunning());

        if (Config.enableSDLSound) {
            mSoundCard.setEnabled(getMachine().getEnableVNC() != 1 && getMachine().getPaused() == 0);
        } else
            mSoundCard.setEnabled(false);

        mMachine.setEnabled(false);
    }

    private String fixMouseValue(String mouse) {
        if (mouse != null) {
            if (mouse.startsWith("usb-tablet"))
                mouse += " " + getString(R.string.fixesMouseParen);
        }
        return mouse;
    }

    private synchronized void updateSummary() {
        updateUISummary(false);
        updateCPUSummary(false);
        updateStorageSummary(false);
        updateRemovableStorageSummary(false);
        updateGraphicsSummary(false);
        updateAudioSummary(false);
        updateNetworkSummary(false);
        updateBootSummary(false);
        updateAdvancedSummary(false);
    }

    public void promptMachineName(final Activity activity) {
        final AlertDialog alertDialog;
        alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle(getString(R.string.NewMachineName));
        final EditText vmNameTextView = new EditText(activity);
        vmNameTextView.setPadding(20, 20, 20, 20);
        vmNameTextView.setEnabled(true);
        vmNameTextView.setVisibility(View.VISIBLE);
        vmNameTextView.setSingleLine();
        alertDialog.setView(vmNameTextView);
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.Create), (DialogInterface.OnClickListener) null);

        alertDialog.show();

        Button button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (vmNameTextView.getText().toString().trim().equals(""))
                    ToastUtils.toastShort(activity, getString(R.string.MachineNameCannotBeEmpty));
                else {
                    createMachine(vmNameTextView.getText().toString());
                    alertDialog.dismiss();
                }
            }
        });
        alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(vmNameTextView.getWindowToken(), 0);
            }
        });
    }

    public void promptImageName(final Activity activity, final FileType fileType) {

        final AlertDialog alertDialog;
        alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle(getString(R.string.ImageName));

        LinearLayout mLayout = new LinearLayout(this);
        mLayout.setPadding(20, 20, 20, 20);
        mLayout.setOrientation(LinearLayout.VERTICAL);

        final EditText imageNameView = new EditText(activity);
        imageNameView.setEnabled(true);
        imageNameView.setVisibility(View.VISIBLE);
        imageNameView.setSingleLine();
        LinearLayout.LayoutParams imageNameViewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mLayout.addView(imageNameView, imageNameViewParams);

        final Spinner size = new Spinner(this);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        String[] arraySpinner = new String[5];
        arraySpinner[0] = "1GB (Growable)";
        arraySpinner[1] = "2GB (Growable)";
        arraySpinner[2] = "4GB (Growable)";
        arraySpinner[3] = "10 GB (Growable)";
        arraySpinner[4] = "20 GB (Growable)";

        ArrayAdapter<?> sizeAdapter = new ArrayAdapter<Object>(this, R.layout.custom_spinner_item, arraySpinner);
        sizeAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        size.setAdapter(sizeAdapter);
        mLayout.addView(size, spinnerParams);

        alertDialog.setView(mLayout);

        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.Create), (DialogInterface.OnClickListener) null);
        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.ChangeDirectory), (DialogInterface.OnClickListener) null);

        alertDialog.show();

        Button positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                if (LimboSettingsManager.getImagesDir(LimboActivity.this) == null) {
                    changeImagesDir();
                    return;
                }

                int sizeSel = size.getSelectedItemPosition();
                String templateImage = "hd1g.qcow2";
                if (sizeSel == 0) {
                    templateImage = "hd1g.qcow2";
                } else if (sizeSel == 1) {
                    templateImage = "hd2g.qcow2";
                } else if (sizeSel == 2) {
                    templateImage = "hd4g.qcow2";
                } else if (sizeSel == 3) {
                    templateImage = "hd10g.qcow2";
                } else if (sizeSel == 4) {
                    templateImage = "hd20g.qcow2";
                }

                String image = imageNameView.getText().toString();
                if (image.trim().equals(""))
                    ToastUtils.toastShort(activity, getString(R.string.ImageFilenameCannotBeEmpty));
                else {
                    if (!image.endsWith(".qcow2")) {
                        image += ".qcow2";
                    }
                    String filePath = FileUtils.createImgFromTemplate(LimboActivity.this, templateImage, image, fileType);
                    if (filePath!=null) {
                        updateDrive(fileType, filePath);
                        alertDialog.dismiss();
                    }

                }
            }
        });

        Button negativeButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        negativeButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                changeImagesDir();

            }
        });
    }

    public void changeImagesDir() {
        ToastUtils.toastLong(LimboActivity.this, getString(R.string.chooseDirToCreateImage));
        LimboFileManager.browse(LimboActivity.this, FileType.IMAGE_DIR, Config.OPEN_IMAGE_DIR_REQUEST_CODE);
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (homeMode) {
                moveTaskToBack(true);
            } else {
                finish();
            }
            return true; // return
        }

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (homeMode) {
            if (resultCode == Config.SDL_QUIT_RESULT_CODE
                    && MachineController.getInstance().isRunning()) {
                notifyAction(MachineAction.STOP_VM, null);
            }
            if (homeController != null) {
                homeController.onActivityResult(requestCode, resultCode, data);
            }
            return;
        }

        if (resultCode == Config.SDL_QUIT_RESULT_CODE) {
            if (getParent() != null) {
                getParent().finish();
            }
            finish();
            if (MachineController.getInstance().isRunning()) {
                notifyAction(MachineAction.STOP_VM, null);
            }
        } else if (requestCode == Config.OPEN_IMPORT_FILE_REQUEST_CODE || requestCode == Config.OPEN_IMPORT_FILE_ASF_REQUEST_CODE) {
            String file;
            if (requestCode == Config.OPEN_IMPORT_FILE_ASF_REQUEST_CODE) {
                file = FileUtils.getFileUriFromIntent(this, data, false);
            } else {
                file = FileUtils.getFilePathFromIntent(this, data);
            }
            if (file != null)
                importMachines(file);
        } else if (requestCode == Config.OPEN_EXPORT_DIR_REQUEST_CODE || requestCode == Config.OPEN_EXPORT_DIR_ASF_REQUEST_CODE) {
            String exportDir;
            if (requestCode == Config.OPEN_EXPORT_DIR_ASF_REQUEST_CODE) {
                exportDir = FileUtils.getFileUriFromIntent(this, data, true);
            } else {
                exportDir = FileUtils.getDirPathFromIntent(this, data);
            }
            if (exportDir != null)
                LimboSettingsManager.setExportDir(this, exportDir);
        } else if (requestCode == Config.OPEN_IMAGE_FILE_REQUEST_CODE || requestCode == Config.OPEN_IMAGE_FILE_ASF_REQUEST_CODE) {
            String file;
            if (requestCode == Config.OPEN_IMAGE_FILE_ASF_REQUEST_CODE) {
                file = FileUtils.getFileUriFromIntent(this, data, true);
            } else {
                browseFileType = FileUtils.getFileTypeFromIntent(this, data);
                file = FileUtils.getFilePathFromIntent(this, data);
            }
            if (file != null)
                updateDrive(browseFileType, file);
        } else if (requestCode == Config.OPEN_IMAGE_DIR_REQUEST_CODE || requestCode == Config.OPEN_IMAGE_DIR_ASF_REQUEST_CODE) {
            String imageDir;
            if (requestCode == Config.OPEN_IMAGE_DIR_ASF_REQUEST_CODE) {
                imageDir = FileUtils.getFileUriFromIntent(this, data, true);
            } else {
                imageDir = FileUtils.getDirPathFromIntent(this, data);
            }
            if (imageDir != null)
                LimboSettingsManager.setImagesDir(this, imageDir);

        } else if (requestCode == Config.OPEN_SHARED_DIR_REQUEST_CODE || requestCode == Config.OPEN_SHARED_DIR_ASF_REQUEST_CODE) {
            String file;
            if (requestCode == Config.OPEN_SHARED_DIR_ASF_REQUEST_CODE) {
                file = FileUtils.getFileUriFromIntent(this, data, true);
            } else {
                browseFileType = FileUtils.getFileTypeFromIntent(this, data);
                file = FileUtils.getDirPathFromIntent(this, data);
            }
            if (file != null) {
                updateDrive(browseFileType, file);
                LimboSettingsManager.setSharedDir(this, file);
            }
        } else if (requestCode == Config.OPEN_LOG_FILE_DIR_REQUEST_CODE || requestCode == Config.OPEN_LOG_FILE_DIR_ASF_REQUEST_CODE) {
            String file;
            if (requestCode == Config.OPEN_LOG_FILE_DIR_ASF_REQUEST_CODE) {
                file = FileUtils.getFileUriFromIntent(this, data, true);
            } else {
                file = FileUtils.getDirPathFromIntent(this, data);
            }
            if (file != null) {
                FileUtils.saveLogToFile(LimboActivity.this, file);
            }
        } else if (requestCode == Config.OPEN_IMPORT_BIOS_FILE_REQUEST_CODE || requestCode == Config.OPEN_IMPORT_BIOS_FILE_ASF_REQUEST_CODE) {
            String file;
            if (requestCode == Config.OPEN_IMPORT_BIOS_FILE_ASF_REQUEST_CODE) {
                file = FileUtils.getFileUriFromIntent(this, data, false);
            } else {
                file = FileUtils.getFilePathFromIntent(this, data);
            }
            if (file != null)
                BIOSImporter.importBIOSFile(this, file);
        }
    }

    private void updateDrive(FileType fileType, String diskValue) {
        //FIXME: sometimes the array adapters try to set invalid values
        if (fileType == null || diskValue == null) {
            return;
        }
        Spinner spinner = getSpinner(fileType);
        if (!diskValue.trim().isEmpty()) {
            if (SpinnerAdapter.getPositionFromSpinner(spinner, diskValue) < 0) {
                android.widget.SpinnerAdapter adapter = spinner.getAdapter();
                if (adapter instanceof ArrayAdapter) {
                    SpinnerAdapter.addItem(spinner, diskValue);
                }
            }
            notifyAction(MachineAction.INSERT_FAV, new Object[]{diskValue, fileType});
            seMachineDriveValue(fileType, diskValue);
        }
        int res = spinner.getSelectedItemPosition();
        if (res == 1) {
            spinner.setSelection(0);
        }
    }

    private ArrayAdapter getAdapter(FileType fileType) {
        Spinner spinner = getSpinner(fileType);
        return (ArrayAdapter) spinner.getAdapter();
    }

    private Spinner getSpinner(FileType fileType) {
        if (diskMapping.containsKey(fileType))
            return diskMapping.get(fileType).spinner;
        return null;
    }

    private MachineProperty getProperty(FileType fileType) {
        if (diskMapping.containsKey(fileType))
            return diskMapping.get(fileType).colName;
        return null;
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (homeMode) {
            if (homeController != null) {
                homeController.onDestroy();
                homeController = null;
            }
            setViewListener(null);
            super.onDestroy();
            return;
        }
        savePendingEditText();
        MachineController.getInstance().removeOnStatusChangeListener(this);
        MachineController.getInstance().removeOnEventListener(this);
        if (getMachine() != null) {
            getMachine().deleteObserver(LimboActivity.this);
        }
        setViewListener(null);
        super.onDestroy();
    }

    private void populateRAM() {
        String[] arraySpinner = new String[4 * 256];
        arraySpinner[0] = 4 + "";
        for (int i = 1; i < arraySpinner.length; i++) {
            arraySpinner[i] = i * 8 + "";
        }
        ArrayAdapter<String> ramAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arraySpinner);
        ramAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mRamSize.setAdapter(ramAdapter);
        mRamSize.invalidate();
    }

    private void populateCPUNum() {
        String[] arraySpinner = new String[Config.MAX_CPU_NUM];
        for (int i = 0; i < arraySpinner.length; i++) {
            arraySpinner[i] = (i + 1) + "";
        }
        ArrayAdapter<String> cpuNumAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arraySpinner);
        cpuNumAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mCPUNum.setAdapter(cpuNumAdapter);
        mCPUNum.invalidate();
    }

    private void populateBootDevices() {
        ArrayList<String> bootDevicesList = new ArrayList<>();
        bootDevicesList.add("Default");
        bootDevicesList.add("CDROM");
        bootDevicesList.add("Hard Disk");
        if (Config.enableEmulatedFloppy)
            bootDevicesList.add("Floppy");

        String[] arraySpinner = bootDevicesList.toArray(new String[0]);

        ArrayAdapter<String> bootDevAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arraySpinner);
        bootDevAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mBootDevices.setAdapter(bootDevAdapter);
        mBootDevices.invalidate();
    }

    private void populateNet() {
        String[] arraySpinner = {"None", "User", "TAP"};
        ArrayAdapter<String> netAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arraySpinner);
        netAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mNetConfig.setAdapter(netAdapter);
        mNetConfig.invalidate();
    }

    private void populateVGA() {
        ArrayList<String> arrList = ArchDefinitions.getVGAValues(this);
        ArrayAdapter<String> vgaAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arrList);
        vgaAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mVGAConfig.setAdapter(vgaAdapter);
        mVGAConfig.invalidate();
    }
private void populateFirmware() {
        if (mFirmware == null) {
            return;
        }
        ArrayList<String> arrList = ArchDefinitions.getFirmwareValues(this);
        ArrayAdapter<String> firmwareAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arrList);
        firmwareAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mFirmware.setAdapter(firmwareAdapter);
        mFirmware.invalidate();
    }

    private void populateKeyboardLayout() {
        ArrayList<String> arrList = ArchDefinitions.getKeyboardValues(this);
        ArrayAdapter<String> keyboardAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arrList);
        keyboardAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mKeyboard.setAdapter(keyboardAdapter);
        mKeyboard.invalidate();
        //TODO: for now we use only English keyboard, add more layouts
        mKeyboard.setSelection(0);
    }

    private void populateMouse() {
        ArrayList<String> arrList = ArchDefinitions.getMouseValues(this);
        ArrayAdapter<String> mouseAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arrList);
        mouseAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mMouse.setAdapter(mouseAdapter);
        mMouse.invalidate();
    }

    private void populateSoundcardConfig() {
        ArrayList<String> soundCards = new ArrayList<>();
        soundCards.add("None");
        soundCards.addAll(ArchDefinitions.getSoundcards(this));
        ArrayAdapter<String> sndAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, soundCards);
        sndAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mSoundCard.setAdapter(sndAdapter);
        mSoundCard.invalidate();
    }

    private void populateNetDevices(String nic) {
        ArrayList<String> networkCards = ArchDefinitions.getNetworkDevices(this);
        ArrayAdapter<String> nicCfgAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, networkCards);
        nicCfgAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mNetworkCard.setAdapter(nicCfgAdapter);
        mNetworkCard.invalidate();

        int pos = nicCfgAdapter.getPosition(nic);
        if (pos >= 0) {
            mNetworkCard.setSelection(pos);
        }
    }

    private void populateMachines(final String machineValue) {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                final ArrayList<String> machinesList = ArchDefinitions.getMachineValues(LimboActivity.this);
                ArrayList<String> machinesDB = MachineController.getInstance().getStoredMachines();
                machinesList.addAll(machinesDB);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        ArrayAdapter<String> machineAdapter = new ArrayAdapter<>(LimboActivity.this, R.layout.custom_spinner_item, machinesList);
                        machineAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
                        mMachine.setAdapter(machineAdapter);
                        mMachine.invalidate();
                        if (machineValue != null)
                            SpinnerAdapter.setDiskAdapterValue(mMachine, machineValue);
                        if (pendingMachineName != null)
                            SpinnerAdapter.setDiskAdapterValue(mMachine, pendingMachineName);
                    }
                });
            }
        });
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private void seMachineDriveValue(FileType fileType, final String diskValue) {
        Spinner spinner = getSpinner(fileType);
        if (spinner != null) {
            suppressSpinnerCallbacks(spinner);
            SpinnerAdapter.setDiskAdapterValue(spinner, diskValue);
        }
    }

    private void populateCPUs(String cpu) {
        ArrayList<String> arrList = ArchDefinitions.getCpuValues(this);
        ArrayAdapter<String> cpuAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arrList);
        cpuAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mCPU.setAdapter(cpuAdapter);
        mCPU.invalidate();
        int pos = cpuAdapter.getPosition(cpu);
        if (pos >= 0) {
            mCPU.setSelection(pos);
        }
    }

    private void populateMachineType(String machineType) {
        ArrayList<String> arrList = ArchDefinitions.getMachineTypeValues(this);

        ArrayAdapter<String> machineTypeAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arrList);
        machineTypeAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mMachineType.setAdapter(machineTypeAdapter);

        mMachineType.invalidate();
        int pos = machineTypeAdapter.getPosition(machineType);
        mMachineType.setSelection(Math.max(pos, 0));

    }

    private void populateUI() {
        ArrayList<String> arrList = ArchDefinitions.getUIValues();
        ArrayAdapter<String> uiAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arrList);
        uiAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mUI.setAdapter(uiAdapter);
        mUI.invalidate();
    }

    public void populateDiskAdapter(final Spinner spinner, final FileType fileType, final boolean createOption) {
        pendingDiskAdapters++;
        if (listenersEnabled) {
            setUserPressed(false);
        }
        Thread t = new Thread(new Runnable() {
            public void run() {
                ArrayList<String> oldHDs = MachineFilePaths.getRecentFilePaths(fileType);
                final ArrayList<String> arraySpinner = new ArrayList<>();
                arraySpinner.add("None");
                if (createOption)
                    arraySpinner.add("New");
                arraySpinner.add(getString(R.string.open));
                final int index = arraySpinner.size();
                if (oldHDs != null) {
                    for (String file : oldHDs) {
                        if (file != null) {
                            arraySpinner.add(file);
                        }
                    }
                }
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        SpinnerAdapter adapter = new SpinnerAdapter(LimboActivity.this, R.layout.custom_spinner_item, arraySpinner, index);
                        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
                        // Replacing an adapter resets the selection to position 0 and
                        // fires onItemSelected("None"); the machine must not see that
                        // pseudo event.
                        suppressSpinnerCallbacks(spinner);
                        spinner.setAdapter(adapter);
                        spinner.invalidate();
                        // The adapter may have been built after the machine was
                        // loaded; re-apply its drive so the value is never lost.
                        if (getMachine() != null) {
                            seMachineDriveValue(fileType, getDriveValue(fileType));
                        }
                        if (pendingDiskAdapters > 0) {
                            pendingDiskAdapters--;
                        }
                        maybeEnableEditors();
                    }
                });
            }
        });
        t.start();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        invalidateOptionsMenu();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(0, HELP, 0, R.string.help);
        menu.add(0, INSTALL, 0, R.string.InstallRoms);
        if(!MachineController.getInstance().isRunning()) {
            menu.add(0, CREATE, 0, R.string.CreateMachine);
            menu.add(0, DELETE, 0, R.string.DeleteMachine);
            if (getMachine() != null && getMachine().getPaused() == 1)
                menu.add(0, DISCARD_VM_STATE, 0, R.string.DiscardSavedState);
            menu.add(0, EXPORT, 0, R.string.ExportMachines);
            menu.add(0, IMPORT, 0, R.string.ImportMachines);
        }
        menu.add(0, IMPORT_BIOS_FILE, 0, R.string.ImportBIOSFile);
        menu.add(0, SETTINGS, 0, R.string.Settings);
        menu.add(0, TOOLS, 0, R.string.advancedTools);
        if (getMachine() != null && getMachine().getEnableVNC() == 1) {
            menu.add(0, VNC_CLIENT, 0, R.string.vnc_open);
        }
        menu.add(0, VIEWLOG, 0, R.string.ViewLog);
        menu.add(0, HELP, 0, R.string.help);
        menu.add(0, CHANGELOG, 0, R.string.Changelog);
        menu.add(0, LICENSE, 0, R.string.License);
        menu.add(0, QUIT, 0, R.string.Exit);

        // Everything lives in the overflow so the floating glass toolbar stays clean.
        for (int i = 0; i < menu.size(); i++) {
            menu.getItem(i).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {

        super.onOptionsItemSelected(item);
        if (item.getItemId() == INSTALL) {
            Installer.installFiles(this, true);
        } else if (item.getItemId() == DELETE) {
            promptDeleteMachine();
        } else if (item.getItemId() == DISCARD_VM_STATE) {
            promptDiscardVMState();
        } else if (item.getItemId() == CREATE) {
            promptMachineName(this);
        } else if (item.getItemId() == SETTINGS) {
            showSettings();
        } else if (item.getItemId() == TOOLS) {
            LimboActivityCommon.goToURL(this, Config.toolsLink);
        } else if (item.getItemId() == EXPORT) {
            MachineExporter.promptExport(this);
        } else if (item.getItemId() == IMPORT) {
            MachineImporter.promptImportMachines(this);
        } else if (item.getItemId() == IMPORT_BIOS_FILE) {
            BIOSImporter.promptImportBIOSFile(this);
        } else if (item.getItemId() == HELP) {
            Help.showHelp(this);
        } else if (item.getItemId() == VIEWLOG) {
            Logger.viewLimboLog(LimboActivity.this);
        } else if (item.getItemId() == VNC_CLIENT) {
            if (MachineController.getInstance().isRunning()) {
                startActivity(new Intent(this, com.max2idea.android.limbo.vnc.VncActivity.class));
            } else {
                ToastUtils.toastShort(this, getString(R.string.vnc_not_running));
            }
        } else if (item.getItemId() == CHANGELOG) {
            LimboActivityCommon.showChangelog(LimboActivity.this);
        } else if (item.getItemId() == LICENSE) {
            promptLicense();
        } else if (item.getItemId() == QUIT) {
            exit();
        }
        return true;
    }

    private void showSettings() {
        Intent i = new Intent(this, AppSettingsActivity.class);
        startActivity(i);
    }

    public void promptDeleteMachine() {
        if (getMachine() == null) {
            ToastUtils.toastShort(this, getString(R.string.NoMachineSelected));
            return;
        }
        new AlertDialog.Builder(this).setTitle(getString(R.string.DeleteVM) + ": " + getMachine().getName())
                .setMessage(R.string.deleteVMWarning)
                .setPositiveButton(getString(android.R.string.yes), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        onDeleteMachine();
                    }
                }).setNegativeButton(getString(android.R.string.no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        }).show();
    }

    public void promptDiscardVMState() {
        new AlertDialog.Builder(this).setTitle(R.string.discardVMState)
                .setMessage(R.string.discardVMInstructions)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        notifyFieldChange(MachineProperty.PAUSED, 0);
                        changeStatus(MachineStatus.Ready);
                        enableNonRemovableDeviceOptions(true);
                        enableRemovableDeviceOptions(true);

                    }
                }).setNegativeButton(getString(android.R.string.no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        }).show();
    }

    public void onPause() {
        View currentView = getCurrentFocus();
        if (currentView instanceof EditText) {
            currentView.setFocusable(false);
        }
        super.onPause();
    }

    public void onResume() {
        super.onResume();
        if (homeMode) {
            if (homeController != null) {
                homeController.onResume();
            }
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                updateValues();
                if(libLoaded)
                    notifyAction(MachineAction.IGNORE_BREAKPOINT_INVALIDATION, LimboSettingsManager.getIgnoreBreakpointInvalidation(LimboActivity.this));
            }
        }, 1000);

    }

    private void updateValues() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        changeStatus(MachineController.getInstance().getCurrStatus());
                        updateRemovableDiskValues();
                        updateSummary();
                    }
                });
            }
        });
        t.start();
    }

    private void updateRemovableDiskValues() {
        if (getMachine() != null) {
            disableRemovableDiskListeners();
            updateDrive(FileType.CDROM, getMachine().getCdImagePath());
            updateDrive(FileType.FDA, getMachine().getFdaImagePath());
            updateDrive(FileType.FDB, getMachine().getFdbImagePath());
            updateDrive(FileType.SD, getMachine().getSdImagePath());
            enableRemovableDiskListeners();
        }
    }

    public boolean isLandscapeOrientation(Activity activity) {
        Display display = activity.getWindowManager().getDefaultDisplay();
        Point screenSize = new Point();
        display.getSize(screenSize);
        return screenSize.x >= screenSize.y;
    }

    @Override
    public void onMachineStatusChanged(Machine machine, MachineStatus status, Object o) {
        switch (status) {
            case SaveFailed:
                LimboActivityCommon.promptPausedErrorVM(this, (String) o, viewListener);
                break;
            case SaveCompleted:
                LimboActivityCommon.promptPausedVM(this, viewListener);
                break;
            default:
                changeStatus(status);
        }
    }

    @Override
    public void onEvent(Machine machine, MachineController.Event event, Object o) {
        switch (event) {
            case MachineCreateFailed:
                if (o instanceof Integer) {
                    ToastUtils.toastShort(LimboActivity.this, getString((int) o));
                } else if (o instanceof String) {
                    ToastUtils.toastShort(LimboActivity.this, (String) o);
                }
                break;
            case MachineCreated:
                machineCreated();
                break;
            case MachineLoaded:
                loadMachine();
                break;
            case MachineContinued:
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        invalidateOptionsMenu();
                        changeStatus(MachineController.getInstance().getCurrStatus());
                    }
                }, 1000);
                break;
            case MachinesImported:
                onMachinesImported((ArrayList<Machine>) o);
                break;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateSummary();
            }
        });
    }


    private void onMachinesImported(ArrayList<Machine> machines) {
        populateAttributesUI();
        setupMiscOptions();
        setupNonRemovableDiskListeners();
        enableRemovableDiskListeners();
        updateFavAdapters();
        LimboActivityCommon.promptMachinesImported(this, machines);
    }

    private void updateFavAdapters() {
        mHDA.getAdapter().getCount();
        mHDB.getAdapter().getCount();
        mHDC.getAdapter().getCount();
        mHDD.getAdapter().getCount();
        mCD.getAdapter().getCount();
        mFDA.getAdapter().getCount();
        mFDB.getAdapter().getCount();
        mSD.getAdapter().getCount();
        mKernel.getAdapter().getCount();
        mInitrd.getAdapter().getCount();
    }

    private void addGenericOperatingSystems() {
        Config.osImages.put(getString(R.string.other), new LinksManager.LinkInfo("Other",
                getString(R.string.otherOperatingSystem),
                Config.otherOSLink,
                LinksManager.LinkType.OTHER));
                Config.osImages.put(getString(R.string.custom), new LinksManager.LinkInfo("Custom",
                getString(R.string.customOperatingSystem),
                null,
                LinksManager.LinkType.OTHER));
    }

    @Override
    public void update(Observable observable, final Object o) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Object [] params = (Object[] ) o;
                if (params[0] instanceof MachineProperty) {
                    MachineProperty property = (MachineProperty) params[0];
                    if (property == MachineProperty.UI) {
                        if (getMachine().getEnableVNC() != 1)
                            mSoundCard.setEnabled(true);
                        else
                            mSoundCard.setEnabled(true);
                    }
                }
                updateSummary();
            }
        });

    }

    public void notifyFieldChange(MachineProperty property, Object value) {
        if (viewListener != null)
            viewListener.onFieldChange(property, value);
    }

    public void notifyAction(MachineAction action, Object value) {
        if (viewListener != null)
            viewListener.onAction(action, value);
    }

    static class DiskInfo {
        public CompoundButton enableCheckBox;
        public Spinner spinner;
        public MachineProperty colName;

        public DiskInfo(Spinner spinner, CompoundButton enableCheckbox, MachineProperty dbColName) {
            this.spinner = spinner;
            this.enableCheckBox = enableCheckbox;
            this.colName = dbColName;
        }
    }

}
