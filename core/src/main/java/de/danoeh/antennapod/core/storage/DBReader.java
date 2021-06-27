package de.danoeh.antennapod.core.storage;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.core.feed.SubscriptionsFilter;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.download.DownloadStatus;
import de.danoeh.antennapod.core.storage.mapper.ChapterCursorMapper;
import de.danoeh.antennapod.core.storage.mapper.FeedCursorMapper;
import de.danoeh.antennapod.core.storage.mapper.FeedItemCursorMapper;
import de.danoeh.antennapod.core.storage.mapper.FeedMediaCursorMapper;
import de.danoeh.antennapod.core.storage.mapper.FeedPreferencesCursorMapper;
import de.danoeh.antennapod.core.util.LongIntMap;
import de.danoeh.antennapod.core.util.LongList;
import de.danoeh.antennapod.core.util.comparator.DownloadStatusComparator;
import de.danoeh.antennapod.core.util.comparator.FeedItemPubdateComparator;
import de.danoeh.antennapod.core.util.comparator.PlaybackCompletionDateComparator;

/**
 * Provides methods for reading data from the AntennaPod database.
 * In general, all database calls in DBReader-methods are executed on the caller's thread.
 * This means that the caller should make sure that DBReader-methods are not executed on the GUI-thread.
 */
public final class DBReader {

    private static final String TAG = "DBReader";

    /**
     * Maximum size of the list returned by {@link #getPlaybackHistory()}.
     */
    public static final int PLAYBACK_HISTORY_SIZE = 50;

    /**
     * Maximum size of the list returned by {@link #getDownloadLog()}.
     */
    private static final int DOWNLOAD_LOG_SIZE = 200;


    private DBReader() {
    }

