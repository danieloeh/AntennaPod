package de.danoeh.antennapod.core.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.core.R;
import de.danoeh.antennapod.core.feed.MediaType;
import de.danoeh.antennapod.core.service.download.ProxyConfig;
import de.danoeh.antennapod.core.storage.APCleanupAlgorithm;
import de.danoeh.antennapod.core.storage.APNullCleanupAlgorithm;
import de.danoeh.antennapod.core.storage.APQueueCleanupAlgorithm;
import de.danoeh.antennapod.core.storage.EpisodeCleanupAlgorithm;
import de.danoeh.antennapod.core.util.Converter;
import de.danoeh.antennapod.core.util.SortOrder;
import de.danoeh.antennapod.core.util.download.AutoUpdateManager;

/**
 * Provides access to preferences set by the user in the settings screen. A
 * private instance of this class must first be instantiated via
 * init() or otherwise every public method will throw an Exception
 * when called.
 */
public class UserPreferences {
    private UserPreferences(){}

    private static final String IMPORT_DIR = "import/";

    private static final String TAG = "UserPreferences";

    // User Interface
    public static final String PREF_THEME = "prefTheme";
    public static final String PREF_HIDDEN_DRAWER_ITEMS = "prefHiddenDrawerItems";
    private static final String PREF_DRAWER_FEED_ORDER = "prefDrawerFeedOrder";
    private static final String PREF_DRAWER_FEED_COUNTER = "prefDrawerFeedIndicator";
    public static final String PREF_EXPANDED_NOTIFICATION = "prefExpandNotify";
    public static final String PREF_USE_EPISODE_COVER = "prefEpisodeCover";
    private static final String PREF_PERSISTENT_NOTIFICATION = "prefPersistNotify";
    public static final String PREF_COMPACT_NOTIFICATION_BUTTONS = "prefCompactNotificationButtons";
    public static final String PREF_LOCKSCREEN_BACKGROUND = "prefLockscreenBackground";
    private static final String PREF_SHOW_DOWNLOAD_REPORT = "prefShowDownloadReport";
    private static final String PREF_SHOW_AUTO_DOWNLOAD_REPORT = "prefShowAutoDownloadReport";
    public static final String PREF_BACK_BUTTON_BEHAVIOR = "prefBackButtonBehavior";
    private static final String PREF_BACK_BUTTON_GO_TO_PAGE = "prefBackButtonGoToPage";

    public static final String PREF_QUEUE_KEEP_SORTED = "prefQueueKeepSorted";
    public static final String PREF_QUEUE_KEEP_SORTED_ORDER = "prefQueueKeepSortedOrder";

    // Playback
    public static final String PREF_PAUSE_ON_HEADSET_DISCONNECT = "prefPauseOnHeadsetDisconnect";
    public static final String PREF_UNPAUSE_ON_HEADSET_RECONNECT = "prefUnpauseOnHeadsetReconnect";
    private static final String PREF_UNPAUSE_ON_BLUETOOTH_RECONNECT = "prefUnpauseOnBluetoothReconnect";
    private static final String PREF_HARDWARE_FOWARD_BUTTON_SKIPS = "prefHardwareForwardButtonSkips";
    private static final String PREF_HARDWARE_PREVIOUS_BUTTON_RESTARTS = "prefHardwarePreviousButtonRestarts";
    public static final String PREF_FOLLOW_QUEUE = "prefFollowQueue";
    public static final String PREF_SKIP_KEEPS_EPISODE = "prefSkipKeepsEpisode";
    private static final String PREF_FAVORITE_KEEPS_EPISODE = "prefFavoriteKeepsEpisode";
    private static final String PREF_AUTO_DELETE = "prefAutoDelete";
    public static final String PREF_SMART_MARK_AS_PLAYED_SECS = "prefSmartMarkAsPlayedSecs";
    private static final String PREF_PLAYBACK_SPEED_ARRAY = "prefPlaybackSpeedArray";
    private static final String PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS = "prefPauseForFocusLoss";
    private static final String PREF_RESUME_AFTER_CALL = "prefResumeAfterCall";
    public static final String PREF_VIDEO_BEHAVIOR = "prefVideoBehavior";
    private static final String PREF_TIME_RESPECTS_SPEED = "prefPlaybackTimeRespectsSpeed";
    public static final String PREF_STREAM_OVER_DOWNLOAD = "prefStreamOverDownload";
    private static final String PREF_AUTO_ADD_PLAYING_MEDIA = "prefAutoAddPlaying";

