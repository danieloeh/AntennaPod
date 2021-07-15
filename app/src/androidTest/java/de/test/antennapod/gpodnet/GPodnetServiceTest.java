package de.test.antennapod.gpodnet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.danoeh.antennapod.core.service.download.AntennapodHttpClient;
import de.danoeh.antennapod.net.sync.gpoddernet.GpodnetService;
import de.danoeh.antennapod.net.sync.gpoddernet.GpodnetServiceException;
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetDevice;
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetTag;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import static java.util.Collections.singletonList;

/**
 * Test class for GpodnetService
 */
@Ignore("???")
@RunWith(AndroidJUnit4.class)
public class GPodnetServiceTest {

    private GpodnetService service;

    private static final String USER = "";
    private static final String PW = "";
    private static final String DEVICE_ID = "radio";

    @Before
    public void setUp() {
        service = new GpodnetService(AntennapodHttpClient.getHttpClient(),
                GpodnetService.DEFAULT_BASE_HOST, DEVICE_ID, USER, PW);
    }

    private void authenticate() throws GpodnetServiceException {
        service.login();
    }

    @Test
    public void testUploadSubscription() throws GpodnetServiceException {
        authenticate();
        ArrayList<String> l = new ArrayList<>();
        l.add("http://bitsundso.de/feed");
        service.uploadSubscriptions(DEVICE_ID, l);
    }

    @Test
    public void testUploadSubscription2() throws GpodnetServiceException {
        authenticate();
        ArrayList<String> l = new ArrayList<>();
        l.add("http://bitsundso.de/feed");
        l.add("http://gamesundso.de/feed");
        service.uploadSubscriptions(DEVICE_ID, l);
    }

    @Test
    public void testUploadChanges() throws GpodnetServiceException {
        authenticate();
        String[] URLS = {"http://bitsundso.de/feed", "http://gamesundso.de/feed", "http://cre.fm/feed/mp3/", "http://freakshow.fm/feed/m4a/"};
        List<String> subscriptions = Arrays.asList(URLS[0], URLS[1]);
        List<String> removed = singletonList(URLS[0]);
        List<String> added = Arrays.asList(URLS[2], URLS[3]);
        service.uploadSubscriptions(DEVICE_ID, subscriptions);
        service.uploadSubscriptionChanges(added, removed);
    }

    @Test
    public void testGetSubscriptionChanges() throws GpodnetServiceException {
        authenticate();
        service.getSubscriptionChanges(1362322610L);
    }

    @Test
    public void testGetSubscriptionsOfUser()
            throws GpodnetServiceException {
        authenticate();
        service.getSubscriptionsOfUser();
    }

    @Test
    public void testGetSubscriptionsOfDevice()
            throws GpodnetServiceException {
        authenticate();
        service.getSubscriptionsOfDevice(DEVICE_ID);
    }

    @Test
    public void testConfigureDevices() throws GpodnetServiceException {
        authenticate();
        service.configureDevice("foo", "This is an updated caption",  GpodnetDevice.DeviceType.LAPTOP);
    }

    @Test
    public void testGetDevices() throws GpodnetServiceException {
        authenticate();
        service.getDevices();
    }

    @Test
    public void testGetSuggestions() throws GpodnetServiceException {
        authenticate();
        service.getSuggestions(10);
    }

    @Test
    public void testTags() throws GpodnetServiceException {
        service.getTopTags(20);
    }

    @Test
    public void testPodcastForTags() throws GpodnetServiceException {
        List<GpodnetTag> tags = service.getTopTags(20);
        service.getPodcastsForTag(tags.get(1),
                10);
    }

    @Test
    public void testSearch() throws GpodnetServiceException {
        service.searchPodcasts("linux", 64);
    }

    @Test
    public void testToplist() throws GpodnetServiceException {
        service.getPodcastToplist(10);
    }
}
