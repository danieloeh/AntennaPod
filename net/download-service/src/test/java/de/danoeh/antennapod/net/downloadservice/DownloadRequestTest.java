package de.danoeh.antennapod.net.downloadservice;

import android.os.Bundle;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;

import de.danoeh.antennapod.model.feed.FeedFile;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class DownloadRequestTest {

    @Test
    public void parcelInArrayListTest_WithAuth() {
        doTestParcelInArrayList("case has authentication",
                "usr1", "pass1", "usr2", "pass2");
    }

    @Test
    public void parcelInArrayListTest_NoAuth() {
        doTestParcelInArrayList("case no authentication",
                null, null, null, null);
    }

    @Test
    public void parcelInArrayListTest_MixAuth() {
        doTestParcelInArrayList("case mixed authentication",
                null, null, "usr2", "pass2");
    }

    // Test to ensure parcel using put/getParcelableArrayList() API work
    // based on: https://stackoverflow.com/a/13507191
    private void doTestParcelInArrayList(String message,
                                         String username1, String password1,
                                         String username2, String password2) {
        ArrayList<DownloadRequest> toParcel;
        { // test DownloadRequests to parcel
            String destStr = "file://location/media.mp3";
            FeedFile item1 = createFeedItem(1);
            Bundle arg1 = new Bundle();
            arg1.putString("arg1", "value1");
            DownloadRequest request1 = new DownloadRequest.Builder(destStr, item1, false)
                    .withAuthentication(username1, password1)
                    .withArguments(arg1)
                    .build();

            FeedFile item2 = createFeedItem(2);
            DownloadRequest request2 = new DownloadRequest.Builder(destStr, item2, true)
                    .withAuthentication(username2, password2)
                    .build();

            toParcel = new ArrayList<>();
            toParcel.add(request1);
            toParcel.add(request2);
        }

        // parcel the download requests
        Bundle bundleIn = new Bundle();
        bundleIn.putParcelableArrayList("r", toParcel);

        Parcel parcel = Parcel.obtain();
        bundleIn.writeToParcel(parcel, 0);

        Bundle bundleOut = new Bundle();
        bundleOut.setClassLoader(DownloadRequest.class.getClassLoader());
        parcel.setDataPosition(0); // to read the parcel from the beginning.
        bundleOut.readFromParcel(parcel);

        ArrayList<DownloadRequest> fromParcel = bundleOut.getParcelableArrayList("r");

        // spot-check contents to ensure they are the same
        // DownloadRequest.equals() implementation doesn't quite work
        // for DownloadRequest.argument (a Bundle)
        assertEquals(message + " - size", toParcel.size(), fromParcel.size());
        assertEquals(message + " - source", toParcel.get(1).getSource(), fromParcel.get(1).getSource());
        assertEquals(message + " - password", toParcel.get(0).getPassword(), fromParcel.get(0).getPassword());
        assertEquals(message + " - argument", toString(toParcel.get(0).getArguments()),
                toString(fromParcel.get(0).getArguments()));
    }

    private static String toString(Bundle b) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (String key: b.keySet()) {
            Object val = b.get(key);
            sb.append("(").append(key).append(":").append(val).append(") ");
        }
        sb.append("}");
        return sb.toString();
    }

    private FeedFile createFeedItem(final int id) {
        // Use mockito would be less verbose, but it'll take extra 1 second for this tiny test
        return new FeedFile() {
            @Override
            public long getId() {
                return id;
            }

            @Override
            public String getDownload_url() {
                return "http://example.com/episode" + id;
            }

            @Override
            public int getTypeAsInt() {
                return 0;
            }

            @Override
            public String getHumanReadableIdentifier() {
                return "human-id-" + id;
            }
        };
    }
}
