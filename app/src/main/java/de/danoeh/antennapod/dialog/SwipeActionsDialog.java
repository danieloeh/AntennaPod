package de.danoeh.antennapod.dialog;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;

import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

import com.annimon.stream.Stream;

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.fragment.EpisodesFragment;
import de.danoeh.antennapod.fragment.FeedItemlistFragment;
import de.danoeh.antennapod.fragment.QueueFragment;
import de.danoeh.antennapod.fragment.swipeactions.SwipeActions;

public class SwipeActionsDialog {

    private final Context context;
    private final String tag;

    public SwipeActionsDialog(Context context, String tag) {
        this.context = context;
        this.tag = tag;
    }

    public void show(Callback prefsChanged) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        String forFragment = "";
        switch (tag) {
            /*case InboxFragment.TAG:
                forFragment = context.getString(R.string.inbox_label);
                break;*/
            case EpisodesFragment.TAG:
                forFragment = context.getString(R.string.episodes_label);
                break;
            case FeedItemlistFragment.TAG:
                forFragment = context.getString(R.string.feeds_label);
                break;
            case QueueFragment.TAG:
                forFragment = context.getString(R.string.queue_label);
                break;
            default: break;
        }

        builder.setTitle(context.getString(R.string.swipeactions_label) + " - " + forFragment);

        LayoutInflater inflater = LayoutInflater.from(context);
        View layout = inflater.inflate(R.layout.swipeactions_dialog, null, false);
        populateMockEpisode(layout.findViewById(R.id.mockEpisodeLeft));
        populateMockEpisode(layout.findViewById(R.id.mockEpisodeRight));

        final ImageView rightIcon = layout.findViewById(R.id.swipeactionIconRight);
        final ImageView leftIcon = layout.findViewById(R.id.swipeactionIconLeft);

        Spinner spinnerRightAction = layout.findViewById(R.id.spinnerRightAction);
        Spinner spinnerLeftAction = layout.findViewById(R.id.spinnerLeftAction);

        rightIcon.setOnClickListener(view -> spinnerRightAction.performClick());
        leftIcon.setOnClickListener(view -> spinnerLeftAction.performClick());

        spinnerRightAction.setAdapter(adapter());
        spinnerLeftAction.setAdapter(adapter());

        spinnerRightAction.setOnItemSelectedListener(listener((a, v, i, l) -> {
            rightIcon.setImageResource(SwipeActions.swipeActions.get(i).actionIcon());
            rightIcon.setBackgroundResource(SwipeActions.swipeActions.get(i).actionColor());
        }));
        spinnerLeftAction.setOnItemSelectedListener(listener((a, v, i, l) -> {
            leftIcon.setImageResource(SwipeActions.swipeActions.get(i).actionIcon());
            leftIcon.setBackgroundResource(SwipeActions.swipeActions.get(i).actionColor());
        }));

        //load prefs and suggest defaults if swiped the first time
        int[] rightleft = SwipeActions.getPrefsWithDefaults(context, tag);
        int right = rightleft[0];
        int left = rightleft[1];

        spinnerRightAction.setSelection(right);
        spinnerLeftAction.setSelection(left);

        builder.setView(layout);

        builder.setPositiveButton(R.string.confirm_label, (dialog, which) -> {
            int rightAction = spinnerRightAction.getSelectedItemPosition();
            int leftAction = spinnerLeftAction.getSelectedItemPosition();
            savePrefs(tag, rightAction, leftAction);
            prefsChanged.onCall();
        });
        builder.setNegativeButton(R.string.cancel_label, null);
        builder.create().show();
    }

    private void populateMockEpisode(View view) {
        view.setAlpha(0.3f);
        view.findViewById(R.id.secondaryActionButton).setVisibility(View.GONE);
        view.findViewById(R.id.drag_handle).setVisibility(View.GONE);
        ((TextView) view.findViewById(R.id.statusUnread)).setText("███");
        ((TextView) view.findViewById(R.id.txtvTitle)).setText("████████████");
        ((TextView) view.findViewById(R.id.txtvPosition)).setText("█████");
        ((TextView) view.findViewById(R.id.txtvDuration)).setText("█████");
    }

    private void savePrefs(String tag, int right, int left) {
        SharedPreferences prefs = context.getSharedPreferences(SwipeActions.PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(SwipeActions.PREF_SWIPEACTION_RIGHT + tag, right).apply();
        prefs.edit().putInt(SwipeActions.PREF_SWIPEACTION_LEFT + tag, left).apply();
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
        final List<String> titles = Stream.of(SwipeActions.swipeActions)
                .map(swa -> swa.title(context))
                .toList();

        return new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item,
                titles);
    }

    public interface Callback {
        void onCall();
    }
}
