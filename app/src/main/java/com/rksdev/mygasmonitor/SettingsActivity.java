package com.rksdev.mygasmonitor;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS = "AppPrefs";
    private static final String KEY_HOST = "ws_host";
    private static final int WS_PORT = 8765;
    private OkHttpClient testClient;
    private Button btnEditProfile, btnChangeAgency, btnSetHost;
    private TextView tvCurrentIP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_settings);

        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnChangeAgency = findViewById(R.id.btnChangeAgency);
        btnSetHost = findViewById(R.id.btnSetHost);
        tvCurrentIP = findViewById(R.id.tvCurrentIP);

        testClient = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build();

        updateCurrentIpDisplay();


        btnSetHost.setOnClickListener(v -> showSetHostDialog());
        findViewById(R.id.fabBack).setOnClickListener(v -> finish());

        btnEditProfile.setOnClickListener(v -> {
            Intent i = new Intent(SettingsActivity.this, EditDetailsActivity.class);
            startActivity(i);
        });

        btnChangeAgency.setOnClickListener(v -> {
            Intent i = new Intent(SettingsActivity.this, EditDetailsActivity.class);
            startActivity(i);
        });
    }

    private void showSetHostDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        String current = getSavedHost();
        if (current != null) input.setText(current);

        input.setHint("Enter IP");

        input.setTextColor(Color.parseColor("#3F51B5")); // or any hex, e.g. "#FFFFFF"

        input.setHintTextColor(Color.parseColor("#9E9E9E"));

        input.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E8EAF6")));
        input.setPadding(32, 24, 32, 24);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme);
        builder.setTitle(Html.fromHtml("<font color='#212121'>Set Host IP</font>"));
        builder.setMessage(Html.fromHtml("<font color='#424242'>Enter IP (e.g. 192.0.0.1):</font>"));
        builder.setView(input);
        builder.setPositiveButton("Connect", (dialog, which) -> {
            String host = input.getText().toString().trim();
            if (!host.isEmpty()) {
                testAndSaveHost(host);
            } else {
                Toast.makeText(this, "Invalid IP address", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void testAndSaveHost(String host) {
        String wsUrl = "ws://" + host + ":" + WS_PORT;
//        Toast.makeText(this, "Testing connection to " + wsUrl, Toast.LENGTH_SHORT).show();
        Toast.makeText(this, "Connection to Host", Toast.LENGTH_SHORT).show();

        Request request = new Request.Builder().url(wsUrl).build();
        WebSocket testSocket = testClient.newWebSocket(request, new WebSocketListener() {

            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                runOnUiThread(() -> {
                    saveHost(host);
                    Toast.makeText(SettingsActivity.this,
                            "Connected successfully! IP saved", Toast.LENGTH_LONG).show();
                    finish(); // close settings and go back to main
                });
                webSocket.close(1000, "Test success");
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
                runOnUiThread(() ->
//                        Toast.makeText(SettingsActivity.this,
//                                "Connection failed: " + t.getMessage(), Toast.LENGTH_LONG).show());
                        Toast.makeText(SettingsActivity.this,
                                "Connection failed: ", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void saveHost(String host) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().putString(KEY_HOST, host).apply();
        updateCurrentIpDisplay();
    }

    private String getSavedHost() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        return prefs.getString(KEY_HOST, null);
    }

    private void updateCurrentIpDisplay() {
        String current = getSavedHost();
        if (current != null)
            tvCurrentIP.setText(current);
        else
            tvCurrentIP.setText("Not set");
    }

    @Override
    protected void onDestroy() {
        if (testClient != null) testClient.dispatcher().executorService().shutdown();
        super.onDestroy();
    }
}
