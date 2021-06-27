package de.danoeh.antennapod.fragment;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.EpisodeItemListAdapter;
import de.danoeh.antennapod.core.dialog.ConfirmationDialog;
import de.danoeh.antennapod.core.event.DownloadEvent;
import de.danoeh.antennapod.core.event.DownloaderUpdate;
import de.danoeh.antennapod.core.event.FavoritesEvent;
import de.danoeh.antennapod.core.event.FeedItemEvent;
import de.danoeh.antennapod.core.event.FeedListUpdateEvent;
import de.danoeh.antennapod.core.event.PlaybackPositionEvent;
import de.danoeh.antennapod.core.event.PlayerStatusEvent;
import de.danoeh.antennapod.core.event.QueueEvent;
import de.danoeh.antennapod.core.event.UnreadItemsUpdateEvent;
import de.danoeh.antennapod.core.service.download.DownloadService;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.storage.DownloadRequester;
import de.danoeh.antennapod.core.util.FeedItemUtil;
import de.danoeh.antennapod.core.util.download.AutoUpdateManager;
import de.danoeh.antennapod.menuhandler.FeedItemMenuHandler;
import de.danoeh.antennapod.menuhandler.MenuItemUtils;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.view.EmptyViewHandler;
import de.danoeh.antennapod.view.EpisodeItemListRecyclerView;
import de.danoeh.antennapod.view.viewholder.EpisodeItemViewHolder;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Shows unread or recently published episodes
 */
public abstract class EpisodesListFragment extends Fragment implements Toolbar.OnMenuItemClickListener {

    public static final String TAG = "EpisodesListFragment";
    protected static final int EPISODES_PER_PAGE = 150;
    private static final String KEY_UP_ARROW = "up_arrow";

    public static final int QUICKFILTER_ALL = 0;
    public static final int QUICKFILTER_NEW = 1;
    public static final int QUICKFILTER_DOWNLOADED = 2;
    public static final int QUICKFILTER_FAV = 3;

    protected int page = 1;
    protected boolean isLoadingMore = false;
    protected boolean hasMoreItems = true;
    public boolean hideToolbar = false;
    private boolean displayUpArrow;

    EpisodeItemListRecyclerView recyclerView;
    EpisodeItemListAdapter listAdapter;
    ProgressBar progLoading;
    View loadingMoreView;
    EmptyViewHandler emptyView;

    Toolbar toolbar;

    SwipeActions swipeActions;

    @NonNull
    List<FeedItem> episodes = new ArrayList<>();

    private volatile boolean isUpdatingFeeds;
    protected Disposable disposable;
    protected TextView txtvInformation;

    String getPrefName() {
        return TAG;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        loadItems();
    }

    @Override
    public void onResume() {
        super.onResume();
        registerForContextMenu(recyclerView);
    }

