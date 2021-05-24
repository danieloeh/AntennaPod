package de.danoeh.antennapod.net.downloadservice;

import android.net.Uri;
import androidx.annotation.NonNull;

/**
 * Provides methods for checking and editing a URL.
 */
public final class URLChecker {

    /**
     * Class shall not be instantiated.
     */
    private URLChecker() {
    }

    private static final String AP_SUBSCRIBE = "antennapod-subscribe://";

    /**
     * Checks if URL is valid and modifies it if necessary.
     *
     * @param url The url which is going to be prepared
     * @return The prepared url
     */
    public static String prepareURL(@NonNull String url) {
        url = url.trim();
        String lowerCaseUrl = url.toLowerCase(); // protocol names are case insensitive
        if (lowerCaseUrl.startsWith("feed://")) {
            return prepareURL(url.substring("feed://".length()));
        } else if (lowerCaseUrl.startsWith("pcast://")) {
            return prepareURL(url.substring("pcast://".length()));
        } else if (lowerCaseUrl.startsWith("pcast:")) {
            return prepareURL(url.substring("pcast:".length()));
        } else if (lowerCaseUrl.startsWith("itpc")) {
            return prepareURL(url.substring("itpc://".length()));
        } else if (lowerCaseUrl.startsWith(AP_SUBSCRIBE)) {
            return prepareURL(url.substring(AP_SUBSCRIBE.length()));
        } else if (!(lowerCaseUrl.startsWith("http://") || lowerCaseUrl.startsWith("https://"))) {
            return "http://" + url;
        } else {
            return url;
        }
    }

    /**
     * Checks if URL is valid and modifies it if necessary.
     * This method also handles protocol relative URLs.
     *
     * @param url  The url which is going to be prepared
     * @param base The url against which the (possibly relative) url is applied. If this is null,
     *             the result of prepareURL(url) is returned instead.
     * @return The prepared url
     */
    public static String prepareURL(String url, String base) {
        if (base == null) {
            return prepareURL(url);
        }
        url = url.trim();
        base = prepareURL(base);
        Uri urlUri = Uri.parse(url);
        Uri baseUri = Uri.parse(base);
        if (urlUri.isRelative() && baseUri.isAbsolute()) {
            return urlUri.buildUpon().scheme(baseUri.getScheme()).build().toString();
        } else {
            return prepareURL(url);
        }
    }
}
