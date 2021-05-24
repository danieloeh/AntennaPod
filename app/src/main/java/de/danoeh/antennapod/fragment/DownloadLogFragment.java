package de.danoeh.antennapod.fragment;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.ListFragment;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.google.android.material.snackbar.Snackbar;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.DownloadLogAdapter;
import de.danoeh.antennapod.core.event.DownloadLogEvent;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.download.DownloadStatus;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.util.download.AutoUpdateManager;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.downloadservice.DownloadRequest;
import de.danoeh.antennapod.net.downloadservice.DownloadRequester;
import de.danoeh.antennapod.net.downloadservice.DownloadWorker;
import de.danoeh.antennapod.view.EmptyViewHandler;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the download log
 */
public class DownloadLogFragment extends ListFragment {

    private static final String TAG = "DownloadLogFragment";

    private List<DownloadStatus> downloadLog = new ArrayList<>();
    private DownloadLogAdapter adapter;
    private Disposable disposable;

    @Override
    public void onStart() {
        super.onStart();
        loadDownloadLog();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // add padding
        final ListView lv = getListView();
        lv.setClipToPadding(false);
        final int vertPadding = getResources().getDimensionPixelSize(R.dimen.list_vertical_padding);
        lv.setPadding(0, vertPadding, 0, vertPadding);
        setListShown(true);

        EmptyViewHandler emptyView = new EmptyViewHandler(getActivity());
        emptyView.setIcon(R.drawable.ic_download);
        emptyView.setTitle(R.string.no_log_downloads_head_label);
        emptyView.setMessage(R.string.no_log_downloads_label);
        emptyView.attachToListView(getListView());

        adapter = new DownloadLogAdapter(getActivity(), this);
        setListAdapter(adapter);
        EventBus.getDefault().register(this);
        setupDownloaderUpdates();
    }

    @Override
    public void onDestroyView() {
        EventBus.getDefault().unregister(this);
        super.onDestroyView();
    }

    @Override
    public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        Object item = adapter.getItem(position);
        if (item instanceof DownloadRequest) {
            DownloadRequest downloadRequest = ((DownloadRequest) item);
            DownloadWorker.cancel(getContext(), downloadRequest.getSource());

            if (downloadRequest.getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA
                    && UserPreferences.isEnableAutodownload()) {
                FeedMedia media = DBReader.getFeedMedia(downloadRequest.getFeedfileId());
                DBWriter.setFeedItemAutoDownload(media.getItem(), false);

                ((MainActivity) getActivity()).showSnackbarAbovePlayer(
                        R.string.download_canceled_autodownload_enabled_msg, Toast.LENGTH_SHORT);
            }
        } else if (item instanceof DownloadStatus) {
            DownloadStatus status = (DownloadStatus) item;
            String url = "unknown";
            String message = getString(R.string.download_successful);
            if (status.getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
                FeedMedia media = DBReader.getFeedMedia(status.getFeedfileId());
                if (media != null) {
                    url = media.getDownload_url();
                }
            } else if (status.getFeedfileType() == Feed.FEEDFILETYPE_FEED) {
                Feed feed = DBReader.getFeed(status.getFeedfileId());
                if (feed != null) {
                    url = feed.getDownload_url();
                }
            }

            if (!status.isSuccessful()) {
                message = status.getReasonDetailed();
            }

            String messageFull = getString(R.string.download_error_details_message, message, url);
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.download_error_details);
            builder.setMessage(messageFull);
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setNeutralButton(R.string.copy_to_clipboard, (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager) getContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(getString(R.string.download_error_details), messageFull);
                clipboard.setPrimaryClip(clip);
                ((MainActivity) getActivity()).showSnackbarAbovePlayer(
                        R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT);
            });
            Dialog dialog = builder.show();
            ((TextView) dialog.findViewById(android.R.id.message)).setTextIsSelectable(true);
        }
    }

    @Subscribe
    public void onDownloadLogChanged(DownloadLogEvent event) {
        loadDownloadLog();
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        menu.findItem(R.id.episode_actions).setVisible(false);
        menu.findItem(R.id.clear_logs_item).setVisible(!downloadLog.isEmpty());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (super.onOptionsItemSelected(item)) {
            return true;
        } else if (item.getItemId() == R.id.clear_logs_item) {
            DBWriter.clearDownloadLog();
            return true;
        } else if (item.getItemId() == R.id.refresh_item) {
            DownloadRequester.getInstance().refreshAllFeeds(requireContext());
            return true;
        }
        return false;
    }

    private void setupDownloaderUpdates() {
        WorkManager.getInstance(getContext()).getWorkInfosByTagLiveData(DownloadRequest.TAG)
                .observe(getViewLifecycleOwner(), workInfos -> {
                    List<DownloadRequest> requests = new ArrayList<>();
                    for (WorkInfo workInfo : workInfos) {
                        if (!workInfo.getState().isFinished()) {
                            requests.add(DownloadRequest.from(workInfo.getProgress()));
                        }
                    }
                    adapter.setRunningDownloads(requests);
                });
    }

    private void loadDownloadLog() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Observable.fromCallable(DBReader::getDownloadLog)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result != null) {
                        downloadLog = result;
                        adapter.setDownloadLog(downloadLog);
                        ((PagedToolbarFragment) getParentFragment()).invalidateOptionsMenuIfActive(this);
                    }
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }
}
