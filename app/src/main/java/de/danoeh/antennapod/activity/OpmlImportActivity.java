package de.danoeh.antennapod.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.asynctask.OpmlFeedQueuer;
import de.danoeh.antennapod.asynctask.OpmlImportWorker;
import de.danoeh.antennapod.core.export.opml.OpmlElement;
import de.danoeh.antennapod.core.preferences.UserPreferences;

import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;

/**
 * Activity for Opml Import.
 * */
public class OpmlImportActivity extends AppCompatActivity {
    private static final String TAG = "OpmlImportBaseActivity";
    @Nullable private Uri uri;
    private final ActivityResultLauncher<Intent> opmlFeedChooserLauncher =
            registerForActivityResult(new StartActivityForResult(), this::opmlFeedChooserResult);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(UserPreferences.getTheme());
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        Uri uri = getIntent().getData();
        if (uri != null && uri.toString().startsWith("/")) {
            uri = Uri.parse("file://" + uri.toString());
        } else {
            String extraText = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            if (extraText != null) {
                uri = Uri.parse(extraText);
            }
        }
        importUri(uri);
    }

    /**
     * Handles the choices made by the user in the OpmlFeedChooserActivity and
     * starts the OpmlFeedQueuer if necessary.
     */
    private void opmlFeedChooserResult(final ActivityResult result) {
        Log.d(TAG, "Received result");
        if (result.getResultCode() == RESULT_CANCELED) {
            Log.d(TAG, "Activity was cancelled");
            finish();
        } else {
            if (result.getData() != null) {
                int[] selected = result.getData().getIntArrayExtra(OpmlFeedChooserActivity.EXTRA_SELECTED_ITEMS);
                if (selected != null && selected.length > 0) {
                    OpmlFeedQueuer queuer = new OpmlFeedQueuer(this, selected) {

                        @Override
                        protected void onPostExecute(Void result) {
                            super.onPostExecute(result);
                            Intent intent = new Intent(OpmlImportActivity.this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }

                    };
                    queuer.executeAsync();
                } else {
                    Log.d(TAG, "No items were selected");
                }
            }
        }
    }

    void importUri(@Nullable Uri uri) {
        if (uri == null) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.opml_import_error_no_file)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        this.uri = uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && uri.toString().contains(Environment.getExternalStorageDirectory().toString())) {
            int permission = ActivityCompat.checkSelfPermission(this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                requestPermission();
                return;
            }
        }
        startImport();
    }

    private void requestPermission() {
        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startImport();
                } else {
                    new AlertDialog.Builder(this)
                            .setMessage(R.string.opml_import_ask_read_permission)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> requestPermission())
                            .setNegativeButton(R.string.cancel_label, (dialog, which) -> finish())
                            .show();
                }
            });

    /** Starts the import process. */
    private void startImport() {
        try {
            InputStream opmlFileStream = getContentResolver().openInputStream(uri);
            BOMInputStream bomInputStream = new BOMInputStream(opmlFileStream);
            ByteOrderMark bom = bomInputStream.getBOM();
            String charsetName = (bom == null) ? "UTF-8" : bom.getCharsetName();
            Reader reader = new InputStreamReader(bomInputStream, charsetName);

            OpmlImportWorker importWorker = new OpmlImportWorker(this, reader) {

                @Override
                protected void onPostExecute(ArrayList<OpmlElement> result) {
                    super.onPostExecute(result);
                    if (result != null) {
                        Log.d(TAG, "Parsing was successful");
                        OpmlImportHolder.setReadElements(result);
                        opmlFeedChooserLauncher.launch(new Intent(
                                OpmlImportActivity.this,
                                OpmlFeedChooserActivity.class));
                    } else {
                        Log.d(TAG, "Parser error occurred");
                    }
                }
            };
            importWorker.executeAsync();
        } catch (Exception e) {
            Log.d(TAG, Log.getStackTraceString(e));
            String message = getString(R.string.opml_reader_error);
            new AlertDialog.Builder(this)
                    .setMessage(message + " " + e.getMessage())
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }
}
