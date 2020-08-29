package de.danoeh.antennapod.asynctask;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import de.danoeh.antennapod.core.export.ExportWriter;
import de.danoeh.antennapod.core.storage.DBReader;
import io.reactivex.Observable;

/**
 * Writes an OPML file into the user selected export directory in the background.
 */
public class DocumentFileExportWorker {

    private final @NonNull ExportWriter exportWriter;
    private @NonNull Context context;
    private @NonNull Uri outputFileUri;

    public DocumentFileExportWorker(@NonNull ExportWriter exportWriter, @NonNull Context context, @NonNull Uri outputFileUri) {
        this.exportWriter = exportWriter;
        this.context = context;
        this.outputFileUri = outputFileUri;
    }

    public Observable<DocumentFile> exportObservable() {
        DocumentFile output = DocumentFile.fromSingleUri(context, outputFileUri);
        return Observable.create(subscriber -> {
            OutputStream outputStream = null;
            OutputStreamWriter writer = null;
            try {
                Uri uri = output.getUri();
                if (uri == null) {
                    throw new FileNotFoundException("Export file not found.");
                }
                outputStream = context.getContentResolver().openOutputStream(uri);
                if (outputStream == null) {
                    throw new IOException();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                } else {
                    writer = new OutputStreamWriter(outputStream, Charset.forName("UTF-8"));
                }
                exportWriter.writeDocument(DBReader.getFeedList(), writer, context);
                subscriber.onNext(output);
            } catch (IOException e) {
                subscriber.onError(e);
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException e) {
                        subscriber.onError(e);
                    }
                }
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        subscriber.onError(e);
                    }
                }
                subscriber.onComplete();
            }
        });
    }

}