    // Network
    private static final String PREF_ENQUEUE_DOWNLOADED = "prefEnqueueDownloaded";
    public static final String PREF_ENQUEUE_LOCATION = "prefEnqueueLocation";
    public static final String PREF_UPDATE_INTERVAL = "prefAutoUpdateIntervall";
    private static final String PREF_MOBILE_UPDATE = "prefMobileUpdateTypes";
    public static final String PREF_EPISODE_CLEANUP = "prefEpisodeCleanup";
    public static final String PREF_PARALLEL_DOWNLOADS = "prefParallelDownloads";
    public static final String PREF_EPISODE_CACHE_SIZE = "prefEpisodeCacheSize";
    public static final String PREF_ENABLE_AUTODL = "prefEnableAutoDl";
    public static final String PREF_ENABLE_AUTODL_ON_BATTERY = "prefEnableAutoDownloadOnBattery";
    public static final String PREF_ENABLE_AUTODL_WIFI_FILTER = "prefEnableAutoDownloadWifiFilter";
    private static final String PREF_AUTODL_SELECTED_NETWORKS = "prefAutodownloadSelectedNetworks";
    private static final String PREF_PROXY_TYPE = "prefProxyType";
    private static final String PREF_PROXY_HOST = "prefProxyHost";
    private static final String PREF_PROXY_PORT = "prefProxyPort";
    private static final String PREF_PROXY_USER = "prefProxyUser";
    private static final String PREF_PROXY_PASSWORD = "prefProxyPassword";

    // Services
    private static final String PREF_GPODNET_NOTIFICATIONS = "pref_gpodnet_notifications";

    // Other
    private static final String PREF_DATA_FOLDER = "prefDataFolder";
    public static final String PREF_IMAGE_CACHE_SIZE = "prefImageCacheSize";
    public static final String PREF_DELETE_REMOVES_FROM_QUEUE = "prefDeleteRemovesFromQueue";

    // Mediaplayer
    public static final String PREF_MEDIA_PLAYER = "prefMediaPlayer";
    public static final String PREF_MEDIA_PLAYER_EXOPLAYER = "exoplayer";
    private static final String PREF_PLAYBACK_SPEED = "prefPlaybackSpeed";
    private static final String PREF_VIDEO_PLAYBACK_SPEED = "prefVideoPlaybackSpeed";
    public static final String PREF_PLAYBACK_SKIP_SILENCE = "prefSkipSilence";
    private static final String PREF_FAST_FORWARD_SECS = "prefFastForwardSecs";
    private static final String PREF_REWIND_SECS = "prefRewindSecs";
    private static final String PREF_QUEUE_LOCKED = "prefQueueLocked";
    private static final String IMAGE_CACHE_DEFAULT_VALUE = "100";
    private static final int IMAGE_CACHE_SIZE_MINIMUM = 20;
    private static final String PREF_LEFT_VOLUME = "prefLeftVolume";
    private static final String PREF_RIGHT_VOLUME = "prefRightVolume";

    // Experimental
    private static final String PREF_STEREO_TO_MONO = "PrefStereoToMono";
    public static final String PREF_CAST_ENABLED = "prefCast"; //Used for enabling Chromecast support
    public static final int EPISODE_CLEANUP_QUEUE = -1;
    public static final int EPISODE_CLEANUP_NULL = -2;
    public static final int EPISODE_CLEANUP_DEFAULT = 0;

    // Constants
    private static final int NOTIFICATION_BUTTON_REWIND = 0;
    private static final int NOTIFICATION_BUTTON_FAST_FORWARD = 1;
    private static final int NOTIFICATION_BUTTON_SKIP = 2;
    private static final int EPISODE_CACHE_SIZE_UNLIMITED = -1;
    public static final int FEED_ORDER_COUNTER = 0;
    public static final int FEED_ORDER_ALPHABETICAL = 1;
    public static final int FEED_ORDER_MOST_PLAYED = 3;
    public static final int FEED_COUNTER_SHOW_NEW_UNPLAYED_SUM = 0;
    public static final int FEED_COUNTER_SHOW_NEW = 1;
    public static final int FEED_COUNTER_SHOW_UNPLAYED = 2;
    public static final int FEED_COUNTER_SHOW_NONE = 3;
    public static final int FEED_COUNTER_SHOW_DOWNLOADED = 4;

    private static Context context;
    private static SharedPreferences prefs;

    /**
     * Sets up the UserPreferences class.
     *
     * @throws IllegalArgumentException if context is null
     */
    public static void init(@NonNull Context context) {
        Log.d(TAG, "Creating new instance of UserPreferences");

        UserPreferences.context = context.getApplicationContext();
        UserPreferences.prefs = PreferenceManager.getDefaultSharedPreferences(context);

        createImportDirectory();
        createNoMediaFile();
    }

    /**
     * Returns theme as R.style value
     *
     * @return R.style.Theme_AntennaPod_Light or R.style.Theme_AntennaPod_Dark
     */
    public static int getTheme() {
        return readThemeValue(prefs.getString(PREF_THEME, "system"));
    }

    public static int getNoTitleTheme() {
        int theme = getTheme();
        if (theme == R.style.Theme_AntennaPod_Dark) {
            return R.style.Theme_AntennaPod_Dark_NoTitle;
        } else if (theme == R.style.Theme_AntennaPod_TrueBlack) {
            return R.style.Theme_AntennaPod_TrueBlack_NoTitle;
        } else {
            return R.style.Theme_AntennaPod_Light_NoTitle;
        }
    }

