package de.danoeh.antennapod.fragment;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.google.android.material.snackbar.Snackbar;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.QueueRecyclerAdapter;
import de.danoeh.antennapod.core.dialog.ConfirmationDialog;
import de.danoeh.antennapod.core.event.FeedItemEvent;
import de.danoeh.antennapod.core.event.PlaybackPositionEvent;
import de.danoeh.antennapod.core.event.PlayerStatusEvent;
import de.danoeh.antennapod.core.event.QueueEvent;
import de.danoeh.antennapod.core.event.UnreadItemsUpdateEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.core.feed.util.PlaybackSpeedUtils;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.util.Converter;
import de.danoeh.antennapod.core.util.FeedItemUtil;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.core.util.download.AutoUpdateManager;
import de.danoeh.antennapod.dialog.EpisodesApplyActionFragment;
import de.danoeh.antennapod.menuhandler.FeedItemMenuHandler;
import de.danoeh.antennapod.menuhandler.MenuItemUtils;
import de.danoeh.antennapod.net.downloadservice.DownloadRequest;
import de.danoeh.antennapod.net.downloadservice.DownloadRequester;
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

import java.util.List;
import java.util.Locale;

import static de.danoeh.antennapod.dialog.EpisodesApplyActionFragment.ACTION_DELETE;
import static de.danoeh.antennapod.dialog.EpisodesApplyActionFragment.ACTION_DOWNLOAD;
import static de.danoeh.antennapod.dialog.EpisodesApplyActionFragment.ACTION_REMOVE_FROM_QUEUE;

/**
 * Shows all items in the queue.
 */
public class QueueFragment extends Fragment implements Toolbar.OnMenuItemClickListener {
    public static final String TAG = "QueueFragment";
    private static final String KEY_UP_ARROW = "up_arrow";

    private TextView infoBar;
    private EpisodeItemListRecyclerView recyclerView;
    private QueueRecyclerAdapter recyclerAdapter;
    private EmptyViewHandler emptyView;
    private ProgressBar progLoading;
    private Toolbar toolbar;
    private boolean displayUpArrow;

    private List<FeedItem> queue;
    private static final String PREFS = "QueueFragment";
    private static final String PREF_SHOW_LOCK_WARNING = "show_lock_warning";

