package com.block.client.preferences;

import android.content.Context;
import android.content.SharedPreferences;


public class AppSharedPreference {
    private static final String DB_NAME = "VoiceCall";
    private static AppSharedPreference appSharedPreference;
    private static SharedPreferences preferences;

    private AppSharedPreference() {
    }

    public static AppSharedPreference getInstance(Context context) {
        if (appSharedPreference == null) {
            appSharedPreference = new AppSharedPreference();
            preferences = context.getSharedPreferences(DB_NAME, Context.MODE_PRIVATE);
        }
        return appSharedPreference;
    }

    public String getCallId() {
        return preferences.getString("call_id", "");
    }

    public void setCallId(String callId) {
        preferences.edit().putString("call_id", callId).apply();
    }
}
