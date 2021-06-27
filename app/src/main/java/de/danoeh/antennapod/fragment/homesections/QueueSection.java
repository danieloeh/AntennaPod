package de.danoeh.antennapod.fragment.homesections;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.CoverLoader;
import de.danoeh.antennapod.core.feed.util.ImageResourceUtils;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.util.DateUtils;
import de.danoeh.antennapod.core.util.FeedItemUtil;
import de.danoeh.antennapod.core.util.IntentUtils;
import de.danoeh.antennapod.core.util.playback.PlaybackServiceStarter;
import de.danoeh.antennapod.fragment.HomeFragment;
import de.danoeh.antennapod.fragment.QueueFragment;
import de.danoeh.antennapod.model.feed.FeedItem;
import kotlin.Unit;
import slush.AdapterAppliedResult;

import static de.danoeh.antennapod.core.service.playback.PlaybackService.ACTION_PAUSE_PLAY_CURRENT_EPISODE;


public class QueueSection extends HomeSection<FeedItem> {

    public static final String TAG = "QueueSection";

    private AdapterAppliedResult<FeedItem> slush;

    public QueueSection(HomeFragment context) {
        super(context);
        sectionTitle = context.getString(R.string.continue_title);
        sectionNavigateTitle = context.getString(R.string.queue_label);
        updateEvents = Arrays.asList(UpdateEvents.FEED_ITEM, UpdateEvents.QUEUE);
    }

    @NonNull
    @Override
    protected View.OnClickListener navigate() {
        return view -> {
            ((MainActivity) context.requireActivity()).loadFragment(QueueFragment.TAG, null);
        };
    }

    @Override
    protected Unit onItemClick(View view, FeedItem feedItem) {
        boolean isPlaying = FeedItemUtil.isCurrentlyPlaying(feedItem.getMedia());
        if (isPlaying) {
            IntentUtils.sendLocalBroadcast(context.requireContext(), ACTION_PAUSE_PLAY_CURRENT_EPISODE);
        } else {
            new PlaybackServiceStarter(context.requireContext(), feedItem.getMedia())
                    .callEvenIfRunning(true)
                    .startWhenPrepared(true)
                    .shouldStream(!feedItem.isDownloaded())
                    .start();
        }
        playPauseIcon(view.findViewById(R.id.play_icon), !isPlaying);
        return null;
    }

    @Override
    public void addSectionTo(LinearLayout parent) {
        slush = easySlush(R.layout.cover_play_title_item, (view, item) -> {
            final ImageView coverPlay = view.findViewById(R.id.cover_play);
            final TextView title = view.findViewById(R.id.playTitle);
            final TextView date = view.findViewById(R.id.playDate);
            playPauseIcon(view.findViewById(R.id.play_icon),
                    FeedItemUtil.isCurrentlyPlaying(item.getMedia()));
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
            view.setOnCreateContextMenuListener(QueueSection.this);
        });

        super.addSectionTo(parent);
    }

    private void playPauseIcon(ImageView icon, boolean isPlaying) {
        icon.setImageResource(isPlaying ? R.drawable.ic_pause_circle : R.drawable.ic_play_circle);
    }

    @NonNull
    @Override
    protected List<FeedItem> loadItems() {
        return DBReader.getPausedQueue(5);
    }

    @Override
    public void updateItems(UpdateEvents event) {
        slush.getItemListEditor().changeAll(loadItems());
        super.updateItems(event);
    }


}
