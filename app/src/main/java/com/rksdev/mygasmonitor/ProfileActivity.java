package com.rksdev.mygasmonitor;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvCustomerName, tvConsumerId, tvAadhaarId, tvPhone, tvEmail, tvCylinderType, tvAgencyName, tvLastRefill, tvAddress, tvAgencyBookingNumber;
    private Button btnBook; // Edit Details button

    private FloatingActionButton fabBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_profile);

        fabBack = findViewById(R.id.fabBack);
        tvCustomerName = findViewById(R.id.tvCustomerName);
        tvConsumerId = findViewById(R.id.tvConsumerId);
        tvAadhaarId = findViewById(R.id.tvAadhaarId);
        tvPhone = findViewById(R.id.tvPhone);
        tvEmail = findViewById(R.id.tvEmail);
        tvCylinderType = findViewById(R.id.tvCylinderType);
        tvAgencyName = findViewById(R.id.tvAgencyName);
        tvAgencyBookingNumber = findViewById(R.id.tvAgencyBookingNumber);
        tvLastRefill = findViewById(R.id.tvLastRefill);
        tvAddress = findViewById(R.id.tvAddress);
        btnBook = findViewById(R.id.btnBook);

        fabBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // simply closes the activity and returns to previous screen
            }
        });

        btnBook.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, EditDetailsActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Load saved details whenever coming back
        SharedPreferences prefs = getSharedPreferences("UserDetails", MODE_PRIVATE);
        tvCustomerName.setText(prefs.getString("name", "N/A"));
        tvConsumerId.setText(prefs.getString("consumer_id", "N/A"));

        String fullAadhaar = prefs.getString("aadhaar_id", "");
        if (fullAadhaar == null || fullAadhaar.isEmpty()) {
            tvAadhaarId.setText("N/A");
        } else {
            // Extract last 4 digits safely
            String last4 = fullAadhaar.length() > 4 ? fullAadhaar.substring(fullAadhaar.length() - 4) : fullAadhaar;

            // Show masked Aadhaar in 4–4–4 grouping
            String maskedAadhaar = "XXXX-XXXX-" + last4;
            tvAadhaarId.setText(maskedAadhaar);
        }

        tvPhone.setText(prefs.getString("phone", "N/A"));
        tvEmail.setText(prefs.getString("email", "N/A"));
        tvCylinderType.setText(prefs.getString("cylinder_type", "N/A"));
        tvAgencyName.setText(prefs.getString("agency_name", "N/A"));
        tvAgencyBookingNumber.setText(prefs.getString("agency_booking_number", "N/A"));
        tvLastRefill.setText(prefs.getString("last_refill", "N/A"));
        tvAddress.setText(prefs.getString("address", "N/A"));
    }
}
