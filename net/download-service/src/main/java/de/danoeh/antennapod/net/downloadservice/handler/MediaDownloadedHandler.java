package de.danoeh.antennapod.net.downloadservice.handler;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.concurrent.ExecutionException;

import de.danoeh.antennapod.core.event.UnreadItemsUpdateEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.core.service.download.DownloadStatus;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.util.ChapterUtils;
import de.danoeh.antennapod.core.util.DownloadError;
import de.danoeh.antennapod.core.sync.SyncService;
import de.danoeh.antennapod.net.downloadservice.DownloadRequest;
import de.danoeh.antennapod.net.sync.model.EpisodeAction;
import org.greenrobot.eventbus.EventBus;

/**
 * Handles a completed media download.
 */
public class MediaDownloadedHandler implements Runnable {
    private static final String TAG = "MediaDownloadedHandler";
    private final DownloadRequest request;
    private final DownloadStatus status;
    private final Context context;
    private DownloadStatus updatedStatus;

    public MediaDownloadedHandler(@NonNull Context context, @NonNull DownloadStatus status,
                                  @NonNull DownloadRequest request) {
        this.status = status;
        this.request = request;
        this.context = context;
    }

    @Override
    public void run() {
        updatedStatus = status;
        FeedMedia media = DBReader.getFeedMedia(request.getFeedfileId());
        if (media == null) {
            Log.e(TAG, "Could not find downloaded media object in database");
            return;
        }
        // media.setDownloaded modifies played state
        boolean broadcastUnreadStateUpdate = media.getItem() != null && media.getItem().isNew();
        media.setDownloaded(true);
        media.setFile_url(request.getDestination());
        media.setSize(new File(request.getDestination()).length());
        media.checkEmbeddedPicture(); // enforce check

        // check if file has chapters
        if (media.getItem() != null && !media.getItem().hasChapters()) {
            media.setChapters(ChapterUtils.loadChaptersFromMediaFile(media, context));
        }

        // Get duration
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        String durationStr = null;
        try {
            mmr.setDataSource(media.getFile_url());
            durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            media.setDuration(Integer.parseInt(durationStr));
            Log.d(TAG, "Duration of file is " + media.getDuration());
        } catch (NumberFormatException e) {
            Log.d(TAG, "Invalid file duration: " + durationStr);
        } catch (Exception e) {
            Log.e(TAG, "Get duration failed", e);
        } finally {
            mmr.release();
        }

        final FeedItem item = media.getItem();

        try {
            DBWriter.setFeedMedia(media).get();

            // we've received the media, we don't want to autodownload it again
            if (item != null) {
                item.setAutoDownload(false);
                // setFeedItem() signals (via EventBus) that the item has been updated,
                // so we do it after the enclosing media has been updated above,
                // to ensure subscribers will get the updated FeedMedia as well
                DBWriter.setFeedItem(item).get();
                if (broadcastUnreadStateUpdate) {
                    EventBus.getDefault().post(new UnreadItemsUpdateEvent());
                }
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "MediaHandlerThread was interrupted");
        } catch (ExecutionException e) {
            Log.e(TAG, "ExecutionException in MediaHandlerThread: " + e.getMessage());
            updatedStatus = new DownloadStatus(media, media.getEpisodeTitle(),
                    DownloadError.ERROR_DB_ACCESS_ERROR, false, e.getMessage(), request.isInitiatedByUser());
        }

        if (item != null) {
            EpisodeAction action = new EpisodeAction.Builder(item, EpisodeAction.DOWNLOAD)
                    .currentTimestamp()
                    .build();
            SyncService.enqueueEpisodeAction(context, action);
        }
    }

    public DownloadStatus getUpdatedStatus() {
        return updatedStatus;
    }
}
