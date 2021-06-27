package de.danoeh.antennapod.fragment;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.greenrobot.eventbus.EventBus;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.core.dialog.ConfirmationDialog;
import de.danoeh.antennapod.core.event.settings.SkipIntroEndingChangedEvent;
import de.danoeh.antennapod.core.event.settings.SpeedPresetChangedEvent;
import de.danoeh.antennapod.core.event.settings.VolumeAdaptionChangedEvent;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.dialog.AuthenticationDialog;
import de.danoeh.antennapod.dialog.EpisodeFilterDialog;
import de.danoeh.antennapod.dialog.FeedPreferenceSkipDialog;
import de.danoeh.antennapod.dialog.TagSettingsDialog;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedFilter;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import io.reactivex.Maybe;
import io.reactivex.MaybeOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static de.danoeh.antennapod.model.feed.FeedPreferences.SPEED_USE_GLOBAL;

public class FeedSettingsFragment extends Fragment {
    private static final String TAG = "FeedSettingsFragment";
    private static final String EXTRA_FEED_ID = "de.danoeh.antennapod.extra.feedId";

    private Disposable disposable;

    public static FeedSettingsFragment newInstance(Feed feed) {
        FeedSettingsFragment fragment = new FeedSettingsFragment();
        Bundle arguments = new Bundle();
        arguments.putLong(EXTRA_FEED_ID, feed.getId());
        fragment.setArguments(arguments);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.feedsettings, container, false);
        long feedId = getArguments().getLong(EXTRA_FEED_ID);

