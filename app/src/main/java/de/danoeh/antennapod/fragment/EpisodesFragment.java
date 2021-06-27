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

import com.addisonelliott.segmentedbutton.SegmentedButtonGroup;
import com.joanzapata.iconify.Iconify;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.util.download.AutoUpdateManager;
import de.danoeh.antennapod.dialog.FilterDialog;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.view.EmptyViewHandler;

public class EpisodesFragment extends EpisodesListFragment {

    public static final String TAG = "PowerEpisodesFragment";
    private static final String PREF_NAME = "PrefPowerEpisodesFragment";
    private static final String PREF_POSITION = "position";

    public static final String PREF_FILTER = "filter";

    private FeedItemFilter feedItemFilter = new FeedItemFilter("");

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
        floatingQuickFilter.setVisibility(View.VISIBLE);
        floatingQuickFilter.setOnPositionChangedListener(position -> {
            String newFilter;
            switch (position) {
                default:
                case QUICKFILTER_ALL:
                    newFilter = getPrefFilter();
                    break;
                case QUICKFILTER_NEW:
                    newFilter = "unplayed";
                    break;
                case QUICKFILTER_DOWNLOADED:
                    newFilter = "downloaded";
                    break;
                case QUICKFILTER_FAV:
                    newFilter = "is_favorite";
                    break;
            }
            updateFeedItemFilter(newFilter);
        });

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
        menu.findItem(R.id.mark_all_item).setVisible(true);
        menu.findItem(R.id.swipe_settings).setVisible(true);
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

    @NonNull
    @Override
    protected List<FeedItem> loadMoreData() {
        return load((page - 1) * EPISODES_PER_PAGE);
    }
}
