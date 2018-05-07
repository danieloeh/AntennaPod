package de.danoeh.antennapod.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.activity.OnlineFeedViewActivity;
import de.danoeh.antennapod.activity.OpmlImportFromPathActivity;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.dialog.ShowCaseHelper;
import de.danoeh.antennapod.fragment.gpodnet.GpodnetMainFragment;

/**
 * Provides actions for adding new podcast subscriptions
 */
public class AddFeedFragment extends Fragment {

    public static final String TAG = "AddFeedFragment";

    /**
     * Preset value for url text field.
     */
    private static final String ARG_FEED_URL = "feedurl";

    private Button butSearchITunes;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View root = inflater.inflate(R.layout.addfeed, container, false);

        final EditText etxtFeedurl = (EditText) root.findViewById(R.id.etxtFeedurl);

        Bundle args = getArguments();
        if (args != null && args.getString(ARG_FEED_URL) != null) {
            etxtFeedurl.setText(args.getString(ARG_FEED_URL));
        }

        butSearchITunes = (Button) root.findViewById(R.id.butSearchItunes);
        Button butBrowserGpoddernet = (Button) root.findViewById(R.id.butBrowseGpoddernet);
        Button butSearchFyyd = (Button) root.findViewById(R.id.butSearchFyyd);
        Button butOpmlImport = (Button) root.findViewById(R.id.butOpmlImport);
        Button butConfirm = (Button) root.findViewById(R.id.butConfirm);

        final MainActivity activity = (MainActivity) getActivity();
        activity.getSupportActionBar().setTitle(R.string.add_feed_label);

        butSearchITunes.setOnClickListener(v -> activity.loadChildFragment(new ItunesSearchFragment()));

        butBrowserGpoddernet.setOnClickListener(v -> activity.loadChildFragment(new GpodnetMainFragment()));

        butSearchFyyd.setOnClickListener(v -> activity.loadChildFragment(new FyydSearchFragment()));

        butOpmlImport.setOnClickListener(v -> startActivity(new Intent(getActivity(),
                OpmlImportFromPathActivity.class)));

        butConfirm.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), OnlineFeedViewActivity.class);
            intent.putExtra(OnlineFeedViewActivity.ARG_FEEDURL, etxtFeedurl.getText().toString());
            intent.putExtra(OnlineFeedViewActivity.ARG_TITLE, getString(R.string.add_feed_label));
            startActivity(intent);
        });

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (UserPreferences.shouldShowOnboarding("AddFeedFragment")) {
            final MainActivity activity = (MainActivity) getActivity();

            ShowCaseHelper.brandedShowcase(getActivity(), R.string.onboarding_search_podcast)
                    .focusOn(butSearchITunes)
                    .dismissListener(new ShowCaseHelper.DismissListener() {
                        @Override
                        public void onDismiss(String id) {
                            activity.loadChildFragment(new ItunesSearchFragment());
                        }
                    })
                    .build()
                    .show();
        }
    }
}
