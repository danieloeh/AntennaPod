package de.danoeh.antennapod.net.sync.nextcloud_gpodder;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.gson.GsonBuilder;
import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.aidl.NextcloudRequest;
import com.nextcloud.android.sso.api.NextcloudAPI;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException;
import com.nextcloud.android.sso.exceptions.NoCurrentAccountSelectedException;
import com.nextcloud.android.sso.helper.SingleAccountHelper;
import com.nextcloud.android.sso.model.SingleSignOnAccount;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetEpisodeActionPostResponse;
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetUploadChangesResponse;
import de.danoeh.antennapod.net.sync.model.EpisodeAction;
import de.danoeh.antennapod.net.sync.model.EpisodeActionChanges;
import de.danoeh.antennapod.net.sync.model.ISyncService;
import de.danoeh.antennapod.net.sync.model.SubscriptionChanges;
import de.danoeh.antennapod.net.sync.model.SyncServiceException;
import de.danoeh.antennapod.net.sync.model.UploadChangesResponse;
import okhttp3.Request;

import static java.time.Instant.now;

public class NextcloudGpodderService implements ISyncService {

    private Context mContext;
    private NextcloudAPI nextcloudAPI;

    public NextcloudGpodderService(Context mContext) {
        this.mContext = mContext;
    }

    @Override
    public boolean authenticated() {
        try {
            SingleAccountHelper.getCurrentSingleSignOnAccount(mContext);
            return true;
        } catch (NextcloudFilesAppAccountNotFoundException e) {
            return false;
        } catch (NoCurrentAccountSelectedException e) {
            return false;
        }
    }

