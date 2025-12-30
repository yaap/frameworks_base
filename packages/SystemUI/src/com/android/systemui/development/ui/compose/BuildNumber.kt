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

package com.android.systemui.development.ui.compose

import android.content.Context
import android.database.ContentObserver
import android.os.UserHandle
import android.provider.Settings
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.systemui.communal.ui.compose.extensions.detectLongPressGesture
import com.android.systemui.development.ui.viewmodel.BuildNumberViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.ui.compose.borderOnFocus
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog

@Composable
fun BuildNumber(
    viewModelFactory: BuildNumberViewModel.Factory,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val viewModel = rememberViewModel(traceName = "BuildNumber") { viewModelFactory.create() }

    BuildNumber(viewModel, modifier, textColor)
}

@Composable
fun BuildNumber(
    viewModel: BuildNumberViewModel,
    modifier: Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var shouldShowBuildText by remember {
        mutableStateOf(
            try {
                Settings.System.getIntForUser(
                    context.contentResolver,
                    Settings.System.QS_FOOTER_TEXT_SHOW, 0,
                    UserHandle.USER_CURRENT
                ) == 1
            } catch (_: Throwable) {
                false
            }
        )
    }

    DisposableEffect(Unit) {
        val toggleObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                context.mainExecutor.execute {
                    shouldShowBuildText = try {
                        Settings.System.getIntForUser(
                            context.contentResolver,
                            Settings.System.QS_FOOTER_TEXT_SHOW, 0,
                            UserHandle.USER_CURRENT
                        ) != 0
                    } catch (_: Throwable) {
                        false
                    }
                }
            }
        }

        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.QS_FOOTER_TEXT_SHOW),
            false, toggleObserver, UserHandle.USER_ALL)

        onDispose {
            context.contentResolver.unregisterContentObserver(toggleObserver)
        }
    }

    var text by remember {
        mutableStateOf(
            try {
                Settings.System.getStringForUser(
                    context.contentResolver,
                    Settings.System.QS_FOOTER_TEXT_STRING,
                    UserHandle.USER_CURRENT
                )
            } catch (_: Throwable) {
                ""
            }
        )
    }

    DisposableEffect(Unit) {
        val textObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                context.mainExecutor.execute {
                    text = try {
                        Settings.System.getStringForUser(
                            context.contentResolver,
                            Settings.System.QS_FOOTER_TEXT_STRING,
                            UserHandle.USER_CURRENT
                        )
                    } catch (_: Throwable) {
                        ""
                    }
                }
            }
        }

        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.QS_FOOTER_TEXT_STRING),
            false, textObserver, UserHandle.USER_ALL)

        onDispose {
            context.contentResolver.unregisterContentObserver(textObserver)
        }
    }

    if (text != null)
    {
        var textToUse = ""
        if (shouldShowBuildText) {
            textToUse = text
            if (textToUse.isEmpty()) textToUse = "YAAP"
        }

        Text(
            text = textToUse,
            style = MaterialTheme.typography.bodySmall,
            modifier =
                modifier
                    .borderOnFocus(
                        color = MaterialTheme.colorScheme.secondary,
                        cornerSize = CornerSize(1.dp),
                    )
                    .focusable()
                    .wrapContentWidth()
                    .combinedClickable(
                        onClick = {
                            Toast.makeText(context, R.string.qs_footer_dialog_toast,
                                Toast.LENGTH_SHORT).show()
                        },
                        onLongClick = {
                            if (shouldShowBuildText) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                showFooterEditDialog(context, viewModel)
                            }
                        }
                    )
                    .basicMarquee(iterations = 1, initialDelayMillis = 2000)
                    .minimumInteractiveComponentSize(),
            color = textColor,
            maxLines = 1,
        )
    } else {
        Spacer(modifier)
    }
}

private fun setFooterText(context: Context, text: String) {
    Settings.System.putStringForUser(
        context.contentResolver,
        Settings.System.QS_FOOTER_TEXT_STRING, text,
        UserHandle.USER_CURRENT
    )
}

private fun showFooterEditDialog(context: Context, viewModel: BuildNumberViewModel) {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val dialog = viewModel.getSystemUIDialogFactory().create()
    val editText = EditText(context)
    var text = try {
        Settings.System.getStringForUser(
            context.contentResolver,
            Settings.System.QS_FOOTER_TEXT_STRING,
            UserHandle.USER_CURRENT
        )
    } catch (_: Throwable) {
        ""
    }
    if (text.isEmpty()) text = "YAAP"

    val lp = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.MATCH_PARENT)
    editText.layoutParams = lp
    editText.hint = "YAAP"
    editText.setText(text, TextView.BufferType.EDITABLE)
    editText.setSelectAllOnFocus(true)
    editText.setSingleLine(true)
    editText.imeOptions = EditorInfo.IME_ACTION_DONE
    editText.setRawInputType(InputType.TYPE_CLASS_TEXT)
    editText.setOnEditorActionListener { view, actionId, event ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            setFooterText(context, editText.text.toString())
            dialog.dismiss()
        }
        true
    }

    dialog.setTitle(R.string.qs_footer_dialog_title)
    dialog.setPositiveButton(com.android.internal.R.string.ok) { d, w ->
        setFooterText(context, editText.text.toString())
    }
    dialog.setOnShowListener { d ->
        editText.requestFocus()
    }
    SystemUIDialog.registerDismissListener(dialog) {
        imm?.hideSoftInputFromWindow(editText.windowToken, 0)
    }
    dialog.setNegativeButton(R.string.cancel, null)
    dialog.setCanceledOnTouchOutside(true)
    dialog.setView(editText)
    dialog.window?.clearFlags(
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    dialog.window?.setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    dialog.show()
}
