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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;

import javax.inject.Inject;

public class DcDimTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "dc_dim";

    private static final String CONFIG = "config_showDcDimSettings";
    private static final String SETTINGS_PKG = "com.android.settings";
    private static final Intent SETTINGS_INTENT = new Intent(SETTINGS_PKG + ".DC_DIM_SETTINGS");

    private final SettingsObserver mSettingsObserver = new SettingsObserver(
            new Handler(Looper.getMainLooper()));
    private final String mNodePath;
    private final boolean mIsAvailable;

    private volatile boolean mSelfChange = false;

    @Inject
    public DcDimTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);

        mNodePath = mContext.getResources().getString(
                com.android.internal.R.string.config_dcdNodePath);

        // Get config_showDcDimSettings from Settings
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
        mIsAvailable = settingsRes.getBoolean(resId);
    }

    @Override
    public boolean isAvailable() {
        return mIsAvailable && mNodePath != null && !mNodePath.isEmpty();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick(@Nullable Expandable expandable) {
        if (!mIsAvailable || mNodePath == null || mNodePath.isEmpty()) return;
        final boolean newState = !mState.value;
        mSelfChange = true;
        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.DC_DIM_ENABLED, newState ? 1 : 0, UserHandle.USER_CURRENT);
        refreshState(newState);
    }

    @Override
    public Intent getLongClickIntent() {
        return SETTINGS_INTENT;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_dc_dim);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (!isAvailable()) {
            return;
        }

        boolean enable = state.value;
        if (arg instanceof Boolean) {
            enable = (Boolean) arg;
        } else {
            enable = getIsEnabled();
        }

        int resId = R.drawable.qs_dimming_off;
        int stateInt = Tile.STATE_INACTIVE;
        if (enable) {
            resId = R.drawable.qs_dimming_on;
            stateInt = Tile.STATE_ACTIVE;
        }
        state.icon = maybeLoadResourceIcon(resId);
        state.state = stateInt;
        state.label = getTileLabel();
        state.value = enable;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.YASP;
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (listening) {
            mSettingsObserver.observe();
            return;
        }
        mSettingsObserver.stop();
    }

    private boolean getIsEnabled() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.DC_DIM_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
    }

    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.DC_DIM_ENABLED),
                    false, this);
        }

        void stop() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (mSelfChange) {
                mSelfChange = false;
                return;
            }
            refreshState();
        }
    }
}