    private Disposable disposable;
    private ItemTouchHelper itemTouchHelper;
    private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        prefs = getActivity().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (queue != null) {
            onFragmentLoaded(true);
        }
        loadItems(true);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        recyclerView.saveScrollPosition(QueueFragment.TAG);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(QueueEvent event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        if (queue == null) {
            return;
        } else if (recyclerAdapter == null) {
            loadItems(true);
            return;
        }
        switch(event.action) {
            case ADDED:
                queue.add(event.position, event.item);
                recyclerAdapter.notifyItemInserted(event.position);
                break;
            case SET_QUEUE:
            case SORTED: //Deliberate fall-through
                queue = event.items;
                recyclerAdapter.notifyDataSetChanged();
                break;
            case REMOVED:
            case IRREVERSIBLE_REMOVED:
                int position = FeedItemUtil.indexOfItemWithId(queue, event.item.getId());
                queue.remove(position);
                recyclerAdapter.notifyItemRemoved(position);
                break;
            case CLEARED:
                queue.clear();
                recyclerAdapter.notifyDataSetChanged();
                break;
            case MOVED:
                return;
        }
        recyclerView.saveScrollPosition(QueueFragment.TAG);
        onFragmentLoaded(false);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(FeedItemEvent event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        if (queue == null) {
            return;
        } else if (recyclerAdapter == null) {
            loadItems(true);
            return;
        }
        for (int i = 0, size = event.items.size(); i < size; i++) {
            FeedItem item = event.items.get(i);
            int pos = FeedItemUtil.indexOfItemWithId(queue, item.getId());
            if (pos >= 0) {
                queue.remove(pos);
                queue.add(pos, item);
                recyclerAdapter.notifyItemChangedCompat(pos);
                refreshInfoBar();
            }
        }
    }

    /*@Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(DownloadEvent event) {
        Log.d(TAG, "onEventMainThread() called with DownloadEvent");
        DownloaderUpdate update = event.update;
        if (event.hasChangedFeedUpdateStatus(isUpdatingFeeds)) {
            refreshToolbarState();
        }
        if (recyclerAdapter != null && update.mediaIds.length > 0) {
            for (long mediaId : update.mediaIds) {
                int pos = FeedItemUtil.indexOfItemWithMediaId(queue, mediaId);
                if (pos >= 0) {
                    recyclerAdapter.notifyItemChangedCompat(pos);
                }
            }
        }
    }*/

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaybackPositionEvent event) {
        if (recyclerAdapter != null) {
            for (int i = 0; i < recyclerAdapter.getItemCount(); i++) {
                EpisodeItemViewHolder holder = (EpisodeItemViewHolder)
                        recyclerView.findViewHolderForAdapterPosition(i);
                if (holder != null && holder.isCurrentlyPlayingItem()) {
                    holder.notifyPlaybackPositionUpdated(event);
                    break;
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusChanged(PlayerStatusEvent event) {
        loadItems(false);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUnreadItemsChanged(UnreadItemsUpdateEvent event) {
        // Sent when playback position is reset
        loadItems(false);
    }

    private void resetViewState() {
        recyclerAdapter = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        resetViewState();
    }

    private void refreshToolbarState() {
        MenuItemUtils.refreshLockItem(getActivity(), toolbar.getMenu());
        boolean keepSorted = UserPreferences.isQueueKeepSorted();

        toolbar.getMenu().findItem(R.id.queue_sort_random).setVisible(!keepSorted);
        toolbar.getMenu().findItem(R.id.queue_keep_sorted).setChecked(keepSorted);
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
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.queue_lock:
                toggleQueueLock();
                return true;
            case R.id.refresh_item:
                DownloadRequester.getInstance().refreshAllFeeds(requireContext());
                return true;
            case R.id.clear_queue:
                // make sure the user really wants to clear the queue
                ConfirmationDialog conDialog = new ConfirmationDialog(getActivity(),
                        R.string.clear_queue_label,
                        R.string.clear_queue_confirmation_msg) {

                    @Override
                    public void onConfirmButtonPressed(
                            DialogInterface dialog) {
                        dialog.dismiss();
                        DBWriter.clearQueue();
                    }
                };
                conDialog.createNewDialog().show();
                return true;
            case R.id.episode_actions:
                ((MainActivity) requireActivity()).loadChildFragment(
                        EpisodesApplyActionFragment.newInstance(queue,
                                ACTION_DELETE | ACTION_REMOVE_FROM_QUEUE  | ACTION_DOWNLOAD));
                return true;
            case R.id.queue_sort_episode_title_asc:
                setSortOrder(SortOrder.EPISODE_TITLE_A_Z);
                return true;
            case R.id.queue_sort_episode_title_desc:
                setSortOrder(SortOrder.EPISODE_TITLE_Z_A);
                return true;
            case R.id.queue_sort_date_asc:
                setSortOrder(SortOrder.DATE_OLD_NEW);
                return true;
            case R.id.queue_sort_date_desc:
                setSortOrder(SortOrder.DATE_NEW_OLD);
                return true;
            case R.id.queue_sort_duration_asc:
                setSortOrder(SortOrder.DURATION_SHORT_LONG);
                return true;
            case R.id.queue_sort_duration_desc:
                setSortOrder(SortOrder.DURATION_LONG_SHORT);
                return true;
            case R.id.queue_sort_feed_title_asc:
                setSortOrder(SortOrder.FEED_TITLE_A_Z);
                return true;
            case R.id.queue_sort_feed_title_desc:
                setSortOrder(SortOrder.FEED_TITLE_Z_A);
                return true;
            case R.id.queue_sort_random:
                setSortOrder(SortOrder.RANDOM);
                return true;
            case R.id.queue_sort_smart_shuffle_asc:
                setSortOrder(SortOrder.SMART_SHUFFLE_OLD_NEW);
                return true;
            case R.id.queue_sort_smart_shuffle_desc:
                setSortOrder(SortOrder.SMART_SHUFFLE_NEW_OLD);
                return true;
            case R.id.queue_keep_sorted:
                boolean keepSortedOld = UserPreferences.isQueueKeepSorted();
                boolean keepSortedNew = !keepSortedOld;
                UserPreferences.setQueueKeepSorted(keepSortedNew);
                if (keepSortedNew) {
                    SortOrder sortOrder = UserPreferences.getQueueKeepSortedOrder();
                    DBWriter.reorderQueue(sortOrder, true);
                }
                if (recyclerAdapter != null) {
                    recyclerAdapter.updateDragDropEnabled();
                }
                refreshToolbarState();
                return true;
            default:
                return false;
        }
    }

    private void toggleQueueLock() {
        boolean isLocked = UserPreferences.isQueueLocked();
        if (isLocked) {
            setQueueLocked(false);
        } else {
            boolean shouldShowLockWarning = prefs.getBoolean(PREF_SHOW_LOCK_WARNING, true);
            if (!shouldShowLockWarning) {
                setQueueLocked(true);
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.lock_queue);
                builder.setMessage(R.string.queue_lock_warning);

                View view = View.inflate(getContext(), R.layout.checkbox_do_not_show_again, null);
                CheckBox checkDoNotShowAgain = view.findViewById(R.id.checkbox_do_not_show_again);
                builder.setView(view);

                builder.setPositiveButton(R.string.lock_queue, (dialog, which) -> {
                    prefs.edit().putBoolean(PREF_SHOW_LOCK_WARNING, !checkDoNotShowAgain.isChecked()).apply();
                    setQueueLocked(true);
                });
                builder.setNegativeButton(R.string.cancel_label, null);
                builder.show();
            }
        }
    }

    private void setQueueLocked(boolean locked) {
        UserPreferences.setQueueLocked(locked);
        refreshToolbarState();
        if (recyclerAdapter != null) {
            recyclerAdapter.updateDragDropEnabled();
        }
        if (queue.size() == 0) {
            if (locked) {
                ((MainActivity) getActivity()).showSnackbarAbovePlayer(R.string.queue_locked, Snackbar.LENGTH_SHORT);
            } else {
                ((MainActivity) getActivity()).showSnackbarAbovePlayer(R.string.queue_unlocked, Snackbar.LENGTH_SHORT);
            }
        }
    }

    /**
     * This method is called if the user clicks on a sort order menu item.
     *
     * @param sortOrder New sort order.
     */
    private void setSortOrder(SortOrder sortOrder) {
        UserPreferences.setQueueKeepSortedOrder(sortOrder);
        DBWriter.reorderQueue(sortOrder, true);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Log.d(TAG, "onContextItemSelected() called with: " + "item = [" + item + "]");
        if (!isVisible() || recyclerAdapter == null) {
            return false;
        }
        FeedItem selectedItem = recyclerAdapter.getSelectedItem();
        if (selectedItem == null) {
            Log.i(TAG, "Selected item was null, ignoring selection");
            return super.onContextItemSelected(item);
        }

        int position = FeedItemUtil.indexOfItemWithId(queue, selectedItem.getId());
        if (position < 0) {
            Log.i(TAG, "Selected item no longer exist, ignoring selection");
            return super.onContextItemSelected(item);
        }

        switch(item.getItemId()) {
            case R.id.move_to_top_item:
                queue.add(0, queue.remove(position));
                recyclerAdapter.notifyItemMoved(position, 0);
                DBWriter.moveQueueItemToTop(selectedItem.getId(), true);
                return true;
            case R.id.move_to_bottom_item:
                queue.add(queue.size()-1, queue.remove(position));
                recyclerAdapter.notifyItemMoved(position, queue.size()-1);
                DBWriter.moveQueueItemToBottom(selectedItem.getId(), true);
                return true;
            default:
                return FeedItemMenuHandler.onMenuItemClicked(this, item.getItemId(), selectedItem);
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View root = inflater.inflate(R.layout.queue_fragment, container, false);
        toolbar = root.findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(this);
        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0;
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW);
        }
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar, displayUpArrow);
        toolbar.inflateMenu(R.menu.queue);
        MenuItemUtils.setupSearchItem(toolbar.getMenu(), (MainActivity) getActivity(), 0, "");
        refreshToolbarState();

        infoBar = root.findViewById(R.id.info_bar);
        recyclerView = root.findViewById(R.id.recyclerView);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        recyclerView.setRecycledViewPool(((MainActivity) getActivity()).getRecycledViewPool());
        registerForContextMenu(recyclerView);

        SwipeRefreshLayout swipeRefreshLayout = root.findViewById(R.id.swipeRefresh);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            DownloadRequester.getInstance().refreshAllFeeds(requireContext());
            new Handler(Looper.getMainLooper()).postDelayed(() -> swipeRefreshLayout.setRefreshing(false),
                    getResources().getInteger(R.integer.swipe_to_refresh_duration_in_ms));
        });