        Toolbar toolbar = root.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());

        getParentFragmentManager().beginTransaction()
                .replace(R.id.settings_fragment_container,
                        FeedSettingsPreferenceFragment.newInstance(feedId), "settings_fragment")
                .commitAllowingStateLoss();

        disposable = Maybe.create((MaybeOnSubscribe<Feed>) emitter -> {
            Feed feed = DBReader.getFeed(feedId);
            if (feed != null) {
                emitter.onSuccess(feed);
            } else {
                emitter.onComplete();
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> toolbar.setSubtitle(result.getTitle()),
                        error -> Log.d(TAG, Log.getStackTraceString(error)),
                        () -> { });


        return root;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    public static class FeedSettingsPreferenceFragment extends PreferenceFragmentCompat {
        private static final CharSequence PREF_EPISODE_FILTER = "episodeFilter";
        private static final CharSequence PREF_SCREEN = "feedSettingsScreen";
        private static final CharSequence PREF_AUTHENTICATION = "authentication";
        private static final CharSequence PREF_AUTO_DELETE = "autoDelete";
        private static final CharSequence PREF_CATEGORY_AUTO_DOWNLOAD = "autoDownloadCategory";
        private static final String PREF_FEED_PLAYBACK_SPEED = "feedPlaybackSpeed";
        private static final String PREF_AUTO_SKIP = "feedAutoSkip";
        private static final String PREF_TAGS = "tags";
        private static final DecimalFormat SPEED_FORMAT =
                new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));

        private Feed feed;
        private Disposable disposable;
        private FeedPreferences feedPreferences;

        public static FeedSettingsPreferenceFragment newInstance(long feedId) {
            FeedSettingsPreferenceFragment fragment = new FeedSettingsPreferenceFragment();
            Bundle arguments = new Bundle();
            arguments.putLong(EXTRA_FEED_ID, feedId);
            fragment.setArguments(arguments);
            return fragment;
        }

        @Override
        public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
            final RecyclerView view = super.onCreateRecyclerView(inflater, parent, state);
            // To prevent transition animation because of summary update
            view.setItemAnimator(null);
            view.setLayoutAnimation(null);
            return view;
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.feed_settings);
            // To prevent displaying partially loaded data
            findPreference(PREF_SCREEN).setVisible(false);

            long feedId = getArguments().getLong(EXTRA_FEED_ID);
            disposable = Maybe.create((MaybeOnSubscribe<Feed>) emitter -> {
                Feed feed = DBReader.getFeed(feedId);
                if (feed != null) {
                    emitter.onSuccess(feed);
                } else {
                    emitter.onComplete();
                }
            })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(result -> {
                        feed = result;
                        feedPreferences = feed.getPreferences();

                        setupAutoDownloadGlobalPreference();
                        setupAutoDownloadPreference();
                        setupKeepUpdatedPreference();
                        setupAutoDeletePreference();
                        setupVolumeReductionPreferences();
                        setupAuthentificationPreference();
                        setupEpisodeFilterPreference();
                        setupPlaybackSpeedPreference();
                        setupFeedAutoSkipPreference();
                        setupEpisodeNotificationPreference();
                        setupTags();

                        updateAutoDeleteSummary();
                        updateVolumeReductionValue();
                        updateAutoDownloadEnabled();
                        updatePlaybackSpeedPreference();

                        if (feed.isLocalFeed()) {
                            findPreference(PREF_AUTHENTICATION).setVisible(false);
                            findPreference(PREF_AUTO_DELETE).setVisible(false);
                            findPreference(PREF_CATEGORY_AUTO_DOWNLOAD).setVisible(false);
                        }

                        findPreference(PREF_SCREEN).setVisible(true);
                    }, error -> Log.d(TAG, Log.getStackTraceString(error)), () -> { });
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (disposable != null) {
                disposable.dispose();
            }
        }

        private void setupFeedAutoSkipPreference() {
            findPreference(PREF_AUTO_SKIP).setOnPreferenceClickListener(preference -> {
                new FeedPreferenceSkipDialog(getContext(),
                        feedPreferences.getFeedSkipIntro(),
                        feedPreferences.getFeedSkipEnding()) {
                    @Override
                    protected void onConfirmed(int skipIntro, int skipEnding) {
                        feedPreferences.setFeedSkipIntro(skipIntro);
                        feedPreferences.setFeedSkipEnding(skipEnding);
                        DBWriter.setFeedPreferences(feedPreferences);
                        EventBus.getDefault().post(
                                new SkipIntroEndingChangedEvent(feedPreferences.getFeedSkipIntro(),
                                        feedPreferences.getFeedSkipEnding(),
                                        feed.getId()));
                    }
                }.show();
                return false;
            });
        }

        private void setupPlaybackSpeedPreference() {
            ListPreference feedPlaybackSpeedPreference = findPreference(PREF_FEED_PLAYBACK_SPEED);

            final String[] speeds = getResources().getStringArray(R.array.playback_speed_values);
            String[] values = new String[speeds.length + 1];
            values[0] = SPEED_FORMAT.format(SPEED_USE_GLOBAL);

            String[] entries = new String[speeds.length + 1];
            entries[0] = getString(R.string.feed_auto_download_global);

            System.arraycopy(speeds, 0, values, 1, speeds.length);
            System.arraycopy(speeds, 0, entries, 1, speeds.length);

            feedPlaybackSpeedPreference.setEntryValues(values);
            feedPlaybackSpeedPreference.setEntries(entries);
            feedPlaybackSpeedPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                feedPreferences.setFeedPlaybackSpeed(Float.parseFloat((String) newValue));
                DBWriter.setFeedPreferences(feedPreferences);
                updatePlaybackSpeedPreference();
                EventBus.getDefault().post(
                        new SpeedPresetChangedEvent(feedPreferences.getFeedPlaybackSpeed(), feed.getId()));
                return false;
            });
        }

        private void setupEpisodeFilterPreference() {
            findPreference(PREF_EPISODE_FILTER).setOnPreferenceClickListener(preference -> {
                new EpisodeFilterDialog(getContext(), feedPreferences.getFilter()) {
                    @Override
                    protected void onConfirmed(FeedFilter filter) {
                        feedPreferences.setFilter(filter);
                        DBWriter.setFeedPreferences(feedPreferences);
                    }
                }.show();
                return false;
            });
        }

        private void setupAuthentificationPreference() {
            findPreference(PREF_AUTHENTICATION).setOnPreferenceClickListener(preference -> {
                new AuthenticationDialog(getContext(),
                        R.string.authentication_label, true,
                        feedPreferences.getUsername(), feedPreferences.getPassword()) {
                    @Override
                    protected void onConfirmed(String username, String password) {
                        feedPreferences.setUsername(username);
                        feedPreferences.setPassword(password);
                        DBWriter.setFeedPreferences(feedPreferences);
                    }
                }.show();
                return false;
            });
        }

        private void setupAutoDeletePreference() {
            findPreference(PREF_AUTO_DELETE).setOnPreferenceChangeListener((preference, newValue) -> {
                switch ((String) newValue) {
                    case "global":
                        feedPreferences.setAutoDeleteAction(FeedPreferences.AutoDeleteAction.GLOBAL);
                        break;
                    case "always":
                        feedPreferences.setAutoDeleteAction(FeedPreferences.AutoDeleteAction.YES);
                        break;
                    case "never":
                        feedPreferences.setAutoDeleteAction(FeedPreferences.AutoDeleteAction.NO);
                        break;
                }
                DBWriter.setFeedPreferences(feedPreferences);
                updateAutoDeleteSummary();
                return false;
            });
        }

        private void updatePlaybackSpeedPreference() {
            ListPreference feedPlaybackSpeedPreference = findPreference(PREF_FEED_PLAYBACK_SPEED);

            float speedValue = feedPreferences.getFeedPlaybackSpeed();
            feedPlaybackSpeedPreference.setValue(SPEED_FORMAT.format(speedValue));
        }

        private void updateAutoDeleteSummary() {
            ListPreference autoDeletePreference = findPreference(PREF_AUTO_DELETE);

            switch (feedPreferences.getAutoDeleteAction()) {
                case GLOBAL:
                    autoDeletePreference.setSummary(R.string.feed_auto_download_global);
                    autoDeletePreference.setValue("global");
                    break;
                case YES:
                    autoDeletePreference.setSummary(R.string.feed_auto_download_always);
                    autoDeletePreference.setValue("always");
                    break;
                case NO:
                    autoDeletePreference.setSummary(R.string.feed_auto_download_never);
                    autoDeletePreference.setValue("never");
                    break;
            }
        }

        private void setupVolumeReductionPreferences() {
            ListPreference volumeReductionPreference = findPreference("volumeReduction");
            volumeReductionPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                switch ((String) newValue) {
                    case "off":
                        feedPreferences.setVolumeAdaptionSetting(VolumeAdaptionSetting.OFF);
                        break;
                    case "light":
                        feedPreferences.setVolumeAdaptionSetting(VolumeAdaptionSetting.LIGHT_REDUCTION);
                        break;
                    case "heavy":
                        feedPreferences.setVolumeAdaptionSetting(VolumeAdaptionSetting.HEAVY_REDUCTION);
                        break;
                }
                DBWriter.setFeedPreferences(feedPreferences);
                updateVolumeReductionValue();
                EventBus.getDefault().post(
                        new VolumeAdaptionChangedEvent(feedPreferences.getVolumeAdaptionSetting(), feed.getId()));
                return false;
            });
        }

        private void updateVolumeReductionValue() {
            ListPreference volumeReductionPreference = findPreference("volumeReduction");

            switch (feedPreferences.getVolumeAdaptionSetting()) {
                case OFF:
                    volumeReductionPreference.setValue("off");
                    break;
                case LIGHT_REDUCTION:
                    volumeReductionPreference.setValue("light");
                    break;
                case HEAVY_REDUCTION:
                    volumeReductionPreference.setValue("heavy");
                    break;
            }
        }

        private void setupKeepUpdatedPreference() {
            SwitchPreferenceCompat pref = findPreference("keepUpdated");

            pref.setChecked(feedPreferences.getKeepUpdated());
            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean checked = newValue == Boolean.TRUE;
                feedPreferences.setKeepUpdated(checked);
                DBWriter.setFeedPreferences(feedPreferences);
                pref.setChecked(checked);
                return false;
            });
        }

        private void setupAutoDownloadGlobalPreference() {
            if (!UserPreferences.isEnableAutodownload()) {
                SwitchPreferenceCompat autodl = findPreference("autoDownload");
                autodl.setChecked(false);
                autodl.setEnabled(false);
                autodl.setSummary(R.string.auto_download_disabled_globally);
                findPreference(PREF_EPISODE_FILTER).setEnabled(false);
            }
        }

        private void setupAutoDownloadPreference() {
            SwitchPreferenceCompat pref = findPreference("autoDownload");

            pref.setEnabled(UserPreferences.isEnableAutodownload());
            if (UserPreferences.isEnableAutodownload()) {
                pref.setChecked(feedPreferences.getAutoDownload());
            } else {
                pref.setChecked(false);
                pref.setSummary(R.string.auto_download_disabled_globally);
            }

            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean checked = newValue == Boolean.TRUE;

                feedPreferences.setAutoDownload(checked);
                DBWriter.setFeedPreferences(feedPreferences);
                updateAutoDownloadEnabled();
                ApplyToEpisodesDialog dialog = new ApplyToEpisodesDialog(getActivity(), checked);
                dialog.createNewDialog().show();
                pref.setChecked(checked);
                return false;
            });
        }

        private void updateAutoDownloadEnabled() {
            if (feed != null && feed.getPreferences() != null) {
                boolean enabled = feed.getPreferences().getAutoDownload() && UserPreferences.isEnableAutodownload();
                findPreference(PREF_EPISODE_FILTER).setEnabled(enabled);
            }
        }

        private void setupTags() {
            findPreference(PREF_TAGS).setOnPreferenceClickListener(preference -> {
                TagSettingsDialog.newInstance(feedPreferences).show(getChildFragmentManager(), TagSettingsDialog.TAG);
                return true;
            });
        }

        private void setupEpisodeNotificationPreference() {
            SwitchPreferenceCompat pref = findPreference("episodeNotification");

            pref.setChecked(feedPreferences.getShowEpisodeNotification());
            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean checked = newValue == Boolean.TRUE;
                feedPreferences.setShowEpisodeNotification(checked);
                DBWriter.setFeedPreferences(feedPreferences);
                pref.setChecked(checked);
                return false;
            });
        }

        private class ApplyToEpisodesDialog extends ConfirmationDialog {
            private final boolean autoDownload;

            ApplyToEpisodesDialog(Context context, boolean autoDownload) {
                super(context, R.string.auto_download_apply_to_items_title,
                        R.string.auto_download_apply_to_items_message);
                this.autoDownload = autoDownload;
                setPositiveText(R.string.yes);
                setNegativeText(R.string.no);
            }

            @Override
            public void onConfirmButtonPressed(DialogInterface dialog) {
                DBWriter.setFeedsItemsAutoDownload(feed, autoDownload);
            }
        }
    }
}
