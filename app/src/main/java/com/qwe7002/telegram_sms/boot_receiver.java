package com.qwe7002.telegram_sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class boot_receiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        public_func.write_log(context, "Received [" + intent.getAction() + "] broadcast, starting background service.");
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean("initialized", false)) {
            public_func.start_service(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
        }
    }
}
