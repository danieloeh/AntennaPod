package de.danoeh.antennapod.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.widget.ProgressBar;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.core.storage.PodDBAdapter;
import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Shows the AntennaPod logo while waiting for the main activity to start.
 */
public class SplashActivity extends AppCompatActivity {

    public static final String PREF_NAME = "SplashActivityPrefs";
    public static final String PREF_IS_FIRST_LAUNCH = "prefSplashActivityIsFirstLaunch";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash);

        ProgressBar progressBar = findViewById(R.id.progressBar);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Drawable wrapDrawable = DrawableCompat.wrap(progressBar.getIndeterminateDrawable());
            DrawableCompat.setTint(wrapDrawable, 0xffffffff);
            progressBar.setIndeterminateDrawable(DrawableCompat.unwrap(wrapDrawable));
        } else {
            progressBar.getIndeterminateDrawable().setColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN);
        }

        Completable.create(subscriber -> {
            // Trigger schema updates
            PodDBAdapter.getInstance().open();
            PodDBAdapter.getInstance().close();
            subscriber.onComplete();
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    Intent intent;
                    SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                            When user open the app for the 1st time, re-direct them to the
//                            OpenSettingsActivity.class, otherwise trigger MainActivity.class
                        if (prefs.getBoolean(PREF_IS_FIRST_LAUNCH, true)) {
                            intent = new Intent(SplashActivity.this,
                                    OpenSettingsActivity.class);
                        } else {
                            intent = new Intent(SplashActivity.this,
                                    MainActivity.class);
                        }
                        SharedPreferences.Editor edit = prefs.edit();
                        edit.putBoolean(PREF_IS_FIRST_LAUNCH, false);
                        edit.apply();
                    } else {
                        intent = new Intent(SplashActivity.this,
                                MainActivity.class);
                    }
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                }, error -> {
                    error.printStackTrace();
                    Toast.makeText(this, error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
    }
}
