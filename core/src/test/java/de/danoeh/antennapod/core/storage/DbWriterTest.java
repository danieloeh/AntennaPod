package de.danoeh.antennapod.core.storage;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;

import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;

import de.danoeh.antennapod.core.feed.Note;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.core.ApplicationCallbacks;
import de.danoeh.antennapod.core.ClientConfig;
import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.FeedMedia;
import de.danoeh.antennapod.core.preferences.PlaybackPreferences;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.util.FeedItemUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test class for {@link DBWriter}.
 */
@RunWith(RobolectricTestRunner.class)
public class DbWriterTest {

    private static final String TAG = "DBWriterTest";
    private static final String TEST_FOLDER = "testDBWriter";
    private static final long TIMEOUT = 5L;
    
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);

        Application app = (Application) context;
        ClientConfig.applicationCallbacks = mock(ApplicationCallbacks.class);
        when(ClientConfig.applicationCallbacks.getApplicationInstance()).thenReturn(app);

        // create new database
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();

        SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(
                context.getApplicationContext()).edit();
        prefEdit.putBoolean(UserPreferences.PREF_DELETE_REMOVES_FROM_QUEUE, true).commit();
    }

    @After
    public void tearDown() {
        PodDBAdapter.tearDownTests();
        DBWriter.tearDownTests();

        File testDir = context.getExternalFilesDir(TEST_FOLDER);
        assertNotNull(testDir);
        for (File f : testDir.listFiles()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    @Test
    public void testSetFeedMediaPlaybackInformation() throws Exception {
        final int position = 50;
        final long lastPlayedTime = 1000;
        final int playedDuration = 60;
        final int duration = 100;

        Feed feed = new Feed("url", null, "title");
        List<FeedItem> items = new ArrayList<>();
        feed.setItems(items);
        FeedItem item = new FeedItem(0, "Item", "Item", "url", new Date(), FeedItem.PLAYED, feed);
        items.add(item);
        FeedMedia media = new FeedMedia(0, item, duration, 1, 1, "mime_type",
                "dummy path", "download_url", true, null, 0, 0);
        item.setMedia(media);

        DBWriter.setFeedItem(item).get(TIMEOUT, TimeUnit.SECONDS);

        media.setPosition(position);
        media.setLastPlayedTime(lastPlayedTime);
        media.setPlayedDuration(playedDuration);

        DBWriter.setFeedMediaPlaybackInformation(item.getMedia()).get(TIMEOUT, TimeUnit.SECONDS);

        FeedItem itemFromDb = DBReader.getFeedItem(item.getId());
        FeedMedia mediaFromDb = itemFromDb.getMedia();

        assertEquals(position, mediaFromDb.getPosition());
        assertEquals(lastPlayedTime, mediaFromDb.getLastPlayedTime());
        assertEquals(playedDuration, mediaFromDb.getPlayedDuration());
        assertEquals(duration, mediaFromDb.getDuration());
    }

    @Test
    public void testDeleteFeedMediaOfItemFileExists() throws Exception {
        File dest = new File(context.getExternalFilesDir(TEST_FOLDER), "testFile");

        assertTrue(dest.createNewFile());

        Feed feed = new Feed("url", null, "title");
        List<FeedItem> items = new ArrayList<>();
        feed.setItems(items);
        FeedItem item = new FeedItem(0, "Item", "Item", "url", new Date(), FeedItem.PLAYED, feed);

        FeedMedia media = new FeedMedia(0, item, 1, 1, 1, "mime_type",
                dest.getAbsolutePath(), "download_url", true, null, 0, 0);
        item.setMedia(media);

        items.add(item);

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();
        assertTrue(media.getId() != 0);
        assertTrue(item.getId() != 0);

        DBWriter.deleteFeedMediaOfItem(context, media.getId())
                .get(TIMEOUT, TimeUnit.SECONDS);
        media = DBReader.getFeedMedia(media.getId());
        assertNotNull(media);
        assertFalse(dest.exists());
        assertFalse(media.isDownloaded());
        assertNull(media.getFile_url());
    }

    @Test
    public void testDeleteFeedMediaOfItemRemoveFromQueue() throws Exception {
        assertTrue(UserPreferences.shouldDeleteRemoveFromQueue());

        File dest = new File(context.getExternalFilesDir(TEST_FOLDER), "testFile");

        assertTrue(dest.createNewFile());

        Feed feed = new Feed("url", null, "title");
        List<FeedItem> items = new ArrayList<>();
        feed.setItems(items);
        FeedItem item = new FeedItem(0, "Item", "Item", "url", new Date(), FeedItem.UNPLAYED, feed);

        FeedMedia media = new FeedMedia(0, item, 1, 1, 1, "mime_type",
                dest.getAbsolutePath(), "download_url", true, null, 0, 0);
        item.setMedia(media);

        items.add(item);
        List<FeedItem> queue = new ArrayList<>();
        queue.add(item);

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.setQueue(queue);
        adapter.close();
        assertTrue(media.getId() != 0);
        assertTrue(item.getId() != 0);
        queue = DBReader.getQueue();
        assertTrue(queue.size() != 0);

        DBWriter.deleteFeedMediaOfItem(context, media.getId());
        Awaitility.await().until(() -> !dest.exists());
        media = DBReader.getFeedMedia(media.getId());
        assertNotNull(media);
        assertFalse(dest.exists());
        assertFalse(media.isDownloaded());
        assertNull(media.getFile_url());
        queue = DBReader.getQueue();
        assertEquals(0, queue.size());
    }

    @Test
    public void testDeleteFeed() throws Exception {
        File destFolder = context.getExternalFilesDir(TEST_FOLDER);
        assertNotNull(destFolder);

        Feed feed = new Feed("url", null, "title");
        feed.setItems(new ArrayList<>());

        List<File> itemFiles = new ArrayList<>();
        // create items with downloaded media files
        for (int i = 0; i < 10; i++) {
            FeedItem item = new FeedItem(0, "Item " + i, "Item" + i, "url", new Date(), FeedItem.PLAYED, feed);
            feed.getItems().add(item);

            File enc = new File(destFolder, "file " + i);
            assertTrue(enc.createNewFile());

            itemFiles.add(enc);
            FeedMedia media = new FeedMedia(0, item, 1, 1, 1, "mime_type",
                    enc.getAbsolutePath(), "download_url", true, null, 0, 0);
            item.setMedia(media);
        }

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        assertTrue(feed.getId() != 0);
        for (FeedItem item : feed.getItems()) {
            assertTrue(item.getId() != 0);
            assertTrue(item.getMedia().getId() != 0);
        }

        DBWriter.deleteFeed(context, feed.getId()).get(TIMEOUT, TimeUnit.SECONDS);

        // check if files still exist
        for (File f : itemFiles) {
            assertFalse(f.exists());
        }

        adapter = PodDBAdapter.getInstance();
        adapter.open();
        Cursor c = adapter.getFeedCursor(feed.getId());
        assertEquals(0, c.getCount());
        c.close();
        for (FeedItem item : feed.getItems()) {
            c = adapter.getFeedItemCursor(String.valueOf(item.getId()));
            assertEquals(0, c.getCount());
            c.close();
            c = adapter.getSingleFeedMediaCursor(item.getMedia().getId());
            assertEquals(0, c.getCount());
            c.close();
        }
        adapter.close();
    }

    @Test
    public void testDeleteFeedNoItems() throws Exception {
        File destFolder = context.getExternalFilesDir(TEST_FOLDER);
        assertNotNull(destFolder);

        Feed feed = new Feed("url", null, "title");
        feed.setItems(null);
        feed.setImageUrl("url");

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        assertTrue(feed.getId() != 0);

        DBWriter.deleteFeed(context, feed.getId()).get(TIMEOUT, TimeUnit.SECONDS);

        adapter = PodDBAdapter.getInstance();
        adapter.open();
        Cursor c = adapter.getFeedCursor(feed.getId());
        assertEquals(0, c.getCount());
        c.close();
        adapter.close();
    }

    @Test
    public void testDeleteFeedNoFeedMedia() throws Exception {
        File destFolder = context.getExternalFilesDir(TEST_FOLDER);
        assertNotNull(destFolder);

        Feed feed = new Feed("url", null, "title");
        feed.setItems(new ArrayList<>());

        feed.setImageUrl("url");

        // create items
        for (int i = 0; i < 10; i++) {
            FeedItem item = new FeedItem(0, "Item " + i, "Item" + i, "url", new Date(), FeedItem.PLAYED, feed);
            feed.getItems().add(item);

        }

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        assertTrue(feed.getId() != 0);
        for (FeedItem item : feed.getItems()) {
            assertTrue(item.getId() != 0);
        }

        DBWriter.deleteFeed(context, feed.getId()).get(TIMEOUT, TimeUnit.SECONDS);

        adapter = PodDBAdapter.getInstance();
        adapter.open();
        Cursor c = adapter.getFeedCursor(feed.getId());
        assertEquals(0, c.getCount());
        c.close();
        for (FeedItem item : feed.getItems()) {
            c = adapter.getFeedItemCursor(String.valueOf(item.getId()));
            assertEquals(0, c.getCount());
            c.close();
        }
        adapter.close();
    }

    @Test
    public void testDeleteFeedWithQueueItems() throws Exception {
        File destFolder = context.getExternalFilesDir(TEST_FOLDER);
        assertNotNull(destFolder);

        Feed feed = new Feed("url", null, "title");
        feed.setItems(new ArrayList<>());

        feed.setImageUrl("url");

        // create items with downloaded media files
        for (int i = 0; i < 10; i++) {
            FeedItem item = new FeedItem(0, "Item " + i, "Item" + i, "url", new Date(), FeedItem.PLAYED, feed);
            feed.getItems().add(item);
            File enc = new File(destFolder, "file " + i);
            FeedMedia media = new FeedMedia(0, item, 1, 1, 1, "mime_type",
                    enc.getAbsolutePath(), "download_url", false, null, 0, 0);
            item.setMedia(media);
        }

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        assertTrue(feed.getId() != 0);
        for (FeedItem item : feed.getItems()) {
            assertTrue(item.getId() != 0);
            assertTrue(item.getMedia().getId() != 0);
        }

        List<FeedItem> queue = new ArrayList<>(feed.getItems());
        adapter.open();
        adapter.setQueue(queue);

        Cursor queueCursor = adapter.getQueueIDCursor();
        assertEquals(queue.size(), queueCursor.getCount());
        queueCursor.close();

        adapter.close();
        DBWriter.deleteFeed(context, feed.getId()).get(TIMEOUT, TimeUnit.SECONDS);
        adapter.open();

        Cursor c = adapter.getFeedCursor(feed.getId());
        assertEquals(0, c.getCount());
        c.close();
        for (FeedItem item : feed.getItems()) {
            c = adapter.getFeedItemCursor(String.valueOf(item.getId()));
            assertEquals(0, c.getCount());
            c.close();
            c = adapter.getSingleFeedMediaCursor(item.getMedia().getId());
            assertEquals(0, c.getCount());
            c.close();
        }
        c = adapter.getQueueCursor();
        assertEquals(0, c.getCount());
        c.close();
        adapter.close();
    }

    @Test
    public void testDeleteFeedNoDownloadedFiles() throws Exception {
        File destFolder = context.getExternalFilesDir(TEST_FOLDER);
        assertNotNull(destFolder);

        Feed feed = new Feed("url", null, "title");
        feed.setItems(new ArrayList<>());

        feed.setImageUrl("url");

        // create items with downloaded media files
        for (int i = 0; i < 10; i++) {
            FeedItem item = new FeedItem(0, "Item " + i, "Item" + i, "url", new Date(), FeedItem.PLAYED, feed);
            feed.getItems().add(item);
            File enc = new File(destFolder, "file " + i);
            FeedMedia media = new FeedMedia(0, item, 1, 1, 1, "mime_type",
                    enc.getAbsolutePath(), "download_url", false, null, 0, 0);
            item.setMedia(media);
        }

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        assertTrue(feed.getId() != 0);
        for (FeedItem item : feed.getItems()) {
            assertTrue(item.getId() != 0);
            assertTrue(item.getMedia().getId() != 0);
        }

        DBWriter.deleteFeed(context, feed.getId()).get(TIMEOUT, TimeUnit.SECONDS);

        adapter = PodDBAdapter.getInstance();
        adapter.open();
        Cursor c = adapter.getFeedCursor(feed.getId());
        assertEquals(0, c.getCount());
        c.close();
        for (FeedItem item : feed.getItems()) {
            c = adapter.getFeedItemCursor(String.valueOf(item.getId()));
            assertEquals(0, c.getCount());
            c.close();
            c = adapter.getSingleFeedMediaCursor(item.getMedia().getId());
            assertEquals(0, c.getCount());
            c.close();
        }
        adapter.close();
    }

    @Test
    public void testDeleteFeedItems() throws Exception {
        Feed feed = new Feed("url", null, "title");
        feed.setItems(new ArrayList<>());
        feed.setImageUrl("url");

        // create items
        for (int i = 0; i < 10; i++) {
            FeedItem item = new FeedItem(0, "Item " + i, "Item" + i, "url", new Date(), FeedItem.PLAYED, feed);
            feed.getItems().add(item);
        }

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        List<FeedItem> itemsToDelete = feed.getItems().subList(0, 2);
        DBWriter.deleteFeedItems(context, itemsToDelete).get(TIMEOUT, TimeUnit.SECONDS);

        adapter = PodDBAdapter.getInstance();
        adapter.open();
        for (int i = 0; i < feed.getItems().size(); i++) {
            FeedItem feedItem = feed.getItems().get(i);
            Cursor c = adapter.getFeedItemCursor(String.valueOf(feedItem.getId()));
            if (i < 2) {
                assertEquals(0, c.getCount());
            } else {
                assertEquals(1, c.getCount());
            }
            c.close();
        }
        adapter.close();
    }

    private FeedMedia playbackHistorySetup(Date playbackCompletionDate) {
        Feed feed = new Feed("url", null, "title");
        feed.setItems(new ArrayList<>());
        FeedItem item = new FeedItem(0, "title", "id", "link", new Date(), FeedItem.PLAYED, feed);
        FeedMedia media = new FeedMedia(0, item, 10, 0, 1, "mime", null,
                "url", false, playbackCompletionDate, 0, 0);
        feed.getItems().add(item);
        item.setMedia(media);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();
        assertTrue(media.getId() != 0);
        return media;
    }

    @Test
    public void testAddItemToPlaybackHistoryNotPlayedYet() throws Exception {
        FeedMedia media = playbackHistorySetup(null);
        DBWriter.addItemToPlaybackHistory(media).get(TIMEOUT, TimeUnit.SECONDS);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        media = DBReader.getFeedMedia(media.getId());
        adapter.close();

        assertNotNull(media);
        assertNotNull(media.getPlaybackCompletionDate());
    }

    @Test
    public void testAddItemToPlaybackHistoryAlreadyPlayed() throws Exception {
        final long oldDate = 0;

        FeedMedia media = playbackHistorySetup(new Date(oldDate));
        DBWriter.addItemToPlaybackHistory(media).get(TIMEOUT, TimeUnit.SECONDS);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        media = DBReader.getFeedMedia(media.getId());
        adapter.close();

        assertNotNull(media);
        assertNotNull(media.getPlaybackCompletionDate());
        assertNotEquals(media.getPlaybackCompletionDate().getTime(), oldDate);
    }

    @SuppressWarnings("SameParameterValue")
    private Feed queueTestSetupMultipleItems(final int numItems) throws Exception {
        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.BACK);
        Feed feed = new Feed("url", null, "title");
        feed.setItems(new ArrayList<>());
        for (int i = 0; i < numItems; i++) {
            FeedItem item = new FeedItem(0, "title " + i, "id " + i, "link " + i, new Date(), FeedItem.PLAYED, feed);
            feed.getItems().add(item);
        }

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        for (FeedItem item : feed.getItems()) {
            assertTrue(item.getId() != 0);
        }
        List<Future<?>> futures = new ArrayList<>();
        for (FeedItem item : feed.getItems()) {
            futures.add(DBWriter.addQueueItem(context, item));
        }
        for (Future<?> f : futures) {
            f.get(TIMEOUT, TimeUnit.SECONDS);
        }
        return feed;
    }

    @Test
    public void testAddQueueItemSingleItem() throws Exception {
        Feed feed = new Feed("url", null, "title");
        feed.setItems(new ArrayList<>());
        FeedItem item = new FeedItem(0, "title", "id", "link", new Date(), FeedItem.PLAYED, feed);
        feed.getItems().add(item);

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        assertTrue(item.getId() != 0);
        DBWriter.addQueueItem(context, item).get(TIMEOUT, TimeUnit.SECONDS);

        adapter = PodDBAdapter.getInstance();
        adapter.open();
        Cursor cursor = adapter.getQueueIDCursor();
        assertTrue(cursor.moveToFirst());
        assertEquals(item.getId(), cursor.getLong(0));
        cursor.close();
        adapter.close();
    }

    @Test
    public void testAddQueueItemSingleItemAlreadyInQueue() throws Exception {
        Feed feed = new Feed("url", null, "title");
        feed.setItems(new ArrayList<>());
        FeedItem item = new FeedItem(0, "title", "id", "link", new Date(), FeedItem.PLAYED, feed);
        feed.getItems().add(item);

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        assertTrue(item.getId() != 0);
        DBWriter.addQueueItem(context, item).get(TIMEOUT, TimeUnit.SECONDS);

        adapter = PodDBAdapter.getInstance();
        adapter.open();
        Cursor cursor = adapter.getQueueIDCursor();
        assertTrue(cursor.moveToFirst());
        assertEquals(item.getId(), cursor.getLong(0));
        cursor.close();
        adapter.close();

        DBWriter.addQueueItem(context, item).get(TIMEOUT, TimeUnit.SECONDS);
        adapter = PodDBAdapter.getInstance();
        adapter.open();
        cursor = adapter.getQueueIDCursor();
        assertTrue(cursor.moveToFirst());
        assertEquals(item.getId(), cursor.getLong(0));
        assertEquals(1, cursor.getCount());
        cursor.close();
        adapter.close();
    }

    @Test
    public void testAddQueueItemMultipleItems() throws Exception {
        final int numItems = 10;

        Feed feed;
        feed = queueTestSetupMultipleItems(numItems);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        Cursor cursor = adapter.getQueueIDCursor();
        assertTrue(cursor.moveToFirst());
        assertEquals(numItems, cursor.getCount());
        List<Long> expectedIds;
        expectedIds = FeedItemUtil.getIdList(feed.getItems());
        List<Long> actualIds = new ArrayList<>();
        for (int i = 0; i < numItems; i++) {
            assertTrue(cursor.moveToPosition(i));
            actualIds.add(cursor.getLong(0));
        }
        cursor.close();
        adapter.close();
        assertEquals("Bulk add to queue: result order should be the same as the order given",
                expectedIds, actualIds);
    }

    @Test
    public void testClearQueue() throws Exception {
        final int numItems = 10;

        queueTestSetupMultipleItems(numItems);
        DBWriter.clearQueue().get(TIMEOUT, TimeUnit.SECONDS);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        Cursor cursor = adapter.getQueueIDCursor();
        assertFalse(cursor.moveToFirst());
        cursor.close();
        adapter.close();
    }

    @Test
    public void testRemoveQueueItem() throws Exception {
        final int numItems = 10;
        Feed feed = createTestFeed(numItems);

        for (int removeIndex = 0; removeIndex < numItems; removeIndex++) {
            final FeedItem item = feed.getItems().get(removeIndex);
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setQueue(feed.getItems());
            adapter.close();

            DBWriter.removeQueueItem(context, false, item).get(TIMEOUT, TimeUnit.SECONDS);
            adapter = PodDBAdapter.getInstance();
            adapter.open();
            Cursor queue = adapter.getQueueIDCursor();
            assertEquals(numItems - 1, queue.getCount());
            for (int i = 0; i < queue.getCount(); i++) {
                assertTrue(queue.moveToPosition(i));
                final long queueID = queue.getLong(0);
                assertTrue(queueID != item.getId());  // removed item is no longer in queue
                boolean idFound = false;
                for (FeedItem other : feed.getItems()) { // items that were not removed are still in the queue
                    idFound = idFound | (other.getId() == queueID);
                }
                assertTrue(idFound);
            }
            queue.close();
            adapter.close();
        }
    }

    @Test
    public void testRemoveQueueItemMultipleItems() throws Exception {
        final int numItems = 5;
        final int numInQueue = numItems - 1; // the last one not in queue for boundary condition
        Feed feed = createTestFeed(numItems);

        List<FeedItem> itemsToAdd = feed.getItems().subList(0, numInQueue);
        DbTestUtils.withPodDB(adapter -> adapter.setQueue(itemsToAdd));

        // Actual tests
        //

        // Use array rather than List to make codes more succinct
        Long[] itemIds = toItemIds(feed.getItems()).toArray(new Long[0]);

        DBWriter.removeQueueItem(context, false,
                itemIds[1], itemIds[3]).get(TIMEOUT, TimeUnit.SECONDS);
        assertQueueByItemIds("Average case - 2 items removed successfully",
                itemIds[0], itemIds[2]);

        DBWriter.removeQueueItem(context, false).get(TIMEOUT, TimeUnit.SECONDS);
        assertQueueByItemIds("Boundary case - no items supplied. queue should see no change",
                itemIds[0], itemIds[2]);

        DBWriter.removeQueueItem(context, false,
                itemIds[0], itemIds[4], -1L).get(TIMEOUT, TimeUnit.SECONDS);
        assertQueueByItemIds("Boundary case - items not in queue ignored",
                itemIds[2]);

        DBWriter.removeQueueItem(context, false,
                itemIds[2], -1L).get(TIMEOUT, TimeUnit.SECONDS);
        assertQueueByItemIds("Boundary case - invalid itemIds ignored"); // the queue is empty

    }

    @Test
    public void testMoveQueueItem() throws Exception {
        final int numItems = 10;
        Feed feed = new Feed("url", null, "title");
        feed.setItems(new ArrayList<>());
        for (int i = 0; i < numItems; i++) {
            FeedItem item = new FeedItem(0, "title " + i, "id " + i, "link " + i,
                    new Date(), FeedItem.PLAYED, feed);
            feed.getItems().add(item);
        }

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        for (FeedItem item : feed.getItems()) {
            assertTrue(item.getId() != 0);
        }
        for (int from = 0; from < numItems; from++) {
            for (int to = 0; to < numItems; to++) {
                if (from == to) {
                    continue;
                }
                Log.d(TAG, String.format(Locale.US, "testMoveQueueItem: From=%d, To=%d", from, to));
                final long fromID = feed.getItems().get(from).getId();

                adapter = PodDBAdapter.getInstance();
                adapter.open();
                adapter.setQueue(feed.getItems());
                adapter.close();

                DBWriter.moveQueueItem(from, to, false).get(TIMEOUT, TimeUnit.SECONDS);
                adapter = PodDBAdapter.getInstance();
                adapter.open();
                Cursor queue = adapter.getQueueIDCursor();
                assertEquals(numItems, queue.getCount());
                assertTrue(queue.moveToPosition(from));
                assertNotEquals(fromID, queue.getLong(0));
                assertTrue(queue.moveToPosition(to));
                assertEquals(fromID, queue.getLong(0));

                queue.close();
                adapter.close();
            }
        }
    }

    @Test
    public void testMarkFeedRead() throws Exception {
        final int numItems = 10;
        Feed feed = new Feed("url", null, "title");
        feed.setItems(new ArrayList<>());
        for (int i = 0; i < numItems; i++) {
            FeedItem item = new FeedItem(0, "title " + i, "id " + i, "link " + i,
                    new Date(), FeedItem.UNPLAYED, feed);
            feed.getItems().add(item);
        }

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        assertTrue(feed.getId() != 0);
        for (FeedItem item : feed.getItems()) {
            assertTrue(item.getId() != 0);
        }

        DBWriter.markFeedRead(feed.getId()).get(TIMEOUT, TimeUnit.SECONDS);
        List<FeedItem> loadedItems = DBReader.getFeedItemList(feed);
        for (FeedItem item : loadedItems) {
            assertTrue(item.isPlayed());
        }
    }

    @Test
    public void testMarkAllItemsReadSameFeed() throws Exception {
        final int numItems = 10;
        Feed feed = new Feed("url", null, "title");
        feed.setItems(new ArrayList<>());
        for (int i = 0; i < numItems; i++) {
            FeedItem item = new FeedItem(0, "title " + i, "id " + i, "link " + i,
                    new Date(), FeedItem.UNPLAYED, feed);
            feed.getItems().add(item);
        }

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        assertTrue(feed.getId() != 0);
        for (FeedItem item : feed.getItems()) {
            assertTrue(item.getId() != 0);
        }

        DBWriter.markAllItemsRead().get(TIMEOUT, TimeUnit.SECONDS);
        List<FeedItem> loadedItems = DBReader.getFeedItemList(feed);
        for (FeedItem item : loadedItems) {
            assertTrue(item.isPlayed());
        }
    }

    private static Feed createTestFeed(int numItems) {
        Feed feed = new Feed("url", null, "title");
        feed.setItems(new ArrayList<>());
        for (int i = 0; i < numItems; i++) {
            FeedItem item = new FeedItem(0, "title " + i, "id " + i, "link " + i,
                    new Date(), FeedItem.PLAYED, feed);
            feed.getItems().add(item);
        }

        DbTestUtils.withPodDB(adapter -> adapter.setCompleteFeed(feed));

        for (FeedItem item : feed.getItems()) {
            assertTrue(item.getId() != 0);
        }
        return feed;
    }

    private static void assertQueueByItemIds(String message, long... itemIdsExpected) {
        List<FeedItem> queue = DBReader.getQueue();
        List<Long> itemIdsActualList = toItemIds(queue);
        List<Long> itemIdsExpectedList = new ArrayList<>(itemIdsExpected.length);
        for (long id : itemIdsExpected) {
            itemIdsExpectedList.add(id);
        }

        assertEquals(message, itemIdsExpectedList, itemIdsActualList);
    }

    private static List<Long> toItemIds(List<FeedItem> items) {
        List<Long> itemIds = new ArrayList<>(items.size());
        for (FeedItem item : items) {
            itemIds.add(item.getId());
        }
        return itemIds;
    }

    @Test
    public void testSetNote() throws Exception {
        Feed feed = new Feed("url", null, "title");
        List<FeedItem> items = new ArrayList<>();
        feed.setItems(items);
        FeedItem item = new FeedItem(0, "Title with note", "id1", "link1",
                new Date(), FeedItem.PLAYED, feed);
        Note note = new Note();
        note.setNotes("My awesome note");
        item.setNote(note);
        feed.getItems().add(item);

        DbTestUtils.withPodDB(adapter -> adapter.setNote(item));

        Note noteFromDb = DBReader.getNoteForEpisode(item);
        assertNotNull(noteFromDb);
        assertEquals(note.getNotes(), noteFromDb.getNotes());
    }
}
