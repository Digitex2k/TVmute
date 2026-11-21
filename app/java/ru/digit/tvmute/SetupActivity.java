package ru.digit.tvmute;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SetupActivity extends Activity {

    private TextView status;
    private TextView hint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(48);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.parseColor("#101418"));
        scroll.addView(root, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(40);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.service_description);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        subtitle.setTextColor(Color.parseColor("#90A4AE"));
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(8);
        root.addView(subtitle, subtitleParams);

        status = new TextView(this);
        status.setTextSize(24);
        status.setTextColor(Color.parseColor("#B0BEC5"));
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(24);
        root.addView(status, statusParams);

        hint = new TextView(this);
        hint.setText(R.string.service_off_hint);
        hint.setTextSize(16);
        hint.setTextColor(Color.parseColor("#FF8A65"));
        hint.setGravity(Gravity.CENTER);
        hint.setVisibility(View.GONE);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(16);
        root.addView(hint, hintParams);

        Button open = new Button(this);
        open.setId(View.generateViewId());
        styleTvButton(open, R.string.open_accessibility, R.drawable.ic_open);
        open.setOnClickListener(v -> openSettings());
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        openParams.topMargin = dp(24);
        root.addView(open, openParams);

        Button instructions = new Button(this);
        instructions.setId(View.generateViewId());
        styleTvButton(instructions, R.string.instructions_button, R.drawable.ic_book);
        instructions.setOnClickListener(v -> startActivity(
                new Intent(this, InstructionsActivity.class)));
        LinearLayout.LayoutParams instructionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        instructionsParams.topMargin = dp(16);
        root.addView(instructions, instructionsParams);

        Button check = new Button(this);
        check.setId(View.generateViewId());
        styleTvButton(check, R.string.how_to_check, R.drawable.ic_info);
        check.setOnClickListener(v -> status.setText(R.string.check_hint));
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        checkParams.topMargin = dp(16);
        root.addView(check, checkParams);

        open.setNextFocusDownId(instructions.getId());
        instructions.setNextFocusUpId(open.getId());
        instructions.setNextFocusDownId(check.getId());
        check.setNextFocusUpId(instructions.getId());

        setContentView(scroll);
        open.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean enabled = isServiceEnabled();
        status.setText(enabled ? R.string.service_on : R.string.service_off);
        hint.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    private void openSettings() {
        Intent accessibility = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        if (isStub(accessibility)) {
            Intent main = new Intent(Settings.ACTION_SETTINGS)
                    .setComponent(new ComponentName("com.android.tv.settings",
                            "com.android.tv.settings.MainSettings"));
            try {
                startActivity(main);
                return;
            } catch (ActivityNotFoundException ignored) {
                // fall through to the standard accessibility intent
            }
        }
        startActivity(accessibility);
    }

    private boolean isStub(Intent intent) {
        ResolveInfo info = getPackageManager().resolveActivity(intent, 0);
        return info != null && info.activityInfo != null
                && "com.google.android.tv.frameworkpackagestubs"
                .equals(info.activityInfo.packageName);
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) {
            return false;
        }
        ComponentName me = new ComponentName(this, MuteAccessibilityService.class);
        return enabled.contains(me.flattenToString())
                || enabled.contains(me.flattenToShortString());
    }

    private void styleTvButton(Button button, int textRes, int iconRes) {
        button.setText(textRes);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        button.setTextColor(Color.WHITE);
        button.setBackgroundResource(R.drawable.tv_button);
        button.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        button.setCompoundDrawablePadding(dp(12));
        int vpad = dp(14);
        int hpad = dp(20);
        button.setPadding(hpad, vpad, hpad, vpad);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
