/*
 * Copyright (C) 2023 Yet Another AOSP Project
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
package com.android.server.display;

import static android.provider.Settings.Secure.DC_DIM_AUTO_MODE;
import static android.provider.Settings.Secure.DC_DIM_AUTO_TIME;
import static com.android.internal.util.yaap.AutoSettingConsts.MODE_DISABLED;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.server.AutoSettingService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AutoDCDimService extends AutoSettingService {

    private static final String TAG = "AutoDCDimService";
    private static final String ACTION_DCMODE_CHANGED = "com.yaap.device.DeviceSettings.ModeSwitch.DCMODE_CHANGED";
    private static final String EXTRA_DCMODE_STATE = "enabled";
    private static final String ON = "1";
    private static final String OFF = "0";

    private final String mNodePath;
    private final Uri mNodeUri;
    private final List<Uri> mListenUris = new ArrayList<>(List.of(
        Settings.Secure.getUriFor(DC_DIM_AUTO_MODE),
        Settings.Secure.getUriFor(DC_DIM_AUTO_TIME)
    ));

    public AutoDCDimService(Context context) {
        super(context);
        mNodePath = context.getResources().getString(
                com.android.internal.R.string.config_dcdNodePath);
        if (mNodePath != null && !mNodePath.isEmpty()) {
            mNodeUri = Uri.fromFile(new File(mNodePath));
            mListenUris.add(0, mNodeUri);
        } else {
            mNodeUri = null;
        }
    }

    @Override
    public void publish() {
        if (mNodePath == null || mNodePath.isEmpty()) return;
        publishLocalService(AutoDCDimService.class, this);
    }

    @Override
    public List<Uri> getObserveUris() {
        return mListenUris;
    }

    @Override
    public String getMainSetting() {
        return mNodePath;
    }

    @Override
    public int getModeValue() {
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                DC_DIM_AUTO_MODE, MODE_DISABLED,
                UserHandle.USER_CURRENT);
    }

    @Override
    public String getTimeValue() {
        return Settings.Secure.getStringForUser(mContext.getContentResolver(),
                DC_DIM_AUTO_TIME, UserHandle.USER_CURRENT);
    }

    @Override
    public boolean getIsActive() {
        if (mNodePath == null || mNodePath.isEmpty()) return false;
        return getFileValueAsBoolean(mNodePath, false);
    }

    @Override
    public void setActive(boolean active) {
        if (mNodePath == null || mNodePath.isEmpty()) return;
        writeValue(mNodePath, active ? ON : OFF);
        Intent intent = new Intent(ACTION_DCMODE_CHANGED);
        intent.putExtra(EXTRA_DCMODE_STATE, active);
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
    }

    @Override
    public String getTag() {
        return TAG;
    }

    private static boolean getFileValueAsBoolean(String filename, boolean defValue) {
        String fileValue = readLine(filename);
        if (fileValue != null)
            return (fileValue.equals(ON));
        return defValue;
    }

    private static String readLine(String filename) {
        if (filename == null) return null;
        String line;
        try (BufferedReader br = new BufferedReader(new FileReader(filename), 1024)) {
            line = br.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return line;
    }

    private static void writeValue(String filename, String value) {
        if (filename == null) return;
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            fos.write(value.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
