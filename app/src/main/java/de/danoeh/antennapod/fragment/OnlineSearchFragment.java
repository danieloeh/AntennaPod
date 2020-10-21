package de.danoeh.antennapod.fragment;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.activity.OnlineFeedViewActivity;
import de.danoeh.antennapod.adapter.itunes.ItunesAdapter;
import de.danoeh.antennapod.discovery.PodcastSearchResult;
import de.danoeh.antennapod.discovery.PodcastSearcher;
import de.danoeh.antennapod.discovery.PodcastSearcherRegistry;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;

import static android.view.View.INVISIBLE;

public class OnlineSearchFragment extends Fragment {

    private static final String TAG = "FyydSearchFragment";
    private static final String ARG_SEARCHER = "searcher";
    private static final String ARG_QUERY = "query";

    /**
     * Adapter responsible with the search results
     */
    private ItunesAdapter adapter;
    private PodcastSearcher searchProvider;
    private GridView gridView;
    private ProgressBar progressBar;
    private TextView txtvError;
    private Button butRetry;
    private TextView txtvEmpty;

    /**
     * List of podcasts retreived from the search
     */
    private List<PodcastSearchResult> searchResults;
    private Disposable disposable;

    public static OnlineSearchFragment newInstance(Class<? extends PodcastSearcher> searchProvider) {
        return newInstance(searchProvider, null);
    }

    public static OnlineSearchFragment newInstance(Class<? extends PodcastSearcher> searchProvider, String query) {
        OnlineSearchFragment fragment = new OnlineSearchFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_SEARCHER, searchProvider.getName());
        arguments.putString(ARG_QUERY, query);
        fragment.setArguments(arguments);
        return fragment;
    }

    /**
     * Constructor
     */
    public OnlineSearchFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        for (PodcastSearcherRegistry.SearcherInfo info : PodcastSearcherRegistry.getSearchProviders()) {
            if (info.searcher.getClass().getName().equals(getArguments().getString(ARG_SEARCHER))) {
                searchProvider = info.searcher;
                break;
            }
        }
        if (searchProvider == null) {
            throw new IllegalArgumentException("Podcast searcher not found");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_itunes_search, container, false);
        ((AppCompatActivity) getActivity()).setSupportActionBar(root.findViewById(R.id.toolbar));
        root.findViewById(R.id.spinner_country).setVisibility(INVISIBLE);
        gridView = root.findViewById(R.id.gridView);
        adapter = new ItunesAdapter(getActivity(), new ArrayList<>());
        gridView.setAdapter(adapter);

        //Show information about the podcast when the list item is clicked
        gridView.setOnItemClickListener((parent, view1, position, id) -> {
            PodcastSearchResult podcast = searchResults.get(position);
            Intent intent = new Intent(getActivity(), OnlineFeedViewActivity.class);
            intent.putExtra(OnlineFeedViewActivity.ARG_FEEDURL, podcast.feedUrl);
            intent.putExtra(MainActivity.EXTRA_STARTED_FROM_SEARCH, true);
            startActivity(intent);
        });
        progressBar = root.findViewById(R.id.progressBar);
        txtvError = root.findViewById(R.id.txtvError);
        butRetry = root.findViewById(R.id.butRetry);
        txtvEmpty = root.findViewById(android.R.id.empty);
        TextView txtvPoweredBy = root.findViewById(R.id.search_powered_by);
        txtvPoweredBy.setText(getString(R.string.search_powered_by, searchProvider.getName()));
        return root;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
        }
        adapter = null;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.online_search, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView sv = (SearchView) searchItem.getActionView();
        sv.setQueryHint(getString(R.string.search_podcast_hint));
        sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                sv.clearFocus();
                search(s);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return false;
            }
        });
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                ((MainActivity) requireActivity()).disableHardwareShortcuts();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                getActivity().getSupportFragmentManager().popBackStack();
                ((MainActivity) requireActivity()).enableHardwareShortcuts();
                return true;
            }
        });
        searchItem.expandActionView();

        if (getArguments().getString(ARG_QUERY, null) != null) {
            sv.setQuery(getArguments().getString(ARG_QUERY, null), true);
        }

    }

    private void search(String query) {
        if (disposable != null) {
            disposable.dispose();
        }
        showOnlyProgressBar();
        disposable = searchProvider.search(query).subscribe(result -> {
            searchResults = result;
            progressBar.setVisibility(View.GONE);
            adapter.clear();
            adapter.addAll(searchResults);
            adapter.notifyDataSetInvalidated();
            gridView.setVisibility(!searchResults.isEmpty() ? View.VISIBLE : View.GONE);
            txtvEmpty.setVisibility(searchResults.isEmpty() ? View.VISIBLE : View.GONE);
            txtvEmpty.setText(getString(R.string.no_results_for_query, query));
        }, error -> {
                Log.e(TAG, Log.getStackTraceString(error));
                progressBar.setVisibility(View.GONE);
                txtvError.setText(error.toString());
                txtvError.setVisibility(View.VISIBLE);
                butRetry.setOnClickListener(v -> search(query));
                butRetry.setVisibility(View.VISIBLE);
            });
    }

    private void showOnlyProgressBar() {
        gridView.setVisibility(View.GONE);
        txtvError.setVisibility(View.GONE);
        butRetry.setVisibility(View.GONE);
        txtvEmpty.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
    }
}
