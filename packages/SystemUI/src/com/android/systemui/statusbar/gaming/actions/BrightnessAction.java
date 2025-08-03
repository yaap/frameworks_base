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

import static android.os.PowerManager.BRIGHTNESS_MAX;
import static android.os.PowerManager.BRIGHTNESS_MIN;
import static android.provider.Settings.System.GAMING_MODE_BRIGHTNESS;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;

import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MIN;
import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MAX;
import static com.android.settingslib.display.BrightnessUtils.convertGammaToLinearFloat;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManager;
import android.provider.Settings;

public class BrightnessAction extends GamingMacroAction {
    private static final String PREF_KEY = "gaming_mode_state_brightness";
    private static final String LEVEL_KEY = "gaming_mode_level_brightness";

    private final DisplayManager mDisplayManager;
    private final int mDisplayId;

    private float mBrightnessMin = BRIGHTNESS_MIN;
    private float mBrightnessMax = BRIGHTNESS_MAX;

    public BrightnessAction(Context context, SharedPreferences prefs, String settingKey) {
        super(context, prefs, settingKey);
        mDisplayManager = (DisplayManager) context.getSystemService(DisplayManager.class);
        mDisplayId = context.getDisplayId();

        final BrightnessInfo info = context.getDisplay().getBrightnessInfo();
        if (info != null) {
            mBrightnessMin = info.brightnessMinimum;
            mBrightnessMax = info.brightnessMaximum;
        }
    }

    @Override
    public void saveState(SharedPreferences.Editor editor) {
        final int mode = Settings.System.getInt(mResolver,
                SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        final float level = mDisplayManager.getBrightness(mDisplayId);
        editor.putInt(PREF_KEY, mode);
        editor.putInt(LEVEL_KEY, Math.round(level * 100f));
    }

    @Override
    public void apply() {
        final int level = Settings.System.getInt(mResolver, GAMING_MODE_BRIGHTNESS, 80);
        // Set manual
        Settings.System.putInt(mResolver,
                SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL);
        if (level != 0) {
            // Set level
            final int gamma = Math.round(GAMMA_SPACE_MIN +
                    (level / 100f) * (GAMMA_SPACE_MAX - GAMMA_SPACE_MIN));
            final float lFloat = convertGammaToLinearFloat(gamma, mBrightnessMin, mBrightnessMax);
            mDisplayManager.setBrightness(mDisplayId, Math.min(lFloat, mBrightnessMax));
        }
    }

    @Override
    public void restore() {
        if (!mPrefs.contains(PREF_KEY)) {
            return;
        }
        final int mode = mPrefs.getInt(PREF_KEY, SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        final int levelSetting = Settings.System.getInt(
                mResolver, GAMING_MODE_BRIGHTNESS, 80);
        Settings.System.putInt(mResolver, SCREEN_BRIGHTNESS_MODE, mode);
        if (mode != SCREEN_BRIGHTNESS_MODE_AUTOMATIC && levelSetting != 0) {
            final int level = mPrefs.getInt(LEVEL_KEY, 0);
            mDisplayManager.setBrightness(mDisplayId, level / 100f);
        }
    }
}
