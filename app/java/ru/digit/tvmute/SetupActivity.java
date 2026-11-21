package ru.digit.tvmute;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class SetupActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("TV Mute");
        tv.setTextSize(32);
        tv.setGravity(Gravity.CENTER);
        setContentView(tv);
    }
}
