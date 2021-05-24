package de.danoeh.antennapod.net.downloadservice;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DownloadStateProvider;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedFile;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.util.FileNameGenerator;


/**
 * Sends download requests to the DownloadService. This class should always be used for starting downloads,
 * otherwise they won't work correctly.
 */
public class DownloadRequester implements DownloadStateProvider {
    private static final String TAG = "DownloadRequester";

    private static final String FEED_DOWNLOADPATH = "cache/";
    private static final String MEDIA_DOWNLOADPATH = "media/";

    /**
     * Denotes the page of the feed that is contained in the DownloadRequest sent by the DownloadRequester.
     */
    public static final String REQUEST_ARG_PAGE_NR = "page";

    /**
     * True if all pages after the feed that is contained in this DownloadRequest should be downloaded.
     */
    public static final String REQUEST_ARG_LOAD_ALL_PAGES = "loadAllPages";

    private static DownloadRequester downloader;

    private DownloadRequester() {

    }

    public static synchronized DownloadRequester getInstance() {
        if (downloader == null) {
            downloader = new DownloadRequester();
        }
        return downloader;
    }

    @Nullable
    public DownloadRequest createRequest(FeedFile item, FeedFile container, File dest, boolean overwriteIfExists,
                                          String username, String password, String lastModified,
                                          boolean deleteOnFailure, Bundle arguments, boolean initiatedByUser) {
        final boolean partiallyDownloadedFileExists = item.getFile_url() != null && new File(item.getFile_url()).exists();

        Log.d(TAG, "partiallyDownloadedFileExists: " + partiallyDownloadedFileExists);
        if (isDownloadingFile(item)) {
            Log.e(TAG, "URL " + item.getDownload_url()
                    + " is already being downloaded");
            return null;
        }
        if (!isFilenameAvailable(dest.toString()) || (!partiallyDownloadedFileExists && dest.exists())) {
            Log.d(TAG, "Filename already used.");
            if (isFilenameAvailable(dest.toString()) && overwriteIfExists) {
                boolean result = dest.delete();
                Log.d(TAG, "Deleting file. Result: " + result);
            } else {
                // find different name
                File newDest = null;
                for (int i = 1; i < Integer.MAX_VALUE; i++) {
                    String newName = FilenameUtils.getBaseName(dest
                            .getName())
                            + "-"
                            + i
                            + FilenameUtils.EXTENSION_SEPARATOR
                            + FilenameUtils.getExtension(dest.getName());
                    Log.d(TAG, "Testing filename " + newName);
                    newDest = new File(dest.getParent(), newName);
                    if (!newDest.exists()
                            && isFilenameAvailable(newDest.toString())) {
                        Log.d(TAG, "File doesn't exist yet. Using " + newName);
                        break;
                    }
                }
                if (newDest != null) {
                    dest = newDest;
                }
            }
        }
        Log.d(TAG, "Requesting download of url " + item.getDownload_url());
        String baseUrl = (container != null) ? container.getDownload_url() : null;
        item.setDownload_url(URLChecker.prepareURL(item.getDownload_url(), baseUrl));

        DownloadRequest.Builder builder = new DownloadRequest.Builder(dest.toString(), item, initiatedByUser)
                .withAuthentication(username, password)
                .lastModified(lastModified)
                .deleteOnFailure(deleteOnFailure)
                .withArguments(arguments);
        return builder.build();
    }

    /**
     * Returns true if a filename is available and false if it has already been
     * taken by another requested download.
     */
    private boolean isFilenameAvailable(String path) {
        return true;/*
        for (String key : downloads.keySet()) {
            DownloadRequest r = downloads.get(key);
            if (TextUtils.equals(r.getDestination(), path)) {
                if (BuildConfig.DEBUG)
                    Log.d(TAG, path
                            + " is already used by another requested download");
                return false;
            }
        }
        if (BuildConfig.DEBUG)
            Log.d(TAG, path + " is available as a download destination");
        return true;*/
    }

