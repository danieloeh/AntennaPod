package de.danoeh.antennapod.core.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test class for {@link UrlComparer}
 */
@RunWith(RobolectricTestRunner.class)
public class UrlComparerTest {
    @Test
    public void testUrlEqualsSame() {
        assertTrue(UrlComparer.urlEquals("https://www.example.com/test", "https://www.example.com/test"));
        assertTrue(UrlComparer.urlEquals("https://www.example.com/test", "https://www.example.com/test/"));
        assertTrue(UrlComparer.urlEquals("https://www.example.com/test", "https://www.example.com//test"));
        assertTrue(UrlComparer.urlEquals("https://www.example.com", "https://www.example.com/"));
        assertTrue(UrlComparer.urlEquals("https://www.example.com", "http://www.example.com"));
        assertTrue(UrlComparer.urlEquals("http://www.example.com/", "https://www.example.com/"));
        assertTrue(UrlComparer.urlEquals("https://www.example.com/?id=42", "https://www.example.com/?id=42"));
        assertTrue(UrlComparer.urlEquals("https://example.com/podcast%20test", "https://example.com/podcast test"));
        assertTrue(UrlComparer.urlEquals("https://example.com/?a=podcast%20test", "https://example.com/?a=podcast test"));
        assertTrue(UrlComparer.urlEquals("https://example.com/?", "https://example.com/"));
        assertTrue(UrlComparer.urlEquals("https://example.com/?", "https://example.com"));
        assertTrue(UrlComparer.urlEquals("https://Example.com", "https://example.com"));
        assertTrue(UrlComparer.urlEquals("https://example.com/test", "https://example.com/Test"));
    }

    @Test
    public void testUrlEqualsDifferent() {
        assertFalse(UrlComparer.urlEquals("https://www.example.com/test", "https://www.example2.com/test"));
        assertFalse(UrlComparer.urlEquals("https://www.example.com/test", "https://www.example.de/test"));
        assertFalse(UrlComparer.urlEquals("https://example.com/", "https://otherpodcast.example.com/"));
        assertFalse(UrlComparer.urlEquals("https://www.example.com/?id=42&a=b", "https://www.example.com/?id=43&a=b"));
        assertFalse(UrlComparer.urlEquals("https://example.com/podcast%25test", "https://example.com/podcast test"));
    }
}
