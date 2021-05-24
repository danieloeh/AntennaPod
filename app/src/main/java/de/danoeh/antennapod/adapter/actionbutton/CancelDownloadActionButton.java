package de.danoeh.antennapod.adapter.actionbutton;

import android.content.Context;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import android.widget.Toast;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.net.downloadservice.DownloadWorker;

public class CancelDownloadActionButton extends ItemActionButton {

    public CancelDownloadActionButton(FeedItem item) {
        super(item);
    }

    @Override
    @StringRes
    public int getLabel() {
        return R.string.cancel_download_label;
    }

    @Override
    @DrawableRes
    public int getDrawable() {
        return R.drawable.ic_cancel;
    }

    @Override
    public void onClick(Context context) {
        FeedMedia media = item.getMedia();
        DownloadWorker.cancel(context, media.getDownload_url());
        if (UserPreferences.isEnableAutodownload()) {
            DBWriter.setFeedItemAutoDownload(media.getItem(), false);
            Toast.makeText(context, R.string.download_canceled_autodownload_enabled_msg, Toast.LENGTH_LONG).show();
        }
    }
}
