package de.danoeh.antennapod.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.transition.ChangeBounds;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;

import com.addisonelliott.segmentedbutton.SegmentedButton;
import com.addisonelliott.segmentedbutton.SegmentedButtonGroup;
import com.joanzapata.iconify.Iconify;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.EpisodeItemListAdapter;
import de.danoeh.antennapod.adapter.actionbutton.DeleteActionButton;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.util.download.AutoUpdateManager;
import de.danoeh.antennapod.dialog.FilterDialog;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.view.EmptyViewHandler;
import de.danoeh.antennapod.view.viewholder.EpisodeItemViewHolder;

public class EpisodesFragment extends EpisodesListFragment {

    public static final String TAG = "PowerEpisodesFragment";
    private static final String PREF_NAME = "PrefPowerEpisodesFragment";
    private static final String PREF_POSITION = "position";

    public static final String PREF_FILTER = "filter";

    public EpisodesFragment() {
        super();
    }

    public EpisodesFragment(boolean hideToolbar) {
        super();
        this.hideToolbar = hideToolbar;
    }

    private SegmentedButtonGroup floatingQuickFilter;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        feedItemFilter = new FeedItemFilter(getPrefFilter());
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = super.onCreateView(inflater, container, savedInstanceState);

        toolbar.setTitle(R.string.episodes_label);

        floatingQuickFilter = rootView.findViewById(R.id.floatingFilter);

        setUpQuickFilter();

        setSwipeActions(TAG);

