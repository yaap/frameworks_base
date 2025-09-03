/*
 * Copyright (C) 2024 Yet Another AOSP Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.gaming;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.display.ColorDisplayManager;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.systemui.res.R;
import com.android.systemui.statusbar.gaming.actions.*;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BluetoothController;

import java.util.ArrayList;
import java.util.List;

/** A class to set/restore gaming macro */
public class GamingMacro {
    private static final String TAG = "GamingMacro";
    private static final String CHANNEL_ID = "gaming_mode";
    private static final String ACTION_STOP = "gaming_macro_stop";
    private static final Intent SETTINGS_INTENT = new Intent("com.android.settings.GAMING_MODE_SETTINGS");
    static {
        SETTINGS_INTENT.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    private static final int NOTIFICATION_ID = TAG.hashCode();

    private final Context mContext;
    private final NotificationManager mNm;
    private final ContentResolver mResolver;
    private final GamingStopBroadcastReceiver mStopBroadcastReceiver;
    private final ScreenBroadcastReceiver mScreenBroadcastReceiver;
    private final BatteryBroadcastReceiver mBatteryBroadcastReceiver;
    private final PowerManager mPowerManager;
    private final SharedPreferences mPrefs;
    private boolean mScreenRegistered;
    private boolean mBatteryRegistered;
    private boolean mStopRegistered;
    private boolean mIsChannelSetup = false;

    // user settings
    private boolean mScreenOffEnabled;
    private boolean mBatterySaverDisables;

    private final List<GamingMacroAction> mActions = new ArrayList<>();

    public GamingMacro(Context context,
            ColorDisplayManager colorManager,
            BatteryController batteryController,
            BluetoothController bluetoothController
    ) {
        mContext = context;
        mResolver = context.getContentResolver();
        mNm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mPrefs = context.createDeviceProtectedStorageContext()
                .getSharedPreferences(TAG, Context.MODE_PRIVATE);

        mStopBroadcastReceiver = new GamingStopBroadcastReceiver();
        mScreenBroadcastReceiver = new ScreenBroadcastReceiver();
        mBatteryBroadcastReceiver = new BatteryBroadcastReceiver();

        mActions.add(new HeadsUpAction(context, mPrefs,
                Settings.System.GAMING_MODE_HEADS_UP, 1));
        mActions.add(new ZenAction(context, mPrefs,
                Settings.System.GAMING_MODE_ZEN));
        mActions.add(new RingerAction(context, mPrefs,
                Settings.System.GAMING_MODE_RINGER));
        mActions.add(new ColorModeAction(context, mPrefs,
                Settings.System.GAMING_MODE_COLOR_MODE, -1, colorManager));
        mActions.add(new NightLightAction(context, mPrefs,
                Settings.System.GAMING_MODE_NIGHT_LIGHT, colorManager));
        mActions.add(new BatterySaverAction(context, mPrefs,
                Settings.System.GAMING_MODE_BATTERY_SCHEDULE, batteryController));
        mActions.add(new PowerAction(context, mPrefs,
                Settings.System.GAMING_MODE_POWER, 1));
        mActions.add(new BluetoothAction(context, mPrefs,
                Settings.System.GAMING_MODE_BLUETOOTH, bluetoothController));
        mActions.add(new ThreeFingerAction(context, mPrefs,
                Settings.System.GAMING_MODE_THREE_FINGER));
        mActions.add(new TouchSensitivityAction(context, mPrefs,
                Settings.System.GAMING_MODE_TOUCH_SENSITIVITY));
        mActions.add(new TouchPollingRateAction(context, mPrefs,
                Settings.System.GAMING_MODE_HIGH_TOUCH_RATE));
        mActions.add(new LtpoFeaturesAction(context, mPrefs,
                Settings.System.GAMING_MODE_LTPO_FEATURES));
        mActions.add(new ExtraDimAction(context, mPrefs,
                Settings.System.GAMING_MODE_EXTRA_DIM, colorManager));
        mActions.add(new BrightnessAction(context, mPrefs,
                Settings.System.GAMING_MODE_BRIGHTNESS_ENABLED));
        mActions.add(new MediaLevelAction(context, mPrefs,
                Settings.System.GAMING_MODE_MEDIA_ENABLED));
        mActions.add(new SmoothDisplayAction(context, mPrefs,
                Settings.System.GAMING_MODE_SMOOTH_DISPLAY));
    }

    /**
     * Activates/Deactivates the macro
     * Only call externally!
     */
    public synchronized boolean setEnabled(boolean enabled) {
        if (enabled) {
            updateUserSettings();

            // save all enabled actions states
            SharedPreferences.Editor editor = mPrefs.edit();
            editor.clear(); // remove all keys first. in restore we check which ones exist
            for (GamingMacroAction action : mActions) {
                if (!action.isEnabled()) continue;
                action.saveState(editor);
            }
            if (!editor.commit()) { // use commit to keep this synced
                fireSaveErrNotification();
                return false;
            }

            // apply all enabled actions
            boolean batterySaverEnabled = false;
            for (GamingMacroAction action : mActions) {
                if (!action.isEnabled()) continue;
                action.apply();
                if (action instanceof BatterySaverAction) {
                    batterySaverEnabled = true;
                }
            }

            if (mScreenOffEnabled) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_SCREEN_OFF);
                mContext.registerReceiver(mScreenBroadcastReceiver, filter);
                mScreenRegistered = true;
            }

            if (mBatterySaverDisables && !batterySaverEnabled) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
                mContext.registerReceiver(mBatteryBroadcastReceiver, filter);
                mBatteryRegistered = true;
            }
        } else {
            // restore all previously enabled actions
            for (GamingMacroAction action : mActions) {
                action.restore();
            }

            if (mScreenRegistered) {
                mContext.unregisterReceiver(mScreenBroadcastReceiver);
                mScreenRegistered = false;
            }
            if (mBatteryRegistered) {
                mContext.unregisterReceiver(mBatteryBroadcastReceiver);
                mBatteryRegistered = false;
            }
        }
        setNotification(enabled);
        return true;
    }

    private void updateUserSettings() {
        mScreenOffEnabled = Settings.System.getInt(mResolver,
                Settings.System.GAMING_MODE_SCREEN_OFF, 0) == 1;
        mBatterySaverDisables = Settings.System.getInt(mResolver,
                Settings.System.GAMING_MODE_BATTERY_SAVER_DISABLES, 0) == 1;
    }

    private void setupChannel() {
        if (mIsChannelSetup) return;
        final Resources res = mContext.getResources();
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                res.getString(R.string.gaming_mode_tile_title),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(res.getString(R.string.accessibility_quick_settings_gaming_mode_on));
        channel.enableVibration(false);
        mNm.createNotificationChannel(channel);
        mIsChannelSetup = true;
    }

    public void setNotification(boolean show) {
        if (show) {
            setupChannel();
            final Resources res = mContext.getResources();
            Intent stopIntent = new Intent(mContext, GamingStopBroadcastReceiver.class);
            stopIntent.setAction(ACTION_STOP);
            stopIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            PendingIntent stopPI = PendingIntent.getBroadcast(mContext, mContext.getUserId(), stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Action stopAction = new Notification.Action.Builder(
                R.drawable.ic_qs_gaming_mode,
                res.getString(R.string.screenrecord_stop_label),
                stopPI
            ).build();
            if (!mStopRegistered) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(ACTION_STOP);
                mContext.registerReceiver(mStopBroadcastReceiver, filter,
                        Context.RECEIVER_EXPORTED);
                mStopRegistered = true;
            }

            PendingIntent contentPI = PendingIntent.getActivity(
                    mContext, 0, SETTINGS_INTENT, PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_qs_gaming_mode)
                    .setContentTitle(res.getString(R.string.gaming_mode_tile_title))
                    .setContentText(res.getString(R.string.gaming_mode_notification_content))
                    .setContentIntent(contentPI)
                    .setAutoCancel(false)
                    .setShowWhen(true)
                    .setOngoing(true)
                    .addAction(stopAction)
                    .build();
            mNm.notifyAsUser(null, NOTIFICATION_ID, notification, UserHandle.CURRENT);
        } else {
            mNm.cancelAsUser(null, NOTIFICATION_ID, UserHandle.CURRENT);
            if (mStopRegistered) {
                mContext.unregisterReceiver(mStopBroadcastReceiver);
                mStopRegistered = false;
            }
        }
    }

    private void fireSaveErrNotification() {
        setupChannel();
        final Resources res = mContext.getResources();
        Notification notification = new Notification.Builder(mContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_qs_gaming_mode)
                .setContentTitle(res.getString(R.string.gaming_mode_tile_title))
                .setContentText(res.getString(R.string.gaming_mode_notification_content))
                .setAutoCancel(false)
                .setShowWhen(true)
                .setOngoing(false)
                .build();
        mNm.notifyAsUser(null, NOTIFICATION_ID, notification, UserHandle.CURRENT);
    }

    private class ScreenBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                Settings.Global.putInt(mResolver, Settings.Global.GAMING_MACRO_ENABLED, 0);
            }
        }
    }

    private class BatteryBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(intent.getAction())
                    && mPowerManager.isPowerSaveMode()) {
                Settings.Global.putInt(mResolver, Settings.Global.GAMING_MACRO_ENABLED, 0);
            }
        }
    }
}
