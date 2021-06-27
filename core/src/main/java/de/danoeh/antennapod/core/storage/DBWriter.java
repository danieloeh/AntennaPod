package de.danoeh.antennapod.core.storage;

import android.app.backup.BackupManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.core.R;
import de.danoeh.antennapod.core.event.DownloadLogEvent;
import de.danoeh.antennapod.core.event.FavoritesEvent;
import de.danoeh.antennapod.core.event.FeedItemEvent;
import de.danoeh.antennapod.core.event.FeedListUpdateEvent;
import de.danoeh.antennapod.core.event.MessageEvent;
import de.danoeh.antennapod.core.event.PlaybackHistoryEvent;
import de.danoeh.antennapod.core.event.QueueEvent;
import de.danoeh.antennapod.core.event.UnreadItemsUpdateEvent;
import de.danoeh.antennapod.core.feed.FeedEvent;
import de.danoeh.antennapod.core.preferences.GpodnetPreferences;
import de.danoeh.antennapod.core.preferences.PlaybackPreferences;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.download.DownloadStatus;
import de.danoeh.antennapod.core.service.playback.PlaybackService;
import de.danoeh.antennapod.core.sync.SyncService;
import de.danoeh.antennapod.core.util.FeedItemPermutors;
import de.danoeh.antennapod.core.util.IntentUtils;
import de.danoeh.antennapod.core.util.LongList;
import de.danoeh.antennapod.core.util.Permutor;
import de.danoeh.antennapod.core.util.playback.PlayableUtils;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.net.sync.model.EpisodeAction;

/**
 * Provides methods for writing data to AntennaPod's database.
 * In general, DBWriter-methods will be executed on an internal ExecutorService.
 * Some methods return a Future-object which the caller can use for waiting for the method's completion. The returned Future's
 * will NOT contain any results.
 */
public class DBWriter {

    private static final String TAG = "DBWriter";

    private static final ExecutorService dbExec;

