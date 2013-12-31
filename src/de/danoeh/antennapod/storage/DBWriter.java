package de.danoeh.antennapod.storage;

import java.io.File;
import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import org.shredzone.flattr4j.model.Flattr;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.util.Log;
import android.net.Uri;
import de.danoeh.antennapod.AppConfig;
import de.danoeh.antennapod.feed.*;
import de.danoeh.antennapod.preferences.GpodnetPreferences;
import de.danoeh.antennapod.preferences.PlaybackPreferences;
import de.danoeh.antennapod.service.GpodnetSyncService;
import de.danoeh.antennapod.service.PlaybackService;
import de.danoeh.antennapod.service.download.DownloadStatus;
import de.danoeh.antennapod.util.QueueAccess;
import de.danoeh.antennapod.util.flattr.*;

/**
 * Provides methods for writing data to AntennaPod's database.
 * In general, DBWriter-methods will be executed on an internal ExecutorService.
 * Some methods return a Future-object which the caller can use for waiting for the method's completion. The returned Future's
 * will NOT contain any results.
 * The caller can also use the {@link EventDistributor} in order to be notified about the method's completion asynchronously.
 * This class will use the {@link EventDistributor} to notify listeners about changes in the database.
 */
public class DBWriter {
    private static final String TAG = "DBWriter";

    private static final ExecutorService dbExec;

