package com.liskovsoft.leankeyboard.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

public class Analytics {
    private static final String TAG = "Analytics";
    private static final String ACTION_APP_UNCAUGHT_EXCEPTION = "ACTION_APP_UNCAUGHT_EXCEPTION";
    private static PackageInfo sPackageInfo = null;
    private static Context sContext = null;

    public static void init(@NonNull Context context) {
        sContext = context;
        sPackageInfo = getPackageInfo(context);
    }

    public static void sendAppCrash(String name, String trace, String log) {
        Intent intent = createIntent(ACTION_APP_UNCAUGHT_EXCEPTION, sPackageInfo);
        intent.putExtra("trace", trace);
        intent.putExtra("name", name);
        intent.putExtra("logs", log);
        sContext.sendBroadcast(intent);
    }

    private static PackageInfo getPackageInfo(@NonNull Context context) {
        PackageInfo packageInfo = new PackageInfo();
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return packageInfo;
    }

    private static Intent createIntent(String actionName, PackageInfo packageInfo) {
        Intent intent = new Intent(actionName);
        intent.putExtra("app_name", packageInfo.packageName);
        intent.putExtra("app_version_name", packageInfo.versionName);
        intent.putExtra("app_version_code", packageInfo.versionCode);
        return intent;
    }
}