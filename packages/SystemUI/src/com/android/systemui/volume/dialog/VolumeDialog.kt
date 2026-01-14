/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.volume.dialog

import android.content.res.Configuration
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import com.android.app.tracing.coroutines.coroutineScopeTraced
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.volume.Events
import com.android.systemui.volume.dialog.dagger.factory.VolumeDialogComponentFactory
import com.android.systemui.volume.dialog.domain.interactor.DesktopAudioTileDetailsFeatureInteractor
import com.android.systemui.volume.dialog.domain.interactor.VolumeDialogVisibilityInteractor
import javax.inject.Inject
import kotlinx.coroutines.awaitCancellation

class VolumeDialog
@Inject
constructor(
    @Application context: Context,
    private val componentFactory: VolumeDialogComponentFactory,
    private val visibilityInteractor: VolumeDialogVisibilityInteractor,
    private val configurationController: ConfigurationController,
    desktopAudioTileDetailsFeatureInteractor: DesktopAudioTileDetailsFeatureInteractor,
) : ComponentDialog(context, R.style.Theme_SystemUI_Dialog_Volume),
    ConfigurationController.ConfigurationListener {
    // Use horizontal volume dialog if the audio tile details view is enabled
    private val isVolumeDialogVertical = !desktopAudioTileDetailsFeatureInteractor.isEnabled()

    private val onLeftDefault: Boolean = context.resources.getBoolean(
        R.bool.config_audioPanelOnLeftSide);
    private var volumePanelOnLeft: Boolean = false
    private var volumePanelOnLeftLand: Boolean = false

    private val volumePanelOnLeftObserver =
    object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            val onLeft =
                Settings.System.getIntForUser(
                    context.contentResolver,
                    Settings.System.VOLUME_PANEL_ON_LEFT,
                    if (onLeftDefault) 1 else 0,
                    UserHandle.USER_CURRENT
                ) != 0
            val onLeftLand =
                Settings.System.getIntForUser(
                    context.contentResolver,
                    Settings.System.VOLUME_PANEL_ON_LEFT_LAND,
                    if (onLeftDefault) 1 else 0,
                    UserHandle.USER_CURRENT
                ) != 0
            if (volumePanelOnLeft != onLeft || volumePanelOnLeftLand != onLeftLand) {
                volumePanelOnLeft = onLeft
                volumePanelOnLeftLand = onLeftLand
                applyLayoutAndGravity()
            }
        }
    }

    private fun applyLayoutAndGravity() {
        val win = window ?: return
        val dialogView = win.decorView
        val isLeft = isLandscape() && volumePanelOnLeftLand ||
            !isLandscape() && volumePanelOnLeft

        dialogView.layoutDirection = if (isLeft) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

        if (isVolumeDialogVertical) {
            win.setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            win.setGravity(if (isLeft) Gravity.LEFT else Gravity.RIGHT)
        } else {
            win.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            val side = if (isLeft) Gravity.LEFT else Gravity.RIGHT
            win.setGravity(Gravity.TOP or side)
        }
    }

    init {
        with(window!!) {
            addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
            addPrivateFlags(WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
            setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY)
            setWindowAnimations(-1)

            attributes =
                attributes.apply {
                    title = "VolumeDialog" // Not the same as Window#setTitle
                }
        }

        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.VOLUME_PANEL_ON_LEFT),
            false,
            volumePanelOnLeftObserver,
            UserHandle.USER_ALL
        )
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.VOLUME_PANEL_ON_LEFT_LAND),
            false,
            volumePanelOnLeftObserver,
            UserHandle.USER_ALL
        )
        volumePanelOnLeftObserver.onChange(true)
        applyLayoutAndGravity()
        configurationController.addCallback(this)

        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isVolumeDialogVertical) {
            setContentView(R.layout.volume_dialog)
        } else {
            setContentView(R.layout.volume_dialog_horizontal)
        }
        requireViewById<View>(R.id.volume_dialog).repeatWhenAttached {
            coroutineScopeTraced("[Volume]dialog") {
                val component = componentFactory.create(this)
                with(component.volumeDialogViewBinder()) {
                    bind(this@VolumeDialog, isVolumeDialogVertical)
                }

                awaitCancellation()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        configurationController.removeCallback(this)
        context.contentResolver.unregisterContentObserver(volumePanelOnLeftObserver)
    }

    override fun onOrientationChanged(orientation: Int) {
        applyLayoutAndGravity()
    }

    /**
     * NOTE: This will be called with ACTION_OUTSIDE MotionEvents for touches that occur outside of
     * the touchable region of the volume dialog (as returned by [.onComputeInternalInsets]) even if
     * those touches occurred within the bounds of the volume dialog.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isShowing) {
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                visibilityInteractor.dismissDialog(Events.DISMISS_REASON_TOUCH_OUTSIDE)
                return true
            }
        }
        return false
    }

    private fun isLandscape(): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
}
