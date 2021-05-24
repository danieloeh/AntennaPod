package de.danoeh.antennapod.net.downloadservice;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import de.danoeh.antennapod.model.feed.FeedFile;

public class DownloadRequest implements Parcelable {
    public static final String TAG = "DownloadRequest";

    private final String destination;
    private final String source;
    private final String title;
    private String username;
    private String password;
    private String lastModified;
    private final boolean deleteOnFailure;
    private final long feedfileId;
    private final int feedfileType;
    private final Bundle arguments;

    private int progressPercent;
    private long soFar;
    private long size;
    private int statusMsg;
    private boolean mediaEnqueued;
    private boolean initiatedByUser;
    private boolean cleanupMedia = false;

    public DownloadRequest(@NonNull String destination, @NonNull String source, @NonNull String title, long feedfileId,
                           int feedfileType, String username, String password, boolean deleteOnFailure,
                           Bundle arguments, boolean initiatedByUser) {
        this(destination, source, title, feedfileId, feedfileType, null, deleteOnFailure, username, password, false,
             arguments, initiatedByUser);
    }

    private DownloadRequest(Builder builder) {
        this(builder.destination, builder.source, builder.title, builder.feedfileId, builder.feedfileType,
             builder.lastModified, builder.deleteOnFailure, builder.username, builder.password, false,
             builder.arguments != null ? builder.arguments : new Bundle(), builder.initiatedByUser);
    }

    private DownloadRequest(Parcel in) {
        this(in.readString(), in.readString(), in.readString(), in.readLong(), in.readInt(), in.readString(),
             in.readByte() > 0, nullIfEmpty(in.readString()), nullIfEmpty(in.readString()), in.readByte() > 0,
             in.readBundle(), in.readByte() > 0);
    }

    private DownloadRequest(String destination, String source, String title, long feedfileId, int feedfileType,
                            String lastModified, boolean deleteOnFailure, String username, String password,
                            boolean mediaEnqueued, Bundle arguments, boolean initiatedByUser) {
        this.destination = destination;
        this.source = source;
        this.title = title;
        this.feedfileId = feedfileId;
        this.feedfileType = feedfileType;
        this.lastModified = lastModified;
        this.deleteOnFailure = deleteOnFailure;
        this.username = username;
        this.password = password;
        this.mediaEnqueued = mediaEnqueued;
        this.arguments = arguments;
        this.initiatedByUser = initiatedByUser;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(destination);
        dest.writeString(source);
        dest.writeString(title);
        dest.writeLong(feedfileId);
        dest.writeInt(feedfileType);
        dest.writeString(lastModified);
        dest.writeByte((deleteOnFailure) ? (byte) 1 : 0);
        // in case of null username/password, still write an empty string
        // (rather than skipping it). Otherwise, unmarshalling  a collection
        // of them from a Parcel (from an Intent extra to submit a request to DownloadService) will fail.
        //
        // see: https://stackoverflow.com/a/22926342
        dest.writeString(nonNullString(username));
        dest.writeString(nonNullString(password));
        dest.writeByte((mediaEnqueued) ? (byte) 1 : 0);
        dest.writeBundle(arguments);
        dest.writeByte(initiatedByUser ? (byte) 1 : 0);
    }

    private static String nonNullString(String str) {
        return str != null ? str : "";
    }

    private static String nullIfEmpty(String str) {
        return TextUtils.isEmpty(str) ? null : str;
    }

