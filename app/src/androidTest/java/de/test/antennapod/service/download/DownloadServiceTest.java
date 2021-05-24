package de.test.antennapod.service.download;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import de.test.antennapod.EspressoTestUtils;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.net.downloadservice.DownloadRequest;
import de.danoeh.antennapod.core.service.download.DownloadStatus;
import de.danoeh.antennapod.net.downloadservice.Downloader;
import de.danoeh.antennapod.net.downloadservice.DownloaderFactory;
import de.danoeh.antennapod.core.service.download.StubDownloader;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.net.downloadservice.DownloadRequester;

import static de.test.antennapod.util.event.DownloadEventListener.withDownloadEventListener;
import static de.test.antennapod.util.event.FeedItemEventListener.withFeedItemEventListener;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @see HttpDownloaderTest for the test of actual download (and saving the file).
 */
@RunWith(AndroidJUnit4.class)
public class DownloadServiceTest {
    private FeedMedia testMedia11 = null;

    private DownloaderFactory origFactory = null;

    /*@Before
    public void setUp() throws Exception {
        EspressoTestUtils.clearDatabase();
        EspressoTestUtils.clearPreferences();
        origFactory = DownloadService.getDownloaderFactory();
        Feed testFeed = setUpTestFeeds();
        testMedia11 = testFeed.getItemAtIndex(0).getMedia();
    }

    private Feed setUpTestFeeds() throws Exception {
        // To avoid complication in case of test failures, leaving behind orphaned
        // media files: add a timestamp so that each test run will have its own directory for media files.
        Feed feed = new Feed("url", null, "Test Feed title 1 " + System.currentTimeMillis());
        List<FeedItem> items = new ArrayList<>();
        feed.setItems(items);
        FeedItem item1 = new FeedItem(0, "Item 1-1", "Item 1-1", "url", new Date(), FeedItem.NEW, feed);
        items.add(item1);
        FeedMedia media1 = new FeedMedia(0, item1, 123, 1, 1, "audio/mp3", null, "http://example.com/episode.mp3", false, null, 0, 0);
        item1.setMedia(media1);

        DBWriter.setFeedItem(item1).get();
        return feed;
    }


    @After
    public void tearDown() throws Exception {
        DownloadService.setDownloaderFactory(origFactory);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DownloadRequester.getInstance().cancelAllDownloads(context);
        context.stopService(new Intent(context, DownloadService.class));
        EspressoTestUtils.tryKillDownloadService();
    }

    @Test
    public void testEventsGeneratedCaseMediaDownloadSuccess_noEnqueue() throws Exception {
        doTestEventsGeneratedCaseMediaDownloadSuccess(false, 1);
    }

    @Test
    public void testEventsGeneratedCaseMediaDownloadSuccess_withEnqueue() throws Exception {
        // enqueue itself generates additional FeedItem event
        doTestEventsGeneratedCaseMediaDownloadSuccess(true, 2);
    }

    private void doTestEventsGeneratedCaseMediaDownloadSuccess(boolean enqueueDownloaded,
                                                               int numEventsExpected)
            throws Exception {
        // create a stub download that returns successful
        //
        // OPEN: Ideally, I'd like the download time long enough so that multiple in-progress DownloadEvents
        // are generated (to simulate typical download), but it'll make download time quite long (1-2 seconds)
        // to do so
        DownloadService.setDownloaderFactory(new StubDownloaderFactory(50, DownloadStatus::setSuccessful));

        UserPreferences.setEnqueueDownloadedEpisodes(enqueueDownloaded);
        withFeedItemEventListener(feedItemEventListener -> {
            try {
                assertEquals(0, feedItemEventListener.getEvents().size());
                assertFalse("The media in test should not yet been downloaded",
                        DBReader.getFeedMedia(testMedia11.getId()).isDownloaded());

                DownloadRequester.getInstance().downloadMedia(false, InstrumentationRegistry
                        .getInstrumentation().getTargetContext(), true, testMedia11.getItem());
                Awaitility.await()
                        .atMost(5000, TimeUnit.MILLISECONDS)
                        .until(() -> feedItemEventListener.getEvents().size() >= numEventsExpected);
                assertTrue("After media download has completed, FeedMedia object in db should indicate so.",
                        DBReader.getFeedMedia(testMedia11.getId()).isDownloaded());
                assertEquals("The FeedItem should have been " + (enqueueDownloaded ? "" : "not ") +  "enqueued",
                        enqueueDownloaded,
                        DBReader.getQueueIDList().contains(testMedia11.getItem().getId()));
            } catch (ConditionTimeoutException cte) {
                fail("The expected FeedItemEvent (for media download complete) has not been posted. "
                        + cte.getMessage());
            }
        });
    }

    @Test
    public void testCancelDownload_UndoEnqueue_Normal() throws Exception {
        doTestCancelDownload_UndoEnqueue(false);
    }

    @Test
    public void testCancelDownload_UndoEnqueue_AlreadyInQueue() throws Exception {
        doTestCancelDownload_UndoEnqueue(true);
    }

    private void doTestCancelDownload_UndoEnqueue(boolean itemAlreadyInQueue) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // let download take longer to ensure the test can cancel the download in time
        DownloadService.setDownloaderFactory(
                new StubDownloaderFactory(30000, DownloadStatus::setSuccessful));
        UserPreferences.setEnqueueDownloadedEpisodes(true);
        UserPreferences.setEnableAutodownload(false);

        final long item1Id = testMedia11.getItem().getId();
        if (itemAlreadyInQueue) {
            // simulate item already in queue condition
            DBWriter.addQueueItem(context, false, item1Id).get();
            assertTrue(DBReader.getQueueIDList().contains(item1Id));
        } else {
            assertFalse(DBReader.getQueueIDList().contains(item1Id));
        }

        withFeedItemEventListener(feedItemEventListener -> {
            DownloadRequester.getInstance().downloadMedia(false, context, true, testMedia11.getItem());
            withDownloadEventListener(downloadEventListener ->
                    Awaitility.await("download is actually running")
                        .atMost(5000, TimeUnit.MILLISECONDS)
                        .until(() -> downloadEventListener.getLatestEvent() != null
                                && downloadEventListener.getLatestEvent().update.mediaIds.length > 0
                                && downloadEventListener.getLatestEvent().update.mediaIds[0] == testMedia11.getId()));

            if (itemAlreadyInQueue) {
                assertEquals("download service receives the request - no event is expected before cancel is issued",
                        0, feedItemEventListener.getEvents().size());
            } else {
                Awaitility.await("item enqueue event")
                        .atMost(2000, TimeUnit.MILLISECONDS)
                        .until(() -> feedItemEventListener.getEvents().size() >= 1);
            }
            DownloadRequester.getInstance().cancelDownload(context, testMedia11);
            final int totalNumEventsExpected = itemAlreadyInQueue ? 1 : 3;
            Awaitility.await("item dequeue event + download termination event")
                    .atMost(2000, TimeUnit.MILLISECONDS)
                    .until(() -> feedItemEventListener.getEvents().size() >= totalNumEventsExpected);
            assertFalse("The download should have been canceled",
                    DBReader.getFeedMedia(testMedia11.getId()).isDownloaded());
            if (itemAlreadyInQueue) {
                assertTrue("The FeedItem should still be in the queue after the download is cancelled."
                                + " It's there before download.",
                        DBReader.getQueueIDList().contains(item1Id));
            } else {
                assertFalse("The FeedItem should not be in the queue after the download is cancelled.",
                        DBReader.getQueueIDList().contains(item1Id));
            }
        });
    }

    private static class StubDownloaderFactory implements DownloaderFactory {
        private final long downloadTime;

        @NonNull
        private final Consumer<DownloadStatus> onDownloadComplete;

        StubDownloaderFactory(long downloadTime, @NonNull Consumer<DownloadStatus> onDownloadComplete) {
            this.downloadTime = downloadTime;
            this.onDownloadComplete = onDownloadComplete;
        }

        @Nullable
        @Override
        public Downloader create(@NonNull DownloadRequest request) {
            return new StubDownloader(request, downloadTime, onDownloadComplete);
        }
    }
*/
}
