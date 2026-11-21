package ru.digit.tvmute;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;

final class Actions {

    private static final String TAG = "TVMute";
    private static final String PATCHWALL_PKG = "com.mitv.tvhome.atv";
    private static final String PATCHWALL_CLS = "com.mitv.tvhome.atv.app.BannerLogoB";
    private static final int[] STREAMS = {0, 1, 2, 3, 4, 5, 7, 8, 9, 10};

    private Actions() {
    }

    static void toggleMute(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            Log.e(TAG, "AudioManager is null");
            return;
        }
        boolean mute = !am.isStreamMute(AudioManager.STREAM_MUSIC);
        for (int stream : STREAMS) {
            try {
                am.adjustStreamVolume(stream,
                        mute ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
            } catch (SecurityException e) {
                Log.w(TAG, "stream " + stream + " not adjustable: " + e.getMessage());
            }
        }
        Log.i(TAG, "mute=" + mute);
    }

    static void openPatchWall(Context ctx) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(PATCHWALL_PKG, PATCHWALL_CLS))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "PatchWall activity not found", e);
        }
    }
}
