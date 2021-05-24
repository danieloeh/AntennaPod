package de.danoeh.antennapod.net.downloadservice.handler;

import android.content.Context;
import android.util.Log;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.core.service.download.DownloadStatus;
import de.danoeh.antennapod.core.storage.DBTasks;
import de.danoeh.antennapod.net.downloadservice.DownloadRequestException;
import de.danoeh.antennapod.net.downloadservice.DownloadRequester;
import de.danoeh.antennapod.core.syndication.handler.FeedHandlerResult;
import de.danoeh.antennapod.net.downloadservice.DownloadRequest;

public class FeedSyncTask {
    private static final String TAG = "FeedParserTask";
    private final DownloadRequest request;
    private final Context context;
    private DownloadStatus downloadStatus;
    private Feed savedFeed;

    public FeedSyncTask(Context context, DownloadRequest request) {
        this.request = request;
        this.context = context;
    }

    public boolean run() {
        FeedParserTask task = new FeedParserTask(request);
        FeedHandlerResult result = task.call();
        downloadStatus = task.getDownloadStatus();

        if (!task.isSuccessful()) {
            return false;
        }

        savedFeed = DBTasks.updateFeed(context, result.feed, false);
        // If loadAllPages=true, check if another page is available and queue it for download
        final boolean loadAllPages = request.getArguments().getBoolean(DownloadRequester.REQUEST_ARG_LOAD_ALL_PAGES);
        final Feed feed = result.feed;
        if (loadAllPages && feed.getNextPageLink() != null) {
            try {
                feed.setId(savedFeed.getId());
                DBTasks.loadNextPageOfFeed(context, feed, true);
            } catch (Exception e) {
                Log.e(TAG, "Error trying to load next page", e);
            }
        }
        return true;
    }

    public DownloadStatus getDownloadStatus() {
        return downloadStatus;
    }

    public Feed getSavedFeed() {
        return savedFeed;
    }
}
