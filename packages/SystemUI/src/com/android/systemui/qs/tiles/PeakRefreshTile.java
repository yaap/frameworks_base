/*
 * Copyright (C) 2018 FireHound
 *               2022-2023 Yet Another AOSP Project
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

package com.android.systemui.qs.tiles;

import static com.android.internal.display.RefreshRateSettingsUtils.DEFAULT_REFRESH_RATE;
import static com.android.internal.display.RefreshRateSettingsUtils.findHighestRefreshRateAmongAllDisplays;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.service.quicksettings.Tile;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.animation.Expandable;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.policy.KeyguardStateController;

import java.util.concurrent.Executor;

import javax.inject.Inject;

/** Quick settings tile: Peak refresh rate (Smooth Display) tile **/
public class PeakRefreshTile extends QSTileImpl<BooleanState> {
    public static final String TILE_SPEC = "peak_refresh";

    private static final String CONFIG = "config_show_smooth_display";
    private static final String SETTINGS_PKG = "com.android.settings";
    private static final Intent SETTINGS_INTENT = new Intent(SETTINGS_PKG + ".DISPLAY_SETTINGS");
    private static final float INVALIDATE_REFRESH_RATE = -1f;

    private final Handler mHandler;
    private final IDeviceConfigChange mOnDeviceConfigChange;
    private DeviceConfigDisplaySettings mDeviceConfigDisplaySettings;
    private float mPeakRefreshRate;

    private interface IDeviceConfigChange {
        void onDefaultRefreshRateChanged();
    }

    @Inject
    public PeakRefreshTile(QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            BroadcastDispatcher broadcastDispatcher,
            KeyguardStateController keyguardStateController
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);

        mHandler = mainHandler;
        mDeviceConfigDisplaySettings = new DeviceConfigDisplaySettings();
        mOnDeviceConfigChange =
                new IDeviceConfigChange() {
                    public void onDefaultRefreshRateChanged() {
                        refreshState();
                    }
                };
        mPeakRefreshRate = Math.round(findHighestRefreshRateAmongAllDisplays(mContext));
    }

    @Override
    public boolean isAvailable() {
        // Get config_show_smooth_display from Settings
        Context settingsContext;
        try {
            settingsContext = mContext.createPackageContext(SETTINGS_PKG,
                    Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
        } catch (NameNotFoundException e) {
            // Nothing to do, If Settings was not found you have bigger issues :)
            settingsContext = mContext;
        }
        Resources settingsRes = settingsContext.getResources();
        final int resId = settingsRes.getIdentifier(CONFIG, "bool", SETTINGS_PKG);
        final boolean isEnabled = settingsRes.getBoolean(resId);

        return isEnabled && mPeakRefreshRate > DEFAULT_REFRESH_RATE;
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick(@Nullable Expandable expandable) {
        final boolean newState = !mState.value;
        final float valueIfChecked = Float.POSITIVE_INFINITY;
        final float peakRefreshRate = newState ? valueIfChecked : DEFAULT_REFRESH_RATE;
        Settings.System.putFloat(mContext.getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, peakRefreshRate);
        refreshState(newState);
    }

    @Override
    public Intent getLongClickIntent() {
        return SETTINGS_INTENT;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        boolean enable = state.value;
        if (arg instanceof Boolean) {
            enable = (Boolean) arg;
        } else {
            enable = isEnabled();
        }
        state.icon = maybeLoadResourceIcon(R.drawable.ic_qs_refresh_rate);
        state.value = enable;
        state.label = getTileLabel();
        String subtitle = mContext.getString(R.string.peak_refresh_tile_subtitle);
        if (enable) {
            subtitle = String.format(subtitle, (int) mPeakRefreshRate);
            state.state = Tile.STATE_ACTIVE;
        } else {
            subtitle = String.format(subtitle, 60);
            state.state = Tile.STATE_INACTIVE;
        }
        state.secondaryLabel = subtitle;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.peak_refresh_tile_title);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.YASP;
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (listening) {
            mDeviceConfigDisplaySettings.startListening();
            refreshState();
            return;
        }
        mDeviceConfigDisplaySettings.stopListening();
    }

    private boolean isEnabled() {
        final float peakRefreshRate =
                Settings.System.getFloat(
                        mContext.getContentResolver(),
                        Settings.System.PEAK_REFRESH_RATE,
                        getDefaultPeakRefreshRate());
        return Math.round(peakRefreshRate) == Math.round(mPeakRefreshRate)
                || Float.isInfinite(peakRefreshRate);
    }

    private float getDefaultPeakRefreshRate() {
        float defaultPeakRefreshRate = mDeviceConfigDisplaySettings.getDefaultPeakRefreshRate();
        if (defaultPeakRefreshRate == INVALIDATE_REFRESH_RATE) {
            defaultPeakRefreshRate = (float) mContext.getResources().getInteger(
                    com.android.internal.R.integer.config_defaultPeakRefreshRate);
        }
        return defaultPeakRefreshRate;
    }

    private class DeviceConfigDisplaySettings
            implements DeviceConfig.OnPropertiesChangedListener, Executor {
        public void startListening() {
            DeviceConfig.addOnPropertiesChangedListener(
                    DeviceConfig.NAMESPACE_DISPLAY_MANAGER,
                    this /* Executor */,
                    this /* Listener */);
        }

        public void stopListening() {
            DeviceConfig.removeOnPropertiesChangedListener(this);
        }

        public float getDefaultPeakRefreshRate() {
            float defaultPeakRefreshRate =
                    DeviceConfig.getFloat(
                            DeviceConfig.NAMESPACE_DISPLAY_MANAGER,
                            DisplayManager.DeviceConfig.KEY_PEAK_REFRESH_RATE_DEFAULT,
                            INVALIDATE_REFRESH_RATE);

            return defaultPeakRefreshRate;
        }

        @Override
        public void onPropertiesChanged(DeviceConfig.Properties properties) {
            // Got notified if any property has been changed in NAMESPACE_DISPLAY_MANAGER. The
            // KEY_PEAK_REFRESH_RATE_DEFAULT value could be added, changed, removed or unchanged.
            // Just force a UI update for any case.
            if (mOnDeviceConfigChange != null) {
                mOnDeviceConfigChange.onDefaultRefreshRateChanged();
                refreshState();
            }
        }

        @Override
        public void execute(Runnable runnable) {
            if (mHandler != null) {
                mHandler.post(runnable);
            }
        }
    }
}