    public static int getTranslucentTheme() {
        int theme = getTheme();
        if (theme == R.style.Theme_AntennaPod_Dark) {
            return R.style.Theme_AntennaPod_Dark_Translucent;
        } else if (theme == R.style.Theme_AntennaPod_TrueBlack) {
            return R.style.Theme_AntennaPod_TrueBlack_Translucent;
        } else {
            return R.style.Theme_AntennaPod_Light_Translucent;
        }
    }

    public static List<String> getHiddenDrawerItems() {
        String hiddenItems = prefs.getString(PREF_HIDDEN_DRAWER_ITEMS, "");
        return new ArrayList<>(Arrays.asList(TextUtils.split(hiddenItems, ",")));
    }

    public static List<Integer> getCompactNotificationButtons() {
        String[] buttons = TextUtils.split(
                prefs.getString(PREF_COMPACT_NOTIFICATION_BUTTONS,
                        String.valueOf(NOTIFICATION_BUTTON_SKIP)),
                ",");
        List<Integer> notificationButtons = new ArrayList<>();
        for (String button : buttons) {
            notificationButtons.add(Integer.parseInt(button));
        }
        return notificationButtons;
    }

    /**
     * Helper function to return whether the specified button should be shown on compact
     * notifications.
     *
     * @param buttonId Either NOTIFICATION_BUTTON_REWIND, NOTIFICATION_BUTTON_FAST_FORWARD or
     *                 NOTIFICATION_BUTTON_SKIP.
     * @return {@code true} if button should be shown, {@code false}  otherwise
     */
    private static boolean showButtonOnCompactNotification(int buttonId) {
        return getCompactNotificationButtons().contains(buttonId);
    }

    public static boolean showRewindOnCompactNotification() {
        return showButtonOnCompactNotification(NOTIFICATION_BUTTON_REWIND);
    }

    public static boolean showFastForwardOnCompactNotification() {
        return showButtonOnCompactNotification(NOTIFICATION_BUTTON_FAST_FORWARD);
    }

    public static boolean showSkipOnCompactNotification() {
        return showButtonOnCompactNotification(NOTIFICATION_BUTTON_SKIP);
    }

    public static int getFeedOrder() {
        String value = prefs.getString(PREF_DRAWER_FEED_ORDER, "" + FEED_ORDER_COUNTER);
        return Integer.parseInt(value);
    }

    public static int getFeedCounterSetting() {
        String value = prefs.getString(PREF_DRAWER_FEED_COUNTER, "" + FEED_COUNTER_SHOW_NEW);
        return Integer.parseInt(value);
    }

    /**
     * @return {@code true} if episodes should use their own cover, {@code false}  otherwise
     */
    public static boolean getUseEpisodeCoverSetting() {
        return prefs.getBoolean(PREF_USE_EPISODE_COVER, true);
    }

    /**
     * Returns notification priority.
     *
     * @return NotificationCompat.PRIORITY_MAX or NotificationCompat.PRIORITY_DEFAULT
     */
    public static int getNotifyPriority() {
        if (prefs.getBoolean(PREF_EXPANDED_NOTIFICATION, false)) {
            return NotificationCompat.PRIORITY_MAX;
        } else {
            return NotificationCompat.PRIORITY_DEFAULT;
        }
    }

    /**
     * Returns true if notifications are persistent
     *
     * @return {@code true} if notifications are persistent, {@code false}  otherwise
     */
    public static boolean isPersistNotify() {
        return prefs.getBoolean(PREF_PERSISTENT_NOTIFICATION, true);
    }

    /**
     * Returns true if the lockscreen background should be set to the current episode's image
     *
     * @return {@code true} if the lockscreen background should be set, {@code false}  otherwise
     */
    public static boolean setLockscreenBackground() {
        return prefs.getBoolean(PREF_LOCKSCREEN_BACKGROUND, true);
    }

    /**
     * Returns true if download reports are shown
     *
     * @return {@code true} if download reports are shown, {@code false}  otherwise
     */
    public static boolean showDownloadReport() {
        return prefs.getBoolean(PREF_SHOW_DOWNLOAD_REPORT, true);
    }

    public static boolean showAutoDownloadReport() {
        return prefs.getBoolean(PREF_SHOW_AUTO_DOWNLOAD_REPORT, false);
    }

    public static boolean enqueueDownloadedEpisodes() {
        return prefs.getBoolean(PREF_ENQUEUE_DOWNLOADED, true);
    }

    @VisibleForTesting
    public static void setEnqueueDownloadedEpisodes(boolean enqueueDownloadedEpisodes) {
        prefs.edit()
                .putBoolean(PREF_ENQUEUE_DOWNLOADED, enqueueDownloadedEpisodes)
                .apply();
    }

    public enum EnqueueLocation {
        BACK, FRONT, AFTER_CURRENTLY_PLAYING
    }

    @NonNull
    public static EnqueueLocation getEnqueueLocation() {
        String valStr = prefs.getString(PREF_ENQUEUE_LOCATION, EnqueueLocation.BACK.name());
        try {
            return EnqueueLocation.valueOf(valStr);
        } catch (Throwable t) {
            // should never happen but just in case
            Log.e(TAG, "getEnqueueLocation: invalid value '" + valStr + "' Use default.", t);
            return EnqueueLocation.BACK;
        }
    }

