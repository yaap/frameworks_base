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
import android.media.AudioManager;
import android.provider.Settings;

public class RingerAction extends GamingMacroAction {
    private static final String PREF_KEY = "gmaing_mode_ringer_mode";

    private final AudioManager mAudioManager;

    public RingerAction(Context context, SharedPreferences prefs, String settingKey) {
        super(context, prefs, settingKey);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void saveState(SharedPreferences.Editor editor) {
        final int value = mAudioManager.getRingerModeInternal();
        editor.putInt(PREF_KEY, value);
    }

    @Override
    public void apply() {
        final int value = Settings.System.getInt(
                mResolver, mSettingKey, mDefaultValue);
        int mode = -1;
        if (value == 1) {
            mode = AudioManager.RINGER_MODE_VIBRATE;
        } else if (value == 2) {
            mode = AudioManager.RINGER_MODE_SILENT;
        }
        // if we somehow have an invalid setting value stay at the same mode
        if (mode != -1) {
            mAudioManager.setRingerModeInternal(mode);
        }
    }

    @Override
    public void restore() {
        if (!mPrefs.contains(PREF_KEY)) {
            return;
        }
        final int value = mPrefs.getInt(PREF_KEY,
                AudioManager.RINGER_MODE_NORMAL);
        mAudioManager.setRingerModeInternal(value);
    }
}
