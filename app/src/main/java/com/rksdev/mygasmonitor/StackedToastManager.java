package com.rksdev.mygasmonitor;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class StackedToastManager {

    private final Activity activity;
    private final Handler handler = new Handler();
    private final List<View> activeToasts = new ArrayList<>();

    public StackedToastManager(Activity activity) {
        this.activity = activity;
    }

    public void showStackedToasts(List<String> messages) {
        int delayBetween = 300; // ms between stacking
        int visibleDuration = 2000; // ms before each starts dismissing

        int index = 0;
        for (String msg : messages) {
            int finalIndex = index;
            handler.postDelayed(() -> showToast(msg, finalIndex, visibleDuration), delayBetween * index);
            index++;
        }
    }

    private void showToast(String message, int index, int visibleDuration) {
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View toastView = inflater.inflate(R.layout.custom_stack_toast, null);

        TextView textView = toastView.findViewById(R.id.tvToastMessage);
        textView.setText(message);

        FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView().findViewById(android.R.id.content);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = 160 + (index * 140); // stack height difference

        decor.addView(toastView, params);
        activeToasts.add(toastView);

        toastView.setAlpha(0f);
        toastView.animate().alpha(1f).setDuration(200).start();

        // Schedule dismissal — in order: 1st first, then next, etc.
        handler.postDelayed(() -> {
            toastView.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                decor.removeView(toastView);
                activeToasts.remove(toastView);
            }).start();
        }, visibleDuration + (index * 300)); // progressive dismiss
    }
}