    public void refreshAllFeeds(Context context) {
        new Thread(() -> {
            List<Feed> feeds = DBReader.getFeedList();
            for (Feed feed : feeds) {
                try {
                    DownloadWorker.enqueue(context, createRequest(feed, false, false, false));
                } catch (DownloadRequestException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }


    /**
     * Downloads a list of feeds.
     *
     * @param loadAllPages Set to true to download all pages
     */
    public synchronized DownloadRequest createRequest(Feed feed, boolean loadAllPages,
                                          boolean force, boolean initiatedByUser) throws DownloadRequestException {
        if (!feedFileValid(feed)) {
            return null;
        }
        String username = (feed.getPreferences() != null) ? feed.getPreferences().getUsername() : null;
        String password = (feed.getPreferences() != null) ? feed.getPreferences().getPassword() : null;
        String lastModified = feed.isPaged() || force ? null : feed.getLastUpdate();

        Bundle args = new Bundle();
        args.putInt(REQUEST_ARG_PAGE_NR, feed.getPageNr());
        args.putBoolean(REQUEST_ARG_LOAD_ALL_PAGES, loadAllPages);

        return createRequest(feed, null, new File(getFeedfilePath(), getFeedfileName(feed)),
                true, username, password, lastModified, true, args, initiatedByUser);
    }

    public synchronized void downloadMedia(@NonNull Context context, boolean initiatedByUser, FeedItem... feedItems)
            throws DownloadRequestException {
        downloadMedia(true, context, initiatedByUser, feedItems);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public synchronized void downloadMedia(boolean performAutoCleanup, @NonNull Context context,
                                           boolean initiatedByUser, FeedItem... items)
            throws DownloadRequestException {
        Log.d(TAG, "downloadMedia() called with: performAutoCleanup = [" + performAutoCleanup
                + "], #items = [" + items.length + "]");

        /*List<DownloadRequest> requests = new ArrayList<>(items.length);
        for (FeedItem item : items) {
            try {
                DownloadRequest request = createRequest(item.getMedia(), initiatedByUser);
                if (request != null) {
                    requests.add(request);
                }
            } catch (DownloadRequestException e) {
                if (items.length < 2) {
                    // single download, typically initiated from users
                    throw e;
                } else {
                    // batch download, typically initiated by auto-download in the background
                    e.printStackTrace();
                    DBWriter.addDownloadStatus(
                            new DownloadStatus(item.getMedia(), item
                                    .getMedia()
                                    .getHumanReadableIdentifier(),
                                    DownloadError.ERROR_REQUEST_ERROR,
                                    false, e.getMessage(), initiatedByUser
                            )
                    );
                }
            }
        }
        download(context, performAutoCleanup, requests.toArray(new DownloadRequest[0]));*/
    }

    @Nullable
    public DownloadRequest createRequest(@Nullable FeedMedia feedmedia, boolean initiatedByUser)
            throws DownloadRequestException {
        if (!feedFileValid(feedmedia)) {
            return null;
        }
        Feed feed = feedmedia.getItem().getFeed();
        String username;
        String password;
        if (feed != null && feed.getPreferences() != null) {
            username = feed.getPreferences().getUsername();
            password = feed.getPreferences().getPassword();
        } else {
            username = null;
            password = null;
        }

        File dest;
        if (feedmedia.getFile_url() != null && new File(feedmedia.getFile_url()).exists()) {
            dest = new File(feedmedia.getFile_url());
        } else {
            dest = new File(getMediafilePath(feedmedia), getMediafilename(feedmedia));
        }
        return createRequest(feedmedia, feed, dest, false, username, password, null, false, null, initiatedByUser);
    }

    /**
     * Throws a DownloadRequestException if the feedfile or the download url of
     * the feedfile is null.
     *
     * @throws DownloadRequestException
     */
    private boolean feedFileValid(FeedFile f) throws DownloadRequestException {
        if (f == null) {
            throw new DownloadRequestException("Feedfile was null");
        } else if (f.getDownload_url() == null) {
            throw new DownloadRequestException("File has no download URL");
        } else {
            return true;
        }
    }

    /**
     * Cancels a running download.
     */
    public synchronized void cancelDownload(final Context context, final FeedFile f) {
        cancelDownload(context, f.getDownload_url());
    }

    /**
     * Cancels a running download.
     */
    public synchronized void cancelDownload(final Context context, final String downloadUrl) {
    }

    /**
     * Cancels all running downloads
     */
    public synchronized void cancelAllDownloads(Context context) {
    }

    /**
     * Returns true if there is at least one Feed in the downloads queue.
     */
    public synchronized boolean isDownloadingFeeds() {
        return false;
    }

    /**
     * Checks if feedfile is in the downloads list
     */
    public synchronized boolean isDownloadingFile(@NonNull FeedFile item) {
        return false;
    }

    /**
     * Get the downloader for this item.
     */
    public synchronized String /*DownloadRequest*/ getRequestFor(FeedFile item) {
        return null;
    }

    /**
     * Checks if feedfile with the given download url is in the downloads list
     */
    public synchronized boolean isDownloadingFile(String downloadUrl) {
        return false;
    }

    public synchronized boolean hasNoDownloads() {
        return false;
    }

    /**
     * Get the number of uncompleted Downloads
     */
    public synchronized int getNumberOfDownloads() {
        return 0;
    }

    private synchronized String getFeedfilePath() throws DownloadRequestException {
        return getExternalFilesDirOrThrowException(FEED_DOWNLOADPATH).toString() + "/";
    }

    private synchronized String getFeedfileName(Feed feed) {
        String filename = feed.getDownload_url();
        if (feed.getTitle() != null && !feed.getTitle().isEmpty()) {
            filename = feed.getTitle();
        }
        return "feed-" + FileNameGenerator.generateFileName(filename);
    }

    private synchronized String getMediafilePath(FeedMedia media) throws DownloadRequestException {
        File externalStorage = getExternalFilesDirOrThrowException(
                MEDIA_DOWNLOADPATH
                        + FileNameGenerator.generateFileName(media.getItem()
                        .getFeed().getTitle()) + "/"
        );
        return externalStorage.toString();
    }

    private File getExternalFilesDirOrThrowException(String type) throws DownloadRequestException {
        File result = UserPreferences.getDataFolder(type);
        if (result == null) {
            throw new DownloadRequestException(
                    "Failed to access external storage");
        }
        return result;
    }

    private String getMediafilename(FeedMedia media) {
        String filename;
        String titleBaseFilename = "";

        // Try to generate the filename by the item title
        if (media.getItem() != null && media.getItem().getTitle() != null) {
            String title = media.getItem().getTitle();
            titleBaseFilename = FileNameGenerator.generateFileName(title);
        }

        String URLBaseFilename = URLUtil.guessFileName(media.getDownload_url(),
                null, media.getMime_type());

        if (!titleBaseFilename.equals("")) {
            // Append extension
            final int FILENAME_MAX_LENGTH = 220;
            if (titleBaseFilename.length() > FILENAME_MAX_LENGTH) {
                titleBaseFilename = titleBaseFilename.substring(0, FILENAME_MAX_LENGTH);
            }
            filename = titleBaseFilename + FilenameUtils.EXTENSION_SEPARATOR +
                    FilenameUtils.getExtension(URLBaseFilename);
        } else {
            // Fall back on URL file name
            filename = URLBaseFilename;
        }
        return filename;
    }
}
