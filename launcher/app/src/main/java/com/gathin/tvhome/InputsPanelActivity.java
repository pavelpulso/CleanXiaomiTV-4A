package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Side panel of inputs drawn over whatever is on screen, in the style of the stock Android TV
 * launcher's InputsPanelActivity, so the Input button switches sources without going through Home.
 */
public class InputsPanelActivity extends Activity {

    private static final int PANEL_BG = 0xFF202124;
    private static final int FOCUS_BG = 0xFF3B3C3F;
    private static final int CIRCLE_BG = 0xFF3B3C3F;
    private static final int ICON_DIM = 0xFF99A0A7;
    private static final int SUBTITLE = 0xFFBDC1C6;

    private float density;
    private Config cfg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        cfg = Config.load(this);

        Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(PANEL_BG));
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.width = dp(360);
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.gravity = Gravity.END | Gravity.TOP;
        lp.dimAmount = 0.6f;
        lp.windowAnimations = android.R.style.Animation_Translucent;
        w.setAttributes(lp);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, dp(40), 0, dp(24));

        TextView title = new TextView(this);
        title.setText(R.string.inputs);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(22), 0, dp(22), dp(20));
        panel.addView(title);

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        panel.addView(rows);
        fillRows(rows);

        setContentView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        rows.getChildAt(0).requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }

    private void fillRows(LinearLayout rows) {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return;
        List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
        Collections.sort(inputs, (a, b) -> {
            boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
            if (ha != hb) return ha ? -1 : 1;
            return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
        });

        String tunerId = null;
        boolean tunerIsHw = false;
        for (TvInputInfo info : inputs) {
            if (info.getType() != TvInputInfo.TYPE_TUNER
                    || (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) continue;
            boolean hw = info.getId().matches(".*/HW\\d+$");
            if (tunerId == null || (hw && !tunerIsHw) || (hw == tunerIsHw && info.getId().compareTo(tunerId) < 0)) {
                tunerId = info.getId();
                tunerIsHw = hw;
            }
        }
        if (tunerId != null && !cfg.liveTvPackage.isEmpty()) {
            final Uri uri = TvContract.buildChannelUriForPassthroughInput(tunerId);
            addRow(rows, R.drawable.ic_livetv, getString(R.string.tv), null, true,
                    () -> view(uri, cfg.liveTvPackage));
        }

        for (TvInputInfo info : inputs) {
            if (!info.isPassthroughInput()) continue;
            CharSequence l = info.loadLabel(this);
            String label = l == null ? info.getId() : l.toString();
            boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
            if (hdmi && !label.toUpperCase(java.util.Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
            CharSequence custom = info.loadCustomLabel(this);
            boolean hasCustom = custom != null && custom.length() > 0 && !custom.toString().equals(label);
            boolean enabled = tim.getInputState(info.getId()) != TvInputManager.INPUT_STATE_DISCONNECTED;
            final Uri uri = TvContract.buildChannelUriForPassthroughInput(info.getId());
            addRow(rows, hdmi ? R.drawable.ic_input : R.drawable.ic_av,
                    hasCustom ? custom.toString() : label, hasCustom ? label : null, enabled,
                    () -> view(uri, null));
        }
    }

    private void view(Uri uri, String pkg) {
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (pkg != null) i.setPackage(pkg);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            if (cfg.inputAction.isEmpty()) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(cfg.inputAction).setDataAndType(uri, "vnd.android.cursor.item/channel")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
        finish();
    }

    private void addRow(LinearLayout rows, int iconRes, String title, String subtitle, boolean enabled, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(22), 0, dp(22), 0);
        row.setFocusable(true);
        row.setClickable(true);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(CIRCLE_BG);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ICON_DIM);
        icon.setBackground(circle);
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(16), 0, 0, 0);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setSingleLine(true);
        texts.addView(t);
        if (subtitle != null) {
            TextView s = new TextView(this);
            s.setText(subtitle);
            s.setTextColor(SUBTITLE);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            s.setSingleLine(true);
            texts.addView(s);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!enabled) row.setAlpha(0.4f);
        row.setOnFocusChangeListener((v, focused) -> {
            row.setBackgroundColor(focused ? FOCUS_BG : Color.TRANSPARENT);
            circle.setColor(focused ? Color.TRANSPARENT : CIRCLE_BG);
            circle.setStroke(focused ? dp(2) : 0, Color.WHITE);
            icon.setColorFilter(focused ? Color.WHITE : ICON_DIM);
        });
        row.setOnClickListener(v -> action.run());
        rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
    }

    private int dp(int v) {
        return (int) (v * density);
    }
}
