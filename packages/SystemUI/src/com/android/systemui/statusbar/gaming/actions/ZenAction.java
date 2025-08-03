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

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

public class ZenAction extends GamingMacroAction {
    private static final String PREF_KEY = "gaming_mode_state_zen";

    private final NotificationManager mNotificationManager;

    public ZenAction(Context context, SharedPreferences prefs, String settingKey) {
        super(context, prefs, settingKey);
        mNotificationManager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
    }

    @Override
    public void saveState(SharedPreferences.Editor editor) {
        final int value = mNotificationManager.getCurrentInterruptionFilter();
        editor.putInt(PREF_KEY, value);
    }

    @Override
    public void apply() {
        mNotificationManager.setInterruptionFilter(
                NotificationManager.INTERRUPTION_FILTER_PRIORITY);
    }

    @Override
    public void restore() {
        if (!mPrefs.contains(PREF_KEY)) {
            return;
        }
        final int value = mPrefs.getInt(PREF_KEY,
                NotificationManager.INTERRUPTION_FILTER_ALL);
        mNotificationManager.setInterruptionFilter(value);
    }
}
