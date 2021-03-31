package de.danoeh.antennapod.core.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import android.util.Log;

import java.io.File;
import java.util.List;

import de.danoeh.antennapod.core.R;
import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.FeedMedia;

/** Utility methods for sharing data */
public class ShareUtils {
    private static final String TAG = "ShareUtils";

    private ShareUtils() {
    }

    public static void shareLink(Context context, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(i, context.getString(R.string.share_url_label)));
    }

    public static void shareFeedlink(Context context, Feed feed) {
        shareLink(context, feed.getTitle() + ": " + feed.getLink());
    }

    public static void shareFeedDownloadLink(Context context, Feed feed) {
        shareLink(context, feed.getTitle() + ": " + feed.getDownload_url());
    }

    public static void shareFeedItemLink(Context context, FeedItem item) {
        shareFeedItemLink(context, item, false);
    }

    public static void shareFeedItemDownloadLink(Context context, FeedItem item) {
        shareFeedItemDownloadLink(context, item, false);
    }

    public static String getItemShareText(FeedItem item) {
        return item.getFeed().getTitle() + ": " + item.getTitle();
    }

    public static boolean hasLinkToShare(FeedItem item) {
        return FeedItemUtil.getLinkWithFallback(item) != null;
    }

    public static void shareFeedItemLink(Context context, FeedItem item, boolean withPosition) {
        String text = getItemShareText(item) + " " + FeedItemUtil.getLinkWithFallback(item);
        if (withPosition) {
            int pos = item.getMedia().getPosition();
            text += " [" + Converter.getDurationStringLong(pos) + "]";
        }
        shareLink(context, text);
    }

    public static void shareFeedItemDownloadLink(Context context, FeedItem item, boolean withPosition) {
        String text = getItemShareText(item) + " " + item.getMedia().getDownload_url();
        if (withPosition) {
            int pos = item.getMedia().getPosition();
            text += "#t=" + pos / 1000;
            text += " [" + Converter.getDurationStringLong(pos) + "]";
        }
        shareLink(context, text);
    }

    public static void shareFeedItemFile(Context context, FeedMedia media) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(media.getMime_type());
        Uri fileUri = FileProvider.getUriForFile(context, context.getString(R.string.provider_authority),
                new File(media.getLocalMediaUrl()));
        i.putExtra(Intent.EXTRA_STREAM,  fileUri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            List<ResolveInfo> resInfoList = context.getPackageManager()
                    .queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                context.grantUriPermission(packageName, fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }
        context.startActivity(Intent.createChooser(i, context.getString(R.string.share_file_label)));
        Log.e(TAG, "shareFeedItemFile called");
    }
}
