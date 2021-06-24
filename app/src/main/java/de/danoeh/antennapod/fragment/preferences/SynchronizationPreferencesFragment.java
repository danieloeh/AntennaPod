package de.danoeh.antennapod.fragment.preferences;

import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceFragmentCompat;

import java.io.File;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.PreferenceActivity;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.dialog.ChooseDataFolderDialog;

public class SynchronizationPreferencesFragment extends PreferenceFragmentCompat {
    private static final String TAG = "SynchronizationPrefFragment";

    private static final String PREF_SCREEN_GPODDER = "prefScreenGpodder";
    private static final String PREF_SCREEN_NEXTCLOUD_GPODDER = "prefScreenNextcloudGpodder";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_synchronization);
        setupStorageScreen();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((PreferenceActivity) getActivity()).getSupportActionBar().setTitle("Synchronization");
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void setupStorageScreen() {
        findPreference(PREF_SCREEN_GPODDER).setOnPreferenceClickListener(
                preference -> {
                    ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_gpodder);
                    return true;
                }
        );
        findPreference(PREF_SCREEN_NEXTCLOUD_GPODDER).setOnPreferenceClickListener(
                preference -> {
                    ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_nextcloud_gpodder);
                    return true;
                }
        );
    }
}
