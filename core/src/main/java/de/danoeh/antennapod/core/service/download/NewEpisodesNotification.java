package de.danoeh.antennapod.core.service.download;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.core.R;
import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.FeedPreferences;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.util.gui.NotificationUtils;

public class NewEpisodesNotification {
    private final List<Long> feedIDs = new ArrayList<>();
    private final Map<Long, Long> lastItemsMap = new HashMap<>();

    private final Context context;

    public NewEpisodesNotification(Context context) {
        this.context = context;

        for (Feed feed : DBReader.getFeedList()) {
            FeedPreferences prefs = feed.getPreferences();
            if (prefs.getKeepUpdated() && prefs.getShowEpisodeNotification()) {
                List<FeedItem> outdatedFeedItems = DBReader.getFeedItemList(feed);
                if (!outdatedFeedItems.isEmpty()) {
                    FeedItem newestEpisode = outdatedFeedItems.get(0);

                    lastItemsMap.put(feed.getId(), newestEpisode.getId());
                }
                feedIDs.add(feed.getId());
            }
        }
    }

    public void showNotifications() {
        for (long feedId : feedIDs) {
            Feed feed = DBReader.getFeed(feedId);
            List<FeedItem> feedItems = DBReader.getFeedItemList(feed);

            int newEpisodes;
            if (lastItemsMap.containsKey(feedId)) {
                long lastKnownFeedItemIds = lastItemsMap.get(feedId);
                FeedItem lastKnownFeedItems = DBReader.getFeedItem(lastKnownFeedItemIds);

                newEpisodes = feedItems.indexOf(lastKnownFeedItems);
            } else {
                newEpisodes = feedItems.size();
            }

            if (newEpisodes > 0) {
                showNotification(newEpisodes, feed, context);
            }
        }
    }

    static private void showNotification(int newEpisodes, Feed feed, Context context) {
        Resources res = context.getResources();
        String text = res.getQuantityString(R.plurals.new_episode_message, newEpisodes, newEpisodes, feed.getTitle());

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(context, "de.danoeh.antennapod.activity.MainActivity"));

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("fragment_feed_id", feed.getId());
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID_EPISODE_NOTIFICATIONS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("New Episode")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(NotificationUtils.CHANNEL_ID_EPISODE_NOTIFICATIONS, feed.hashCode(), builder.build());
    }
}