    static {
        dbExec = Executors.newSingleThreadExecutor(new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }
        });
    }

    private DBWriter() {
    }

    /**
     * Deletes a downloaded FeedMedia file from the storage device.
     *
     * @param context A context that is used for opening a database connection.
     * @param mediaId ID of the FeedMedia object whose downloaded file should be deleted.
     */
    public static Future<?> deleteFeedMediaOfItem(final Context context,
                                                  final long mediaId) {
        return dbExec.submit(new Runnable() {
            @Override
            public void run() {

                final FeedMedia media = DBReader.getFeedMedia(context, mediaId);
                if (media != null) {
                    Log.i(TAG, String.format("Requested to delete FeedMedia [id=%d, title=%s, downloaded=%s",
                            media.getId(), media.getEpisodeTitle(), String.valueOf(media.isDownloaded())));
                    boolean result = false;
                    if (media.isDownloaded()) {
                        // delete downloaded media file
                        File mediaFile = new File(media.getFile_url());
                        if (mediaFile.exists()) {
                            result = mediaFile.delete();
                        }
                        media.setDownloaded(false);
                        media.setFile_url(null);
                        PodDBAdapter adapter = new PodDBAdapter(context);
                        adapter.open();
                        adapter.setMedia(media);
                        adapter.close();

                        // If media is currently being played, change playback
                        // type to 'stream' and shutdown playback service
                        SharedPreferences prefs = PreferenceManager
                                .getDefaultSharedPreferences(context);
                        if (PlaybackPreferences.getCurrentlyPlayingMedia() == FeedMedia.PLAYABLE_TYPE_FEEDMEDIA) {
                            if (media.getId() == PlaybackPreferences
                                    .getCurrentlyPlayingFeedMediaId()) {
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putBoolean(
                                        PlaybackPreferences.PREF_CURRENT_EPISODE_IS_STREAM,
                                        true);
                                editor.commit();
                            }
                            if (PlaybackPreferences
                                    .getCurrentlyPlayingFeedMediaId() == media
                                    .getId()) {
                                context.sendBroadcast(new Intent(
                                        PlaybackService.ACTION_SHUTDOWN_PLAYBACK_SERVICE));
                            }
                        }
                    }
                    if (AppConfig.DEBUG)
                        Log.d(TAG, "Deleting File. Result: " + result);
                    EventDistributor.getInstance().sendQueueUpdateBroadcast();
                    EventDistributor.getInstance().sendUnreadItemsUpdateBroadcast();
                }
            }
        });
    }

    /**
     * Deletes a Feed and all downloaded files of its components like images and downloaded episodes.
     *
     * @param context A context that is used for opening a database connection.
     * @param feedId  ID of the Feed that should be deleted.
     */
    public static Future<?> deleteFeed(final Context context, final long feedId) {
        return dbExec.submit(new Runnable() {
            @Override
            public void run() {
                DownloadRequester requester = DownloadRequester.getInstance();
                SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(context
                                .getApplicationContext());
                final Feed feed = DBReader.getFeed(context, feedId);
                if (feed != null) {
                    if (PlaybackPreferences.getCurrentlyPlayingMedia() == FeedMedia.PLAYABLE_TYPE_FEEDMEDIA
                            && PlaybackPreferences.getLastPlayedFeedId() == feed
                            .getId()) {
                        context.sendBroadcast(new Intent(
                                PlaybackService.ACTION_SHUTDOWN_PLAYBACK_SERVICE));
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putLong(
                                PlaybackPreferences.PREF_CURRENTLY_PLAYING_FEED_ID,
                                -1);
                        editor.commit();
                    }

                    // delete image file
                    if (feed.getImage() != null) {
                        if (feed.getImage().isDownloaded()
                                && feed.getImage().getFile_url() != null) {
                            File imageFile = new File(feed.getImage()
                                    .getFile_url());
                            imageFile.delete();
                        } else if (requester.isDownloadingFile(feed.getImage())) {
                            requester.cancelDownload(context, feed.getImage());
                        }
                    }
                    // delete stored media files and mark them as read
                    List<FeedItem> queue = DBReader.getQueue(context);
                    boolean queueWasModified = false;
                    if (feed.getItems() == null) {
                        DBReader.getFeedItemList(context, feed);
                    }

                    for (FeedItem item : feed.getItems()) {
                        queueWasModified |= queue.remove(item);
                        if (item.getMedia() != null
                                && item.getMedia().isDownloaded()) {
                            File mediaFile = new File(item.getMedia()
                                    .getFile_url());
                            mediaFile.delete();
                        } else if (item.getMedia() != null
                                && requester.isDownloadingFile(item.getMedia())) {
                            requester.cancelDownload(context, item.getMedia());
                        }
                    }
                    PodDBAdapter adapter = new PodDBAdapter(context);
                    adapter.open();
                    if (queueWasModified) {
                        adapter.setQueue(queue);
                    }
                    adapter.removeFeed(feed);
                    adapter.close();

                    GpodnetPreferences.addRemovedFeed(feed.getDownload_url());
                    EventDistributor.getInstance().sendFeedUpdateBroadcast();
                }
            }
        });
    }

    /**
     * Deletes the entire playback history.
     *
     * @param context A context that is used for opening a database connection.
     */
    public static Future<?> clearPlaybackHistory(final Context context) {
        return dbExec.submit(new Runnable() {

            @Override
            public void run() {
                PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                adapter.clearPlaybackHistory();
                adapter.close();
                EventDistributor.getInstance()
                        .sendPlaybackHistoryUpdateBroadcast();
            }
        });
    }

    /**
     * Adds a FeedMedia object to the playback history. A FeedMedia object is in the playback history if
     * its playback completion date is set to a non-null value. This method will set the playback completion date to the
     * current date regardless of the current value.
     *
     * @param context A context that is used for opening a database connection.
     * @param media   FeedMedia that should be added to the playback history.
     */
    public static Future<?> addItemToPlaybackHistory(final Context context,
                                                     final FeedMedia media) {
        return dbExec.submit(new Runnable() {
            @Override
            public void run() {
                if (AppConfig.DEBUG)
                    Log.d(TAG, "Adding new item to playback history");
                media.setPlaybackCompletionDate(new Date());
                PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                adapter.setFeedMediaPlaybackCompletionDate(media);
                adapter.close();
                EventDistributor.getInstance().sendPlaybackHistoryUpdateBroadcast();

            }
        });
    }

    private static void cleanupDownloadLog(final PodDBAdapter adapter) {
        final long logSize = adapter.getDownloadLogSize();
        if (logSize > DBReader.DOWNLOAD_LOG_SIZE) {
            if (AppConfig.DEBUG)
                Log.d(TAG, "Cleaning up download log");
            adapter.removeDownloadLogItems(logSize - DBReader.DOWNLOAD_LOG_SIZE);
        }
    }

    /**
     * Adds a Download status object to the download log.
     *
     * @param context A context that is used for opening a database connection.
     * @param status  The DownloadStatus object.
     */
    public static Future<?> addDownloadStatus(final Context context,
                                              final DownloadStatus status) {
        return dbExec.submit(new Runnable() {

            @Override
            public void run() {

                PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                adapter.setDownloadStatus(status);
                adapter.close();
                EventDistributor.getInstance().sendDownloadLogUpdateBroadcast();
            }
        });

    }

    /**
     * Inserts a FeedItem in the queue at the specified index. The 'read'-attribute of the FeedItem will be set to
     * true. If the FeedItem is already in the queue, the queue will not be modified.
     *
     * @param context             A context that is used for opening a database connection.
     * @param itemId              ID of the FeedItem that should be added to the queue.
     * @param index               Destination index. Must be in range 0..queue.size()
     * @param performAutoDownload True if an auto-download process should be started after the operation
     * @throws IndexOutOfBoundsException if index < 0 || index >= queue.size()
     */
    public static Future<?> addQueueItemAt(final Context context, final long itemId,
                                           final int index, final boolean performAutoDownload) {
        return dbExec.submit(new Runnable() {

            @Override
            public void run() {
                final PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                final List<FeedItem> queue = DBReader
                        .getQueue(context, adapter);
                FeedItem item = null;

                if (queue != null) {
                    boolean queueModified = false;
                    boolean unreadItemsModified = false;

                    if (!itemListContains(queue, itemId)) {
                        item = DBReader.getFeedItem(context, itemId);
                        if (item != null) {
                            queue.add(index, item);
                            queueModified = true;
                            if (!item.isRead()) {
                                item.setRead(true);
                                unreadItemsModified = true;
                            }
                        }
                    }
                    if (queueModified) {
                        adapter.setQueue(queue);
                        EventDistributor.getInstance()
                                .sendQueueUpdateBroadcast();
                    }
                    if (unreadItemsModified && item != null) {
                        adapter.setSingleFeedItem(item);
                        EventDistributor.getInstance()
                                .sendUnreadItemsUpdateBroadcast();
                    }
                }
                adapter.close();
                if (performAutoDownload) {
                    DBTasks.autodownloadUndownloadedItems(context);
                }

            }
        });

    }

    /**
     * Appends FeedItem objects to the end of the queue. The 'read'-attribute of all items will be set to true.
     * If a FeedItem is already in the queue, the FeedItem will not change its position in the queue.
     *
     * @param context A context that is used for opening a database connection.
     * @param itemIds IDs of the FeedItem objects that should be added to the queue.
     */
    public static Future<?> addQueueItem(final Context context,
                                         final long... itemIds) {
        return dbExec.submit(new Runnable() {

            @Override
            public void run() {
                if (itemIds.length > 0) {
                    final PodDBAdapter adapter = new PodDBAdapter(context);
                    adapter.open();
                    final List<FeedItem> queue = DBReader.getQueue(context,
                            adapter);

                    if (queue != null) {
                        boolean queueModified = false;
                        boolean unreadItemsModified = false;
                        List<FeedItem> itemsToSave = new LinkedList<FeedItem>();
                        for (int i = 0; i < itemIds.length; i++) {
                            if (!itemListContains(queue, itemIds[i])) {
                                final FeedItem item = DBReader.getFeedItem(
                                        context, itemIds[i]);

                                if (item != null) {
                                    queue.add(item);
                                    queueModified = true;
                                    if (!item.isRead()) {
                                        item.setRead(true);
                                        itemsToSave.add(item);
                                        unreadItemsModified = true;
                                    }
                                }
                            }
                        }
                        if (queueModified) {
                            adapter.setQueue(queue);
                            EventDistributor.getInstance()
                                    .sendQueueUpdateBroadcast();
                        }
                        if (unreadItemsModified) {
                            adapter.setFeedItemlist(itemsToSave);
                            EventDistributor.getInstance()
                                    .sendUnreadItemsUpdateBroadcast();
                        }
                    }
                    adapter.close();
                    DBTasks.autodownloadUndownloadedItems(context);
                }
            }
        });

    }

    /**
     * Removes all FeedItem objects from the queue.
     *
     * @param context A context that is used for opening a database connection.
     */
    public static Future<?> clearQueue(final Context context) {
        return dbExec.submit(new Runnable() {

            @Override
            public void run() {
                PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                adapter.clearQueue();
                adapter.close();

                EventDistributor.getInstance().sendQueueUpdateBroadcast();
            }
        });
    }

    /**
     * Removes a FeedItem object from the queue.
     *
     * @param context             A context that is used for opening a database connection.
     * @param itemId              ID of the FeedItem that should be removed.
     * @param performAutoDownload true if an auto-download process should be started after the operation.
     */
    public static Future<?> removeQueueItem(final Context context,
                                            final long itemId, final boolean performAutoDownload) {
        return dbExec.submit(new Runnable() {

            @Override
            public void run() {
                final PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                final List<FeedItem> queue = DBReader
                        .getQueue(context, adapter);
                FeedItem item = null;

                if (queue != null) {
                    boolean queueModified = false;
                    QueueAccess queueAccess = QueueAccess.ItemListAccess(queue);
                    if (queueAccess.contains(itemId)) {
                        item = DBReader.getFeedItem(context, itemId);
                        if (item != null) {
                            queueModified = queueAccess.remove(itemId);
                        }
                    }
                    if (queueModified) {
                        adapter.setQueue(queue);
                        EventDistributor.getInstance()
                                .sendQueueUpdateBroadcast();
                    } else {
                        Log.w(TAG, "Queue was not modified by call to removeQueueItem");
                    }
                } else {
                    Log.e(TAG, "removeQueueItem: Could not load queue");
                }
                adapter.close();
                if (performAutoDownload) {
                    DBTasks.autodownloadUndownloadedItems(context);
                }
            }
        });

    }
    
    /**
     * Moves the specified item to the top of the queue.
     *
     * @param context         A context that is used for opening a database connection.
     * @param itemId          The item to move to the top of the queue
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     *                        false if the caller wants to avoid unexpected updates of the GUI.
     */
    public static Future<?> moveQueueItemToTop(final Context context, final long itemId, final boolean broadcastUpdate) {
        return dbExec.submit(new Runnable() {
            @Override
            public void run() {
                List<Long> queueIdList = DBReader.getQueueIDList(context);
                int currentLocation = 0;
                for (long id : queueIdList) {
                    if (id == itemId) {
                        moveQueueItemHelper(context, currentLocation, 0, broadcastUpdate);
                        return;
                    }
                    currentLocation++;
                }
                Log.e(TAG, "moveQueueItemToTop: item not found");
            }
        });
    }
    
    /**
     * Moves the specified item to the bottom of the queue.
     *
     * @param context         A context that is used for opening a database connection.
     * @param itemId          The item to move to the bottom of the queue
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     *                        false if the caller wants to avoid unexpected updates of the GUI.
     */
    public static Future<?> moveQueueItemToBottom(final Context context, final long itemId,
                                                  final boolean broadcastUpdate) {
        return dbExec.submit(new Runnable() {
            @Override
            public void run() {
                List<Long> queueIdList = DBReader.getQueueIDList(context);
                int currentLocation = 0;
                for (long id : queueIdList) {
                    if (id == itemId) {
                        moveQueueItemHelper(context, currentLocation, queueIdList.size() - 1,
                                            broadcastUpdate);
                        return;
                    }
                    currentLocation++;
                }
                Log.e(TAG, "moveQueueItemToBottom: item not found");
            }
        });
    }
    
    /**
     * Changes the position of a FeedItem in the queue.
     *
     * @param context         A context that is used for opening a database connection.
     * @param from            Source index. Must be in range 0..queue.size()-1.
     * @param to              Destination index. Must be in range 0..queue.size()-1.
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     *                        false if the caller wants to avoid unexpected updates of the GUI.
     * @throws IndexOutOfBoundsException if (to < 0 || to >= queue.size()) || (from < 0 || from >= queue.size())
     */
    public static Future<?> moveQueueItem(final Context context, final int from,
                                          final int to, final boolean broadcastUpdate) {
        return dbExec.submit(new Runnable() {

            @Override
            public void run() {
                moveQueueItemHelper(context, from, to, broadcastUpdate);
            }
        });
    }

    /**
     * Changes the position of a FeedItem in the queue.
     *
     * This function must be run using the ExecutorService (dbExec).
     *
     * @param context         A context that is used for opening a database connection.
     * @param from            Source index. Must be in range 0..queue.size()-1.
     * @param to              Destination index. Must be in range 0..queue.size()-1.
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     *                        false if the caller wants to avoid unexpected updates of the GUI.
     * @throws IndexOutOfBoundsException if (to < 0 || to >= queue.size()) || (from < 0 || from >= queue.size())
     */
    private static void moveQueueItemHelper(final Context context, final int from,
                                       final int to, final boolean broadcastUpdate) {
        final PodDBAdapter adapter = new PodDBAdapter(context);
        adapter.open();
        final List<FeedItem> queue = DBReader
                .getQueue(context, adapter);

        if (queue != null) {
            if (from >= 0 && from < queue.size() && to >= 0
                    && to < queue.size()) {

                final FeedItem item = queue.remove(from);
                queue.add(to, item);

                adapter.setQueue(queue);
                if (broadcastUpdate) {
                    EventDistributor.getInstance()
                            .sendQueueUpdateBroadcast();
                }

            }
        } else {
            Log.e(TAG, "moveQueueItemHelper: Could not load queue");
        }
        adapter.close();
    }

    /**
     * Sets the 'read'-attribute of a FeedItem to the specified value.
     *
     * @param context            A context that is used for opening a database connection.
     * @param item               The FeedItem object
     * @param read               New value of the 'read'-attribute
     * @param resetMediaPosition true if this method should also reset the position of the FeedItem's FeedMedia object.
     *                           If the FeedItem has no FeedMedia object, this parameter will be ignored.
     */
    public static Future<?> markItemRead(Context context, FeedItem item, boolean read, boolean resetMediaPosition) {
        long mediaId = (item.hasMedia()) ? item.getMedia().getId() : 0;
        return markItemRead(context, item.getId(), read, mediaId, resetMediaPosition);
    }

    /**
     * Sets the 'read'-attribute of a FeedItem to the specified value.
     *
     * @param context A context that is used for opening a database connection.
     * @param itemId  ID of the FeedItem
     * @param read    New value of the 'read'-attribute
     */
    public static Future<?> markItemRead(final Context context, final long itemId,
                                         final boolean read) {
        return markItemRead(context, itemId, read, 0, false);
    }

    private static Future<?> markItemRead(final Context context, final long itemId,
                                          final boolean read, final long mediaId,
                                          final boolean resetMediaPosition) {
        return dbExec.submit(new Runnable() {

            @Override
            public void run() {
                final PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                adapter.setFeedItemRead(read, itemId, mediaId,
                        resetMediaPosition);
                adapter.close();

                EventDistributor.getInstance().sendUnreadItemsUpdateBroadcast();
            }
        });
    }

    /**
     * Sets the 'read'-attribute of all FeedItems of a specific Feed to true.
     *
     * @param context A context that is used for opening a database connection.
     * @param feedId  ID of the Feed.
     */
    public static Future<?> markFeedRead(final Context context, final long feedId) {
        return dbExec.submit(new Runnable() {

            @Override
            public void run() {
                final PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                Cursor itemCursor = adapter.getAllItemsOfFeedCursor(feedId);
                long[] itemIds = new long[itemCursor.getCount()];
                itemCursor.moveToFirst();
                for (int i = 0; i < itemIds.length; i++) {
                    itemIds[i] = itemCursor.getLong(PodDBAdapter.KEY_ID_INDEX);
                    itemCursor.moveToNext();
                }
                itemCursor.close();
                adapter.setFeedItemRead(true, itemIds);
                adapter.close();

                EventDistributor.getInstance().sendUnreadItemsUpdateBroadcast();
            }
        });

    }

    /**
     * Sets the 'read'-attribute of all FeedItems to true.
     *
     * @param context A context that is used for opening a database connection.
     */
    public static Future<?> markAllItemsRead(final Context context) {
        return dbExec.submit(new Runnable() {

            @Override
            public void run() {
                final PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                Cursor itemCursor = adapter.getUnreadItemsCursor();
                long[] itemIds = new long[itemCursor.getCount()];
                itemCursor.moveToFirst();
                for (int i = 0; i < itemIds.length; i++) {
                    itemIds[i] = itemCursor.getLong(PodDBAdapter.KEY_ID_INDEX);
                    itemCursor.moveToNext();
                }
                itemCursor.close();
                adapter.setFeedItemRead(true, itemIds);
                adapter.close();

                EventDistributor.getInstance().sendUnreadItemsUpdateBroadcast();
            }
        });

    }

    static Future<?> addNewFeed(final Context context, final Feed feed) {
        return dbExec.submit(new Runnable() {

            @Override
            public void run() {
                final PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                adapter.setCompleteFeed(feed);
                adapter.close();

                GpodnetPreferences.addAddedFeed(feed.getDownload_url());
                EventDistributor.getInstance().sendFeedUpdateBroadcast();
            }
        });
    }

    static Future<?> setCompleteFeed(final Context context, final Feed feed) {
        return dbExec.submit(new Runnable() {

            @Override
            public void run() {
                PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                adapter.setCompleteFeed(feed);
                adapter.close();

                EventDistributor.getInstance().sendFeedUpdateBroadcast();
            }
        });

    }

    /**
     * Saves a FeedMedia object in the database. This method will save all attributes of the FeedMedia object. The
     * contents of FeedComponent-attributes (e.g. the FeedMedia's 'item'-attribute) will not be saved.
     *
     * @param context A context that is used for opening a database connection.
     * @param media   The FeedMedia object.
     */
    public static Future<?> setFeedMedia(final Context context,
                                         final FeedMedia media) {
        return dbExec.submit(new Runnable() {

            @Override
            public void run() {
                PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                adapter.setMedia(media);
                adapter.close();
            }
        });
    }

    /**
     * Saves the 'position' and 'duration' attributes of a FeedMedia object
     *
     * @param context A context that is used for opening a database connection.
     * @param media   The FeedMedia object.
     */
    public static Future<?> setFeedMediaPlaybackInformation(final Context context, final FeedMedia media) {
        return dbExec.submit(new Runnable() {
            @Override
            public void run() {
                PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                adapter.setFeedMediaPlaybackInformation(media);
                adapter.close();
            }
        });
    }

    /**
     * Saves a FeedItem object in the database. This method will save all attributes of the FeedItem object including
     * the content of FeedComponent-attributes.
     *
     * @param context A context that is used for opening a database connection.
     * @param item    The FeedItem object.
     */
    public static Future<?> setFeedItem(final Context context,
                                        final FeedItem item) {
        return dbExec.submit(new Runnable() {

            @Override
            public void run() {
                PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                adapter.setSingleFeedItem(item);
                adapter.close();
            }
        });
    }

    /**
     * Saves a FeedImage object in the database. This method will save all attributes of the FeedImage object. The
     * contents of FeedComponent-attributes (e.g. the FeedImages's 'feed'-attribute) will not be saved.
     *
     * @param context A context that is used for opening a database connection.
     * @param image   The FeedImage object.
     */
    public static Future<?> setFeedImage(final Context context,
                                         final FeedImage image) {
        return dbExec.submit(new Runnable() {

            @Override
            public void run() {
                PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                adapter.setImage(image);
                adapter.close();
            }
        });
    }

    /**
     * Updates download URLs of feeds from a given Map. The key of the Map is the original URL of the feed
     * and the value is the updated URL
     */
    public static Future<?> updateFeedDownloadURLs(final Context context, final Map<String, String> urls) {
        return dbExec.submit(new Runnable() {
            @Override
            public void run() {
                PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                for (String key : urls.keySet()) {
                    if (AppConfig.DEBUG) Log.d(TAG, "Replacing URL " + key + " with url " + urls.get(key));

                    adapter.setFeedDownloadUrl(key, urls.get(key));
                }
                adapter.close();
            }
        });
    }

    /**
     * Saves a FeedPreferences object in the database. The Feed ID of the FeedPreferences-object MUST NOT be 0.
     *
     * @param context     Used for opening a database connection.
     * @param preferences The FeedPreferences object.
     */
    public static Future<?> setFeedPreferences(final Context context, final FeedPreferences preferences) {
        return dbExec.submit(new Runnable() {
            @Override
            public void run() {
                PodDBAdapter adapter = new PodDBAdapter(context);
                adapter.open();
                adapter.setFeedPreferences(preferences);
                adapter.close();
            }
        });
    }

    private static boolean itemListContains(List<FeedItem> items, long itemId) {
        for (FeedItem item : items) {
            if (item.getId() == itemId) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeURI(String uri) {
        String normalizedURI = null;
        if (uri != null) {
            try {
                normalizedURI = (new URI(uri)).normalize().toString();
                if (! normalizedURI.endsWith("/"))
                    normalizedURI = normalizedURI + "/";
            }
            catch (URISyntaxException e) {
            }
        }
        return normalizedURI;
    }


    // Set flattr status of the passed thing (either a FeedItem or a Feed)
    public static void setFlattredStatus(Context context, FlattrThing thing) {
        Log.d(TAG, "setFlattredStatus of " + thing.getTitle());
        // must propagate this to back db
        if (thing instanceof FeedItem)
                DBWriter.setFeedItem(context, (FeedItem) thing);
        else if (thing instanceof Feed)
                DBWriter.setCompleteFeed(context, (Feed) thing);
        else
                Log.e(TAG, "flattrQueue processing - thing is neither FeedItem nor Feed");
    }

    /*
     * Set flattr status of the feeds/feeditems in flattrList to flattred at the given timestamp,
     * where the information has been retrieved from the flattr API
     */
    public static void setFlattredStatus(Context context, List<Flattr> flattrList) {
        class FlattrLinkTime {
            public String paymentLink;
            public long time;

            FlattrLinkTime(String paymentLink, long time) {
                this.paymentLink = paymentLink;
                this.time = time;
            }
        }

        // build list with flattred things having normalized URLs
        ArrayList<FlattrLinkTime> flattrLinkTime = new ArrayList<FlattrLinkTime>(flattrList.size());
        for (Flattr flattr: flattrList) {
            flattrLinkTime.add(new FlattrLinkTime(normalizeURI(flattr.getThing().getUrl()), flattr.getCreated().getTime()));
            if (AppConfig.DEBUG)
                Log.d(TAG, "FlattredUrl: " + flattr.getThing().getUrl());
        }


        String paymentLink;
        List<Feed> feeds = DBReader.getFeedList(context);
        for (Feed feed: feeds) {
            // check if the feed has been flattred
            paymentLink = feed.getPaymentLink();
            if (paymentLink != null) {
                String feedThingUrl = normalizeURI(Uri.parse(paymentLink).getQueryParameter("url"));

                feed.getFlattrStatus().setUnflattred(); // reset our offline status tracking

                if (AppConfig.DEBUG)
                    Log.d(TAG, "Feed: Trying to match " + feedThingUrl);
                for (FlattrLinkTime flattr: flattrLinkTime) {
                    if (flattr.paymentLink.equals(feedThingUrl)) {
                        feed.setFlattrStatus(new FlattrStatus(flattr.time));
                        break;
                    }
                }

                setCompleteFeed(context, feed); // writeback to db, as we do this in any case, items that are locally marked as flattred but not returned by the api become flattrable again
            }

            // check if any of the feeditems have been flattred
            for (FeedItem item: DBReader.getFeedItemList(context, feed)) {
                paymentLink = item.getPaymentLink();

                if (paymentLink != null) {
                    String feedItemThingUrl = normalizeURI(Uri.parse(paymentLink).getQueryParameter("url"));

                    item.getFlattrStatus().setUnflattred(); // reset our offline status tracking

                    if (AppConfig.DEBUG)
                        Log.d(TAG, "FeedItem: Trying to match " + feedItemThingUrl);
                    for (FlattrLinkTime flattr: flattrLinkTime) {
                        if (flattr.paymentLink.equals(feedItemThingUrl)) {
                            item.setFlattrStatus(new FlattrStatus(flattr.time));
                            break;
                        }
                    }

                    setFeedItem(context, item);  // writeback to db, as we do this in any case, items that are locally marked as flattred but not returned by the api become flattrable again
                }
            }
        }
    }

}