    public static void setEnqueueLocation(@NonNull EnqueueLocation location) {
        prefs.edit()
                .putString(PREF_ENQUEUE_LOCATION, location.name())
                .apply();
    }

    public static boolean isPauseOnHeadsetDisconnect() {
        return prefs.getBoolean(PREF_PAUSE_ON_HEADSET_DISCONNECT, true);
    }

    public static boolean isUnpauseOnHeadsetReconnect() {
        return prefs.getBoolean(PREF_UNPAUSE_ON_HEADSET_RECONNECT, true);
    }

    public static boolean isUnpauseOnBluetoothReconnect() {
        return prefs.getBoolean(PREF_UNPAUSE_ON_BLUETOOTH_RECONNECT, false);
    }

    public static boolean shouldHardwareButtonSkip() {
        return prefs.getBoolean(PREF_HARDWARE_FOWARD_BUTTON_SKIPS, false);
    }

    public static boolean shouldHardwarePreviousButtonRestart() {
        return prefs.getBoolean(PREF_HARDWARE_PREVIOUS_BUTTON_RESTARTS, false);
    }


    public static boolean isFollowQueue() {
        return prefs.getBoolean(PREF_FOLLOW_QUEUE, true);
    }

    /**
     * Set to true to enable Continuous Playback
     */
    @VisibleForTesting
    public static void setFollowQueue(boolean value) {
        prefs.edit().putBoolean(UserPreferences.PREF_FOLLOW_QUEUE, value).apply();
    }

    public static boolean shouldSkipKeepEpisode() { return prefs.getBoolean(PREF_SKIP_KEEPS_EPISODE, true); }

    public static boolean shouldFavoriteKeepEpisode() {
        return prefs.getBoolean(PREF_FAVORITE_KEEPS_EPISODE, true);
    }

    public static boolean isAutoDelete() {
        return prefs.getBoolean(PREF_AUTO_DELETE, false);
    }

    public static int getSmartMarkAsPlayedSecs() {
        return Integer.parseInt(prefs.getString(PREF_SMART_MARK_AS_PLAYED_SECS, "30"));
    }

    public static boolean shouldDeleteRemoveFromQueue() {
        return prefs.getBoolean(PREF_DELETE_REMOVES_FROM_QUEUE, false);
    }

    public static float getPlaybackSpeed(MediaType mediaType) {
        if (mediaType == MediaType.VIDEO) {
            return getVideoPlaybackSpeed();
        } else {
            return getAudioPlaybackSpeed();
        }
    }

    private static float getAudioPlaybackSpeed() {
        try {
            return Float.parseFloat(prefs.getString(PREF_PLAYBACK_SPEED, "1.00"));
        } catch (NumberFormatException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            UserPreferences.setPlaybackSpeed(1.0f);
            return 1.0f;
        }
    }

    private static float getVideoPlaybackSpeed() {
        try {
            return Float.parseFloat(prefs.getString(PREF_VIDEO_PLAYBACK_SPEED, "1.00"));
        } catch (NumberFormatException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            UserPreferences.setVideoPlaybackSpeed(1.0f);
            return 1.0f;
        }
    }

    public static boolean isSkipSilence() {
        return prefs.getBoolean(PREF_PLAYBACK_SKIP_SILENCE, false);
    }

    public static List<Float> getPlaybackSpeedArray() {
        return readPlaybackSpeedArray(prefs.getString(PREF_PLAYBACK_SPEED_ARRAY, null));
    }

    public static float getLeftVolume() {
        int volume = prefs.getInt(PREF_LEFT_VOLUME, 100);
        return Converter.getVolumeFromPercentage(volume);
    }

    public static float getRightVolume() {
        int volume = prefs.getInt(PREF_RIGHT_VOLUME, 100);
        return Converter.getVolumeFromPercentage(volume);
    }

    public static int getLeftVolumePercentage() {
        return prefs.getInt(PREF_LEFT_VOLUME, 100);
    }

    public static int getRightVolumePercentage() {
        return prefs.getInt(PREF_RIGHT_VOLUME, 100);
    }

    public static boolean shouldPauseForFocusLoss() {
        return prefs.getBoolean(PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, false);
    }


    /*
     * Returns update interval in milliseconds; value 0 means that auto update is disabled
     * or feeds are updated at a certain time of day
     */
    public static long getUpdateInterval() {
        String updateInterval = prefs.getString(PREF_UPDATE_INTERVAL, "0");
        if(!updateInterval.contains(":")) {
            return readUpdateInterval(updateInterval);
        } else {
            return 0;
        }
    }

    public static int[] getUpdateTimeOfDay() {
        String datetime = prefs.getString(PREF_UPDATE_INTERVAL, "");
        if(datetime.length() >= 3 && datetime.contains(":")) {
            String[] parts = datetime.split(":");
            int hourOfDay = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            return new int[] { hourOfDay, minute };
        } else {
            return new int[0];
        }
    }

    public static boolean isAutoUpdateDisabled() {
        return prefs.getString(PREF_UPDATE_INTERVAL, "").equals("0");
    }

