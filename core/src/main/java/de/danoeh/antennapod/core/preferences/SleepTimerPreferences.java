package de.danoeh.antennapod.core.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import android.util.Log;

import java.util.concurrent.TimeUnit;

public class SleepTimerPreferences {

    private static final String TAG = "SleepTimerPreferences";

    private static final String PREF_NAME = "SleepTimerDialog";
    private static final String PREF_VALUE = "LastValue";
    private static final String PREF_TIME_UNIT = "LastTimeUnit";
    private static final String PREF_VIBRATE = "Vibrate";
    private static final String PREF_SHAKE_TO_RESET = "ShakeToReset";
    private static final String PREF_SHAKE_BEHAVIOUR = "lastShakeBehaviour";
    private static final String PREF_AUTO_ENABLE = "AutoEnable";

    private static final TimeUnit[] UNITS = { TimeUnit.SECONDS, TimeUnit.MINUTES, TimeUnit.HOURS };

    private static final String DEFAULT_VALUE = "15";
    private static final int DEFAULT_TIME_UNIT = 1;

    private static final int SHAKE_BEHAVIOUR_FADEOUT = 0;
    private static final int SHAKE_BEHAVIOUR_ANYTIME = 1;

    private static final int DEFAULT_SHAKE_BEHAVIOUR = SHAKE_BEHAVIOUR_FADEOUT;

    private static SharedPreferences prefs;

    /**
     * Sets up the UserPreferences class.
     *
     * @throws IllegalArgumentException if context is null
     */
    public static void init(@NonNull Context context) {
        Log.d(TAG, "Creating new instance of SleepTimerPreferences");
        SleepTimerPreferences.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void setLastTimer(String value, int timeUnit) {
        prefs.edit().putString(PREF_VALUE, value).putInt(PREF_TIME_UNIT, timeUnit).apply();
    }

    public static String lastTimerValue() {
        return prefs.getString(PREF_VALUE, DEFAULT_VALUE);
    }

    public static int lastTimerTimeUnit() {
        return prefs.getInt(PREF_TIME_UNIT, DEFAULT_TIME_UNIT);
    }

    public static long timerMillis() {
        long value = Long.parseLong(lastTimerValue());
        return UNITS[lastTimerTimeUnit()].toMillis(value);
    }

    public static void setVibrate(boolean vibrate) {
        prefs.edit().putBoolean(PREF_VIBRATE, vibrate).apply();
    }

    public static boolean vibrate() {
        return prefs.getBoolean(PREF_VIBRATE, true);
    }

    public static void setShakeToReset(boolean shakeToReset) {
        prefs.edit().putBoolean(PREF_SHAKE_TO_RESET, shakeToReset).apply();
    }

    public static void setShakeBehaviour(int shakeBehaviour) {
        prefs.edit().putInt(PREF_SHAKE_BEHAVIOUR, shakeBehaviour).apply();
    }

    public static boolean shakeToReset() {
        return prefs.getBoolean(PREF_SHAKE_TO_RESET, true);
    }

    public static int shakeBehaviour() {
        return prefs.getInt(PREF_SHAKE_BEHAVIOUR, DEFAULT_SHAKE_BEHAVIOUR);
    }

    public static boolean allowShake(boolean almostExpired) {
        return shakeToReset() && (almostExpired || (shakeBehaviour() == SHAKE_BEHAVIOUR_ANYTIME));
    }

    public static void setAutoEnable(boolean autoEnable) {
        prefs.edit().putBoolean(PREF_AUTO_ENABLE, autoEnable).apply();
    }

    public static boolean autoEnable() {
        return prefs.getBoolean(PREF_AUTO_ENABLE, false);
    }

}
