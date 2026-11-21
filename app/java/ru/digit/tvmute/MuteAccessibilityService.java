package ru.digit.tvmute;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Перехватывает только кнопку Mi (keyCode 191).
 * Короткое нажатие — mute, удержание — PatchWall.
 */
public class MuteAccessibilityService extends AccessibilityService {

    private static final String TAG = "TVMute";
    private static final int KEYCODE_MI = 191;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PressStateMachine machine = new PressStateMachine();

    private final Runnable timeout = new Runnable() {
        @Override
        public void run() {
            PressStateMachine.Action action = machine.onTimeout();
            Log.i(TAG, "key=191 timeout action=" + action);
            perform(action);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        Log.i(TAG, "service connected");
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KEYCODE_MI) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                machine.onDown(SystemClock.uptimeMillis(), false);
                handler.removeCallbacks(timeout);
                handler.postDelayed(timeout, PressStateMachine.LONG_PRESS_MS);
            }
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            long now = SystemClock.uptimeMillis();
            handler.removeCallbacks(timeout);
            PressStateMachine.Action action = machine.onUp(now);
            Log.i(TAG, "key=191 up action=" + action);
            perform(action);
            return true;
        }

        handler.removeCallbacks(timeout);
        machine.reset();
        return true;
    }

    private void perform(PressStateMachine.Action action) {
        switch (action) {
            case MUTE:
                Actions.toggleMute(this);
                break;
            case OPEN_PATCHWALL:
                Actions.openPatchWall(this);
                break;
            default:
                break;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Только фильтрация клавиш, события окон не нужны.
    }

    @Override
    public void onInterrupt() {
    }
}
