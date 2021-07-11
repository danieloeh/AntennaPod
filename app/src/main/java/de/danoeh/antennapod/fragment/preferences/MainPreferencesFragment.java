package de.danoeh.antennapod.fragment.preferences;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.bytehamster.lib.preferencesearch.SearchConfiguration;
import com.bytehamster.lib.preferencesearch.SearchPreference;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.BugReportActivity;
import de.danoeh.antennapod.activity.PreferenceActivity;
import de.danoeh.antennapod.core.util.IntentUtils;
import de.danoeh.antennapod.fragment.preferences.about.AboutFragment;

public class MainPreferencesFragment extends PreferenceFragmentCompat {

    private static final String PREF_SCREEN_USER_INTERFACE = "prefScreenInterface";
    private static final String PREF_SCREEN_PLAYBACK = "prefScreenPlayback";
    private static final String PREF_SCREEN_NETWORK = "prefScreenNetwork";
    private static final String PREF_SCREEN_SYNCHRONIZATION = "prefScreenSynchronization";
    private static final String PREF_SCREEN_STORAGE = "prefScreenStorage";
    private static final String PREF_DOCUMENTATION = "prefDocumentation";
    private static final String PREF_VIEW_FORUM = "prefViewForum";
    private static final String PREF_SEND_BUG_REPORT = "prefSendBugReport";
    private static final String PREF_CATEGORY_PROJECT = "project";
    private static final String STATISTICS = "statistics";
    private static final String PREF_ABOUT = "prefAbout";
    private static final String PREF_NOTIFICATION = "notifications";
    private static final String PREF_CONTRIBUTE = "prefContribute";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences);
        setupMainScreen();
        setupSearch();

        // If you are writing a spin-off, please update the details on screens like "About" and "Report bug"
        // and afterwards remove the following lines. Please keep in mind that AntennaPod is licensed under the GPL.
        // This means that your application needs to be open-source under the GPL, too.
        // It must also include a prominent copyright notice.
        String packageName = getContext().getPackageName();
        if (!"de.danoeh.antennapod".equals(packageName) && !"de.danoeh.antennapod.debug".equals(packageName)) {
            findPreference(PREF_CATEGORY_PROJECT).setVisible(false);
            Preference copyrightNotice = new Preference(getContext());
            copyrightNotice.setSummary("This application is based on AntennaPod."
                    + " The AntennaPod team does NOT provide support for this unofficial version."
                    + " If you can read this message, the developers of this modification"
                    + " violate the GNU General Public License (GPL).");
            findPreference(PREF_CATEGORY_PROJECT).getParent().addPreference(copyrightNotice);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        ((PreferenceActivity) getActivity()).getSupportActionBar().setTitle(R.string.settings_label);
    }

    private void setupMainScreen() {
        findPreference(PREF_SCREEN_USER_INTERFACE).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_user_interface);
            return true;
        });
        findPreference(PREF_SCREEN_PLAYBACK).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_playback);
            return true;
        });
        findPreference(PREF_SCREEN_NETWORK).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_network);
            return true;
        });
        findPreference(PREF_SCREEN_SYNCHRONIZATION).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_synchronization);
            return true;
        });
        findPreference(PREF_SCREEN_STORAGE).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_storage);
            return true;
        });
        findPreference(PREF_NOTIFICATION).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_notifications);
            return true;
        });
        findPreference(PREF_ABOUT).setOnPreferenceClickListener(
                preference -> {
                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.settingsContainer, new AboutFragment())
                            .addToBackStack(getString(R.string.about_pref)).commit();
                    return true;
                }
        );
        findPreference(STATISTICS).setOnPreferenceClickListener(
                preference -> {
                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.settingsContainer, new StatisticsFragment())
                            .addToBackStack(getString(R.string.statistics_label)).commit();
                    return true;
                }
        );
        findPreference(PREF_DOCUMENTATION).setOnPreferenceClickListener(preference -> {
            IntentUtils.openInBrowser(getContext(), "https://antennapod.org/documentation/");
            return true;
        });
        findPreference(PREF_VIEW_FORUM).setOnPreferenceClickListener(preference -> {
            IntentUtils.openInBrowser(getContext(), "https://forum.antennapod.org/");
            return true;
        });
        findPreference(PREF_CONTRIBUTE).setOnPreferenceClickListener(preference -> {
            IntentUtils.openInBrowser(getContext(), "https://antennapod.org/contribute/");
            return true;
        });
        findPreference(PREF_SEND_BUG_REPORT).setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(getActivity(), BugReportActivity.class));
            return true;
        });
    }

    private void setupSearch() {
        SearchPreference searchPreference = findPreference("searchPreference");
        SearchConfiguration config = searchPreference.getSearchConfiguration();
        config.setActivity((AppCompatActivity) getActivity());
        config.setFragmentContainerViewId(R.id.settingsContainer);
        config.setBreadcrumbsEnabled(true);

        config.index(R.xml.preferences_user_interface)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_user_interface));
        config.index(R.xml.preferences_playback)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_playback));
        config.index(R.xml.preferences_network)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_network));
        config.index(R.xml.preferences_storage)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_storage));
        config.index(R.xml.preferences_import_export)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_storage))
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_import_export));
        config.index(R.xml.preferences_autodownload)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_network))
                .addBreadcrumb(R.string.automation)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_autodownload));
        config.index(R.xml.preferences_synchronization)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_synchronization));
        config.index(R.xml.preferences_notifications)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_notifications));
        config.index(R.xml.feed_settings)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.feed_settings));
        config.index(R.xml.preferences_swipe)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_user_interface))
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_swipe));
    }
}
