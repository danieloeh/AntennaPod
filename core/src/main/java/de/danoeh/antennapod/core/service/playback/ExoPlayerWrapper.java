package de.danoeh.antennapod.core.service.playback;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider;
import com.google.android.exoplayer2.ui.TrackNameProvider;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;

import de.danoeh.antennapod.core.ClientConfig;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.BasicAuthorizationInterceptor;
import de.danoeh.antennapod.core.util.playback.IPlayer;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import org.antennapod.audio.MediaPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ExoPlayerWrapper implements IPlayer {
    private static final String TAG = "ExoPlayerWrapper";
    public static final int ERROR_CODE_OFFSET = 1000;
    private final Context context;
    private final Disposable bufferingUpdateDisposable;
    private SimpleExoPlayer exoPlayer;
    private MediaSource mediaSource;
    private MediaPlayer.OnSeekCompleteListener audioSeekCompleteListener;
    private MediaPlayer.OnCompletionListener audioCompletionListener;
    private MediaPlayer.OnErrorListener audioErrorListener;
    private MediaPlayer.OnBufferingUpdateListener bufferingUpdateListener;
    private PlaybackParameters playbackParameters;
    private MediaPlayer.OnInfoListener infoListener;
    private DefaultTrackSelector trackSelector;

    ExoPlayerWrapper(Context context) {
        this.context = context;
        createPlayer();
        playbackParameters = exoPlayer.getPlaybackParameters();
        bufferingUpdateDisposable = Observable.interval(2, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(tickNumber -> {
                    if (bufferingUpdateListener != null) {
                        bufferingUpdateListener.onBufferingUpdate(null, exoPlayer.getBufferedPercentage());
                    }
                });
    }

    private void createPlayer() {
        DefaultLoadControl.Builder loadControl = new DefaultLoadControl.Builder();
        loadControl.setBufferDurationsMs(30000, 120000,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS);
        loadControl.setBackBuffer(UserPreferences.getRewindSecs() * 1000 + 500, true);
        trackSelector = new DefaultTrackSelector(context);
        exoPlayer = new SimpleExoPlayer.Builder(context, new DefaultRenderersFactory(context))
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl.createDefaultLoadControl())
                .build();
        exoPlayer.setSeekParameters(SeekParameters.EXACT);
        exoPlayer.addListener(new Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (audioCompletionListener != null && playbackState == Player.STATE_ENDED) {
                    audioCompletionListener.onCompletion(null);
                } else if (infoListener != null && playbackState == Player.STATE_BUFFERING) {
                    infoListener.onInfo(null, android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START, 0);
                } else if (infoListener != null) {
                    infoListener.onInfo(null, android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END, 0);
                }
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                if (audioErrorListener != null) {
                    audioErrorListener.onError(null, error.type + ERROR_CODE_OFFSET, 0);
                }
            }

            @Override
            public void onSeekProcessed() {
                if (audioSeekCompleteListener != null) {
                    audioSeekCompleteListener.onSeekComplete(null);
                }
            }
        });
    }

    @Override
    public boolean canDownmix() {
        return false;
    }

    @Override
    public int getCurrentPosition() {
        return (int) exoPlayer.getCurrentPosition();
    }

    @Override
    public float getCurrentSpeedMultiplier() {
        return playbackParameters.speed;
    }

    @Override
    public int getDuration() {
        if (exoPlayer.getDuration() == C.TIME_UNSET) {
            return PlaybackServiceMediaPlayer.INVALID_TIME;
        }
        return (int) exoPlayer.getDuration();
    }

    @Override
    public boolean isPlaying() {
        return exoPlayer.getPlayWhenReady();
    }

    @Override
    public void pause() {
        exoPlayer.setPlayWhenReady(false);
    }

    @Override
    public void prepare() throws IllegalStateException {
        exoPlayer.prepare(mediaSource, false, true);
    }

    @Override
    public void release() {
        bufferingUpdateDisposable.dispose();
        if (exoPlayer != null) {
            exoPlayer.release();
        }
        audioSeekCompleteListener = null;
        audioCompletionListener = null;
        audioErrorListener = null;
        bufferingUpdateListener = null;
    }

    @Override
    public void reset() {
        exoPlayer.release();
        createPlayer();
    }

    @Override
    public void seekTo(int i) throws IllegalStateException {
        exoPlayer.seekTo(i);
        if (audioSeekCompleteListener != null) {
            audioSeekCompleteListener.onSeekComplete(null);
        }
    }

    @Override
    public void setAudioStreamType(int i) {
        AudioAttributes a = exoPlayer.getAudioAttributes();
        AudioAttributes.Builder b = new AudioAttributes.Builder();
        b.setContentType(i);
        b.setFlags(a.flags);
        b.setUsage(a.usage);
        exoPlayer.setAudioAttributes(b.build());
    }

    public void setDataSource(String s, String user, String password)
            throws IllegalArgumentException, IllegalStateException {
        Log.d(TAG, "setDataSource: " + s);
        DefaultHttpDataSourceFactory httpDataSourceFactory = new DefaultHttpDataSourceFactory(
                ClientConfig.USER_AGENT, null,
                DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                true);

        if (!TextUtils.isEmpty(user) && !TextUtils.isEmpty(password)) {
            httpDataSourceFactory.getDefaultRequestProperties().set("Authorization",
                    BasicAuthorizationInterceptor.encodeCredentials(
                            user,
                            password,
                            "ISO-8859-1"));
        }
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context, null, httpDataSourceFactory);
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
        extractorsFactory.setConstantBitrateSeekingEnabled(true);
        extractorsFactory.setMp3ExtractorFlags(Mp3Extractor.FLAG_DISABLE_ID3_METADATA);
        ProgressiveMediaSource.Factory f = new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory);
        mediaSource = f.createMediaSource(Uri.parse(s));
    }

    @Override
    public void setDataSource(String s) throws IllegalArgumentException, IllegalStateException {
        setDataSource(s, null, null);
    }

    @Override
    public void setDisplay(SurfaceHolder sh) {
        exoPlayer.setVideoSurfaceHolder(sh);
    }

    @Override
    public void setPlaybackParams(float speed, boolean skipSilence) {
        playbackParameters = new PlaybackParameters(speed, playbackParameters.pitch, skipSilence);
        exoPlayer.setPlaybackParameters(playbackParameters);
    }

    @Override
    public void setDownmix(boolean b) {

    }

    @Override
    public void setVolume(float v, float v1) {
        exoPlayer.setVolume(v);
    }

    @Override
    public void setWakeMode(Context context, int i) {

    }

    @Override
    public void start() {
        exoPlayer.setPlayWhenReady(true);
        // Can't set params when paused - so always set it on start in case they changed
        exoPlayer.setPlaybackParameters(playbackParameters);
    }

    @Override
    public void stop() {
        exoPlayer.stop();
    }

    @Override
    public List<String> getAudioTracks() {
        List<String> trackNames = new ArrayList<>();
        TrackNameProvider trackNameProvider = new DefaultTrackNameProvider(context.getResources());
        for (Format format : getFormats()) {
            trackNames.add(trackNameProvider.getTrackName(format));
        }
        return trackNames;
    }

    private List<Format> getFormats() {
        List<Format> formats = new ArrayList<>();
        MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (trackInfo == null) {
            return Collections.emptyList();
        }
        TrackGroupArray trackGroups = trackInfo.getTrackGroups(getAudioRendererIndex());
        for (int i = 0; i < trackGroups.length; i++) {
            formats.add(trackGroups.get(i).getFormat(0));
        }
        return formats;
    }

    @Override
    public void setAudioTrack(int track) {
        MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (trackInfo == null) {
            return;
        }
        TrackGroupArray trackGroups = trackInfo.getTrackGroups(getAudioRendererIndex());
        DefaultTrackSelector.SelectionOverride override = new DefaultTrackSelector.SelectionOverride(track, 0);
        DefaultTrackSelector.ParametersBuilder params = trackSelector.buildUponParameters()
                .setSelectionOverride(getAudioRendererIndex(), trackGroups, override);
        trackSelector.setParameters(params);
    }

    private int getAudioRendererIndex() {
        for (int i = 0; i < exoPlayer.getRendererCount(); i++) {
            if (exoPlayer.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getSelectedAudioTrack() {
        TrackSelectionArray trackSelections = exoPlayer.getCurrentTrackSelections();
        List<Format> availableFormats = getFormats();
        for (int i = 0; i < trackSelections.length; i++) {
            TrackSelection track = trackSelections.get(i);
            if (track == null) {
                continue;
            }
            if (availableFormats.contains(track.getSelectedFormat())) {
                return availableFormats.indexOf(track.getSelectedFormat());
            }
        }
        return -1;
    }

    void setOnCompletionListener(MediaPlayer.OnCompletionListener audioCompletionListener) {
        this.audioCompletionListener = audioCompletionListener;
    }

    void setOnSeekCompleteListener(MediaPlayer.OnSeekCompleteListener audioSeekCompleteListener) {
        this.audioSeekCompleteListener = audioSeekCompleteListener;
    }

    void setOnErrorListener(MediaPlayer.OnErrorListener audioErrorListener) {
        this.audioErrorListener = audioErrorListener;
    }

    int getVideoWidth() {
        if (exoPlayer.getVideoFormat() == null) {
            return 0;
        }
        return exoPlayer.getVideoFormat().width;
    }

    int getVideoHeight() {
        if (exoPlayer.getVideoFormat() == null) {
            return 0;
        }
        return exoPlayer.getVideoFormat().height;
    }

    void setOnBufferingUpdateListener(MediaPlayer.OnBufferingUpdateListener bufferingUpdateListener) {
        this.bufferingUpdateListener = bufferingUpdateListener;
    }

    public void setOnInfoListener(MediaPlayer.OnInfoListener infoListener) {
        this.infoListener = infoListener;
    }
}
