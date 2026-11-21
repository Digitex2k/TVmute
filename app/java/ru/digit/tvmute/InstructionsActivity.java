package ru.digit.tvmute;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class InstructionsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(48);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setFocusable(true);
        scroll.setFocusableInTouchMode(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.parseColor("#101418"));
        scroll.addView(root, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.instructions_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        addSection(root, R.string.details_title, R.string.details_what);
        addSection(root, R.string.enable_title, R.string.enable_text);
        addSection(root, R.string.how_to_check, R.string.check_hint);
        addSection(root, R.string.notes_title, R.string.notes_text);

        Button back = new Button(this);
        back.setId(View.generateViewId());
        styleTvButton(back, R.string.back, R.drawable.ic_back);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        backParams.topMargin = dp(32);
        root.addView(back, backParams);

        setContentView(scroll);
        scroll.requestFocus();
    }

    private void addSection(LinearLayout root, int titleRes, int bodyRes) {
        TextView sectionTitle = sectionTitle(titleRes);
        root.addView(sectionTitle, sectionParams());

        TextView body = bodyText(bodyRes);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(8);
        root.addView(body, bodyParams);
    }

    private TextView sectionTitle(int textRes) {
        TextView tv = new TextView(this);
        tv.setText(textRes);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(28);
        return params;
    }

    private TextView bodyText(int textRes) {
        TextView tv = new TextView(this);
        tv.setText(textRes);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTextColor(Color.parseColor("#CFD8DC"));
        tv.setLineSpacing(0, 1.25f);
        return tv;
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
