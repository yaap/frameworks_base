/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.keyguard.KeyguardSliceView
import com.android.keyguard.KeyguardSliceViewController
import com.android.systemui.customization.clocks.R as clocksR
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dump.DumpManager
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.keyguard.ui.binder.KeyguardSliceViewBinder
import com.android.systemui.keyguard.ui.viewmodel.AodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardSmartspaceViewModel
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds
import com.android.systemui.res.R
import com.android.systemui.settings.DisplayTracker
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController
import com.android.systemui.statusbar.policy.ConfigurationController
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.DisposableHandle

class KeyguardSliceViewSection
@Inject
constructor(
    @ShadeDisplayAware val context: Context,
    val smartspaceController: LockscreenSmartspaceController,
    val layoutInflater: LayoutInflater,
    @Main val handler: Handler,
    @Background val bgHandler: Handler,
    val activityStarter: ActivityStarter,
    val keyguardClockViewModel: KeyguardClockViewModel,
    val configurationController: ConfigurationController,
    val dumpManager: DumpManager,
    val displayTracker: DisplayTracker,
    val keyguardInteractor: KeyguardInteractor,
    val aodBurnInViewModel: AodBurnInViewModel,
    val keyguardSmartspaceViewModel: KeyguardSmartspaceViewModel,
    val powerInteractor: PowerInteractor,
) : KeyguardSection() {
    private lateinit var sliceView: KeyguardSliceView
    private var dateView: TextView? = null
    private var disposableHandle: DisposableHandle? = null
    private var dateDisposableHandle: DisposableHandle? = null

    private var weatherStyleObserver: ContentObserver? = null

    private fun ensureWeatherStyleObserver() {
        if (weatherStyleObserver != null || smartspaceController.isEnabled) return

        weatherStyleObserver =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    val layout = sliceView.parent as? ConstraintLayout ?: return
                    layout.post {
                        val parent = sliceView.parent as? ConstraintLayout ?: return@post
                        if (smartspaceController.isOmniWeatherModern) {
                            sliceView.setTitleVisible(false)
                            updateDateView()
                        } else {
                            sliceView.setTitleVisible(true)
                        }
                        val updated = ConstraintSet()
                        updated.clone(parent)
                        applyConstraints(updated)
                        updated.applyTo(parent)
                        parent.requestLayout()
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

    private fun updateDateView() {
        val dv = dateView ?: return
        val pattern = context.getString(R.string.system_ui_aod_date_pattern)
        val format = DateFormat.getInstanceForSkeleton(pattern, Locale.getDefault())
        format.setContext(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)
        dv.text = format.format(Date())
    }

    override fun addViews(constraintLayout: ConstraintLayout) {
        if (smartspaceController.isEnabled) return
        sliceView =
            layoutInflater.inflate(R.layout.keyguard_slice_view, null, false) as KeyguardSliceView
        constraintLayout.addView(sliceView)
        dateView =
            layoutInflater.inflate(R.layout.keyguard_date_view, null, false) as TextView
        constraintLayout.addView(dateView)
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        if (smartspaceController.isEnabled) return
        val controller =
            KeyguardSliceViewController(
                handler,
                bgHandler,
                sliceView,
                activityStarter,
                configurationController,
                dumpManager,
                displayTracker,
                keyguardInteractor,
                powerInteractor,
            )
        // Hide title before any showSlice() call from init/binding
        if (smartspaceController.isOmniWeatherModern) {
            sliceView.setTitleVisible(false)
        }

        controller.setupUri(null)
        controller.init()

        disposableHandle?.dispose()
        disposableHandle =
            KeyguardSliceViewBinder.bind(
                sliceView,
                keyguardInteractor,
                controller,
                aodBurnInViewModel,
            )

        dateView = constraintLayout.findViewById(R.id.keyguard_date_view)

        if (smartspaceController.isOmniWeatherModern) {
            updateDateView()
            sliceView.setContentChangeListener {
                if (smartspaceController.isOmniWeatherModern) {
                    sliceView.setTitleVisible(false)
                    val dateText = dateView?.text?.toString()
                    if (dateText != null) {
                        val row = sliceView.findViewById<View>(R.id.row)
                        if (row is ViewGroup) {
                            for (i in 0 until row.childCount) {
                                val child = row.getChildAt(i)
                                if (child is TextView && child.text?.toString() == dateText) {
                                    child.visibility = View.GONE
                                }
                            }
                        }
                    }
                    updateDateView()
                }
            }
        }

        dateDisposableHandle?.dispose()
        dateDisposableHandle =
            sliceView.repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    launch {
                        keyguardInteractor.dozeTimeTick.collect {
                            if (smartspaceController.isOmniWeatherModern) {
                                updateDateView()
                            }
                        }
                    }
                }
            }
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (smartspaceController.isEnabled) return

        ensureWeatherStyleObserver()

        val isModern = smartspaceController.isOmniWeatherModern
        val isLargeClock = keyguardClockViewModel.isLargeClockVisible.value
        val marginStart =
            context.resources.getDimensionPixelSize(clocksR.dimen.clock_padding_start) +
                context.resources.getDimensionPixelSize(clocksR.dimen.status_view_margin_horizontal)

        val clockGap = (16 * context.resources.displayMetrics.density).toInt()
        val smallClockGap = (12 * context.resources.displayMetrics.density).toInt()
        val weatherGap = (8 * context.resources.displayMetrics.density).toInt()

        constraintSet.apply {
            constrainHeight(R.id.keyguard_slice_view, ConstraintSet.WRAP_CONTENT)

            when {
                isModern && !isLargeClock -> {
                    // Date view: inline right of small clock
                    setVisibility(R.id.keyguard_date_view, View.VISIBLE)
                    constrainWidth(R.id.keyguard_date_view, ConstraintSet.WRAP_CONTENT)
                    constrainHeight(R.id.keyguard_date_view, ConstraintSet.WRAP_CONTENT)
                    clear(R.id.keyguard_date_view, ConstraintSet.START)
                    clear(R.id.keyguard_date_view, ConstraintSet.END)
                    clear(R.id.keyguard_date_view, ConstraintSet.TOP)
                    clear(R.id.keyguard_date_view, ConstraintSet.BOTTOM)
                    connect(
                        R.id.keyguard_date_view, ConstraintSet.START,
                        ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL, ConstraintSet.END,
                        smallClockGap,
                    )
                    connect(
                        R.id.keyguard_date_view, ConstraintSet.TOP,
                        ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL, ConstraintSet.TOP,
                    )
                    connect(
                        R.id.keyguard_date_view, ConstraintSet.BOTTOM,
                        R.id.weather_inline_text, ConstraintSet.TOP,
                        weatherGap,
                    )
                    connect(
                        R.id.keyguard_date_view, ConstraintSet.END,
                        ConstraintSet.PARENT_ID, ConstraintSet.END,
                    )
                    setHorizontalBias(R.id.keyguard_date_view, 0.0f)
                    setVerticalChainStyle(R.id.keyguard_date_view, ConstraintSet.CHAIN_PACKED)
                    setVerticalBias(R.id.keyguard_date_view, 0.5f)

                    // Slice view: full width below small clock (old position)
                    sliceView.setPaddingRelative(0, 0, 0, 0)
                    constrainWidth(R.id.keyguard_slice_view, ConstraintSet.MATCH_CONSTRAINT)
                    clear(R.id.keyguard_slice_view, ConstraintSet.START)
                    clear(R.id.keyguard_slice_view, ConstraintSet.END)
                    clear(R.id.keyguard_slice_view, ConstraintSet.TOP)
                    clear(R.id.keyguard_slice_view, ConstraintSet.BOTTOM)
                    connect(
                        R.id.keyguard_slice_view, ConstraintSet.START,
                        ConstraintSet.PARENT_ID, ConstraintSet.START,
                        marginStart,
                    )
                    connect(
                        R.id.keyguard_slice_view, ConstraintSet.END,
                        ConstraintSet.PARENT_ID, ConstraintSet.END,
                    )
                    connect(
                        R.id.keyguard_slice_view, ConstraintSet.TOP,
                        ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL, ConstraintSet.BOTTOM,
                    )
                }

                isModern && isLargeClock -> {
                    // Date view: inline below big clock
                    setVisibility(R.id.keyguard_date_view, View.VISIBLE)
                    constrainWidth(R.id.keyguard_date_view, ConstraintSet.WRAP_CONTENT)
                    constrainHeight(R.id.keyguard_date_view, ConstraintSet.WRAP_CONTENT)
                    clear(R.id.keyguard_date_view, ConstraintSet.START)
                    clear(R.id.keyguard_date_view, ConstraintSet.END)
                    clear(R.id.keyguard_date_view, ConstraintSet.TOP)
                    clear(R.id.keyguard_date_view, ConstraintSet.BOTTOM)
                    connect(
                        R.id.keyguard_date_view, ConstraintSet.START,
                        ConstraintSet.PARENT_ID, ConstraintSet.START,
                        0,
                    )
                    connect(
                        R.id.keyguard_date_view, ConstraintSet.END,
                        R.id.weather_inline_text, ConstraintSet.START,
                        0,
                    )
                    connect(
                        R.id.keyguard_date_view, ConstraintSet.TOP,
                        ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE, ConstraintSet.BOTTOM,
                        clockGap,
                    )
                    setHorizontalChainStyle(R.id.keyguard_date_view, ConstraintSet.CHAIN_PACKED)
                    setHorizontalBias(R.id.keyguard_date_view, 0.5f)

                    // Slice view: upper-left below small clock (old position)
                    sliceView.setPaddingRelative(0, 0, 0, 0)
                    constrainWidth(R.id.keyguard_slice_view, ConstraintSet.MATCH_CONSTRAINT)
                    clear(R.id.keyguard_slice_view, ConstraintSet.START)
                    clear(R.id.keyguard_slice_view, ConstraintSet.END)
                    clear(R.id.keyguard_slice_view, ConstraintSet.TOP)
                    clear(R.id.keyguard_slice_view, ConstraintSet.BOTTOM)
                    connect(
                        R.id.keyguard_slice_view, ConstraintSet.START,
                        ConstraintSet.PARENT_ID, ConstraintSet.START,
                        marginStart,
                    )
                    connect(
                        R.id.keyguard_slice_view, ConstraintSet.END,
                        ConstraintSet.PARENT_ID, ConstraintSet.END,
                    )
                    connect(
                        R.id.keyguard_slice_view, ConstraintSet.TOP,
                        ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL, ConstraintSet.BOTTOM,
                    )
                }

                else -> {
                    // Classic / non-modern
                    setVisibility(R.id.keyguard_date_view, View.GONE)
                    clear(R.id.keyguard_date_view, ConstraintSet.START)
                    clear(R.id.keyguard_date_view, ConstraintSet.END)
                    clear(R.id.keyguard_date_view, ConstraintSet.TOP)
                    clear(R.id.keyguard_date_view, ConstraintSet.BOTTOM)

                    sliceView.setPaddingRelative(0, 0, 0, 0)
                    constrainWidth(R.id.keyguard_slice_view, ConstraintSet.MATCH_CONSTRAINT)
                    clear(R.id.keyguard_slice_view, ConstraintSet.START)
                    clear(R.id.keyguard_slice_view, ConstraintSet.END)
                    clear(R.id.keyguard_slice_view, ConstraintSet.TOP)
                    clear(R.id.keyguard_slice_view, ConstraintSet.BOTTOM)
                    connect(
                        R.id.keyguard_slice_view, ConstraintSet.START,
                        ConstraintSet.PARENT_ID, ConstraintSet.START,
                        marginStart,
                    )
                    connect(
                        R.id.keyguard_slice_view, ConstraintSet.END,
                        ConstraintSet.PARENT_ID, ConstraintSet.END,
                    )
                    connect(
                        R.id.keyguard_slice_view, ConstraintSet.TOP,
                        ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL, ConstraintSet.BOTTOM,
                    )
                }
            }

            val barrierIds = mutableListOf(R.id.keyguard_slice_view)
            if (smartspaceController.isOmniWeatherEnabled) {
                if (isModern) {
                    barrierIds.add(R.id.weather_inline_text)
                } else {
                    barrierIds.add(R.id.keyguard_weather_area)
                }
            }

            createBarrier(
                R.id.smart_space_barrier_bottom,
                Barrier.BOTTOM,
                0,
                *barrierIds.toIntArray()
            )
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        if (smartspaceController.isEnabled) return
        clearWeatherStyleObserver()
        disposableHandle?.dispose()
        dateDisposableHandle?.dispose()
        constraintLayout.removeView(R.id.keyguard_slice_view)
        constraintLayout.findViewById<View>(R.id.keyguard_date_view)?.let {
            constraintLayout.removeView(it)
        }
        dateView = null
    }
}
