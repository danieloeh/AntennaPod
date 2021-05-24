package de.danoeh.antennapod.net.downloadservice;

import android.content.Context;
import android.net.wifi.WifiManager;
import androidx.annotation.NonNull;

import java.util.Date;
import java.util.concurrent.Callable;

import de.danoeh.antennapod.core.ClientConfig;
import de.danoeh.antennapod.core.R;
import de.danoeh.antennapod.core.service.download.DownloadStatus;

/**
 * Downloads files
 */
public abstract class Downloader implements Callable<Downloader> {
    private static final String TAG = "Downloader";

    private volatile boolean finished;

    public volatile boolean cancelled;

    @NonNull
    final DownloadRequest request;
    @NonNull
    final DownloadStatus result;

    protected Downloader(@NonNull DownloadRequest request) {
        super();
        this.request = request;
        this.request.setStatusMsg(R.string.download_pending);
        this.cancelled = false;
        this.result = new DownloadStatus(0, request.getTitle(), request.getFeedfileId(),
                request.getFeedfileType(), false, false, false,
                null, new Date(), null, request.isInitiatedByUser());
    }

    protected abstract void download();

    public final Downloader call() {
        WifiManager wifiManager = (WifiManager)
                ClientConfig.applicationCallbacks.getApplicationInstance().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.WifiLock wifiLock = null;
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(TAG);
            wifiLock.acquire();
        }

        download();

        if (wifiLock != null) {
            wifiLock.release();
        }

        if (result == null) {
            throw new IllegalStateException(
                    "Downloader hasn't created DownloadStatus object");
        }
        finished = true;
        return this;
    }

    @NonNull
    public DownloadRequest getDownloadRequest() {
        return request;
    }

    @NonNull
    public DownloadStatus getResult() {
        return result;
    }

    public boolean isFinished() {
        return finished;
    }

    public void cancel() {
        cancelled = true;
    }

}