    private static boolean isAllowMobileFor(String type) {
        HashSet<String> defaultValue = new HashSet<>();
        defaultValue.add("images");
        Set<String> allowed = prefs.getStringSet(PREF_MOBILE_UPDATE, defaultValue);
        return allowed.contains(type);
    }

    public static boolean isAllowMobileFeedRefresh() {
        return isAllowMobileFor("feed_refresh");
    }

    public static boolean isAllowMobileEpisodeDownload() {
        return isAllowMobileFor("episode_download");
    }

    public static boolean isAllowMobileAutoDownload() {
        return isAllowMobileFor("auto_download");
    }

    public static boolean isAllowMobileStreaming() {
        return isAllowMobileFor("streaming");
    }

    public static boolean isAllowMobileImages() {
        return isAllowMobileFor("images");
    }

    private static void setAllowMobileFor(String type, boolean allow) {
        HashSet<String> defaultValue = new HashSet<>();
        defaultValue.add("images");
        Set<String> allowed = prefs.getStringSet(PREF_MOBILE_UPDATE, defaultValue);
        if (allow) {
            allowed.add(type);
        } else {
            allowed.remove(type);
        }
        prefs.edit().putStringSet(PREF_MOBILE_UPDATE, allowed).apply();
    }

    public static void setAllowMobileFeedRefresh(boolean allow) {
        setAllowMobileFor("feed_refresh", allow);
    }

    public static void setAllowMobileEpisodeDownload(boolean allow) {
        setAllowMobileFor("episode_download", allow);
    }

    public static void setAllowMobileAutoDownload(boolean allow) {
        setAllowMobileFor("auto_download", allow);
    }

    public static void setAllowMobileStreaming(boolean allow) {
        setAllowMobileFor("streaming", allow);
    }

    public static void setAllowMobileImages(boolean allow) {
        setAllowMobileFor("images", allow);
    }

    public static int getParallelDownloads() {
        return Integer.parseInt(prefs.getString(PREF_PARALLEL_DOWNLOADS, "4"));
    }

    public static int getEpisodeCacheSizeUnlimited() {
        return context.getResources().getInteger(R.integer.episode_cache_size_unlimited);
    }

    /**
     * Returns the capacity of the episode cache. This method will return the
     * negative integer EPISODE_CACHE_SIZE_UNLIMITED if the cache size is set to
     * 'unlimited'.
     */
    public static int getEpisodeCacheSize() {
        return readEpisodeCacheSizeInternal(prefs.getString(PREF_EPISODE_CACHE_SIZE, "20"));
    }

    public static boolean isEnableAutodownload() {
        return prefs.getBoolean(PREF_ENABLE_AUTODL, false);
    }

    @VisibleForTesting
    public static void setEnableAutodownload(boolean enabled) {
        prefs.edit().putBoolean(PREF_ENABLE_AUTODL, enabled).apply();
    }

    public static boolean isEnableAutodownloadOnBattery() {
        return prefs.getBoolean(PREF_ENABLE_AUTODL_ON_BATTERY, true);
    }

    public static boolean isEnableAutodownloadWifiFilter() {
        return Build.VERSION.SDK_INT < 29 && prefs.getBoolean(PREF_ENABLE_AUTODL_WIFI_FILTER, false);
    }

    public static int getImageCacheSize() {
        String cacheSizeString = prefs.getString(PREF_IMAGE_CACHE_SIZE, IMAGE_CACHE_DEFAULT_VALUE);
        int cacheSizeInt = Integer.parseInt(cacheSizeString);
        // if the cache size is too small the user won't get any images at all
        // that's bad, force it back to the default.
        if (cacheSizeInt < IMAGE_CACHE_SIZE_MINIMUM) {
            prefs.edit().putString(PREF_IMAGE_CACHE_SIZE, IMAGE_CACHE_DEFAULT_VALUE).apply();
            cacheSizeInt = Integer.parseInt(IMAGE_CACHE_DEFAULT_VALUE);
        }
        return cacheSizeInt * 1024 * 1024;
    }

    public static int getFastForwardSecs() {
        return prefs.getInt(PREF_FAST_FORWARD_SECS, 30);
    }

    public static int getRewindSecs() {
        return prefs.getInt(PREF_REWIND_SECS, 10);
    }

    public static String[] getAutodownloadSelectedNetworks() {
        String selectedNetWorks = prefs.getString(PREF_AUTODL_SELECTED_NETWORKS, "");
        return TextUtils.split(selectedNetWorks, ",");
    }

