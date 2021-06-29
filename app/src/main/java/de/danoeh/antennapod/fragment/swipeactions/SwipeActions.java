package de.danoeh.antennapod.fragment.swipeactions;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.dialog.SwipeActionsDialog;
import de.danoeh.antennapod.fragment.EpisodesFragment;
import de.danoeh.antennapod.fragment.QueueFragment;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.ui.common.ThemeUtils;
import de.danoeh.antennapod.view.viewholder.EpisodeItemViewHolder;
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator;

public class SwipeActions {
    public static final String PREF_NAME = "SwipeActionsPrefs";
    public static final String PREF_SWIPEACTION_RIGHT = "PrefsSwipeActionRight";
    public static final String PREF_SWIPEACTION_LEFT = "PrefsSwipeActionLeft";

    //indexes of swipeActions
    public static final int ADD_TO_QUEUE = 0;
    public static final int MARK_UNPLAYED = 1;
    public static final int START_DOWNLOAD = 2;
    public static final int MARK_FAV = 3;
    public static final int MARK_PLAYED = 4;
    public static final int REMOVE_FROM_QUEUE = 5;

    public static final List<SwipeAction> swipeActions = Collections.unmodifiableList(
            Arrays.asList(new AddToQueueSwipeAction(), new MarkUnplayedSwipeAction(),
                    new StartDownloadSwipeAction(), new MarkFavouriteSwipeAction(),
                    new MarkPlayedSwipeAction(), new RemoveFromQueueSwipeAction())
    );

    private RecyclerView recyclerView;
    public ItemTouchHelper itemTouchHelper;
    private final Fragment fragment;
    private final String tag;
    private FeedItemFilter filter = null;

    private NewSwipeCallback newSwipeCallback = SimpleSwipeCallback::new;

    public SwipeActions(Fragment fragment, String tag) {
        this.fragment = fragment;
        this.tag = tag;
        itemTouchHelper();
    }

    public void setFilter(FeedItemFilter filter) {
        this.filter = filter;
    }

