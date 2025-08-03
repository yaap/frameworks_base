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
import android.hardware.display.ColorDisplayManager;

public class NightLightAction extends GamingMacroAction {
    private static final String PREF_KEY = "gaming_mode_night_light";
    private static final String PREF_KEY_AUTO = "gaming_mode_night_light_auto";

    private final ColorDisplayManager mColorManager;

    public NightLightAction(Context context, SharedPreferences prefs,
            String settingKey, ColorDisplayManager colorManager) {
        super(context, prefs, settingKey);
        mColorManager = colorManager;
    }

    @Override
    public void saveState(SharedPreferences.Editor editor) {
        editor.putBoolean(PREF_KEY, mColorManager.isNightDisplayActivated());
        editor.putInt(PREF_KEY_AUTO, mColorManager.getNightDisplayAutoMode());
    }

    @Override
    public void apply() {
        mColorManager.setNightDisplayActivated(false);
        mColorManager.setNightDisplayAutoMode(
                ColorDisplayManager.AUTO_MODE_DISABLED);
    }

    @Override
    public void restore() {
        if (!mPrefs.contains(PREF_KEY)) {
            return;
        }
        mColorManager.setNightDisplayActivated(mPrefs.getBoolean(PREF_KEY, false));
        mColorManager.setNightDisplayAutoMode(mPrefs.getInt(PREF_KEY_AUTO, 0));
    }
}
