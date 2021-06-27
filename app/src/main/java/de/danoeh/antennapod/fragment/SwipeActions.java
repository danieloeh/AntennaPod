package de.danoeh.antennapod.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.actionbutton.DownloadActionButton;
import de.danoeh.antennapod.core.dialog.DownloadRequestErrorDialogCreator;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.storage.DownloadRequestException;
import de.danoeh.antennapod.core.storage.DownloadRequester;
import de.danoeh.antennapod.menuhandler.FeedItemMenuHandler;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.view.viewholder.EpisodeItemViewHolder;
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator;

public class SwipeActions {
    private static final String PREF_NAME = "SwipeActionsPrefs";
    private static final String PREF_SWIPEACTIONS = "PrefsSwipeActions";
    public static final String ADD_TO_QUEUE = "ADDTOQUEUE";
    public static final String MARK_PLAYED = "MARKPLAYED";
    public static final String MARK_UNPLAYED = "MARKUNPLAYED";
    public static final String MARK_FAV = "MARKFAV";
    public static final String START_DOWNLOAD = "STARTDOWNLOAD";

    RecyclerView recyclerView;
    ItemTouchHelper itemTouchHelper;
    Fragment fragment;
    String tag;

    private interface Callback {
        void onPrefsChange();
    }

    public SwipeActions(Fragment fragment, String tag) {
        this.fragment = fragment;
        this.tag = tag;
        itemTouchHelper();
    }

    public SwipeActions attachTo(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
        itemTouchHelper.attachToRecyclerView(recyclerView);
        return this;
    }

