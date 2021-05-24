package de.danoeh.antennapod.core.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import de.danoeh.antennapod.core.R;
import de.danoeh.antennapod.core.event.FeedItemEvent;
import de.danoeh.antennapod.core.event.FeedListUpdateEvent;
import de.danoeh.antennapod.core.event.MessageEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.core.feed.LocalFeedUpdater;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.download.DownloadStatus;
import de.danoeh.antennapod.core.storage.mapper.FeedCursorMapper;
import de.danoeh.antennapod.core.sync.SyncService;
import de.danoeh.antennapod.core.util.DownloadError;
import de.danoeh.antennapod.core.util.LongList;
import de.danoeh.antennapod.core.util.comparator.FeedItemPubdateComparator;
import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.content.Context.MODE_PRIVATE;

/**
 * Provides methods for doing common tasks that use DBReader and DBWriter.
 */
public final class DBTasks {
    private static final String TAG = "DBTasks";

    private static final String PREF_NAME = "dbtasks";
    private static final String PREF_LAST_REFRESH = "last_refresh";

    /**
     * Executor service used by the autodownloadUndownloadedEpisodes method.
     */
    private static final ExecutorService autodownloadExec;

    private static AutomaticDownloadAlgorithm downloadAlgorithm = new AutomaticDownloadAlgorithm();

