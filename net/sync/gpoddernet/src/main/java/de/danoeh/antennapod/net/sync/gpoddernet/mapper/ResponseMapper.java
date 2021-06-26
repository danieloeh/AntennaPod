package de.danoeh.antennapod.net.sync.gpoddernet.mapper;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import de.danoeh.antennapod.net.sync.model.EpisodeAction;
import de.danoeh.antennapod.net.sync.model.EpisodeActionChanges;
import de.danoeh.antennapod.net.sync.model.SubscriptionChanges;

public class ResponseMapper {
    public static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public static SubscriptionChanges readSubscriptionChangesFromJsonObject(@NonNull JSONObject object)
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

    public static EpisodeActionChanges readEpisodeActionsFromJsonObject(@NonNull JSONObject object)
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
}
