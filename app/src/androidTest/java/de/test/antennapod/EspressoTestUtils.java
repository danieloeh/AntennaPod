package de.test.antennapod;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.espresso.PerformException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.contrib.DrawerActions;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.util.HumanReadables;
import androidx.test.espresso.util.TreeIterables;
import android.view.View;

import junit.framework.AssertionFailedError;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.playback.PlaybackService;
import de.danoeh.antennapod.core.storage.PodDBAdapter;
import de.danoeh.antennapod.dialog.RatingDialog;
import de.danoeh.antennapod.fragment.NavDrawerFragment;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.hamcrest.Matcher;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;

public class EspressoTestUtils {
    /**
     * Perform action of waiting for a specific view id.
     * https://stackoverflow.com/a/49814995/
     * @param viewMatcher The view to wait for.
     * @param millis The timeout of until when to wait for.
     */
    public static ViewAction waitForView(final Matcher<View> viewMatcher, final long millis) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isRoot();
            }

            @Override
            public String getDescription() {
                return "wait for a specific view for " + millis + " millis.";
            }

            @Override
            public void perform(final UiController uiController, final View view) {
                uiController.loopMainThreadUntilIdle();
                final long startTime = System.currentTimeMillis();
                final long endTime = startTime + millis;

                do {
                    for (View child : TreeIterables.breadthFirstViewTraversal(view)) {
                        // found view with required ID
                        if (viewMatcher.matches(child)) {
                            return;
                        }
                    }

                    uiController.loopMainThreadForAtLeast(50);
                } while (System.currentTimeMillis() < endTime);

                // timeout happens
                throw new PerformException.Builder()
                        .withActionDescription(this.getDescription())
                        .withViewDescription(HumanReadables.describe(view))
                        .withCause(new TimeoutException())
                        .build();
            }
        };
    }

    /**
     * Wait until a certain view becomes visible, but at the longest until the timeout.
     * Unlike {@link #waitForView(Matcher, long)} it doesn't stick to the initial root view.
     *
     * @param viewMatcher The view to wait for.
     * @param timeoutMillis Maximum waiting period in milliseconds.
     * @throws Exception Throws an Exception in case of a timeout.
     */
    public static void waitForViewGlobally(@NonNull Matcher<View> viewMatcher, long timeoutMillis) throws Exception {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeoutMillis;

        do {
            try {
                onView(viewMatcher).check(matches(isDisplayed()));
                // no Exception thrown -> check successful
                return;
            } catch (NoMatchingViewException | AssertionFailedError ignore) {
                // check was not successful "not found" -> continue waiting
            }
            //noinspection BusyWait
            Thread.sleep(50);
        } while (System.currentTimeMillis() < endTime);

        throw new Exception("Timeout after " + timeoutMillis + " ms");
    }

    /**
     * Perform action of waiting for a specific view id.
     * https://stackoverflow.com/a/30338665/
     * @param id The id of the child to click.
     */
    public static ViewAction clickChildViewWithId(final @IdRes int id) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return null;
            }

            @Override
            public String getDescription() {
                return "Click on a child view with specified id.";
            }

            @Override
            public void perform(UiController uiController, View view) {
                View v = view.findViewById(id);
                v.performClick();
            }
        };
    }

    /**
     * Clear all app databases.
     */
    public static void clearPreferences() {
        File root = InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir().getParentFile();
        String[] sharedPreferencesFileNames = new File(root, "shared_prefs").list();
        for (String fileName : sharedPreferencesFileNames) {
            System.out.println("Cleared database: " + fileName);
            InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences(
                    fileName.replace(".xml", ""), Context.MODE_PRIVATE).edit().clear().commit();
        }

        InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(MainActivity.PREF_IS_FIRST_LAUNCH, false)
                .commit();

        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getInstrumentation().getTargetContext())
                .edit()
                .putString(UserPreferences.PREF_UPDATE_INTERVAL, "0")
                .commit();

        RatingDialog.init(InstrumentationRegistry.getInstrumentation().getTargetContext());
        RatingDialog.saveRated();
    }

    public static void setLastNavFragment(String tag) {
        InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSharedPreferences(NavDrawerFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(NavDrawerFragment.PREF_LAST_FRAGMENT_TAG, tag)
                .commit();
    }

    public static void clearDatabase() {
        PodDBAdapter.init(InstrumentationRegistry.getInstrumentation().getTargetContext());
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
    }

    public static void clickPreference(@StringRes int title) {
        onView(withId(R.id.recycler_view)).perform(
                RecyclerViewActions.actionOnItem(
                        allOf(hasDescendant(withText(title)),
                                hasDescendant(withId(android.R.id.widget_frame))),
                        click()));
    }

    public static void openNavDrawer() {
        onView(isRoot()).perform(waitForView(withId(R.id.drawer_layout), 1000));
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open());
    }

    public static ViewInteraction onDrawerItem(Matcher<View> viewMatcher) {
        return onView(allOf(viewMatcher, withId(R.id.txtvTitle)));
    }

    public static void tryKillPlaybackService() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.stopService(new Intent(context, PlaybackService.class));
        try {
            // Android has no reliable way to stop a service instantly.
            // Calling stopSelf marks allows the system to destroy the service but the actual call
            // to onDestroy takes until the next GC of the system, which we can not influence.
            // Try to wait for the service at least a bit.
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> !PlaybackService.isRunning);
        } catch (ConditionTimeoutException e) {
            e.printStackTrace();
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    public static void tryKillDownloadService() {
        /*Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.stopService(new Intent(context, DownloadService.class));
        try {
            // Android has no reliable way to stop a service instantly.
            // Calling stopSelf marks allows the system to destroy the service but the actual call
            // to onDestroy takes until the next GC of the system, which we can not influence.
            // Try to wait for the service at least a bit.
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> !DownloadService.isRunning);
        } catch (ConditionTimeoutException e) {
            e.printStackTrace();
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();*/
    }

    public static Matcher<View> actionBarOverflow() {
        return allOf(isDisplayed(), withContentDescription("More options"));
    }
}
