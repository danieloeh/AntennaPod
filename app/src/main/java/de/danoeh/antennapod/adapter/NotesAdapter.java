package de.danoeh.antennapod.adapter;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.core.feed.Note;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * List adapter for the list of new episodes.
 */
public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NotesViewHolder> {

    private final WeakReference<MainActivity> mainActivityRef;
    private List<Note> mNotes = new ArrayList<>();
    private OnNoteClickListener onItemClickListener;

    public interface OnNoteClickListener {
        void onNoteTapped(Note note);
    }

    public NotesAdapter(MainActivity mainActivity) {
        super();
        this.mainActivityRef = new WeakReference<>(mainActivity);
    }

    public void updateItems(List<Note> items) {
        mNotes = items;
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnNoteClickListener clickListener) {
        this.onItemClickListener = clickListener;
    }

    @NonNull
    @Override
    public final NotesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View entryView = inflater.inflate(R.layout.row_notes, parent, false);
        return new NotesViewHolder(entryView);
    }

    @Override
    public final void onBindViewHolder(NotesViewHolder holder, int pos) {

        Note item = mNotes.get(pos);
        holder.tvNoteTitle.setText(item.getFeedItemTitle());
        holder.tvNote.setText(item.getNotes());
    }

    @Override
    public int getItemCount() {
        return mNotes.size();
    }

    protected Note getItem(int index) {
        return mNotes.get(index);
    }

    protected Activity getActivity() {
        return mainActivityRef.get();
    }

    class NotesViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final TextView tvNoteTitle;
        private final TextView tvNote;

        public NotesViewHolder(View entryView) {
            super(entryView);
            tvNote = entryView.findViewById(R.id.tvNote);
            tvNoteTitle = entryView.findViewById(R.id.tvNoteTitle);
            entryView.findViewById(R.id.notesCard).setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (onItemClickListener != null) {
                int position = getBindingAdapterPosition();
                onItemClickListener.onNoteTapped(mNotes.get(position));
            }
        }
    }

}
