package de.danoeh.antennapod.core.service.playback;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;

import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;
import de.danoeh.antennapod.core.storage.DBReader;
import org.antennapod.audio.MediaPlayer;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.core.feed.util.PlaybackSpeedUtils;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.util.RewindAfterPauseUtils;
import de.danoeh.antennapod.core.util.playback.AudioPlayer;
import de.danoeh.antennapod.core.util.playback.IPlayer;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.core.util.playback.PlaybackServiceStarter;
import de.danoeh.antennapod.core.util.playback.VideoPlayer;

/**
 * Manages the MediaPlayer object of the PlaybackService.
 */
public class LocalPSMP extends PlaybackServiceMediaPlayer {
    private static final String TAG = "LclPlaybackSvcMPlayer";

    private final AudioManager audioManager;

    private volatile PlayerStatus statusBeforeSeeking;
    private volatile IPlayer mediaPlayer;
    private volatile Playable media;

    private volatile boolean stream;
    private volatile MediaType mediaType;
    private final AtomicBoolean startWhenPrepared;
    private volatile boolean pausedBecauseOfTransientAudiofocusLoss;
    private volatile Pair<Integer, Integer> videoSize;
    private final AudioFocusRequestCompat audioFocusRequest;

    /**
     * Some asynchronous calls might change the state of the MediaPlayer object. Therefore calls in other threads
     * have to wait until these operations have finished.
     */
    private final PlayerLock playerLock;
    private final PlayerExecutor executor;
    private boolean useCallerThread = true;


    private CountDownLatch seekLatch;

    /**
     * All ExoPlayer methods must be executed on the same thread.
     * We use the main application thread. This class allows to
     * "fake" an executor that just calls the methods on the
     * calling thread instead of submitting to an executor.
     * Other players are still executed in a background thread.
     */
    private class PlayerExecutor {
        private ThreadPoolExecutor threadPool;

        public Future<?> submit(Runnable r) {
            if (useCallerThread) {
                r.run();
                return new FutureTask<Void>(() -> {}, null);
            } else {
                return threadPool.submit(r);
            }
        }

        public void shutdown() {
            threadPool.shutdown();
        }
    }

    /**
     * All ExoPlayer methods must be executed on the same thread.
     * We use the main application thread. This class allows to
     * "fake" a lock that does nothing. A lock is not needed if
     * everything is called on the same thread.
     * Other players are still executed in a background thread and
     * therefore use a real lock.
     */
    private class PlayerLock {
        private ReentrantLock lock = new ReentrantLock();

        public void lock() {
            if (!useCallerThread) {
                lock.lock();
            }
        }

        public boolean tryLock(int i, TimeUnit milliseconds) throws InterruptedException {
            if (!useCallerThread) {
                return lock.tryLock(i, milliseconds);
            }
            return true;
        }

        public boolean tryLock() {
            if (!useCallerThread) {
                return lock.tryLock();
            }
            return true;
        }

        public void unlock() {
            if (!useCallerThread) {
                lock.unlock();
            }
        }

        public boolean isHeldByCurrentThread() {
            if (!useCallerThread) {
                return lock.isHeldByCurrentThread();
            }
            return true;
        }
    }

    public LocalPSMP(@NonNull Context context,
                     @NonNull PSMPCallback callback) {
        super(context, callback);
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.playerLock = new PlayerLock();
        this.startWhenPrepared = new AtomicBoolean(false);

        executor = new PlayerExecutor();
        executor.threadPool = new ThreadPoolExecutor(1, 1, 5, TimeUnit.MINUTES, new LinkedBlockingDeque<>(),
                (r, executor) -> Log.d(TAG, "Rejected execution of runnable"));

        mediaPlayer = null;
        statusBeforeSeeking = null;
        pausedBecauseOfTransientAudiofocusLoss = false;
        mediaType = MediaType.UNKNOWN;
        videoSize = null;

        AudioAttributesCompat audioAttributes = new AudioAttributesCompat.Builder()
                .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                .build();
        audioFocusRequest = new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setWillPauseWhenDucked(true)
                .build();
    }

