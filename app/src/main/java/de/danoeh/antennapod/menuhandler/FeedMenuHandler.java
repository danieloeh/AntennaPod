package de.danoeh.antennapod.menuhandler;

import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.StringUtils;

import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.core.dialog.ConfirmationDialog;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.core.storage.DBTasks;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.storage.DownloadRequestException;
import de.danoeh.antennapod.core.util.IntentUtils;
import de.danoeh.antennapod.core.util.ShareUtils;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.dialog.FilterDialog;
import de.danoeh.antennapod.dialog.IntraFeedSortDialog;

/**
 * Handles interactions with the FeedItemMenu.
 */
public class FeedMenuHandler {

    private FeedMenuHandler(){ }

    private static final String TAG = "FeedMenuHandler";

    public static boolean onPrepareOptionsMenu(Menu menu, Feed selectedFeed) {
        if (selectedFeed == null) {
            return true;
        }

        Log.d(TAG, "Preparing options menu");

        menu.findItem(R.id.refresh_complete_item).setVisible(selectedFeed.isPaged());
        if (StringUtils.isBlank(selectedFeed.getLink())) {
            menu.findItem(R.id.visit_website_item).setVisible(false);
            menu.findItem(R.id.share_link_item).setVisible(false);
        }
        if (selectedFeed.isLocalFeed()) {
            // hide complete submenu "Share..." as both sub menu items are not visible
            menu.findItem(R.id.share_item).setVisible(false);
        }

        return true;
    }

    /**
     * NOTE: This method does not handle clicks on the 'remove feed' - item.
     *
     * @throws DownloadRequestException
     */
    public static boolean onOptionsItemClicked(final Context context, final MenuItem item,
                                               final Feed selectedFeed) throws DownloadRequestException {
        final int itemId = item.getItemId();
        if (itemId == R.id.refresh_item) {
            DBTasks.forceRefreshFeed(context, selectedFeed, true);
        } else if (itemId == R.id.refresh_complete_item) {
            DBTasks.forceRefreshCompleteFeed(context, selectedFeed);
        } else if (itemId == R.id.sort_items) {
            showSortDialog(context, selectedFeed);
        } else if (itemId == R.id.filter_items) {
            showFilterDialog(context, selectedFeed);
        } else if (itemId == R.id.mark_all_read_item) {
            ConfirmationDialog conDialog = new ConfirmationDialog(context,
                    R.string.mark_all_read_label,
                    R.string.mark_all_read_feed_confirmation_msg) {

                @Override
                public void onConfirmButtonPressed(
                        DialogInterface dialog) {
                    dialog.dismiss();
                    DBWriter.markFeedRead(selectedFeed.getId());
                }
            };
            conDialog.createNewDialog().show();
        } else if (itemId == R.id.visit_website_item) {
            IntentUtils.openInBrowser(context, selectedFeed.getLink());
        } else if (itemId == R.id.share_link_item) {
            ShareUtils.shareFeedlink(context, selectedFeed);
        } else if (itemId == R.id.share_download_url_item) {
            ShareUtils.shareFeedDownloadLink(context, selectedFeed);
        } else {
            return false;
        }
        return true;
    }

    private static void showFilterDialog(Context context, Feed selectedFeed) {
        FilterDialog filterDialog = new FilterDialog(context, selectedFeed.getItemFilter()) {
            @Override
            protected void updateFilter(Set<String> filterValues) {
                selectedFeed.setItemFilter(filterValues.toArray(new String[0]));
                DBWriter.setFeedItemsFilter(selectedFeed.getId(), filterValues);
            }
        };

        filterDialog.openDialog();
    }


    private static void showSortDialog(Context context, Feed selectedFeed) {
        IntraFeedSortDialog sortDialog = new IntraFeedSortDialog(context, selectedFeed.getSortOrder()) {
            @Override
            protected void updateSort(@NonNull SortOrder sortOrder) {
                selectedFeed.setSortOrder(sortOrder);
                DBWriter.setFeedItemSortOrder(selectedFeed.getId(), sortOrder);
            }
        };
        sortDialog.openDialog();
    }

}
