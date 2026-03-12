/*
 * Copyright (C) 2024-2025 crDroid Android Project
 * Copyright (C) 2026 VoltageOS
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
 *
 */

package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.customization.clocks.R as clocksR
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds
import com.android.systemui.res.R
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController
import com.android.systemui.weather.WeatherInfoView
import com.android.systemui.weather.WeatherViewController
import javax.inject.Inject

class KeyguardWeatherViewSection
@Inject
constructor(
    private val context: Context,
    private val activityStarter: ActivityStarter,
    val layoutInflater: LayoutInflater,
    val smartspaceController: LockscreenSmartspaceController,
    val keyguardClockViewModel: KeyguardClockViewModel,
) : KeyguardSection() {

    private var weatherView: WeatherInfoView? = null

    private var weatherInlineView: TextView? = null
    private var weatherInlineController: WeatherViewController? = null
    private var attachedConstraintLayout: ConstraintLayout? = null
    private var weatherStyleObserver: ContentObserver? = null
    private var burnInSyncRunnable: Runnable? = null

    private fun isEnabled() =
        smartspaceController.isOmniWeatherEnabled && !smartspaceController.isEnabled

    private fun isModern() = isEnabled() && smartspaceController.isOmniWeatherModern

    private fun ensureWeatherStyleObserver() {
        if (weatherStyleObserver != null) return

        weatherStyleObserver =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    attachedConstraintLayout?.post {
                        refreshLayout()
                    }
                }
            }.also {
                context.contentResolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.LOCKSCREEN_WEATHER_STYLE),
                    false,
                    it,
                )
            }
    }

    private fun clearWeatherStyleObserver() {
        weatherStyleObserver?.let(context.contentResolver::unregisterContentObserver)
        weatherStyleObserver = null
    }

    private fun ensureViews(constraintLayout: ConstraintLayout) {
        if (weatherView == null) {
            weatherView =
                layoutInflater.inflate(
                    R.layout.keyguard_weather_area, null, false,
                ) as WeatherInfoView
            constraintLayout.addView(weatherView)
            weatherView?.setActivityStarter(activityStarter)
        }

        if (weatherInlineView == null) {
            weatherInlineView =
                layoutInflater.inflate(
                    R.layout.keyguard_weather_area_inline, null, false,
                ) as TextView
            constraintLayout.addView(weatherInlineView)
        }
    }

    private fun bindClassicIfNeeded() {
        weatherInlineController?.destroy()
        weatherInlineController = null
        weatherInlineView?.visibility = View.GONE
        weatherInlineView?.text = ""
        weatherInlineView?.setCompoundDrawablesRelative(null, null, null, null)
        weatherView?.init()
        weatherView?.visibility = View.VISIBLE
    }

    private fun bindInlineIfNeeded() {
        weatherView?.cleanup()
        weatherView?.visibility = View.GONE
        val inlineView = weatherInlineView ?: return

        if (weatherInlineController == null) {
            weatherInlineController =
                WeatherViewController(
                    context = context,
                    weatherIcon = null,
                    weatherTemp = null,
                    weatherInfoView = null,
                    weatherInlineView = inlineView,
                )
            weatherInlineController?.init()
        }
        inlineView.visibility = View.VISIBLE
    }

    private fun refreshLayout() {
        val layout = attachedConstraintLayout ?: return
        if (!isEnabled()) return

        ensureViews(layout)
        if (isModern()) bindInlineIfNeeded() else bindClassicIfNeeded()

        val updated = ConstraintSet()
        updated.clone(layout)
        applyConstraints(updated)
        updated.applyTo(layout)
        layout.requestLayout()
    }

    private fun removeAllWeatherViews(constraintLayout: ConstraintLayout) {
        clearWeatherStyleObserver()
        attachedConstraintLayout = null

        weatherInlineController?.destroy()
        weatherInlineController = null
        weatherInlineView?.let { constraintLayout.removeView(it) }
        weatherInlineView = null

        constraintLayout.findViewById<WeatherInfoView?>(R.id.keyguard_weather_area)?.let {
            it.cleanup()
            constraintLayout.removeView(it)
        }
        weatherView = null
    }

    override fun addViews(constraintLayout: ConstraintLayout) {
        removeAllWeatherViews(constraintLayout)
        if (!isEnabled()) return

        weatherInlineView = layoutInflater.inflate(
            R.layout.keyguard_weather_area_inline, null, false,
        ) as TextView
        constraintLayout.addView(weatherInlineView)

        val view = layoutInflater.inflate(
            R.layout.keyguard_weather_area, null, false,
        ) as WeatherInfoView
        weatherView = view
        constraintLayout.addView(view)
        view.setActivityStarter(activityStarter)
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        if (!isEnabled()) return

        attachedConstraintLayout = constraintLayout
        ensureWeatherStyleObserver()
        ensureViews(constraintLayout)
        if (isModern()) bindInlineIfNeeded() else bindClassicIfNeeded()
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!isEnabled()) return

        ensureWeatherStyleObserver()

        val marginStart =
            context.resources.getDimensionPixelSize(clocksR.dimen.clock_padding_start) +
                context.resources.getDimensionPixelSize(clocksR.dimen.status_view_margin_horizontal)
        
        val weatherGap = (8 * context.resources.displayMetrics.density).toInt()
        val smallClockGap = (12 * context.resources.displayMetrics.density).toInt()
        val isLargeClock = keyguardClockViewModel.isLargeClockVisible.value
        val modern = isModern()

        if (modern) {
            bindInlineIfNeeded()
            constraintSet.apply {
                setVisibility(R.id.weather_inline_text, View.VISIBLE)
                setVisibility(R.id.keyguard_weather_area, View.GONE)
                clear(R.id.keyguard_weather_area, ConstraintSet.START)
                clear(R.id.keyguard_weather_area, ConstraintSet.END)
                clear(R.id.keyguard_weather_area, ConstraintSet.TOP)
                clear(R.id.keyguard_weather_area, ConstraintSet.BOTTOM)
                constrainHeight(R.id.weather_inline_text, ConstraintSet.WRAP_CONTENT)
                constrainWidth(R.id.weather_inline_text, ConstraintSet.WRAP_CONTENT)

                if (!isLargeClock) {
                    weatherInlineView?.setPaddingRelative(smallClockGap, 0, 0, 0)

                    clear(R.id.weather_inline_text, ConstraintSet.START)
                    clear(R.id.weather_inline_text, ConstraintSet.END)
                    clear(R.id.weather_inline_text, ConstraintSet.TOP)
                    clear(R.id.weather_inline_text, ConstraintSet.BOTTOM)

                    connect(
                        R.id.weather_inline_text, ConstraintSet.START,
                        ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL, ConstraintSet.END,
                        0,
                    )
                    connect(
                        R.id.weather_inline_text, ConstraintSet.END,
                        ConstraintSet.PARENT_ID, ConstraintSet.END,
                        0,
                    )
                    setHorizontalBias(R.id.weather_inline_text, 0.0f)

                    connect(
                        R.id.weather_inline_text, ConstraintSet.TOP,
                        R.id.keyguard_slice_view, ConstraintSet.BOTTOM,
                    )
                    connect(
                        R.id.weather_inline_text, ConstraintSet.BOTTOM,
                        ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL, ConstraintSet.BOTTOM,
                    )
                } else {
                    weatherInlineView?.setPaddingRelative(0, 0, 0, 0)
                    clear(R.id.weather_inline_text, ConstraintSet.START)
                    clear(R.id.weather_inline_text, ConstraintSet.END)
                    clear(R.id.weather_inline_text, ConstraintSet.TOP)
                    clear(R.id.weather_inline_text, ConstraintSet.BOTTOM)

                    connect(
                        R.id.weather_inline_text, ConstraintSet.START,
                        R.id.keyguard_slice_view, ConstraintSet.END,
                        weatherGap,
                    )
                    connect(
                        R.id.weather_inline_text, ConstraintSet.END,
                        ConstraintSet.PARENT_ID, ConstraintSet.END,
                    )
                    setHorizontalBias(R.id.weather_inline_text, 0.5f)
                    
                    connect(
                        R.id.weather_inline_text, ConstraintSet.TOP,
                        R.id.keyguard_slice_view, ConstraintSet.TOP,
                    )
                    connect(
                        R.id.weather_inline_text, ConstraintSet.BOTTOM,
                        R.id.keyguard_slice_view, ConstraintSet.BOTTOM,
                    )
                }
            }
        } else {
            weatherInlineView?.setPaddingRelative(0, 0, 0, 0)
            constraintSet.apply {
                setVisibility(R.id.weather_inline_text, View.GONE)
                clear(R.id.weather_inline_text, ConstraintSet.START)
                clear(R.id.weather_inline_text, ConstraintSet.END)
                clear(R.id.weather_inline_text, ConstraintSet.TOP)
                clear(R.id.weather_inline_text, ConstraintSet.BOTTOM)
                clear(R.id.keyguard_weather_area, ConstraintSet.START)
                clear(R.id.keyguard_weather_area, ConstraintSet.END)
                clear(R.id.keyguard_weather_area, ConstraintSet.TOP)
                clear(R.id.keyguard_weather_area, ConstraintSet.BOTTOM)

                setVisibility(R.id.keyguard_weather_area, View.VISIBLE)

                connect(
                    R.id.keyguard_weather_area, ConstraintSet.START,
                    ConstraintSet.PARENT_ID, ConstraintSet.START,
                    marginStart,
                )
                connect(
                    R.id.keyguard_weather_area, ConstraintSet.END,
                    ConstraintSet.PARENT_ID, ConstraintSet.END,
                )
                constrainHeight(R.id.keyguard_weather_area, ConstraintSet.WRAP_CONTENT)
                connect(
                    R.id.keyguard_weather_area, ConstraintSet.TOP,
                    R.id.keyguard_slice_view, ConstraintSet.BOTTOM,
                )
            }
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        removeAllWeatherViews(constraintLayout)
    }
}