    /**
     * Starts or prepares playback of the specified Playable object. If another Playable object is already being played, the currently playing
     * episode will be stopped and replaced with the new Playable object. If the Playable object is already being played, the method will
     * not do anything.
     * Whether playback starts immediately depends on the given parameters. See below for more details.
     * <p/>
     * States:
     * During execution of the method, the object will be in the INITIALIZING state. The end state depends on the given parameters.
     * <p/>
     * If 'prepareImmediately' is set to true, the method will go into PREPARING state and after that into PREPARED state. If
     * 'startWhenPrepared' is set to true, the method will additionally go into PLAYING state.
     * <p/>
     * If an unexpected error occurs while loading the Playable's metadata or while setting the MediaPlayers data source, the object
     * will enter the ERROR state.
     * <p/>
     * This method is executed on an internal executor service.
     *
     * @param playable           The Playable object that is supposed to be played. This parameter must not be null.
     * @param stream             The type of playback. If false, the Playable object MUST provide access to a locally available file via
     *                           getLocalMediaUrl. If true, the Playable object MUST provide access to a resource that can be streamed by
     *                           the Android MediaPlayer via getStreamUrl.
     * @param startWhenPrepared  Sets the 'startWhenPrepared' flag. This flag determines whether playback will start immediately after the
     *                           episode has been prepared for playback. Setting this flag to true does NOT mean that the episode will be prepared
     *                           for playback immediately (see 'prepareImmediately' parameter for more details)
     * @param prepareImmediately Set to true if the method should also prepare the episode for playback.
     */
    @Override
    public void playMediaObject(@NonNull final Playable playable, final boolean stream, final boolean startWhenPrepared, final boolean prepareImmediately) {
        Log.d(TAG, "playMediaObject(...)");
        useCallerThread = UserPreferences.useExoplayer();
        executor.submit(() -> {
            playerLock.lock();
            try {
                playMediaObject(playable, false, stream, startWhenPrepared, prepareImmediately);
            } catch (RuntimeException e) {
                e.printStackTrace();
                throw e;
            } finally {
                playerLock.unlock();
            }
        });
    }

    /**
     * Internal implementation of playMediaObject. This method has an additional parameter that allows the caller to force a media player reset even if
     * the given playable parameter is the same object as the currently playing media.
     * <p/>
     * This method requires the playerLock and is executed on the caller's thread.
     *
     * @see #playMediaObject(Playable, boolean, boolean, boolean)
     */
    private void playMediaObject(@NonNull final Playable playable, final boolean forceReset, final boolean stream, final boolean startWhenPrepared, final boolean prepareImmediately) {
        if (!playerLock.isHeldByCurrentThread()) {
            throw new IllegalStateException("method requires playerLock");
        }


        if (media != null) {
            if (!forceReset && media.getIdentifier().equals(playable.getIdentifier())
                    && playerStatus == PlayerStatus.PLAYING) {
                // episode is already playing -> ignore method call
                Log.d(TAG, "Method call to playMediaObject was ignored: media file already playing.");
                return;
            } else {
                // stop playback of this episode
                if (playerStatus == PlayerStatus.PAUSED || playerStatus == PlayerStatus.PLAYING || playerStatus == PlayerStatus.PREPARED) {
                    mediaPlayer.stop();
                }
                // set temporarily to pause in order to update list with current position
                if (playerStatus == PlayerStatus.PLAYING) {
                    callback.onPlaybackPause(media, getPosition());
                }

                if (!media.getIdentifier().equals(playable.getIdentifier())) {
                    final Playable oldMedia = media;
                    executor.submit(() -> callback.onPostPlayback(oldMedia, false, false, true));
                }

                setPlayerStatus(PlayerStatus.INDETERMINATE, null);
            }
        }

        this.media = playable;
        this.stream = stream;
        this.mediaType = media.getMediaType();
        this.videoSize = null;
        createMediaPlayer();
        LocalPSMP.this.startWhenPrepared.set(startWhenPrepared);
        setPlayerStatus(PlayerStatus.INITIALIZING, media);
        try {
            if (media instanceof FeedMedia && ((FeedMedia) media).getItem() == null) {
                ((FeedMedia) media).setItem(DBReader.getFeedItem(((FeedMedia) media).getItemId()));
            }
            callback.onMediaChanged(false);
            setPlaybackParams(PlaybackSpeedUtils.getCurrentPlaybackSpeed(media), UserPreferences.isSkipSilence());
            if (stream) {
                if (playable instanceof FeedMedia) {
                    FeedMedia feedMedia = (FeedMedia) playable;
                    FeedPreferences preferences = feedMedia.getItem().getFeed().getPreferences();
                    mediaPlayer.setDataSource(
                            media.getStreamUrl(),
                            preferences.getUsername(),
                            preferences.getPassword());
                } else {
                    mediaPlayer.setDataSource(media.getStreamUrl());
                }
            } else if (media.getLocalMediaUrl() != null && new File(media.getLocalMediaUrl()).canRead()) {
                mediaPlayer.setDataSource(media.getLocalMediaUrl());
            } else {
                throw new IOException("Unable to read local file " + media.getLocalMediaUrl());
            }
            UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
            if (uiModeManager.getCurrentModeType() != Configuration.UI_MODE_TYPE_CAR) {
                setPlayerStatus(PlayerStatus.INITIALIZED, media);
            }

            if (prepareImmediately) {
                setPlayerStatus(PlayerStatus.PREPARING, media);
                mediaPlayer.prepare();
                onPrepared(startWhenPrepared);
            }

        } catch (IOException | IllegalStateException e) {
            e.printStackTrace();
            setPlayerStatus(PlayerStatus.ERROR, null);
        }
    }

