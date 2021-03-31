package de.danoeh.antennapod.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.NotesAdapter;
import de.danoeh.antennapod.core.feed.Note;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.view.EmptyViewHandler;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.List;

/**
 * Show all saved notes
 */
public class NotesListFragment extends Fragment implements NotesAdapter.OnNoteClickListener {

    public static final String TAG = "NotesFragment";
    private RecyclerView recyclerView;
    private EmptyViewHandler emptyView;
    private ProgressBar progLoading;
    private Disposable disposable;
    List<Note> notes;
    private NotesAdapter adapter;
    private static final String KEY_UP_ARROW = "up_arrow";
    private boolean displayUpArrow;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View root = inflater.inflate(R.layout.layout_notes, container, false);
        Toolbar toolbar = root.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.notes);

        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0;
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW);
        }
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar, displayUpArrow);

        recyclerView = root.findViewById(R.id.recyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);

        adapter = new NotesAdapter((MainActivity) getActivity());
        adapter.setOnItemClickListener(this);
        recyclerView.setAdapter(adapter);

        emptyView = new EmptyViewHandler(getContext());
        emptyView.attachToRecyclerView(recyclerView);
        emptyView.setIcon(R.attr.stat_playlist);
        emptyView.setTitle(R.string.no_notes_header);
        emptyView.setMessage(R.string.no_notes);

        progLoading = root.findViewById(R.id.progLoading);
        progLoading.setVisibility(View.VISIBLE);
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadNotes();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow);
        super.onSaveInstanceState(outState);
    }

    private void onFragmentLoaded() {
        adapter.notifyDataSetChanged();
    }

    private void loadNotes() {
        if (disposable != null) {
            disposable.dispose();
        }
        progLoading.setVisibility(View.VISIBLE);
        emptyView.hide();
        disposable = Observable.fromCallable(this::loadData)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    progLoading.setVisibility(View.GONE);
                    notes = result;
                    adapter.updateItems(notes);
                    onFragmentLoaded();
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private List<Note> loadData() {
        List<Note> notes = DBReader.getAllNotes();
        return notes;
    }

    @Override
    public void onNoteTapped(Note note) {
        copyToClipboard(note.getNotes());
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("notes", text);
        clipboard.setPrimaryClip(clip);
        ((MainActivity) getActivity()).showSnackbarAbovePlayer(R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT);
    }
}
