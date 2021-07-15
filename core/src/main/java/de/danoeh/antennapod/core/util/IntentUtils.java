package de.danoeh.antennapod.core.util;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import de.danoeh.antennapod.core.R;

import java.util.List;

public class IntentUtils {
    private static final String TAG = "IntentUtils";

    private IntentUtils(){}

    /*
     *  Checks if there is at least one exported activity that can be performed for the intent
     */
    public static boolean isCallable(final Context context, final Intent intent) {
        // TODO: Deal with https://developer.android.com/about/versions/11/privacy/package-visibility
        @SuppressLint("QueryPermissionsNeeded")
        List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        for(ResolveInfo info : list) {
            if(info.activityInfo.exported) {
                return true;
            }
        }
        return false;
    }

    public static void sendLocalBroadcast(Context context, String action) {
        context.sendBroadcast(new Intent(action).setPackage(context.getPackageName()));
    }

    public static void openInBrowser(Context context, String url) {
        try {
            Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(myIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.pref_no_browser_found, Toast.LENGTH_LONG).show();
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }
}
