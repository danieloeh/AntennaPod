package de.danoeh.antennapod.core.service;

import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okio.ByteString;

public class BasicAuthorizationInterceptor implements Interceptor {
    private static final String TAG = "BasicAuthInterceptor";

    @Override
    @NonNull
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        Response response = chain.proceed(request);

        if (response.code() != HttpURLConnection.HTTP_UNAUTHORIZED) {
            return response;
        }

        String userInfo = "";
        /*if (request.tag() instanceof DownloadRequest) {
            DownloadRequest downloadRequest = (DownloadRequest) request.tag();
            userInfo = URIUtil.getURIFromRequestUrl(downloadRequest.getSource()).getUserInfo();
            if (TextUtils.isEmpty(userInfo)) {
                userInfo = downloadRequest.getUsername() + ":" + downloadRequest.getPassword();
            }
        } else {
            userInfo = DBReader.getImageAuthentication(request.url().toString());
        }*/

        if (TextUtils.isEmpty(userInfo)) {
            Log.d(TAG, "no credentials for '" + request.url() + "'");
            return response;
        }

        String[] parts = userInfo.split(":");
        if (parts.length != 2) {
            Log.d(TAG, "Invalid credentials for '" + request.url() + "'");
            return response;
        }

        Request.Builder newRequest = request.newBuilder();
        Log.d(TAG, "Authorization failed, re-trying with ISO-8859-1 encoded credentials");
        String credentials = encodeCredentials(parts[0], parts[1], "ISO-8859-1");
        newRequest.header("Authorization", credentials);
        response = chain.proceed(newRequest.build());

        if (response.code() != HttpURLConnection.HTTP_UNAUTHORIZED) {
            return response;
        }

        Log.d(TAG, "Authorization failed, re-trying with UTF-8 encoded credentials");
        credentials = encodeCredentials(parts[0], parts[1], "UTF-8");
        newRequest.header("Authorization", credentials);
        return chain.proceed(newRequest.build());
    }

    public static String encodeCredentials(String username, String password, String charset) {
        try {
            String credentials = username + ":" + password;
            byte[] bytes = credentials.getBytes(charset);
            String encoded = ByteString.of(bytes).base64();
            return "Basic " + encoded;
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