    @Override
    public void login() throws SyncServiceException {
        SingleSignOnAccount ssoAccount = null;
        try {
            try {
                ssoAccount = AccountImporter.getSingleSignOnAccount(mContext, SingleAccountHelper.getCurrentSingleSignOnAccount(mContext).name);
            } catch (NoCurrentAccountSelectedException e) {
                e.printStackTrace();
            }
            NextcloudAPI.ApiConnectedListener callback = new NextcloudAPI.ApiConnectedListener() {
                @Override
                public void onConnected() {
                }

                @Override
                public void onError(Exception ex) {
                }
            };
            this.nextcloudAPI = new NextcloudAPI(mContext, ssoAccount, new GsonBuilder().create(), callback);
        } catch (NextcloudFilesAppAccountNotFoundException e) {
            e.printStackTrace();
        }

        return;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public SubscriptionChanges getSubscriptionChanges(long lastSync) throws SyncServiceException {
        try {
            String uri = "/index.php/apps/gpoddersync/subscriptions";
            HashMap<String, String> parameters = new HashMap<>();
            parameters.put("since", String.valueOf(lastSync));
            NextcloudRequest nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod("GET")
                    .setUrl(Uri.encode(uri, "/"))
                    .setParameter(parameters)
                    .build();

            InputStream inputStream = this.nextcloudAPI.performNetworkRequest(nextcloudRequest);
            String responseString = convertStreamToString(inputStream);
            JSONObject json = new JSONObject(responseString);
            inputStream.close();
            return readSubscriptionChangesFromJsonObject(json);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw new IllegalStateException(e);
        } catch (JSONException | MalformedURLException e) {
            e.printStackTrace();
            throw new SyncServiceException(e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new SyncServiceException(e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public UploadChangesResponse uploadSubscriptionChanges(List<String> addedFeeds, List<String> removedFeeds) throws SyncServiceException {
        try {
            HashMap<String, List<String>> header = new HashMap<>();
            header.put("Content-Type", Collections.singletonList("application/json"));

            String body = "{\"add\": \"" + addedFeeds.toString() + "\", \"remove\": \"" + removedFeeds.toString() + "\"}";
            NextcloudRequest nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod("POST")
                    .setUrl(Uri.encode("/index.php/apps/gpoddersync/subscription_change/create", "/"))
                    .setHeader(header)
                    .setRequestBody(body)
                    .build();

            this.nextcloudAPI.performNetworkRequest(nextcloudRequest).close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new GpodnetUploadChangesResponse(now().getEpochSecond(), new HashMap<>());
    }

    @Override
    public EpisodeActionChanges getEpisodeActionChanges(long timestamp) throws SyncServiceException {
        try {
            String uri = "/index.php/apps/gpoddersync/episode_action";
            HashMap<String, String> parameters = new HashMap<>();
            parameters.put("since", String.valueOf(timestamp));
            NextcloudRequest nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod("GET")
                    .setUrl(Uri.encode(uri, "/"))
                    .setParameter(parameters)
                    .build();

            InputStream inputStream = this.nextcloudAPI.performNetworkRequest(nextcloudRequest);
            String responseString = convertStreamToString(inputStream);
            inputStream.close();
            JSONObject json = new JSONObject(responseString);
            return readEpisodeActionsFromJsonObject(json);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw new IllegalStateException(e);
        } catch (JSONException | MalformedURLException e) {
            e.printStackTrace();
            throw new SyncServiceException(e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new SyncServiceException(e);
        }
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public UploadChangesResponse uploadEpisodeActions(List<EpisodeAction> queuedEpisodeActions) {
        try {
            HashMap<String, List<String>> header = new HashMap<>();
            header.put("Content-Type", Collections.singletonList("application/json"));
            String body = createBody(queuedEpisodeActions.toString());
            NextcloudRequest nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod("POST")
                    .setUrl(Uri.encode("/index.php/apps/gpoddersync/episode_action/create", "/"))
                    .setHeader(header)
                    .setRequestBody(body)
                    .build();

            this.nextcloudAPI.performNetworkRequest(nextcloudRequest);
        } catch (NextcloudFilesAppAccountNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }


        return new NextcloudGpodderEpisodeActionPostResponse(now().getEpochSecond(), new HashMap<>());
    }

    private String createBody(String data) {
        return "{\"data\": \"" + data + "\"}";
    }

    @Override
    public void logout() {

    }

    private EpisodeActionChanges readEpisodeActionsFromJsonObject(@NonNull JSONObject object)
            throws JSONException {

        List<EpisodeAction> episodeActions = new ArrayList<>();

        long timestamp = object.getLong("timestamp");
        JSONArray jsonActions = object.getJSONArray("actions");
        for (int i = 0; i < jsonActions.length(); i++) {
            JSONObject jsonAction = jsonActions.getJSONObject(i);
            EpisodeAction episodeAction = EpisodeAction.readFromJsonObject(jsonAction);
            if (episodeAction != null) {
                episodeActions.add(episodeAction);
            }
        }
        return new EpisodeActionChanges(episodeActions, timestamp);
    }

    private SubscriptionChanges readSubscriptionChangesFromJsonObject(@NonNull JSONObject object)
            throws JSONException {

        List<String> added = new LinkedList<>();
        JSONArray jsonAdded = object.getJSONArray("add");
        for (int i = 0; i < jsonAdded.length(); i++) {
            String addedUrl = jsonAdded.getString(i);
            // gpodder escapes colons unnecessarily
            addedUrl = addedUrl.replace("%3A", ":");
            added.add(addedUrl);
        }

        List<String> removed = new LinkedList<>();
        JSONArray jsonRemoved = object.getJSONArray("remove");
        for (int i = 0; i < jsonRemoved.length(); i++) {
            String removedUrl = jsonRemoved.getString(i);
            // gpodder escapes colons unnecessarily
            removedUrl = removedUrl.replace("%3A", ":");
            removed.add(removedUrl);
        }

        long timestamp = object.getLong("timestamp");
        return new SubscriptionChanges(added, removed, timestamp);
    }

    private static class NextcloudGpodderEpisodeActionPostResponse extends UploadChangesResponse {
        public NextcloudGpodderEpisodeActionPostResponse(long epochSecond, HashMap<Object, Object> objectObjectHashMap) {
            super(epochSecond);
        }
    }
}

