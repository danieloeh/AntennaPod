package de.danoeh.antennapod.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.menuhandler.MenuItemUtils;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.net.downloadservice.DownloadRequest;

/**
 * Shows the CompletedDownloadsFragment and the RunningDownloadsFragment.
 */
public class DownloadsFragment extends PagedToolbarFragment {

    public static final String TAG = "DownloadsFragment";

    public static final String ARG_SELECTED_TAB = "selected_tab";
    private static final String PREF_LAST_TAB_POSITION = "tab_position";
    private static final String KEY_UP_ARROW = "up_arrow";

    private static final int POS_COMPLETED = 0;
    public static final int POS_LOG = 1;
    private static final int TOTAL_COUNT = 2;

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private boolean displayUpArrow;
    private Toolbar toolbar;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View root = inflater.inflate(R.layout.pager_fragment, container, false);
        toolbar = root.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.downloads_label);
        toolbar.inflateMenu(R.menu.downloads);
        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0;
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW);
        }
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar, displayUpArrow);

        viewPager = root.findViewById(R.id.viewpager);
        viewPager.setAdapter(new DownloadsPagerAdapter(this));
        viewPager.setOffscreenPageLimit(2);
        super.setupPagedToolbar(toolbar, viewPager);

        // Give the TabLayout the ViewPager
        tabLayout = root.findViewById(R.id.sliding_tabs);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case POS_COMPLETED:
                    tab.setText(R.string.downloads_completed_label);
                    break;
                case POS_LOG:
                    tab.setText(R.string.downloads_log_label);
                    break;
                default:
                    break;
            }
        }).attach();

        // restore our last position
        SharedPreferences prefs = getActivity().getSharedPreferences(TAG, Context.MODE_PRIVATE);
        int lastPosition = prefs.getInt(PREF_LAST_TAB_POSITION, 0);
        viewPager.setCurrentItem(lastPosition, false);
        setupRefreshIndicator();

        return root;
    }

    private void setupRefreshIndicator() {
        WorkManager.getInstance(getContext()).getWorkInfosByTagLiveData(DownloadRequest.TAG)
                .observe(getViewLifecycleOwner(), workInfos -> {
                    boolean isDownloadingFeed = false;
                    for (WorkInfo workInfo : workInfos) {
                        if (workInfo.getState().isFinished()) {
                            continue;
                        }
                        DownloadRequest request = DownloadRequest.from(workInfo.getProgress());
                        if (request.getFeedfileType() == Feed.FEEDFILETYPE_FEED) {
                            isDownloadingFeed = true;
                            break;
                        }
                    }
                    MenuItemUtils.updateRefreshMenuItem(toolbar.getMenu(), R.id.refresh_item, isDownloadingFeed);
                });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            int tab = getArguments().getInt(ARG_SELECTED_TAB);
            viewPager.setCurrentItem(tab, false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // save our tab selection
        SharedPreferences prefs = getActivity().getSharedPreferences(TAG, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREF_LAST_TAB_POSITION, tabLayout.getSelectedTabPosition());
        editor.apply();
    }

    public static class DownloadsPagerAdapter extends FragmentStateAdapter {

        DownloadsPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case POS_COMPLETED:
                    return new CompletedDownloadsFragment();
                default:
                case POS_LOG:
                    return new DownloadLogFragment();
            }
        }

        @Override
        public int getItemCount() {
            return TOTAL_COUNT;
        }
    }
}
