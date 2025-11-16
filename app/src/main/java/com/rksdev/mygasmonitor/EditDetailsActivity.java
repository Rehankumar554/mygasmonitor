package com.rksdev.mygasmonitor;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class EditDetailsActivity extends AppCompatActivity {

    private EditText etName, etConsumerId, etAadhaarId, etPhone, etEmail, etAddress, etAgencyBookingNumber;
    private Spinner spCylinderType, spAgencyName;
    private Button btnSave;

    private FloatingActionButton fabBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Optional safety check for null ActionBar
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_edit_details);

        // === Initialize UI Elements ===
        etName = findViewById(R.id.etName);
        etConsumerId = findViewById(R.id.etConsumerId);
        etAadhaarId = findViewById(R.id.etAadhaarId);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etAddress = findViewById(R.id.etAddress);
        spCylinderType = findViewById(R.id.spCylinderType);
        spAgencyName = findViewById(R.id.spAgencyName);
        etAgencyBookingNumber = findViewById(R.id.etAgencyBookingNumber);
        btnSave = findViewById(R.id.btnSave);
        fabBack = findViewById(R.id.fabBack);

        fabBack.setOnClickListener(v -> finish());

        // === Load saved data ===
        SharedPreferences prefs = getSharedPreferences("UserDetails", MODE_PRIVATE);
        etName.setText(prefs.getString("name", ""));
        etConsumerId.setText(prefs.getString("consumer_id", ""));
        etAadhaarId.setText(prefs.getString("aadhaar_id", ""));
        etPhone.setText(prefs.getString("phone", ""));
        etEmail.setText(prefs.getString("email", ""));
        etAddress.setText(prefs.getString("address", ""));
        spCylinderType.setSelection(getSpinnerIndex(spCylinderType, prefs.getString("cylinder_type", "")));
        spAgencyName.setSelection(getSpinnerIndex(spAgencyName, prefs.getString("agency_name", "")));
        etAgencyBookingNumber.setText(prefs.getString("agency_booking_number", ""));

        // === Save Button ===
        btnSave.setOnClickListener(v -> validateAndSave(prefs));
    }

    private void validateAndSave(SharedPreferences prefs) {
        String consumerId = etConsumerId.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();

        List<String> errors = new ArrayList<>();

        // --- Consumer ID ---
        if (!consumerId.matches("\\d+")) {
            errors.add("Consumer ID must contain digits only.");
        } else if (consumerId.length() < 17) {
            errors.add("Consumer ID must be at least 17 digits long.");
        }

        // --- Phone ---
        if (!phone.matches("\\d+")) {
            errors.add("Phone number must contain digits only.");
        } else if (phone.length() < 10) {
            errors.add("Phone number must be at least 10 digits long.");
        }

        // --- Email ---
        if (!email.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errors.add("Please enter a valid email address.");
        }

        if (!errors.isEmpty()) {
            // Use new stacked toast system for multiple errors
            new StackedToastManager(this).showStackedToasts(errors);
            return;
        }

        // --- Save valid data ---
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("name", etName.getText().toString().trim());
        editor.putString("consumer_id", consumerId);
        editor.putString("aadhaar_id", etAadhaarId.getText().toString().trim());
        editor.putString("phone", phone);
        editor.putString("email", email);
        editor.putString("cylinder_type", spCylinderType.getSelectedItem().toString());
        editor.putString("agency_name", spAgencyName.getSelectedItem().toString());
        editor.putString("agency_booking_number", etAgencyBookingNumber.getText().toString().trim());
        editor.putString("address", etAddress.getText().toString().trim());
        editor.apply();

        // Success message
        List<String> successMsg = new ArrayList<>();

        finish();
    }

    private int getSpinnerIndex(Spinner spinner, String value) {
        if (value == null) return 0;
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equalsIgnoreCase(value))
                return i;
        }
        return 0;
    }
}