    static {
        dbExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("DatabaseExecutor");
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    private DBWriter() {
    }

    /**
     * Wait until all threads are finished to avoid the "Illegal connection pointer" error of
     * Robolectric. Call this method only for unit tests.
     */
    public static void tearDownTests() {
        try {
            dbExec.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // ignore error
        }
    }

    /**
     * Deletes a downloaded FeedMedia file from the storage device.
     *
     * @param context A context that is used for opening a database connection.
     * @param mediaId ID of the FeedMedia object whose downloaded file should be deleted.
     */
    public static Future<?> deleteFeedMediaOfItem(@NonNull final Context context,
                                                  final long mediaId) {
        return dbExec.submit(() -> {
            final FeedMedia media = DBReader.getFeedMedia(mediaId);
            if (media != null) {
                boolean result = deleteFeedMediaSynchronous(context, media);

                if (result && UserPreferences.shouldDeleteRemoveFromQueue()) {
                    DBWriter.removeQueueItemSynchronous(context, false, media.getItem().getId());
                }
            }
        });
    }

    private static boolean deleteFeedMediaSynchronous(
            @NonNull Context context, @NonNull FeedMedia media) {
        Log.i(TAG, String.format(Locale.US, "Requested to delete FeedMedia [id=%d, title=%s, downloaded=%s",
                media.getId(), media.getEpisodeTitle(), media.isDownloaded()));
        if (media.isDownloaded()) {
            // delete downloaded media file
            File mediaFile = new File(media.getFile_url());
            if (mediaFile.exists() && !mediaFile.delete()) {
                MessageEvent evt = new MessageEvent(context.getString(R.string.delete_failed));
                EventBus.getDefault().post(evt);
                return false;
            }
            media.setDownloaded(false);
            media.setFile_url(null);
            media.setHasEmbeddedPicture(false);
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setMedia(media);
            adapter.close();

            if (media.getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId()) {
                PlaybackPreferences.writeNoMediaPlaying();
                IntentUtils.sendLocalBroadcast(context, PlaybackService.ACTION_SHUTDOWN_PLAYBACK_SERVICE);
            }

            // Gpodder: queue delete action for synchronization
            if (GpodnetPreferences.loggedIn()) {
                FeedItem item = media.getItem();
                EpisodeAction action = new EpisodeAction.Builder(item, EpisodeAction.DELETE)
                        .currentTimestamp()
                        .build();
                SyncService.enqueueEpisodeAction(context, action);
            }
        }
        EventBus.getDefault().post(FeedItemEvent.deletedMedia(Collections.singletonList(media.getItem())));
        return true;
    }

    /**
     * Deletes a Feed and all downloaded files of its components like images and downloaded episodes.
     *
     * @param context A context that is used for opening a database connection.
     * @param feedId  ID of the Feed that should be deleted.
     */
    public static Future<?> deleteFeed(final Context context, final long feedId) {
        return dbExec.submit(() -> {
            DownloadRequester requester = DownloadRequester.getInstance();
            final Feed feed = DBReader.getFeed(feedId);
            if (feed == null) {
                return;
            }

            // delete stored media files and mark them as read
            if (feed.getItems() == null) {
                DBReader.getFeedItemList(feed);
            }
            deleteFeedItemsSynchronous(context, feed.getItems());

            // delete feed
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.removeFeed(feed);
            adapter.close();

            SyncService.enqueueFeedRemoved(context, feed.getDownload_url());
            EventBus.getDefault().post(new FeedListUpdateEvent(feed));
        });
    }

    /**
     * Remove the listed items and their FeedMedia entries.
     * Deleting media also removes the download log entries.
     */
    @NonNull
    public static Future<?> deleteFeedItems(@NonNull Context context, @NonNull List<FeedItem> items) {
        return dbExec.submit(() -> deleteFeedItemsSynchronous(context, items));
    }

    /**
     * Remove the listed items and their FeedMedia entries.
     * Deleting media also removes the download log entries.
     */
    private static void deleteFeedItemsSynchronous(@NonNull Context context, @NonNull List<FeedItem> items) {
        DownloadRequester requester = DownloadRequester.getInstance();
        List<FeedItem> queue = DBReader.getQueue();
        List<FeedItem> removedFromQueue = new ArrayList<>();
        for (FeedItem item : items) {
            if (queue.remove(item)) {
                removedFromQueue.add(item);
            }
            if (item.getMedia() != null) {
                if (item.getMedia().getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId()) {
                    // Applies to both downloaded and streamed media
                    PlaybackPreferences.writeNoMediaPlaying();
                    IntentUtils.sendLocalBroadcast(context, PlaybackService.ACTION_SHUTDOWN_PLAYBACK_SERVICE);
                }
                if (item.getMedia().isDownloaded()) {
                    deleteFeedMediaSynchronous(context, item.getMedia());
                } else if (requester.isDownloadingFile(item.getMedia())) {
                    requester.cancelDownload(context, item.getMedia());
                }
            }
        }

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        if (!removedFromQueue.isEmpty()) {
            adapter.setQueue(queue);
        }
        adapter.removeFeedItems(items);
        adapter.close();

        for (FeedItem item : removedFromQueue) {
            EventBus.getDefault().post(QueueEvent.irreversibleRemoved(item));
        }

        // we assume we also removed download log entries for the feed or its media files.
        // especially important if download or refresh failed, as the user should not be able
        // to retry these
        EventBus.getDefault().post(DownloadLogEvent.listUpdated());

        BackupManager backupManager = new BackupManager(context);
        backupManager.dataChanged();
    }

    /**
     * Deletes the entire playback history.
     */
    public static Future<?> clearPlaybackHistory() {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.clearPlaybackHistory();
            adapter.close();
            EventBus.getDefault().post(PlaybackHistoryEvent.listUpdated());
        });
    }

    /**
     * Deletes the entire download log.
     */
    public static Future<?> clearDownloadLog() {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.clearDownloadLog();
            adapter.close();
            EventBus.getDefault().post(DownloadLogEvent.listUpdated());
        });
    }


    /**
     * Adds a FeedMedia object to the playback history. A FeedMedia object is in the playback history if
     * its playback completion date is set to a non-null value. This method will set the playback completion date to the
     * current date regardless of the current value.
     *
     * @param media FeedMedia that should be added to the playback history.
     */
    public static Future<?> addItemToPlaybackHistory(final FeedMedia media) {
        return dbExec.submit(() -> {
            Log.d(TAG, "Adding new item to playback history");
            media.setPlaybackCompletionDate(new Date());

            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedMediaPlaybackCompletionDate(media);
            adapter.close();
            EventBus.getDefault().post(PlaybackHistoryEvent.listUpdated());

        });
    }

    /**
     * Adds a Download status object to the download log.
     *
     * @param status The DownloadStatus object.
     */
    public static Future<?> addDownloadStatus(final DownloadStatus status) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setDownloadStatus(status);
            adapter.close();
            EventBus.getDefault().post(DownloadLogEvent.listUpdated());
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
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            final List<FeedItem> queue = DBReader.getQueue(adapter);
            FeedItem item;

            if (queue != null) {
                if (!itemListContains(queue, itemId)) {
                    item = DBReader.getFeedItem(itemId);
                    if (item != null) {
                        queue.add(index, item);
                        adapter.setQueue(queue);
                        item.addTag(FeedItem.TAG_QUEUE);
                        EventBus.getDefault().post(QueueEvent.added(item, index));
                        EventBus.getDefault().post(FeedItemEvent.updated(item));
                        if (item.isNew()) {
                            DBWriter.markItemPlayed(FeedItem.UNPLAYED, item.getId());
                        }
                    }
                }
            }

            adapter.close();
            if (performAutoDownload) {
                DBTasks.autodownloadUndownloadedItems(context);
            }

        });

    }

    public static Future<?> addQueueItem(final Context context, final FeedItem... items) {
        return addQueueItem(context, true, items);
    }

    public static Future<?> addQueueItem(final Context context, boolean markAsUnplayed, final FeedItem... items) {
        LongList itemIds = new LongList(items.length);
        for (FeedItem item : items) {
            itemIds.add(item.getId());
            item.addTag(FeedItem.TAG_QUEUE);
        }
        return addQueueItem(context, false, markAsUnplayed, itemIds.toArray());
    }

    /**
     * Appends FeedItem objects to the end of the queue. The 'read'-attribute of all items will be set to true.
     * If a FeedItem is already in the queue, the FeedItem will not change its position in the queue.
     *
     * @param context             A context that is used for opening a database connection.
     * @param performAutoDownload true if an auto-download process should be started after the operation.
     * @param itemIds             IDs of the FeedItem objects that should be added to the queue.
     */
    public static Future<?> addQueueItem(final Context context, final boolean performAutoDownload,
                                         final long... itemIds) {
        return addQueueItem(context, performAutoDownload, true, itemIds);
    }

    /**
     * Appends FeedItem objects to the end of the queue. The 'read'-attribute of all items will be set to true.
     * If a FeedItem is already in the queue, the FeedItem will not change its position in the queue.
     *
     * @param context             A context that is used for opening a database connection.
     * @param performAutoDownload true if an auto-download process should be started after the operation.
     * @param markAsUnplayed      true if the items should be marked as unplayed when enqueueing
     * @param itemIds             IDs of the FeedItem objects that should be added to the queue.
     */
    public static Future<?> addQueueItem(final Context context, final boolean performAutoDownload,
                                         final boolean markAsUnplayed, final long... itemIds) {
        return dbExec.submit(() -> {
            if (itemIds.length < 1) {
                return;
            }

            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            final List<FeedItem> queue = DBReader.getQueue(adapter);

            boolean queueModified = false;
            LongList markAsUnplayedIds = new LongList();
            List<QueueEvent> events = new ArrayList<>();
            List<FeedItem> updatedItems = new ArrayList<>();
            ItemEnqueuePositionCalculator positionCalculator =
                    new ItemEnqueuePositionCalculator(UserPreferences.getEnqueueLocation());
            Playable currentlyPlaying = PlayableUtils.createInstanceFromPreferences(context);
            int insertPosition = positionCalculator.calcPosition(queue, currentlyPlaying);
            for (long itemId : itemIds) {
                if (!itemListContains(queue, itemId)) {
                    final FeedItem item = DBReader.getFeedItem(itemId);
                    if (item != null) {
                        queue.add(insertPosition, item);
                        events.add(QueueEvent.added(item, insertPosition));

                        item.addTag(FeedItem.TAG_QUEUE);
                        updatedItems.add(item);
                        queueModified = true;
                        if (item.isNew()) {
                            markAsUnplayedIds.add(item.getId());
                        }
                        insertPosition++;
                    }
                }
            }
            if (queueModified) {
                applySortOrder(queue, events);
                adapter.setQueue(queue);
                for (QueueEvent event : events) {
                    EventBus.getDefault().post(event);
                }
                EventBus.getDefault().post(FeedItemEvent.updated(updatedItems));
                if (markAsUnplayed && markAsUnplayedIds.size() > 0) {
                    DBWriter.markItemPlayed(FeedItem.UNPLAYED, markAsUnplayedIds.toArray());
                }
            }
            adapter.close();
            if (performAutoDownload) {
                DBTasks.autodownloadUndownloadedItems(context);
            }
        });
    }

    /**
     * Sorts the queue depending on the configured sort order.
     * If the queue is not in keep sorted mode, nothing happens.
     *
     * @param queue  The queue to be sorted.
     * @param events Replaces the events by a single SORT event if the list has to be sorted automatically.
     */
    private static void applySortOrder(List<FeedItem> queue, List<QueueEvent> events) {
        if (!UserPreferences.isQueueKeepSorted()) {
            // queue is not in keep sorted mode, there's nothing to do
            return;
        }

        // Sort queue by configured sort order
        SortOrder sortOrder = UserPreferences.getQueueKeepSortedOrder();
        if (sortOrder == SortOrder.RANDOM) {
            // do not shuffle the list on every change
            return;
        }
        Permutor<FeedItem> permutor = FeedItemPermutors.getPermutor(sortOrder);
        permutor.reorder(queue);

        // Replace ADDED events by a single SORTED event
        events.clear();
        events.add(QueueEvent.sorted(queue));
    }

    /**
     * Removes all FeedItem objects from the queue.
     */
    public static Future<?> clearQueue() {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.clearQueue();
            adapter.close();

            EventBus.getDefault().post(QueueEvent.cleared());
        });
    }

    /**
     * Removes a FeedItem object from the queue.
     *
     * @param context             A context that is used for opening a database connection.
     * @param performAutoDownload true if an auto-download process should be started after the operation.
     * @param item                FeedItem that should be removed.
     */
    public static Future<?> removeQueueItem(final Context context,
                                            final boolean performAutoDownload, final FeedItem item) {
        return dbExec.submit(() -> removeQueueItemSynchronous(context, performAutoDownload, item.getId()));
    }

    public static Future<?> removeQueueItem(final Context context, final boolean performAutoDownload,
                                            final long... itemIds) {
        return dbExec.submit(() -> removeQueueItemSynchronous(context, performAutoDownload, itemIds));
    }

    private static void removeQueueItemSynchronous(final Context context,
                                                   final boolean performAutoDownload,
                                                   final long... itemIds) {
        if (itemIds.length < 1) {
            return;
        }
        final PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        final List<FeedItem> queue = DBReader.getQueue(adapter);

        if (queue != null) {
            boolean queueModified = false;
            List<QueueEvent> events = new ArrayList<>();
            List<FeedItem> updatedItems = new ArrayList<>();
            for (long itemId : itemIds) {
                int position = indexInItemList(queue, itemId);
                if (position >= 0) {
                    final FeedItem item = DBReader.getFeedItem(itemId);
                    if (item == null) {
                        Log.e(TAG, "removeQueueItem - item in queue but somehow cannot be loaded." +
                                " Item ignored. It should never happen. id:" + itemId);
                        continue;
                    }
                    queue.remove(position);
                    item.removeTag(FeedItem.TAG_QUEUE);
                    events.add(QueueEvent.removed(item));
                    updatedItems.add(item);
                    queueModified = true;
                } else {
                    Log.v(TAG, "removeQueueItem - item  not in queue:" + itemId);
                }
            }
            if (queueModified) {
                adapter.setQueue(queue);
                for (QueueEvent event : events) {
                    EventBus.getDefault().post(event);
                }
                EventBus.getDefault().post(FeedItemEvent.updated(updatedItems));
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

    public static Future<?> toggleFavoriteItem(final FeedItem item) {
        if (item.isTagged(FeedItem.TAG_FAVORITE)) {
            return removeFavoriteItem(item);
        } else {
            return addFavoriteItem(item);
        }
    }

    public static Future<?> addFavoriteItem(final FeedItem item) {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance().open();
            adapter.addFavoriteItem(item);
            adapter.close();
            item.addTag(FeedItem.TAG_FAVORITE);
            EventBus.getDefault().post(FavoritesEvent.added(item));
            EventBus.getDefault().post(FeedItemEvent.updated(item));
        });
    }

    public static Future<?> removeFavoriteItem(final FeedItem item) {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance().open();
            adapter.removeFavoriteItem(item);
            adapter.close();
            item.removeTag(FeedItem.TAG_FAVORITE);
            EventBus.getDefault().post(FavoritesEvent.removed(item));
            EventBus.getDefault().post(FeedItemEvent.updated(item));
        });
    }

    /**
     * Moves the specified item to the top of the queue.
     *
     * @param itemId          The item to move to the top of the queue
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     */
    public static Future<?> moveQueueItemToTop(final long itemId, final boolean broadcastUpdate) {
        return dbExec.submit(() -> {
            LongList queueIdList = DBReader.getQueueIDList();
            int index = queueIdList.indexOf(itemId);
            if (index >= 0) {
                moveQueueItemHelper(index, 0, broadcastUpdate);
            } else {
                Log.e(TAG, "moveQueueItemToTop: item not found");
            }
        });
    }

    /**
     * Moves the specified item to the bottom of the queue.
     *
     * @param itemId          The item to move to the bottom of the queue
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     */
    public static Future<?> moveQueueItemToBottom(final long itemId,
                                                  final boolean broadcastUpdate) {
        return dbExec.submit(() -> {
            LongList queueIdList = DBReader.getQueueIDList();
            int index = queueIdList.indexOf(itemId);
            if (index >= 0) {
                moveQueueItemHelper(index, queueIdList.size() - 1,
                        broadcastUpdate);
            } else {
                Log.e(TAG, "moveQueueItemToBottom: item not found");
            }
        });
    }

    /**
     * Changes the position of a FeedItem in the queue.
     *
     * @param from            Source index. Must be in range 0..queue.size()-1.
     * @param to              Destination index. Must be in range 0..queue.size()-1.
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     *                        false if the caller wants to avoid unexpected updates of the GUI.
     * @throws IndexOutOfBoundsException if (to < 0 || to >= queue.size()) || (from < 0 || from >= queue.size())
     */
    public static Future<?> moveQueueItem(final int from,
                                          final int to, final boolean broadcastUpdate) {
        return dbExec.submit(() -> moveQueueItemHelper(from, to, broadcastUpdate));
    }

    /**
     * Changes the position of a FeedItem in the queue.
     * <p/>
     * This function must be run using the ExecutorService (dbExec).
     *
     * @param from            Source index. Must be in range 0..queue.size()-1.
     * @param to              Destination index. Must be in range 0..queue.size()-1.
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     *                        false if the caller wants to avoid unexpected updates of the GUI.
     * @throws IndexOutOfBoundsException if (to < 0 || to >= queue.size()) || (from < 0 || from >= queue.size())
     */
    private static void moveQueueItemHelper(final int from,
                                            final int to, final boolean broadcastUpdate) {
        final PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        final List<FeedItem> queue = DBReader.getQueue(adapter);

        if (queue != null) {
            if (from >= 0 && from < queue.size() && to >= 0 && to < queue.size()) {
                final FeedItem item = queue.remove(from);
                queue.add(to, item);

                adapter.setQueue(queue);
                if (broadcastUpdate) {
                    EventBus.getDefault().post(QueueEvent.moved(item, to));
                }
            }
        } else {
            Log.e(TAG, "moveQueueItemHelper: Could not load queue");
        }
        adapter.close();
    }

    /*
     * Sets the 'read'-attribute of all specified FeedItems
     *
     * @param played  New value of the 'read'-attribute, one of FeedItem.PLAYED, FeedItem.NEW,
     *                FeedItem.UNPLAYED
     * @param itemIds IDs of the FeedItems.
     */
    public static Future<?> markItemPlayed(final int played, final long... itemIds) {
        return markItemPlayed(played, true, itemIds);
    }

    /*
     * Sets the 'read'-attribute of all specified FeedItems
     *
     * @param played  New value of the 'read'-attribute, one of FeedItem.PLAYED, FeedItem.NEW,
     *                FeedItem.UNPLAYED
     * @param broadcastUpdate true if this operation should trigger a UnreadItemsUpdate broadcast.
     *        This option is usually set to true
     * @param itemIds IDs of the FeedItems.
     */
    public static Future<?> markItemPlayed(final int played, final boolean broadcastUpdate,
                                           final long... itemIds) {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItemRead(played, itemIds);
            adapter.close();
            if (broadcastUpdate) {
                EventBus.getDefault().post(new UnreadItemsUpdateEvent());
            }
        });
    }


    /**
     * Sets the 'read'-attribute of a FeedItem to the specified value.
     *
     * @param item               The FeedItem object
     * @param played             New value of the 'read'-attribute one of FeedItem.PLAYED,
     *                           FeedItem.NEW, FeedItem.UNPLAYED
     * @param resetMediaPosition true if this method should also reset the position of the FeedItem's FeedMedia object.
     */
    @NonNull
    public static Future<?> markItemPlayed(FeedItem item, int played, boolean resetMediaPosition) {
        long mediaId = (item.hasMedia()) ? item.getMedia().getId() : 0;
        return markItemPlayed(item.getId(), played, mediaId, resetMediaPosition);
    }

    @NonNull
    private static Future<?> markItemPlayed(final long itemId,
                                            final int played,
                                            final long mediaId,
                                            final boolean resetMediaPosition) {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItemRead(played, itemId, mediaId,
                    resetMediaPosition);
            adapter.close();

            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }

    /**
     * Sets the 'read'-attribute of all NEW FeedItems of a specific Feed to UNPLAYED.
     *
     * @param feedId ID of the Feed.
     */
    public static Future<?> removeFeedNewFlag(final long feedId) {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItems(FeedItem.NEW, FeedItem.UNPLAYED, feedId);
            adapter.close();

            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }

    /**
     * Sets the 'read'-attribute of all FeedItems of a specific Feed to PLAYED.
     *
     * @param feedId ID of the Feed.
     */
    public static Future<?> markFeedRead(final long feedId) {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItems(FeedItem.PLAYED, feedId);
            adapter.close();

            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }

    /**
     * Sets the 'read'-attribute of all FeedItems to PLAYED.
     */
    public static Future<?> markAllItemsRead(int state) {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItems(state);
            adapter.close();

            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }

    /**
     * Sets the 'read'-attribute of all NEW FeedItems to UNPLAYED.
     */
    public static Future<?> removeAllNewFlags() {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItems(FeedItem.NEW, FeedItem.UNPLAYED);
            adapter.close();

            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }

    static Future<?> addNewFeed(final Context context, final Feed... feeds) {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setCompleteFeed(feeds);
            adapter.close();

            for (Feed feed : feeds) {
                SyncService.enqueueFeedAdded(context, feed.getDownload_url());
            }

            BackupManager backupManager = new BackupManager(context);
            backupManager.dataChanged();
        });
    }

    static Future<?> setCompleteFeed(final Feed... feeds) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setCompleteFeed(feeds);
            adapter.close();
        });
    }

    public static Future<?> setItemList(final List<FeedItem> items) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItemlist(items);
            adapter.close();
            EventBus.getDefault().post(FeedItemEvent.updated(items));
        });
    }

    /**
     * Saves a FeedMedia object in the database. This method will save all attributes of the FeedMedia object. The
     * contents of FeedComponent-attributes (e.g. the FeedMedia's 'item'-attribute) will not be saved.
     *
     * @param media The FeedMedia object.
     */
    public static Future<?> setFeedMedia(final FeedMedia media) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setMedia(media);
            adapter.close();
        });
    }

    /**
     * Saves the 'position', 'duration' and 'last played time' attributes of a FeedMedia object
     *
     * @param media The FeedMedia object.
     */
    public static Future<?> setFeedMediaPlaybackInformation(final FeedMedia media) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedMediaPlaybackInformation(media);
            adapter.close();
        });
    }

    /**
     * Saves a FeedItem object in the database. This method will save all attributes of the FeedItem object including
     * the content of FeedComponent-attributes.
     *
     * @param item The FeedItem object.
     */
    public static Future<?> setFeedItem(final FeedItem item) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setSingleFeedItem(item);
            adapter.close();
            EventBus.getDefault().post(FeedItemEvent.updated(item));
        });
    }

    /**
     * Updates download URL of a feed
     */
    public static Future<?> updateFeedDownloadURL(final String original, final String updated) {
        Log.d(TAG, "updateFeedDownloadURL(original: " + original + ", updated: " + updated + ")");
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedDownloadUrl(original, updated);
            adapter.close();
        });
    }

    /**
     * Saves a FeedPreferences object in the database. The Feed ID of the FeedPreferences-object MUST NOT be 0.
     *
     * @param preferences The FeedPreferences object.
     */
    public static Future<?> setFeedPreferences(final FeedPreferences preferences) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedPreferences(preferences);
            adapter.close();
            EventBus.getDefault().post(new FeedListUpdateEvent(preferences.getFeedID()));
        });
    }

    private static boolean itemListContains(List<FeedItem> items, long itemId) {
        return indexInItemList(items, itemId) >= 0;
    }

    private static int indexInItemList(List<FeedItem> items, long itemId) {
        for (int i = 0; i < items.size(); i++) {
            FeedItem item = items.get(i);
            if (item.getId() == itemId) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Saves if a feed's last update failed
     *
     * @param lastUpdateFailed true if last update failed
     */
    public static Future<?> setFeedLastUpdateFailed(final long feedId,
                                                    final boolean lastUpdateFailed) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedLastUpdateFailed(feedId, lastUpdateFailed);
            adapter.close();
            EventBus.getDefault().post(new FeedListUpdateEvent(feedId));
        });
    }

    public static Future<?> setFeedCustomTitle(Feed feed) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedCustomTitle(feed.getId(), feed.getCustomTitle());
            adapter.close();
            EventBus.getDefault().post(new FeedListUpdateEvent(feed));
        });
    }

    /**
     * Sort the FeedItems in the queue with the given the named sort order.
     *
     * @param broadcastUpdate <code>true</code> if this operation should trigger a
     *                        QueueUpdateBroadcast. This option should be set to <code>false</code>
     *                        if the caller wants to avoid unexpected updates of the GUI.
     */
    public static Future<?> reorderQueue(@Nullable SortOrder sortOrder, final boolean broadcastUpdate) {
        if (sortOrder == null) {
            Log.w(TAG, "reorderQueue() - sortOrder is null. Do nothing.");
            return dbExec.submit(() -> { });
        }
        final Permutor<FeedItem> permutor = FeedItemPermutors.getPermutor(sortOrder);
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            final List<FeedItem> queue = DBReader.getQueue(adapter);

            if (queue != null) {
                permutor.reorder(queue);
                adapter.setQueue(queue);
                if (broadcastUpdate) {
                    EventBus.getDefault().post(QueueEvent.sorted(queue));
                }
            } else {
                Log.e(TAG, "reorderQueue: Could not load queue");
            }
            adapter.close();
        });
    }

    /**
     * Sets the 'auto_download'-attribute of specific FeedItem.
     *
     * @param feedItem     FeedItem.
     * @param autoDownload true enables auto download, false disables it
     */
    public static Future<?> setFeedItemAutoDownload(final FeedItem feedItem,
                                                    final boolean autoDownload) {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItemAutoDownload(feedItem, autoDownload ? 1 : 0);
            adapter.close();
            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }

    public static Future<?> saveFeedItemAutoDownloadFailed(final FeedItem feedItem) {
        return dbExec.submit(() -> {
            int failedAttempts = feedItem.getFailedAutoDownloadAttempts() + 1;
            long autoDownload;
            if (!feedItem.getAutoDownload() || failedAttempts >= 10) {
                autoDownload = 0; // giving up, disable auto download
                feedItem.setAutoDownload(false);
            } else {
                long now = System.currentTimeMillis();
                autoDownload = (now / 10) * 10 + failedAttempts;
            }
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItemAutoDownload(feedItem, autoDownload);
            adapter.close();
            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }

    /**
     * Sets the 'auto_download'-attribute of specific FeedItem.
     *
     * @param feed         This feed's episodes will be processed.
     * @param autoDownload If true, auto download will be enabled for the feed's episodes. Else,
     */
    public static Future<?> setFeedsItemsAutoDownload(final Feed feed,
                                                      final boolean autoDownload) {
        Log.d(TAG, (autoDownload ? "Enabling" : "Disabling") + " auto download for items of feed " + feed.getId());
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedsItemsAutoDownload(feed, autoDownload);
            adapter.close();
            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }


    /**
     * Set filter of the feed
     *
     * @param feedId       The feed's ID
     * @param filterValues Values that represent properties to filter by
     */
    public static Future<?> setFeedItemsFilter(final long feedId,
                                               final Set<String> filterValues) {
        Log.d(TAG, "setFeedItemsFilter() called with: " + "feedId = [" + feedId + "], filterValues = [" + filterValues + "]");
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItemFilter(feedId, filterValues);
            adapter.close();
            EventBus.getDefault().post(new FeedEvent(FeedEvent.Action.FILTER_CHANGED, feedId));
        });
    }

    /**
     * Set item sort order of the feed
     *
     */
    public static Future<?> setFeedItemSortOrder(long feedId, @Nullable SortOrder sortOrder) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItemSortOrder(feedId, sortOrder);
            adapter.close();
            EventBus.getDefault().post(new FeedEvent(FeedEvent.Action.SORT_ORDER_CHANGED, feedId));
        });
    }

    /**
     * Reset the statistics in DB
     */
    @NonNull
    public static Future<?> resetStatistics() {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.resetAllMediaPlayedDuration();
            adapter.close();
        });
    }
}
