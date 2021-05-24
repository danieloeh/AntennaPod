package de.danoeh.antennapod.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import de.danoeh.antennapod.core.ClientConfig;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.storage.DBTasks;
import de.danoeh.antennapod.net.downloadservice.DownloadRequester;

// modified from http://developer.android.com/training/monitoring-device-state/battery-monitoring.html
// and ConnectivityActionReceiver.java
// Updated based on http://stackoverflow.com/questions/20833241/android-charge-intent-has-no-extra-data
// Since the intent doesn't have the EXTRA_STATUS like the android.com article says it does
// (though it used to)
public class PowerConnectionReceiver extends BroadcastReceiver {
	private static final String TAG = "PowerConnectionReceiver";

	@Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        Log.d(TAG, "charging intent: " + action);

        ClientConfig.initialize(context);
        if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            Log.d(TAG, "charging, starting auto-download");
            // we're plugged in, this is a great time to auto-download if everything else is
            // right. So, even if the user allows auto-dl on battery, let's still start
            // downloading now. They shouldn't mind.
            // autodownloadUndownloadedItems will make sure we're on the right wifi networks,
            // etc... so we don't have to worry about it.
            DBTasks.autodownloadUndownloadedItems(context);
        } else {
            // if we're not supposed to be auto-downloading when we're not charging, stop it
            if (!UserPreferences.isEnableAutodownloadOnBattery()) {
                Log.d(TAG, "not charging anymore, canceling auto-download");
                DownloadRequester.getInstance().cancelAllDownloads(context);
            } else {
                Log.d(TAG, "not charging anymore, but the user allows auto-download " +
                           "when on battery so we'll keep going");
            }
        }

    }
}
