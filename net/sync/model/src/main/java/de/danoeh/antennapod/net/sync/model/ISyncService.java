package de.danoeh.antennapod.net.sync.model;

import java.util.List;

public interface ISyncService {

    boolean isAuthenticated();

    void login() throws SyncServiceException;

    SubscriptionChanges getSubscriptionChanges(long lastSync) throws SyncServiceException;

    UploadChangesResponse uploadSubscriptionChanges(
            List<String> addedFeeds, List<String> removedFeeds) throws SyncServiceException;

    EpisodeActionChanges getEpisodeActionChanges(long lastSync) throws SyncServiceException;

    UploadChangesResponse uploadEpisodeActions(List<EpisodeAction> queuedEpisodeActions)
            throws SyncServiceException;

    void logout() throws SyncServiceException;
}
