package de.danoeh.antennapod.fragment.homesections;

import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.CoverLoader;
import de.danoeh.antennapod.core.feed.LocalFeedUpdater;
import de.danoeh.antennapod.core.feed.util.ImageResourceUtils;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.NavDrawerData;
import de.danoeh.antennapod.core.util.FeedItemUtil;
import de.danoeh.antennapod.fragment.FeedItemlistFragment;
import de.danoeh.antennapod.fragment.HomeFragment;
import de.danoeh.antennapod.fragment.ItemPagerFragment;
import de.danoeh.antennapod.fragment.SubscriptionFragment;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import kotlin.Unit;
import slush.AdapterAppliedResult;
import slush.Slush;


public class SubsSection extends HomeSection<NavDrawerData.DrawerItem> {

    public static final String TAG = "SubsSection";

    private AdapterAppliedResult<NavDrawerData.DrawerItem> slush;

    public SubsSection(HomeFragment context) {
        super(context);
        sectionTitle = "Rediscover";
        sectionNavigateTitle = context.getString(R.string.subscriptions_label);
    }

    @NonNull
    @Override
    protected View.OnClickListener navigate() {
        return view -> {
            ((MainActivity) context.requireActivity()).loadFragment(SubscriptionFragment.TAG, null);
        };
    }

    protected Unit onItemClick(View view, NavDrawerData.DrawerItem item) {
        if (item.type == NavDrawerData.DrawerItem.Type.FEED) {
            Feed feed = ((NavDrawerData.FeedDrawerItem) item).feed;
            Fragment fragment = FeedItemlistFragment.newInstance(feed.getId());
            ((MainActivity) context.requireActivity()).loadChildFragment(fragment);
        } else if (item.type == NavDrawerData.DrawerItem.Type.FOLDER) {
            Fragment fragment = SubscriptionFragment.newInstance(item.getTitle());
            ((MainActivity) context.requireActivity()).loadChildFragment(fragment);
        }
        return null;
    }

    @Override
    public void addSectionTo(LinearLayout parent) {
        slush = new Slush.SingleType<NavDrawerData.DrawerItem>()
                .setItemLayout(R.layout.quick_feed_discovery_item)
                .setLayoutManager(new LinearLayoutManager(context.getContext(), RecyclerView.HORIZONTAL, false))
                .setItems(loadItems())
                .onItemClickWithItem(this::onItemClick)
                .onBind((view, item) -> {
                    DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
                    int side = (int) displayMetrics.density * 140;
                    view.getLayoutParams().height = side;
                    view.getLayoutParams().width = side;
                    ImageView cover = view.findViewById(R.id.discovery_cover);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        cover.setElevation(2 * displayMetrics.density);
                    }
                    CoverLoader coverLoader = new CoverLoader((MainActivity) context.requireActivity())
                            .withCoverView(cover);
                    if (item.type == NavDrawerData.DrawerItem.Type.FEED) {
                        Feed feed = ((NavDrawerData.FeedDrawerItem) item).feed;
                        coverLoader.withUri(feed.getImageUrl());
                    } else {
                        coverLoader.withResource(R.drawable.ic_folder);
                    }
                    coverLoader.load();
                })
                .into(recyclerView);

        super.addSectionTo(parent);
    }

    @NonNull
    @Override
    protected List<NavDrawerData.DrawerItem> loadItems() {
        List<NavDrawerData.DrawerItem> items = DBReader.getNavDrawerData(UserPreferences.FEED_ORDER_MOST_PLAYED).items;
        //Least played on top
        Collections.reverse(items);
        //mix up the first few podcasts
        List<NavDrawerData.DrawerItem> topItems = items.subList(0,4);
        items = items.subList(4, items.size());
        Collections.shuffle(topItems);
        topItems.addAll(items);
        return topItems;
    }

    //don't update, to prevent reordering of topItems
    //public void updateItems()
}
