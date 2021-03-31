package de.danoeh.antennapod.core.event;

import androidx.annotation.NonNull;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class NotesEvent {

    public enum Action {
        SAVED
    }

    private final long noteId;
    private final NotesEvent.Action action;

    public NotesEvent(Action action, long noteId) {
        this.action = action;
        this.noteId = noteId;
    }

    public static NotesEvent saved(long noteId) {
        return new NotesEvent(Action.SAVED, noteId);
    }

    @NonNull
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("action", action)
                .append("noteId", noteId)
                .toString();
    }

    public long getNoteId() {
        return noteId;
    }
}