    public static final Parcelable.Creator<DownloadRequest> CREATOR = new Parcelable.Creator<DownloadRequest>() {
        public DownloadRequest createFromParcel(Parcel in) {
            return new DownloadRequest(in);
        }

        public DownloadRequest[] newArray(int size) {
            return new DownloadRequest[size];
        }
    };


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DownloadRequest)) return false;

        DownloadRequest that = (DownloadRequest) o;

        if (lastModified != null ? !lastModified.equals(that.lastModified) : that.lastModified != null)
            return false;
        if (deleteOnFailure != that.deleteOnFailure) return false;
        if (feedfileId != that.feedfileId) return false;
        if (feedfileType != that.feedfileType) return false;
        if (progressPercent != that.progressPercent) return false;
        if (size != that.size) return false;
        if (soFar != that.soFar) return false;
        if (statusMsg != that.statusMsg) return false;
        if (!arguments.equals(that.arguments)) return false;
        if (!destination.equals(that.destination)) return false;
        if (password != null ? !password.equals(that.password) : that.password != null)
            return false;
        if (!source.equals(that.source)) return false;
        if (title != null ? !title.equals(that.title) : that.title != null) return false;
        if (username != null ? !username.equals(that.username) : that.username != null)
            return false;
        if (mediaEnqueued != that.mediaEnqueued) return false;
        if (initiatedByUser != that.initiatedByUser) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = destination.hashCode();
        result = 31 * result + source.hashCode();
        result = 31 * result + (title != null ? title.hashCode() : 0);
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (password != null ? password.hashCode() : 0);
        result = 31 * result + (lastModified != null ? lastModified.hashCode() : 0);
        result = 31 * result + (deleteOnFailure ? 1 : 0);
        result = 31 * result + (int) (feedfileId ^ (feedfileId >>> 32));
        result = 31 * result + feedfileType;
        result = 31 * result + arguments.hashCode();
        result = 31 * result + progressPercent;
        result = 31 * result + (int) (soFar ^ (soFar >>> 32));
        result = 31 * result + (int) (size ^ (size >>> 32));
        result = 31 * result + statusMsg;
        result = 31 * result + (mediaEnqueued ? 1 : 0);
        return result;
    }

    private static final String KEY_DESTINATION = "destination";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_TITLE = "title";
    private static final String KEY_FEEDFILE_ID = "feedfile_id";
    private static final String KEY_FEEDFILE_TYPE = "feedfile_type";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_DELETE_ON_FAILURE = "delete_on_failure";
    private static final String KEY_INITIATED_BY_USER = "initiated_by_user";
    private static final String KEY_PROGRESS = "progress";

    public Data toWorkData() {
        Data.Builder data = new Data.Builder();
        data.putString(KEY_DESTINATION, getDestination());
        data.putString(KEY_SOURCE, getSource());
        data.putString(KEY_TITLE, getTitle());
        data.putLong(KEY_FEEDFILE_ID, getFeedfileId());
        data.putInt(KEY_FEEDFILE_TYPE, getFeedfileType());
        data.putString(KEY_USERNAME, getUsername());
        data.putString(KEY_PASSWORD, getPassword());
        data.putBoolean(KEY_DELETE_ON_FAILURE, isDeleteOnFailure());
        data.putBoolean(KEY_INITIATED_BY_USER, isInitiatedByUser());
        data.putBoolean(KEY_INITIATED_BY_USER, isInitiatedByUser());
        data.putInt(KEY_PROGRESS, getProgressPercent());
        return data.build();
    }

    public static DownloadRequest from(Data data) {
        DownloadRequest request = new DownloadRequest(
                data.getString(KEY_DESTINATION),
                data.getString(KEY_SOURCE),
                data.getString(KEY_TITLE),
                data.getLong(KEY_FEEDFILE_ID, 0),
                data.getInt(KEY_FEEDFILE_TYPE, 0),
                data.getString(KEY_USERNAME),
                data.getString(KEY_PASSWORD),
                data.getBoolean(KEY_DELETE_ON_FAILURE, false),
                new Bundle(),
                data.getBoolean(KEY_INITIATED_BY_USER, false));
        request.setProgressPercent(data.getInt(KEY_PROGRESS, 0));
        return request;
    }

    public boolean isCleanupMedia() {
        return cleanupMedia;
    }

    public String getDestination() {
        return destination;
    }

    public String getSource() {
        return source;
    }

    public String getTitle() {
        return title;
    }

    public long getFeedfileId() {
        return feedfileId;
    }

    public int getFeedfileType() {
        return feedfileType;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = progressPercent;
    }

    public long getSoFar() {
        return soFar;
    }

    public void setSoFar(long soFar) {
        this.soFar = soFar;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setStatusMsg(int statusMsg) {
        this.statusMsg = statusMsg;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public DownloadRequest setLastModified(@Nullable String lastModified) {
        this.lastModified = lastModified;
        return this;
    }

    @Nullable
    public String getLastModified() {
        return lastModified;
    }

    public boolean isDeleteOnFailure() {
        return deleteOnFailure;
    }

    public boolean isMediaEnqueued() {
        return mediaEnqueued;
    }

    public boolean isInitiatedByUser() {
        return initiatedByUser;
    }

    /**
     * Set to true if the media is enqueued because of this download.
     * The state is helpful if the download is cancelled, and undoing the enqueue is needed.
     */
    public void setMediaEnqueued(boolean mediaEnqueued) {
        this.mediaEnqueued = mediaEnqueued;
    }

    public Bundle getArguments() {
        return arguments;
    }

    public static class Builder {
        private final String destination;
        private final String source;
        private final String title;
        private String username;
        private String password;
        private String lastModified;
        private boolean deleteOnFailure = false;
        private final long feedfileId;
        private final int feedfileType;
        private Bundle arguments;
        private boolean initiatedByUser;

        public Builder(@NonNull String destination, @NonNull FeedFile item, boolean initiatedByUser) {
            this.destination = destination;
            this.source = URLChecker.prepareURL(item.getDownload_url());
            this.title = item.getHumanReadableIdentifier();
            this.feedfileId = item.getId();
            this.feedfileType = item.getTypeAsInt();
            this.initiatedByUser = initiatedByUser;
        }

        public Builder deleteOnFailure(boolean deleteOnFailure) {
            this.deleteOnFailure = deleteOnFailure;
            return this;
        }

        public Builder lastModified(String lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder withAuthentication(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        public DownloadRequest build() {
            return new DownloadRequest(this);
        }

        public Builder withArguments(Bundle args) {
            this.arguments = args;
            return this;
        }

    }
}