        itemTouchHelper = new ItemTouchHelper(
            new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                    ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

                // Position tracking whilst dragging
                int dragFrom = -1;
                int dragTo = -1;

                @Override
                public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                      RecyclerView.ViewHolder target) {
                    int fromPosition = viewHolder.getAdapterPosition();
                    int toPosition = target.getAdapterPosition();

                    // Update tracked position
                    if (dragFrom == -1) {
                        dragFrom =  fromPosition;
                    }
                    dragTo = toPosition;

                    int from = viewHolder.getAdapterPosition();
                    int to = target.getAdapterPosition();
                    Log.d(TAG, "move(" + from + ", " + to + ") in memory");
                    if (from >= queue.size() || to >= queue.size() || from < 0 || to < 0) {
                        return false;
                    }
                    queue.add(to, queue.remove(from));
                    recyclerAdapter.notifyItemMoved(from, to);
                    return true;
                }

                @Override
                public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                    if (disposable != null) {
                        disposable.dispose();
                    }
                    final int position = viewHolder.getAdapterPosition();
                    Log.d(TAG, "remove(" + position + ")");
                    final FeedItem item = queue.get(position);
                    DBWriter.removeQueueItem(getActivity(), true, item);

                    ((MainActivity) getActivity()).showSnackbarAbovePlayer(
                            getResources().getQuantityString(R.plurals.removed_from_queue_batch_label, 1, 1),
                            Snackbar.LENGTH_LONG)
                            .setAction(getString(R.string.undo), v ->
                                    DBWriter.addQueueItemAt(getActivity(), item.getId(), position, false));
                }

                @Override
                public boolean isLongPressDragEnabled() {
                    return false;
                }

                @Override
                public boolean isItemViewSwipeEnabled() {
                    return !UserPreferences.isQueueLocked();
                }

                @Override
                public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                    super.clearView(recyclerView, viewHolder);
                    // Check if drag finished
                    if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                        reallyMoved(dragFrom, dragTo);
                    }

                    dragFrom = dragTo = -1;
                }

                private void reallyMoved(int from, int to) {
                    // Write drag operation to database
                    Log.d(TAG, "Write to database move(" + from + ", " + to + ")");
                    DBWriter.moveQueueItem(from, to, true);
                }
            }
        );
        itemTouchHelper.attachToRecyclerView(recyclerView);

        emptyView = new EmptyViewHandler(getContext());
        emptyView.attachToRecyclerView(recyclerView);
        emptyView.setIcon(R.drawable.ic_playlist);
        emptyView.setTitle(R.string.no_items_header_label);
        emptyView.setMessage(R.string.no_items_label);

        progLoading = root.findViewById(R.id.progLoading);
        progLoading.setVisibility(View.VISIBLE);
        setupRefreshIndicator();

        return root;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow);
        super.onSaveInstanceState(outState);
    }

    private void onFragmentLoaded(final boolean restoreScrollPosition) {
        if (queue != null && queue.size() > 0) {
            if (recyclerAdapter == null) {
                MainActivity activity = (MainActivity) getActivity();
                recyclerAdapter = new QueueRecyclerAdapter(activity, itemTouchHelper);
                recyclerView.setAdapter(recyclerAdapter);
                emptyView.updateAdapter(recyclerAdapter);
            }
            recyclerAdapter.updateItems(queue);
            recyclerView.setVisibility(View.VISIBLE);
        } else {
            recyclerAdapter = null;
            recyclerView.setVisibility(View.GONE);
            emptyView.updateAdapter(recyclerAdapter);
        }

        if (restoreScrollPosition) {
            recyclerView.restoreScrollPosition(QueueFragment.TAG);
        }

        // we need to refresh the options menu because it sometimes
        // needs data that may have just been loaded.
        refreshToolbarState();

        refreshInfoBar();
    }

    private void refreshInfoBar() {
        String info = String.format(Locale.getDefault(), "%d%s",
                queue.size(), getString(R.string.episodes_suffix));
        if (queue.size() > 0) {
            long timeLeft = 0;
            for (FeedItem item : queue) {
                float playbackSpeed = 1;
                if (UserPreferences.timeRespectsSpeed()) {
                    playbackSpeed = PlaybackSpeedUtils.getCurrentPlaybackSpeed(item.getMedia());
                }
                if (item.getMedia() != null) {
                    long itemTimeLeft = item.getMedia().getDuration() - item.getMedia().getPosition();
                    timeLeft += itemTimeLeft / playbackSpeed;
                }
            }
            info += " • ";
            info += getString(R.string.time_left_label);
            info += Converter.getDurationStringLocalized(getActivity(), timeLeft);
        }
        infoBar.setText(info);
    }

    private void loadItems(final boolean restoreScrollPosition) {
        Log.d(TAG, "loadItems()");
        if (disposable != null) {
            disposable.dispose();
        }
        if (queue == null) {
            recyclerView.setVisibility(View.GONE);
            emptyView.hide();
            progLoading.setVisibility(View.VISIBLE);
        }
        disposable = Observable.fromCallable(DBReader::getQueue)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    progLoading.setVisibility(View.GONE);
                    queue = items;
                    onFragmentLoaded(restoreScrollPosition);
                    if (recyclerAdapter != null) {
                        recyclerAdapter.notifyDataSetChanged();
                    }
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }
}