    /**
     * Returns a list of Feeds, sorted alphabetically by their title.
     *
     * @return A list of Feeds, sorted alphabetically by their title. A Feed-object
     * of the returned list does NOT have its list of FeedItems yet. The FeedItem-list
     * can be loaded separately with {@link #getFeedItemList(Feed)}.
     */
    @NonNull
    public static List<Feed> getFeedList() {
        Log.d(TAG, "Extracting Feedlist");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            return getFeedList(adapter);
        } finally {
            adapter.close();
        }
    }

    @NonNull
    private static List<Feed> getFeedList(PodDBAdapter adapter) {
        try (Cursor cursor = adapter.getAllFeedsCursor()) {
            List<Feed> feeds = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                Feed feed = extractFeedFromCursorRow(cursor);
                feeds.add(feed);
            }
            return feeds;
        }
    }

    /**
     * Returns a list with the download URLs of all feeds.
     *
     * @return A list of Strings with the download URLs of all feeds.
     */
    public static List<String> getFeedListDownloadUrls() {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (Cursor cursor = adapter.getFeedCursorDownloadUrls()) {
            List<String> result = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                result.add(cursor.getString(1));
            }
            return result;
        } finally {
            adapter.close();
        }
    }

    /**
     * Loads additional data in to the feed items from other database queries
     *
     * @param items the FeedItems who should have other data loaded
     */
    public static void loadAdditionalFeedItemListData(List<FeedItem> items) {
        loadTagsOfFeedItemList(items);
        loadFeedDataOfFeedItemList(items);
    }

    private static void loadTagsOfFeedItemList(List<FeedItem> items) {
        LongList favoriteIds = getFavoriteIDList();
        LongList queueIds = getQueueIDList();

        for (FeedItem item : items) {
            if (favoriteIds.contains(item.getId())) {
                item.addTag(FeedItem.TAG_FAVORITE);
            }
            if (queueIds.contains(item.getId())) {
                item.addTag(FeedItem.TAG_QUEUE);
            }
        }
    }

    /**
     * Takes a list of FeedItems and loads their corresponding Feed-objects from the database.
     * The feedID-attribute of a FeedItem must be set to the ID of its feed or the method will
     * not find the correct feed of an item.
     *
     * @param items The FeedItems whose Feed-objects should be loaded.
     */
    private static void loadFeedDataOfFeedItemList(List<FeedItem> items) {
        List<Feed> feeds = getFeedList();

        Map<Long, Feed> feedIndex = new ArrayMap<>(feeds.size());
        for (Feed feed : feeds) {
            feedIndex.put(feed.getId(), feed);
        }
        for (FeedItem item : items) {
            Feed feed = feedIndex.get(item.getFeedId());
            if (feed == null) {
                Log.w(TAG, "No match found for item with ID " + item.getId() + ". Feed ID was " + item.getFeedId());
                feed = new Feed("", "", "Error: Item without feed");
            }
            item.setFeed(feed);
        }
    }

    /**
     * Loads the list of FeedItems for a certain Feed-object.
     * This method should NOT be used if the FeedItems are not used.
     *
     * @param feed The Feed whose items should be loaded
     * @return A list with the FeedItems of the Feed. The Feed-attribute of the FeedItems will already be set correctly.
     *         The method does NOT change the items-attribute of the feed.
     */
    public static List<FeedItem> getFeedItemList(final Feed feed) {
        return getFeedItemList(feed, FeedItemFilter.unfiltered());
    }

    public static List<FeedItem> getFeedItemList(final Feed feed, final FeedItemFilter filter) {
        Log.d(TAG, "getFeedItemList() called with: " + "feed = [" + feed + "]");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (Cursor cursor = adapter.getItemsOfFeedCursor(feed, filter)) {
            List<FeedItem> items = extractItemlistFromCursor(adapter, cursor);
            Collections.sort(items, new FeedItemPubdateComparator());
            for (FeedItem item : items) {
                item.setFeed(feed);
            }
            return items;
        } finally {
            adapter.close();
        }
    }

    public static List<FeedItem> extractItemlistFromCursor(Cursor itemlistCursor) {
        Log.d(TAG, "extractItemlistFromCursor() called with: " + "itemlistCursor = [" + itemlistCursor + "]");
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            return extractItemlistFromCursor(adapter, itemlistCursor);
        } finally {
            adapter.close();
        }
    }

    @NonNull
    private static List<FeedItem> extractItemlistFromCursor(PodDBAdapter adapter, Cursor cursor) {
        List<FeedItem> result = new ArrayList<>(cursor.getCount());
        if (cursor.moveToFirst()) {
            int indexMediaId = cursor.getColumnIndexOrThrow(PodDBAdapter.SELECT_KEY_MEDIA_ID);
            do {
                FeedItem item = FeedItemCursorMapper.convert(cursor);
                result.add(item);
                if (!cursor.isNull(indexMediaId)) {
                    item.setMedia(FeedMediaCursorMapper.convert(cursor));
                }
            } while (cursor.moveToNext());
        }
        return result;
    }

    private static Feed extractFeedFromCursorRow(Cursor cursor) {
        Feed feed = FeedCursorMapper.convert(cursor);
        FeedPreferences preferences = FeedPreferencesCursorMapper.convert(cursor);
        feed.setPreferences(preferences);
        return feed;
    }

    @NonNull
    static List<FeedItem> getQueue(PodDBAdapter adapter) {
        Log.d(TAG, "getQueue()");
        try (Cursor cursor = adapter.getQueueCursor()) {
            List<FeedItem> items = extractItemlistFromCursor(adapter, cursor);
            loadAdditionalFeedItemListData(items);
            return items;
        }
    }

    @NonNull
    static List<FeedItem> getPausedQueue(PodDBAdapter adapter, int limit) {
        Log.d(TAG, "getQueue()");
        try (Cursor cursor = adapter.getPausedQueueCursor(limit)) {
            List<FeedItem> items = extractItemlistFromCursor(adapter, cursor);
            loadAdditionalFeedItemListData(items);
            return items;
        }
    }

    /**
     * Loads the IDs of the FeedItems in the queue. This method should be preferred over
     * {@link #getQueue()} if the FeedItems of the queue are not needed.
     *
     * @return A list of IDs sorted by the same order as the queue.
     */
    public static LongList getQueueIDList() {
        Log.d(TAG, "getQueueIDList() called");
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            return getQueueIDList(adapter);
        } finally {
            adapter.close();
        }
    }

    private static LongList getQueueIDList(PodDBAdapter adapter) {
        try (Cursor cursor = adapter.getQueueIDCursor()) {
            LongList queueIds = new LongList(cursor.getCount());
            while (cursor.moveToNext()) {
                queueIds.add(cursor.getLong(0));
            }
            return queueIds;
        }
    }

    /**
     * Loads a list of the FeedItems in the queue. If the FeedItems of the queue are not used directly, consider using
     * {@link #getQueueIDList()} instead.
     *
     * @return A list of FeedItems sorted by the same order as the queue.
     */
    @NonNull
    public static List<FeedItem> getQueue() {
        Log.d(TAG, "getQueue() called");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            return getQueue(adapter);
        } finally {
            adapter.close();
        }
    }

    @NonNull
    public static List<FeedItem> getPausedQueue(int limit) {
        Log.d(TAG, "getQueue() called");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            return getPausedQueue(adapter, limit);
        } finally {
            adapter.close();
        }
    }

    /**
     * Loads a list of FeedItems whose episode has been downloaded.
     *
     * @return A list of FeedItems whose episdoe has been downloaded.
     */
    @NonNull
    public static List<FeedItem> getDownloadedItems() {
        Log.d(TAG, "getDownloadedItems() called");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (Cursor cursor = adapter.getDownloadedItemsCursor()) {
            List<FeedItem> items = extractItemlistFromCursor(adapter, cursor);
            loadAdditionalFeedItemListData(items);
            Collections.sort(items, new FeedItemPubdateComparator());
            return items;
        } finally {
            adapter.close();
        }
    }

    /**
     * Loads a list of FeedItems whose episode has been played.
     *
     * @return A list of FeedItems whose episdoe has been played.
     */
    @NonNull
    public static List<FeedItem> getPlayedItems() {
        Log.d(TAG, "getPlayedItems() called");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (Cursor cursor = adapter.getPlayedItemsCursor()) {
            List<FeedItem> items = extractItemlistFromCursor(adapter, cursor);
            loadAdditionalFeedItemListData(items);
            return items;
        } finally {
            adapter.close();
        }
    }

    /**
     * Loads a list of FeedItems that are considered new.
     * Excludes items from feeds that do not have keep updated enabled.
     *
     * @param offset The first episode that should be loaded.
     * @param limit The maximum number of episodes that should be loaded.
     * @return A list of FeedItems that are considered new.
     */
    public static List<FeedItem> getNewItemsList(int offset, int limit) {
        Log.d(TAG, "getNewItemsList() called");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (Cursor cursor = adapter.getNewItemsCursor(offset, limit)) {
            List<FeedItem> items = extractItemlistFromCursor(adapter, cursor);
            loadAdditionalFeedItemListData(items);
            return items;
        } finally {
            adapter.close();
        }
    }

    /**
     * Loads a list of favorite items.
     *
     * @param offset The first episode that should be loaded.
     * @param limit The maximum number of episodes that should be loaded.
     * @return A list of FeedItems that are marked as favorite.
     */
    public static List<FeedItem> getFavoriteItemsList(int offset, int limit) {
        Log.d(TAG, "getFavoriteItemsList() called");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (Cursor cursor = adapter.getFavoritesCursor(offset, limit)) {
            List<FeedItem> items = extractItemlistFromCursor(adapter, cursor);
            loadAdditionalFeedItemListData(items);
            return items;
        } finally {
            adapter.close();
        }
    }

    private static LongList getFavoriteIDList() {
        Log.d(TAG, "getFavoriteIDList() called");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (Cursor cursor = adapter.getFavoritesCursor(0, Integer.MAX_VALUE)) {
            LongList favoriteIDs = new LongList(cursor.getCount());
            while (cursor.moveToNext()) {
                favoriteIDs.add(cursor.getLong(0));
            }
            return favoriteIDs;
        } finally {
            adapter.close();
        }
    }

    /**
     * Loads a filtered list of FeedItems sorted by pubDate in descending order.
     *
     * @param offset The first episode that should be loaded.
     * @param limit The maximum number of episodes that should be loaded.
     * @param filter The filter describing which episodes to filter out.
     */
    @NonNull
    public static List<FeedItem> getRecentlyPublishedEpisodes(int offset, int limit, FeedItemFilter filter, boolean randomOrder) {
        Log.d(TAG, "getRecentlyPublishedEpisodes() called with: offset=" + offset + ", limit=" + limit);

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (Cursor cursor = adapter.getRecentlyPublishedItemsCursor(offset, limit, filter, randomOrder)) {
            List<FeedItem> items = extractItemlistFromCursor(adapter, cursor);
            loadAdditionalFeedItemListData(items);
            return items;
        } finally {
            adapter.close();
        }
    }

    @NonNull
    public static List<FeedItem> getRecentlyPublishedEpisodes(int offset, int limit, FeedItemFilter filter) {
        return getRecentlyPublishedEpisodes(offset, limit, filter, false);
    }

    /**
     * Loads the playback history from the database. A FeedItem is in the playback history if playback of the correpsonding episode
     * has been completed at least once.
     *
     * @return The playback history. The FeedItems are sorted by their media's playbackCompletionDate in descending order.
     * The size of the returned list is limited by {@link #PLAYBACK_HISTORY_SIZE}.
     */
    @NonNull
    public static List<FeedItem> getPlaybackHistory() {
        Log.d(TAG, "getPlaybackHistory() called");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        Cursor mediaCursor = null;
        Cursor itemCursor = null;
        try {
            mediaCursor = adapter.getCompletedMediaCursor(PLAYBACK_HISTORY_SIZE);
            String[] itemIds = new String[mediaCursor.getCount()];
            for (int i = 0; i < itemIds.length && mediaCursor.moveToPosition(i); i++) {
                int index = mediaCursor.getColumnIndex(PodDBAdapter.KEY_FEEDITEM);
                itemIds[i] = Long.toString(mediaCursor.getLong(index));
            }
            itemCursor = adapter.getFeedItemCursor(itemIds);
            List<FeedItem> items = extractItemlistFromCursor(adapter, itemCursor);
            loadAdditionalFeedItemListData(items);
            Collections.sort(items, new PlaybackCompletionDateComparator());
            return items;
        } finally {
            if (mediaCursor != null) {
                mediaCursor.close();
            }
            if (itemCursor != null) {
                itemCursor.close();
            }
            adapter.close();
        }
    }

    /**
     * Loads the download log from the database.
     *
     * @return A list with DownloadStatus objects that represent the download log.
     * The size of the returned list is limited by {@link #DOWNLOAD_LOG_SIZE}.
     */
    public static List<DownloadStatus> getDownloadLog() {
        Log.d(TAG, "getDownloadLog() called");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (Cursor cursor = adapter.getDownloadLogCursor(DOWNLOAD_LOG_SIZE)) {
            List<DownloadStatus> downloadLog = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                downloadLog.add(DownloadStatus.fromCursor(cursor));
            }
            Collections.sort(downloadLog, new DownloadStatusComparator());
            return downloadLog;
        } finally {
            adapter.close();
        }
    }

    /**
     * Loads the download log for a particular feed from the database.
     *
     * @param feedId Feed id for which the download log is loaded
     * @return A list with DownloadStatus objects that represent the feed's download log,
     * newest events first.
     */
    public static List<DownloadStatus> getFeedDownloadLog(long feedId) {
        Log.d(TAG, "getFeedDownloadLog() called with: " + "feed = [" + feedId + "]");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (Cursor cursor = adapter.getDownloadLog(Feed.FEEDFILETYPE_FEED, feedId)) {
            List<DownloadStatus> downloadLog = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                downloadLog.add(DownloadStatus.fromCursor(cursor));
            }
            Collections.sort(downloadLog, new DownloadStatusComparator());
            return downloadLog;
        } finally {
            adapter.close();
        }
    }

    /**
     * Loads a specific Feed from the database.
     *
     * @param feedId The ID of the Feed
     * @return The Feed or null if the Feed could not be found. The Feeds FeedItems will also be loaded from the
     *         database and the items-attribute will be set correctly.
     */
    @Nullable
    public static Feed getFeed(final long feedId) {
        return getFeed(feedId, false);
    }

    /**
     * Loads a specific Feed from the database.
     *
     * @param feedId The ID of the Feed
     * @param filtered <code>true</code> if only the visible items should be loaded according to the feed filter.
     * @return The Feed or null if the Feed could not be found. The Feeds FeedItems will also be loaded from the
     *         database and the items-attribute will be set correctly.
     */
    @Nullable
    public static Feed getFeed(final long feedId, boolean filtered) {
        Log.d(TAG, "getFeed() called with: " + "feedId = [" + feedId + "]");
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        Feed feed = null;
        try (Cursor cursor = adapter.getFeedCursor(feedId)) {
            if (cursor.moveToNext()) {
                feed = extractFeedFromCursorRow(cursor);
                if (filtered) {
                    feed.setItems(getFeedItemList(feed, feed.getItemFilter()));
                } else {
                    feed.setItems(getFeedItemList(feed));
                }
            } else {
                Log.e(TAG, "getFeed could not find feed with id " + feedId);
            }
            return feed;
        } finally {
            adapter.close();
        }
    }

    @Nullable
    private static FeedItem getFeedItem(final long itemId, PodDBAdapter adapter) {
        Log.d(TAG, "Loading feeditem with id " + itemId);

        FeedItem item = null;
        try (Cursor cursor = adapter.getFeedItemCursor(Long.toString(itemId))) {
            if (cursor.moveToNext()) {
                List<FeedItem> list = extractItemlistFromCursor(adapter, cursor);
                if (!list.isEmpty()) {
                    item = list.get(0);
                    loadAdditionalFeedItemListData(list);
                }
            }
            return item;
        }
    }

    /**
     * Loads a specific FeedItem from the database. This method should not be used for loading more
     * than one FeedItem because this method might query the database several times for each item.
     *
     * @param itemId The ID of the FeedItem
     * @return The FeedItem or null if the FeedItem could not be found.
     */
    @Nullable
    public static FeedItem getFeedItem(final long itemId) {
        Log.d(TAG, "getFeedItem() called with: " + "itemId = [" + itemId + "]");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            return getFeedItem(itemId, adapter);
        } finally {
            adapter.close();
        }
    }

    /**
     * Loads a specific FeedItem from the database.
     *
     * @param podcastUrl the corresponding feed's url
     * @param episodeUrl the feed item's url
     * @return The FeedItem or null if the FeedItem could not be found.
     *          Does NOT load additional attributes like feed or queue state.
     */
    @Nullable
    private static FeedItem getFeedItemByUrl(final String podcastUrl, final String episodeUrl, PodDBAdapter adapter) {
        Log.d(TAG, "Loading feeditem with podcast url " + podcastUrl + " and episode url " + episodeUrl);
        try (Cursor cursor = adapter.getFeedItemCursor(podcastUrl, episodeUrl)) {
            if (!cursor.moveToNext()) {
                return null;
            }
            List<FeedItem> list = extractItemlistFromCursor(adapter, cursor);
            if (!list.isEmpty()) {
                return list.get(0);
            }
            return null;
        }
    }

    /**
     * Returns credentials based on image URL
     *
     * @param imageUrl The URL of the image
     * @return Credentials in format "Username:Password", empty String if no authorization given
     */
    public static String getImageAuthentication(final String imageUrl) {
        Log.d(TAG, "getImageAuthentication() called with: " + "imageUrl = [" + imageUrl + "]");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            return getImageAuthentication(imageUrl, adapter);
        } finally {
            adapter.close();
        }
    }

    private static String getImageAuthentication(final String imageUrl, PodDBAdapter adapter) {
        String credentials;
        try (Cursor cursor = adapter.getImageAuthenticationCursor(imageUrl)) {
            if (cursor.moveToFirst()) {
                String username = cursor.getString(0);
                String password = cursor.getString(1);
                if (!TextUtils.isEmpty(username) && password != null) {
                    credentials = username + ":" + password;
                } else {
                    credentials = "";
                }
            } else {
                credentials = "";
            }
        }
        return credentials;
    }

    /**
     * Loads a specific FeedItem from the database.
     *
     * @param podcastUrl the corresponding feed's url
     * @param episodeUrl the feed item's url
     * @return The FeedItem or null if the FeedItem could not be found.
     *          Does NOT load additional attributes like feed or queue state.
     */
    public static FeedItem getFeedItemByUrl(final String podcastUrl, final String episodeUrl) {
        Log.d(TAG, "getFeedItem() called with: " + "podcastUrl = [" + podcastUrl + "], episodeUrl = [" + episodeUrl + "]");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            return getFeedItemByUrl(podcastUrl, episodeUrl, adapter);
        } finally {
            adapter.close();
        }
    }

    /**
     * Loads shownotes information about a FeedItem.
     *
     * @param item The FeedItem
     */
    public static void loadDescriptionOfFeedItem(final FeedItem item) {
        Log.d(TAG, "loadDescriptionOfFeedItem() called with: " + "item = [" + item + "]");
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (Cursor cursor = adapter.getDescriptionOfItem(item)) {
            if (cursor.moveToFirst()) {
                int indexDescription = cursor.getColumnIndex(PodDBAdapter.KEY_DESCRIPTION);
                String description = cursor.getString(indexDescription);
                item.setDescriptionIfLonger(description);
            }
        } finally {
            adapter.close();
        }
    }

    /**
     * Loads the list of chapters that belongs to this FeedItem if available. This method overwrites
     * any chapters that this FeedItem has. If no chapters were found in the database, the chapters
     * reference of the FeedItem will be set to null.
     *
     * @param item The FeedItem
     */
    public static List<Chapter> loadChaptersOfFeedItem(final FeedItem item) {
        Log.d(TAG, "loadChaptersOfFeedItem() called with: " + "item = [" + item + "]");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            return loadChaptersOfFeedItem(adapter, item);
        } finally {
            adapter.close();
        }
    }

    private static List<Chapter> loadChaptersOfFeedItem(PodDBAdapter adapter, FeedItem item) {
        try (Cursor cursor = adapter.getSimpleChaptersOfFeedItemCursor(item)) {
            int chaptersCount = cursor.getCount();
            if (chaptersCount == 0) {
                item.setChapters(null);
                return null;
            }
            ArrayList<Chapter> chapters = new ArrayList<>();
            while (cursor.moveToNext()) {
                chapters.add(ChapterCursorMapper.convert(cursor));
            }
            return chapters;
        }
    }

    /**
     * Returns the number of downloaded episodes.
     *
     * @return The number of downloaded episodes.
     */

    public static int getNumberOfDownloadedEpisodes() {
        Log.d(TAG, "getNumberOfDownloadedEpisodes() called with: " + "");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            return adapter.getNumberOfDownloadedEpisodes();
        } finally {
            adapter.close();
        }
    }

    /**
     * Searches the DB for a FeedMedia of the given id.
     *
     * @param mediaId The id of the object
     * @return The found object, or null if it does not exist
     */
    @Nullable
    public static FeedMedia getFeedMedia(final long mediaId) {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        try (Cursor mediaCursor = adapter.getSingleFeedMediaCursor(mediaId)) {
            if (!mediaCursor.moveToFirst()) {
                return null;
            }

            int indexFeedItem = mediaCursor.getColumnIndex(PodDBAdapter.KEY_FEEDITEM);
            long itemId = mediaCursor.getLong(indexFeedItem);
            FeedMedia media = FeedMediaCursorMapper.convert(mediaCursor);
            FeedItem item = getFeedItem(itemId);
            if (item != null) {
                media.setItem(item);
                item.setMedia(media);
            }
            return media;
        } finally {
            adapter.close();
        }
    }

    /**
     * Searches the DB for statistics.
     *
     * @return The list of statistics objects
     */
    @NonNull
    public static List<StatisticsItem> getStatistics() {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        List<StatisticsItem> feedTime = new ArrayList<>();

        List<Feed> feeds = getFeedList();
        for (Feed feed : feeds) {
            long feedPlayedTimeCountAll = 0;
            long feedPlayedTime = 0;
            long feedTotalTime = 0;
            long episodes = 0;
            long episodesStarted = 0;
            long episodesStartedIncludingMarked = 0;
            long totalDownloadSize = 0;
            long episodesDownloadCount = 0;
            List<FeedItem> items = getFeed(feed.getId()).getItems();
            for (FeedItem item : items) {
                FeedMedia media = item.getMedia();
                if (media == null) {
                    continue;
                }

                feedPlayedTime += media.getPlayedDuration() / 1000;

                if (item.isPlayed()) {
                    feedPlayedTimeCountAll += media.getDuration() / 1000;
                } else {
                    feedPlayedTimeCountAll += media.getPosition() / 1000;
                }

                if (media.getPlaybackCompletionDate() != null || media.getPlayedDuration() > 0) {
                    episodesStarted++;
                }

                if (item.isPlayed() || media.getPosition() != 0) {
                    episodesStartedIncludingMarked++;
                }

                feedTotalTime += media.getDuration() / 1000;

                if (media.isDownloaded()) {
                    totalDownloadSize += new File(media.getFile_url()).length();
                    episodesDownloadCount++;
                }

                episodes++;
            }
            feedTime.add(new StatisticsItem(
                    feed, feedTotalTime, feedPlayedTime, feedPlayedTimeCountAll, episodes,
                    episodesStarted, episodesStartedIncludingMarked, totalDownloadSize, episodesDownloadCount));
        }

        adapter.close();
        return feedTime;
    }

    /**
     * Returns data necessary for displaying the navigation drawer. This includes
     * the list of subscriptions, the number of items in the queue and the number of unread
     * items.
     */
    @NonNull
    public static NavDrawerData getNavDrawerData(Integer feedOrder) {
        Log.d(TAG, "getNavDrawerData() called with: " + "");
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        final LongIntMap feedCounters = adapter.getFeedCounters();
        SubscriptionsFilter subscriptionsFilter = UserPreferences.getSubscriptionsFilter();
        List<Feed> feeds = subscriptionsFilter.filter(getFeedList(adapter), feedCounters);

        Comparator<Feed> comparator;
        if (feedOrder == null) {
            feedOrder = UserPreferences.getFeedOrder();
        }
        if (feedOrder == UserPreferences.FEED_ORDER_COUNTER) {
            comparator = (lhs, rhs) -> {
                long counterLhs = feedCounters.get(lhs.getId());
                long counterRhs = feedCounters.get(rhs.getId());
                if (counterLhs > counterRhs) {
                    // reverse natural order: podcast with most unplayed episodes first
                    return -1;
                } else if (counterLhs == counterRhs) {
                    return lhs.getTitle().compareToIgnoreCase(rhs.getTitle());
                } else {
                    return 1;
                }
            };
        } else if (feedOrder == UserPreferences.FEED_ORDER_ALPHABETICAL) {
            comparator = (lhs, rhs) -> {
                String t1 = lhs.getTitle();
                String t2 = rhs.getTitle();
                if (t1 == null) {
                    return 1;
                } else if (t2 == null) {
                    return -1;
                } else {
                    return t1.compareToIgnoreCase(t2);
                }
            };
        } else if (feedOrder == UserPreferences.FEED_ORDER_MOST_PLAYED) {
            final LongIntMap playedCounters = adapter.getPlayedEpisodesCounters();

            comparator = (lhs, rhs) -> {
                long counterLhs = playedCounters.get(lhs.getId());
                long counterRhs = playedCounters.get(rhs.getId());
                if (counterLhs > counterRhs) {
                    // podcast with most played episodes first
                    return -1;
                } else if (counterLhs == counterRhs) {
                    return lhs.getTitle().compareToIgnoreCase(rhs.getTitle());
                } else {
                    return 1;
                }
            };
        } else {
            final Map<Long, Long> recentPubDates = adapter.getMostRecentItemDates();
            comparator = (lhs, rhs) -> {
                long dateLhs = recentPubDates.containsKey(lhs.getId()) ? recentPubDates.get(lhs.getId()) : 0;
                long dateRhs = recentPubDates.containsKey(rhs.getId()) ? recentPubDates.get(rhs.getId()) : 0;
                return Long.compare(dateRhs, dateLhs);
            };
        }

        Collections.sort(feeds, comparator);
        int queueSize = adapter.getQueueSize();
        int numNewItems = adapter.getNumberOfNewItems();
        int numDownloadedItems = adapter.getNumberOfDownloadedEpisodes();

        List<NavDrawerData.DrawerItem> items = new ArrayList<>();
        Map<String, NavDrawerData.FolderDrawerItem> folders = new HashMap<>();
        for (Feed feed : feeds) {
            for (String tag : feed.getPreferences().getTags()) {
                NavDrawerData.FeedDrawerItem drawerItem = new NavDrawerData.FeedDrawerItem(feed, feed.getId(),
                        feedCounters.get(feed.getId()));
                if (FeedPreferences.TAG_ROOT.equals(tag)) {
                    items.add(drawerItem);
                    continue;
                }
                NavDrawerData.FolderDrawerItem folder;
                if (folders.containsKey(tag)) {
                    folder = folders.get(tag);
                } else {
                    folder = new NavDrawerData.FolderDrawerItem(tag);
                    folders.put(tag, folder);
                }
                drawerItem.id |= folder.id;
                folder.children.add(drawerItem);
            }
        }
        List<NavDrawerData.FolderDrawerItem> foldersSorted = new ArrayList<>(folders.values());
        Collections.sort(foldersSorted, (o1, o2) -> o1.getTitle().compareToIgnoreCase(o2.getTitle()));
        items.addAll(foldersSorted);

        NavDrawerData result = new NavDrawerData(items, queueSize, numNewItems, numDownloadedItems,
                feedCounters, UserPreferences.getEpisodeCleanupAlgorithm().getReclaimableItems());
        adapter.close();
        return result;
    }

    @NonNull
    public static NavDrawerData getNavDrawerData() {
        return getNavDrawerData(null);
    }
}
