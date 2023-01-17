/**
 * Copyright (C) 2022 Yet Another AOSP Project
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

package com.android.server.power;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;

import com.android.internal.statusbar.IStatusBarService;

/**
 * Communicates with System UI to suppress the ambient display.
 */
public class AmbientDisplayStateController {
    private static final String TAG = "AmbientDisplayStateController";

    private final Context mContext;
    private final Object mLock = new Object();
    private IStatusBarService mStatusBarService;

    AmbientDisplayStateController(Context context) {
        mContext = requireNonNull(context);
    }

    /**
     * Updates ambient display state according to settings
     */
    public void update() {
        try {
            synchronized (mLock) {
                getStatusBar().updateAmbientDisplayState();
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to update ambient display state", e);
        }
    }

    private synchronized IStatusBarService getStatusBar() {
        if (mStatusBarService == null) {
            mStatusBarService = IStatusBarService.Stub.asInterface(
                    ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        }
        return mStatusBarService;
    }
}
