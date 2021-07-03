package de.danoeh.antennapod.fragment.preferences;

import android.app.Activity;
import android.os.Bundle;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceFragmentCompat;

import android.text.Spanned;
import android.text.format.DateUtils;
import com.google.android.material.snackbar.Snackbar;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.PreferenceActivity;
import de.danoeh.antennapod.core.event.SyncServiceEvent;
import de.danoeh.antennapod.core.preferences.GpodnetPreferences;
import de.danoeh.antennapod.core.sync.SyncService;
import de.danoeh.antennapod.dialog.AuthenticationDialog;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class GpodderPreferencesFragment extends PreferenceFragmentCompat {
    private static final String PREF_GPODNET_LOGIN = "pref_gpodnet_authenticate";
    private static final String PREF_GPODNET_SETLOGIN_INFORMATION = "pref_gpodnet_setlogin_information";
    private static final String PREF_GPODNET_SYNC = "pref_gpodnet_sync";
    private static final String PREF_GPODNET_FORCE_FULL_SYNC = "pref_gpodnet_force_full_sync";
    private static final String PREF_GPODNET_LOGOUT = "pref_gpodnet_logout";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_gpodder);
        setupGpodderScreen();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((PreferenceActivity) getActivity()).getSupportActionBar().setTitle(R.string.gpodnet_main_label);
        updateGpodnetPreferenceScreen();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        ((PreferenceActivity) getActivity()).getSupportActionBar().setSubtitle("");
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void syncStatusChanged(SyncServiceEvent event) {
        updateGpodnetPreferenceScreen();
        if (!GpodnetPreferences.loggedIn()) {
            return;
        }
        if (event.getMessageResId() == R.string.sync_status_error
                || event.getMessageResId() == R.string.sync_status_success) {
            updateLastGpodnetSyncReport(SyncService.isLastSyncSuccessful(getContext()),
                    SyncService.getLastSyncAttempt(getContext()));
        } else {
            ((PreferenceActivity) getActivity()).getSupportActionBar().setSubtitle(event.getMessageResId());
        }
    }

    private void setupGpodderScreen() {
        final Activity activity = getActivity();

        findPreference(PREF_GPODNET_LOGIN).setOnPreferenceClickListener(preference -> {
            new GpodderAuthenticationFragment().show(getChildFragmentManager(), GpodderAuthenticationFragment.TAG);
            return true;
        });
        findPreference(PREF_GPODNET_SETLOGIN_INFORMATION)
                .setOnPreferenceClickListener(preference -> {
                    AuthenticationDialog dialog = new AuthenticationDialog(activity,
                            R.string.pref_gpodnet_setlogin_information_title, false, GpodnetPreferences.getUsername(),
                            null) {

                        @Override
                        protected void onConfirmed(String username, String password) {
                            GpodnetPreferences.setPassword(password);
                            SyncService.setIsProviderConnected(getContext(), true);
                        }
                    };
                    dialog.show();
                    return true;
                });
        findPreference(PREF_GPODNET_SYNC).setOnPreferenceClickListener(preference -> {
            SyncService.syncImmediately(getActivity().getApplicationContext());
            return true;
        });
        findPreference(PREF_GPODNET_FORCE_FULL_SYNC).setOnPreferenceClickListener(preference -> {
            SyncService.fullSync(getContext());
            return true;
        });
        findPreference(PREF_GPODNET_LOGOUT).setOnPreferenceClickListener(preference -> {
            GpodnetPreferences.logout();
            Snackbar.make(getView(), R.string.pref_gpodnet_logout_toast, Snackbar.LENGTH_LONG).show();
            SyncService.setIsProviderConnected(getContext(), false);
            updateGpodnetPreferenceScreen();
            return true;
        });
    }

    private void updateGpodnetPreferenceScreen() {
        final boolean loggedIn = GpodnetPreferences.loggedIn();
        findPreference(PREF_GPODNET_LOGIN).setEnabled(!loggedIn);
        findPreference(PREF_GPODNET_SETLOGIN_INFORMATION).setEnabled(loggedIn);
        findPreference(PREF_GPODNET_SYNC).setEnabled(loggedIn);
        findPreference(PREF_GPODNET_FORCE_FULL_SYNC).setEnabled(loggedIn);
        findPreference(PREF_GPODNET_LOGOUT).setEnabled(loggedIn);
        if (loggedIn) {
            String format = getActivity().getString(R.string.pref_gpodnet_login_status);
            String summary = String.format(format, GpodnetPreferences.getUsername(),
                    GpodnetPreferences.getDeviceID());
            Spanned formattedSummary = HtmlCompat.fromHtml(summary, HtmlCompat.FROM_HTML_MODE_LEGACY);
            findPreference(PREF_GPODNET_LOGOUT).setSummary(formattedSummary);
            updateLastGpodnetSyncReport(SyncService.isLastSyncSuccessful(getContext()),
                    SyncService.getLastSyncAttempt(getContext()));
        } else {
            findPreference(PREF_GPODNET_LOGOUT).setSummary(null);
        }
    }

    private void updateLastGpodnetSyncReport(boolean successful, long lastTime) {
        String status = String.format("%1$s (%2$s)", getString(successful
                        ? R.string.gpodnetsync_pref_report_successful : R.string.gpodnetsync_pref_report_failed),
                        DateUtils.getRelativeDateTimeString(getContext(),
                        lastTime, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, DateUtils.FORMAT_SHOW_TIME));
        ((PreferenceActivity) getActivity()).getSupportActionBar().setSubtitle(status);
    }
}
