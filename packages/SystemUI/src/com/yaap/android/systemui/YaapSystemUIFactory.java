package com.yaap.android.systemui;

import android.content.Context;

import com.yaap.android.systemui.dagger.YaapGlobalRootComponent;
import com.yaap.android.systemui.dagger.DaggerYaapGlobalRootComponent;

import com.android.systemui.SystemUIFactory;
import com.android.systemui.dagger.GlobalRootComponent;

public class YaapSystemUIFactory extends SystemUIFactory {
    @Override
    protected GlobalRootComponent buildGlobalRootComponent(Context context) {
        return DaggerYaapGlobalRootComponent.builder()
                .context(context)
                .build();
    }
}
