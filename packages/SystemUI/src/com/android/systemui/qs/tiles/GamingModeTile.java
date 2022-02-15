/*
 * Copyright (C) 2018 FireHound
 *               2022 Yet Another AOSP Project
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

package com.android.systemui.qs.tiles;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.hardware.display.ColorDisplayManager;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserHandle;
import android.media.AudioManager;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.Prefs;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.KeyguardStateController;

import java.util.ArrayList;
import javax.inject.Inject;

/** Quick settings tile: Gaming Mode tile **/
public class GamingModeTile extends QSTileImpl<BooleanState> {
    private static final int NOTIFICATION_ID = 10000;
    private static final String CHANNEL_ID = "gaming_mode";
    private static final Intent SETTINGS_INTENT = new Intent("com.android.settings.GAMING_MODE_SETTINGS");

    // saved settings state keys
    private static final String KEY_HEADSUP_STATE = "gaming_mode_state_headsup";
    private static final String KEY_ZEN_STATE = "gaming_mode_state_zen";
    private static final String KEY_RINGER_MODE = "gmaing_mode_ringer_mode";
    // private static final String KEY_NAVBAR_STATE = "gaming_mode_state_navbar";
    // private static final String KEY_HW_KEYS_STATE = "gaming_mode_state_hw_keys";
    private static final String KEY_NIGHT_LIGHT = "gaming_mode_night_light";
    private static final String KEY_NIGHT_LIGHT_AUTO = "gaming_mode_night_light_auto";
    private static final String KEY_BATTERY_SAVER = "gaming_mode_battery_saver";
    private static final String KEY_BATTERY_SAVER_MODE = "gaming_mode_battery_saver_mode";
    private static final String KEY_BATTERY_SAVER_LEVEL = "gaming_mode_battery_saver_level";
    private static final String KEY_BRIGHTNESS_STATE = "gaming_mode_state_brightness";
    private static final String KEY_BRIGHTNESS_LEVEL = "gaming_mode_level_brightness";
    private static final String KEY_MEDIA_LEVEL = "gaming_mode_level_media";
    // other shared prefs keys
    private static final String KEY_DIALOG_SHOWN = "gaming_mode_dialog_shown";

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_qs_gaming_mode);
    private final AudioManager mAudio;
    private final NotificationManager mNm;
    private final ContentResolver mResolver;
    private final ShutdownBroadcastReciever mShutdownBroadcastReciever;
    private final ScreenBroadcastReceiver mScreenBroadcastReceiver;
    private final BatteryController mBatteryController;
    private final DisplayManager mDisplayManager;
    private final ColorDisplayManager mColorManager;
    // private final boolean mHasHWKeys;
    private boolean mShutdownRegistered;
    private boolean mScreenRegistered;

    // user settings
    private boolean mHeadsUpEnabled;
    private boolean mZenEnabled;
    // private boolean mNavBarEnabled;
    // private boolean mHwKeysEnabled;
    private boolean mNightLightEnabled;
    private boolean mBatterySaverEnabled;
    private boolean mBrightnessEnabled;
    private boolean mMediaEnabled;
    private boolean mScreenOffEnabled;

    private int mRingerMode = 0;
    private int mBrightnessLevel = 80;
    private int mMediaLevel = 80;

    @Inject
    public GamingModeTile(QSHost host,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            BroadcastDispatcher broadcastDispatcher,
            KeyguardStateController keyguardStateController,
            ColorDisplayManager colorManager,
            BatteryController batteryController
    ) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mResolver = mContext.getContentResolver();
        mAudio = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mNm = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mDisplayManager = (DisplayManager) mContext.getSystemService(DisplayManager.class);
        mColorManager = colorManager;
        mBatteryController = batteryController;

        // find out if a physical navbar is present
        // Configuration c = mContext.getResources().getConfiguration();
        // mHasHWKeys = c.navigation != Configuration.NAVIGATION_NONAV;

        mShutdownBroadcastReciever = new ShutdownBroadcastReciever();
        mScreenBroadcastReceiver = new ScreenBroadcastReceiver();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick(@Nullable View view) {
        if (!Prefs.getBoolean(mContext, KEY_DIALOG_SHOWN, false)) {
            showGamingModeWhatsThisDialog();
            return;
        }
        final boolean newState = !mState.value;
        handleState(newState);
        refreshState(newState);
    }

    @Override
    public Intent getLongClickIntent() {
        // no need to show it to the user if they already did this
        Prefs.putBoolean(mContext, KEY_DIALOG_SHOWN, true);
        return SETTINGS_INTENT;
    }

    private void handleState(boolean enabled) {
        ArrayList<String> enabledStrings = new ArrayList<>();
        if (enabled) {
            saveSettingsState();
            updateUserSettings();

            if (mHeadsUpEnabled) {
                Settings.Global.putInt(mResolver,
                        Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 0);
                enabledStrings.add(mContext.getString(R.string.gaming_mode_headsup));
            }

            if (mZenEnabled) {
                mNm.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                enabledStrings.add(mContext.getString(R.string.gaming_mode_zen));
            }

            if (mRingerMode != 0) {
                // if we somehow have an invalid setting value stay at the same mode
                int mode = mAudio.getRingerModeInternal();
                String modeStr = "invalid";
                if (mRingerMode == 1) {
                    mode = AudioManager.RINGER_MODE_VIBRATE;
                    modeStr = mContext.getString(R.string.gaming_mode_vibrate);
                } else if (mRingerMode == 2) {
                    mode = AudioManager.RINGER_MODE_SILENT;
                    modeStr = mContext.getString(R.string.gaming_mode_silent);
                }
                mAudio.setRingerModeInternal(mode);
                enabledStrings.add(mContext.getString(R.string.gaming_mode_ringer)
                        + " (" + modeStr + ")");
            }

            // if (mNavBarEnabled) {
            //     Settings.System.putInt(mResolver,
            //             Settings.System.FORCE_SHOW_NAVBAR, 0);
            //     enabledStrings.add(mContext.getString(R.string.gaming_mode_navbar));
            // }
            //
            // if (mHwKeysEnabled && mHasHWKeys) {
            //     Settings.Secure.putInt(mResolver,
            //             Settings.Secure.HARDWARE_KEYS_DISABLE, 1);
            //     enabledStrings.add(mContext.getString(R.string.gaming_mode_hardware_keys));
            // }

            if (mNightLightEnabled) {
                mColorManager.setNightDisplayActivated(false);
                mColorManager.setNightDisplayAutoMode(ColorDisplayManager.AUTO_MODE_DISABLED);
                enabledStrings.add(mContext.getString(R.string.gaming_mode_night_light));
            }

            if (mBatterySaverEnabled) {
                // disable
                mBatteryController.setPowerSaveMode(false);
                // Set to percentage mode at 0
                Settings.Global.putInt(mResolver,
                        Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, 0);
                Settings.Global.putInt(mResolver,
                        Settings.Global.AUTOMATIC_POWER_SAVE_MODE,
                        PowerManager.POWER_SAVE_MODE_TRIGGER_PERCENTAGE);
                enabledStrings.add(mContext.getString(R.string.gaming_mode_battery_saver));
            }

            if (mBrightnessEnabled) {
                // Set manual
                Settings.System.putInt(mResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                if (mBrightnessLevel != 0) {
                    // Set level
                    mDisplayManager.setBrightness(mContext.getDisplayId(),
                            mBrightnessLevel / 100f);
                }
                enabledStrings.add(mContext.getString(R.string.gaming_mode_brightness));
            }

            if (mMediaEnabled) {
                final int max = mAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                final int level = Math.round((float)max * ((float)mMediaLevel / 100f));
                mAudio.setStreamVolume(AudioManager.STREAM_MUSIC, level,
                        AudioManager.FLAG_SHOW_UI);
                enabledStrings.add(mContext.getString(R.string.gaming_mode_media));
            }

            if (mScreenOffEnabled) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_SCREEN_OFF);
                mContext.registerReceiver(mScreenBroadcastReceiver, filter);
                mScreenRegistered = true;
            }

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SHUTDOWN);
            mContext.registerReceiver(mShutdownBroadcastReciever, filter);
            mShutdownRegistered = true;

        } else {
            restoreSettingsState();
            if (mScreenRegistered) {
                mContext.unregisterReceiver(mScreenBroadcastReceiver);
                mScreenRegistered = false;
            }
            if (mShutdownRegistered) {
                mContext.unregisterReceiver(mShutdownBroadcastReciever);
                mShutdownRegistered = false;
            }
        }
        setNotification(enabled, enabledStrings);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        boolean enable = state.value;
        if (state.slash == null) {
            state.slash = new SlashState();
        }
        if (arg instanceof Boolean) {
            enable = (Boolean) arg;
        }
        state.icon = mIcon;
        state.value = enable;
        state.slash.isSlashed = !state.value;
        state.label = mContext.getString(R.string.gaming_mode_tile_title);
        if (enable) {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_on);
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_off);
            state.state = Tile.STATE_INACTIVE;
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.gaming_mode_tile_title);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.YASP;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_on);
        } else {
            return mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_off);
        }
    }

    @Override
    public void handleSetListening(boolean listening) {
        // no-op
    }

    private void showGamingModeWhatsThisDialog() {
        SystemUIDialog dialog = new SystemUIDialog(mContext);
        dialog.setTitle(R.string.gaming_mode_dialog_title);
        dialog.setMessage(R.string.gaming_mode_dialog_message);
        dialog.setPositiveButton(com.android.internal.R.string.ok,
                (DialogInterface.OnClickListener) (dialog1, which) ->
                    Prefs.putBoolean(mContext, KEY_DIALOG_SHOWN, true));
        dialog.setShowForAllUsers(true);
        dialog.show();
    }

    private void updateUserSettings() {
        mHeadsUpEnabled = Settings.System.getInt(mResolver,
                Settings.System.GAMING_MODE_HEADS_UP, 1) == 1;
        mZenEnabled = Settings.System.getInt(mResolver,
                Settings.System.GAMING_MODE_ZEN, 0) == 1;
        mRingerMode = Settings.System.getInt(mResolver,
                Settings.System.GAMING_MODE_RINGER, 0);
        // mNavBarEnabled = Settings.System.getInt(mResolver,
        //         Settings.System.GAMING_MODE_NAVBAR, 0) == 1;
        // mHwKeysEnabled = Settings.System.getInt(mResolver,
        //         Settings.System.GAMING_MODE_HW_BUTTONS, 1) == 1;
        mNightLightEnabled = Settings.System.getInt(mResolver,
                Settings.System.GAMING_MODE_NIGHT_LIGHT, 0) == 1;
        mBatterySaverEnabled = Settings.System.getInt(mResolver,
                Settings.System.GAMING_MODE_BATTERY_SCHEDULE, 0) == 1;
        mBrightnessEnabled = Settings.System.getInt(mResolver,
                Settings.System.GAMING_MODE_BRIGHTNESS_ENABLED, 0) == 1;
        mBrightnessLevel = Settings.System.getInt(mResolver,
                Settings.System.GAMING_MODE_BRIGHTNESS, 80);
        mMediaEnabled = Settings.System.getInt(mResolver,
                Settings.System.GAMING_MODE_MEDIA_ENABLED, 0) == 1;
        mMediaLevel = Settings.System.getInt(mResolver,
                Settings.System.GAMING_MODE_MEDIA, 80);
        mScreenOffEnabled = Settings.System.getInt(mResolver,
                Settings.System.GAMING_MODE_SCREEN_OFF, 0) == 1;
    }

    private void saveSettingsState() {
        Prefs.putInt(mContext, KEY_HEADSUP_STATE, Settings.Global.getInt(mResolver,
                Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 1));
        Prefs.putInt(mContext, KEY_ZEN_STATE, Settings.Global.getInt(mResolver,
                Settings.Global.ZEN_MODE, 0) != 0 ? 1 : 0);
        Prefs.putInt(mContext, KEY_RINGER_MODE, mAudio.getRingerModeInternal());
        // Prefs.putInt(mContext, KEY_NAVBAR_STATE, Settings.System.getInt(mResolver,
        //         Settings.System.FORCE_SHOW_NAVBAR, 1));
        // Prefs.putInt(mContext, KEY_HW_KEYS_STATE, Settings.Secure.getInt(mResolver,
        //         Settings.Secure.HARDWARE_KEYS_DISABLE, 0));
        Prefs.putInt(mContext, KEY_NIGHT_LIGHT,
                mColorManager.isNightDisplayActivated() ? 1 : 0);
        Prefs.putInt(mContext, KEY_NIGHT_LIGHT_AUTO,
                mColorManager.getNightDisplayAutoMode());
        Prefs.putInt(mContext, KEY_BATTERY_SAVER, mBatteryController.isPowerSave() ? 1 : 0);
        Prefs.putInt(mContext, KEY_BATTERY_SAVER_MODE, Settings.Global.getInt(mResolver,
                Settings.Global.AUTOMATIC_POWER_SAVE_MODE,
                PowerManager.POWER_SAVE_MODE_TRIGGER_PERCENTAGE));
        Prefs.putInt(mContext, KEY_BATTERY_SAVER_LEVEL, Settings.Global.getInt(mResolver,
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, 0));
        Prefs.putInt(mContext, KEY_BRIGHTNESS_STATE, Settings.System.getInt(mResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC));
        Prefs.putInt(mContext, KEY_BRIGHTNESS_LEVEL,
                Math.round(mDisplayManager.getBrightness(mContext.getDisplayId()) * 100f));
        // save current volume as percentage
        // we can restore it that way even if vol steps was changed in runtime
        final int max = mAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final int curr = mAudio.getStreamVolume(AudioManager.STREAM_MUSIC);
        Prefs.putInt(mContext, KEY_MEDIA_LEVEL,
                Math.round((float)curr * 100f / (float)max));
    }

    private void restoreSettingsState() {
        if (mHeadsUpEnabled) {
            Settings.Global.putInt(mResolver,
                    Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED,
                    Prefs.getInt(mContext, KEY_HEADSUP_STATE, 1));
        }

        if (mZenEnabled) {
            mNm.setInterruptionFilter(Prefs.getInt(mContext, KEY_ZEN_STATE, 0) == 1
                    ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    : NotificationManager.INTERRUPTION_FILTER_ALL);
        }

        if (mRingerMode != 0) {
            mAudio.setRingerModeInternal(Prefs.getInt(mContext,
                    KEY_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL));
        }

        // if (mNavBarEnabled) {
        //     Settings.System.putInt(mResolver,
        //             Settings.System.FORCE_SHOW_NAVBAR,
        //             Prefs.getInt(mContext, KEY_NAVBAR_STATE, 1));
        // }
        //
        // if (mHwKeysEnabled) {
        //     Settings.Secure.putInt(mResolver,
        //             Settings.Secure.HARDWARE_KEYS_DISABLE,
        //             Prefs.getInt(mContext, KEY_HW_KEYS_STATE, 0));
        // }

        if (mNightLightEnabled) {
            mColorManager.setNightDisplayActivated(
                    Prefs.getInt(mContext, KEY_NIGHT_LIGHT, 0) == 1);
            mColorManager.setNightDisplayAutoMode(
                    Prefs.getInt(mContext, KEY_NIGHT_LIGHT_AUTO, 0));
        }

        if (mBatterySaverEnabled) {
            final int prevMode = Prefs.getInt(mContext, KEY_BATTERY_SAVER_MODE,
                    PowerManager.POWER_SAVE_MODE_TRIGGER_PERCENTAGE);
            final int prevLevel = Prefs.getInt(mContext, KEY_BATTERY_SAVER_LEVEL, 0);
            Settings.Global.putInt(mResolver,
                    Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, prevLevel);
            Settings.Global.putInt(mResolver,
                    Settings.Global.AUTOMATIC_POWER_SAVE_MODE, prevMode);
            mBatteryController.setPowerSaveMode(
                    Prefs.getInt(mContext, KEY_BATTERY_SAVER, 0) == 1);
        }

        if (mBrightnessEnabled) {
            final int prevMode = Prefs.getInt(mContext, KEY_BRIGHTNESS_STATE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
            Settings.System.putInt(mResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE, prevMode);
            if (prevMode != Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    && mBrightnessLevel != 0) {
                mDisplayManager.setBrightness(mContext.getDisplayId(),
                        Prefs.getInt(mContext, KEY_BRIGHTNESS_LEVEL, 0) / 100f);
            }
        }

        if (mMediaEnabled) {
            final int max = mAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            final int prevVol = Prefs.getInt(mContext, KEY_MEDIA_LEVEL, 80);
            mAudio.setStreamVolume(AudioManager.STREAM_MUSIC,
                    Math.round((float)max * (float)prevVol / 100f),
                    AudioManager.FLAG_SHOW_UI);
        }
    }

    private void setNotification(boolean show, ArrayList<String> strings) {
        if (show) {
            final Resources res = mContext.getResources();
            StringBuilder text = new StringBuilder(res.getString(R.string.accessibility_quick_settings_gaming_mode_on));
            if (!strings.isEmpty()) {
                text.append(" ").append(res.getString(R.string.gaming_mode_for)).append(" ");
                text.append(strings.remove(0));
                for (String str : strings) text.append(", ").append(str);
            }
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    res.getString(R.string.gaming_mode_tile_title),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(res.getString(R.string.accessibility_quick_settings_gaming_mode_on));
            channel.enableVibration(false);
            mNm.createNotificationChannel(channel);
            Notification notification = new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_qs_gaming_mode)
                    .setContentTitle(res.getString(R.string.gaming_mode_tile_title))
                    .setContentText(text.toString())
                    .setShowWhen(true)
                    .setOngoing(true)
                    .build();
            mNm.notifyAsUser(null, NOTIFICATION_ID, notification, UserHandle.CURRENT);
        } else {
            mNm.cancelAsUser(null, NOTIFICATION_ID, UserHandle.CURRENT);
        }
    }

    private class ScreenBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                handleState(false);
                refreshState(false);
            }
        }
    }

    private class ShutdownBroadcastReciever extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SHUTDOWN)) {
                handleState(false);
                refreshState(false);
            }
        }
    }
}
