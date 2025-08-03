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

import com.android.systemui.statusbar.policy.BluetoothController;

public class BluetoothAction extends GamingMacroAction {
    private static final String PREF_KEY = "gaming_mode_bluetooth";

    private final BluetoothController mBluetoothController;

    public BluetoothAction(Context context, SharedPreferences prefs,
            String settingKey, BluetoothController bluetoothController) {
        super(context, prefs, settingKey);
        mBluetoothController = bluetoothController;
    }

    @Override
    public void saveState(SharedPreferences.Editor editor) {
        editor.putBoolean(PREF_KEY, mBluetoothController.isBluetoothEnabled());
    }

    @Override
    public void apply() {
        mBluetoothController.setBluetoothEnabled(true);
    }

    @Override
    public void restore() {
        if (!mPrefs.contains(PREF_KEY)) {
            return;
        }
        final boolean value = mPrefs.getBoolean(PREF_KEY, false);
        mBluetoothController.setBluetoothEnabled(value);
    }
}
