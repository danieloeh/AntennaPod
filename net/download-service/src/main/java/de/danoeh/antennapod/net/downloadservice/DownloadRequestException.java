package de.danoeh.antennapod.net.downloadservice;

/**
 * Thrown by the DownloadRequester if a download request contains invalid data
 * or something went wrong while processing the request.
 */
public class DownloadRequestException extends Exception {
    private static final long serialVersionUID = 1L;

    public DownloadRequestException() {
        super();
    }

    public DownloadRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public DownloadRequestException(String message) {
        super(message);
    }

    public DownloadRequestException(Throwable cause) {
        super(cause);
    }
}
