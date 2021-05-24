package de.danoeh.antennapod.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.EpisodeItemListAdapter;
import de.danoeh.antennapod.adapter.actionbutton.DeleteActionButton;
import de.danoeh.antennapod.core.event.DownloadLogEvent;
import de.danoeh.antennapod.core.event.FeedItemEvent;
import de.danoeh.antennapod.core.event.PlaybackPositionEvent;
import de.danoeh.antennapod.core.event.PlayerStatusEvent;
import de.danoeh.antennapod.core.event.UnreadItemsUpdateEvent;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.util.FeedItemUtil;
import de.danoeh.antennapod.core.util.download.AutoUpdateManager;
import de.danoeh.antennapod.dialog.EpisodesApplyActionFragment;
import de.danoeh.antennapod.menuhandler.FeedItemMenuHandler;
import de.danoeh.antennapod.model.feed.FeedItem;
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

import java.util.ArrayList;
import java.util.List;

import static de.danoeh.antennapod.dialog.EpisodesApplyActionFragment.ACTION_ADD_TO_QUEUE;
import static de.danoeh.antennapod.dialog.EpisodesApplyActionFragment.ACTION_DELETE;

/**
 * Displays all completed downloads and provides a button to delete them.
 */
public class CompletedDownloadsFragment extends Fragment {

    private static final String TAG = CompletedDownloadsFragment.class.getSimpleName();

    private List<FeedItem> items = new ArrayList<>();
    private CompletedDownloadsListAdapter adapter;
    private EpisodeItemListRecyclerView recyclerView;
    private ProgressBar progressBar;
    private Disposable disposable;
    private EmptyViewHandler emptyView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.simple_list_fragment, container, false);
        Toolbar toolbar = root.findViewById(R.id.toolbar);
        toolbar.setVisibility(View.GONE);

        recyclerView = root.findViewById(R.id.recyclerView);
        recyclerView.setRecycledViewPool(((MainActivity) getActivity()).getRecycledViewPool());
        recyclerView.setVisibility(View.GONE);
        adapter = new CompletedDownloadsListAdapter((MainActivity) getActivity());
        recyclerView.setAdapter(adapter);
        progressBar = root.findViewById(R.id.progLoading);

        addEmptyView();
        EventBus.getDefault().register(this);
        return root;
    }

    @Override
    public void onDestroyView() {
        EventBus.getDefault().unregister(this);
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        loadItems();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        menu.findItem(R.id.clear_logs_item).setVisible(false);
        menu.findItem(R.id.episode_actions).setVisible(items.size() > 0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.episode_actions) {
            ((MainActivity) requireActivity())
                    .loadChildFragment(EpisodesApplyActionFragment.newInstance(items, ACTION_DELETE | ACTION_ADD_TO_QUEUE));
            return true;
        } else if (item.getItemId() == R.id.refresh_item) {

            DownloadRequester.getInstance().refreshAllFeeds(requireContext());
            return true;
        }
        return false;
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

    private void addEmptyView() {
        emptyView = new EmptyViewHandler(getActivity());
        emptyView.setIcon(R.drawable.ic_download);
        emptyView.setTitle(R.string.no_comp_downloads_head_label);
        emptyView.setMessage(R.string.no_comp_downloads_label);
        emptyView.attachToRecyclerView(recyclerView);
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(FeedItemEvent event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        if (items == null) {
            return;
        } else if (adapter == null) {
            loadItems();
            return;
        }
        for (int i = 0, size = event.items.size(); i < size; i++) {
            FeedItem item = event.items.get(i);
            int pos = FeedItemUtil.indexOfItemWithId(items, item.getId());
            if (pos >= 0) {
                items.remove(pos);
                if (item.getMedia().isDownloaded()) {
                    items.add(pos, item);
                    adapter.notifyItemChangedCompat(pos);
                } else {
                    adapter.notifyItemRemoved(pos);
                }
            }
        }
    }

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
        loadItems();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadLogChanged(DownloadLogEvent event) {
        loadItems();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUnreadItemsChanged(UnreadItemsUpdateEvent event) {
        loadItems();
    }

    private void loadItems() {
        if (disposable != null) {
            disposable.dispose();
        }
        progressBar.setVisibility(View.VISIBLE);
        emptyView.hide();
        disposable = Observable.fromCallable(DBReader::getDownloadedItems)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    items = result;
                    adapter.updateItems(result);
                    ((PagedToolbarFragment) getParentFragment()).invalidateOptionsMenuIfActive(this);
                    progressBar.setVisibility(View.GONE);
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private static class CompletedDownloadsListAdapter extends EpisodeItemListAdapter {

        public CompletedDownloadsListAdapter(MainActivity mainActivity) {
            super(mainActivity);
        }

        @Override
        public void afterBindViewHolder(EpisodeItemViewHolder holder, int pos) {
            DeleteActionButton actionButton = new DeleteActionButton(getItem(pos));
            actionButton.configure(holder.secondaryActionButton, holder.secondaryActionIcon, getActivity());
        }
    }
}
