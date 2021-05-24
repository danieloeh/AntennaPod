package de.test.antennapod.util.event;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.reactivex.functions.Consumer;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.List;

/**
 * Test helper to listen to {@link DownloadEvent} and handle them accordingly.
 */
public class DownloadEventListener {
    //private final List<DownloadEvent> events = new ArrayList<>();

    /**
     * Provides an listener subscribing to {@link DownloadEvent} that the callers can use.
     * Note: it uses RxJava's version of {@link Consumer} because it allows exceptions to be thrown.
     */
    public static void withDownloadEventListener(@NonNull Consumer<DownloadEventListener> consumer) throws Exception {
        DownloadEventListener feedItemEventListener = new DownloadEventListener();
        try {
            EventBus.getDefault().register(feedItemEventListener);
            consumer.accept(feedItemEventListener);
        } finally {
            EventBus.getDefault().unregister(feedItemEventListener);
        }
    }

    /*@Subscribe
    public void onEvent(DownloadEvent event) {
        events.add(event);
    }

    @Nullable
    public DownloadEvent getLatestEvent() {
        if (events.size() == 0) {
            return null;
        }
        return events.get(events.size() - 1);
    }*/
}
