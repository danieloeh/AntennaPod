package de.danoeh.antennapod.fragment.homesections;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.CoverLoader;
import de.danoeh.antennapod.core.feed.util.ImageResourceUtils;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.util.DateUtils;
import de.danoeh.antennapod.core.util.playback.PlaybackServiceStarter;
import de.danoeh.antennapod.fragment.EpisodesFragment;
import de.danoeh.antennapod.fragment.EpisodesListFragment;
import de.danoeh.antennapod.fragment.HomeFragment;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import kotlin.Unit;
import slush.AdapterAppliedResult;


public class SurpriseSection extends HomeSection<FeedItem> {

    public static final String TAG = "SurpriseSection";

    private AdapterAppliedResult<FeedItem> slush;

    public SurpriseSection(HomeFragment context) {
        super(context);
        sectionTitle = context.getString(R.string.surprise_title);
        sectionNavigateTitle = context.getString(R.string.episodes_label);
    }

    @Override
    protected View.OnClickListener navigate() {
        return view -> {
            Bundle b = new Bundle();
            b.putInt(EpisodesFragment.PREF_FILTER, EpisodesListFragment.QUICKFILTER_NEW);
            ((MainActivity) context.requireActivity()).loadFragment(EpisodesFragment.TAG, b);
        };
    }

    @Override
    protected Unit onItemClick(View view, FeedItem feedItem) {
        new PlaybackServiceStarter(context.requireContext(), feedItem.getMedia())
                .callEvenIfRunning(true)
                .startWhenPrepared(true)
                .shouldStream(!feedItem.isDownloaded())
                .start();
        slush.getItemListEditor().removeItem(feedItem);
        slush.getItemListEditor().addItem(loadItems().get(0));
        return null;
    }

    @Override
    public void addSectionTo(LinearLayout parent) {
        slush = easySlush(R.layout.cover_play_title_item, (view, item) -> {
            ImageView coverPlay = view.findViewById(R.id.cover_play);
            TextView title = view.findViewById(R.id.playTitle);
            TextView date = view.findViewById(R.id.playDate);
            new CoverLoader((MainActivity) context.requireActivity())
                    .withUri(ImageResourceUtils.getEpisodeListImageLocation(item))
                    .withFallbackUri(item.getFeed().getImageUrl())
                    .withCoverView(coverPlay)
                    .load();
            title.setText(item.getTitle());
            date.setText(DateUtils.formatAbbrev(context.requireContext(), item.getPubDate()));

            view.setOnLongClickListener(v -> {
                selectedItem = item;
                context.setSelectedItem(item);
                return false;
            });
            view.setOnCreateContextMenuListener(SurpriseSection.this);
        });

        ImageButton shuffle = section.findViewById(R.id.shuffleButton);
        shuffle.setVisibility(View.VISIBLE);
        shuffle.setOnClickListener(view -> slush.getItemListEditor().changeAll(loadItems()));

        super.addSectionTo(parent);
    }

    @NonNull
    @Override
    protected List<FeedItem> loadItems() {
        return DBReader.getRecentlyPublishedEpisodes(0, 6, new FeedItemFilter("not_queued,unplayed"), true);
    }

}
