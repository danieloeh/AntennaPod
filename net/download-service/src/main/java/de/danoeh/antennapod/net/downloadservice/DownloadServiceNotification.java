package de.danoeh.antennapod.net.downloadservice;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import de.danoeh.antennapod.core.ClientConfig;
import de.danoeh.antennapod.core.R;
import de.danoeh.antennapod.core.service.download.DownloadStatus;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.core.util.gui.NotificationUtils;
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter;

import java.util.List;

public class DownloadServiceNotification {
    private static final String TAG = "DownloadSvcNotification";

    private final Context context;
    private NotificationCompat.Builder notificationCompatBuilder;

    public DownloadServiceNotification(Context context) {
        this.context = context;
        setupNotificationBuilders();
    }

    private void setupNotificationBuilders() {

        notificationCompatBuilder = new NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID_DOWNLOADING)
                .setOngoing(false)
                .setWhen(0)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(new MainActivityStarter(context).withOpenDownloadLogs().getPendingIntent())
                .setSmallIcon(R.drawable.ic_notification_sync);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notificationCompatBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        }

        Log.d(TAG, "Notification set up");
    }

    /**
     * Updates the contents of the service's notifications. Should be called
     * after setupNotificationBuilders.
     */
    public Notification updateNotifications(int numDownloads, List<Downloader> downloads) {
        if (notificationCompatBuilder == null) {
            return null;
        }

        String contentTitle = context.getString(R.string.download_notification_title);
        String downloadsLeft = (numDownloads > 0)
                ? context.getResources().getQuantityString(R.plurals.downloads_left, numDownloads, numDownloads)
                : context.getString(R.string.service_shutting_down);
        String bigText = compileNotificationString(downloads);

        notificationCompatBuilder.setContentTitle(contentTitle);
        notificationCompatBuilder.setContentText(downloadsLeft);
        notificationCompatBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(bigText));
        return notificationCompatBuilder.build();
    }

    private static String compileNotificationString(List<Downloader> downloads) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < downloads.size(); i++) {
            Downloader downloader = downloads.get(i);
            if (downloader.cancelled) {
                continue;
            }
            stringBuilder.append("• ");
            DownloadRequest request = downloader.getDownloadRequest();
            switch (request.getFeedfileType()) {
                case Feed.FEEDFILETYPE_FEED:
                    if (request.getTitle() != null) {
                        stringBuilder.append(request.getTitle());
                    }
                    break;
                case FeedMedia.FEEDFILETYPE_FEEDMEDIA:
                    if (request.getTitle() != null) {
                        stringBuilder.append(request.getTitle())
                                .append(" (")
                                .append(request.getProgressPercent())
                                .append("%)");
                    }
                    break;
                default:
                    stringBuilder.append("Unknown: ").append(request.getFeedfileType());
            }
            if (i != downloads.size()) {
                stringBuilder.append("\n");
            }
        }
        return stringBuilder.toString();
    }

    private static String createAutoDownloadNotificationContent(List<DownloadStatus> statuses) {
        int length = statuses.size();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            sb.append("• ").append(statuses.get(i).getTitle());
            if (i != length - 1) {
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String createFailedDownloadNotificationContent(List<DownloadStatus> statuses) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < statuses.size(); i++) {
            if (statuses.get(i).isSuccessful()) {
                continue;
            }
            sb.append("• ").append(statuses.get(i).getTitle());
            sb.append(": ").append(statuses.get(i).getReason().getErrorString(context));
            if (i != statuses.size() - 1) {
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Creates a notification at the end of the service lifecycle to notify the
     * user about the number of completed downloads. A report will only be
     * created if there is at least one failed download excluding images
     */
    public void updateReport(List<DownloadStatus> reportQueue, boolean showAutoDownloadReport) {
        // check if report should be created
        boolean createReport = false;
        int successfulDownloads = 0;
        int failedDownloads = 0;

        // a download report is created if at least one download has failed
        // (excluding failed image downloads)
        for (DownloadStatus status : reportQueue) {
            if (status.isSuccessful()) {
                successfulDownloads++;
                createReport |= showAutoDownloadReport && !status.isInitiatedByUser() && status.getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA;
            } else if (!status.isCancelled()) {
                failedDownloads++;
                createReport = true;
            }
        }

        if (createReport) {
            Log.d(TAG, "Creating report");

            // create notification object
            String channelId;
            int titleId;
            int iconId;
            int id;
            String content;
            PendingIntent intent;
            if (failedDownloads == 0) {
                // We are generating an auto-download report
                channelId = NotificationUtils.CHANNEL_ID_AUTO_DOWNLOAD;
                titleId = R.string.auto_download_report_title;
                iconId = R.drawable.ic_notification_new;
                intent = new MainActivityStarter(context).withOpenDownloadLogs().getPendingIntent();
                id = R.id.notification_auto_download_report;
                content = createAutoDownloadNotificationContent(reportQueue);
            } else {
                channelId = NotificationUtils.CHANNEL_ID_DOWNLOAD_ERROR;
                titleId = R.string.download_report_title;
                iconId = R.drawable.ic_notification_sync_error;
                intent = new MainActivityStarter(context).withOpenDownloadLogs().getPendingIntent();
                id = R.id.notification_download_report;
                content = createFailedDownloadNotificationContent(reportQueue);
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);
            builder.setTicker(context.getString(titleId))
                   .setContentTitle(context.getString(titleId))
                   .setContentText(content)
                   .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                   .setSmallIcon(iconId)
                   .setContentIntent(intent)
                   .setAutoCancel(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            }
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(id, builder.build());
            Log.d(TAG, "Download report notification was posted");
        } else {
            Log.d(TAG, "No report is created");
        }
    }

    public void postAuthenticationNotification(final DownloadRequest downloadRequest) {
        final String resourceTitle = (downloadRequest.getTitle() != null) ?
                downloadRequest.getTitle() : downloadRequest.getSource();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID_USER_ACTION);
        builder.setTicker(context.getText(R.string.authentication_notification_title))
                .setContentTitle(context.getText(R.string.authentication_notification_title))
                .setContentText(context.getText(R.string.authentication_notification_msg))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getText(R.string.authentication_notification_msg)
                        + ": " + resourceTitle))
                .setSmallIcon(R.drawable.ic_notification_key)
                .setAutoCancel(true);
                //.setContentIntent(ClientConfig.downloadServiceCallbacks.getAuthentificationNotificationContentIntent(context, downloadRequest));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        }
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(downloadRequest.getSource().hashCode(), builder.build());
    }
}
