package de.danoeh.antennapod.adapter;

import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
public class SubscriptionsRecyclerAdapter extends SelectableAdapter<SubscriptionsRecyclerAdapter.SubscriptionViewHolder> implements View.OnCreateContextMenuListener
{
    /** the position in the view that holds the add item; 0 is the first, -1 is the last position */
    private static final String TAG = "SubscriptionsRecyclerAdapter";

    private final WeakReference<MainActivity> mainActivityRef;
    private final ItemAccess itemAccess;
    private Feed selectedFeed = null;
    int longPressedPosition = 0; // used to init actionMode

    public SubscriptionsRecyclerAdapter(MainActivity mainActivity, ItemAccess itemAccess) {
        super(mainActivity);
        this.mainActivityRef = new WeakReference<>(mainActivity);
        this.itemAccess = itemAccess;
    }

    public Object getItem(int position) {
        return itemAccess.getItem(position);
    }

    public Feed getSelectedFeed() {
        return selectedFeed;
    }

    @NonNull
    @Override
    public SubscriptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(mainActivityRef.get()).inflate(R.layout.subscription_item, parent, false);
        SubscriptionViewHolder viewHolder = new SubscriptionViewHolder(itemView);

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull SubscriptionViewHolder holder, int position) {
        holder.onBind(itemAccess.getItem(position), position);
        holder.itemView.setOnCreateContextMenuListener(this);

    }

    @Override
    public int getItemCount() {
        return itemAccess.getCount();
    }

    @Override
    public long getItemId(int position) {
        NavDrawerData.DrawerItem drawerItem = itemAccess.getItem(position);
        if (drawerItem != null && drawerItem.type == NavDrawerData.DrawerItem.Type.FEED) {
            return drawerItem.id;
        } else {
            return RecyclerView.NO_ID;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if(selectedFeed != null && !inActionMode()) {
            MenuInflater inflater = mainActivityRef.get().getMenuInflater();
            inflater.inflate(R.menu.nav_feed_context, menu);
            menu.setHeaderTitle(selectedFeed.getTitle());
        }
    }

    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.multi_select) {
            startSelectMode(longPressedPosition);
            return true;
        } /*else if (item.getItemId() == R.id.select_all_above) {
            setSelected(0, longPressedPosition, true);
            return true;
        } else if (item.getItemId() == R.id.select_all_below) {
            setSelected(longPressedPosition + 1, getItemCount(), true);
            return true;
        }*/
        return false;
    }

    public interface ItemAccess {
        int getCount();

        NavDrawerData.DrawerItem getItem(int position);
    }
    public class SubscriptionViewHolder extends RecyclerView.ViewHolder {
        private TextView feedTitle;
        private ImageView imageView;
        private TriangleLabelView count;
        private CheckBox selectCheckbox;
        public SubscriptionViewHolder(@NonNull View itemView) {
            super(itemView);
            feedTitle = itemView.findViewById(R.id.txtvTitle);
            imageView = itemView.findViewById(R.id.imgvCover);
            count = itemView.findViewById(R.id.triangleCountView);
            selectCheckbox = itemView.findViewById(R.id.selectCheckBox);
        }

        public void onBind(NavDrawerData.DrawerItem drawerItem, int position) {
            feedTitle.setText(drawerItem.getTitle());
            imageView.setContentDescription(drawerItem.getTitle());
            feedTitle.setVisibility(View.VISIBLE);

            if (TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault())
                    == ViewCompat.LAYOUT_DIRECTION_RTL) {
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

        if (inActionMode()) {
            selectCheckbox.setVisibility(View.VISIBLE);
            if (isSelected(position)) {
                selectCheckbox.setChecked(true);
            } else {
                selectCheckbox.setChecked(false);
            }
            selectCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setSelected(position, isChecked);
                }
            });
        } else {
            selectCheckbox.setVisibility(View.GONE);
        }

        itemView.setOnLongClickListener(v -> {
            if(!inActionMode())
                if (drawerItem.type == NavDrawerData.DrawerItem.Type.FEED) {
                    selectedFeed = ((NavDrawerData.FeedDrawerItem) drawerItem).feed;
                    longPressedPosition = position;
                } else{
                    selectedFeed = null;
                }
            return false;
        });
        itemView.setOnClickListener(v -> {
            if (drawerItem == null) {
                return;
            } else if (inActionMode()) {
                selectCheckbox.setChecked(!isSelected(position));
                return;
            } else if (drawerItem.type == NavDrawerData.DrawerItem.Type.FEED) {
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
