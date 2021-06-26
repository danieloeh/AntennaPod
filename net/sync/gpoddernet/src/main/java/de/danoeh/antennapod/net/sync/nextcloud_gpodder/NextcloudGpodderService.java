package de.danoeh.antennapod.net.sync.nextcloud_gpodder;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.google.gson.GsonBuilder;
import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.aidl.NextcloudRequest;
import com.nextcloud.android.sso.api.NextcloudAPI;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException;
import com.nextcloud.android.sso.exceptions.NoCurrentAccountSelectedException;
import com.nextcloud.android.sso.helper.SingleAccountHelper;
import com.nextcloud.android.sso.model.SingleSignOnAccount;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import de.danoeh.antennapod.net.sync.gpoddernet.mapper.ResponseMapper;
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetUploadChangesResponse;
import de.danoeh.antennapod.net.sync.model.EpisodeAction;
import de.danoeh.antennapod.net.sync.model.EpisodeActionChanges;
import de.danoeh.antennapod.net.sync.model.ISyncService;
import de.danoeh.antennapod.net.sync.model.SubscriptionChanges;
import de.danoeh.antennapod.net.sync.model.SyncServiceException;
import de.danoeh.antennapod.net.sync.model.UploadChangesResponse;

import static java.time.Instant.now;

public class NextcloudGpodderService implements ISyncService {

    private final Context mContext;
    private NextcloudAPI nextcloudAPI;

    public NextcloudGpodderService(Context mContext) {
        this.mContext = mContext;
    }

    @Override
    public boolean isAuthenticated() {
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
    public void login() {
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
            String responseString = ResponseMapper.convertStreamToString(inputStream);
            JSONObject json = new JSONObject(responseString);
            inputStream.close();
            return ResponseMapper.readSubscriptionChangesFromJsonObject(json);
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
    public UploadChangesResponse uploadSubscriptionChanges(List<String> addedFeeds, List<String> removedFeeds) {
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
            String responseString = ResponseMapper.convertStreamToString(inputStream);
            inputStream.close();
            JSONObject json = new JSONObject(responseString);
            return ResponseMapper.readEpisodeActionsFromJsonObject(json);
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


        return new NextcloudGpodderEpisodeActionPostResponse(now().getEpochSecond());
    }

    private String createBody(String data) {
        return "{\"data\": \"" + data + "\"}";
    }

    @Override
    public void logout() {

    }

    private static class NextcloudGpodderEpisodeActionPostResponse extends UploadChangesResponse {
        public NextcloudGpodderEpisodeActionPostResponse(long epochSecond) {
            super(epochSecond);
        }
    }
}

