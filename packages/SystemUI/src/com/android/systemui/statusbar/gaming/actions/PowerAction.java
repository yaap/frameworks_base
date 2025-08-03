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

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.power.Mode;
import android.os.PowerManagerInternal;
import android.provider.Settings;

import com.android.server.LocalServices;

public class PowerAction extends GamingMacroAction {
    private final PowerManagerInternal mPowerManagerInternal;

    public PowerAction(Context context, SharedPreferences prefs,
            String settingKey, int defaultValue) {
        super(context, prefs, settingKey, defaultValue);
        mPowerManagerInternal = LocalServices.getService(PowerManagerInternal.class);
    }

    @Override
    public boolean isEnabled() {
        return Settings.System.getInt(mResolver,
                mSettingKey, mDefaultValue) == mDefaultValue;
    }

    @Override
    public void saveState(SharedPreferences.Editor editor) {
        // disabling regardless of setting - no need to save
    }

    @Override
    public void apply() {
        if (mPowerManagerInternal == null) return;
        mPowerManagerInternal.setPowerMode(Mode.GAME, true);
    }

    @Override
    public void restore() {
        if (mPowerManagerInternal == null) return;
        // disabling regardless of setting
        mPowerManagerInternal.setPowerMode(Mode.GAME, false);
    }
}