        return  rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        SharedPreferences prefs = getActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        setQuickFilterPosition(prefs.getInt(PREF_POSITION, QUICKFILTER_ALL));
        loadArgsIfAvailable();
    }

    @Override
    public void onPause() {
        super.onPause();
        SharedPreferences prefs = getActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(PREF_POSITION, floatingQuickFilter.getPosition()).apply();
    }

    public String getPrefFilter() {
        SharedPreferences prefs = getActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_FILTER, "");
    }

    private void loadArgsIfAvailable() {
        if (getArguments() != null) {
            int argumentsFilter = getArguments().getInt(PREF_FILTER, -1);
            if (argumentsFilter >= 0) {
                setQuickFilterPosition(argumentsFilter);
            }
        }
    }

    private void setUpQuickFilter() {
        floatingQuickFilter.setVisibility(View.VISIBLE);
        floatingQuickFilter.setOnPositionChangedListener(position -> {
            String newFilter;
            switch (position) {
                default:
                case QUICKFILTER_ALL:
                    newFilter = getPrefFilter();
                    break;
                case QUICKFILTER_NEW:
                    newFilter = FeedItemFilter.UNPLAYED;
                    break;
                case QUICKFILTER_DOWNLOADED:
                    newFilter = FeedItemFilter.DOWNLOADED;
                    break;
                case QUICKFILTER_FAV:
                    newFilter = FeedItemFilter.IS_FAVORITE;
                    break;
            }

            updateFeedItemFilter(newFilter);

            //imitate expandable action button
            for (int i = 0; i < floatingQuickFilter.getButtons().size(); i++) {
                if (i != position) {
                    floatingQuickFilter.getButton(i).setVisibility(View.GONE);
                    floatingQuickFilter.getButton(i).setOnClickListener(null);
                } else {
                    floatingQuickFilter.getButton(i).setOnClickListener(new View.OnClickListener() {
                        boolean collapsed = true;

                        @Override
                        public void onClick(View view) {
                            for (int i1 = 0; i1 < floatingQuickFilter.getButtons().size(); i1++) {
                                if (i1 != position) {
                                    floatingQuickFilter.getButton(i1)
                                            .setVisibility(collapsed ? View.VISIBLE : View.GONE);
                                }
                            }
                            collapsed = !collapsed;
                        }
                    });
                }
            }
        });
    }

    @Override
    protected String getPrefName() {
        return PREF_NAME;
    }

    public void setQuickFilterPosition(int position) {
        floatingQuickFilter.setPosition(position, false);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (!super.onMenuItemClick(item)) {
            if (item.getItemId() == R.id.filter_items) {
                AutoUpdateManager.runImmediate(requireContext());
                setQuickFilterPosition(QUICKFILTER_ALL);
                showFilterDialog();
            } else {
                return false;
            }
        }

        return true;
    }

    private void savePrefsBoolean(String s, Boolean b) {
        SharedPreferences prefs = getActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(s, b).apply();
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.filter_items).setVisible(true);
        menu.findItem(R.id.refresh_item).setVisible(false);
    }

    @Override
    protected void onFragmentLoaded(List<FeedItem> episodes) {
        super.onFragmentLoaded(episodes);

        //smoothly animate filter info
        TransitionSet auto = new TransitionSet();
        auto.addTransition(new ChangeBounds());
        auto.excludeChildren(EmptyViewHandler.class, true);
        auto.excludeChildren(R.id.swipeRefresh, true);
        auto.excludeChildren(R.id.floatingFilter, true);
        TransitionManager.beginDelayedTransition(
                (ViewGroup) txtvInformation.getParent(),
                auto);

        if (feedItemFilter.getValues().length > 0) {
            txtvInformation.setText("{md-info-outline} " + this.getString(R.string.filtered_label));
            Iconify.addIcons(txtvInformation);
            txtvInformation.setVisibility(View.VISIBLE);
        } else {
            txtvInformation.setVisibility(View.GONE);
        }

        setEmptyView(TAG + floatingQuickFilter.getPosition());
    }

    private void showFilterDialog() {
        SharedPreferences prefs = getActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        FeedItemFilter prefFilter = new FeedItemFilter(prefs.getString(PREF_FILTER, ""));
        FilterDialog filterDialog = new FilterDialog(getContext(), prefFilter) {
            @Override
            protected void updateFilter(Set<String> filterValues) {
                feedItemFilter = new FeedItemFilter(filterValues.toArray(new String[0]));
                prefs.edit().putString(PREF_FILTER, StringUtils.join(filterValues, ",")).apply();
                loadItems();
            }
        };

        filterDialog.openDialog();
    }

    public void updateFeedItemFilter(String strings) {
        feedItemFilter = new FeedItemFilter(strings);
        swipeActions.setFilter(feedItemFilter);
        loadItems();
    }

    @Override
    protected boolean shouldUpdatedItemRemainInList(FeedItem item) {
        SharedPreferences prefs = getActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        FeedItemFilter feedItemFilter = new FeedItemFilter(prefs.getString(PREF_FILTER, ""));

        if (feedItemFilter.isShowDownloaded() && (!item.hasMedia() || !item.getMedia().isDownloaded())) {
            return false;
        }

        return true;
    }

    @NonNull
    @Override
    protected List<FeedItem> loadData() {
        return load(0);
    }

    private List<FeedItem> load(int offset) {
        int limit = EPISODES_PER_PAGE;
        return DBReader.getRecentlyPublishedEpisodes(offset, limit, feedItemFilter);
    }

    @Override
    protected EpisodeItemListAdapter newAdapter(MainActivity mainActivity) {
        return new EpisodesListAdapter(mainActivity);
    }

    @NonNull
    @Override
    protected List<FeedItem> loadMoreData() {
        return load((page - 1) * EPISODES_PER_PAGE);
    }

    private static class EpisodesListAdapter extends EpisodeItemListAdapter {

        public EpisodesListAdapter(MainActivity mainActivity) {
            super(mainActivity);
        }

        @Override
        public void afterBindViewHolder(EpisodeItemViewHolder holder, int pos) {
            FeedItem item = getItem(pos);
            if (item.isPlayed() && item.getMedia() != null && item.getMedia().isDownloaded()) {
                DeleteActionButton actionButton = new DeleteActionButton(getItem(pos));
                actionButton.configure(holder.secondaryActionButton, holder.secondaryActionIcon, getActivity());
            }
        }
    }
}
