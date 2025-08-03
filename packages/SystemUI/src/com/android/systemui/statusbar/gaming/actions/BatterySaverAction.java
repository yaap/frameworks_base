/*
 * Copyright (C) 2025 Yet Another AOSP Project
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

package com.android.systemui.statusbar.gaming.actions;

import static android.os.PowerManager.POWER_SAVE_MODE_TRIGGER_PERCENTAGE;
import static android.provider.Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL;
import static android.provider.Settings.Global.AUTOMATIC_POWER_SAVE_MODE;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import com.android.systemui.statusbar.policy.BatteryController;

public class BatterySaverAction extends GamingMacroAction {
    private static final String PREF_KEY = "gaming_mode_battery_saver";
    private static final String PREF_KEY_MODE = "gaming_mode_battery_saver_mode";
    private static final String PREF_KEY_LEVEL = "gaming_mode_battery_saver_level";

    private final BatteryController mBatteryController;

    public BatterySaverAction(Context context, SharedPreferences prefs,
            String settingKey, BatteryController batteryController) {
        super(context, prefs, settingKey);
        mBatteryController = batteryController;
    }

    @Override
    public void saveState(SharedPreferences.Editor editor) {
        final int mode = Settings.Global.getInt(mResolver,
                AUTOMATIC_POWER_SAVE_MODE, POWER_SAVE_MODE_TRIGGER_PERCENTAGE);
        final int level = Settings.Global.getInt(mResolver, LOW_POWER_MODE_TRIGGER_LEVEL, 0);
        editor.putBoolean(PREF_KEY, mBatteryController.isPowerSave());
        editor.putInt(PREF_KEY_MODE, mode);
        editor.putInt(PREF_KEY_LEVEL, level);
    }

    @Override
    public void apply() {
        // disable
        mBatteryController.setPowerSaveMode(false);
        // Set to percentage mode at 0
        Settings.Global.putInt(mResolver, LOW_POWER_MODE_TRIGGER_LEVEL, 0);
        Settings.Global.putInt(mResolver,
                AUTOMATIC_POWER_SAVE_MODE, POWER_SAVE_MODE_TRIGGER_PERCENTAGE);
    }

    @Override
    public void restore() {
        if (!mPrefs.contains(PREF_KEY)) {
            return;
        }
        final int mode = mPrefs.getInt(PREF_KEY_MODE, POWER_SAVE_MODE_TRIGGER_PERCENTAGE);
        final int level = mPrefs.getInt(PREF_KEY_LEVEL, 0);
        Settings.Global.putInt(mResolver, AUTOMATIC_POWER_SAVE_MODE, mode);
        Settings.Global.putInt(mResolver, LOW_POWER_MODE_TRIGGER_LEVEL, level);
        mBatteryController.setPowerSaveMode(mPrefs.getBoolean(PREF_KEY, false));
    }
}
