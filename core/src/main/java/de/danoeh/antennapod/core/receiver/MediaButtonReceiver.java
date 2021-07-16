package de.danoeh.antennapod.core.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import android.util.Log;
import android.view.KeyEvent;

import de.danoeh.antennapod.core.ClientConfig;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.playback.PlaybackService;
import de.danoeh.antennapod.core.widget.WidgetUpdaterJobService;

/**
 * Receives media button events.
 */
public class MediaButtonReceiver extends BroadcastReceiver {
    public static final String NOTIFY_PROGRESS_STRING_RECEIVER = "de.danoeh.antennapod.NOTIFY_PROGRESS_STRING_RECEIVER";
    public static final String DUMMY_VALUE = "DUMMY_VALUE";
    private static final String TAG = "MediaButtonReceiver";
    public static final String EXTRA_KEYCODE = "de.danoeh.antennapod.core.service.extra.MediaButtonReceiver.KEYCODE";
    public static final String EXTRA_SOURCE = "de.danoeh.antennapod.core.service.extra.MediaButtonReceiver.SOURCE";
    public static final String EXTRA_HARDWAREBUTTON = "de.danoeh.antennapod.core.service.extra."
            + "MediaButtonReceiver.HARDWAREBUTTON";

    public static final String NOTIFY_BUTTON_RECEIVER = "de.danoeh.antennapod.NOTIFY_BUTTON_RECEIVER";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received intent");
        if (intent == null || intent.getExtras() == null) {
            return;
        }

        if (intent.getAction().equals(NOTIFY_PROGRESS_STRING_RECEIVER)) {
            Log.d(TAG, "onReceive: got it");
            boolean showTimeLeft = UserPreferences.shouldShowRemainingTime();
            showTimeLeft = !showTimeLeft;
            UserPreferences.setShowRemainTimeSetting(showTimeLeft);
            WidgetUpdaterJobService.performBackgroundUpdate(context);
            return;
        }

        KeyEvent event = (KeyEvent) intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
        if (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            ClientConfig.initialize(context);
            Intent serviceIntent = new Intent(context, PlaybackService.class);
            serviceIntent.putExtra(EXTRA_KEYCODE, event.getKeyCode());
            serviceIntent.putExtra(EXTRA_SOURCE, event.getSource());
            //detect if this is a hardware button press
            if (event.getEventTime() > 0 || event.getDownTime() > 0) {
                serviceIntent.putExtra(EXTRA_HARDWAREBUTTON, true);
            } else {
                serviceIntent.putExtra(EXTRA_HARDWAREBUTTON, false);
            }
            ContextCompat.startForegroundService(context, serviceIntent);
        }

    }

}
