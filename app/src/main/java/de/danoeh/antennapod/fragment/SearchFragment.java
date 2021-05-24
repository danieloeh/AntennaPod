package de.danoeh.antennapod.fragment;

import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.EpisodeItemListAdapter;
import de.danoeh.antennapod.adapter.FeedSearchResultAdapter;
import de.danoeh.antennapod.core.event.FeedItemEvent;
import de.danoeh.antennapod.core.event.PlaybackPositionEvent;
import de.danoeh.antennapod.core.event.PlayerStatusEvent;
import de.danoeh.antennapod.core.event.UnreadItemsUpdateEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.core.storage.FeedSearcher;
import de.danoeh.antennapod.core.util.FeedItemUtil;
import de.danoeh.antennapod.menuhandler.FeedItemMenuHandler;
import de.danoeh.antennapod.view.EmptyViewHandler;
import de.danoeh.antennapod.view.EpisodeItemListRecyclerView;
import de.danoeh.antennapod.view.viewholder.EpisodeItemViewHolder;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Collections;
import java.util.List;

/**
 * Performs a search operation on all feeds or one specific feed and displays the search result.
 */
public class SearchFragment extends Fragment {
    private static final String TAG = "SearchFragment";
    private static final String ARG_QUERY = "query";
    private static final String ARG_FEED = "feed";
    private static final String ARG_FEED_NAME = "feedName";

    private EpisodeItemListAdapter adapter;
    private FeedSearchResultAdapter adapterFeeds;
    private Disposable disposable;
    private ProgressBar progressBar;
    private EmptyViewHandler emptyViewHandler;
    private EpisodeItemListRecyclerView recyclerView;
    private List<FeedItem> results;
    private Chip chip;

    /**
     * Create a new SearchFragment that searches all feeds.
     */
    public static SearchFragment newInstance(String query) {
        if (query == null) {
            query = "";
        }
        SearchFragment fragment = new SearchFragment();
        Bundle args = new Bundle();
        args.putString(ARG_QUERY, query);
        args.putLong(ARG_FEED, 0);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Create a new SearchFragment that searches one specific feed.
     */
    public static SearchFragment newInstance(String query, long feed, String feedTitle) {
        SearchFragment fragment = newInstance(query);
        fragment.getArguments().putLong(ARG_FEED, feed);
        fragment.getArguments().putString(ARG_FEED_NAME, feedTitle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        search();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.search_fragment, container, false);
        setupToolbar(layout.findViewById(R.id.toolbar));
        progressBar = layout.findViewById(R.id.progressBar);

        recyclerView = layout.findViewById(R.id.recyclerView);
        recyclerView.setRecycledViewPool(((MainActivity) getActivity()).getRecycledViewPool());
        recyclerView.setVisibility(View.GONE);
        adapter = new EpisodeItemListAdapter((MainActivity) getActivity());
        recyclerView.setAdapter(adapter);

        RecyclerView recyclerViewFeeds = layout.findViewById(R.id.recyclerViewFeeds);
        LinearLayoutManager layoutManagerFeeds = new LinearLayoutManager(getActivity());
        layoutManagerFeeds.setOrientation(RecyclerView.HORIZONTAL);
        recyclerViewFeeds.setLayoutManager(layoutManagerFeeds);
        adapterFeeds = new FeedSearchResultAdapter((MainActivity) getActivity());
        recyclerViewFeeds.setAdapter(adapterFeeds);

        emptyViewHandler = new EmptyViewHandler(getContext());
        emptyViewHandler.attachToRecyclerView(recyclerView);
        emptyViewHandler.setIcon(R.drawable.ic_search);
        emptyViewHandler.setTitle(R.string.search_status_no_results);
        EventBus.getDefault().register(this);

        chip = layout.findViewById(R.id.feed_title_chip);
        chip.setOnCloseIconClickListener(v -> {
            getArguments().putLong(ARG_FEED, 0);
            search();
        });
        return layout;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }

    private void setupToolbar(Toolbar toolbar) {
        toolbar.setTitle(R.string.search_label);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());
        toolbar.inflateMenu(R.menu.search);

        MenuItem item = toolbar.getMenu().findItem(R.id.action_search);
        item.expandActionView();
        final SearchView sv = (SearchView) item.getActionView();
        sv.setQueryHint(getString(R.string.search_label));
        sv.clearFocus();
        sv.setQuery(getArguments().getString(ARG_QUERY), false);
        sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                sv.clearFocus();
                getArguments().putString(ARG_QUERY, s);
                search();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return false;
            }
        });
        item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                getParentFragmentManager().popBackStack();
                return true;
            }
        });
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        FeedItem selectedItem = adapter.getSelectedItem();
        if (selectedItem == null) {
            Log.i(TAG, "Selected item at current position was null, ignoring selection");
            return super.onContextItemSelected(item);
        }
        return FeedItemMenuHandler.onMenuItemClicked(this, item.getItemId(), selectedItem);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUnreadItemsChanged(UnreadItemsUpdateEvent event) {
        search();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(FeedItemEvent event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        if (results == null) {
            return;
        } else if (adapter == null) {
            search();
            return;
        }
        for (int i = 0, size = event.items.size(); i < size; i++) {
            FeedItem item = event.items.get(i);
            int pos = FeedItemUtil.indexOfItemWithId(results, item.getId());
            if (pos >= 0) {
                results.remove(pos);
                results.add(pos, item);
                adapter.notifyItemChangedCompat(pos);
            }
        }
    }

    /*@Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(DownloadEvent event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        DownloaderUpdate update = event.update;
        if (adapter != null && update.mediaIds.length > 0) {
            for (long mediaId : update.mediaIds) {
                int pos = FeedItemUtil.indexOfItemWithMediaId(results, mediaId);
                if (pos >= 0) {
                    adapter.notifyItemChangedCompat(pos);
                }
            }
        }
    }*/

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaybackPositionEvent event) {
        if (adapter != null) {
            for (int i = 0; i < adapter.getItemCount(); i++) {
                EpisodeItemViewHolder holder = (EpisodeItemViewHolder) recyclerView.findViewHolderForAdapterPosition(i);
                if (holder != null && holder.isCurrentlyPlayingItem()) {
                    holder.notifyPlaybackPositionUpdated(event);
                    break;
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusChanged(PlayerStatusEvent event) {
        search();
    }

    private void search() {
        if (disposable != null) {
            disposable.dispose();
        }
        progressBar.setVisibility(View.VISIBLE);
        emptyViewHandler.hide();
        disposable = Observable.fromCallable(this::performSearch)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(results -> {
                    progressBar.setVisibility(View.GONE);
                    this.results = results.first;
                    adapter.updateItems(results.first);
                    if (getArguments().getLong(ARG_FEED, 0) == 0) {
                        adapterFeeds.updateData(results.second);
                        chip.setVisibility(View.GONE);
                    } else {
                        adapterFeeds.updateData(Collections.emptyList());
                        chip.setText(getArguments().getString(ARG_FEED_NAME, ""));
                    }
                    String query = getArguments().getString(ARG_QUERY);
                    emptyViewHandler.setMessage(getString(R.string.no_results_for_query, query));
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    @NonNull
    private Pair<List<FeedItem>, List<Feed>> performSearch() {
        String query = getArguments().getString(ARG_QUERY);
        long feed = getArguments().getLong(ARG_FEED);
        List<FeedItem> items = FeedSearcher.searchFeedItems(getContext(), query, feed);
        List<Feed> feeds = FeedSearcher.searchFeeds(getContext(), query);
        return new Pair<>(items, feeds);
    }
}
