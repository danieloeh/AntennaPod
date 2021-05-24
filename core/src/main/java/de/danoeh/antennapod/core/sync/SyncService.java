package de.danoeh.antennapod.core.sync;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import androidx.core.app.NotificationCompat;
import androidx.core.util.Pair;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import de.danoeh.antennapod.core.R;
import de.danoeh.antennapod.core.event.SyncServiceEvent;
import de.danoeh.antennapod.core.util.UrlComparer;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.core.preferences.GpodnetPreferences;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.download.AntennapodHttpClient;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DBTasks;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.util.FeedItemUtil;
import de.danoeh.antennapod.core.util.LongList;
import de.danoeh.antennapod.core.util.gui.NotificationUtils;
import de.danoeh.antennapod.net.sync.gpoddernet.GpodnetService;
import de.danoeh.antennapod.net.sync.model.EpisodeAction;
import de.danoeh.antennapod.net.sync.model.EpisodeActionChanges;
import de.danoeh.antennapod.net.sync.model.ISyncService;
import de.danoeh.antennapod.net.sync.model.SubscriptionChanges;
import de.danoeh.antennapod.net.sync.model.SyncServiceException;
import de.danoeh.antennapod.net.sync.model.UploadChangesResponse;
import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;
import org.apache.commons.lang3.StringUtils;
import org.greenrobot.eventbus.EventBus;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class SyncService extends Worker {
    private static final String PREF_NAME = "SyncService";
    private static final String PREF_LAST_SUBSCRIPTION_SYNC_TIMESTAMP = "last_sync_timestamp";
    private static final String PREF_LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP = "last_episode_actions_sync_timestamp";
    private static final String PREF_QUEUED_FEEDS_ADDED = "sync_added";
    private static final String PREF_QUEUED_FEEDS_REMOVED = "sync_removed";
    private static final String PREF_QUEUED_EPISODE_ACTIONS = "sync_queued_episode_actions";
    private static final String PREF_LAST_SYNC_ATTEMPT_TIMESTAMP = "last_sync_attempt_timestamp";
    private static final String PREF_LAST_SYNC_ATTEMPT_SUCCESS = "last_sync_attempt_success";
    private static final String TAG = "SyncService";
    private static final String WORK_ID_SYNC = "SyncServiceWorkId";
    private static final ReentrantLock lock = new ReentrantLock();

    private ISyncService syncServiceImpl;

    public SyncService(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @Override
    @NonNull
    public Result doWork() {
        if (!GpodnetPreferences.loggedIn()) {
            return Result.success();
        }
        syncServiceImpl = new GpodnetService(AntennapodHttpClient.getHttpClient(),
                GpodnetPreferences.getHosturl(), GpodnetPreferences.getDeviceID(),
                GpodnetPreferences.getUsername(), GpodnetPreferences.getPassword());
        SharedPreferences.Editor prefs = getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit();
        prefs.putLong(PREF_LAST_SYNC_ATTEMPT_TIMESTAMP, System.currentTimeMillis()).apply();
        try {
            syncServiceImpl.login();
            EventBus.getDefault().postSticky(new SyncServiceEvent(R.string.sync_status_subscriptions));
            syncSubscriptions();
            syncEpisodeActions();
            syncServiceImpl.logout();
            clearErrorNotifications();
            EventBus.getDefault().postSticky(new SyncServiceEvent(R.string.sync_status_success));
            prefs.putBoolean(PREF_LAST_SYNC_ATTEMPT_SUCCESS, true).apply();
            return Result.success();
        } catch (SyncServiceException e) {
            EventBus.getDefault().postSticky(new SyncServiceEvent(R.string.sync_status_error));
            prefs.putBoolean(PREF_LAST_SYNC_ATTEMPT_SUCCESS, false).apply();
            Log.e(TAG, Log.getStackTraceString(e));
            if (getRunAttemptCount() % 3 == 2) {
                // Do not spam users with notification and retry before notifying
                updateErrorNotification(e);
            }
            return Result.retry();
        }
    }

    public static void clearQueue(Context context) {
        executeLockedAsync(() ->
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                    .putLong(PREF_LAST_SUBSCRIPTION_SYNC_TIMESTAMP, 0)
                    .putLong(PREF_LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP, 0)
                    .putLong(PREF_LAST_SYNC_ATTEMPT_TIMESTAMP, 0)
                    .putString(PREF_QUEUED_EPISODE_ACTIONS, "[]")
                    .putString(PREF_QUEUED_FEEDS_ADDED, "[]")
                    .putString(PREF_QUEUED_FEEDS_REMOVED, "[]")
                    .apply());
    }

    public static void enqueueFeedAdded(Context context, String downloadUrl) {
        if (!GpodnetPreferences.loggedIn()) {
            return;
        }
        executeLockedAsync(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                String json = prefs.getString(PREF_QUEUED_FEEDS_ADDED, "[]");
                JSONArray queue = new JSONArray(json);
                queue.put(downloadUrl);
                prefs.edit().putString(PREF_QUEUED_FEEDS_ADDED, queue.toString()).apply();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            sync(context);
        });
    }

    public static void enqueueFeedRemoved(Context context, String downloadUrl) {
        if (!GpodnetPreferences.loggedIn()) {
            return;
        }
        executeLockedAsync(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                String json = prefs.getString(PREF_QUEUED_FEEDS_REMOVED, "[]");
                JSONArray queue = new JSONArray(json);
                queue.put(downloadUrl);
                prefs.edit().putString(PREF_QUEUED_FEEDS_REMOVED, queue.toString()).apply();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            sync(context);
        });
    }

    public static void enqueueEpisodeAction(Context context, EpisodeAction action) {
        if (!GpodnetPreferences.loggedIn()) {
            return;
        }
        executeLockedAsync(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                String json = prefs.getString(PREF_QUEUED_EPISODE_ACTIONS, "[]");
                JSONArray queue = new JSONArray(json);
                queue.put(action.writeToJsonObject());
                prefs.edit().putString(PREF_QUEUED_EPISODE_ACTIONS, queue.toString()).apply();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            sync(context);
        });
    }

    public static void enqueueEpisodePlayed(Context context, FeedMedia media, boolean completed) {
        if (!GpodnetPreferences.loggedIn()) {
            return;
        }
        if (media.getItem() == null) {
            return;
        }
        if (media.getStartPosition() < 0 || (!completed && media.getStartPosition() >= media.getPosition())) {
            return;
        }
        EpisodeAction action = new EpisodeAction.Builder(media.getItem(), EpisodeAction.PLAY)
                .currentTimestamp()
                .started(media.getStartPosition() / 1000)
                .position((completed ? media.getDuration() : media.getPosition()) / 1000)
                .total(media.getDuration() / 1000)
                .build();
        SyncService.enqueueEpisodeAction(context, action);
    }

    public static void sync(Context context) {
        OneTimeWorkRequest workRequest = getWorkRequest().build();
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_ID_SYNC, ExistingWorkPolicy.REPLACE, workRequest);
        EventBus.getDefault().postSticky(new SyncServiceEvent(R.string.sync_status_started));
    }

    public static void syncImmediately(Context context) {
        OneTimeWorkRequest workRequest = getWorkRequest()
                .setInitialDelay(0L, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_ID_SYNC, ExistingWorkPolicy.REPLACE, workRequest);
        EventBus.getDefault().postSticky(new SyncServiceEvent(R.string.sync_status_started));
    }

    public static void fullSync(Context context) {
        executeLockedAsync(() -> {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                    .putLong(PREF_LAST_SUBSCRIPTION_SYNC_TIMESTAMP, 0)
                    .putLong(PREF_LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP, 0)
                    .putLong(PREF_LAST_SYNC_ATTEMPT_TIMESTAMP, 0)
                    .apply();

            OneTimeWorkRequest workRequest = getWorkRequest()
                    .setInitialDelay(0L, TimeUnit.SECONDS)
                    .build();
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_ID_SYNC, ExistingWorkPolicy.REPLACE, workRequest);
            EventBus.getDefault().postSticky(new SyncServiceEvent(R.string.sync_status_started));
        });
    }

    private static OneTimeWorkRequest.Builder getWorkRequest() {
        Constraints.Builder constraints = new Constraints.Builder();
        if (UserPreferences.isAllowMobileFeedRefresh()) {
            constraints.setRequiredNetworkType(NetworkType.CONNECTED);
        } else {
            constraints.setRequiredNetworkType(NetworkType.UNMETERED);
        }

        return new OneTimeWorkRequest.Builder(SyncService.class)
                .setConstraints(constraints.build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .setInitialDelay(5L, TimeUnit.SECONDS); // Give it some time, so other actions can be queued
    }

    /**
     * Take the lock and execute runnable (to prevent changes to preferences being lost when enqueueing while sync is
     * in progress). If the lock is free, the runnable is directly executed in the calling thread to prevent overhead.
     */
    private static void executeLockedAsync(Runnable runnable) {
        if (lock.tryLock()) {
            try {
                runnable.run();
            } finally {
                lock.unlock();
            }
        } else {
            Completable.fromRunnable(() -> {
                lock.lock();
                try {
                    runnable.run();
                } finally {
                    lock.unlock();
                }
            }).subscribeOn(Schedulers.io())
                .subscribe();
        }
    }

    public static boolean isLastSyncSuccessful(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_LAST_SYNC_ATTEMPT_SUCCESS, false);
    }

    public static long getLastSyncAttempt(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getLong(PREF_LAST_SYNC_ATTEMPT_TIMESTAMP, 0);
    }

    private List<EpisodeAction> getQueuedEpisodeActions() {
        ArrayList<EpisodeAction> actions = new ArrayList<>();
        try {
            SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(PREF_QUEUED_EPISODE_ACTIONS, "[]");
            JSONArray queue = new JSONArray(json);
            for (int i = 0; i < queue.length(); i++) {
                actions.add(EpisodeAction.readFromJsonObject(queue.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return actions;
    }

    private List<String> getQueuedRemovedFeeds() {
        ArrayList<String> actions = new ArrayList<>();
        try {
            SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(PREF_QUEUED_FEEDS_REMOVED, "[]");
            JSONArray queue = new JSONArray(json);
            for (int i = 0; i < queue.length(); i++) {
                actions.add(queue.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return actions;
    }

    private List<String> getQueuedAddedFeeds() {
        ArrayList<String> actions = new ArrayList<>();
        try {
            SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(PREF_QUEUED_FEEDS_ADDED, "[]");
            JSONArray queue = new JSONArray(json);
            for (int i = 0; i < queue.length(); i++) {
                actions.add(queue.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return actions;
    }

    private void syncSubscriptions() throws SyncServiceException {
        final long lastSync = getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getLong(PREF_LAST_SUBSCRIPTION_SYNC_TIMESTAMP, 0);
        final List<String> localSubscriptions = DBReader.getFeedListDownloadUrls();
        SubscriptionChanges subscriptionChanges = syncServiceImpl.getSubscriptionChanges(lastSync);
        long newTimeStamp = subscriptionChanges.getTimestamp();

        List<String> queuedRemovedFeeds = getQueuedRemovedFeeds();
        List<String> queuedAddedFeeds = getQueuedAddedFeeds();

        Log.d(TAG, "Downloaded subscription changes: " + subscriptionChanges);
        for (String downloadUrl : subscriptionChanges.getAdded()) {
            if (!UrlComparer.containsUrl(localSubscriptions, downloadUrl)
                    && !queuedRemovedFeeds.contains(downloadUrl)) {
                Feed feed = new Feed(downloadUrl, null);
                /*try {
                    DownloadRequester.getInstance().downloadFeed(getApplicationContext(), feed);
                } catch (DownloadRequestException e) {
                    e.printStackTrace();
                }*/
            }
        }

        // remove subscription if not just subscribed (again)
        for (String downloadUrl : subscriptionChanges.getRemoved()) {
            if (!queuedAddedFeeds.contains(downloadUrl)) {
                DBTasks.removeFeedWithDownloadUrl(getApplicationContext(), downloadUrl);
            }
        }

        if (lastSync == 0) {
            Log.d(TAG, "First sync. Adding all local subscriptions.");
            queuedAddedFeeds = localSubscriptions;
            queuedAddedFeeds.removeAll(subscriptionChanges.getAdded());
            queuedRemovedFeeds.removeAll(subscriptionChanges.getRemoved());
        }

        if (queuedAddedFeeds.size() > 0 || queuedRemovedFeeds.size() > 0) {
            Log.d(TAG, "Added: " + StringUtils.join(queuedAddedFeeds, ", "));
            Log.d(TAG, "Removed: " + StringUtils.join(queuedRemovedFeeds, ", "));

            lock.lock();
            try {
                UploadChangesResponse uploadResponse = syncServiceImpl
                        .uploadSubscriptionChanges(queuedAddedFeeds, queuedRemovedFeeds);
                getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                        .putString(PREF_QUEUED_FEEDS_ADDED, "[]").apply();
                getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                        .putString(PREF_QUEUED_FEEDS_REMOVED, "[]").apply();
                newTimeStamp = uploadResponse.timestamp;
            } finally {
                lock.unlock();
            }
        }
        getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putLong(PREF_LAST_SUBSCRIPTION_SYNC_TIMESTAMP, newTimeStamp).apply();
    }

    private void syncEpisodeActions() throws SyncServiceException {
        final long lastSync = getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getLong(PREF_LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP, 0);
        EventBus.getDefault().postSticky(new SyncServiceEvent(R.string.sync_status_episodes_download));
        EpisodeActionChanges getResponse = syncServiceImpl.getEpisodeActionChanges(lastSync);
        long newTimeStamp = getResponse.getTimestamp();
        List<EpisodeAction> remoteActions = getResponse.getEpisodeActions();
        processEpisodeActions(remoteActions);

        // upload local actions
        EventBus.getDefault().postSticky(new SyncServiceEvent(R.string.sync_status_episodes_upload));
        List<EpisodeAction> queuedEpisodeActions = getQueuedEpisodeActions();
        if (lastSync == 0) {
            EventBus.getDefault().postSticky(new SyncServiceEvent(R.string.sync_status_upload_played));
            List<FeedItem> readItems = DBReader.getPlayedItems();
            Log.d(TAG, "First sync. Upload state for all " + readItems.size() + " played episodes");
            for (FeedItem item : readItems) {
                FeedMedia media = item.getMedia();
                if (media == null) {
                    continue;
                }
                EpisodeAction played = new EpisodeAction.Builder(item, EpisodeAction.PLAY)
                        .currentTimestamp()
                        .started(media.getDuration() / 1000)
                        .position(media.getDuration() / 1000)
                        .total(media.getDuration() / 1000)
                        .build();
                queuedEpisodeActions.add(played);
            }
        }
        if (queuedEpisodeActions.size() > 0) {
            lock.lock();
            try {
                Log.d(TAG, "Uploading " + queuedEpisodeActions.size() + " actions: "
                        + StringUtils.join(queuedEpisodeActions, ", "));
                UploadChangesResponse postResponse = syncServiceImpl.uploadEpisodeActions(queuedEpisodeActions);
                newTimeStamp = postResponse.timestamp;
                Log.d(TAG, "Upload episode response: " + postResponse);
                getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                        .putString(PREF_QUEUED_EPISODE_ACTIONS, "[]").apply();
            } finally {
                lock.unlock();
            }
        }
        getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putLong(PREF_LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP, newTimeStamp).apply();
    }


    private synchronized void processEpisodeActions(List<EpisodeAction> remoteActions) {
        Log.d(TAG, "Processing " + remoteActions.size() + " actions");
        if (remoteActions.size() == 0) {
            return;
        }

        Map<Pair<String, String>, EpisodeAction> localMostRecentPlayAction = new ArrayMap<>();
        for (EpisodeAction action : getQueuedEpisodeActions()) {
            Pair<String, String> key = new Pair<>(action.getPodcast(), action.getEpisode());
            EpisodeAction mostRecent = localMostRecentPlayAction.get(key);
            if (mostRecent == null || mostRecent.getTimestamp() == null) {
                localMostRecentPlayAction.put(key, action);
            } else if (mostRecent.getTimestamp().before(action.getTimestamp())) {
                localMostRecentPlayAction.put(key, action);
            }
        }

        // make sure more recent local actions are not overwritten by older remote actions
        Map<Pair<String, String>, EpisodeAction> mostRecentPlayAction = new ArrayMap<>();
        for (EpisodeAction action : remoteActions) {
            Log.d(TAG, "Processing action: " + action.toString());
            switch (action.getAction()) {
                case NEW:
                    FeedItem newItem = DBReader.getFeedItemByUrl(action.getPodcast(), action.getEpisode());
                    if (newItem != null) {
                        DBWriter.markItemPlayed(newItem, FeedItem.UNPLAYED, true);
                    } else {
                        Log.i(TAG, "Unknown feed item: " + action);
                    }
                    break;
                case DOWNLOAD:
                    break;
                case PLAY:
                    Pair<String, String> key = new Pair<>(action.getPodcast(), action.getEpisode());
                    EpisodeAction localMostRecent = localMostRecentPlayAction.get(key);
                    if (localMostRecent == null || localMostRecent.getTimestamp() == null
                            || localMostRecent.getTimestamp().before(action.getTimestamp())) {
                        EpisodeAction mostRecent = mostRecentPlayAction.get(key);
                        if (mostRecent == null || mostRecent.getTimestamp() == null) {
                            mostRecentPlayAction.put(key, action);
                        } else if (action.getTimestamp() != null
                                && mostRecent.getTimestamp().before(action.getTimestamp())) {
                            mostRecentPlayAction.put(key, action);
                        } else {
                            Log.d(TAG, "No date information in action, skipping it");
                        }
                    }
                    break;
                case DELETE:
                    // NEVER EVER call DBWriter.deleteFeedMediaOfItem() here, leads to an infinite loop
                    break;
                default:
                    Log.e(TAG, "Unknown action: " + action);
                    break;
            }
        }
        LongList queueToBeRemoved = new LongList();
        List<FeedItem> updatedItems = new ArrayList<>();
        for (EpisodeAction action : mostRecentPlayAction.values()) {
            FeedItem playItem = DBReader.getFeedItemByUrl(action.getPodcast(), action.getEpisode());
            Log.d(TAG, "Most recent play action: " + action.toString());
            if (playItem != null) {
                FeedMedia media = playItem.getMedia();
                media.setPosition(action.getPosition() * 1000);
                if (FeedItemUtil.hasAlmostEnded(playItem.getMedia())) {
                    Log.d(TAG, "Marking as played");
                    playItem.setPlayed(true);
                    queueToBeRemoved.add(playItem.getId());
                }
                updatedItems.add(playItem);
            }
        }
        DBWriter.removeQueueItem(getApplicationContext(), false, queueToBeRemoved.toArray());
        DBReader.loadAdditionalFeedItemListData(updatedItems);
        DBWriter.setItemList(updatedItems);
    }

    private void clearErrorNotifications() {
        NotificationManager nm = (NotificationManager) getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(R.id.notification_gpodnet_sync_error);
        nm.cancel(R.id.notification_gpodnet_sync_autherror);
    }

    private void updateErrorNotification(SyncServiceException exception) {
        if (!UserPreferences.gpodnetNotificationsEnabled()) {
            Log.d(TAG, "Skipping sync error notification because of user setting");
            return;
        }
        Log.d(TAG, "Posting sync error notification");
        final String description = getApplicationContext().getString(R.string.gpodnetsync_error_descr)
                + exception.getMessage();

        Intent intent = getApplicationContext().getPackageManager().getLaunchIntentForPackage(
                getApplicationContext().getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(),
                R.id.pending_intent_sync_error, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new NotificationCompat.Builder(getApplicationContext(),
                NotificationUtils.CHANNEL_ID_SYNC_ERROR)
                .setContentTitle(getApplicationContext().getString(R.string.gpodnetsync_error_title))
                .setContentText(description)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_notification_sync_error)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
        NotificationManager nm = (NotificationManager) getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(R.id.notification_gpodnet_sync_error, notification);
    }
}
