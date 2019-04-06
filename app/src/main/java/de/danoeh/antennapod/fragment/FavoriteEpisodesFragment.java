package de.danoeh.antennapod.fragment;

import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.adapter.AllEpisodesRecycleAdapter;
import de.danoeh.antennapod.core.event.FavoritesEvent;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DBWriter;


/**
 * Like 'EpisodesFragment' except that it only shows favorite episodes and
 * supports swiping to remove from favorites.
 */

public class FavoriteEpisodesFragment extends AllEpisodesFragment {

    private static final String TAG = "FavoriteEpisodesFrag";

    private static final String PREF_NAME = "PrefFavoriteEpisodesFragment";

    @Override
    protected boolean showOnlyNewEpisodes() { return true; }

    @Override
    protected String getPrefName() { return PREF_NAME; }

    public void onEvent(FavoritesEvent event) {
        Log.d(TAG, "onEvent() called with: " + "event = [" + event + "]");
        loadItems();
    }

    @Override
    protected void resetViewState() {
        super.resetViewState();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateViewHelper(inflater, container, savedInstanceState,
                R.layout.all_episodes_fragment);
        ((TextView)root.findViewById(R.id.emptyViewTitle)).setText(R.string.no_fav_episodes_head_label);
        ((TextView)root.findViewById(R.id.emptyViewMessage)).setText(R.string.no_fav_episodes_label);

        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
                AllEpisodesRecycleAdapter.Holder holder = (AllEpisodesRecycleAdapter.Holder)viewHolder;
                Log.d(TAG, "remove(" + holder.getItemId() + ")");

                if (disposable != null) {
                    disposable.dispose();
                }
                FeedItem item = holder.getFeedItem();
                if (item != null) {
                    DBWriter.removeFavoriteItem(item);

                    Snackbar snackbar = Snackbar.make(root, getString(R.string.removed_item),
                            Snackbar.LENGTH_LONG);
                    snackbar.setAction(getString(R.string.undo), v -> DBWriter.addFavoriteItem(item));
                    snackbar.show();
                }
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
        return root;
    }

    @Override
    protected List<FeedItem> loadData() {
        return DBReader.getFavoriteItemsList();
    }
}