    /**
     * Resumes playback if the PSMP object is in PREPARED or PAUSED state. If the PSMP object is in an invalid state.
     * nothing will happen.
     * <p/>
     * This method is executed on an internal executor service.
     */
    @Override
    public void resume() {
        executor.submit(() -> {
            playerLock.lock();
            resumeSync();
            playerLock.unlock();
        });
    }

    private void resumeSync() {
        if (playerStatus == PlayerStatus.PAUSED || playerStatus == PlayerStatus.PREPARED) {
            int focusGained = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest);

            if (focusGained == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "Audiofocus successfully requested");
                Log.d(TAG, "Resuming/Starting playback");
                acquireWifiLockIfNecessary();

                setPlaybackParams(PlaybackSpeedUtils.getCurrentPlaybackSpeed(media), UserPreferences.isSkipSilence());

                float leftVolume = UserPreferences.getLeftVolume();
                float rightVolume = UserPreferences.getRightVolume();
                setVolume(leftVolume, rightVolume);

                if (playerStatus == PlayerStatus.PREPARED && media.getPosition() > 0) {
                    int newPosition = RewindAfterPauseUtils.calculatePositionWithRewind(
                        media.getPosition(),
                        media.getLastPlayedTime());
                    seekToSync(newPosition);
                }
                mediaPlayer.start();

                setPlayerStatus(PlayerStatus.PLAYING, media);
                pausedBecauseOfTransientAudiofocusLoss = false;
            } else {
                Log.e(TAG, "Failed to request audio focus");
            }
        } else {
            Log.d(TAG, "Call to resume() was ignored because current state of PSMP object is " + playerStatus);
        }
    }


    /**
     * Saves the current position and pauses playback. Note that, if audiofocus
     * is abandoned, the lockscreen controls will also disapear.
     * <p/>
     * This method is executed on an internal executor service.
     *
     * @param abandonFocus is true if the service should release audio focus
     * @param reinit       is true if service should reinit after pausing if the media
     *                     file is being streamed
     */
    @Override
    public void pause(final boolean abandonFocus, final boolean reinit) {
        executor.submit(() -> {
            playerLock.lock();
            releaseWifiLockIfNecessary();
            if (playerStatus == PlayerStatus.PLAYING) {
                Log.d(TAG, "Pausing playback.");
                mediaPlayer.pause();
                setPlayerStatus(PlayerStatus.PAUSED, media, getPosition());

                if (abandonFocus) {
                    abandonAudioFocus();
                    pausedBecauseOfTransientAudiofocusLoss = false;
                }
                if (stream && reinit) {
                    reinit();
                }
            } else {
                Log.d(TAG, "Ignoring call to pause: Player is in " + playerStatus + " state");
            }

            playerLock.unlock();
        });
    }

    private void abandonAudioFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest);
    }

    /**
     * Prepares media player for playback if the service is in the INITALIZED
     * state.
     * <p/>
     * This method is executed on an internal executor service.
     */
    @Override
    public void prepare() {
        executor.submit(() -> {
            playerLock.lock();

            if (playerStatus == PlayerStatus.INITIALIZED) {
                Log.d(TAG, "Preparing media player");
                setPlayerStatus(PlayerStatus.PREPARING, media);
                try {
                    mediaPlayer.prepare();
                    onPrepared(startWhenPrepared.get());
                } catch (IOException e) {
                    e.printStackTrace();
                    setPlayerStatus(PlayerStatus.ERROR, null);
                }
            }
            playerLock.unlock();

        });
    }

    /**
     * Called after media player has been prepared. This method is executed on the caller's thread.
     */
    private void onPrepared(final boolean startWhenPrepared) {
        playerLock.lock();

        if (playerStatus != PlayerStatus.PREPARING) {
            playerLock.unlock();
            throw new IllegalStateException("Player is not in PREPARING state");
        }

        Log.d(TAG, "Resource prepared");

        if (mediaType == MediaType.VIDEO && mediaPlayer instanceof ExoPlayerWrapper) {
            ExoPlayerWrapper vp = (ExoPlayerWrapper) mediaPlayer;
            videoSize = new Pair<>(vp.getVideoWidth(), vp.getVideoHeight());
        } else if(mediaType == MediaType.VIDEO && mediaPlayer instanceof VideoPlayer) {
            VideoPlayer vp = (VideoPlayer) mediaPlayer;
            videoSize = new Pair<>(vp.getVideoWidth(), vp.getVideoHeight());
        }

        // TODO this call has no effect!
        if (media.getPosition() > 0) {
            seekToSync(media.getPosition());
        }

        if (media.getDuration() <= 0) {
            Log.d(TAG, "Setting duration of media");
            media.setDuration(mediaPlayer.getDuration());
        }
        setPlayerStatus(PlayerStatus.PREPARED, media);

        if (startWhenPrepared) {
            resumeSync();
        }

        playerLock.unlock();
    }

    /**
     * Resets the media player and moves it into INITIALIZED state.
     * <p/>
     * This method is executed on an internal executor service.
     */
    @Override
    public void reinit() {
        useCallerThread = UserPreferences.useExoplayer();
        executor.submit(() -> {
            playerLock.lock();
            Log.d(TAG, "reinit()");
            releaseWifiLockIfNecessary();
            if (media != null) {
                playMediaObject(media, true, stream, startWhenPrepared.get(), false);
            } else if (mediaPlayer != null) {
                mediaPlayer.reset();
            } else {
                Log.d(TAG, "Call to reinit was ignored: media and mediaPlayer were null");
            }
            playerLock.unlock();
        });
    }


    /**
     * Seeks to the specified position. If the PSMP object is in an invalid state, this method will do nothing.
     *
     * @param t The position to seek to in milliseconds. t < 0 will be interpreted as t = 0
     *          <p/>
     *          This method is executed on the caller's thread.
     */
    private void seekToSync(int t) {
        if (t < 0) {
            t = 0;
        }
        playerLock.lock();

        if (playerStatus == PlayerStatus.PLAYING
                || playerStatus == PlayerStatus.PAUSED
                || playerStatus == PlayerStatus.PREPARED) {
            if(seekLatch != null && seekLatch.getCount() > 0) {
                try {
                    seekLatch.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Log.e(TAG, Log.getStackTraceString(e));
                }
            }
            seekLatch = new CountDownLatch(1);
            statusBeforeSeeking = playerStatus;
            setPlayerStatus(PlayerStatus.SEEKING, media, getPosition());
            mediaPlayer.seekTo(t);
            if (statusBeforeSeeking == PlayerStatus.PREPARED) {
                media.setPosition(t);
            }
            try {
                seekLatch.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        } else if (playerStatus == PlayerStatus.INITIALIZED) {
            media.setPosition(t);
            startWhenPrepared.set(false);
            prepare();
        }
        playerLock.unlock();
    }

    /**
     * Seeks to the specified position. If the PSMP object is in an invalid state, this method will do nothing.
     * Invalid time values (< 0) will be ignored.
     * <p/>
     * This method is executed on an internal executor service.
     */
    @Override
    public void seekTo(final int t) {
        executor.submit(() -> seekToSync(t));
    }

    /**
     * Seek a specific position from the current position
     *
     * @param d offset from current position (positive or negative)
     */
    @Override
    public void seekDelta(final int d) {
        executor.submit(() -> {
            playerLock.lock();
            int currentPosition = getPosition();
            if (currentPosition != INVALID_TIME) {
                seekToSync(currentPosition + d);
            } else {
                Log.e(TAG, "getPosition() returned INVALID_TIME in seekDelta");
            }

            playerLock.unlock();
        });
    }

    /**
     * Returns the duration of the current media object or INVALID_TIME if the duration could not be retrieved.
     */
    @Override
    public int getDuration() {
        if (!playerLock.tryLock()) {
            return INVALID_TIME;
        }

        int retVal = INVALID_TIME;
        if (playerStatus == PlayerStatus.PLAYING
                || playerStatus == PlayerStatus.PAUSED
                || playerStatus == PlayerStatus.PREPARED) {
            retVal = mediaPlayer.getDuration();
        }
        if (retVal <= 0 && media != null && media.getDuration() > 0) {
            retVal = media.getDuration();
        }

        playerLock.unlock();
        return retVal;
    }

    /**
     * Returns the position of the current media object or INVALID_TIME if the position could not be retrieved.
     */
    @Override
    public int getPosition() {
        try {
            if (!playerLock.tryLock(50, TimeUnit.MILLISECONDS)) {
                return INVALID_TIME;
            }
        } catch (InterruptedException e) {
            return INVALID_TIME;
        }

        int retVal = INVALID_TIME;
        if (playerStatus.isAtLeast(PlayerStatus.PREPARED)) {
            retVal = mediaPlayer.getCurrentPosition();
        }
        if (retVal <= 0 && media != null && media.getPosition() >= 0) {
            retVal = media.getPosition();
        }

        playerLock.unlock();
        return retVal;
    }

    @Override
    public boolean isStartWhenPrepared() {
        return startWhenPrepared.get();
    }

    @Override
    public void setStartWhenPrepared(boolean startWhenPrepared) {
        this.startWhenPrepared.set(startWhenPrepared);
    }

    /**
     * Sets the playback speed.
     * This method is executed on the caller's thread.
     */
    private void setSpeedSyncAndSkipSilence(float speed, boolean skipSilence) {
        playerLock.lock();
        Log.d(TAG, "Playback speed was set to " + speed);
        callback.playbackSpeedChanged(speed);
        mediaPlayer.setPlaybackParams(speed, skipSilence);
        playerLock.unlock();
    }

    /**
     * Sets the playback speed.
     * This method is executed on an internal executor service.
     */
    @Override
    public void setPlaybackParams(final float speed, final boolean skipSilence) {
        executor.submit(() -> setSpeedSyncAndSkipSilence(speed, skipSilence));
    }

    /**
     * Returns the current playback speed. If the playback speed could not be retrieved, 1 is returned.
     */
    @Override
    public float getPlaybackSpeed() {
        if (!playerLock.tryLock()) {
            return 1;
        }

        float retVal = 1;
        if ((playerStatus == PlayerStatus.PLAYING
                || playerStatus == PlayerStatus.PAUSED
                || playerStatus == PlayerStatus.INITIALIZED
                || playerStatus == PlayerStatus.PREPARED)) {
            retVal = mediaPlayer.getCurrentSpeedMultiplier();
        }
        playerLock.unlock();
        return retVal;
    }

    /**
     * Sets the playback volume.
     * This method is executed on an internal executor service.
     */
    @Override
    public void setVolume(final float volumeLeft, float volumeRight) {
        executor.submit(() -> setVolumeSync(volumeLeft, volumeRight));
    }

    /**
     * Sets the playback volume.
     * This method is executed on the caller's thread.
     */
    private void setVolumeSync(float volumeLeft, float volumeRight) {
        playerLock.lock();
        Playable playable = getPlayable();
        if (playable instanceof FeedMedia) {
            FeedMedia feedMedia = (FeedMedia) playable;
            FeedPreferences preferences = feedMedia.getItem().getFeed().getPreferences();
            VolumeAdaptionSetting volumeAdaptionSetting = preferences.getVolumeAdaptionSetting();
            float adaptionFactor = volumeAdaptionSetting.getAdaptionFactor();
            volumeLeft *= adaptionFactor;
            volumeRight *= adaptionFactor;
        }
        mediaPlayer.setVolume(volumeLeft, volumeRight);
        Log.d(TAG, "Media player volume was set to " + volumeLeft + " " + volumeRight);
        playerLock.unlock();
    }

    /**
     * Returns true if the mediaplayer can mix stereo down to mono
     */
    @Override
    public boolean canDownmix() {
        boolean retVal = false;
        if (mediaPlayer != null && media != null && media.getMediaType() == MediaType.AUDIO) {
            retVal = mediaPlayer.canDownmix();
        }
        return retVal;
    }

    @Override
    public void setDownmix(boolean enable) {
        playerLock.lock();
        if (media != null && media.getMediaType() == MediaType.AUDIO) {
            mediaPlayer.setDownmix(enable);
            Log.d(TAG, "Media player downmix was set to " + enable);
        }
        playerLock.unlock();
    }

    @Override
    public MediaType getCurrentMediaType() {
        return mediaType;
    }

    @Override
    public boolean isStreaming() {
        return stream;
    }


    /**
     * Releases internally used resources. This method should only be called when the object is not used anymore.
     */
    @Override
    public void shutdown() {
        executor.shutdown();
        if (mediaPlayer != null) {
            try {
                removeMediaPlayerErrorListener();
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignore) { }
            mediaPlayer.release();
        }
        releaseWifiLockIfNecessary();
    }

    private void removeMediaPlayerErrorListener() {
        if (mediaPlayer instanceof VideoPlayer) {
            VideoPlayer vp = (VideoPlayer) mediaPlayer;
            vp.setOnErrorListener((mp, what, extra) -> true);
        } else if (mediaPlayer instanceof AudioPlayer) {
            AudioPlayer ap = (AudioPlayer) mediaPlayer;
            ap.setOnErrorListener((mediaPlayer, i, i1) -> true);
        } else if (mediaPlayer instanceof ExoPlayerWrapper) {
            ExoPlayerWrapper ap = (ExoPlayerWrapper) mediaPlayer;
            ap.setOnErrorListener((mediaPlayer, i, i1) -> true);
        }
    }

    /**
     * Releases internally used resources. This method should only be called when the object is not used anymore.
     * This method is executed on an internal executor service.
     */
    @Override
    public void shutdownQuietly() {
        executor.submit(this::shutdown);
        executor.shutdown();
    }

    @Override
    public void setVideoSurface(final SurfaceHolder surface) {
        executor.submit(() -> {
            playerLock.lock();
            if (mediaPlayer != null) {
                mediaPlayer.setDisplay(surface);
            }
            playerLock.unlock();
        });
    }

    @Override
    public void resetVideoSurface() {
        executor.submit(() -> {
            playerLock.lock();
            if (mediaType == MediaType.VIDEO) {
                Log.d(TAG, "Resetting video surface");
                mediaPlayer.setDisplay(null);
                reinit();
            } else {
                Log.e(TAG, "Resetting video surface for media of Audio type");
            }
            playerLock.unlock();
        });
    }

    /**
     * Return width and height of the currently playing video as a pair.
     *
     * @return Width and height as a Pair or null if the video size could not be determined. The method might still
     * return an invalid non-null value if the getVideoWidth() and getVideoHeight() methods of the media player return
     * invalid values.
     */
    @Override
    public Pair<Integer, Integer> getVideoSize() {
        if (!playerLock.tryLock()) {
            // use cached value if lock can't be aquired
            return videoSize;
        }
        Pair<Integer, Integer> res;
        if (mediaPlayer == null || playerStatus == PlayerStatus.ERROR || mediaType != MediaType.VIDEO) {
            res = null;
        } else if (mediaPlayer instanceof ExoPlayerWrapper) {
            ExoPlayerWrapper vp = (ExoPlayerWrapper) mediaPlayer;
            videoSize = new Pair<>(vp.getVideoWidth(), vp.getVideoHeight());
            res = videoSize;
        } else {
            VideoPlayer vp = (VideoPlayer) mediaPlayer;
            videoSize = new Pair<>(vp.getVideoWidth(), vp.getVideoHeight());
            res = videoSize;
        }
        playerLock.unlock();
        return res;
    }

    /**
     * Returns the current media, if you need the media and the player status together, you should
     * use getPSMPInfo() to make sure they're properly synchronized. Otherwise a race condition
     * could result in nonsensical results (like a status of PLAYING, but a null playable)
     * @return the current media. May be null
     */
    @Override
    public Playable getPlayable() {
        return media;
    }

    @Override
    protected void setPlayable(Playable playable) {
        media = playable;
    }

    public List<String> getAudioTracks() {
        return mediaPlayer.getAudioTracks();
    }

    public void setAudioTrack(int track) {
        mediaPlayer.setAudioTrack(track);
    }

    public int getSelectedAudioTrack() {
        return mediaPlayer.getSelectedAudioTrack();
    }

    private void createMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        if (media == null) {
            mediaPlayer = null;
            return;
        }

        if (UserPreferences.useExoplayer()) {
            mediaPlayer = new ExoPlayerWrapper(context);
        } else if (media.getMediaType() == MediaType.VIDEO) {
            mediaPlayer = new VideoPlayer();
        } else {
            mediaPlayer = new AudioPlayer(context);
        }

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
        setMediaPlayerListeners(mediaPlayer);
    }

    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {

        @Override
        public void onAudioFocusChange(final int focusChange) {
            if (!PlaybackService.isRunning) {
                abandonAudioFocus();
                Log.d(TAG, "onAudioFocusChange: PlaybackService is no longer running");
                if (focusChange == AudioManager.AUDIOFOCUS_GAIN && pausedBecauseOfTransientAudiofocusLoss) {
                    new PlaybackServiceStarter(context, getPlayable())
                            .startWhenPrepared(true)
                            .streamIfLastWasStream()
                            .callEvenIfRunning(false)
                            .start();
                }
                return;
            }

            executor.submit(() -> {
                playerLock.lock();

                // If there is an incoming call, playback should be paused permanently
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                final int callState = (tm != null) ? tm.getCallState() : 0;
                Log.i(TAG, "Call state:" + callState);

                if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        (!UserPreferences.shouldResumeAfterCall() && callState != TelephonyManager.CALL_STATE_IDLE)) {
                    Log.d(TAG, "Lost audio focus");
                    pause(true, false);
                    callback.shouldStop();
                } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                    Log.d(TAG, "Gained audio focus");
                    if (pausedBecauseOfTransientAudiofocusLoss) { // we paused => play now
                        resume();
                    } else { // we ducked => raise audio level back
                        setVolumeSync(UserPreferences.getLeftVolume(),
                                UserPreferences.getRightVolume());
                    }
                } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    if (playerStatus == PlayerStatus.PLAYING) {
                        if (!UserPreferences.shouldPauseForFocusLoss()) {
                            Log.d(TAG, "Lost audio focus temporarily. Ducking...");
                            final float DUCK_FACTOR = 0.25f;
                            setVolumeSync(DUCK_FACTOR * UserPreferences.getLeftVolume(),
                                    DUCK_FACTOR * UserPreferences.getRightVolume());
                            pausedBecauseOfTransientAudiofocusLoss = false;
                        } else {
                            Log.d(TAG, "Lost audio focus temporarily. Could duck, but won't, pausing...");
                            pause(false, false);
                            pausedBecauseOfTransientAudiofocusLoss = true;
                        }
                    }
                } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    if (playerStatus == PlayerStatus.PLAYING) {
                        Log.d(TAG, "Lost audio focus temporarily. Pausing...");
                        pause(false, false);
                        pausedBecauseOfTransientAudiofocusLoss = true;
                    }
                }
                playerLock.unlock();
            });
        }
    };


    @Override
    protected Future<?> endPlayback(final boolean hasEnded, final boolean wasSkipped,
                                    final boolean shouldContinue, final boolean toStoppedState) {
        useCallerThread = UserPreferences.useExoplayer();
        return executor.submit(() -> {
            playerLock.lock();
            releaseWifiLockIfNecessary();

            boolean isPlaying = playerStatus == PlayerStatus.PLAYING;

            // we're relying on the position stored in the Playable object for post-playback processing
            if (media != null) {
                int position = getPosition();
                if (position >= 0) {
                    media.setPosition(position);
                }
            }

            if (mediaPlayer != null) {
                mediaPlayer.reset();
            }

            abandonAudioFocus();

            final Playable currentMedia = media;
            Playable nextMedia = null;

            if (shouldContinue) {
                // Load next episode if previous episode was in the queue and if there
                // is an episode in the queue left.
                // Start playback immediately if continuous playback is enabled

                // Repeat episode implementation
                if (UserPreferences.repeatEpisode() && !wasSkipped) {
                    nextMedia = currentMedia;
                    nextMedia.setPosition(0);
                } else {
                    nextMedia = callback.getNextInQueue(currentMedia);
                }

                boolean playNextEpisode = isPlaying &&
                        nextMedia != null &&
                        UserPreferences.isFollowQueue();

                if (playNextEpisode) {
                    Log.d(TAG, "Playback of next episode will start immediately.");
                } else if (nextMedia == null){
                    Log.d(TAG, "No more episodes available to play");
                } else {
                    Log.d(TAG, "Loading next episode, but not playing automatically.");
                }

                if (nextMedia != null) {
                    callback.onPlaybackEnded(nextMedia.getMediaType(), !playNextEpisode);
                    // setting media to null signals to playMediaObject() that we're taking care of post-playback processing
                    media = null;
                    playMediaObject(nextMedia, false, !nextMedia.localFileAvailable(), playNextEpisode, playNextEpisode);
                }
            }
            if (shouldContinue || toStoppedState) {
                if (nextMedia == null) {
                    callback.onPlaybackEnded(null, true);
                    stop();
                }
                final boolean hasNext = nextMedia != null;

                executor.submit(() -> callback.onPostPlayback(currentMedia, hasEnded, wasSkipped, hasNext));
            } else if (isPlaying) {
                callback.onPlaybackPause(currentMedia, currentMedia.getPosition());
            }
            playerLock.unlock();
        });
    }

    /**
     * Moves the LocalPSMP into STOPPED state. This call is only valid if the player is currently in
     * INDETERMINATE state, for example after a call to endPlayback.
     * This method will only take care of changing the PlayerStatus of this object! Other tasks like
     * abandoning audio focus have to be done with other methods.
     */
    private void stop() {
        executor.submit(() -> {
            playerLock.lock();
            releaseWifiLockIfNecessary();

            if (playerStatus == PlayerStatus.INDETERMINATE) {
                setPlayerStatus(PlayerStatus.STOPPED, null);
            } else {
                Log.d(TAG, "Ignored call to stop: Current player state is: " + playerStatus);
            }
            playerLock.unlock();

        });
    }

    @Override
    protected boolean shouldLockWifi(){
        return stream;
    }

    private IPlayer setMediaPlayerListeners(IPlayer mp) {
        if (mp == null || media == null) {
            return mp;
        }
        if (mp instanceof VideoPlayer) {
            if (media.getMediaType() != MediaType.VIDEO) {
                Log.w(TAG, "video player, but media type is " + media.getMediaType());
            }
            VideoPlayer vp = (VideoPlayer) mp;
            vp.setOnCompletionListener(videoCompletionListener);
            vp.setOnSeekCompleteListener(videoSeekCompleteListener);
            vp.setOnErrorListener(videoErrorListener);
            vp.setOnBufferingUpdateListener(videoBufferingUpdateListener);
            vp.setOnInfoListener(videoInfoListener);
        } else if (mp instanceof AudioPlayer) {
            if (media.getMediaType() != MediaType.AUDIO) {
                Log.w(TAG, "audio player, but media type is " + media.getMediaType());
            }
            AudioPlayer ap = (AudioPlayer) mp;
            ap.setOnCompletionListener(audioCompletionListener);
            ap.setOnSeekCompleteListener(audioSeekCompleteListener);
            ap.setOnErrorListener(audioErrorListener);
            ap.setOnBufferingUpdateListener(audioBufferingUpdateListener);
            ap.setOnInfoListener(audioInfoListener);
        } else if (mp instanceof ExoPlayerWrapper) {
            ExoPlayerWrapper ap = (ExoPlayerWrapper) mp;
            ap.setOnCompletionListener(audioCompletionListener);
            ap.setOnSeekCompleteListener(audioSeekCompleteListener);
            ap.setOnBufferingUpdateListener(audioBufferingUpdateListener);
            ap.setOnErrorListener(audioErrorListener);
            ap.setOnInfoListener(audioInfoListener);
        } else {
            Log.w(TAG, "Unknown media player: " + mp);
        }
        return mp;
    }

    private final MediaPlayer.OnCompletionListener audioCompletionListener =
            mp -> genericOnCompletion();

    private final android.media.MediaPlayer.OnCompletionListener videoCompletionListener =
            mp -> genericOnCompletion();

    private void genericOnCompletion() {
        endPlayback(true, false, true, true);
    }

    private final MediaPlayer.OnBufferingUpdateListener audioBufferingUpdateListener =
            (mp, percent) -> genericOnBufferingUpdate(percent);

    private final android.media.MediaPlayer.OnBufferingUpdateListener videoBufferingUpdateListener =
            (mp, percent) -> genericOnBufferingUpdate(percent);

    private void genericOnBufferingUpdate(int percent) {
        callback.onBufferingUpdate(percent);
    }

    private final MediaPlayer.OnInfoListener audioInfoListener =
            (mp, what, extra) -> genericInfoListener(what);

    private final android.media.MediaPlayer.OnInfoListener videoInfoListener =
            (mp, what, extra) -> genericInfoListener(what);

    private boolean genericInfoListener(int what) {
        return callback.onMediaPlayerInfo(what, 0);
    }

    private final MediaPlayer.OnErrorListener audioErrorListener =
            (mp, what, extra) -> {
                if(mp != null && mp.canFallback()) {
                    mp.fallback();
                    return true;
                } else {
                    return genericOnError(mp, what, extra);
                }
            };

    private final android.media.MediaPlayer.OnErrorListener videoErrorListener = this::genericOnError;

    private boolean genericOnError(Object inObj, int what, int extra) {
        return callback.onMediaPlayerError(inObj, what, extra);
    }

    private final MediaPlayer.OnSeekCompleteListener audioSeekCompleteListener =
            mp -> genericSeekCompleteListener();

    private final android.media.MediaPlayer.OnSeekCompleteListener videoSeekCompleteListener =
            mp -> genericSeekCompleteListener();

    private void genericSeekCompleteListener() {
        Log.d(TAG, "genericSeekCompleteListener");
        if (seekLatch != null) {
            seekLatch.countDown();
        }

        Runnable r = () -> {
            playerLock.lock();
            if (playerStatus == PlayerStatus.PLAYING) {
                callback.onPlaybackStart(media, getPosition());
            }
            if (playerStatus == PlayerStatus.SEEKING) {
                setPlayerStatus(statusBeforeSeeking, media, getPosition());
            }
            playerLock.unlock();
        };

        if (useCallerThread) {
            r.run();
        } else {
            executor.submit(r);
        }
    }
}
