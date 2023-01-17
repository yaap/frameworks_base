/*
* Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.doze;

import static android.content.Intent.ACTION_UPDATE_AOD;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.AmbientDisplayConfiguration;
import android.os.UserHandle;

import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.doze.dagger.DozeScope;

import javax.inject.Inject;

/**
 * Handles updating doze according to settings
 */
@DozeScope
public class DozeUpdater implements DozeMachine.Part {
    private static final String TAG = "DozeUpdater";

    private DozeMachine mMachine;
    private final DozeHost mDozeHost;
    private final AmbientDisplayConfiguration mConfig;
    private final BroadcastDispatcher mBroadcastDispatcher;

    private boolean mBroadcastReceiverRegistered;

    @Inject
    public DozeUpdater(
            DozeHost dozeHost,
            AmbientDisplayConfiguration config,
            BroadcastDispatcher broadcastDispatcher) {
        mDozeHost = dozeHost;
        mConfig = config;
        mBroadcastDispatcher = broadcastDispatcher;
    }

    @Override
    public void setDozeMachine(DozeMachine dozeMachine) {
        mMachine = dozeMachine;
    }

    @Override
    public void transitionTo(DozeMachine.State oldState, DozeMachine.State newState) {
        switch (newState) {
            case INITIALIZED:
                registerBroadcastReceiver();
                mDozeHost.addCallback(mHostCallback);
                break;
            case FINISH:
                destroy();
                break;
            default:
        }
    }

    @Override
    public void destroy() {
        unregisterBroadcastReceiver();
        mDozeHost.removeCallback(mHostCallback);
    }

    private void registerBroadcastReceiver() {
        if (mBroadcastReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(ACTION_UPDATE_AOD);
        mBroadcastDispatcher.registerReceiver(mBroadcastReceiver, filter);
        mBroadcastReceiverRegistered = true;
    }

    private void unregisterBroadcastReceiver() {
        if (!mBroadcastReceiverRegistered) {
            return;
        }
        mBroadcastDispatcher.unregisterReceiver(mBroadcastReceiver);
        mBroadcastReceiverRegistered = false;
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_UPDATE_AOD.equals(action)) {
                mMachine.requestState(mConfig.alwaysOnEnabled(UserHandle.USER_CURRENT)
                        ? DozeMachine.State.DOZE_AOD : DozeMachine.State.DOZE);
            }
        }
    };

    private DozeHost.Callback mHostCallback = new DozeHost.Callback() {
        @Override
        public void onAlwaysOnUpdate() {
            final DozeMachine.State nextState;
            if (mConfig.alwaysOnEnabled(UserHandle.USER_CURRENT)) {
                nextState = DozeMachine.State.DOZE_AOD;
            } else {
                nextState = DozeMachine.State.DOZE;
            }
            mMachine.requestState(nextState);
        }
    };
}
