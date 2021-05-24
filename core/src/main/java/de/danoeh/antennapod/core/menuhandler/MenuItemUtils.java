package de.danoeh.antennapod.core.menuhandler;

import android.view.Menu;
import android.view.MenuItem;

import de.danoeh.antennapod.core.R;

/**
 * Utilities for menu items
 */
public class MenuItemUtils {

    /**
     * Changes the appearance of a MenuItem depending on whether the given UpdateRefreshMenuItemChecker
     * is refreshing or not. If it returns true, the menu item will be replaced by an indeterminate progress
     * bar, otherwise the progress bar will be hidden.
     *
     * @param menu    The menu that the MenuItem belongs to
     * @param resId   The id of the MenuItem
     * @param checker Is used for checking whether to show the progress indicator or not.
     * @return The returned value of the UpdateRefreshMenuItemChecker's isRefreshing() method.
     */
    public static boolean updateRefreshMenuItem(Menu menu, int resId, UpdateRefreshMenuItemChecker checker) {
        updateRefreshMenuItem(menu, resId, checker.isRefreshing());
        return checker.isRefreshing();
    }

    public static void updateRefreshMenuItem(Menu menu, int resId, boolean isRefreshing) {
        MenuItem refreshItem = menu.findItem(resId);
        if (isRefreshing) {
            if (refreshItem.getActionView() == null) { // Don't flicker
                refreshItem.setActionView(R.layout.refresh_action_view);
            }
        } else {
            refreshItem.setActionView(null);
        }
    }

    public interface UpdateRefreshMenuItemChecker {
        boolean isRefreshing();
    }
}
