package de.danoeh.antennapod.fragment.swipeactions;

import android.content.Context;

import androidx.fragment.app.Fragment;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.menuhandler.FeedItemMenuHandler;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;

public class MarkUnplayedSwipeAction implements SwipeAction {

    @Override
    public int actionIcon() {
        return R.drawable.ic_check;
    }

    @Override
    public int actionColor() {
        return R.color.swipe_blue;
    }

    @Override
    public String title(Context context) {
        return context.getString(R.string.remove_new_flag_label);
    }

    @Override
    public void action(FeedItem item, Fragment fragment, FeedItemFilter filter) {
        FeedItemMenuHandler.markReadWithUndo(fragment,
                item, FeedItem.UNPLAYED, willRemove(filter));
    }

    @Override
    public boolean willRemove(FeedItemFilter filter) {
        return filter.showUnplayed;
    }
}
