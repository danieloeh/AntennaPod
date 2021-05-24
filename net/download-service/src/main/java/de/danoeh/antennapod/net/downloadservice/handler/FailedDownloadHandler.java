package de.danoeh.antennapod.net.downloadservice.handler;

import android.util.Log;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.net.downloadservice.DownloadRequest;

/**
 * Handles failed downloads.
 * <p/>
 * If the file has been partially downloaded, this handler will set the file_url of the FeedFile to the location
 * of the downloaded file.
 * <p/>
 * Currently, this handler only handles FeedMedia objects, because Feeds and FeedImages are deleted if the download fails.
 */
public class FailedDownloadHandler implements Runnable {
    private static final String TAG = "FailedDownloadHandler";
    private final DownloadRequest request;

    public FailedDownloadHandler(DownloadRequest request) {
        this.request = request;
    }

    @Override
    public void run() {
        if (request.getFeedfileType() == Feed.FEEDFILETYPE_FEED) {
            DBWriter.setFeedLastUpdateFailed(request.getFeedfileId(), true);
        } else if (request.isDeleteOnFailure()) {
            Log.d(TAG, "Ignoring failed download, deleteOnFailure=true");
        }
    }
}
