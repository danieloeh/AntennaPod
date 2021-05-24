package de.danoeh.antennapod.view.viewholder;

import android.os.Build;
import android.text.Layout;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.joanzapata.iconify.Iconify;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.CoverLoader;
import de.danoeh.antennapod.adapter.actionbutton.ItemActionButton;
import de.danoeh.antennapod.core.event.PlaybackPositionEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.core.feed.util.ImageResourceUtils;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.playback.PlaybackService;
import de.danoeh.antennapod.net.downloadservice.DownloadRequester;
import de.danoeh.antennapod.core.util.Converter;
import de.danoeh.antennapod.core.util.DateUtils;
import de.danoeh.antennapod.core.util.FeedItemUtil;
import de.danoeh.antennapod.core.util.NetworkUtils;
import de.danoeh.antennapod.ui.common.ThemeUtils;
import de.danoeh.antennapod.ui.common.CircularProgressBar;

/**
 * Holds the view which shows FeedItems.
 */
public class EpisodeItemViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = "EpisodeItemViewHolder";

    private final View container;
    public final ImageView dragHandle;
    private final TextView placeholder;
    private final ImageView cover;
    private final TextView title;
    private final TextView pubDate;
    private final TextView position;
    private final TextView duration;
    private final TextView size;
    public final TextView isNew;
    public final ImageView isInQueue;
    private final ImageView isVideo;
    public final ImageView isFavorite;
    private final ProgressBar progressBar;
    public final View secondaryActionButton;
    public final ImageView secondaryActionIcon;
    private final CircularProgressBar secondaryActionProgress;
    private final TextView separatorIcons;
    private final View leftPadding;
    public final CardView coverHolder;

    private final MainActivity activity;
    private FeedItem item;

    public EpisodeItemViewHolder(MainActivity activity, ViewGroup parent) {
        super(LayoutInflater.from(activity).inflate(R.layout.feeditemlist_item, parent, false));
        this.activity = activity;
        container = itemView.findViewById(R.id.container);
        dragHandle = itemView.findViewById(R.id.drag_handle);
        placeholder = itemView.findViewById(R.id.txtvPlaceholder);
        cover = itemView.findViewById(R.id.imgvCover);
        title = itemView.findViewById(R.id.txtvTitle);
        if (Build.VERSION.SDK_INT >= 23) {
            title.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL);
        }
        pubDate = itemView.findViewById(R.id.txtvPubDate);
        position = itemView.findViewById(R.id.txtvPosition);
        duration = itemView.findViewById(R.id.txtvDuration);
        progressBar = itemView.findViewById(R.id.progressBar);
        isInQueue = itemView.findViewById(R.id.ivInPlaylist);
        isVideo = itemView.findViewById(R.id.ivIsVideo);
        isNew = itemView.findViewById(R.id.statusUnread);
        isFavorite = itemView.findViewById(R.id.isFavorite);
        size = itemView.findViewById(R.id.size);
        separatorIcons = itemView.findViewById(R.id.separatorIcons);
        secondaryActionProgress = itemView.findViewById(R.id.secondaryActionProgress);
        secondaryActionButton = itemView.findViewById(R.id.secondaryActionButton);
        secondaryActionIcon = itemView.findViewById(R.id.secondaryActionIcon);
        coverHolder = itemView.findViewById(R.id.coverHolder);
        leftPadding = itemView.findViewById(R.id.left_padding);
        itemView.setTag(this);
    }

    public void bind(FeedItem item) {
        this.item = item;
        placeholder.setText(item.getFeed().getTitle());
        title.setText(item.getTitle());
        leftPadding.setContentDescription(item.getTitle());
        pubDate.setText(DateUtils.formatAbbrev(activity, item.getPubDate()));
        pubDate.setContentDescription(DateUtils.formatForAccessibility(activity, item.getPubDate()));
        isNew.setVisibility(item.isNew() ? View.VISIBLE : View.GONE);
        isFavorite.setVisibility(item.isTagged(FeedItem.TAG_FAVORITE) ? View.VISIBLE : View.GONE);
        isInQueue.setVisibility(item.isTagged(FeedItem.TAG_QUEUE) ? View.VISIBLE : View.GONE);
        container.setAlpha(item.isPlayed() ? 0.5f : 1.0f);

        ItemActionButton actionButton = ItemActionButton.forItem(item, true, true);
        actionButton.configure(secondaryActionButton, secondaryActionIcon, activity);
        secondaryActionButton.setFocusable(false);

        if (item.getMedia() != null) {
            bind(item.getMedia());
        } else {
            secondaryActionProgress.setPercentage(0, item);
            isVideo.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            duration.setVisibility(View.GONE);
            position.setVisibility(View.GONE);
            itemView.setBackgroundResource(ThemeUtils.getDrawableFromAttr(activity, R.attr.selectableItemBackground));
        }

        if (coverHolder.getVisibility() == View.VISIBLE) {
            new CoverLoader(activity)
                    .withUri(ImageResourceUtils.getEpisodeListImageLocation(item))
                    .withFallbackUri(item.getFeed().getImageUrl())
                    .withPlaceholderView(placeholder)
                    .withCoverView(cover)
                    .load();
        }
    }

    private void bind(FeedMedia media) {
        isVideo.setVisibility(media.getMediaType() == MediaType.VIDEO ? View.VISIBLE : View.GONE);
        duration.setVisibility(media.getDuration() > 0 ? View.VISIBLE : View.GONE);

        if (FeedItemUtil.isCurrentlyPlaying(media)) {
            itemView.setBackgroundColor(ThemeUtils.getColorFromAttr(activity, R.attr.currently_playing_background));
        } else {
            itemView.setBackgroundResource(ThemeUtils.getDrawableFromAttr(activity, R.attr.selectableItemBackground));
        }

        if (DownloadRequester.getInstance().isDownloadingFile(media)) {
            //final DownloadRequest downloadRequest = DownloadRequester.getInstance().getRequestFor(media);
            //float percent = 0.01f * downloadRequest.getProgressPercent();
            //secondaryActionProgress.setPercentage(Math.max(percent, 0.01f), item);
        } else if (media.isDownloaded()) {
            secondaryActionProgress.setPercentage(1, item); // Do not animate 100% -> 0%
        } else {
            secondaryActionProgress.setPercentage(0, item); // Animate X% -> 0%
        }

        duration.setText(Converter.getDurationStringLong(media.getDuration()));
        duration.setContentDescription(activity.getString(R.string.chapter_duration,
                Converter.getDurationStringLocalized(activity, media.getDuration())));
        if (FeedItemUtil.isPlaying(item.getMedia()) || item.isInProgress()) {
            int progress = (int) (100.0 * media.getPosition() / media.getDuration());
            int remainingTime = Math.max(media.getDuration() - media.getPosition(), 0);
            progressBar.setProgress(progress);
            position.setText(Converter.getDurationStringLong(media.getPosition()));
            position.setContentDescription(activity.getString(R.string.position,
                    Converter.getDurationStringLocalized(activity, media.getPosition())));
            progressBar.setVisibility(View.VISIBLE);
            position.setVisibility(View.VISIBLE);
            if (UserPreferences.shouldShowRemainingTime()) {
                duration.setText(((remainingTime > 0) ? "-" : "") + Converter.getDurationStringLong(remainingTime));
                duration.setContentDescription(activity.getString(R.string.chapter_duration,
                        Converter.getDurationStringLocalized(activity, (media.getDuration() - media.getPosition()))));
            }
        } else {
            progressBar.setVisibility(View.GONE);
            position.setVisibility(View.GONE);
        }

        if (media.getSize() > 0) {
            size.setText(Formatter.formatShortFileSize(activity, media.getSize()));
        } else if (NetworkUtils.isEpisodeHeadDownloadAllowed() && !media.checkedOnSizeButUnknown()) {
            size.setText("{fa-spinner}");
            Iconify.addIcons(size);
            NetworkUtils.getFeedMediaSizeObservable(media).subscribe(
                    sizeValue -> {
                        if (sizeValue > 0) {
                            size.setText(Formatter.formatShortFileSize(activity, sizeValue));
                        } else {
                            size.setText("");
                        }
                    }, error -> {
                        size.setText("");
                        Log.e(TAG, Log.getStackTraceString(error));
                    });
        } else {
            size.setText("");
        }
    }

    private void updateDuration(PlaybackPositionEvent event) {
        int currentPosition = event.getPosition();
        int timeDuration = event.getDuration();
        int remainingTime = Math.max(timeDuration - currentPosition, 0);
        Log.d(TAG, "currentPosition " + Converter.getDurationStringLong(currentPosition));
        if (currentPosition == PlaybackService.INVALID_TIME || timeDuration == PlaybackService.INVALID_TIME) {
            Log.w(TAG, "Could not react to position observer update because of invalid time");
            return;
        }
        if (UserPreferences.shouldShowRemainingTime()) {
            duration.setText(((remainingTime > 0) ? "-" : "") + Converter.getDurationStringLong(remainingTime));
        } else {
            duration.setText(Converter.getDurationStringLong(timeDuration));
        }
    }

    public FeedItem getFeedItem() {
        return item;
    }

    public boolean isCurrentlyPlayingItem() {
        return item.getMedia() != null && FeedItemUtil.isCurrentlyPlaying(item.getMedia());
    }

    public void notifyPlaybackPositionUpdated(PlaybackPositionEvent event) {
        progressBar.setProgress((int) (100.0 * event.getPosition() / event.getDuration()));
        position.setText(Converter.getDurationStringLong(event.getPosition()));
        updateDuration(event);
        duration.setVisibility(View.VISIBLE); // Even if the duration was previously unknown, it is now known
    }

    /**
     * Hides the separator dot between icons and text if there are no icons.
     */
    public void hideSeparatorIfNecessary() {
        boolean hasIcons = isNew.getVisibility() == View.VISIBLE
                || isInQueue.getVisibility() == View.VISIBLE
                || isVideo.getVisibility() == View.VISIBLE
                || isFavorite.getVisibility() == View.VISIBLE
                || isNew.getVisibility() == View.VISIBLE;
        separatorIcons.setVisibility(hasIcons ? View.VISIBLE : View.GONE);
    }
}
