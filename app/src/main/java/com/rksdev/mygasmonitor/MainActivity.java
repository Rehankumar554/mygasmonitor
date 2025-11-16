package com.rksdev.mygasmonitor;

import android.graphics.PorterDuffColorFilter;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.text.Html;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.graphics.Color;
import android.graphics.PorterDuff;
import androidx.annotation.NonNull;
import android.graphics.drawable.Drawable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.os.Handler;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private TextView tvWeight, tvPercent, tvStatus, tvLastUpdate, tvFullWeight, rowStatus, rowLastUpdate;
    private TextView oledStatus, oledSignal, oledUptime, oledSystem, chkDeviceView;
    private ImageView rowStatusIcon;
    private LinearProgressIndicator progressBar;
    private Button btnBook, btnHistory;
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "BookingData";
    private static final String KEY_BOOKINGS = "bookings";
    private static final String APP_PREFS = "AppPrefs";
    private static final String KEY_HOST = "ws_host";
    private static final int WS_PORT = 8765;
    private FloatingActionButton btnProfile, btnSettings;
    private OkHttpClient client;
    private WebSocket ws;
    private Handler uiHandler;
    private final String TAG = "MyGasMonitor";
    private boolean manuallyClosed = false;
    private long lastUpdateTimestamp = 0;
    private boolean isDialogVisible = false;
    private boolean dialogDismissed = false;
    private boolean wasLowPreviously = false;
    private boolean dataReceivedYet = false;
    private long appStartTime;

    private static final int REQUEST_PERMISSIONS = 999;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.INTERNET,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestAllPermissions();
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
        appStartTime = System.currentTimeMillis();

        sharedPreferences = getSharedPreferences(APP_PREFS, MODE_PRIVATE);
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Bind UI elements
        btnSettings = findViewById(R.id.btnSettings);
        btnProfile = findViewById(R.id.btnProfile);
        tvWeight = findViewById(R.id.tvWeight);
        tvPercent = findViewById(R.id.tvPercent);
        tvStatus = findViewById(R.id.tvStatus);
        rowLastUpdate = findViewById(R.id.rowLastUpdate);
        rowStatus = findViewById(R.id.rowStatus);
        rowStatusIcon = findViewById(R.id.rowStatusIcon);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);
        progressBar = findViewById(R.id.progressBar);
        btnBook = findViewById(R.id.btnBook);
        btnHistory = findViewById(R.id.btnHistory);
        tvFullWeight = findViewById(R.id.tvFullWeight);
        oledStatus = findViewById(R.id.oledStatus);
        oledSignal = findViewById(R.id.oledSignal);
        oledUptime = findViewById(R.id.oledUptime);
        oledSystem = findViewById(R.id.oledSystem);
        chkDeviceView = findViewById(R.id.chkDeviceView);

        uiHandler = new Handler(getMainLooper());
        client = new OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build();

        connectWebSocket();

        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        btnBook.setOnClickListener(v -> showConfirmationDialog());
        btnHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));

        // Update “Last Update” text every second
        Handler refreshHandler = new Handler();
        Runnable refreshTask = new Runnable() {
            @Override
            public void run() {
                updateLastUpdateTime();
                refreshHandler.postDelayed(this, 1000);
            }
        };
        refreshHandler.postDelayed(refreshTask, 1000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserSettings();

        if (ws != null) {
            try { ws.cancel(); } catch (Exception ignored) {}
        }
        connectWebSocket();
    }

    private boolean hasAllPermissions() {
        for (String perm : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return true;
    }

    private void requestAllPermissions() {
        if (!hasAllPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Some permissions denied — certain features may not work", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadUserSettings() {
        SharedPreferences userPrefs = getSharedPreferences("UserDetails", Context.MODE_PRIVATE);
        String cylinderTypeString = userPrefs.getString("cylinder_type", "Domestic standard: 14.2 kg");

        String cylinderWeight = "14.2 kg";
        if (cylinderTypeString.contains(":")) {
            String[] parts = cylinderTypeString.split(":");
            if (parts.length > 1) cylinderWeight = parts[1].trim();
        }
        if (tvFullWeight != null) tvFullWeight.setText(cylinderWeight);
    }

    private void showConfirmationDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme);
        builder.setIcon(R.drawable.ic_gas_cylinder);
        builder.setTitle(Html.fromHtml("<font color='#212121'>Confirm Booking</font>"));
        builder.setMessage(Html.fromHtml("<font color='#424242'>Are you sure you want to book a gas refill?</font>"));
        builder.setPositiveButton("Yes", (dialog, which) -> {
            saveBooking();
            Toast.makeText(this, "Gas refill booked successfully!", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void showLowGasDialog(double level) {
        if (isFinishing() || isDialogVisible) return;
        isDialogVisible = true;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme);
        builder.setIcon(R.drawable.ic_gas_cylinder);

        builder.setTitle(Html.fromHtml(
                "<font color='#D32F2F'><b>Low Gas Level Alert!</b></font>",
                Html.FROM_HTML_MODE_LEGACY
        ));

        builder.setMessage(Html.fromHtml(
                "<font color='#424242'>Your LPG level is currently " +
                        "<b><font color='#F44336'>" + String.format("%.1f%%", level) +
                        "</font></b>.<br><br>Would you like to book a gas refill now?</font>",
                Html.FROM_HTML_MODE_LEGACY
        ));

        builder.setPositiveButton(Html.fromHtml("<font color='#388E3C'>Yes</font>", Html.FROM_HTML_MODE_LEGACY),
                (dialog, which) -> {
                    saveBooking();
                    Toast.makeText(this, "Refill booked successfully!", Toast.LENGTH_SHORT).show();
                    isDialogVisible = false;
                    dialogDismissed = false; // can alert again when cycle resets
                });

        builder.setNegativeButton(Html.fromHtml("<font color='#D32F2F'>No</font>", Html.FROM_HTML_MODE_LEGACY),
                (dialog, which) -> {
                    dialog.dismiss();
                    dialogDismissed = true; // user said no, don't show until next low cycle
                    isDialogVisible = false;
                });

        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> isDialogVisible = false);

        if (dialog.getWindow() != null)
            dialog.getWindow().getAttributes().windowAnimations = R.style.DialogSlideAnimation;

        dialog.show();
    }

    private void saveBooking() {
        if (sharedPreferences == null)
            sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        String currentDateTime = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                .format(new Date());
        Set<String> existingBookings = sharedPreferences.getStringSet(KEY_BOOKINGS, new HashSet<>());
        if (existingBookings == null) existingBookings = new HashSet<>();
        Set<String> updatedBookings = new HashSet<>(existingBookings);
        updatedBookings.add("Booked on: " + currentDateTime);
        sharedPreferences.edit().putStringSet(KEY_BOOKINGS, updatedBookings).apply();

        smsBookingSend();
    }

    private void smsBookingSend() {
        SharedPreferences userPrefs = getSharedPreferences("UserDetails", Context.MODE_PRIVATE);

        String agencyNumber = userPrefs.getString("agency_booking_number", "");
        String consumerId = userPrefs.getString("consumer_id", "");
        String aadhaarId = userPrefs.getString("aadhaar_id", "");

        if (TextUtils.isEmpty(agencyNumber) || TextUtils.isEmpty(consumerId) || TextUtils.isEmpty(aadhaarId)) {
            Log.w(TAG, "Missing booking details — SMS not sent.");
            Toast.makeText(this, "Booking SMS skipped: incomplete details", Toast.LENGTH_SHORT).show();
            return;
        }

        String message = consumerId + " " + aadhaarId;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS}, 200);
            Toast.makeText(this, "Grant SMS permission to enable auto-booking", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(agencyNumber, null, message, null, null);
            Toast.makeText(this, "Booking SMS sent to Agency", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Booking SMS sent successfully to " + agencyNumber);
        } catch (Exception e) {
            Log.e(TAG, "Booking SMS failed: " + e.getMessage());
            Toast.makeText(this, "Booking SMS failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateGasStatus(double level) {
        String statusText;
        int color;

        if (level >= 60) { statusText = "Normal"; color = Color.parseColor("#4CAF50"); }
        else if (level >= 40) { statusText = "Medium"; color = Color.parseColor("#FFA000"); }
        else if (level >= 20) { statusText = "Low"; color = Color.parseColor("#F57C00"); }
        else { statusText = "Very Low"; color = Color.parseColor("#F44336"); }

        tvStatus.setText(statusText);
        rowStatus.setText(statusText);
        tvStatus.setTextColor(color);
        rowStatus.setTextColor(color);
        progressBar.setIndicatorColor(color);

        Drawable drawable = rowStatusIcon.getDrawable();
        if (drawable != null) {
            Drawable wrapped = drawable.mutate();
            wrapped.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            rowStatusIcon.setImageDrawable(wrapped);
        }

        // ✅ Refill dialog logic (smart cycle detection)
        if (level < 20 && !isDialogVisible && !wasLowPreviously) {
            showLowGasDialog(level);
            wasLowPreviously = true; // mark this low event
        }

        // Reset flags when level rises again to safe zone
        if (level > 30) {
            wasLowPreviously = false;
            dialogDismissed = false;
        }
    }

    private String buildWsUrl() {
        SharedPreferences prefs = getSharedPreferences(APP_PREFS, MODE_PRIVATE);
        String host = prefs.getString(KEY_HOST, null);
        if (host == null || host.isEmpty()) {
            runOnUiThread(() -> oledStatus.setText("Status: No IP set"));
            return null;
        }
        return "ws://" + host + ":" + WS_PORT;
    }

    private void connectWebSocket() {
        String wsUrl = buildWsUrl();
        if (wsUrl == null) {
            Toast.makeText(this, "Please set Host in Settings", Toast.LENGTH_LONG).show();
            return;
        }

        runOnUiThread(() -> {
            oledStatus.setText("Status: Connecting...");
            oledSignal.setText("Signal: --");
            oledUptime.setText("Uptime: --");
            oledSystem.setText("System: --");
            chkDeviceView.setVisibility(View.GONE);
        });

        dataReceivedYet = false;
        Request request = new Request.Builder().url(wsUrl).build();

        ws = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.i(TAG, "WS opened");
                uiHandler.post(() -> oledStatus.setText("Status: Loading..."));
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                dataReceivedYet = true;

                try {
                    JSONObject obj = new JSONObject(text);
                    final double weight = obj.optDouble("weight", Double.NaN);
                    final double level = obj.optDouble("level", Double.NaN);
                    final String signal = obj.optString("signal", "N/A");
                    final String uptime = obj.optString("uptime", "--");

                    // From new Arduino JSON
                    final String systemStatus = obj.optString("system", "Unknown");
                    final String chkDevice = obj.optString("chkdevice", "");

                    uiHandler.post(() -> {
                        oledStatus.setText("Status: Connected");
                        oledSignal.setText("Signal: " + signal);
                        oledUptime.setText("Uptime: " + uptime);

                        // ===== System line coloring =====
                        if (systemStatus.equalsIgnoreCase("ERR")) {
                            oledSystem.setText(Html.fromHtml(
                                    "<font color='#00FF00'>System:</font> " +
                                            "<font color='#F44336'>ERR</font>",
                                    Html.FROM_HTML_MODE_LEGACY
                            ));
                        } else {
                            oledSystem.setText(Html.fromHtml(
                                    "<font color='#00FF00'>System:</font> " +
                                            "<font color='#4CAF50'>OK</font>",
                                    Html.FROM_HTML_MODE_LEGACY
                            ));
                        }

                        // ===== Chk Device line coloring =====
                        if (chkDevice != null && !chkDevice.trim().isEmpty()) {
                            chkDeviceView.setVisibility(View.VISIBLE);
                            chkDeviceView.setText(Html.fromHtml(
                                    "<font color='#00FF00'>Chk Device:</font> " +
                                            "<font color='#F44336'>" + chkDevice + "</font>",
                                    Html.FROM_HTML_MODE_LEGACY
                            ));
                        } else {
                            chkDeviceView.setVisibility(View.GONE);
                        }

                        // ===== Weight / Level Update =====
                        if (!Double.isNaN(weight))
                            tvWeight.setText(String.format("%.2f kg", weight));
                        if (!Double.isNaN(level))
                            tvPercent.setText(String.format("%.1f %%", level));

                        progressBar.setProgress((int) level);
                        updateGasStatus(level);
                        markDataReceived();
                    });

                } catch (Exception e) {
                    Log.w(TAG, "JSON parse error: " + e.getMessage());
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.i(TAG, "WS closed");
                uiHandler.post(() -> oledStatus.setText("Status: Disconnected"));
                if (!manuallyClosed) scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WS failure: " + t.getMessage());
                runOnUiThread(() -> oledStatus.setText("Status: Conn failed"));
                if (!manuallyClosed) scheduleReconnect();
            }
        });
    }

    private void markDataReceived() {
        lastUpdateTimestamp = System.currentTimeMillis();
        updateLastUpdateTime();
    }

    private void updateLastUpdateTime() {
        if (lastUpdateTimestamp == 0) {
            rowLastUpdate.setText("--");
            return;
        }
        long diffMs = System.currentTimeMillis() - lastUpdateTimestamp;
        long seconds = diffMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        String text;
        if (hours > 0) text = String.format("%dh %dm ago", hours, minutes % 60);
        else if (minutes > 0) text = String.format("%dm %ds ago", minutes, seconds % 60);
        else text = String.format("%ds ago", seconds);
        rowLastUpdate.setText(text);
        if (seconds > 15)
            rowLastUpdate.setTextColor(Color.parseColor("#F44336"));
        else
            rowLastUpdate.setTextColor(Color.parseColor("#757575"));
    }

    private void scheduleReconnect() {
        uiHandler.postDelayed(() -> {
            Log.i(TAG, "Attempting reconnect...");
            connectWebSocket();
        }, 3000);
    }

    @Override
    protected void onDestroy() {
        manuallyClosed = true;
        if (ws != null) ws.cancel();
        if (client != null) client.dispatcher().executorService().shutdown();
        super.onDestroy();
    }
}
