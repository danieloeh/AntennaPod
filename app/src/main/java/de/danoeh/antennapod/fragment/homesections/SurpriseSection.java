package de.danoeh.antennapod.fragment.homesections;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.ArrayUtils;

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.CoverLoader;
import de.danoeh.antennapod.core.feed.util.ImageResourceUtils;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.NavDrawerData;
import de.danoeh.antennapod.core.util.DateUtils;
import de.danoeh.antennapod.core.util.FeedItemUtil;
import de.danoeh.antennapod.fragment.HomeFragment;
import de.danoeh.antennapod.fragment.ItemPagerFragment;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import kotlin.Unit;
import slush.AdapterAppliedResult;


public class SurpriseSection extends HomeSection<FeedItem> {

    public static final String TAG = "SurpriseSection";

    private AdapterAppliedResult<FeedItem> slush;

    public SurpriseSection(HomeFragment context) {
        super(context);
        sectionTitle = "Surprise";
        //sectionNavigateTitle = context.getString(R.string.subscriptions_label);
    }

    @Override
    protected View.OnClickListener navigate() {
        return null;
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

                    view.findViewById(R.id.play_icon).setVisibility(View.GONE);

                    view.setOnLongClickListener(v -> {
                        selectedItem = item;
                        context.setSelectedItem(item);
                        return false;
                    });
                    view.setOnCreateContextMenuListener(SurpriseSection.this);
                });

        super.addSectionTo(parent);
    }

    @NonNull
    @Override
    protected List<FeedItem> loadItems() {
        //TODO randomly not new/played
        return DBReader.getRecentlyPublishedEpisodes(6, 6, new FeedItemFilter(""), false);
    }

    @Override
    public void updateItems() {
        slush.getItemListEditor().changeAll(loadItems());
        super.updateItems();
    }
}
