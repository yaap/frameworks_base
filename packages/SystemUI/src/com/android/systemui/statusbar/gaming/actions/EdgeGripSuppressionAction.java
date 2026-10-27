/*
 * Copyright (C) 2026 The YAAP Project
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
import android.os.SystemProperties;
import android.provider.Settings;

public class EdgeGripSuppressionAction extends GamingMacroAction {
    private static final String PREF_KEY = "gaming_mode_edge_grip_suppression";
    private static final String PROP_NAME = "debug.edge_grip_suppression";
    private static final int MIN_LEVEL = 1;
    private static final int MAX_LEVEL = 5;
    private static final int DEFAULT_LEVEL = 1;

    public EdgeGripSuppressionAction(Context context, SharedPreferences prefs, String settingKey) {
        super(context, prefs, settingKey);
    }

    @Override
    public boolean isEnabled() {
        // Gaming mode always applies the selected edge-grip width while active.
        return true;
    }

    @Override
    public void saveState(SharedPreferences.Editor editor) {
        final int value = Settings.Secure.getInt(mResolver,
                Settings.Secure.EDGE_GRIP_SUPPRESSION, DEFAULT_LEVEL);
        editor.putInt(PREF_KEY, value);
    }

    @Override
    public void apply() {
        final int configured = Settings.System.getInt(mResolver, mSettingKey, DEFAULT_LEVEL);
        final int value = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, configured));
        Settings.Secure.putInt(mResolver, Settings.Secure.EDGE_GRIP_SUPPRESSION, value);
        SystemProperties.set(PROP_NAME, String.valueOf(value));
    }

    @Override
    public void restore() {
        if (!mPrefs.contains(PREF_KEY)) {
            return;
        }
        final int value = mPrefs.getInt(PREF_KEY, DEFAULT_LEVEL);
        Settings.Secure.putInt(mResolver, Settings.Secure.EDGE_GRIP_SUPPRESSION, value);
        SystemProperties.set(PROP_NAME, String.valueOf(value));
    }
}
