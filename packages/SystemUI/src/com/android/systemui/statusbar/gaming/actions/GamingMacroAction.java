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

package com.android.systemui.statusbar.gaming.actions;

import android.content.Context;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.provider.Settings;

/** An abstract class that represents an action to be done in GamingMacro */
public abstract class GamingMacroAction {
    protected final Context mContext;
    protected final ContentResolver mResolver;
    protected final SharedPreferences mPrefs;
    protected final String mSettingKey;
    protected final int mDefaultValue;

    /**
     * @param context for settings / utility classes etc
     * @param prefs DE SharedPreferences
     * @param settingKey the setting key that controls this action's enablement
     * @param defaultValue setting key's default value
     */
    public GamingMacroAction(Context context, SharedPreferences prefs,
            String settingKey, int defaultValue) {
        mContext = context;
        mResolver = context.getContentResolver();
        mPrefs = prefs;
        mSettingKey = settingKey;
        mDefaultValue = defaultValue;
    }

    /**
     * Default constructor for when defaultValue is 0
     * @param context for settings / utility classes etc
     * @param prefs DE SharedPreferences
     * @param settingKey the setting key that controls this action's enablement
     */
    public GamingMacroAction(Context context, SharedPreferences prefs,
            String settingKey) {
        this(context, prefs, settingKey, 0);
    }

    /**
     * default implementation just checks if the value is different from the default
     * other scenarios, such as default enabled actions should implement this function
     * @return true if the action is currently enabled by the user
     */
    public boolean isEnabled() {
        return Settings.System.getInt(mResolver,
                mSettingKey, mDefaultValue) != mDefaultValue;
    }

    /** 
     * saves the current state of the settings / values this action is changing
     * @param editor DE SharedPreferences.editor for saving
     */
    public abstract void saveState(SharedPreferences.Editor editor);

    /** 
     * triggers the action
     */
    public abstract void apply();

    /** 
     * restores (disables) the action's affected values to their previous state
     * only if it was ever applied and saved state in preferences
     */
    public abstract void restore();
}
