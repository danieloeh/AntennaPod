package de.danoeh.antennapod.dialog;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.collection.ArrayMap;
import androidx.fragment.app.Fragment;
import com.google.android.material.snackbar.Snackbar;
import com.leinardi.android.speeddial.SpeedDialView;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.core.dialog.DownloadRequestErrorDialogCreator;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.net.downloadservice.DownloadRequestException;
import de.danoeh.antennapod.net.downloadservice.DownloadRequester;
import de.danoeh.antennapod.core.util.FeedItemPermutors;
import de.danoeh.antennapod.core.util.LongList;
import de.danoeh.antennapod.model.feed.SortOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EpisodesApplyActionFragment extends Fragment implements Toolbar.OnMenuItemClickListener {

    public static final String TAG = "EpisodeActionFragment";

    public static final int ACTION_ADD_TO_QUEUE = 1;
    public static final int ACTION_REMOVE_FROM_QUEUE = 2;
    private static final int ACTION_MARK_PLAYED = 4;
    private static final int ACTION_MARK_UNPLAYED = 8;
    public static final int ACTION_DOWNLOAD = 16;
    public static final int ACTION_DELETE = 32;
    public static final int ACTION_ALL = ACTION_ADD_TO_QUEUE | ACTION_REMOVE_FROM_QUEUE
            | ACTION_MARK_PLAYED | ACTION_MARK_UNPLAYED | ACTION_DOWNLOAD | ACTION_DELETE;

    /**
     * Specify an action (defined by #flag) 's UI bindings.
     *
     * Includes: the menu / action item and the actual logic
     */
    private static class ActionBinding {
        int flag;
        @IdRes
        final int actionItemId;
        @NonNull
        final Runnable action;

        ActionBinding(int flag, @IdRes int actionItemId, @NonNull Runnable action) {
            this.flag = flag;
            this.actionItemId = actionItemId;
            this.action = action;
        }
    }

    private final List<? extends ActionBinding> actionBindings;
    private final Map<Long, FeedItem> idMap = new ArrayMap<>();
    private final List<FeedItem> episodes = new ArrayList<>();
    private int actions;
    private final List<String> titles = new ArrayList<>();
    private final LongList checkedIds = new LongList();

    private ListView mListView;
    private ArrayAdapter<String> mAdapter;
    private SpeedDialView mSpeedDialView;
    private Toolbar toolbar;

    public EpisodesApplyActionFragment() {
        actionBindings = Arrays.asList(
                new ActionBinding(ACTION_ADD_TO_QUEUE,
                        R.id.add_to_queue_batch, this::queueChecked),
                new ActionBinding(ACTION_REMOVE_FROM_QUEUE,
                        R.id.remove_from_queue_batch, this::removeFromQueueChecked),
                new ActionBinding(ACTION_MARK_PLAYED,
                        R.id.mark_read_batch, this::markedCheckedPlayed),
                new ActionBinding(ACTION_MARK_UNPLAYED,
                        R.id.mark_unread_batch, this::markedCheckedUnplayed),
                new ActionBinding(ACTION_DOWNLOAD,
                        R.id.download_batch, this::downloadChecked),
                new ActionBinding(ACTION_DELETE,
                        R.id.delete_batch, this::deleteChecked)
                );
    }

    public static EpisodesApplyActionFragment newInstance(List<FeedItem> items, int actions) {
        EpisodesApplyActionFragment f = new EpisodesApplyActionFragment();
        f.episodes.addAll(items);
        for (FeedItem episode : items) {
            f.idMap.put(episode.getId(), episode);
        }
        f.actions = actions;
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.episodes_apply_action_fragment, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.episodes_apply_action_options);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());
        toolbar.setOnMenuItemClickListener(this);
        refreshToolbarState();

        mListView = view.findViewById(android.R.id.list);
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        mListView.setOnItemClickListener((listView, view1, position, rowId) -> {
            long id = episodes.get(position).getId();
            if (checkedIds.contains(id)) {
                checkedIds.remove(id);
            } else {
                checkedIds.add(id);
            }
            refreshCheckboxes();
        });
        mListView.setOnItemLongClickListener((adapterView, view12, position, id) -> {
            new AlertDialog.Builder(getActivity())
                    .setItems(R.array.batch_long_press_options, (dialogInterface, item) -> {
                        int direction;
                        if (item == 0) {
                            direction = -1;
                        } else {
                            direction = 1;
                        }

                        int currentPosition = position + direction;
                        while (currentPosition >= 0 && currentPosition < episodes.size()) {
                            long id1 = episodes.get(currentPosition).getId();
                            if (!checkedIds.contains(id1)) {
                                checkedIds.add(id1);
                            }
                            currentPosition += direction;
                        }
                        refreshCheckboxes();
                    }).show();
            return true;
        });

        titles.clear();
        for (FeedItem episode : episodes) {
            titles.add(episode.getTitle());
        }

        mAdapter = new ArrayAdapter<>(getActivity(),
                R.layout.simple_list_item_multiple_choice_on_start, titles);
        mListView.setAdapter(mAdapter);

        // Init action UI (via a FAB Speed Dial)
        mSpeedDialView = view.findViewById(R.id.fabSD);
        mSpeedDialView.inflate(R.menu.episodes_apply_action_speeddial);

        // show only specified actions, and bind speed dial UIs to the actual logic
        for (ActionBinding binding : actionBindings) {
            if ((actions & binding.flag) == 0) {
                mSpeedDialView.removeActionItemById(binding.actionItemId);
            }
        }

        mSpeedDialView.setOnChangeListener(new SpeedDialView.OnChangeListener() {
            @Override
            public boolean onMainActionSelected() {
                return false;
            }

            @Override
            public void onToggleChanged(boolean open) {
                if (open && checkedIds.size() == 0) {
                    ((MainActivity) getActivity()).showSnackbarAbovePlayer(R.string.no_items_selected,
                            Snackbar.LENGTH_SHORT);
                    mSpeedDialView.close();
                }
            }
        });
        mSpeedDialView.setOnActionSelectedListener(actionItem -> {
            ActionBinding selectedBinding = null;
            for (ActionBinding binding : actionBindings) {
                if (actionItem.getId() == binding.actionItemId) {
                    selectedBinding = binding;
                    break;
                }
            }
            if (selectedBinding != null) {
                selectedBinding.action.run();
            } else {
                Log.e(TAG, "Unrecognized speed dial action item. Do nothing. id=" + actionItem.getId());
            }
            return true;
        });
        refreshCheckboxes();
        return view;
    }

    public void refreshToolbarState() {
        MenuItem selectAllItem = toolbar.getMenu().findItem(R.id.select_toggle);
        if (checkedIds.size() == episodes.size()) {
            selectAllItem.setIcon(R.drawable.ic_select_none);
            selectAllItem.setTitle(R.string.deselect_all_label);
        } else {
            selectAllItem.setIcon(R.drawable.ic_select_all);
            selectAllItem.setTitle(R.string.select_all_label);
        }
    }

    private static final Map<Integer, SortOrder> menuItemIdToSortOrder;
    static {
        Map<Integer, SortOrder> map = new ArrayMap<>();
        map.put(R.id.sort_title_a_z, SortOrder.EPISODE_TITLE_A_Z);
        map.put(R.id.sort_title_z_a, SortOrder.EPISODE_TITLE_Z_A);
        map.put(R.id.sort_date_new_old, SortOrder.DATE_NEW_OLD);
        map.put(R.id.sort_date_old_new, SortOrder.DATE_OLD_NEW);
        map.put(R.id.sort_duration_long_short, SortOrder.DURATION_LONG_SHORT);
        map.put(R.id.sort_duration_short_long, SortOrder.DURATION_SHORT_LONG);
        menuItemIdToSortOrder = Collections.unmodifiableMap(map);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        @StringRes int resId = 0;
        switch (item.getItemId()) {
            case R.id.select_options:
                return true;
            case R.id.select_toggle:
                if (checkedIds.size() == episodes.size()) {
                    checkNone();
                } else {
                    checkAll();
                }
                return true;
            case R.id.check_all:
                checkAll();
                resId = R.string.selected_all_label;
                break;
            case R.id.check_none:
                checkNone();
                resId = R.string.deselected_all_label;
                break;
            case R.id.check_played:
                checkPlayed(true);
                resId = R.string.selected_played_label;
                break;
            case R.id.check_unplayed:
                checkPlayed(false);
                resId = R.string.selected_unplayed_label;
                break;
            case R.id.check_downloaded:
                checkDownloaded(true);
                resId = R.string.selected_downloaded_label;
                break;
            case R.id.check_not_downloaded:
                checkDownloaded(false);
                resId = R.string.selected_not_downloaded_label;
                break;
            case R.id.check_queued:
                checkQueued(true);
                resId = R.string.selected_queued_label;
                break;
            case R.id.check_not_queued:
                checkQueued(false);
                resId = R.string.selected_not_queued_label;
                break;
            case R.id.check_has_media:
                checkWithMedia();
                resId = R.string.selected_has_media_label;
                break;
            default: // handle various sort options
                SortOrder sortOrder = menuItemIdToSortOrder.get(item.getItemId());
                if (sortOrder != null) {
                    sort(sortOrder);
                    return true;
                }
        }
        if (resId != 0) {
            ((MainActivity) getActivity()).showSnackbarAbovePlayer(resId, Snackbar.LENGTH_SHORT);
            return true;
        } else {
            return false;
        }
    }

    private void sort(@NonNull SortOrder sortOrder) {
        FeedItemPermutors.getPermutor(sortOrder)
                .reorder(episodes);
        refreshTitles();
        refreshCheckboxes();
    }

    private void checkAll() {
        for (FeedItem episode : episodes) {
            if (!checkedIds.contains(episode.getId())) {
                checkedIds.add(episode.getId());
            }
        }
        refreshCheckboxes();
    }

    private void checkNone() {
        checkedIds.clear();
        refreshCheckboxes();
    }

    private void checkPlayed(boolean isPlayed) {
        for (FeedItem episode : episodes) {
            if (episode.isPlayed() == isPlayed) {
                if (!checkedIds.contains(episode.getId())) {
                    checkedIds.add(episode.getId());
                }
            } else {
                if (checkedIds.contains(episode.getId())) {
                    checkedIds.remove(episode.getId());
                }
            }
        }
        refreshCheckboxes();
    }

    private void checkDownloaded(boolean isDownloaded) {
        for (FeedItem episode : episodes) {
            if (episode.hasMedia() && episode.getMedia().isDownloaded() == isDownloaded) {
                if (!checkedIds.contains(episode.getId())) {
                    checkedIds.add(episode.getId());
                }
            } else {
                if (checkedIds.contains(episode.getId())) {
                    checkedIds.remove(episode.getId());
                }
            }
        }
        refreshCheckboxes();
    }

    private void checkQueued(boolean isQueued) {
        for (FeedItem episode : episodes) {
            if (episode.isTagged(FeedItem.TAG_QUEUE) == isQueued) {
                checkedIds.add(episode.getId());
            } else {
                checkedIds.remove(episode.getId());
            }
        }
        refreshCheckboxes();
    }

    private void checkWithMedia() {
        for (FeedItem episode : episodes) {
            if (episode.hasMedia()) {
                checkedIds.add(episode.getId());
            } else {
                checkedIds.remove(episode.getId());
            }
        }
        refreshCheckboxes();
    }

    private void refreshTitles() {
        titles.clear();
        for (FeedItem episode : episodes) {
            titles.add(episode.getTitle());
        }
        mAdapter.notifyDataSetChanged();
    }

    private void refreshCheckboxes() {
        for (int i = 0; i < episodes.size(); i++) {
            FeedItem episode = episodes.get(i);
            boolean checked = checkedIds.contains(episode.getId());
            mListView.setItemChecked(i, checked);
        }
        refreshToolbarState();
        toolbar.setTitle(getResources().getQuantityString(R.plurals.num_selected_label,
                checkedIds.size(), checkedIds.size()));
    }

    private void queueChecked() {
        // Check if an episode actually contains any media files before adding it to queue
        LongList toQueue = new LongList(checkedIds.size());
        for (FeedItem episode : episodes) {
            if (checkedIds.contains(episode.getId()) && episode.hasMedia()) {
                toQueue.add(episode.getId());
            }
        }
        DBWriter.addQueueItem(getActivity(), true, toQueue.toArray());
        close(R.plurals.added_to_queue_batch_label, toQueue.size());
    }

    private void removeFromQueueChecked() {
        DBWriter.removeQueueItem(getActivity(), true, checkedIds.toArray());
        close(R.plurals.removed_from_queue_batch_label, checkedIds.size());
    }

    private void markedCheckedPlayed() {
        DBWriter.markItemPlayed(FeedItem.PLAYED, checkedIds.toArray());
        close(R.plurals.marked_read_batch_label, checkedIds.size());
    }

    private void markedCheckedUnplayed() {
        DBWriter.markItemPlayed(FeedItem.UNPLAYED, checkedIds.toArray());
        close(R.plurals.marked_unread_batch_label, checkedIds.size());
    }

    private void downloadChecked() {
        // download the check episodes in the same order as they are currently displayed
        List<FeedItem> toDownload = new ArrayList<>(checkedIds.size());
        for (FeedItem episode : episodes) {
            if (checkedIds.contains(episode.getId()) && episode.hasMedia() && !episode.getFeed().isLocalFeed()) {
                toDownload.add(episode);
            }
        }
        try {
            DownloadRequester.getInstance().downloadMedia(getActivity(), true, toDownload.toArray(new FeedItem[0]));
        } catch (DownloadRequestException e) {
            e.printStackTrace();
            DownloadRequestErrorDialogCreator.newRequestErrorDialog(getActivity(), e.getMessage());
        }
        close(R.plurals.downloading_batch_label, toDownload.size());
    }

    private void deleteChecked() {
        int countHasMedia = 0;
        int countNoMedia = 0;
        for (long id : checkedIds.toArray()) {
            FeedItem episode = idMap.get(id);
            if (episode.hasMedia() && episode.getMedia().isDownloaded()) {
                countHasMedia++;
                DBWriter.deleteFeedMediaOfItem(getActivity(), episode.getMedia().getId());
            } else {
                countNoMedia++;
            }
        }
        closeMore(R.plurals.deleted_multi_episode_batch_label, countNoMedia, countHasMedia);
    }

    private void close(@PluralsRes int msgId, int numItems) {
        ((MainActivity) getActivity()).showSnackbarAbovePlayer(
                getResources().getQuantityString(msgId, numItems, numItems), Snackbar.LENGTH_LONG);
        getActivity().getSupportFragmentManager().popBackStack();
    }

    private void closeMore(@PluralsRes int msgId, int countNoMedia, int countHasMedia) {
        ((MainActivity) getActivity()).showSnackbarAbovePlayer(
                getResources().getQuantityString(msgId,
                        (countHasMedia + countNoMedia),
                        (countHasMedia + countNoMedia), countHasMedia),
                Snackbar.LENGTH_LONG);
        getActivity().getSupportFragmentManager().popBackStack();
    }
}
