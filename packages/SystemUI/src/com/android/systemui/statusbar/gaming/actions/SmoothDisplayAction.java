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

import static android.provider.Settings.System.PEAK_REFRESH_RATE;

import static com.android.internal.display.RefreshRateSettingsUtils.DEFAULT_REFRESH_RATE;
import static com.android.internal.display.RefreshRateSettingsUtils.findHighestRefreshRateAmongAllDisplays;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.provider.DeviceConfig;
import android.provider.Settings;

public class SmoothDisplayAction extends GamingMacroAction {
    private static final String PREF_KEY = "gaming_mode_smooth_display";
    private static final float INVALIDATE_REFRESH_RATE = -1f;

    private final float mPeakRefreshRate;

    public SmoothDisplayAction(Context context, SharedPreferences prefs, String settingKey) {
        super(context, prefs, settingKey);
        mPeakRefreshRate = Math.round(findHighestRefreshRateAmongAllDisplays(context));
    }

    @Override
    public void saveState(SharedPreferences.Editor editor) {
        final float value = Settings.System.getFloat(mResolver,
                PEAK_REFRESH_RATE, getDefaultPeakRefreshRate());
        editor.putFloat(PREF_KEY, value);
    }

    @Override
    public void apply() {
        Settings.System.putFloat(mResolver, PEAK_REFRESH_RATE, Float.POSITIVE_INFINITY);
    }

    @Override
    public void restore() {
        if (!mPrefs.contains(PREF_KEY)) {
            return;
        }
        final float value = mPrefs.getFloat(PREF_KEY, getDefaultPeakRefreshRate());
        Settings.System.putFloat(mResolver, PEAK_REFRESH_RATE, value);
    }

    private float getDefaultPeakRefreshRate() {
        float defaultPeakRefreshRate = DeviceConfig.getFloat(
                DeviceConfig.NAMESPACE_DISPLAY_MANAGER,
                DisplayManager.DeviceConfig.KEY_PEAK_REFRESH_RATE_DEFAULT,
                INVALIDATE_REFRESH_RATE);
        if (defaultPeakRefreshRate == INVALIDATE_REFRESH_RATE) {
            defaultPeakRefreshRate = (float) mContext.getResources().getInteger(
                    com.android.internal.R.integer.config_defaultPeakRefreshRate);
        }
        return defaultPeakRefreshRate;
    }
}