    private void itemTouchHelper() {
        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT) {

            String[] rightleft = getPrefs(fragment.requireContext(), tag);

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                  RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
                if (rightleft.length == 0) {
                    //open settings dialog if no prefs are set
                    show(() -> rightleft = getPrefs(fragment.requireContext(), tag));
                    return;
                }

                String action = rightleft[swipeDir == ItemTouchHelper.RIGHT ? 0 : 1];

                FeedItem item = ((EpisodeItemViewHolder) viewHolder).getFeedItem();
                switch (action) {
                    case ADD_TO_QUEUE:
                        if (!item.isTagged(FeedItem.TAG_QUEUE)) {
                            FeedItemMenuHandler.addToQueue(fragment.requireContext(), item);
                        } else {
                            //already in queue
                            resetItemTouchHelper();
                        }
                        break;
                    case MARK_PLAYED:
                        int togglePlayState =
                                item.getPlayState() != FeedItem.PLAYED  ? FeedItem.PLAYED : FeedItem.UNPLAYED;
                        FeedItemMenuHandler.markReadWithUndo(fragment,
                                item, togglePlayState);
                        break;
                    case MARK_UNPLAYED: //remove "new" flag
                        FeedItemMenuHandler.markReadWithUndo(fragment,
                                item, FeedItem.UNPLAYED);
                        break;
                    case MARK_FAV:
                        DBWriter.toggleFavoriteItem(item);
                        break;
                    case START_DOWNLOAD:
                        if (!item.isDownloaded()) {
                            new DownloadActionButton(item)
                                    .onClick(fragment.requireContext());
                        } else {
                            resetItemTouchHelper();
                            ((MainActivity) fragment.requireActivity()).showSnackbarAbovePlayer(
                                    fragment.getResources().getString(R.string.already_downloaded),
                                    Snackbar.LENGTH_SHORT);
                        }
                        break;
                    default: break;
                }
            }

            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dx, float dy, int actionState, boolean isCurrentlyActive) {
                //display only if preferences are set
                if (rightleft.length > 0) {
                    new RecyclerViewSwipeDecorator.Builder(
                            c, recyclerView, viewHolder, dx, dy, actionState, isCurrentlyActive)
                            .addSwipeRightBackgroundColor(
                                    ContextCompat.getColor(fragment.requireContext(), actionColorFor(rightleft[0])))
                            .addSwipeRightActionIcon(actionIconFor(rightleft[0]))
                            .addSwipeLeftBackgroundColor(
                                    ContextCompat.getColor(fragment.requireContext(), actionColorFor(rightleft[1])))
                            .addSwipeLeftActionIcon(actionIconFor(rightleft[1]))
                            .create()
                            .decorate();
                }

                super.onChildDraw(c, recyclerView, viewHolder, dx, dy, actionState, isCurrentlyActive);
            }
        };

        itemTouchHelper = new ItemTouchHelper(simpleCallback);
    }

    private static int actionIconFor(String swipeAction) {
        switch (swipeAction) {
            case ADD_TO_QUEUE:
                return R.drawable.ic_playlist;
            case MARK_PLAYED:
                return R.drawable.ic_check;
            default:
            case MARK_UNPLAYED:
                return R.drawable.ic_check;
            case MARK_FAV:
                return R.drawable.ic_star;
            case START_DOWNLOAD:
                return R.drawable.ic_download;
        }
    }

    private static int actionColorFor(String swipeAction) {
        switch (swipeAction) {
            case ADD_TO_QUEUE:
                return R.color.swipe_light_green_200;
            case MARK_PLAYED:
                return R.color.swipe_light_red_200;
            default:
            case MARK_UNPLAYED:
                return R.color.swipe_light_blue_200;
            case MARK_FAV:
                return R.color.swipe_yellow_200;
            case START_DOWNLOAD:
                return R.color.swipe_light_blue_200;
        }
    }

    private static String[] getPrefs(Context context, String tag, String defaultActions) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String prefsString = prefs.getString(PREF_SWIPEACTIONS + tag, defaultActions);
        if (prefsString.isEmpty()) {
            //no prefs set
            return new String[]{};
        }
        return prefsString.split(",");
    }

    private static String[] getPrefs(Context context, String tag) {
        return getPrefs(context, tag, "");
    }

    private static String[] getPrefsWithDefaults(Context context, String tag) {
        String defaultActions;
        switch (tag) {
            case InboxFragment.TAG:
                defaultActions = ADD_TO_QUEUE + "," + MARK_UNPLAYED;
                break;
            default:
            case EpisodesFragment.TAG:
                defaultActions = MARK_FAV + "," + START_DOWNLOAD;
                break;
        }

        return getPrefs(context, tag, defaultActions);
    }

    public void resetItemTouchHelper() {
        //prevent swipe staying if item is staying in the list
        if (itemTouchHelper != null && recyclerView != null) {
            itemTouchHelper.attachToRecyclerView(null);
            itemTouchHelper.attachToRecyclerView(recyclerView);
        }
    }

    public void show() {
        new SwipeActionsDialog().show(() -> {
            //detach old
            itemTouchHelper.attachToRecyclerView(null);
            //create new
            itemTouchHelper();
            //attach new
            attachTo(recyclerView);
        });
    }

    private void show(Callback callback) {
        new SwipeActionsDialog().show(callback);
    }

    private class SwipeActionsDialog {

        protected Context context;

        public SwipeActionsDialog() {
            context = fragment.requireContext();
        }

        private void show(Callback callback) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);

            String forFragment = "";
            switch (tag) {
                case InboxFragment.TAG:
                    forFragment = context.getString(R.string.inbox_label);
                    break;
                case EpisodesFragment.TAG:
                    forFragment = context.getString(R.string.episodes_label);
                    break;
                case FeedItemlistFragment.TAG:
                    forFragment = context.getString(R.string.feeds_label);
                    break;
                default: break;
            }

            builder.setTitle(context.getString(R.string.swipeactions_label) + " - " + forFragment);

            //same order as in R.array.swipe_actions
            final List<String> prefKeys =
                    Arrays.asList(ADD_TO_QUEUE, MARK_UNPLAYED, START_DOWNLOAD, MARK_FAV, MARK_PLAYED);

            LayoutInflater inflater = LayoutInflater.from(context);
            View layout = inflater.inflate(R.layout.swipeactions_dialog, null, false);

            final ImageView rightIcon = layout.findViewById(R.id.swipeactionIconRight);
            final ImageView leftIcon = layout.findViewById(R.id.swipeactionIconLeft);
            final View rightColor = layout.findViewById(R.id.swipeColorViewRight);
            final View leftColor = layout.findViewById(R.id.swipeColorViewLeft);

            Spinner spinnerRightAction = layout.findViewById(R.id.spinnerRightAction);
            Spinner spinnerLeftAction = layout.findViewById(R.id.spinnerLeftAction);

            rightColor.setOnClickListener(view -> {
                spinnerRightAction.performClick();
            });
            leftColor.setOnClickListener(view -> {
                spinnerLeftAction.performClick();
            });

            spinnerRightAction.setAdapter(adapter());
            spinnerLeftAction.setAdapter(adapter());

            spinnerRightAction.setOnItemSelectedListener(
                    listener((a, v, i, l) -> {
                        String action = prefKeys.get(i);
                        rightIcon.setImageResource(actionIconFor(action));
                        rightColor.setBackgroundResource(actionColorFor(action));
                    })
            );
            spinnerLeftAction.setOnItemSelectedListener(
                    listener((a, v, i, l) -> {
                        String action = prefKeys.get(i);
                        leftIcon.setImageResource(actionIconFor(action));
                        leftColor.setBackgroundResource(actionColorFor(action));
                    })
            );

            //load prefs and suggest defaults if swiped the first time
            String[] rightleft = getPrefsWithDefaults(context, tag);
            int right = prefKeys.indexOf(rightleft[0]);
            int left = prefKeys.indexOf(rightleft[1]);

            spinnerRightAction.setSelection(right);
            spinnerLeftAction.setSelection(left);

            builder.setView(layout);

            builder.setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                String rightAction = prefKeys.get(spinnerRightAction.getSelectedItemPosition());
                String leftAction = prefKeys.get(spinnerLeftAction.getSelectedItemPosition());
                savePrefs(tag, rightAction, leftAction);
                callback.onPrefsChange();
            });
            builder.setNegativeButton(R.string.cancel_label, null);
            builder.setOnDismissListener(dialogInterface -> resetItemTouchHelper());
            builder.create().show();
        }

        private void savePrefs(String tag, String right, String left) {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(PREF_SWIPEACTIONS + tag, right + "," + left).apply();
        }

        private AdapterView.OnItemSelectedListener listener(AdapterView.OnItemClickListener listener) {
            return new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    listener.onItemClick(adapterView, view, i, l);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            };
        }

        private ArrayAdapter<String> adapter() {
            return new ArrayAdapter<>(context,
                    android.R.layout.simple_spinner_dropdown_item,
                    context.getResources().getStringArray(R.array.swipe_actions));
        }
    }
}