    static {
        autodownloadExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    private DBTasks() {
    }

    /**
     * Removes the feed with the given download url. This method should NOT be executed on the GUI thread.
     *
     * @param context     Used for accessing the db
     * @param downloadUrl URL of the feed.
     */
    public static void removeFeedWithDownloadUrl(Context context, String downloadUrl) {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        Cursor cursor = adapter.getFeedCursorDownloadUrls();
        long feedID = 0;
        if (cursor.moveToFirst()) {
            do {
                if (cursor.getString(1).equals(downloadUrl)) {
                    feedID = cursor.getLong(0);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        adapter.close();

        if (feedID != 0) {
            try {
                DBWriter.deleteFeed(context, feedID).get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        } else {
            Log.w(TAG, "removeFeedWithDownloadUrl: Could not find feed with url: " + downloadUrl);
        }
    }

    private static final AtomicBoolean isRefreshing = new AtomicBoolean(false);

    /**
     * Refreshes all feeds.
     * It must not be from the main thread.
     * This method might ignore subsequent calls if it is still
     * enqueuing Feeds for download from a previous call
     *
     * @param context  Might be used for accessing the database
     * @param initiatedByUser a boolean indicating if the refresh was triggered by user action.
     */
    public static void refreshAllFeeds(final Context context, boolean initiatedByUser) {
        if (!isRefreshing.compareAndSet(false, true)) {
            Log.d(TAG, "Ignoring request to refresh all feeds: Refresh lock is locked");
            return;
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("DBTasks.refreshAllFeeds() must not be called from the main thread.");
        }

        List<Feed> feeds = DBReader.getFeedList();
        ListIterator<Feed> iterator = feeds.listIterator();
        while (iterator.hasNext()) {
            if (!iterator.next().getPreferences().getKeepUpdated()) {
                iterator.remove();
            }
        }
        try {
            refreshFeeds(context, feeds, false, false, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        isRefreshing.set(false);

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putLong(PREF_LAST_REFRESH, System.currentTimeMillis()).apply();

        SyncService.sync(context);
        // Note: automatic download of episodes will be done but not here.
        // Instead it is done after all feeds have been refreshed (asynchronously),
        // in DownloadService.onDestroy()
        // See Issue #2577 for the details of the rationale
    }

    /**
     * Queues the next page of this Feed for download. The given Feed has to be a paged
     * Feed (isPaged()=true) and must contain a nextPageLink.
     *
     * @param context      Used for requesting the download.
     * @param feed         The feed whose next page should be loaded.
     * @param loadAllPages True if any subsequent pages should also be loaded, false otherwise.
     */
    public static void loadNextPageOfFeed(final Context context, Feed feed, boolean loadAllPages) {
        if (feed.isPaged() && feed.getNextPageLink() != null) {
            int pageNr = feed.getPageNr() + 1;
            Feed nextFeed = new Feed(feed.getNextPageLink(), null, feed.getTitle() + "(" + pageNr + ")");
            nextFeed.setPageNr(pageNr);
            nextFeed.setPaged(true);
            nextFeed.setId(feed.getId());
            //DownloadRequester.getInstance().downloadFeed(context, nextFeed, loadAllPages, false, true);
        } else {
            Log.e(TAG, "loadNextPageOfFeed: Feed was either not paged or contained no nextPageLink");
        }
    }

    private static void refreshFeeds(Context context, List<Feed> feeds, boolean loadAllPages,
                                    boolean force, boolean initiatedByUser) {
        List<Feed> localFeeds = new ArrayList<>();
        List<Feed> normalFeeds = new ArrayList<>();

        for (Feed feed : feeds) {
            if (feed.isLocalFeed()) {
                localFeeds.add(feed);
            } else {
                normalFeeds.add(feed);
            }
        }

        if (!localFeeds.isEmpty()) {
            new Thread(() -> {
                for (Feed feed : localFeeds) {
                    LocalFeedUpdater.updateFeed(feed, context);
                }
            }).start();
        }
        if (!normalFeeds.isEmpty()) {
            //DownloadRequester.getInstance().downloadFeeds(context, normalFeeds, loadAllPages, force, initiatedByUser);
        }
    }

    /**
     * Notifies the database about a missing FeedMedia file. This method will correct the FeedMedia object's
     * values in the DB and send a FeedItemEvent.
     */
    public static void notifyMissingFeedMediaFile(final Context context, final FeedMedia media) {
        Log.i(TAG, "The feedmanager was notified about a missing episode. It will update its database now.");
        media.setDownloaded(false);
        media.setFile_url(null);
        DBWriter.setFeedMedia(media);
        EventBus.getDefault().post(FeedItemEvent.deletedMedia(media.getItem()));
        EventBus.getDefault().post(new MessageEvent(context.getString(R.string.error_file_not_found)));
    }

    public static List<? extends FeedItem> enqueueFeedItemsToDownload(final Context context,
                       List<? extends FeedItem> items) throws InterruptedException, ExecutionException {
        List<FeedItem> itemsToEnqueue = new ArrayList<>();
        if (UserPreferences.enqueueDownloadedEpisodes()) {
            LongList queueIDList = DBReader.getQueueIDList();
            for (FeedItem item : items) {
                if (!queueIDList.contains(item.getId())) {
                    itemsToEnqueue.add(item);
                }
            }
            DBWriter.addQueueItem(context, false, itemsToEnqueue.toArray(new FeedItem[0])).get();
        }
        return itemsToEnqueue;
    }

    /**
     * Looks for non-downloaded episodes in the queue or list of unread items and request a download if
     * 1. Network is available
     * 2. The device is charging or the user allows auto download on battery
     * 3. There is free space in the episode cache
     * This method is executed on an internal single thread executor.
     *
     * @param context  Used for accessing the DB.
     * @return A Future that can be used for waiting for the methods completion.
     */
    public static Future<?> autodownloadUndownloadedItems(final Context context) {
        Log.d(TAG, "autodownloadUndownloadedItems");
        return autodownloadExec.submit(downloadAlgorithm.autoDownloadUndownloadedItems(context));
    }

    /**
     * For testing purpose only.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public static void setDownloadAlgorithm(AutomaticDownloadAlgorithm newDownloadAlgorithm) {
        downloadAlgorithm = newDownloadAlgorithm;
    }

    /**
     * Removed downloaded episodes outside of the queue if the episode cache is full. Episodes with a smaller
     * 'playbackCompletionDate'-value will be deleted first.
     * <p/>
     * This method should NOT be executed on the GUI thread.
     *
     * @param context Used for accessing the DB.
     */
    public static void performAutoCleanup(final Context context) {
        UserPreferences.getEpisodeCleanupAlgorithm().performCleanup(context);
    }

    /**
     * Returns the successor of a FeedItem in the queue.
     *
     * @param itemId  ID of the FeedItem
     * @param queue   Used for determining the successor of the item. If this parameter is null, the method will load
     *                the queue from the database in the same thread.
     * @return Successor of the FeedItem or null if the FeedItem is not in the queue or has no successor.
     */
    public static FeedItem getQueueSuccessorOfItem(final long itemId, List<FeedItem> queue) {
        FeedItem result = null;
        if (queue == null) {
            queue = DBReader.getQueue();
        }
        if (queue != null) {
            Iterator<FeedItem> iterator = queue.iterator();
            while (iterator.hasNext()) {
                FeedItem item = iterator.next();
                if (item.getId() == itemId) {
                    if (iterator.hasNext()) {
                        result = iterator.next();
                    }
                    break;
                }
            }
        }
        return result;
    }

    private static Feed searchFeedByIdentifyingValueOrID(PodDBAdapter adapter,
                                                         Feed feed) {
        if (feed.getId() != 0) {
            return DBReader.getFeed(feed.getId());
        } else {
            List<Feed> feeds = DBReader.getFeedList();
            for (Feed f : feeds) {
                if (f.getIdentifyingValue().equals(feed.getIdentifyingValue())) {
                    f.setItems(DBReader.getFeedItemList(f));
                    return f;
                }
            }
        }
        return null;
    }

    /**
     * Get a FeedItem by its identifying value.
     */
    private static FeedItem searchFeedItemByIdentifyingValue(Feed feed, String identifier) {
        for (FeedItem item : feed.getItems()) {
            if (TextUtils.equals(item.getIdentifyingValue(), identifier)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Adds new Feeds to the database or updates the old versions if they already exists. If another Feed with the same
     * identifying value already exists, this method will add new FeedItems from the new Feed to the existing Feed.
     * These FeedItems will be marked as unread with the exception of the most recent FeedItem.
     * <p/>
     * This method can update multiple feeds at once. Submitting a feed twice in the same method call can result in undefined behavior.
     * <p/>
     * This method should NOT be executed on the GUI thread.
     *
     * @param context Used for accessing the DB.
     * @param newFeed The new Feed object.
     * @param removeUnlistedItems The item list in the new Feed object is considered to be exhaustive.
     *                            I.e. items are removed from the database if they are not in this item list.
     * @return The updated Feed from the database if it already existed, or the new Feed from the parameters otherwise.
     */
    public static synchronized Feed updateFeed(Context context, Feed newFeed, boolean removeUnlistedItems) {
        Feed resultFeed;
        List<FeedItem> unlistedItems = new ArrayList<>();

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        // Look up feed in the feedslist
        final Feed savedFeed = searchFeedByIdentifyingValueOrID(adapter, newFeed);
        if (savedFeed == null) {
            Log.d(TAG, "Found no existing Feed with title "
                            + newFeed.getTitle() + ". Adding as new one.");

            // Add a new Feed
            // all new feeds will have the most recent item marked as unplayed
            FeedItem mostRecent = newFeed.getMostRecentItem();
            if (mostRecent != null) {
                mostRecent.setNew();
            }

            resultFeed = newFeed;
        } else {
            Log.d(TAG, "Feed with title " + newFeed.getTitle()
                        + " already exists. Syncing new with existing one.");

            Collections.sort(newFeed.getItems(), new FeedItemPubdateComparator());

            if (newFeed.getPageNr() == savedFeed.getPageNr()) {
                if (savedFeed.compareWithOther(newFeed)) {
                    Log.d(TAG, "Feed has updated attribute values. Updating old feed's attributes");
                    savedFeed.updateFromOther(newFeed);
                }
            } else {
                Log.d(TAG, "New feed has a higher page number.");
                savedFeed.setNextPageLink(newFeed.getNextPageLink());
            }
            if (savedFeed.getPreferences().compareWithOther(newFeed.getPreferences())) {
                Log.d(TAG, "Feed has updated preferences. Updating old feed's preferences");
                savedFeed.getPreferences().updateFromOther(newFeed.getPreferences());
            }

            // get the most recent date now, before we start changing the list
            FeedItem priorMostRecent = savedFeed.getMostRecentItem();
            Date priorMostRecentDate = null;
            if (priorMostRecent != null) {
                priorMostRecentDate = priorMostRecent.getPubDate();
            }

            // Look for new or updated Items
            for (int idx = 0; idx < newFeed.getItems().size(); idx++) {
                final FeedItem item = newFeed.getItems().get(idx);
                FeedItem oldItem = searchFeedItemByIdentifyingValue(savedFeed, item.getIdentifyingValue());
                if (oldItem == null) {
                    // item is new
                    item.setFeed(savedFeed);
                    item.setAutoDownload(savedFeed.getPreferences().getAutoDownload());

                    if (idx >= savedFeed.getItems().size()) {
                        savedFeed.getItems().add(item);
                    } else {
                        savedFeed.getItems().add(idx, item);
                    }

                    // only mark the item new if it was published after or at the same time
                    // as the most recent item
                    // (if the most recent date is null then we can assume there are no items
                    // and this is the first, hence 'new')
                    // New items that do not have a pubDate set are always marked as new
                    if (item.getPubDate() == null || priorMostRecentDate == null
                            || priorMostRecentDate.before(item.getPubDate())
                            || priorMostRecentDate.equals(item.getPubDate())) {
                        Log.d(TAG, "Marking item published on " + item.getPubDate()
                                + " new, prior most recent date = " + priorMostRecentDate);
                        item.setNew();
                    }
                } else {
                    oldItem.updateFromOther(item);
                }
            }

            // identify items to be removed
            if (removeUnlistedItems) {
                Iterator<FeedItem> it = savedFeed.getItems().iterator();
                while (it.hasNext()) {
                    FeedItem feedItem = it.next();
                    if (searchFeedItemByIdentifyingValue(newFeed, feedItem.getIdentifyingValue()) == null) {
                        unlistedItems.add(feedItem);
                        it.remove();
                    }
                }
            }

            // update attributes
            savedFeed.setLastUpdate(newFeed.getLastUpdate());
            savedFeed.setType(newFeed.getType());
            savedFeed.setLastUpdateFailed(false);

            resultFeed = savedFeed;
        }

        try {
            if (savedFeed == null) {
                DBWriter.addNewFeed(context, newFeed).get();
                // Update with default values that are set in database
                resultFeed = searchFeedByIdentifyingValueOrID(adapter, newFeed);
            } else {
                DBWriter.setCompleteFeed(savedFeed).get();
            }
            if (removeUnlistedItems) {
                DBWriter.deleteFeedItems(context, unlistedItems).get();
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        adapter.close();

        if (savedFeed != null) {
            EventBus.getDefault().post(new FeedListUpdateEvent(savedFeed));
        } else {
            EventBus.getDefault().post(new FeedListUpdateEvent(Collections.emptyList()));
        }

        return resultFeed;
    }

    /**
     * Searches the FeedItems of a specific Feed for a given string.
     *
     * @param context Used for accessing the DB.
     * @param feedID  The id of the feed whose items should be searched.
     * @param query   The search string.
     * @return A FutureTask object that executes the search request
     *         and returns the search result as a List of FeedItems.
     */
    public static FutureTask<List<FeedItem>> searchFeedItems(final Context context,
                                                                 final long feedID, final String query) {
        return new FutureTask<>(new QueryTask<List<FeedItem>>(context) {
            @Override
            public void execute(PodDBAdapter adapter) {
                Cursor searchResult = adapter.searchItems(feedID, query);
                List<FeedItem> items = DBReader.extractItemlistFromCursor(searchResult);
                DBReader.loadAdditionalFeedItemListData(items);
                setResult(items);
                searchResult.close();
            }
        });
    }

    public static FutureTask<List<Feed>> searchFeeds(final Context context, final String query) {
        return new FutureTask<>(new QueryTask<List<Feed>>(context) {
            @Override
            public void execute(PodDBAdapter adapter) {
                Cursor cursor = adapter.searchFeeds(query);
                List<Feed> items = new ArrayList<>();
                if (cursor.moveToFirst()) {
                    do {
                        items.add(FeedCursorMapper.convert(cursor));
                    } while (cursor.moveToNext());
                }
                setResult(items);
                cursor.close();
            }
        });
    }

    /**
     * A runnable which should be used for database queries. The onCompletion
     * method is executed on the database executor to handle Cursors correctly.
     * This class automatically creates a PodDBAdapter object and closes it when
     * it is no longer in use.
     */
    abstract static class QueryTask<T> implements Callable<T> {
        private T result;

        public QueryTask(Context context) {
        }

        @Override
        public T call() throws Exception {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            execute(adapter);
            adapter.close();
            return result;
        }

        public abstract void execute(PodDBAdapter adapter);

        void setResult(T result) {
            this.result = result;
        }
    }
}
