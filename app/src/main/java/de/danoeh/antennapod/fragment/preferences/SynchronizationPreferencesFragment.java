package de.danoeh.antennapod.fragment.preferences;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.core.text.HtmlCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import android.text.Spanned;
import android.text.format.DateUtils;

import com.google.android.material.snackbar.Snackbar;
import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.exceptions.AndroidGetAccountsPermissionNotGranted;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppNotInstalledException;
import com.nextcloud.android.sso.exceptions.NoCurrentAccountSelectedException;
import com.nextcloud.android.sso.helper.SingleAccountHelper;
import com.nextcloud.android.sso.ui.UiExceptionManager;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.PreferenceActivity;
import de.danoeh.antennapod.core.event.SyncServiceEvent;
import de.danoeh.antennapod.core.preferences.GpodnetPreferences;
import de.danoeh.antennapod.core.sync.SyncService;
import de.danoeh.antennapod.dialog.AuthenticationDialog;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import static de.danoeh.antennapod.core.sync.SyncService.SYNC_PROVIDER_CHOICE_GPODDER_NET;
import static de.danoeh.antennapod.core.sync.SyncService.SYNC_PROVIDER_CHOICE_NEXTCLOUD;

public class SynchronizationPreferencesFragment extends PreferenceFragmentCompat {
    private static final String PREFERENCE_SYNCHRONIZATION_DESCRIPTION = "preference_synchronization_description";
    private static final String PREFERENCE_LOGIN = "pref_synchronization_authenticate";
    private static final String PREF_GPODNET_SETLOGIN_INFORMATION = "pref_gpodnet_setlogin_information";
    private static final String PREFERENCE_SYNC = "pref_synchronization_sync";
    private static final String PREFERENCE_FORCE_FULL_SYNC = "pref_synchronization_force_full_sync";
    private static final String PREFERENCE_LOGOUT = "pref_synchronization_logout";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_synchronization);
        setupScreen();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((PreferenceActivity) getActivity()).getSupportActionBar().setTitle(R.string.synchronization);
        updateScreen();
        EventBus.getDefault().register(this);
    }

    private int getIconForSelectedSyncProvider(String currentSyncProviderKey) {
        switch (currentSyncProviderKey) {
            case SYNC_PROVIDER_CHOICE_GPODDER_NET: return R.drawable.gpodder_icon;
            case SYNC_PROVIDER_CHOICE_NEXTCLOUD: return R.drawable.nextcloud_logo_svg;
            default: return R.drawable.ic_notification_sync;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        ((PreferenceActivity) getActivity()).getSupportActionBar().setSubtitle("");
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void syncStatusChanged(SyncServiceEvent event) {
        updateScreen();
        if (!GpodnetPreferences.loggedIn()) {
            return;
        }
        if (event.getMessageResId() == R.string.sync_status_error
                || event.getMessageResId() == R.string.sync_status_success) {
            updateLastSyncReport(SyncService.isLastSyncSuccessful(getContext()),
                    SyncService.getLastSyncAttempt(getContext()));
        } else {
            ((PreferenceActivity) getActivity()).getSupportActionBar().setSubtitle(event.getMessageResId());
        }
    }

    private void setupScreen() {
        final Activity activity = getActivity();

        findPreference(PREFERENCE_LOGIN).setOnPreferenceClickListener(preference -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.dialog_choose_sync_service_title);
            String[] syncServiceNames = {SYNC_PROVIDER_CHOICE_GPODDER_NET, "Nextcloud"};
            builder.setItems(syncServiceNames, (dialog, which) -> {
                String userSelect = SYNC_PROVIDER_CHOICE_GPODDER_NET;
                switch (which) {
                    case 0:
                        break;
                    case 1:
                        userSelect = SyncService.SYNC_PROVIDER_CHOICE_NEXTCLOUD;
                        break;
                    default:
                        break;
                }
                setSelectedSyncProvider(userSelect);
                if (userSelect.equals(SYNC_PROVIDER_CHOICE_GPODDER_NET)) {
                    new GpodderAuthenticationFragment().show(getChildFragmentManager(), GpodderAuthenticationFragment.TAG);
                    updateScreen();
                    return;
                }
                openNextcloudAccountChooser();
                updateScreen();
            });
            AlertDialog dialog = builder.create();
            dialog.show();
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
        findPreference(PREFERENCE_SYNC).setOnPreferenceClickListener(preference -> {
            SyncService.syncImmediately(getActivity().getApplicationContext());
            return true;
        });
        findPreference(PREFERENCE_FORCE_FULL_SYNC).setOnPreferenceClickListener(preference -> {
            SyncService.fullSync(getContext());
            return true;
        });
        findPreference(PREFERENCE_LOGOUT).setOnPreferenceClickListener(preference -> {
            GpodnetPreferences.logout();
            Snackbar.make(getView(), R.string.pref_gpodnet_logout_toast, Snackbar.LENGTH_LONG).show();
            SyncService.setIsProviderConnected(getContext(), false);
            setSelectedSyncProvider("n/a");
            updateScreen();
            return true;
        });
    }

    private void setSelectedSyncProvider(String userSelect) {
        SharedPreferences preferences = getActivity()
                .getSharedPreferences(SyncService.SHARED_PREFERENCES_SYNCHRONIZATION, Activity.MODE_PRIVATE);
        preferences.edit()
                .putString(SyncService.SHARED_PREFERENCE_SELECTED_SYNC_PROVIDER, userSelect)
                .apply();
    }

    private void updateScreen() {
        final boolean loggedIn = SyncService.isProviderConnected(getContext());
        findPreference(PREFERENCE_LOGIN).setVisible(!loggedIn);

        int icon = getIconForSelectedSyncProvider(getSelectedSyncProviderKey());
        Preference preferenceHeader = findPreference(PREFERENCE_SYNCHRONIZATION_DESCRIPTION);
        preferenceHeader.setIcon(icon);
        preferenceHeader.setSummary(getHeaderSummary(getSelectedSyncProviderKey()));

        boolean isGpodderServiceSelected = isGpodnetSyncProviderSelected();
        Preference gpodnetSetLoginPreference = findPreference(PREF_GPODNET_SETLOGIN_INFORMATION);
        gpodnetSetLoginPreference.setVisible(isGpodderServiceSelected);
        gpodnetSetLoginPreference.setEnabled(loggedIn);
        findPreference(PREFERENCE_SYNC).setEnabled(loggedIn);
        findPreference(PREFERENCE_FORCE_FULL_SYNC).setEnabled(loggedIn);
        findPreference(PREFERENCE_LOGOUT).setEnabled(loggedIn);
        if (loggedIn) {
            String format = getActivity().getString(R.string.pref_nextcloud_gpodder_login_status);
            String summary = String.format(format, getUsernameFromSelectedSyncProvider(getSelectedSyncProviderKey()),
                    getActivity().getString(R.string.pref_synchronization_logout_summary));
            Spanned formattedSummary = HtmlCompat.fromHtml(summary, HtmlCompat.FROM_HTML_MODE_LEGACY);
            findPreference(PREFERENCE_LOGOUT).setSummary(formattedSummary);
            updateLastSyncReport(SyncService.isLastSyncSuccessful(getContext()),
                    SyncService.getLastSyncAttempt(getContext()));
        } else {
            findPreference(PREFERENCE_LOGOUT).setSummary(null);
        }
    }

    private String getUsernameFromSelectedSyncProvider(String currentSyncProviderKey) {
        switch (currentSyncProviderKey) {
            case SYNC_PROVIDER_CHOICE_GPODDER_NET: return GpodnetPreferences.getUsername();
            case SYNC_PROVIDER_CHOICE_NEXTCLOUD:
                try {
                    return SingleAccountHelper.getCurrentSingleSignOnAccount(getContext()).name;
                } catch (NextcloudFilesAppAccountNotFoundException e) {
                    e.printStackTrace();
                    return "";
                } catch (NoCurrentAccountSelectedException e) {
                    e.printStackTrace();
                    return "";
                }
            default: return "";
        }

    }

    private int getHeaderSummary(String currentSyncProviderKey) {
        switch (currentSyncProviderKey) {
            case SYNC_PROVIDER_CHOICE_GPODDER_NET: return R.string.gpodnet_description;
            case SYNC_PROVIDER_CHOICE_NEXTCLOUD: return R.string.preference_synchronization_summary_nextcloud;
            default: return R.string.preference_synchronization_summary_unchoosen;
        }
    }

    private boolean isGpodnetSyncProviderSelected() {
        return getSelectedSyncProviderKey().equals(SYNC_PROVIDER_CHOICE_GPODDER_NET);
    }

    private String getSelectedSyncProviderKey() {
        return getActivity()
                .getSharedPreferences(SyncService.SHARED_PREFERENCES_SYNCHRONIZATION, Activity.MODE_PRIVATE)
                .getString(SyncService.SHARED_PREFERENCE_SELECTED_SYNC_PROVIDER, "n/a");
    }

    private void updateLastSyncReport(boolean successful, long lastTime) {
        String status = String.format("%1$s (%2$s)", getString(successful
                        ? R.string.gpodnetsync_pref_report_successful : R.string.gpodnetsync_pref_report_failed),
                DateUtils.getRelativeDateTimeString(getContext(),
                        lastTime, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, DateUtils.FORMAT_SHOW_TIME));
        ((PreferenceActivity) getActivity()).getSupportActionBar().setSubtitle(status);
    }

    private void openNextcloudAccountChooser() {
        try {
            AccountImporter.pickNewAccount(getActivity());
        } catch (NextcloudFilesAppNotInstalledException | AndroidGetAccountsPermissionNotGranted e) {
            UiExceptionManager.showDialogForException(getContext(), e);
        }
    }
}
