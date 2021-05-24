package de.danoeh.antennapod.core.service.download;

import android.database.Cursor;
import androidx.annotation.NonNull;

import java.util.Date;

import de.danoeh.antennapod.model.feed.FeedFile;
import de.danoeh.antennapod.core.storage.PodDBAdapter;
import de.danoeh.antennapod.core.util.DownloadError;

/** Contains status attributes for one download */
public class DownloadStatus {
	/**
	 * Downloaders should use this constant for the size attribute if necessary
	 * so that the listadapters etc. can react properly.
	 */
	public static final int SIZE_UNKNOWN = -1;

	// ----------------------------------- ATTRIBUTES STORED IN DB
	/** Unique id for storing the object in database. */
    private long id;
	/**
	 * A human-readable string which is shown to the user so that he can
	 * identify the download. Should be the title of the item/feed/media or the
	 * URL if the download has no other title.
	 */
    private final String title;
	private DownloadError reason;
	/**
	 * A message which can be presented to the user to give more information.
	 * Should be null if Download was successful.
	 */
    private String reasonDetailed;
	private boolean successful;
	private Date completionDate;
	private final long feedfileId;
	/**
	 * Is used to determine the type of the feedfile even if the feedfile does
	 * not exist anymore. The value should be FEEDFILETYPE_FEED,
	 * FEEDFILETYPE_FEEDIMAGE or FEEDFILETYPE_FEEDMEDIA
	 */
    private final int feedfileType;
    private final boolean initiatedByUser;

	// ------------------------------------ NOT STORED IN DB
    private boolean done;
	private boolean cancelled;

	/** Constructor for creating new completed downloads. */
	public DownloadStatus(@NonNull FeedFile feedfile, String title, DownloadError reason, boolean successful,
                          String reasonDetailed, boolean initiatedByUser) {
		this(0, title, feedfile.getId(), feedfile.getTypeAsInt(), successful, false, true, reason, new Date(),
             reasonDetailed, initiatedByUser);
	}

	public static DownloadStatus fromCursor(Cursor cursor) {
		int indexId = cursor.getColumnIndex(PodDBAdapter.KEY_ID);
		int indexTitle = cursor.getColumnIndex(PodDBAdapter.KEY_DOWNLOADSTATUS_TITLE);
		int indexFeedFile = cursor.getColumnIndex(PodDBAdapter.KEY_FEEDFILE);
		int indexFileFileType = cursor.getColumnIndex(PodDBAdapter.KEY_FEEDFILETYPE);
		int indexSuccessful = cursor.getColumnIndex(PodDBAdapter.KEY_SUCCESSFUL);
		int indexReason = cursor.getColumnIndex(PodDBAdapter.KEY_REASON);
		int indexCompletionDate = cursor.getColumnIndex(PodDBAdapter.KEY_COMPLETION_DATE);
		int indexReasonDetailed = cursor.getColumnIndex(PodDBAdapter.KEY_REASON_DETAILED);

		return new DownloadStatus(cursor.getLong(indexId), cursor.getString(indexTitle), cursor.getLong(indexFeedFile),
				                  cursor.getInt(indexFileFileType), cursor.getInt(indexSuccessful) > 0, false, true,
				                  DownloadError.fromCode(cursor.getInt(indexReason)),
				                  new Date(cursor.getLong(indexCompletionDate)),
				                  cursor.getString(indexReasonDetailed), false);
	}

	public DownloadStatus(long id, String title, long feedfileId, int feedfileType, boolean successful,
                           boolean cancelled, boolean done, DownloadError reason, Date completionDate,
                           String reasonDetailed, boolean initiatedByUser) {
		this.id = id;
		this.title = title;
		this.feedfileId = feedfileId;
		this.reason = reason;
		this.successful = successful;
		this.cancelled = cancelled;
		this.done = done;
		this.completionDate = (Date) completionDate.clone();
		this.reasonDetailed = reasonDetailed;
		this.feedfileType = feedfileType;
		this.initiatedByUser = initiatedByUser;
	}

	@Override
	public String toString() {
		return "DownloadStatus [id=" + id + ", title=" + title + ", reason="
				+ reason + ", reasonDetailed=" + reasonDetailed
				+ ", successful=" + successful + ", completionDate="
				+ completionDate + ", feedfileId=" + feedfileId
				+ ", feedfileType=" + feedfileType + ", done=" + done
				+ ", cancelled=" + cancelled + "]";
	}

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public DownloadError getReason() {
        return reason;
    }

    public String getReasonDetailed() {
        return reasonDetailed;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public Date getCompletionDate() {
        return (Date) completionDate.clone();
    }

    public long getFeedfileId() {
        return feedfileId;
    }

    public int getFeedfileType() {
        return feedfileType;
    }

    public boolean isInitiatedByUser() { return initiatedByUser; }

    public boolean isDone() {
        return done;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setSuccessful() {
        this.successful = true;
        this.reason = DownloadError.SUCCESS;
        this.done = true;
    }

    public void setFailed(DownloadError reason, String reasonDetailed) {
        this.successful = false;
        this.reason = reason;
        this.reasonDetailed = reasonDetailed;
        this.done = true;
    }

    public void setCancelled() {
        this.successful = false;
        this.reason = DownloadError.ERROR_DOWNLOAD_CANCELLED;
        this.done = true;
        this.cancelled = true;
    }

    public void setId(long id) {
        this.id = id;
    }
}