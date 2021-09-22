package com.liskovsoft.leankeyboard.activity.settings;

import android.os.Bundle;

import com.liskovsoft.leankeyboard.addons.keyboards.extkeyboards.utils.log.BuildConfig;

public class KbSettingsActivity2 extends KbSettingsActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (BuildConfig.DEBUG) {
            finish();
        }
    }
}
