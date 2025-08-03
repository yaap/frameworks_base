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

import static android.provider.Settings.Secure.EXTRA_DIM_AUTO_MODE;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.ColorDisplayManager;
import android.provider.Settings;

public class ExtraDimAction extends GamingMacroAction {
    private static final String PREF_KEY = "gaming_mode_extra_dim";
    private static final String SCHEDULE_KEY = "gaming_mode_extra_dim_schedule";

    private final ColorDisplayManager mColorManager;

    public ExtraDimAction(Context context, SharedPreferences prefs,
            String settingKey, ColorDisplayManager colorManager) {
        super(context, prefs, settingKey);
        mColorManager = colorManager;
    }

    @Override
    public void saveState(SharedPreferences.Editor editor) {
        final boolean enabled = mColorManager.isReduceBrightColorsActivated();
        final int schedule = Settings.Secure.getInt(mResolver,
                EXTRA_DIM_AUTO_MODE, 0);
        editor.putBoolean(PREF_KEY, enabled);
        editor.putInt(SCHEDULE_KEY, schedule);
    }

    @Override
    public void apply() {
        mColorManager.setReduceBrightColorsActivated(false);
        Settings.Secure.putInt(mResolver, EXTRA_DIM_AUTO_MODE, 0);
    }

    @Override
    public void restore() {
        if (!mPrefs.contains(PREF_KEY)) {
            return;
        }
        final boolean enabled = mPrefs.getBoolean(PREF_KEY, false);
        final int schedule = mPrefs.getInt(SCHEDULE_KEY, 0);
        mColorManager.setReduceBrightColorsActivated(enabled);
        Settings.Secure.putInt(mResolver, EXTRA_DIM_AUTO_MODE, schedule);
    }
}
