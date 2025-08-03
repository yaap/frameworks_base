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

import static android.media.AudioManager.FLAG_SHOW_UI;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.provider.Settings.System.GAMING_MODE_MEDIA;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.provider.Settings;

public class MediaLevelAction extends GamingMacroAction {
    private static final String PREF_KEY = "gaming_mode_level_media";

    private final AudioManager mAudioManager;

    public MediaLevelAction(Context context, SharedPreferences prefs, String settingKey) {
        super(context, prefs, settingKey);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void saveState(SharedPreferences.Editor editor) {
        // save current volume as percentage
        // we can restore it this way even if vol steps were changed during runtime
        final int max = mAudioManager.getStreamMaxVolume(STREAM_MUSIC);
        final int curr = mAudioManager.getStreamVolume(STREAM_MUSIC);
        editor.putInt(PREF_KEY, Math.round((float) curr * 100f / (float) max));
    }

    @Override
    public void apply() {
        final int value = Settings.System.getInt(mResolver, GAMING_MODE_MEDIA, 80);
        final int max = mAudioManager.getStreamMaxVolume(STREAM_MUSIC);
        final int level = Math.round((float) max * (float) value / 100f);
        mAudioManager.setStreamVolume(STREAM_MUSIC, level, FLAG_SHOW_UI);
    }

    @Override
    public void restore() {
        if (!mPrefs.contains(PREF_KEY)) {
            return;
        }
        final int value = mPrefs.getInt(PREF_KEY, 80);
        final int max = mAudioManager.getStreamMaxVolume(STREAM_MUSIC);
        final int volume = Math.round((float) max * (float) value / 100f);
        mAudioManager.setStreamVolume(STREAM_MUSIC, volume, FLAG_SHOW_UI);
    }
}
