package de.danoeh.antennapod.fragment.homesections;

import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.EpisodeItemListAdapter;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.util.FeedItemUtil;
import de.danoeh.antennapod.fragment.HomeFragment;
import de.danoeh.antennapod.fragment.InboxFragment;
import de.danoeh.antennapod.fragment.ItemPagerFragment;
import de.danoeh.antennapod.fragment.SwipeActions;
import de.danoeh.antennapod.model.feed.FeedItem;
import kotlin.Unit;


public class InboxSection extends HomeSection<FeedItem> {

    public static final String TAG = "InboxSection";

    private EpisodeItemListAdapter adapter;

    private SwipeActions swipeActions;

    public InboxSection(HomeFragment context) {
        super(context);
        sectionTitle = context.getString(R.string.new_title);
        sectionNavigateTitle = context.getString(R.string.inbox_label);
        updateEvents = Arrays.asList(UpdateEvents.FEED_ITEM, UpdateEvents.UNREAD);

        recyclerView.setPadding(0, 0, 0, 0);
        swipeActions = new SwipeActions(context, InboxFragment.TAG).attachTo(recyclerView);
    }

    @NonNull
    @Override
    protected View.OnClickListener navigate() {
        return view ->
                ((MainActivity) context.requireActivity()).loadFragment(InboxFragment.TAG, null);
    }

    @Override
    protected Unit onItemClick(View view, FeedItem feedItem) {
        //TODO PLAY
        long[] ids = FeedItemUtil.getIds(loadItems());
        int position = ArrayUtils.indexOf(ids, feedItem.getId());
        ((MainActivity) context.requireActivity()).loadChildFragment(ItemPagerFragment.newInstance(ids, position));
        return null;
    }

    @Override
    public void addSectionTo(LinearLayout parent) {
        adapter = new EpisodeItemListAdapter((MainActivity) context.requireActivity());
        adapter.updateItems(loadItems());
        recyclerView.setLayoutManager(new LinearLayoutManager(context.getContext(), RecyclerView.VERTICAL, false));
        recyclerView.setRecycledViewPool(((MainActivity) context.requireActivity()).getRecycledViewPool());
        recyclerView.setAdapter(adapter);

        adapter.selectedItem.observe(context, feedItem -> context.setSelectedItem(feedItem));
        super.addSectionTo(parent);
    }

    @NonNull
    @Override
    protected List<FeedItem> loadItems() {
        return DBReader.getNewItemsList(0, 2);
    }

    @Override
    public void updateItems(UpdateEvents event) {
        adapter.updateItems(loadItems());
        swipeActions.resetItemTouchHelper();
        super.updateItems(event);
    }
}
