package de.danoeh.antennapod.fragment.homesections;

import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.CoverLoader;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.NavDrawerData;
import de.danoeh.antennapod.fragment.FeedItemlistFragment;
import de.danoeh.antennapod.fragment.HomeFragment;
import de.danoeh.antennapod.fragment.SubscriptionFragment;
import de.danoeh.antennapod.model.feed.Feed;
import kotlin.Unit;


public class SubscriptionsSection extends HomeSection<NavDrawerData.DrawerItem> {

    public static final String TAG = "SubsSection";

    public SubscriptionsSection(HomeFragment context) {
        super(context);
        sectionTitle = context.getString(R.string.rediscover_title);
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
        easySlush(R.layout.quick_feed_discovery_item, (view, item) -> {
            DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
            int side = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 90, displayMetrics);
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
        });

        super.addSectionTo(parent);
    }

    @NonNull
    @Override
    protected List<NavDrawerData.DrawerItem> loadItems() {
        List<NavDrawerData.DrawerItem> items = DBReader.getNavDrawerData(UserPreferences.FEED_ORDER_MOST_PLAYED).items;
        //Least played on top
        Collections.reverse(items);
        //mix up the first few podcasts
        if (items.size() > 4) {
            List<NavDrawerData.DrawerItem> topItems = items.subList(0, 4);
            items = items.subList(4, items.size());
            Collections.shuffle(topItems);
            topItems.addAll(items);
            items = topItems;
        }
        return items;
    }

    //don't update, to prevent reordering of topItems
    //public void updateItems()
}
