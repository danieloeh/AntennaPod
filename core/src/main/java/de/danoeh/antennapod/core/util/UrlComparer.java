package de.danoeh.antennapod.core.util;

import android.text.TextUtils;
import okhttp3.HttpUrl;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides methods for checking a URL.
 */
public final class UrlComparer {
    private UrlComparer() {
    }

    public static boolean containsUrl(List<String> list, String url) {
        for (String item : list) {
            if (urlEquals(item, url)) {
                return true;
            }
        }
        return false;
    }

    public static boolean urlEquals(String string1, String string2) {
        HttpUrl url1 = HttpUrl.parse(string1);
        HttpUrl url2 = HttpUrl.parse(string2);
        if (!url1.host().equals(url2.host())) {
            return false;
        }
        List<String> pathSegments1 = normalizePathSegments(url1.pathSegments());
        List<String> pathSegments2 = normalizePathSegments(url2.pathSegments());
        if (!pathSegments1.equals(pathSegments2)) {
            return false;
        }
        if (TextUtils.isEmpty(url1.query())) {
            return TextUtils.isEmpty(url2.query());
        }
        return url1.query().equals(url2.query());
    }

    /**
     * Removes empty segments and converts all to lower case.
     * @param input List of path segments
     * @return Normalized list of path segments
     */
    private static List<String> normalizePathSegments(List<String> input) {
        List<String> result = new ArrayList<>();
        for (String string : input) {
            if (!TextUtils.isEmpty(string)) {
                result.add(string.toLowerCase());
            }
        }
        return result;
    }
}
