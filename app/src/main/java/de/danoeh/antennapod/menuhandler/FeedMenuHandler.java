package de.danoeh.antennapod.menuhandler;

import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import de.danoeh.antennapod.net.downloadservice.DownloadRequester;
import de.danoeh.antennapod.net.downloadservice.DownloadWorker;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.core.dialog.ConfirmationDialog;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.core.storage.DBTasks;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.net.downloadservice.DownloadRequestException;
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
        switch (item.getItemId()) {
            case R.id.refresh_item:
                DownloadWorker.enqueue(context, DownloadRequester.getInstance()
                        .createRequest(selectedFeed, false, true, true));
                break;
            case R.id.refresh_complete_item:
                DownloadWorker.enqueue(context, DownloadRequester.getInstance()
                        .createRequest(selectedFeed, true, true, true));
                break;
            case R.id.sort_items:
                showSortDialog(context, selectedFeed);
                break;
            case R.id.filter_items:
                showFilterDialog(context, selectedFeed);
                break;
            case R.id.mark_all_read_item:
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
                break;
            case R.id.visit_website_item:
                IntentUtils.openInBrowser(context, selectedFeed.getLink());
                break;
            case R.id.share_link_item:
                ShareUtils.shareFeedlink(context, selectedFeed);
                break;
            case R.id.share_download_url_item:
                ShareUtils.shareFeedDownloadLink(context, selectedFeed);
                break;
            default:
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
