package de.danoeh.antennapod.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.util.Converter;
import de.danoeh.antennapod.core.util.playback.PlaybackController;
import de.danoeh.antennapod.view.PlaybackSpeedSeekBar;

import java.util.List;
import java.util.Locale;

public class PlaybackControlsDialog extends DialogFragment {
    private PlaybackController controller;
    private AlertDialog dialog;

    public static PlaybackControlsDialog newInstance() {
        Bundle arguments = new Bundle();
        PlaybackControlsDialog dialog = new PlaybackControlsDialog();
        dialog.setArguments(arguments);
        return dialog;
    }

    public PlaybackControlsDialog() {
        // Empty constructor required for DialogFragment
    }

    @Override
    public void onStart() {
        super.onStart();
        controller = new PlaybackController(getActivity()) {
            @Override
            public void loadMediaInfo() {
                setupUi();
                setupAudioTracks();
            }
        };
        controller.init();
        setupUi();
    }

    @Override
    public void onStop() {
        super.onStop();
        controller.release();
        controller = null;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        dialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.audio_controls)
                .setView(R.layout.audio_controls)
                .setPositiveButton(R.string.close_label, (dialog1, which) -> {
                    final SeekBar left = dialog.findViewById(R.id.volume_left);
                    final SeekBar right = dialog.findViewById(R.id.volume_right);
                    UserPreferences.setVolume(left.getProgress(), right.getProgress());
                }).create();
        return dialog;
    }

    private void setupUi() {
        final TextView txtvPlaybackSpeed = dialog.findViewById(R.id.txtvPlaybackSpeed);

        PlaybackSpeedSeekBar speedSeekBar = dialog.findViewById(R.id.speed_seek_bar);
        speedSeekBar.setController(controller);
        speedSeekBar.setProgressChangedListener(speed
                -> txtvPlaybackSpeed.setText(String.format(Locale.getDefault(), "%.2fx", speed)));

        final SeekBar barLeftVolume = dialog.findViewById(R.id.volume_left);
        barLeftVolume.setProgress(UserPreferences.getLeftVolumePercentage());
        final SeekBar barRightVolume = dialog.findViewById(R.id.volume_right);
        barRightVolume.setProgress(UserPreferences.getRightVolumePercentage());
        final CheckBox stereoToMono = dialog.findViewById(R.id.stereo_to_mono);
        stereoToMono.setChecked(UserPreferences.stereoToMono());
        final CheckBox repeatEpisode = dialog.findViewById(R.id.repeat_episode);
        repeatEpisode.setChecked(UserPreferences.repeatEpisode());
        if (controller != null && !controller.canDownmix()) {
            stereoToMono.setEnabled(false);
            String sonicOnly = getString(R.string.sonic_only);
            stereoToMono.setText(getString(R.string.stereo_to_mono) + " [" + sonicOnly + "]");
        }

        if (UserPreferences.useExoplayer()) {
            barRightVolume.setEnabled(false);
        }

        final CheckBox skipSilence = dialog.findViewById(R.id.skipSilence);
        skipSilence.setChecked(UserPreferences.isSkipSilence());
        if (!UserPreferences.useExoplayer()) {
            skipSilence.setEnabled(false);
            String exoplayerOnly = getString(R.string.exoplayer_only);
            skipSilence.setText(getString(R.string.pref_skip_silence_title) + " [" + exoplayerOnly + "]");
        }
        skipSilence.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UserPreferences.setSkipSilence(isChecked);
            controller.setSkipSilence(isChecked);
        });

        barLeftVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                controller.setVolume(
                        Converter.getVolumeFromPercentage(progress),
                        Converter.getVolumeFromPercentage(barRightVolume.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        barRightVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                controller.setVolume(
                        Converter.getVolumeFromPercentage(barLeftVolume.getProgress()),
                        Converter.getVolumeFromPercentage(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        stereoToMono.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UserPreferences.stereoToMono(isChecked);
            if (controller != null) {
                controller.setDownmix(isChecked);
            }
        });
        repeatEpisode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UserPreferences.repeatEpisode(isChecked);
        });
    }

    private void setupAudioTracks() {
        List<String> audioTracks = controller.getAudioTracks();
        int selectedAudioTrack = controller.getSelectedAudioTrack();
        final Button butAudioTracks = dialog.findViewById(R.id.audio_tracks);
        if (audioTracks.size() < 2 || selectedAudioTrack < 0) {
            butAudioTracks.setVisibility(View.GONE);
            return;
        }

        butAudioTracks.setVisibility(View.VISIBLE);
        butAudioTracks.setText(audioTracks.get(selectedAudioTrack));
        butAudioTracks.setOnClickListener(v -> {
            controller.setAudioTrack((selectedAudioTrack + 1) % audioTracks.size());
            new Handler(Looper.getMainLooper()).postDelayed(this::setupAudioTracks, 500);
        });
    }
}
