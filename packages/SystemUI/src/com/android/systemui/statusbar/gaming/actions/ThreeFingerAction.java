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

import static android.provider.Settings.System.THREE_FINGER_GESTURE;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

public class ThreeFingerAction extends GamingMacroAction {
    private static final String PREF_KEY = "gaming_mode_three_finger";

    public ThreeFingerAction(Context context, SharedPreferences prefs, String settingKey) {
        super(context, prefs, settingKey);
    }

    @Override
    public void saveState(SharedPreferences.Editor editor) {
        final int value = Settings.System.getInt(mResolver,
                THREE_FINGER_GESTURE, 0);
        editor.putInt(PREF_KEY, value);
    }

    @Override
    public void apply() {
        Settings.System.putInt(mResolver, THREE_FINGER_GESTURE, 0);
    }

    @Override
    public void restore() {
        if (!mPrefs.contains(PREF_KEY)) {
            return;
        }
        final int value = mPrefs.getInt(PREF_KEY, 0);
        Settings.System.putInt(mResolver,
                THREE_FINGER_GESTURE, value);
    }
}
