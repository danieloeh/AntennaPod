package de.danoeh.antennapod.config;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.DownloadAuthenticationActivity;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.net.downloadservice.DownloadRequest;
import de.danoeh.antennapod.fragment.DownloadsFragment;
import de.danoeh.antennapod.fragment.QueueFragment;


public class DownloadServiceCallbacksImpl {

    public PendingIntent getNotificationContentIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_FRAGMENT_TAG, DownloadsFragment.TAG);
        Bundle args = new Bundle();
        args.putInt(DownloadsFragment.ARG_SELECTED_TAB, DownloadsFragment.POS_LOG);
        intent.putExtra(MainActivity.EXTRA_FRAGMENT_ARGS, args);
        return PendingIntent.getActivity(context,
                R.id.pending_intent_download_service_notification, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public PendingIntent getAuthentificationNotificationContentIntent(Context context, DownloadRequest request) {
        final Intent activityIntent = new Intent(context.getApplicationContext(), DownloadAuthenticationActivity.class);
        activityIntent.setAction("request" + request.getFeedfileId());
        activityIntent.putExtra(DownloadAuthenticationActivity.ARG_DOWNLOAD_REQUEST, request);
        return PendingIntent.getActivity(context.getApplicationContext(),
                R.id.pending_intent_download_service_auth, activityIntent, PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent getReportNotificationContentIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_FRAGMENT_TAG, DownloadsFragment.TAG);
        Bundle args = new Bundle();
        args.putInt(DownloadsFragment.ARG_SELECTED_TAB, DownloadsFragment.POS_LOG);
        intent.putExtra(MainActivity.EXTRA_FRAGMENT_ARGS, args);
        return PendingIntent.getActivity(context, R.id.pending_intent_download_service_report,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public PendingIntent getAutoDownloadReportNotificationContentIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_FRAGMENT_TAG, QueueFragment.TAG);
        return PendingIntent.getActivity(context, R.id.pending_intent_download_service_autodownload_report,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
