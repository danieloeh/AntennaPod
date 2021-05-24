package de.danoeh.antennapod.net.downloadservice;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import de.danoeh.antennapod.core.event.FeedItemEvent;
import de.danoeh.antennapod.core.feed.LocalFeedUpdater;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.download.DownloadStatus;
import de.danoeh.antennapod.net.downloadservice.handler.FeedSyncTask;
import de.danoeh.antennapod.net.downloadservice.handler.MediaDownloadedHandler;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.util.DownloadError;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import org.apache.commons.io.FileUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class DownloadWorker extends Worker {
    private static final String TAG = "DownloadWorker";

    private HttpDownloader downloader;

    public static void enqueue(Context context, DownloadRequest request) {
        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(DownloadWorker.class);
        builder.setInputData(request.toWorkData());
        builder.addTag(DownloadRequest.TAG);
        WorkManager.getInstance(context).enqueue(builder.build());
    }

    public static void cancel(Context context, @NonNull String downloadUrl) {
        try {
            List<WorkInfo> downloads = WorkManager.getInstance(context).getWorkInfosByTag(DownloadRequest.TAG).get();
            for (WorkInfo workInfo : downloads) {
                DownloadRequest request = DownloadRequest.from(workInfo.getProgress());
                if (!workInfo.getState().isFinished() && downloadUrl.equals(request.getSource())) {
                    WorkManager.getInstance(context).cancelWorkById(workInfo.getId());
                }
            }
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        DownloadRequest request = DownloadRequest.from(getInputData());
        setProgressAsync(request.toWorkData());
        if (request.getFeedfileType() == Feed.FEEDFILETYPE_FEED) {
            Feed feed = DBReader.getFeed(request.getFeedfileId());
            if (feed.isLocalFeed()) {
                LocalFeedUpdater.updateFeed(feed, getApplicationContext());
                return Result.success();
            }
        }

        if (request.isCleanupMedia()) {
            UserPreferences.getEpisodeCleanupAlgorithm().makeRoomForEpisodes(getApplicationContext(), 1); // TODO
        }

        // First, add to-download items to the queue before actual download
        // so that the resulting queue order is the same as when download is clicked
        if (request.getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
            long mediaId = request.getFeedfileId();
            FeedMedia media = DBReader.getFeedMedia(mediaId);
            if (media != null) {
                DBWriter.addQueueItem(getApplicationContext(), false, false, media.getItemId());
                request.setMediaEnqueued(true);
            }
        }

        writeFileUrl(request);
        downloader = new HttpDownloader(request);
        downloader.setProgressUpdateListener(() -> setProgressAsync(request.toWorkData()));
        downloader.call();

        if (downloader.getResult().isSuccessful()) {
            handleSuccessfulDownload(downloader);
            return Result.success();
        } else {
            handleFailedDownload(downloader);
            return Result.failure();
        }
    }

    @Override
    public void onStopped() {
        super.onStopped();
        if (downloader != null) {
            downloader.cancel();
        }
    }

    /**
     * Creates the destination file and writes FeedMedia File_url directly after starting download
     * to make it possible to resume download after the service was killed by the system.
     */
    private void writeFileUrl(DownloadRequest request) {
        if (request.getFeedfileType() != FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
            return;
        }

        File dest = new File(request.getDestination());
        if (!dest.exists()) {
            try {
                dest.createNewFile();
            } catch (IOException e) {
                Log.e(TAG, "Unable to create file");
            }
        }

        if (dest.exists()) {
            Log.d(TAG, "Writing file url");
            FeedMedia media = DBReader.getFeedMedia(request.getFeedfileId());
            if (media == null) {
                Log.d(TAG, "No media");
                return;
            }
            media.setFile_url(request.getDestination());
            try {
                DBWriter.setFeedMedia(media).get();
            } catch (InterruptedException e) {
                Log.e(TAG, "writeFileUrl was interrupted");
            } catch (ExecutionException e) {
                Log.e(TAG, "ExecutionException in writeFileUrl: " + e.getMessage());
            }
        }
    }

    private void handleSuccessfulDownload(Downloader downloader) {
        DownloadRequest request = downloader.getDownloadRequest();
        DownloadStatus status = downloader.getResult();
        final int type = status.getFeedfileType();

        if (type == Feed.FEEDFILETYPE_FEED) {
            Log.d(TAG, "Handling completed Feed Download");
            FeedSyncTask task = new FeedSyncTask(getApplicationContext(), request);
            boolean success = task.run();

            if (success) {
                // we create a 'successful' download log if the feed's last refresh failed
                List<DownloadStatus> log = DBReader.getFeedDownloadLog(request.getFeedfileId());
                if (log.size() > 0 && !log.get(0).isSuccessful()) {
                    saveDownloadStatus(task.getDownloadStatus());
                }
                if (request.getFeedfileId() != 0 && !request.isInitiatedByUser()) {
                    // Was stored in the database before and not initiated manually
                    //newEpisodesNotification.showIfNeeded(DownloadService.this, task.getSavedFeed());
                }
            } else {
                DBWriter.setFeedLastUpdateFailed(request.getFeedfileId(), true);
                saveDownloadStatus(task.getDownloadStatus());
            }
        } else if (type == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
            Log.d(TAG, "Handling completed FeedMedia Download");
            MediaDownloadedHandler handler = new MediaDownloadedHandler(getApplicationContext(), status, request);
            handler.run();
            saveDownloadStatus(handler.getUpdatedStatus());
        }
    }

    private void handleFailedDownload(Downloader downloader) {
        DownloadStatus status = downloader.getResult();
        final int type = status.getFeedfileType();

        if (!status.isCancelled()) {
            if (status.getReason() == DownloadError.ERROR_UNAUTHORIZED) {
                //notificationManager.postAuthenticationNotification(downloader.getDownloadRequest());
            } else if (status.getReason() == DownloadError.ERROR_HTTP_DATA_ERROR
                    && Integer.parseInt(status.getReasonDetailed()) == 416) {

                Log.d(TAG, "Requested invalid range, restarting download from the beginning");
                FileUtils.deleteQuietly(new File(downloader.getDownloadRequest().getDestination()));
                //DownloadRequester.getInstance().download(DownloadService.this, downloader.getDownloadRequest());
            } else {
                Log.e(TAG, "Download failed");
                saveDownloadStatus(status);
                //syncExecutor.execute(new FailedDownloadHandler(downloader.getDownloadRequest()));

                if (type == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
                    FeedMedia media = DBReader.getFeedMedia(status.getFeedfileId());
                    if (media == null || media.getItem() == null) {
                        return;
                    }
                    FeedItem item = media.getItem();
                    boolean unknownHost = status.getReason() == DownloadError.ERROR_UNKNOWN_HOST;
                    boolean unsupportedType = status.getReason() == DownloadError.ERROR_UNSUPPORTED_TYPE;
                    boolean wrongSize = status.getReason() == DownloadError.ERROR_IO_WRONG_SIZE;

                    if (! (unknownHost || unsupportedType || wrongSize)) {
                        try {
                            DBWriter.saveFeedItemAutoDownloadFailed(item).get();
                        } catch (ExecutionException | InterruptedException e) {
                            Log.d(TAG, "Ignoring exception while setting item download status");
                            e.printStackTrace();
                        }
                    }
                    // to make lists reload the failed item, we fake an item update
                    EventBus.getDefault().post(FeedItemEvent.updated(item));
                }
            }
        } else {
            // if FeedMedia download has been canceled, fake FeedItem update
            // so that lists reload that it
            if (status.getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
                FeedMedia media = DBReader.getFeedMedia(status.getFeedfileId());
                if (media == null || media.getItem() == null) {
                    return;
                }
                EventBus.getDefault().post(FeedItemEvent.updated(media.getItem()));
            }
        }
    }

    /**
     * Adds a new DownloadStatus object to the list of completed downloads and
     * saves it in the database
     *
     * @param status the download that is going to be saved
     */
    private void saveDownloadStatus(DownloadStatus status) {
        //reportQueue.add(status);
        DBWriter.addDownloadStatus(status);
    }
}
