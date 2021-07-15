package de.danoeh.antennapod.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.core.export.opml.OpmlElement;
import de.danoeh.antennapod.core.preferences.UserPreferences;

/**
 * Displays the feeds that the OPML-Importer has read and lets the user choose
 * which feeds he wants to import.
 */
public class OpmlFeedChooserActivity extends AppCompatActivity {
    public static final String EXTRA_SELECTED_ITEMS = "de.danoeh.antennapod.selectedItems";
    private static final String TAG = "OpmlFeedChooserActivity";
    private Button butConfirm;
    private Button butCancel;
    private ListView feedlist;
    private ArrayAdapter<String> listAdapter;

    private MenuItem selectAll;
    private MenuItem deselectAll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(UserPreferences.getTheme());
        super.onCreate(savedInstanceState);

        setContentView(R.layout.opml_selection);
        butConfirm = findViewById(R.id.butConfirm);
        butCancel = findViewById(R.id.butCancel);
        feedlist = findViewById(R.id.feedlist);

        feedlist.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_multiple_choice,
                getTitleList());

        feedlist.setAdapter(listAdapter);
        feedlist.setOnItemClickListener((parent, view, position, id) -> {
            SparseBooleanArray checked = feedlist.getCheckedItemPositions();
            int checkedCount = 0;
            for (int i = 0; i < checked.size(); i++) {
                if (checked.valueAt(i)) {
                    checkedCount++;
                }
            }
            if(checkedCount == listAdapter.getCount()) {
                selectAll.setVisible(false);
                deselectAll.setVisible(true);
            } else {
                deselectAll.setVisible(false);
                selectAll.setVisible(true);
            }
        });

        butCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        butConfirm.setOnClickListener(v -> {
            Intent intent = new Intent();
            SparseBooleanArray checked = feedlist.getCheckedItemPositions();

            int checkedCount = 0;
            // Get number of checked items
            for (int i = 0; i < checked.size(); i++) {
                if (checked.valueAt(i)) {
                    checkedCount++;
                }
            }
            int[] selection = new int[checkedCount];
            for (int i = 0, collected = 0; collected < checkedCount; i++) {
                if (checked.valueAt(i)) {
                    selection[collected] = checked.keyAt(i);
                    collected++;
                }
            }
            intent.putExtra(EXTRA_SELECTED_ITEMS, selection);
            setResult(RESULT_OK, intent);
            finish();
        });

    }

    private List<String> getTitleList() {
        List<String> result = new ArrayList<>();
        if (OpmlImportHolder.getReadElements() != null) {
            for (OpmlElement element : OpmlImportHolder.getReadElements()) {
                result.add(element.getText());
            }
        }
        return result;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.opml_selection_options, menu);
        selectAll = menu.findItem(R.id.select_all_item);
        deselectAll = menu.findItem(R.id.deselect_all_item);
        deselectAll.setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.select_all_item) {
            selectAll.setVisible(false);
            selectAllItems(true);
            deselectAll.setVisible(true);
            return true;
        } else if (itemId == R.id.deselect_all_item) {
            deselectAll.setVisible(false);
            selectAllItems(false);
            selectAll.setVisible(true);
            return true;
        }
        return false;
    }

    private void selectAllItems(boolean b) {
        for (int i = 0; i < feedlist.getCount(); i++) {
            feedlist.setItemChecked(i, b);
        }
    }

}
