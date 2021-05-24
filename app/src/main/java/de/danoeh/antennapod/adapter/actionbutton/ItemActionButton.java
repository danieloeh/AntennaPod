package de.danoeh.antennapod.adapter.actionbutton;

import android.content.Context;
import android.widget.ImageView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import android.view.View;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.net.downloadservice.DownloadRequester;
import de.danoeh.antennapod.core.util.FeedItemUtil;

public abstract class ItemActionButton {
    FeedItem item;

    ItemActionButton(FeedItem item) {
        this.item = item;
    }

    @StringRes
    public abstract int getLabel();

    @DrawableRes
    public abstract int getDrawable();

    public abstract void onClick(Context context);

    public int getVisibility() {
        return View.VISIBLE;
    }

    @NonNull
    public static ItemActionButton forItem(@NonNull FeedItem item, boolean isInQueue, boolean allowStream) {
        final FeedMedia media = item.getMedia();
        if (media == null) {
            return new MarkAsPlayedActionButton(item);
        }

        final boolean isDownloadingMedia = DownloadRequester.getInstance().isDownloadingFile(media);
        if (FeedItemUtil.isCurrentlyPlaying(media)) {
            return new PauseActionButton(item);
        } else if (item.getFeed().isLocalFeed()) {
            return new PlayLocalActionButton(item);
        } else if (media.isDownloaded()) {
            return new PlayActionButton(item);
        } else if (isDownloadingMedia) {
            return new CancelDownloadActionButton(item);
        } else if (UserPreferences.isStreamOverDownload() && allowStream) {
            return new StreamActionButton(item);
        } else if (MobileDownloadHelper.userAllowedMobileDownloads()
                || !MobileDownloadHelper.userChoseAddToQueue() || isInQueue) {
            return new DownloadActionButton(item, isInQueue);
        } else {
            return new AddToQueueActionButton(item);
        }
    }

    public void configure(@NonNull View button, @NonNull ImageView icon, Context context) {
        button.setVisibility(getVisibility());
        button.setContentDescription(context.getString(getLabel()));
        button.setOnClickListener((view) -> onClick(context));
        icon.setImageResource(getDrawable());
    }
}
