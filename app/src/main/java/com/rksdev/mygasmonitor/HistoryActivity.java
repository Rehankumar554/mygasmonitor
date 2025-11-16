package com.rksdev.mygasmonitor;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private TextView tvEmpty;
    private LinearLayout emptyLayout;
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "BookingData";
    private static final String KEY_BOOKINGS = "bookings";
    private FloatingActionButton fabBack, btnFilter, btnMenu;

    private List<String> allBookings = new ArrayList<>();
    private int selectedFilterIndex = 0;

    private final String[] filters = {"All", "Today", "Past 7 Days", "Past 30 Days", "Past Year"};
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    private final Pattern datePattern = Pattern.compile("\\d{2} [A-Za-z]{3} \\d{4}, \\d{1,2}:\\d{2} [APMapm]{2}");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_history);

        rvHistory = findViewById(R.id.rvHistory);
        tvEmpty = findViewById(R.id.tvSubtitle);
        emptyLayout = findViewById(R.id.emptyLayout);

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        fabBack = findViewById(R.id.fabBack);
        btnFilter = findViewById(R.id.btnFilter);
        btnMenu = findViewById(R.id.btnMenu);

        loadAndDisplayBookings();

        fabBack.setOnClickListener(v -> finish());
        btnFilter.setOnClickListener(v -> showTimeFilterDialog());
        btnMenu.setOnClickListener(v -> showOptionsDialog());
    }

    private void loadAndDisplayBookings() {
        allBookings = loadBookings();
        displayBookings(allBookings);
    }
    private List<String> loadBookings() {
        Set<String> savedBookings = sharedPreferences.getStringSet(KEY_BOOKINGS, null);
        List<String> list = new ArrayList<>();

        if (savedBookings != null) {
            list.addAll(savedBookings);
            Collections.sort(list, (a, b) -> {
                Date dateA = extractDateFromText(a);
                Date dateB = extractDateFromText(b);
                if (dateA == null || dateB == null) return 0;
                return dateB.compareTo(dateA); // ✅ Newest first
            });
        }
        return list;
    }

    private void displayBookings(List<String> bookingList) {
        if (bookingList.isEmpty()) {
            emptyLayout.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
            tvEmpty.setText("No booking history yet.");
        } else {
            emptyLayout.setVisibility(View.GONE);
            rvHistory.setVisibility(View.VISIBLE);
            tvEmpty.setText("Your past gas bookings");
            rvHistory.setLayoutManager(new LinearLayoutManager(this));
            rvHistory.setAdapter(new HistoryAdapter(bookingList));
        }
    }

    private void showTimeFilterDialog() {
        TextView title = new TextView(this);
        title.setText("Filter by Time");
        title.setTextSize(20);
        title.setTextColor(Color.parseColor("#212121"));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(48, 40, 48, 20);

        var dialog = new MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
                .setCustomTitle(title)
                .setSingleChoiceItems(filters, selectedFilterIndex, (d, which) -> {
                    selectedFilterIndex = which;
                    applyTimeFilter(filters[which]);
                    d.dismiss();
                })
                .create();

        dialog.show();

        ListView listView = dialog.getListView();
        if (listView != null) {
            listView.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                @Override
                public void onChildViewAdded(View parent, View child) {
                    if (child instanceof TextView)
                        ((TextView) child).setTextColor(Color.parseColor("#212121"));
                }
                @Override public void onChildViewRemoved(View parent, View child) {}
            });
        }
    }

    /** Correct date filtering logic based on your new definitions */
    private void applyTimeFilter(String filter) {
        if (filter.equals("All")) {
            displayBookings(allBookings);
            return;
        }

        Calendar now = Calendar.getInstance();
        Calendar start = Calendar.getInstance();
        start.setTime(now.getTime());

        switch (filter) {
            case "Today":
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                break;

            case "Past 7 Days":
                start.add(Calendar.DAY_OF_YEAR, -7);
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                break;

            case "Past 30 Days":
                start.add(Calendar.DAY_OF_YEAR, -30);
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                break;

            case "Past Year":
                start.add(Calendar.YEAR, -1);
                start.set(Calendar.DAY_OF_YEAR, 1);
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                break;
        }

        List<String> filtered = new ArrayList<>();
        for (String booking : allBookings) {
            Date date = extractDateFromText(booking);
            if (date != null) {
                Calendar bookingCal = Calendar.getInstance();
                bookingCal.setTime(date);

                switch (filter) {
                    case "Today":
                        if (isSameDay(bookingCal, now)) filtered.add(booking);
                        break;
                    case "Past 7 Days":
                        if (bookingCal.before(now) && bookingCal.after(start)) {
                            if (!isSameDay(bookingCal, now)) filtered.add(booking);
                        }
                        break;
                    case "Past 30 Days":
                        if (bookingCal.before(now) && bookingCal.after(start)) {
                            if (!isSameDay(bookingCal, now)) filtered.add(booking);
                        }
                        break;
                    case "Past Year":
                        if (bookingCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) - 1)
                            filtered.add(booking);
                        break;
                }
            }
        }

        if (filtered.isEmpty()) {
            Toast.makeText(this, "No records for " + filter, Toast.LENGTH_SHORT).show();
        }

        displayBookings(filtered);
    }

    private boolean isSameDay(Calendar c1, Calendar c2) {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    /** Extract date from booking string safely */
    private Date extractDateFromText(String bookingText) {
        Matcher matcher = datePattern.matcher(bookingText);
        if (matcher.find()) {
            String datePart = matcher.group();
            try {
                return dateFormat.parse(datePart);
            } catch (ParseException e) {
                Log.e("HistoryActivity", "Date parse error for: " + datePart);
            }
        }
        return null;
    }

    private void showOptionsDialog() {
        CharSequence[] options = {
                Html.fromHtml("<font color='#212121'><b>Delete all history</b></font>")
        };

        TextView title = new TextView(this);
        title.setText("Options");
        title.setTextSize(20);
        title.setTextColor(Color.parseColor("#212121"));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(48, 40, 48, 20);

        new MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
                .setCustomTitle(title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showDeleteConfirmation();
                })
                .show();
    }

    private void showDeleteConfirmation() {
        new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle(Html.fromHtml("<font color='#D32F2F'><b>Delete History</b></font>"))
                .setMessage(Html.fromHtml(
                        "<font color='#212121'>Are you sure you want to <b>delete all booking history?</b><br><br>" +
                                "This action <font color='#D32F2F'>cannot be undone.</font></font>"))
                .setPositiveButton(
                        Html.fromHtml("<font color='#D32F2F'><b>Delete</b></font>"),
                        (dialog, which) -> {
                            clearHistory();
                            Toast.makeText(this, "All history deleted", Toast.LENGTH_SHORT).show();
                        })
                .setNegativeButton(
                        Html.fromHtml("<font color='#3F51B5'><b>Cancel</b></font>"),
                        (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void clearHistory() {
        sharedPreferences.edit().putStringSet(KEY_BOOKINGS, new HashSet<>()).apply();
        loadAndDisplayBookings();
    }
}
