package de.danoeh.antennapod.core.service.playback;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import androidx.annotation.NonNull;
import android.util.Log;

import de.danoeh.antennapod.core.preferences.SleepTimerPreferences;
import de.danoeh.antennapod.core.util.ChapterUtils;
import de.danoeh.antennapod.core.widget.WidgetUpdater;
import io.reactivex.disposables.Disposable;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.core.event.FeedItemEvent;
import de.danoeh.antennapod.core.event.QueueEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.model.playback.Playable;
import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;


/**
 * Manages the background tasks of PlaybackSerivce, i.e.
 * the sleep timer, the position saver, the widget updater and
 * the queue loader.
 * <p/>
 * The PlaybackServiceTaskManager(PSTM) uses a callback object (PSTMCallback)
 * to notify the PlaybackService about updates from the running tasks.
 */
public class PlaybackServiceTaskManager {
    private static final String TAG = "PlaybackServiceTaskMgr";

    /**
     * Update interval of position saver in milliseconds.
     */
    public static final int POSITION_SAVER_WAITING_INTERVAL = 5000;
    /**
     * Notification interval of widget updater in milliseconds.
     */
    public static final int WIDGET_UPDATER_NOTIFICATION_INTERVAL = 1000;

    private static final int SCHED_EX_POOL_SIZE = 2;
    private final ScheduledThreadPoolExecutor schedExecutor;

    private ScheduledFuture<?> positionSaverFuture;
    private ScheduledFuture<?> widgetUpdaterFuture;
    private ScheduledFuture<?> sleepTimerFuture;
    private volatile Future<List<FeedItem>> queueFuture;
    private volatile Disposable chapterLoaderFuture;

    private SleepTimer sleepTimer;

    private final Context context;
    private final PSTMCallback callback;

