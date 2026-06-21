package com.yuhaojun.douyinadskipper;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Diagnostics {
    private static final String PREFS_NAME = "diagnostics";
    private static final String KEY_SERVICE_STATE = "service_state";
    private static final String KEY_LAST_TIME = "last_time";
    private static final String KEY_LAST_PACKAGE = "last_package";
    private static final String KEY_LAST_SCAN = "last_scan";
    private static final String KEY_LAST_ACTION = "last_action";
    private static final String KEY_LAST_ERROR = "last_error";

    private Diagnostics() {
    }

    static void serviceState(Context context, String state) {
        prefs(context).edit()
                .putString(KEY_SERVICE_STATE, state)
                .putString(KEY_LAST_TIME, now())
                .apply();
    }

    static void event(Context context, CharSequence packageName) {
        prefs(context).edit()
                .putString(KEY_LAST_PACKAGE, packageName == null ? "未知" : packageName.toString())
                .putString(KEY_LAST_TIME, now())
                .apply();
    }

    static void scan(Context context, String summary) {
        prefs(context).edit()
                .putString(KEY_LAST_SCAN, summary)
                .putString(KEY_LAST_TIME, now())
                .apply();
    }

    static void action(Context context, String action) {
        prefs(context).edit()
                .putString(KEY_LAST_ACTION, action)
                .putString(KEY_LAST_TIME, now())
                .apply();
    }

    static void error(Context context, Throwable throwable) {
        String message = throwable.getClass().getSimpleName();
        if (throwable.getMessage() != null) {
            message += ": " + throwable.getMessage();
        }
        prefs(context).edit()
                .putString(KEY_LAST_ERROR, message)
                .putString(KEY_LAST_TIME, now())
                .apply();
    }

    static String readSummary(Context context) {
        SharedPreferences prefs = prefs(context);
        return "服务: " + prefs.getString(KEY_SERVICE_STATE, "未连接") + "\n"
                + "时间: " + prefs.getString(KEY_LAST_TIME, "暂无") + "\n"
                + "最近包名: " + prefs.getString(KEY_LAST_PACKAGE, "暂无") + "\n"
                + "扫描: " + prefs.getString(KEY_LAST_SCAN, "暂无") + "\n"
                + "动作: " + prefs.getString(KEY_LAST_ACTION, "暂无") + "\n"
                + "错误: " + prefs.getString(KEY_LAST_ERROR, "暂无");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
    }
}
