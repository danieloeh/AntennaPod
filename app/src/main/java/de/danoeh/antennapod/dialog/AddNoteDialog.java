package de.danoeh.antennapod.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.Note;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.util.Converter;
import de.danoeh.antennapod.core.util.FeedItemUtil;
import de.danoeh.antennapod.core.util.ShareUtils;

public class AddNoteDialog extends DialogFragment {

    private static final String TAG = AddNoteDialog.class.getSimpleName();
    private static final String ARGUMENT_FEED_ITEM = "feedItem";

    private Context ctx;
    private FeedItem item;

    public AddNoteDialog() {
        // Empty constructor required for DialogFragment
    }

    public static AddNoteDialog newInstance(FeedItem item) {
        Log.d(TAG, "newInstance: existing note " + item.getNote());
        Bundle arguments = new Bundle();
        arguments.putSerializable(ARGUMENT_FEED_ITEM, item);
        AddNoteDialog dialog = new AddNoteDialog();
        dialog.setArguments(arguments);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            ctx = getActivity();
            item = (FeedItem) getArguments().getSerializable(ARGUMENT_FEED_ITEM);
        }
        Note note = item.getNote();
        if (note == null) {
            note = new Note();
            item.setNote(note);
        }

        Log.d(TAG, "onCreateDialog: note " + note);
        View content = View.inflate(ctx, R.layout.add_note_dialog, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(R.string.notes_dialog_title);
        builder.setView(content);

        EditText mNotesView = content.findViewById(R.id.editNotes);

        if (note.getNotes() != null) {
            mNotesView.append(note.getNotes());
        }
        if (mNotesView.length() == 0) {
            //append title also the very first time
            mNotesView.append(ShareUtils.getItemShareText(item) + " " + FeedItemUtil.getLinkWithFallback(item));
        }
        mNotesView.append("\n\n");
        int pos = item.getMedia().getPosition();
        String time = "[" + Converter.getDurationStringLong(pos) + "] ";
        mNotesView.append(time);
        //position the cursor at the bottom
        mNotesView.setSelection(mNotesView.getText().length());

        builder.setPositiveButton(R.string.dialog_save, (dialog, id) -> {
            String finalNote = mNotesView.getText() != null ? mNotesView.getText().toString() : null;
            Log.d(TAG, "final note: " + finalNote);
            item.getNote().setNotes(finalNote);
            DBWriter.saveNote(item);
        }).setNegativeButton(R.string.cancel_label, (dialog, id) -> dialog.dismiss());

        return builder.create();
    }

}
