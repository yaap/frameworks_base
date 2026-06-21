/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.screencapture.record.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.Prefs
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screenrecord.ScreenRecordPermissionContentManager.Companion.PREF_HEVC
import com.android.systemui.screenrecord.ScreenRecordPermissionContentManager.Companion.PREF_LOW
import com.android.systemui.screenrecord.ScreenRecordPermissionContentManager.Companion.PREF_SKIP
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import javax.inject.Inject

@ScreenCaptureScope
class ScreenCaptureRecordParametersRepository
@Inject
constructor(@Application private val context: Context) {

    val isHevcAllowed: Boolean =
        context.resources.getBoolean(R.bool.config_screenRecordHEVC)

    var audioSource: ScreenRecordingAudioSource by mutableStateOf(ScreenRecordingAudioSource.NONE)
    var shouldShowTaps: Boolean by mutableStateOf(false)
    var shouldShowFrontCamera: Boolean by mutableStateOf(false)

    private val lowQualityState = mutableStateOf(Prefs.getInt(context, PREF_LOW, 0))
    var lowQuality: Int
        get() = lowQualityState.value
        set(value) {
            lowQualityState.value = value
            Prefs.putInt(context, PREF_LOW, value)
        }

    private val hevcState =
        mutableStateOf(isHevcAllowed && Prefs.getInt(context, PREF_HEVC, 1) == 1)
    var hevc: Boolean
        get() = hevcState.value
        set(value) {
            hevcState.value = value
            Prefs.putInt(context, PREF_HEVC, if (value) 1 else 0)
        }

    private val skipTimerState = mutableStateOf(Prefs.getInt(context, PREF_SKIP, 0) == 1)
    var skipTimer: Boolean
        get() = skipTimerState.value
        set(value) {
            skipTimerState.value = value
            Prefs.putInt(context, PREF_SKIP, if (value) 1 else 0)
        }
}
