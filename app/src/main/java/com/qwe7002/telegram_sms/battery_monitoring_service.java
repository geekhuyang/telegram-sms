package com.qwe7002.telegram_sms;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.content.Context.BATTERY_SERVICE;

public class battery_monitoring_service extends Service {
    static String bot_token;
    static String chat_id;
    static Boolean fallback;
    static String trusted_phone_number;
    static boolean doh_switch;
    static boolean charger_status;
    Context context;
    SharedPreferences sharedPreferences;
    private battery_receiver battery_receiver = null;
    private stop_broadcast_receiver stop_broadcast_receiver = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = public_func.get_notification_obj(context, getString(R.string.battery_monitoring_notify));
        startForeground(1, notification);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        chat_id = sharedPreferences.getString("chat_id", "");
        bot_token = sharedPreferences.getString("bot_token", "");
        fallback = sharedPreferences.getBoolean("fallback_sms", false);
        trusted_phone_number = sharedPreferences.getString("trusted_phone_number", null);
        doh_switch = sharedPreferences.getBoolean("doh_switch", true);
        charger_status = sharedPreferences.getBoolean("charger_status", false);
        IntentFilter intentFilter = new IntentFilter(public_func.broadcast_stop_service);
        stop_broadcast_receiver = new stop_broadcast_receiver();

        battery_receiver = new battery_receiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        if (charger_status) {
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        }
        registerReceiver(battery_receiver, filter);
        registerReceiver(stop_broadcast_receiver, intentFilter);

    }

    @Override
    public void onDestroy() {
        unregisterReceiver(stop_broadcast_receiver);
        unregisterReceiver(battery_receiver);
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    class stop_broadcast_receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopSelf();
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

}

class battery_receiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        Log.d(public_func.log_tag, "onReceive: " + intent.getAction());
        String request_uri = public_func.get_url(battery_monitoring_service.bot_token, "sendMessage");
        final message_json request_body = new message_json();
        request_body.chat_id = battery_monitoring_service.chat_id;
        StringBuilder prebody = new StringBuilder(context.getString(R.string.system_message_head) + "\n");
        final String action = intent.getAction();
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
        switch (Objects.requireNonNull(action)) {
            case Intent.ACTION_BATTERY_OKAY:
                prebody = prebody.append(context.getString(R.string.low_battery_status_end));
                break;
            case Intent.ACTION_BATTERY_LOW:
                prebody = prebody.append(context.getString(R.string.battery_low));
                break;
            case Intent.ACTION_POWER_CONNECTED:
                prebody = prebody.append(context.getString(R.string.charger_connect));
                break;
            case Intent.ACTION_POWER_DISCONNECTED:
                prebody = prebody.append(context.getString(R.string.charger_disconnect));
                break;
        }
        assert batteryManager != null;
        int battery_level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (battery_level > 100) {
            battery_level = 100;
        }
        request_body.text = prebody.append("\n").append(context.getString(R.string.current_battery_level)).append(battery_level).append("%").toString();

        if (!public_func.check_network_status(context)) {
            public_func.write_log(context, public_func.network_error);
            if (action.equals(Intent.ACTION_BATTERY_LOW)) {
                public_func.send_fallback_sms(context, request_body.text, -1);
            }
            return;
        }
        OkHttpClient okhttp_client = public_func.get_okhttp_obj(battery_monitoring_service.doh_switch);
        String request_body_raw = new Gson().toJson(request_body);
        RequestBody body = RequestBody.create(request_body_raw, public_func.JSON);
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        final String error_head = "Send battery info failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                String error_message = error_head + e.getMessage();
                public_func.write_log(context, error_message);
                if (action.equals(Intent.ACTION_BATTERY_LOW)) {
                    public_func.send_fallback_sms(context, request_body.text, -1);
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() != 200) {
                    assert response.body() != null;
                    String error_message = error_head + response.code() + " " + Objects.requireNonNull(response.body()).string();
                    public_func.write_log(context, error_message);
                }
            }
        });
    }
}