    public static void setProxyConfig(ProxyConfig config) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_PROXY_TYPE, config.type.name());
        if(TextUtils.isEmpty(config.host)) {
            editor.remove(PREF_PROXY_HOST);
        } else {
            editor.putString(PREF_PROXY_HOST, config.host);
        }
        if(config.port <= 0 || config.port > 65535) {
            editor.remove(PREF_PROXY_PORT);
        } else {
            editor.putInt(PREF_PROXY_PORT, config.port);
        }
        if(TextUtils.isEmpty(config.username)) {
            editor.remove(PREF_PROXY_USER);
        } else {
            editor.putString(PREF_PROXY_USER, config.username);
        }
        if(TextUtils.isEmpty(config.password)) {
            editor.remove(PREF_PROXY_PASSWORD);
        } else {
            editor.putString(PREF_PROXY_PASSWORD, config.password);
        }
        editor.apply();
    }

    public static ProxyConfig getProxyConfig() {
        Proxy.Type type = Proxy.Type.valueOf(prefs.getString(PREF_PROXY_TYPE, Proxy.Type.DIRECT.name()));
        String host = prefs.getString(PREF_PROXY_HOST, null);
        int port = prefs.getInt(PREF_PROXY_PORT, 0);
        String username = prefs.getString(PREF_PROXY_USER, null);
        String password = prefs.getString(PREF_PROXY_PASSWORD, null);
        return new ProxyConfig(type, host, port, username, password);
    }

    public static boolean shouldResumeAfterCall() {
        return prefs.getBoolean(PREF_RESUME_AFTER_CALL, true);
    }

    public static boolean isQueueLocked() {
        return prefs.getBoolean(PREF_QUEUE_LOCKED, false);
    }

    public static void setFastForwardSecs(int secs) {
        prefs.edit()
             .putInt(PREF_FAST_FORWARD_SECS, secs)
             .apply();
    }

    public static void setRewindSecs(int secs) {
        prefs.edit()
             .putInt(PREF_REWIND_SECS, secs)
             .apply();
    }

    public static void setPlaybackSpeed(float speed) {
        prefs.edit()
             .putString(PREF_PLAYBACK_SPEED, String.valueOf(speed))
             .apply();
    }

    public static void setVideoPlaybackSpeed(float speed) {
        prefs.edit()
                .putString(PREF_VIDEO_PLAYBACK_SPEED, String.valueOf(speed))
                .apply();
    }

    public static void setSkipSilence(boolean skipSilence) {
        prefs.edit()
                .putBoolean(PREF_PLAYBACK_SKIP_SILENCE, skipSilence)
                .apply();
    }

    public static void setPlaybackSpeedArray(List<Float> speeds) {
        DecimalFormatSymbols format = new DecimalFormatSymbols(Locale.US);
        format.setDecimalSeparator('.');
        DecimalFormat speedFormat = new DecimalFormat("0.00", format);
        JSONArray jsonArray = new JSONArray();
        for (float speed : speeds) {
            jsonArray.put(speedFormat.format(speed));
        }
        prefs.edit()
             .putString(PREF_PLAYBACK_SPEED_ARRAY, jsonArray.toString())
             .apply();
    }

    public static void setVolume(@IntRange(from = 0, to = 100) int leftVolume,
                                 @IntRange(from = 0, to = 100) int rightVolume) {
        prefs.edit()
             .putInt(PREF_LEFT_VOLUME, leftVolume)
             .putInt(PREF_RIGHT_VOLUME, rightVolume)
             .apply();
    }

    public static void setAutodownloadSelectedNetworks(String[] value) {
        prefs.edit()
             .putString(PREF_AUTODL_SELECTED_NETWORKS, TextUtils.join(",", value))
             .apply();
    }

    /**
     * Sets the update interval value.
     */
    public static void setUpdateInterval(long hours) {
        prefs.edit()
             .putString(PREF_UPDATE_INTERVAL, String.valueOf(hours))
             .apply();
        // when updating with an interval, we assume the user wants
        // to update *now* and then every 'hours' interval thereafter.
        AutoUpdateManager.restartUpdateAlarm(context);
    }

    /**
     * Sets the update interval value.
     */
    public static void setUpdateTimeOfDay(int hourOfDay, int minute) {
        prefs.edit()
             .putString(PREF_UPDATE_INTERVAL, hourOfDay + ":" + minute)
             .apply();
        AutoUpdateManager.restartUpdateAlarm(context);
    }

    public static void disableAutoUpdate(Context context) {
        prefs.edit()
                .putString(PREF_UPDATE_INTERVAL, "0")
                .apply();
        AutoUpdateManager.disableAutoUpdate(context);
    }

    public static boolean gpodnetNotificationsEnabled() {
        return prefs.getBoolean(PREF_GPODNET_NOTIFICATIONS, true);
    }

    public static void setGpodnetNotificationsEnabled() {
        prefs.edit()
                .putBoolean(PREF_GPODNET_NOTIFICATIONS, true)
                .apply();
    }

    public static void setHiddenDrawerItems(List<String> items) {
        String str = TextUtils.join(",", items);
        prefs.edit()
             .putString(PREF_HIDDEN_DRAWER_ITEMS, str)
             .apply();
    }

    public static void setCompactNotificationButtons(List<Integer> items) {
        String str = TextUtils.join(",", items);
        prefs.edit()
             .putString(PREF_COMPACT_NOTIFICATION_BUTTONS, str)
             .apply();
    }

    public static void setQueueLocked(boolean locked) {
        prefs.edit()
             .putBoolean(PREF_QUEUE_LOCKED, locked)
             .apply();
    }

    private static int readThemeValue(String valueFromPrefs) {
        switch (valueFromPrefs) {
            case "0":
                return R.style.Theme_AntennaPod_Light;
            case "1":
                return R.style.Theme_AntennaPod_Dark;
            case "2":
                return R.style.Theme_AntennaPod_TrueBlack;
            default:
                int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                    return R.style.Theme_AntennaPod_Dark;
                }
                return R.style.Theme_AntennaPod_Light;
        }
    }

    private static long readUpdateInterval(String valueFromPrefs) {
        int hours = Integer.parseInt(valueFromPrefs);
        return TimeUnit.HOURS.toMillis(hours);
    }

    private static int readEpisodeCacheSizeInternal(String valueFromPrefs) {
        if (valueFromPrefs.equals(context.getString(R.string.pref_episode_cache_unlimited))) {
            return EPISODE_CACHE_SIZE_UNLIMITED;
        } else {
            return Integer.parseInt(valueFromPrefs);
        }
    }

    private static List<Float> readPlaybackSpeedArray(String valueFromPrefs) {
        if (valueFromPrefs != null) {
            try {
                JSONArray jsonArray = new JSONArray(valueFromPrefs);
                List<Float> selectedSpeeds = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    selectedSpeeds.add((float) jsonArray.getDouble(i));
                }
                return selectedSpeeds;
            } catch (JSONException e) {
                Log.e(TAG, "Got JSON error when trying to get speeds from JSONArray");
                e.printStackTrace();
            }
        }
        // If this preference hasn't been set yet, return the default options
        return Arrays.asList(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f);
    }

    public static String getMediaPlayer() {
        return prefs.getString(PREF_MEDIA_PLAYER, PREF_MEDIA_PLAYER_EXOPLAYER);
    }

    public static boolean useSonic() {
        return getMediaPlayer().equals("sonic");
    }

    public static boolean useExoplayer() {
        return getMediaPlayer().equals(PREF_MEDIA_PLAYER_EXOPLAYER);
    }

    public static void enableSonic() {
        prefs.edit().putString(PREF_MEDIA_PLAYER, "sonic").apply();
    }

    public static void enableExoplayer() {
        prefs.edit().putString(PREF_MEDIA_PLAYER, PREF_MEDIA_PLAYER_EXOPLAYER).apply();
    }

    public static boolean stereoToMono() {
        return prefs.getBoolean(PREF_STEREO_TO_MONO, false);
    }

    public static void stereoToMono(boolean enable) {
        prefs.edit()
                .putBoolean(PREF_STEREO_TO_MONO, enable)
                .apply();
    }

    public static VideoBackgroundBehavior getVideoBackgroundBehavior() {
        switch (prefs.getString(PREF_VIDEO_BEHAVIOR, "pip")) {
            case "stop": return VideoBackgroundBehavior.STOP;
            case "continue": return VideoBackgroundBehavior.CONTINUE_PLAYING;
            case "pip": //Deliberate fall-through
            default: return VideoBackgroundBehavior.PICTURE_IN_PICTURE;
        }
    }

    public static EpisodeCleanupAlgorithm getEpisodeCleanupAlgorithm() {
        if (!isEnableAutodownload()) {
            return new APNullCleanupAlgorithm();
        }
        int cleanupValue = getEpisodeCleanupValue();
        if (cleanupValue == EPISODE_CLEANUP_QUEUE) {
            return new APQueueCleanupAlgorithm();
        } else if (cleanupValue == EPISODE_CLEANUP_NULL) {
            return new APNullCleanupAlgorithm();
        } else {
            return new APCleanupAlgorithm(cleanupValue);
        }
    }

    public static int getEpisodeCleanupValue() {
        return Integer.parseInt(prefs.getString(PREF_EPISODE_CLEANUP, "" + EPISODE_CLEANUP_NULL));
    }

    public static void setEpisodeCleanupValue(int episodeCleanupValue) {
        prefs.edit()
                .putString(PREF_EPISODE_CLEANUP, Integer.toString(episodeCleanupValue))
                .apply();
    }

    /**
     * Return the folder where the app stores all of its data. This method will
     * return the standard data folder if none has been set by the user.
     *
     * @param type The name of the folder inside the data folder. May be null
     *             when accessing the root of the data folder.
     * @return The data folder that has been requested or null if the folder
     * could not be created.
     */
    public static File getDataFolder(String type) {
        String strDir = prefs.getString(PREF_DATA_FOLDER, null);
        if (strDir == null) {
            Log.d(TAG, "Using default data folder");
            return context.getExternalFilesDir(type);
        } else {
            File dataDir = new File(strDir);
            if (!dataDir.exists()) {
                if (!dataDir.mkdir()) {
                    Log.w(TAG, "Could not create data folder");
                    return null;
                }
            }

            if (type == null) {
                return dataDir;
            } else {
                // handle path separators
                String[] dirs = type.split("/");
                for (int i = 0; i < dirs.length; i++) {
                    if (dirs.length > 0) {
                        if (i < dirs.length - 1) {
                            dataDir = getDataFolder(dirs[i]);
                            if (dataDir == null) {
                                return null;
                            }
                        }
                        type = dirs[i];
                    }
                }
                File typeDir = new File(dataDir, type);
                if (!typeDir.exists()) {
                    if (dataDir.canWrite()) {
                        if (!typeDir.mkdir()) {
                            Log.e(TAG, "Could not create data folder named " + type);
                            return null;
                        }
                    }
                }
                return typeDir;
            }
        }
    }

    public static void setDataFolder(String dir) {
        Log.d(TAG, "setDataFolder(dir: " + dir + ")");
        prefs.edit()
             .putString(PREF_DATA_FOLDER, dir)
             .apply();
        createImportDirectory();
    }

    /**
     * Create a .nomedia file to prevent scanning by the media scanner.
     */
    private static void createNoMediaFile() {
        File f = new File(context.getExternalFilesDir(null), ".nomedia");
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                Log.e(TAG, "Could not create .nomedia file");
                e.printStackTrace();
            }
            Log.d(TAG, ".nomedia file created");
        }
    }

    /**
     * Creates the import directory if it doesn't exist and if storage is
     * available
     */
    private static void createImportDirectory() {
        File importDir = getDataFolder(IMPORT_DIR);
        if (importDir != null) {
            if (importDir.exists()) {
                Log.d(TAG, "Import directory already exists");
            } else {
                Log.d(TAG, "Creating import directory");
                importDir.mkdir();
            }
        } else {
            Log.d(TAG, "Could not access external storage.");
        }
    }

    /**
     *
     * @return true if auto update is set to a specific time
     *         false if auto update is set to interval
     */
    public static boolean isAutoUpdateTimeOfDay() {
        return getUpdateTimeOfDay().length == 2;
    }

    /**
     * Evaluates whether Cast support (Chromecast, Audio Cast, etc) is enabled on the preferences.
     */
    public static boolean isCastEnabled() {
        return prefs.getBoolean(PREF_CAST_ENABLED, false);
    }

    public enum VideoBackgroundBehavior {
        STOP, PICTURE_IN_PICTURE, CONTINUE_PLAYING
    }

    public enum BackButtonBehavior {
        DEFAULT, OPEN_DRAWER, DOUBLE_TAP, SHOW_PROMPT, GO_TO_PAGE
    }

    public static BackButtonBehavior getBackButtonBehavior() {
        switch (prefs.getString(PREF_BACK_BUTTON_BEHAVIOR, "default")) {
            case "drawer": return BackButtonBehavior.OPEN_DRAWER;
            case "doubletap": return BackButtonBehavior.DOUBLE_TAP;
            case "prompt": return BackButtonBehavior.SHOW_PROMPT;
            case "page": return BackButtonBehavior.GO_TO_PAGE;
            case "default": // Deliberate fall-through
            default: return BackButtonBehavior.DEFAULT;
        }
    }

    public static String getBackButtonGoToPage() {
        return prefs.getString(PREF_BACK_BUTTON_GO_TO_PAGE, "QueueFragment");
    }

    public static void setBackButtonGoToPage(String tag) {
        prefs.edit()
                .putString(PREF_BACK_BUTTON_GO_TO_PAGE, tag)
                .apply();
    }

    public static boolean timeRespectsSpeed() {
        return prefs.getBoolean(PREF_TIME_RESPECTS_SPEED, false);
    }

    public static boolean isStreamOverDownload() {
        return prefs.getBoolean(PREF_STREAM_OVER_DOWNLOAD, false);
    }

    public static void setStreamOverDownload(boolean stream) {
        prefs.edit().putBoolean(PREF_STREAM_OVER_DOWNLOAD, stream).apply();
    }

    /**
     * Returns if the queue is in keep sorted mode.
     *
     * @see #getQueueKeepSortedOrder()
     */
    public static boolean isQueueKeepSorted() {
        return prefs.getBoolean(PREF_QUEUE_KEEP_SORTED, false);
    }

    /**
     * Enables/disables the keep sorted mode of the queue.
     *
     * @see #setQueueKeepSortedOrder(SortOrder)
     */
    public static void setQueueKeepSorted(boolean keepSorted) {
        prefs.edit()
                .putBoolean(PREF_QUEUE_KEEP_SORTED, keepSorted)
                .apply();
    }

    /**
     * Returns the sort order for the queue keep sorted mode.
     * Note: This value is stored independently from the keep sorted state.
     *
     * @see #isQueueKeepSorted()
     */
    public static SortOrder getQueueKeepSortedOrder() {
        String sortOrderStr = prefs.getString(PREF_QUEUE_KEEP_SORTED_ORDER, "use-default");
        return SortOrder.parseWithDefault(sortOrderStr, SortOrder.DATE_NEW_OLD);
    }

    /**
     * Sets the sort order for the queue keep sorted mode.
     *
     * @see #setQueueKeepSorted(boolean)
     */
    public static void setQueueKeepSortedOrder(SortOrder sortOrder) {
        if (sortOrder == null) {
            return;
        }
        prefs.edit()
                .putString(PREF_QUEUE_KEEP_SORTED_ORDER, sortOrder.name())
                .apply();
    }

    public static boolean getAutoAddPlayingMedia() {
        return prefs.getBoolean(PREF_AUTO_ADD_PLAYING_MEDIA, true);
    }
}
