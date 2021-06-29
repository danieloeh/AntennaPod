package de.danoeh.antennapod.activity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;

import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import com.bytehamster.lib.preferencesearch.SearchPreferenceResult;
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener;
import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.exceptions.AccountImportCancelledException;
import com.nextcloud.android.sso.helper.SingleAccountHelper;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.databinding.SettingsActivityBinding;
import de.danoeh.antennapod.fragment.preferences.AutoDownloadPreferencesFragment;
import de.danoeh.antennapod.fragment.preferences.GpodderPreferencesFragment;
import de.danoeh.antennapod.fragment.preferences.ImportExportPreferencesFragment;
import de.danoeh.antennapod.fragment.preferences.MainPreferencesFragment;
import de.danoeh.antennapod.fragment.preferences.NetworkPreferencesFragment;
import de.danoeh.antennapod.fragment.preferences.NextcloudPreferencesFragment;
import de.danoeh.antennapod.fragment.preferences.NotificationPreferencesFragment;
import de.danoeh.antennapod.fragment.preferences.PlaybackPreferencesFragment;
import de.danoeh.antennapod.fragment.preferences.StoragePreferencesFragment;
import de.danoeh.antennapod.fragment.preferences.SwipePreferencesFragment;
import de.danoeh.antennapod.fragment.preferences.SynchronizationPreferencesFragment;
import de.danoeh.antennapod.fragment.preferences.UserInterfacePreferencesFragment;

/**
 * PreferenceActivity for API 11+. In order to change the behavior of the preference UI, see
 * PreferenceController.
 */
public class PreferenceActivity extends AppCompatActivity implements SearchPreferenceResultListener {
    private static final String FRAGMENT_TAG = "tag_preferences";
    public static final String OPEN_AUTO_DOWNLOAD_SETTINGS = "OpenAutoDownloadSettings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(UserPreferences.getTheme());
        super.onCreate(savedInstanceState);

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        final SettingsActivityBinding binding = SettingsActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settingsContainer, new MainPreferencesFragment(), FRAGMENT_TAG)
                    .commit();
        }
        Intent intent = getIntent();
        if (intent.getBooleanExtra(OPEN_AUTO_DOWNLOAD_SETTINGS, false)) {
            openScreen(R.xml.preferences_autodownload);
        }
    }

    private PreferenceFragmentCompat getPreferenceScreen(int screen) {
        PreferenceFragmentCompat prefFragment = null;

        if (screen == R.xml.preferences_user_interface) {
            prefFragment = new UserInterfacePreferencesFragment();
        } else if (screen == R.xml.preferences_network) {
            prefFragment = new NetworkPreferencesFragment();
        } else if (screen == R.xml.preferences_storage) {
            prefFragment = new StoragePreferencesFragment();
        } else if (screen == R.xml.preferences_import_export) {
            prefFragment = new ImportExportPreferencesFragment();
        } else if (screen == R.xml.preferences_autodownload) {
            prefFragment = new AutoDownloadPreferencesFragment();
        } else if (screen == R.xml.preferences_synchronization) {
            prefFragment = new SynchronizationPreferencesFragment();
        } else if (screen == R.xml.preferences_gpodder) {
            prefFragment = new GpodderPreferencesFragment();
        } else if (screen == R.xml.preferences_nextcloud_gpodder) {
            prefFragment = new NextcloudPreferencesFragment();
        } else if (screen == R.xml.preferences_playback) {
            prefFragment = new PlaybackPreferencesFragment();
        } else if (screen == R.xml.preferences_notifications) {
            prefFragment = new NotificationPreferencesFragment();
        } else if (screen == R.xml.preferences_swipe) {
            prefFragment = new SwipePreferencesFragment();
        }
        return prefFragment;
    }

    public static int getTitleOfPage(int preferences) {
        switch (preferences) {
            case R.xml.preferences_network:
                return R.string.network_pref;
            case R.xml.preferences_autodownload:
                return R.string.pref_automatic_download_title;
            case R.xml.preferences_playback:
                return R.string.playback_pref;
            case R.xml.preferences_storage:
                return R.string.storage_pref;
            case R.xml.preferences_import_export:
                return R.string.import_export_pref;
            case R.xml.preferences_user_interface:
                return R.string.user_interface_label;
            case R.xml.preferences_gpodder:
                return R.string.gpodnet_main_label;
            case R.xml.preferences_notifications:
                return R.string.notification_pref_fragment;
            case R.xml.feed_settings:
                return R.string.feed_settings_label;
            case R.xml.preferences_swipe:
                return R.string.swipeactions_label;
            default:
                return R.string.settings_label;
        }
    }

    public PreferenceFragmentCompat openScreen(int screen) {
        PreferenceFragmentCompat fragment = getPreferenceScreen(screen);
        if (screen == R.xml.preferences_notifications && Build.VERSION.SDK_INT >= 26) {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        } else {
            getSupportFragmentManager().beginTransaction().replace(R.id.settingsContainer, fragment)
                    .addToBackStack(getString(getTitleOfPage(screen))).commit();
        }


        return fragment;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                finish();
            } else {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm.isActive()) {
                    imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_NOT_ALWAYS);
                }
                getSupportFragmentManager().popBackStack();
            }
            return true;
        }
        return false;
    }

    @Override
    public void onSearchResultClicked(SearchPreferenceResult result) {
        int screen = result.getResourceFile();
        if (screen == R.xml.feed_settings) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.feed_settings_label);
            builder.setMessage(R.string.pref_feed_settings_dialog_msg);
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
        } else if (screen == R.xml.preferences_notifications) {
            openScreen(screen);
        } else {
            PreferenceFragmentCompat fragment = openScreen(result.getResourceFile());
            result.highlight(fragment);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        try {
            AccountImporter.onActivityResult(requestCode, resultCode, data, this, account -> {
                // As this library supports multiple accounts we created some helper methods if you only want to use one.
                // The following line stores the selected account as the "default" account which can be queried by using
                // the SingleAccountHelper.getCurrentSingleSignOnAccount(context) method
                SingleAccountHelper.setCurrentAccount(getApplicationContext(), account.name);
            });
        } catch (AccountImportCancelledException e) {
            e.printStackTrace();
        }
    }

}