    public SwipeActions attachTo(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
        itemTouchHelper.attachToRecyclerView(recyclerView);
        //reload as settings might have changed
        fragment.getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event == Lifecycle.Event.ON_RESUME) {
                resetItemTouchHelper();
            }
        });
        return this;
    }

    public void itemTouchHelper() {
        itemTouchHelper = new ItemTouchHelper(newSwipeCallback.construct());
    }

    private static int[] getPrefs(Context context, String tag, int[] defaultActions) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int prefRight = prefs.getInt(PREF_SWIPEACTION_RIGHT + tag, defaultActions[0]);
        int prefLeft = prefs.getInt(PREF_SWIPEACTION_LEFT + tag, defaultActions[1]);
        if (prefRight == -1 || prefLeft == -1 || prefRight > swipeActions.size() || prefLeft > swipeActions.size()) {
            //no prefs set or out of bounds
            return new int[]{};
        }
        return new int[] {prefRight, prefLeft};
    }

    private static int[] getPrefs(Context context, String tag) {
        return getPrefs(context, tag, new int[] {-1, -1});
    }

    public static int[] getPrefsWithDefaults(Context context, String tag) {
        int[] defaultActions;
        switch (tag) {
            /*case InboxFragment.TAG:
                defaultActions = new int[] {ADD_TO_QUEUE, MARK_UNPLAYED};
                break;*/
            case QueueFragment.TAG:
                defaultActions = new int[] {REMOVE_FROM_QUEUE, REMOVE_FROM_QUEUE};
                break;
            default:
            case EpisodesFragment.TAG:
                defaultActions = new int[] {MARK_FAV, START_DOWNLOAD};
                break;
        }

        return getPrefs(context, tag, defaultActions);
    }

    public void reattachItemTouchHelper() {
        //prevent swipe staying if item is staying in the list
        if (itemTouchHelper != null && recyclerView != null) {
            itemTouchHelper.attachToRecyclerView(null);
            itemTouchHelper.attachToRecyclerView(recyclerView);
        }
    }

    public void resetItemTouchHelper() {
        //refresh itemTouchHelper after prefs changed
        if (itemTouchHelper != null) {
            itemTouchHelper.attachToRecyclerView(null);
        }
        itemTouchHelper();
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    public interface NewSwipeCallback {
        SimpleSwipeCallback construct();
    }

    public void setNewSwipeCallback(NewSwipeCallback newSwipeCallback) {
        this.newSwipeCallback = newSwipeCallback;
        itemTouchHelper();
    }

    public void showDialog() {
        showDialog(this::resetItemTouchHelper);
    }

    private void showDialog(SwipeActionsDialog.Callback prefsChanged) {
        new SwipeActionsDialog(fragment.requireContext(), tag).show(prefsChanged, this::reattachItemTouchHelper);
    }

    private SimpleSwipeCallback newSwipeCallback() {
        return new SimpleSwipeCallback();
    }

    public class SimpleSwipeCallback extends ItemTouchHelper.SimpleCallback {

        int[] rightleft = getPrefs(fragment.requireContext(), tag);
        boolean swipeOutEnabled = true;
        int swipedOutTo = 0;

        public SimpleSwipeCallback(int dragDirs) {
            super(dragDirs, ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT);
        }

        public SimpleSwipeCallback() {
            this(0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int swipeDir) {
            if (rightleft.length == 0) {
                //open settings dialog if no prefs are set
                showDialog(() -> rightleft = getPrefs(fragment.requireContext(), tag));
                return;
            }

            FeedItem item = ((EpisodeItemViewHolder) viewHolder).getFeedItem();

            int index = rightleft[swipeDir == ItemTouchHelper.RIGHT ? 0 : 1];
            swipeActions.get(index).action(item, fragment, filter);
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder viewHolder,
                                float dx, float dy, int actionState, boolean isCurrentlyActive) {
            //display only if preferences are set
            if (rightleft.length > 0) {
                SwipeAction right = swipeActions.get(rightleft[0]);
                SwipeAction left = swipeActions.get(rightleft[1]);

                //normal threshold
                boolean swipeThresholdReached = dx / recyclerView.getWidth() > getSwipeThreshold(viewHolder);

                //check if it will be removed
                boolean wontLeave = (dx > 0 && !right.willRemove(filter)) || (dx < 0 && !left.willRemove(filter));
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && wontLeave) {
                    swipeOutEnabled = false;

                    //Limit swipe if it's not removed
                    int maxMovement = recyclerView.getWidth() * 2 / 5;
                    float sign = dx > 0 ? 1 : -1;
                    float limitMovement = Math.min(maxMovement, sign * dx);
                    float displacementPercentage = limitMovement / maxMovement;
                    swipeThresholdReached = displacementPercentage >= 0.7;

                    // Move slower when getting near the maxMovement
                    dx = sign * maxMovement * (float) Math.sin((Math.PI / 2) * displacementPercentage);
                } else {
                    swipeOutEnabled = true;
                }

                if (isCurrentlyActive) {
                    int dir = dx > 0 ? ItemTouchHelper.RIGHT : ItemTouchHelper.LEFT;
                    swipedOutTo = swipeThresholdReached ? dir : 0;
                }

                Context context = fragment.requireContext();
                RecyclerViewSwipeDecorator.Builder builder = new RecyclerViewSwipeDecorator.Builder(
                        c, recyclerView, viewHolder, dx, dy, actionState, isCurrentlyActive)
                        .addSwipeRightBackgroundColor(ContextCompat.getColor(context, right.actionColor()))
                        .addSwipeRightActionIcon(right.actionIcon())
                        .addSwipeLeftBackgroundColor(ContextCompat.getColor(context, left.actionColor()))
                        .addSwipeLeftActionIcon(left.actionIcon());
                if (wontLeave) {
                    int actionColor = ThemeUtils.getColorFromAttr(context, R.attr.action_icon_color);
                    builder.setActionIconTint(swipeThresholdReached
                            ? actionColor : ((actionColor & 0xffffff) | 0x66000000));
                }
                builder.create().decorate();

            }

            super.onChildDraw(c, recyclerView, viewHolder, dx, dy, actionState, isCurrentlyActive);
        }

        @Override
        public float getSwipeEscapeVelocity(float defaultValue) {
            return swipeOutEnabled ? defaultValue : Float.MAX_VALUE;
        }

        @Override
        public float getSwipeVelocityThreshold(float defaultValue) {
            return swipeOutEnabled ? defaultValue : 0;
        }

        @Override
        public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
            return swipeOutEnabled ? 0.6f : 1.0f;
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);

            if (swipedOutTo != 0) {
                onSwiped(viewHolder, swipedOutTo);
                swipedOutTo = 0;
            }
        }
    }

}
