package de.danoeh.antennapod.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.snackbar.Snackbar;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences;
import de.danoeh.antennapod.core.service.playback.PlaybackService;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.util.Converter;
import de.danoeh.antennapod.core.util.playback.PlaybackController;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

import java.util.concurrent.TimeUnit;

public class SleepTimerDialog extends DialogFragment {
    private PlaybackController controller;
    private Disposable timeUpdater;

    private EditText etxtTime;
    private Spinner spTimeUnit;
    private LinearLayout timeSetup;
    private LinearLayout timeDisplay;
    private TextView time;

    public SleepTimerDialog() {

    }

    @Override
    public void onStart() {
        super.onStart();
        controller = new PlaybackController(getActivity()) {
            @Override
            public void onSleepTimerUpdate() {
                updateTime();
            }

            @Override
            public void loadMediaInfo() {
                updateTime();
            }
        };
        controller.init();
        timeUpdater = Observable.interval(1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(tick -> updateTime());
    }

    @Override
    public void onStop() {
        super.onStop();
        if (controller != null) {
            controller.release();
        }
        if (timeUpdater != null) {
            timeUpdater.dispose();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View content = View.inflate(getContext(), R.layout.time_dialog, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.sleep_timer_label);
        builder.setView(content);
        builder.setPositiveButton(R.string.close_label, null);

        etxtTime = content.findViewById(R.id.etxtTime);
        spTimeUnit = content.findViewById(R.id.spTimeUnit);
        timeSetup = content.findViewById(R.id.timeSetup);
        timeDisplay = content.findViewById(R.id.timeDisplay);
        time = content.findViewById(R.id.time);
        Button extendSleepFiveMinutesButton = content.findViewById(R.id.extendSleepFiveMinutesButton);
        extendSleepFiveMinutesButton.setText(getString(R.string.extend_sleep_timer_label, 5));
        Button extendSleepTenMinutesButton = content.findViewById(R.id.extendSleepTenMinutesButton);
        extendSleepTenMinutesButton.setText(getString(R.string.extend_sleep_timer_label, 10));
        Button extendSleepTwentyMinutesButton = content.findViewById(R.id.extendSleepTwentyMinutesButton);
        extendSleepTwentyMinutesButton.setText(getString(R.string.extend_sleep_timer_label, 20));
        extendSleepFiveMinutesButton.setOnClickListener(v -> {
            if (controller != null) {
                controller.extendSleepTimer(5 * 1000 * 60);
            }
        });
        extendSleepTenMinutesButton.setOnClickListener(v -> {
            if (controller != null) {
                controller.extendSleepTimer(10 * 1000 * 60);
            }
        });
        extendSleepTwentyMinutesButton.setOnClickListener(v -> {
            if (controller != null) {
                controller.extendSleepTimer(20 * 1000 * 60);
            }
        });

        etxtTime.setText(SleepTimerPreferences.lastTimerValue());
        etxtTime.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(etxtTime, InputMethodManager.SHOW_IMPLICIT);
        }, 100);

        String[] spinnerContent = new String[]{
                getString(R.string.time_seconds),
                getString(R.string.time_minutes),
                getString(R.string.time_hours),
                getString(R.string.time_episodes)};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, spinnerContent);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTimeUnit.setAdapter(spinnerAdapter);
        spTimeUnit.setSelection(SleepTimerPreferences.lastTimerTimeUnit());

        CheckBox cbShakeToReset = content.findViewById(R.id.cbShakeToReset);
        CheckBox cbVibrate = content.findViewById(R.id.cbVibrate);
        CheckBox chAutoEnable = content.findViewById(R.id.chAutoEnable);

        cbShakeToReset.setChecked(SleepTimerPreferences.shakeToReset());
        cbVibrate.setChecked(SleepTimerPreferences.vibrate());
        chAutoEnable.setChecked(SleepTimerPreferences.autoEnable());

        cbShakeToReset.setOnCheckedChangeListener((buttonView, isChecked)
                -> SleepTimerPreferences.setShakeToReset(isChecked));
        cbVibrate.setOnCheckedChangeListener((buttonView, isChecked)
                -> SleepTimerPreferences.setVibrate(isChecked));
        chAutoEnable.setOnCheckedChangeListener((compoundButton, isChecked)
                -> SleepTimerPreferences.setAutoEnable(isChecked));

        Button disableButton = content.findViewById(R.id.disableSleeptimerButton);
        disableButton.setOnClickListener(v -> {
            if (controller != null) {
                controller.disableSleepTimer();
            }
        });
        Button setButton = content.findViewById(R.id.setSleeptimerButton);
        setButton.setOnClickListener(v -> {
            if (!PlaybackService.isRunning) {
                Snackbar.make(content, R.string.no_media_playing_label, Snackbar.LENGTH_LONG).show();
                return;
            }
            try {
                if (spTimeUnit.getSelectedItemPosition() >= 3) {
                    int timeValue = Integer.parseInt(etxtTime.getText().toString());
                    if (timeValue > DBReader.getQueueIDList().size()) {
                        Snackbar.make(content,
                                R.string.time_dialog_invalid_episodes_input, Snackbar.LENGTH_LONG).show();
                        return;
                    }
                }

                SleepTimerPreferences.setLastTimer(etxtTime.getText().toString(), spTimeUnit.getSelectedItemPosition());
                long time = SleepTimerPreferences.timerMillis();
                if (controller != null) {
                    if (SleepTimerPreferences.isEpisodesEnabled()) {
                        controller.setSleepTimerEpisodes((int) time);
                    } else {
                        controller.setSleepTimer(time);
                    }
                }
                closeKeyboard(content);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                Snackbar.make(content, R.string.time_dialog_invalid_input, Snackbar.LENGTH_LONG).show();
            }
        });
        return builder.create();
    }

    private void updateTime() {
        if (controller == null) {
            return;
        }
        timeSetup.setVisibility(controller.sleepTimerActive() ? View.GONE : View.VISIBLE);
        timeDisplay.setVisibility(controller.sleepTimerActive() ? View.VISIBLE : View.GONE);
        if (SleepTimerPreferences.isEpisodesEnabled()) {
            time.setText(controller.getSleepTimerTimeLeft() + " " + getString(R.string.time_episodes));
        } else {
            time.setText(Converter.getDurationStringLong((int) controller.getSleepTimerTimeLeft()));
        }
    }

    private void closeKeyboard(View content) {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(content.getWindowToken(), 0);
    }
}
