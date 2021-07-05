package de.danoeh.antennapod.adapter;

import android.content.Context;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.text.TextUtilsCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.Locale;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.core.feed.LocalFeedUpdater;
import de.danoeh.antennapod.core.storage.NavDrawerData;
import de.danoeh.antennapod.fragment.FeedItemlistFragment;
import de.danoeh.antennapod.fragment.SubscriptionFragment;
import de.danoeh.antennapod.model.feed.Feed;
import jp.shts.android.library.TriangleLabelView;

/**
 * Adapter for subscriptions
 */
public class SubscriptionsRecyclerAdapter extends RecyclerView.Adapter<SubscriptionsRecyclerAdapter.SubscriptionViewHolder> implements View.OnCreateContextMenuListener
{
    /** the position in the view that holds the add item; 0 is the first, -1 is the last position */
    private static final String TAG = "SubscriptionsRecyclerAdapter";

    private final WeakReference<MainActivity> mainActivityRef;
    private final ItemAccess itemAccess;
    private Feed selectedFeed = null;
    public SubscriptionsRecyclerAdapter(MainActivity mainActivity, ItemAccess itemAccess) {
        this.mainActivityRef = new WeakReference<>(mainActivity);
        this.itemAccess = itemAccess;
    }


    public Object getItem(int position) {
        return itemAccess.getItem(position);
    }
//    @Override
//    public long getItemId(int position) {
//        return ((NavDrawerData.DrawerItem) getItem(position)).id;
//    }
//

//    @Override
//    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//        final NavDrawerData.DrawerItem drawerItem = (NavDrawerData.DrawerItem) getItem(position);
//        if (drawerItem == null) {
//            return;
//        }
//        if (drawerItem.type == NavDrawerData.DrawerItem.Type.FEED) {
//            Feed feed = ((NavDrawerData.FeedDrawerItem) drawerItem).feed;
//            Fragment fragment = FeedItemlistFragment.newInstance(feed.getId());
//            mainActivityRef.get().loadChildFragment(fragment);
//        } else if (drawerItem.type == NavDrawerData.DrawerItem.Type.FOLDER) {
//            Fragment fragment = SubscriptionFragment.newInstance(drawerItem.getTitle());
//            mainActivityRef.get().loadChildFragment(fragment);
//        }
//    }

    @NonNull
    @Override
    public SubscriptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(mainActivityRef.get()).inflate(R.layout.subscription_item, parent, false);
        SubscriptionViewHolder viewHolder = new SubscriptionViewHolder(itemView);

        return viewHolder;
    }


    @Override
    public void onBindViewHolder(@NonNull SubscriptionViewHolder holder, int position) {
        holder.onBind(itemAccess.getItem(position));
        holder.itemView.setOnCreateContextMenuListener(this);

    }

    @Override
    public int getItemCount() {
        return itemAccess.getCount();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        NavDrawerData.DrawerItem selectedObject = null;
        if(selectedFeed != null ) {
            MenuInflater inflater = mainActivityRef.get().getMenuInflater();
            inflater.inflate(R.menu.nav_feed_context, menu);
            menu.setHeaderTitle(selectedFeed.getTitle());
        }
    }

    public interface ItemAccess {
        int getCount();

        NavDrawerData.DrawerItem getItem(int position);
    }

    class SubscriptionViewHolder extends RecyclerView.ViewHolder {
        private TextView feedTitle;
        private ImageView imageView;
        private TriangleLabelView count;
        public SubscriptionViewHolder(@NonNull View itemView) {
            super(itemView);
            feedTitle = itemView.findViewById(R.id.txtvTitle);
            imageView = itemView.findViewById(R.id.imgvCover);
            count = itemView.findViewById(R.id.triangleCountView);
        }

        public void onBind(NavDrawerData.DrawerItem drawerItem) {
            feedTitle.setText(drawerItem.getTitle());
            imageView.setContentDescription(drawerItem.getTitle());
            feedTitle.setVisibility(View.VISIBLE);

            if (TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault())
                    ==ViewCompat.LAYOUT_DIRECTION_RTL) {
                count.setCorner(TriangleLabelView.Corner.TOP_LEFT);
            }

            if (drawerItem.getCounter() > 0) {
                count.setPrimaryText(NumberFormat.getInstance().format(drawerItem.getCounter()));
                count.setVisibility(View.VISIBLE);
            } else {
                count.setVisibility(View.GONE);
            }

            if (drawerItem.type == NavDrawerData.DrawerItem.Type.FEED) {
                Feed feed = ((NavDrawerData.FeedDrawerItem) drawerItem).feed;
                boolean textAndImageCombind = feed.isLocalFeed()
                        && LocalFeedUpdater.getDefaultIconUrl(itemView.getContext()).equals(feed.getImageUrl());
                new CoverLoader(mainActivityRef.get())
                        .withUri(feed.getImageUrl())
                        .withPlaceholderView(feedTitle, textAndImageCombind)
                        .withCoverView(imageView)
                        .load();
            } else {
                new CoverLoader(mainActivityRef.get())
                        .withResource(R.drawable.ic_folder)
                        .withPlaceholderView(feedTitle, true)
                        .withCoverView(imageView)
                        .load();
            }
            itemView.setOnLongClickListener(v -> {
                if (drawerItem.type == NavDrawerData.DrawerItem.Type.FEED) {
                    selectedFeed = ((NavDrawerData.FeedDrawerItem) drawerItem).feed;
                } else{
                    selectedFeed = null;
                }
                return false;
            });
            itemView.setOnClickListener(v -> {
                if (drawerItem == null) {
                    return;
                }
                if (drawerItem.type == NavDrawerData.DrawerItem.Type.FEED) {
                    Fragment fragment = FeedItemlistFragment.newInstance(((NavDrawerData.FeedDrawerItem) drawerItem).feed.getId());
                    mainActivityRef.get().loadChildFragment(fragment);
                } else if (drawerItem.type == NavDrawerData.DrawerItem.Type.FOLDER) {
                    Fragment fragment = SubscriptionFragment.newInstance(drawerItem.getTitle());
                    mainActivityRef.get().loadChildFragment(fragment);
                }
            });
        }
    }
}
