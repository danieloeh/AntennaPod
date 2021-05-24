package de.danoeh.antennapod.core.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.List;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.core.preferences.UserPreferences.EnqueueLocation;
import de.danoeh.antennapod.model.playback.Playable;

/**
 * @see DBWriter#addQueueItem(Context, boolean, long...) it uses the class to determine
 * the positions of the {@link FeedItem} in the queue.
 */
class ItemEnqueuePositionCalculator {

    @NonNull
    private final EnqueueLocation enqueueLocation;

    //@VisibleForTesting
    //DownloadStateProvider downloadStateProvider = DownloadRequester.getInstance();

    public ItemEnqueuePositionCalculator(@NonNull EnqueueLocation enqueueLocation) {
        this.enqueueLocation = enqueueLocation;
    }

    /**
     * Determine the position (0-based) that the item(s) should be inserted to the named queue.
     *
     * @param curQueue           the queue to which the item is to be inserted
     * @param currentPlaying     the currently playing media
     */
    public int calcPosition(@NonNull List<FeedItem> curQueue, @Nullable Playable currentPlaying) {
        switch (enqueueLocation) {
            case BACK:
                return curQueue.size();
            case FRONT:
                // Return not necessarily 0, so that when a list of items are downloaded and enqueued
                // in succession of calls (e.g., users manually tapping download one by one),
                // the items enqueued are kept the same order.
                // Simply returning 0 will reverse the order.
                return getPositionOfFirstNonDownloadingItem(0, curQueue);
            case AFTER_CURRENTLY_PLAYING:
                int currentlyPlayingPosition = getCurrentlyPlayingPosition(curQueue, currentPlaying);
                return getPositionOfFirstNonDownloadingItem(
                        currentlyPlayingPosition + 1, curQueue);
            default:
                throw new AssertionError("calcPosition() : unrecognized enqueueLocation option: " + enqueueLocation);
        }
    }

    private int getPositionOfFirstNonDownloadingItem(int startPosition, List<FeedItem> curQueue) {
        final int curQueueSize = curQueue.size();
        for (int i = startPosition; i < curQueueSize; i++) {
            if (!isItemAtPositionDownloading(i, curQueue)) {
                return i;
            } // else continue to search;
        }
        return curQueueSize;
    }

    private boolean isItemAtPositionDownloading(int position, List<FeedItem> curQueue) {
        FeedItem curItem;
        try {
            curItem = curQueue.get(position);
        } catch (IndexOutOfBoundsException e) {
            curItem = null;
        }

        if (curItem != null
                && curItem.getMedia() != null
                /*&& downloadStateProvider.isDownloadingFile(curItem.getMedia())*/) {
            return true;
        } else {
            return false;
        }
    }

    private static int getCurrentlyPlayingPosition(@NonNull List<FeedItem> curQueue,
                                                   @Nullable Playable currentPlaying) {
        if (!(currentPlaying instanceof FeedMedia)) {
            return -1;
        }
        final long curPlayingItemId = ((FeedMedia) currentPlaying).getItem().getId();
        for (int i = 0; i < curQueue.size(); i++) {
            if (curPlayingItemId == curQueue.get(i).getId()) {
                return i;
            }
        }
        return -1;
    }
}
