package de.danoeh.antennapod.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
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

public class EpisodesFragment extends PagedToolbarFragment {

    public static final String TAG = "EpisodesFragment";
    private static final String PREF_LAST_TAB_POSITION = "tab_position";
    private static final String KEY_UP_ARROW = "up_arrow";

    private static final int POS_NEW_EPISODES = 0;
    private static final int POS_ALL_EPISODES = 1;
    private static final int POS_FAV_EPISODES = 2;
    private static final int TOTAL_COUNT = 3;

    private TabLayout tabLayout;
    private boolean displayUpArrow;
    private Toolbar toolbar;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View rootView = inflater.inflate(R.layout.pager_fragment, container, false);
        toolbar = rootView.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.episodes_label);
        toolbar.inflateMenu(R.menu.episodes);
        MenuItemUtils.setupSearchItem(toolbar.getMenu(), (MainActivity) getActivity(), 0, "");
        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0;
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW);
        }
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar, displayUpArrow);

        ViewPager2 viewPager = rootView.findViewById(R.id.viewpager);
        viewPager.setAdapter(new EpisodesPagerAdapter(this));
        viewPager.setOffscreenPageLimit(2);
        super.setupPagedToolbar(toolbar, viewPager);

        // Give the TabLayout the ViewPager
        tabLayout = rootView.findViewById(R.id.sliding_tabs);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case POS_NEW_EPISODES:
                    tab.setText(R.string.new_episodes_label);
                    break;
                case POS_ALL_EPISODES:
                    tab.setText(R.string.all_episodes_short_label);
                    break;
                case POS_FAV_EPISODES:
                    tab.setText(R.string.favorite_episodes_label);
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

        return rootView;
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
    public void onPause() {
        super.onPause();
        // save our tab selection
        SharedPreferences prefs = getActivity().getSharedPreferences(TAG, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREF_LAST_TAB_POSITION, tabLayout.getSelectedTabPosition());
        editor.apply();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow);
        super.onSaveInstanceState(outState);
    }

    static class EpisodesPagerAdapter extends FragmentStateAdapter {

        EpisodesPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case POS_NEW_EPISODES:
                    return new NewEpisodesFragment();
                case POS_ALL_EPISODES:
                    return new AllEpisodesFragment();
                default:
                case POS_FAV_EPISODES:
                    return new FavoriteEpisodesFragment();
            }
        }

        @Override
        public int getItemCount() {
            return TOTAL_COUNT;
        }
    }
}
