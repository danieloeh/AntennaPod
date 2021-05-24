package de.danoeh.antennapod.core.service.download;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import de.danoeh.antennapod.net.downloadservice.DownloadRequest;
import de.danoeh.antennapod.net.downloadservice.Downloader;

public class StubDownloader extends Downloader {

    private final long downloadTime;

    @NonNull
    private final Consumer<DownloadStatus> onDownloadComplete;

    public StubDownloader(@NonNull DownloadRequest request, long downloadTime, @NonNull Consumer<DownloadStatus> onDownloadComplete) {
        super(request);
        this.downloadTime = downloadTime;
        this.onDownloadComplete = onDownloadComplete;
    }

    @Override
    protected void download() {
        try {
            Thread.sleep(downloadTime);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        onDownloadComplete.accept(getResult());
    }

    @NonNull
    @Override
    public DownloadRequest getDownloadRequest() {
        return super.getDownloadRequest();
    }

    @NonNull
    @Override
    public DownloadStatus getResult() {
        return super.getResult();
    }

    @Override
    public boolean isFinished() {
        return super.isFinished();
    }

    @Override
    public void cancel() {
        super.cancel();
        getResult().setCancelled();
    }
}