    @Override
    public void onPause() {
        super.onPause();
        recyclerView.saveScrollPosition(getPrefName());
        unregisterForContextMenu(recyclerView);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private final MenuItemUtils.UpdateRefreshMenuItemChecker updateRefreshMenuItemChecker =
            () -> DownloadService.isRunning && DownloadRequester.getInstance().isDownloadingFeeds();

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        isUpdatingFeeds = MenuItemUtils.updateRefreshMenuItem(menu, R.id.refresh_item, updateRefreshMenuItemChecker);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (!super.onOptionsItemSelected(item)) {
            if (item.getItemId() == R.id.refresh_item) {
                AutoUpdateManager.runImmediate(requireContext());
            } else if (item.getItemId() == R.id.mark_all_read_item) {
                markAllAs(FeedItem.PLAYED);
            } else if (item.getItemId() == R.id.remove_all_new_flags_item) {
                ConfirmationDialog removeAllNewFlagsConfirmationDialog = new ConfirmationDialog(getActivity(),
                        R.string.remove_all_new_flags_label,
                        R.string.remove_all_new_flags_confirmation_msg) {

                    @Override
                    public void onConfirmButtonPressed(DialogInterface dialog) {
                        dialog.dismiss();
                        DBWriter.removeAllNewFlags();
                        ((MainActivity) getActivity()).showSnackbarAbovePlayer(
                                R.string.removed_all_new_flags_msg, Toast.LENGTH_SHORT);
                    }
                };
                removeAllNewFlagsConfirmationDialog.createNewDialog().show();
            } else if (item.getItemId() == R.id.mark_all_item) {
                AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
                builder.setTitle(R.string.mark_all_label);
                String[] options = requireActivity().getResources().getStringArray(R.array.mark_all_array);
                builder.setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        switch (i) {
                            case 0: //played
                                markAllAs(FeedItem.PLAYED);
                                break;
                            case 1: //unplayed
                                markAllAs(FeedItem.UNPLAYED);
                                break;
                            default: break;
                            //TODO removeAllPositions
                        }
                    }
                });
                builder.setNegativeButton(R.string.cancel_label, null);
                builder.create().show();
            } else if (item.getItemId() == R.id.swipe_settings && swipeActions != null) {
                //null safe
                swipeActions.show();
            } else {
                return false;
            }
        }

        return true;
    }

    private void markAllAs(int state) {
        ConfirmationDialog markAllReadConfirmationDialog = new ConfirmationDialog(getActivity(),
                R.string.mark_all_read_label,
                R.string.mark_all_read_confirmation_msg) {

            @Override
            public void onConfirmButtonPressed(DialogInterface dialog) {
                dialog.dismiss();
                DBWriter.markAllItemsRead(state);
                ((MainActivity) getActivity()).showSnackbarAbovePlayer(
                        R.string.mark_all_read_msg, Toast.LENGTH_SHORT);
            }
        };
        markAllReadConfirmationDialog.createNewDialog().show();
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        Log.d(TAG, "onContextItemSelected() called with: " + "item = [" + item + "]");
        if (!getUserVisibleHint()) {
            return false;
        }
        if (!isVisible()) {
            return false;
        }

        if (listAdapter.getSelectedItem() == null) {
            Log.i(TAG, "Selected item or listAdapter was null, ignoring selection");
            return super.onContextItemSelected(item);
        }
        FeedItem selectedItem = listAdapter.getSelectedItem();

        return FeedItemMenuHandler.onMenuItemClicked(this, item.getItemId(), selectedItem);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View root = inflater.inflate(R.layout.episodes_fragment, container, false);
        txtvInformation = root.findViewById(R.id.txtvInformation);

        recyclerView = root.findViewById(android.R.id.list);
        recyclerView.setRecycledViewPool(((MainActivity) getActivity()).getRecycledViewPool());
        setupLoadMoreScrollListener();

        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        SwipeRefreshLayout swipeRefreshLayout = root.findViewById(R.id.swipeRefresh);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            AutoUpdateManager.runImmediate(requireContext());
            new Handler(Looper.getMainLooper()).postDelayed(() -> swipeRefreshLayout.setRefreshing(false),
                    getResources().getInteger(R.integer.swipe_to_refresh_duration_in_ms));
        });

        progLoading = root.findViewById(R.id.progLoading);
        progLoading.setVisibility(View.VISIBLE);
        loadingMoreView = root.findViewById(R.id.loadingMore);

        emptyView = new EmptyViewHandler(getContext());
        emptyView.attachToRecyclerView(recyclerView);
        setEmptyView(EpisodesFragment.TAG + QUICKFILTER_ALL);

        createRecycleAdapter(recyclerView, emptyView);
        emptyView.hide();

        toolbar = root.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.episodes_label);
        toolbar.inflateMenu(R.menu.episodes);
        toolbar.setOnMenuItemClickListener(this);
        onPrepareOptionsMenu(toolbar.getMenu());

        MenuItemUtils.setupSearchItem(toolbar.getMenu(), (MainActivity) getActivity(), 0, "");

        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0;
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW);
        }
        ((MainActivity) requireActivity()).setupToolbarToggle(toolbar, displayUpArrow);

        toolbar.setVisibility(hideToolbar ? View.GONE : View.VISIBLE);

        return root;
    }

    public void setEmptyView(String tag) {
        switch (tag) {
            default:
            case EpisodesFragment.TAG + QUICKFILTER_ALL:
            case EpisodesFragment.TAG + QUICKFILTER_NEW:
                emptyView.setIcon(R.drawable.ic_feed);
                emptyView.setTitle(R.string.no_all_episodes_head_label);
                emptyView.setMessage(R.string.no_all_episodes_label);
                break;
            case EpisodesFragment.TAG + QUICKFILTER_DOWNLOADED:
                emptyView.setIcon(R.drawable.ic_download);
                emptyView.setTitle(R.string.no_comp_downloads_head_label);
                emptyView.setMessage(R.string.no_comp_downloads_label);
                break;
            case EpisodesFragment.TAG + QUICKFILTER_FAV:
                emptyView.setIcon(R.drawable.ic_star);
                emptyView.setTitle(R.string.no_fav_episodes_head_label);
                emptyView.setMessage(R.string.no_fav_episodes_label);
                break;
            case InboxFragment.TAG:
                emptyView.setIcon(R.drawable.ic_inbox);
                emptyView.setTitle(R.string.no_new_episodes_head_label);
                emptyView.setMessage(R.string.no_new_episodes_label);
                break;
        }
    }

    private void setupLoadMoreScrollListener() {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int deltaX, int deltaY) {
                super.onScrolled(view, deltaX, deltaY);
                if (!isLoadingMore && hasMoreItems && recyclerView.isScrolledToBottom()) {
                    /* The end of the list has been reached. Load more data. */
                    page++;
                    loadMoreItems();
                    isLoadingMore = true;
                }
            }
        });
    }

    public void setSwipeActions(String tag) {
        swipeActions = new SwipeActions(this, tag).attachTo(recyclerView);
    }

    private void loadMoreItems() {
        if (disposable != null) {
            disposable.dispose();
        }
        isLoadingMore = true;
        loadingMoreView.setVisibility(View.VISIBLE);
        disposable = Observable.fromCallable(this::loadMoreData)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(data -> {
                    if (data.size() < EPISODES_PER_PAGE) {
                        hasMoreItems = false;
                    }
                    episodes.addAll(data);
                    onFragmentLoaded(episodes);
                }, error -> Log.e(TAG, Log.getStackTraceString(error)),
                    () -> {
                        recyclerView.post(() -> isLoadingMore = false); // Make sure to not always load 2 pages at once
                        progLoading.setVisibility(View.GONE);
                        loadingMoreView.setVisibility(View.GONE);
                    });
    }

    protected void onFragmentLoaded(List<FeedItem> episodes) {
        boolean restoreScrollPosition = listAdapter.getItemCount() == 0;
        if (episodes.size() == 0) {
            createRecycleAdapter(recyclerView, emptyView);
        } else {
            listAdapter.updateItems(episodes);
        }
        if (restoreScrollPosition) {
            recyclerView.restoreScrollPosition(getPrefName());
        }
        if (isUpdatingFeeds != updateRefreshMenuItemChecker.isRefreshing()) {
            onPrepareOptionsMenu(toolbar.getMenu());
        }

        swipeActions.resetItemTouchHelper();
    }

    /**
     * Currently, we need to recreate the list adapter in order to be able to undo last item via the
     * snackbar. See #3084 for details.
     */
    private void createRecycleAdapter(RecyclerView recyclerView, EmptyViewHandler emptyViewHandler) {
        MainActivity mainActivity = (MainActivity) getActivity();
        listAdapter = new EpisodeItemListAdapter(mainActivity);
        listAdapter.updateItems(episodes);
        recyclerView.setAdapter(listAdapter);
        emptyViewHandler.updateAdapter(listAdapter);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(FeedItemEvent event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        for (FeedItem item : event.items) {
            int pos = FeedItemUtil.indexOfItemWithId(episodes, item.getId());
            if (pos >= 0) {
                episodes.remove(pos);
                if (shouldUpdatedItemRemainInList(item)) {
                    episodes.add(pos, item);
                    listAdapter.notifyItemChangedCompat(pos);
                } else {
                    listAdapter.notifyItemRemoved(pos);
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaybackPositionEvent event) {
        if (listAdapter != null) {
            for (int i = 0; i < listAdapter.getItemCount(); i++) {
                EpisodeItemViewHolder holder = (EpisodeItemViewHolder) recyclerView.findViewHolderForAdapterPosition(i);
                if (holder != null && holder.isCurrentlyPlayingItem()) {
                    holder.notifyPlaybackPositionUpdated(event);
                    break;
                }
            }
        }
    }

    protected boolean shouldUpdatedItemRemainInList(FeedItem item) {
        return true;
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(DownloadEvent event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        DownloaderUpdate update = event.update;
        if (event.hasChangedFeedUpdateStatus(isUpdatingFeeds)) {
            onPrepareOptionsMenu(toolbar.getMenu());
        }
        if (update.mediaIds.length > 0) {
            for (long mediaId : update.mediaIds) {
                int pos = FeedItemUtil.indexOfItemWithMediaId(episodes, mediaId);
                if (pos >= 0) {
                    listAdapter.notifyItemChangedCompat(pos);
                    swipeActions.resetItemTouchHelper();
                }
            }
        }
    }

    private void updateUi() {
        loadItems();
        if (isUpdatingFeeds != updateRefreshMenuItemChecker.isRefreshing()) {
            onPrepareOptionsMenu(toolbar.getMenu());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusChanged(PlayerStatusEvent event) {
        updateUi();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUnreadItemsChanged(UnreadItemsUpdateEvent event) {
        updateUi();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedListChanged(FeedListUpdateEvent event) {
        updateUi();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void favoritesChanged(FavoritesEvent event) {
        updateUi();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onQueueChanged(QueueEvent event) {
        updateUi();
    }

    void loadItems() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Observable.fromCallable(this::loadData)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(data -> {
                    progLoading.setVisibility(View.GONE);
                    hasMoreItems = true;
                    episodes = data;
                    onFragmentLoaded(episodes);
                    onPrepareOptionsMenu(toolbar.getMenu());
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    @NonNull
    protected abstract List<FeedItem> loadData();

    /**
     * Load a new page of data as defined by {@link #page} and {@link #EPISODES_PER_PAGE}.
     * If the number of items returned is less than {@link #EPISODES_PER_PAGE},
     * it will be assumed that the underlying data is exhausted
     * and this method will not be called again.
     *
     * @return The items from the next page of data
     */
    @NonNull
    protected abstract List<FeedItem> loadMoreData();
}
