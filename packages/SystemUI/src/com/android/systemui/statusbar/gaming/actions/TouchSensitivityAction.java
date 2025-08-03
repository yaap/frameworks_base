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

import static android.provider.Settings.Secure.TOUCH_SENSITIVITY_ENABLED;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.provider.Settings;

public class TouchSensitivityAction extends GamingMacroAction {
    private static final String PREF_KEY = "gaming_mode_touch_sensitivity";
    private static final String PROP_NAME = "debug.touch_sensitivity_mode";

    public TouchSensitivityAction(Context context, SharedPreferences prefs, String settingKey) {
        super(context, prefs, settingKey);
    }

    @Override
    public void saveState(SharedPreferences.Editor editor) {
        final int value = Settings.Secure.getInt(mResolver,
                TOUCH_SENSITIVITY_ENABLED, 0);
        editor.putInt(PREF_KEY, value);
    }

    @Override
    public void apply() {
        Settings.Secure.putInt(mResolver, TOUCH_SENSITIVITY_ENABLED, 1);
        SystemProperties.set(PROP_NAME, "1");
    }

    @Override
    public void restore() {
        if (!mPrefs.contains(PREF_KEY)) {
            return;
        }
        final int value = mPrefs.getInt(PREF_KEY, 0);
        Settings.Secure.putInt(mResolver, TOUCH_SENSITIVITY_ENABLED, value);
        SystemProperties.set(PROP_NAME, String.valueOf(value));
    }
}