    /**
     * Sets up a new PSTM. This method will also start the queue loader task.
     *
     * @param context
     * @param callback A PSTMCallback object for notifying the user about updates. Must not be null.
     */
    public PlaybackServiceTaskManager(@NonNull Context context,
                                      @NonNull PSTMCallback callback) {
        this.context = context;
        this.callback = callback;
        schedExecutor = new ScheduledThreadPoolExecutor(SCHED_EX_POOL_SIZE, r -> {
            Thread t = new Thread(r);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        loadQueue();
        EventBus.getDefault().register(this);
    }

    @Subscribe
    public void onEvent(QueueEvent event) {
        Log.d(TAG, "onEvent(QueueEvent " + event +")");
        cancelQueueLoader();
        loadQueue();
    }

    private synchronized boolean isQueueLoaderActive() {
        return queueFuture != null && !queueFuture.isDone();
    }

    private synchronized void cancelQueueLoader() {
        if (isQueueLoaderActive()) {
            queueFuture.cancel(true);
        }
    }

    private synchronized void loadQueue() {
        if (!isQueueLoaderActive()) {
            queueFuture = schedExecutor.submit(() -> DBReader.getQueue());
        }
    }

    @Subscribe
    public void onEvent(FeedItemEvent event) {
        // Use case: when an item in the queue has been downloaded,
        // listening to the event to ensure the downloaded item will be used.
        Log.d(TAG, "onEvent(FeedItemEvent " + event + ")");

        for (FeedItem item : event.items) {
            if (isItemInQueue(item.getId())) {
                Log.d(TAG, "onEvent(FeedItemEvent) - some item (" + item.getId() + ") in the queue has been updated (usually downloaded). Refresh the queue.");
                cancelQueueLoader();
                loadQueue();
                return;
            }
        }
    }

    private boolean isItemInQueue(long itemId) {
        List<FeedItem> queue = getQueueIfLoaded();
        if (queue != null) {
            for (FeedItem item : queue) {
                if (item.getId() == itemId) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the queue if it is already loaded or null if it hasn't been loaded yet.
     * In order to wait until the queue has been loaded, use getQueue()
     */
    public synchronized List<FeedItem> getQueueIfLoaded() {
        if (queueFuture.isDone()) {
            try {
                return queueFuture.get();
            } catch (InterruptedException | ExecutionException | CancellationException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Returns the queue or waits until the PSTM has loaded the queue from the database.
     */
    public synchronized List<FeedItem> getQueue() throws InterruptedException {
        try {
            return queueFuture.get();
        } catch (ExecutionException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Starts the position saver task. If the position saver is already active, nothing will happen.
     */
    public synchronized void startPositionSaver() {
        if (!isPositionSaverActive()) {
            Runnable positionSaver = callback::positionSaverTick;
            positionSaver = useMainThreadIfNecessary(positionSaver);
            positionSaverFuture = schedExecutor.scheduleWithFixedDelay(positionSaver, POSITION_SAVER_WAITING_INTERVAL,
                    POSITION_SAVER_WAITING_INTERVAL, TimeUnit.MILLISECONDS);

            Log.d(TAG, "Started PositionSaver");
        } else {
            Log.d(TAG, "Call to startPositionSaver was ignored.");
        }
    }

    /**
     * Returns true if the position saver is currently running.
     */
    public synchronized boolean isPositionSaverActive() {
        return positionSaverFuture != null && !positionSaverFuture.isCancelled() && !positionSaverFuture.isDone();
    }

    /**
     * Cancels the position saver. If the position saver is not running, nothing will happen.
     */
    public synchronized void cancelPositionSaver() {
        if (isPositionSaverActive()) {
            positionSaverFuture.cancel(false);
            Log.d(TAG, "Cancelled PositionSaver");
        }
    }

    /**
     * Starts the widget updater task. If the widget updater is already active, nothing will happen.
     */
    public synchronized void startWidgetUpdater() {
        if (!isWidgetUpdaterActive() && !schedExecutor.isShutdown()) {
            Runnable widgetUpdater = this::requestWidgetUpdate;
            widgetUpdater = useMainThreadIfNecessary(widgetUpdater);
            widgetUpdaterFuture = schedExecutor.scheduleWithFixedDelay(widgetUpdater,
                    WIDGET_UPDATER_NOTIFICATION_INTERVAL, WIDGET_UPDATER_NOTIFICATION_INTERVAL, TimeUnit.MILLISECONDS);
            Log.d(TAG, "Started WidgetUpdater");
        } else {
            Log.d(TAG, "Call to startWidgetUpdater was ignored.");
        }
    }

    /**
     * Retrieves information about the widget state in the calling thread and then displays it in a background thread.
     */
    public synchronized void requestWidgetUpdate() {
        WidgetUpdater.WidgetState state = callback.requestWidgetState();
        if (!schedExecutor.isShutdown()) {
            schedExecutor.execute(() -> WidgetUpdater.updateWidget(context, state));
        } else {
            Log.d(TAG, "Call to requestWidgetUpdate was ignored.");
        }
    }

    /**
     * Starts a new sleep timer with the given waiting time. If another sleep timer is already active, it will be
     * cancelled first.
     * After waitingTime has elapsed, onSleepTimerExpired() will be called.
     *
     * @throws java.lang.IllegalArgumentException if waitingTime <= 0
     */
    public synchronized void setSleepTimer(long waitingTime) {
        if (waitingTime <= 0) {
            throw new IllegalArgumentException("Waiting time <= 0");
        }

        Log.d(TAG, "Setting sleep timer to " + waitingTime + " milliseconds");
        if (isSleepTimerActive()) {
            sleepTimerFuture.cancel(true);
        }
        sleepTimer = new SleepTimer(waitingTime);
        sleepTimerFuture = schedExecutor.schedule(sleepTimer, 0, TimeUnit.MILLISECONDS);
    }

    public synchronized void setSleepTimerEpisodes(int waitingEpisodes) {
        if (waitingEpisodes <= 0) {
            throw new IllegalArgumentException("Waiting time <= 0");
        }

        Log.d(TAG, "Setting sleep timer to " + waitingEpisodes + " episodes");
        if (isSleepTimerActive()) {
            sleepTimerFuture.cancel(true);
        }
        sleepTimer = new SleepTimer(waitingEpisodes);
        sleepTimerFuture = schedExecutor.schedule(sleepTimer, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns true if the sleep timer is currently active.
     */
    public synchronized boolean isSleepTimerActive() {
        return sleepTimer != null
                && sleepTimerFuture != null
                && !sleepTimerFuture.isCancelled()
                && !sleepTimerFuture.isDone()
                && sleepTimer.getWaitingTime() > 0;
    }

    /**
     * Disables the sleep timer. If the sleep timer is not active, nothing will happen.
     */
    public synchronized void disableSleepTimer() {
        if (isSleepTimerActive()) {
            Log.d(TAG, "Disabling sleep timer");
            sleepTimer.cancel();
        }
    }

    /**
     * Restarts the sleep timer. If the sleep timer is not active, nothing will happen.
     */
    public synchronized void restartSleepTimer() {
        if (isSleepTimerActive()) {
            Log.d(TAG, "Restarting sleep timer");
            sleepTimer.restart();
        }
    }

    /**
     * Returns the current sleep timer time or 0 if the sleep timer is not active.
     */
    public synchronized long getSleepTimerTimeLeft() {
        if (isSleepTimerActive()) {
            return sleepTimer.getWaitingTime();
        } else {
            return 0;
        }
    }

    public synchronized void playbackEnded() {
        if (isSleepTimerActive()) {
            sleepTimer.playbackEnded();
        }
    }


    /**
     * Returns true if the widget updater is currently running.
     */
    public synchronized boolean isWidgetUpdaterActive() {
        return widgetUpdaterFuture != null && !widgetUpdaterFuture.isCancelled() && !widgetUpdaterFuture.isDone();
    }

    /**
     * Cancels the widget updater. If the widget updater is not running, nothing will happen.
     */
    public synchronized void cancelWidgetUpdater() {
        if (isWidgetUpdaterActive()) {
            widgetUpdaterFuture.cancel(false);
            Log.d(TAG, "Cancelled WidgetUpdater");
        }
    }

    /**
     * Starts a new thread that loads the chapter marks from a playable object. If another chapter loader is already active,
     * it will be cancelled first.
     * On completion, the callback's onChapterLoaded method will be called.
     */
    public synchronized void startChapterLoader(@NonNull final Playable media) {
        if (chapterLoaderFuture != null) {
            chapterLoaderFuture.dispose();
            chapterLoaderFuture = null;
        }

        if (media.getChapters() == null) {
            chapterLoaderFuture = Completable.create(emitter -> {
                ChapterUtils.loadChapters(media, context);
                emitter.onComplete();
            })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> callback.onChapterLoaded(media),
                            throwable -> Log.d(TAG, "Error loading chapters: " + Log.getStackTraceString(throwable)));
        }
    }


    /**
     * Cancels all tasks. The PSTM will be in the initial state after execution of this method.
     */
    public synchronized void cancelAllTasks() {
        cancelPositionSaver();
        cancelWidgetUpdater();
        disableSleepTimer();
        cancelQueueLoader();

        if (chapterLoaderFuture != null) {
            chapterLoaderFuture.dispose();
            chapterLoaderFuture = null;
        }
    }

    /**
     * Cancels all tasks and shuts down the internal executor service of the PSTM. The object should not be used after
     * execution of this method.
     */
    public synchronized void shutdown() {
        EventBus.getDefault().unregister(this);
        cancelAllTasks();
        schedExecutor.shutdown();
    }

    private Runnable useMainThreadIfNecessary(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Called in main thread => ExoPlayer is used
            // Run on ui thread even if called from schedExecutor
            Handler handler = new Handler(Looper.getMainLooper());
            return () -> handler.post(runnable);
        } else {
            return runnable;
        }
    }

    /**
     * Sleeps for a given time and then pauses playback.
     */
    class SleepTimer implements Runnable {
        private static final String TAG = "SleepTimer";
        private static final long UPDATE_INTERVAL = 1000L;
        public static final long NOTIFICATION_THRESHOLD = 10000;
        private boolean hasVibrated = false;
        private ShakeListener shakeListener;
        private final Handler handler;

        private final boolean episodes;
        private final long waitingTime;
        private long timeLeft;
        private final int waitingEpisodes;
        private int episodesLeft;

        public SleepTimer(long waitingTime) {
            super();
            this.waitingTime = waitingTime;
            this.timeLeft = waitingTime;
            this.waitingEpisodes = 0;
            this.episodes = false;

            if (UserPreferences.useExoplayer() && Looper.myLooper() == Looper.getMainLooper()) {
                // Run callbacks in main thread so they can call ExoPlayer methods themselves
                this.handler = new Handler(Looper.getMainLooper());
            } else {
                this.handler = null;
            }
        }

        public SleepTimer(int waitingEpisodes) {
            super();
            this.waitingTime = 0;
            this.waitingEpisodes = waitingEpisodes;
            this.episodesLeft = waitingEpisodes;
            this.episodes = true;

            if (UserPreferences.useExoplayer() && Looper.myLooper() == Looper.getMainLooper()) {
                // Run callbacks in main thread so they can call ExoPlayer methods themselves
                this.handler = new Handler(Looper.getMainLooper());
            } else {
                this.handler = null;
            }
        }

        private void postCallback(Runnable r) {
            if (handler == null) {
                r.run();
            } else {
                handler.post(r);
            }
        }

        @Override
        public void run() {
            if (episodes) {
                checkEpisodes();
            } else {
                checkTime();
            }
        }

        public void playbackEnded() {
            episodesLeft--;
            if (episodesLeft <= 0) {
                Log.d(TAG, "Sleep timer expired");
                if (shakeListener != null) {
                    shakeListener.pause();
                    shakeListener = null;
                }
                hasVibrated = false;
                if (!Thread.currentThread().isInterrupted()) {
                    postCallback(callback::onSleepTimerExpired);
                } else {
                    Log.d(TAG, "Sleep timer interrupted");
                }
            }
        }

        private void checkEpisodes() {
            Log.d(TAG, "Starting");
            while (episodesLeft > 0) {
                try {
                    Thread.sleep(UPDATE_INTERVAL);
                } catch (InterruptedException e) {
                    Log.d(TAG, "Thread was interrupted while waiting");
                    e.printStackTrace();
                    break;
                }

                if (episodesLeft <= 1) {
                    Log.d(TAG, "Sleep timer is about to expire");
                    //TODO: Implement vibrate shortly before end
                    if (SleepTimerPreferences.vibrate() && !hasVibrated) {
                        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) {
                            v.vibrate(500);
                            hasVibrated = true;
                        }
                    }
                    if (shakeListener == null && SleepTimerPreferences.shakeToReset()) {
                        shakeListener = new ShakeListener(context, this);
                    }
                }
            }
        }

        private void checkTime() {
            Log.d(TAG, "Starting");
            long lastTick = System.currentTimeMillis();
            while (timeLeft > 0) {
                try {
                    Thread.sleep(UPDATE_INTERVAL);
                } catch (InterruptedException e) {
                    Log.d(TAG, "Thread was interrupted while waiting");
                    e.printStackTrace();
                    break;
                }

                long now = System.currentTimeMillis();
                timeLeft -= now - lastTick;
                lastTick = now;

                if (timeLeft < NOTIFICATION_THRESHOLD) {
                    Log.d(TAG, "Sleep timer is about to expire");
                    if (SleepTimerPreferences.vibrate() && !hasVibrated) {
                        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) {
                            v.vibrate(500);
                            hasVibrated = true;
                        }
                    }
                    if (shakeListener == null && SleepTimerPreferences.shakeToReset()) {
                        shakeListener = new ShakeListener(context, this);
                    }
                    postCallback(() -> callback.onSleepTimerAlmostExpired(timeLeft));
                }
                if (timeLeft <= 0) {
                    Log.d(TAG, "Sleep timer expired");
                    if (shakeListener != null) {
                        shakeListener.pause();
                        shakeListener = null;
                    }
                    hasVibrated = false;
                    if (!Thread.currentThread().isInterrupted()) {
                        postCallback(callback::onSleepTimerExpired);
                    } else {
                        Log.d(TAG, "Sleep timer interrupted");
                    }
                }
            }
        }

        public long getWaitingTime() {
            return episodes ? episodesLeft : timeLeft;
        }

        public void restart() {
            postCallback(() -> {
                if (episodes) {
                    setSleepTimerEpisodes(waitingEpisodes);
                } else {
                    setSleepTimer(waitingTime);
                }
                callback.onSleepTimerReset();
            });
            if (shakeListener != null) {
                shakeListener.pause();
                shakeListener = null;
            }
        }

        public void cancel() {
            sleepTimerFuture.cancel(true);
            if (shakeListener != null) {
                shakeListener.pause();
            }
            postCallback(callback::onSleepTimerReset);
        }
    }

    public interface PSTMCallback {
        void positionSaverTick();

        void onSleepTimerAlmostExpired(long timeLeft);

        void onSleepTimerExpired();

        void onSleepTimerReset();

        WidgetUpdater.WidgetState requestWidgetState();

        void onChapterLoaded(Playable media);
    }
}
