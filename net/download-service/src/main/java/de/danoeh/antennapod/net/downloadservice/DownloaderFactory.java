package de.danoeh.antennapod.net.downloadservice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface DownloaderFactory {
    @Nullable
    Downloader create(@NonNull DownloadRequest request);
}