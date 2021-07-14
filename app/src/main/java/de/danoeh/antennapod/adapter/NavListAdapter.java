package de.danoeh.antennapod.adapter;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.widget.IconTextView;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.PreferenceActivity;
import de.danoeh.antennapod.fragment.InboxFragment;
import de.danoeh.antennapod.fragment.EpisodesFragment;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.core.glide.ApGlideSettings;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.storage.NavDrawerData;
import de.danoeh.antennapod.fragment.AddFeedFragment;
import de.danoeh.antennapod.fragment.DownloadsFragment;
import de.danoeh.antennapod.fragment.NavDrawerFragment;
import de.danoeh.antennapod.fragment.PlaybackHistoryFragment;
import de.danoeh.antennapod.fragment.QueueFragment;
import de.danoeh.antennapod.fragment.SubscriptionFragment;
import org.apache.commons.lang3.ArrayUtils;

import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * BaseAdapter for the navigation drawer
 */
public class NavListAdapter extends RecyclerView.Adapter<NavListAdapter.Holder>
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final int VIEW_TYPE_NAV = 0;
    public static final int VIEW_TYPE_SECTION_DIVIDER = 1;
    private static final int VIEW_TYPE_SUBSCRIPTION = 2;

    /**
     * a tag used as a placeholder to indicate if the subscription list should be displayed or not
     * This tag doesn't correspond to any specific activity.
     */
    public static final String SUBSCRIPTION_LIST_TAG = "SubscriptionList";

    private final List<String> fragmentTags = new ArrayList<>();
    private final String[] titles;
    private final ItemAccess itemAccess;
    private final WeakReference<Activity> activity;
    public boolean showSubscriptionList = true;

    public NavListAdapter(ItemAccess itemAccess, Activity context) {
        this.itemAccess = itemAccess;
        this.activity = new WeakReference<>(context);

        titles = context.getResources().getStringArray(R.array.nav_drawer_titles);
        loadItems();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (UserPreferences.PREF_HIDDEN_DRAWER_ITEMS.equals(key)) {
            loadItems();
        }
    }

    private void loadItems() {
        List<String> newTags = new ArrayList<>(Arrays.asList(NavDrawerFragment.NAV_DRAWER_TAGS));
        List<String> hiddenFragments = UserPreferences.getHiddenDrawerItems();
        newTags.removeAll(hiddenFragments);

        if (newTags.contains(SUBSCRIPTION_LIST_TAG)) {
            // we never want SUBSCRIPTION_LIST_TAG to be in 'tags'
            // since it doesn't actually correspond to a position in the list, but is
            // a placeholder that indicates if we should show the subscription list in the
            // nav drawer at all.
            showSubscriptionList = true;
            newTags.remove(SUBSCRIPTION_LIST_TAG);
        } else {
            showSubscriptionList = false;
        }

        fragmentTags.clear();
        fragmentTags.addAll(newTags);
        notifyDataSetChanged();
    }

    public String getLabel(String tag) {
        int index = ArrayUtils.indexOf(NavDrawerFragment.NAV_DRAWER_TAGS, tag);
        return titles[index];
    }

    private @DrawableRes int getDrawable(String tag) {
        switch (tag) {
            case InboxFragment.TAG:
                return R.drawable.ic_inbox;
            case QueueFragment.TAG:
                return R.drawable.ic_playlist;
            case EpisodesFragment.TAG:
                return R.drawable.ic_feed;
            case DownloadsFragment.TAG:
                return R.drawable.ic_download;
            case PlaybackHistoryFragment.TAG:
                return R.drawable.ic_history;
            case SubscriptionFragment.TAG:
                return R.drawable.ic_folder;
            case AddFeedFragment.TAG:
                return R.drawable.ic_add;
            default:
                return 0;
        }
    }

    public List<String> getFragmentTags() {
        return Collections.unmodifiableList(fragmentTags);
    }

    @Override
    public int getItemCount() {
        int baseCount = getSubscriptionOffset();
        if (showSubscriptionList) {
            baseCount += itemAccess.getCount();
        }
        return baseCount;
    }

    @Override
    public long getItemId(int position) {
        int viewType = getItemViewType(position);
        if (viewType == VIEW_TYPE_SUBSCRIPTION) {
            return itemAccess.getItem(position - getSubscriptionOffset()).id;
        } else if (viewType == VIEW_TYPE_NAV) {
            return -Math.abs((long) fragmentTags.get(position).hashCode()) - 1; // Folder IDs are >0
        } else {
            return 0;
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (0 <= position && position < fragmentTags.size()) {
            return VIEW_TYPE_NAV;
        } else if (position < getSubscriptionOffset()) {
            return VIEW_TYPE_SECTION_DIVIDER;
        } else {
            return VIEW_TYPE_SUBSCRIPTION;
        }
    }

    public int getSubscriptionOffset() {
        return fragmentTags.size() > 0 ? fragmentTags.size() + 1 : 0;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(activity.get());
        if (viewType == VIEW_TYPE_NAV) {
            return new NavHolder(inflater.inflate(R.layout.nav_listitem, parent, false));
        } else if (viewType == VIEW_TYPE_SECTION_DIVIDER) {
            return new DividerHolder(inflater.inflate(R.layout.nav_section_item, parent, false));
        } else {
            return new FeedHolder(inflater.inflate(R.layout.nav_listitem, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        int viewType = getItemViewType(position);

        holder.itemView.setOnCreateContextMenuListener(null);
        if (viewType == VIEW_TYPE_NAV) {
            bindNavView(getLabel(fragmentTags.get(position)), position, (NavHolder) holder);
        } else if (viewType == VIEW_TYPE_SECTION_DIVIDER) {
            bindSectionDivider((DividerHolder) holder);
        } else {
            int itemPos = position - getSubscriptionOffset();
            NavDrawerData.DrawerItem item = itemAccess.getItem(itemPos);
            bindListItem(item, (FeedHolder) holder);
            if (item.type == NavDrawerData.DrawerItem.Type.FEED) {
                bindFeedView((NavDrawerData.FeedDrawerItem) item, (FeedHolder) holder);
                holder.itemView.setOnCreateContextMenuListener(itemAccess);
            } else {
                bindFolderView((NavDrawerData.FolderDrawerItem) item, (FeedHolder) holder);
            }
        }
        if (viewType != VIEW_TYPE_SECTION_DIVIDER) {
            TypedValue typedValue = new TypedValue();

            activity.get().getTheme().resolveAttribute(itemAccess.isSelected(position)
                    ? R.attr.drawer_activated_color : android.R.attr.windowBackground, typedValue, true);
            holder.itemView.setBackgroundResource(typedValue.resourceId);

            holder.itemView.setOnClickListener(v -> itemAccess.onItemClick(position));
            holder.itemView.setOnLongClickListener(v -> itemAccess.onItemLongClick(position));
        }
    }

    private void bindNavView(String title, int position, NavHolder holder) {
        Activity context = activity.get();
        if (context == null) {
            return;
        }
        holder.title.setText(title);

        // reset for re-use
        holder.count.setVisibility(View.GONE);
        holder.count.setOnClickListener(null);

        String tag = fragmentTags.get(position);
        if (tag.equals(InboxFragment.TAG)) {
            int newItems = itemAccess.getNumberOfNewItems();
            if (newItems > 0) {
                holder.count.setText(NumberFormat.getInstance().format(newItems));
                holder.count.setVisibility(View.VISIBLE);
            }
        } else if (tag.equals(QueueFragment.TAG)) {
            int queueSize = itemAccess.getQueueSize();
            if (queueSize > 0) {
                holder.count.setText(NumberFormat.getInstance().format(queueSize));
                holder.count.setVisibility(View.VISIBLE);
            }
        /*} else if (tag.equals(PowerEpisodesFragment.TAG)) {
            int unreadItems = itemAccess.getNumberOfNewItems();
            if (unreadItems > 0) {
                holder.count.setText(NumberFormat.getInstance().format(unreadItems));
                holder.count.setVisibility(View.VISIBLE);
            }*/
        } else if (tag.equals(SubscriptionFragment.TAG)) {
            int sum = itemAccess.getFeedCounterSum();
            if (sum > 0) {
                holder.count.setText(NumberFormat.getInstance().format(sum));
                holder.count.setVisibility(View.VISIBLE);
            }
        } else if (tag.equals(DownloadsFragment.TAG) && UserPreferences.isEnableAutodownload()) {
            int epCacheSize = UserPreferences.getEpisodeCacheSize();
            // don't count episodes that can be reclaimed
            int spaceUsed = itemAccess.getNumberOfDownloadedItems()
                    - itemAccess.getReclaimableItems();

            if (epCacheSize > 0 && spaceUsed >= epCacheSize) {
                holder.count.setText("{md-disc-full 150%}");
                Iconify.addIcons(holder.count);
                holder.count.setVisibility(View.VISIBLE);
                holder.count.setOnClickListener(v ->
                        new AlertDialog.Builder(context)
                            .setTitle(R.string.episode_cache_full_title)
                            .setMessage(R.string.episode_cache_full_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .setNeutralButton(R.string.open_autodownload_settings, (dialog, which) -> {
                                Intent intent = new Intent(context, PreferenceActivity.class);
                                intent.putExtra(PreferenceActivity.OPEN_AUTO_DOWNLOAD_SETTINGS, true);
                                context.startActivity(intent);
                            })
                            .show()
                );
            }
        }

        holder.image.setImageResource(getDrawable(fragmentTags.get(position)));
    }

    private void bindSectionDivider(DividerHolder holder) {
        Activity context = activity.get();
        if (context == null) {
            return;
        }

        if (UserPreferences.getSubscriptionsFilter().isEnabled() && showSubscriptionList) {
            holder.itemView.setEnabled(true);
            holder.feedsFilteredMsg.setText("{md-info-outline} "
                    + context.getString(R.string.subscriptions_are_filtered));
            Iconify.addIcons(holder.feedsFilteredMsg);
            holder.feedsFilteredMsg.setVisibility(View.VISIBLE);
        } else {
            holder.itemView.setEnabled(false);
            holder.feedsFilteredMsg.setVisibility(View.GONE);
        }
    }

    private void bindListItem(NavDrawerData.DrawerItem item, FeedHolder holder) {
        if (item.getCounter() > 0) {
            holder.count.setVisibility(View.VISIBLE);
            holder.count.setText(NumberFormat.getInstance().format(item.getCounter()));
        } else {
            holder.count.setVisibility(View.GONE);
        }
        holder.title.setText(item.getTitle());
        int padding = (int) (activity.get().getResources().getDimension(R.dimen.thumbnail_length_navlist) / 2);
        holder.itemView.setPadding(item.getLayer() * padding, 0, 0, 0);
    }

    private void bindFeedView(NavDrawerData.FeedDrawerItem drawerItem, FeedHolder holder) {
        Feed feed = drawerItem.feed;
        Activity context = activity.get();
        if (context == null) {
            return;
        }

        Glide.with(context)
                .load(feed.getImageUrl())
                .apply(new RequestOptions()
                    .placeholder(R.color.light_gray)
                    .error(R.color.light_gray)
                    .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                    .fitCenter()
                    .dontAnimate())
                .into(holder.image);

        if (feed.hasLastUpdateFailed()) {
            RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) holder.title.getLayoutParams();
            p.addRule(RelativeLayout.LEFT_OF, R.id.itxtvFailure);
            holder.failure.setVisibility(View.VISIBLE);
        } else {
            RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) holder.title.getLayoutParams();
            p.addRule(RelativeLayout.LEFT_OF, R.id.txtvCount);
            holder.failure.setVisibility(View.GONE);
        }
    }

    private void bindFolderView(NavDrawerData.FolderDrawerItem folder, FeedHolder holder) {
        Activity context = activity.get();
        if (context == null) {
            return;
        }
        if (folder.isOpen) {
            holder.count.setVisibility(View.GONE);
        }
        Glide.with(context).clear(holder.image);
        holder.image.setImageResource(R.drawable.ic_folder);
        holder.failure.setVisibility(View.GONE);
    }

    static class Holder extends RecyclerView.ViewHolder {
        public Holder(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class DividerHolder extends Holder {
        final TextView feedsFilteredMsg;

        public DividerHolder(@NonNull View itemView) {
            super(itemView);
            feedsFilteredMsg = itemView.findViewById(R.id.nav_feeds_filtered_message);
        }
    }

    static class NavHolder extends Holder {
        final ImageView image;
        final TextView title;
        final TextView count;

        public NavHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imgvCover);
            title = itemView.findViewById(R.id.txtvTitle);
            count = itemView.findViewById(R.id.txtvCount);
        }
    }

    static class FeedHolder extends Holder {
        final ImageView image;
        final TextView title;
        final IconTextView failure;
        final TextView count;

        public FeedHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imgvCover);
            title = itemView.findViewById(R.id.txtvTitle);
            failure = itemView.findViewById(R.id.itxtvFailure);
            count = itemView.findViewById(R.id.txtvCount);
        }
    }

    public interface ItemAccess extends View.OnCreateContextMenuListener {
        int getCount();

        NavDrawerData.DrawerItem getItem(int position);

        boolean isSelected(int position);

        int getQueueSize();

        int getNumberOfNewItems();

        int getNumberOfDownloadedItems();

        int getReclaimableItems();

        int getFeedCounterSum();

        void onItemClick(int position);

        boolean onItemLongClick(int position);

        @Override
        void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo);
    }

}
