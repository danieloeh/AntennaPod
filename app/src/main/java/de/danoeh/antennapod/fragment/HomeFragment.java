package de.danoeh.antennapod.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.transition.ChangeBounds;
import androidx.transition.TransitionManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.core.event.FeedItemEvent;
import de.danoeh.antennapod.core.event.PlaybackPositionEvent;
import de.danoeh.antennapod.core.event.PlayerStatusEvent;
import de.danoeh.antennapod.core.event.UnreadItemsUpdateEvent;
import de.danoeh.antennapod.dialog.HomeSectionsSettingsDialog;
import de.danoeh.antennapod.fragment.homesections.HomeSection;
import de.danoeh.antennapod.fragment.homesections.InboxSection;
import de.danoeh.antennapod.fragment.homesections.QueueSection;
import de.danoeh.antennapod.fragment.homesections.StatisticsSection;
import de.danoeh.antennapod.fragment.homesections.SubscriptionsSection;
import de.danoeh.antennapod.fragment.homesections.SurpriseSection;
import de.danoeh.antennapod.menuhandler.FeedItemMenuHandler;
import de.danoeh.antennapod.menuhandler.MenuItemUtils;
import de.danoeh.antennapod.model.feed.FeedItem;

/**
 * Shows unread or recently published episodes
 */
public class HomeFragment extends Fragment implements Toolbar.OnMenuItemClickListener {

    public static final String TAG = "HomeFragment";
    public static final String PREF_NAME = "PrefHomeFragment";
    public static final String PREF_SECTIONS = "PrefHomeSectionsString";
    public static final String PREF_FRAGMENT = "PrefHomeFragment";

    FeedItem selectedItem = null;

    public static final List<SectionTitle> defaultSections = Arrays.asList(
            new SectionTitle(QueueSection.TAG, false),
            new SectionTitle(InboxSection.TAG, false),
            new SectionTitle(StatisticsSection.TAG, false),
            new SectionTitle(SubscriptionsSection.TAG, false),
            new SectionTitle(SurpriseSection.TAG, false)
    );

    private static final String KEY_UP_ARROW = "up_arrow";
    private boolean displayUpArrow;

    Toolbar toolbar;

    LinearLayout homeContainer;
    FragmentContainerView fragmentContainer;
    View divider;

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View root = inflater.inflate(R.layout.home_fragment, container, false);
        homeContainer = root.findViewById(R.id.homeContainer);
        fragmentContainer = root.findViewById(R.id.homeFragmentContainer);
        divider = root.findViewById(R.id.homeFragmentDivider);

        toolbar = root.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.home_label);
        toolbar.inflateMenu(R.menu.home);
        toolbar.setOnMenuItemClickListener(this);

        MenuItemUtils.setupSearchItem(toolbar.getMenu(), (MainActivity) getActivity(), 0, "");

        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() > 0;
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW);
        }
        ((MainActivity) requireActivity()).setupToolbarToggle(toolbar, displayUpArrow);

        loadSections();

        return root;
    }

    ArrayList<HomeSection> sections = new ArrayList<>();

    private void loadSections() {
        homeContainer.removeAllViews();
        sections.clear();

        for (SectionTitle s : getSectionsPrefs()) {
            HomeSection section;
            switch (s.tag) {
                case InboxSection.TAG:
                    section = new InboxSection(this);
                    break;
                default:
                case QueueSection.TAG:
                    section = new QueueSection(this);
                    break;
                case SubscriptionsSection.TAG:
                    section = new SubscriptionsSection(this);
                    break;
                case SurpriseSection.TAG:
                    section = new SurpriseSection(this);
                    break;
                case StatisticsSection.TAG:
                    section = new StatisticsSection(this);
                    break;
            }

            if (!s.hidden) {
                sections.add(section);
                section.addSectionTo(homeContainer);
            }
        }

        fillFragmentIfRoom();
    }

    private void fillFragmentIfRoom() {
        //only if at least halve of the screen is empty
        homeContainer.post(() -> {
            if (homeContainer.getHeight() == homeContainer.getPaddingBottom()) {
                //no subs
                ((MainActivity) requireActivity()).loadFragment(AddFeedFragment.TAG, null);
            } else if (homeContainer.getHeight() < getView().getHeight() / 2) {
                Fragment fragment;
                SharedPreferences prefs = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                switch (prefs.getInt(PREF_FRAGMENT, 0)) {
                    default:
                    case 0: //Episodes
                        fragment = new EpisodesFragment(true);
                        break;
                    case 1: //Inbox
                        fragment = new InboxFragment(true);
                        break;
                    case 2: //Queue
                        fragment = new QueueFragment();
                        break;
                }
                getChildFragmentManager()
                        .beginTransaction().replace(R.id.homeFragmentContainer, fragment, PREF_FRAGMENT)
                        .commit();
                fragmentContainer.setVisibility(View.VISIBLE);
                divider.setVisibility(View.VISIBLE);
            }
        });
    }

    private View searchBar() {
        //TODO
        return new View(requireContext());
    }

    public static List<SectionTitle> getSectionsPrefs(Fragment fragment) {
        SharedPreferences prefs = fragment.requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String[] strings = prefs.getString(PREF_SECTIONS,
                HomeSectionsSettingsDialog.encodeSectionSettings(defaultSections))
                .split(";");
        List<SectionTitle> sectionTitles = new ArrayList<>();
        for (String s:
             strings) {
            String[] tagBool = s.split(",");
            sectionTitles.add(new SectionTitle(tagBool[0], Boolean.parseBoolean(tagBool[1])));
        }
        return sectionTitles;
    }

    private List<SectionTitle> getSectionsPrefs() {
        return getSectionsPrefs(this);
    }

    private void reloadSections() {
        fragmentContainer.setVisibility(View.GONE);
        divider.setVisibility(View.GONE);

        loadSections();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (!super.onOptionsItemSelected(item)) {
            if (item.getItemId() == R.id.homesettings_items) {
                HomeSectionsSettingsDialog.open(this,
                        (dialogInterface, i) -> {
                            reloadSections();
                        });
            }
        }

        return true;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow);
        super.onSaveInstanceState(outState);
    }



    public static class SectionTitle {
        public String tag;
        public boolean hidden;

        public SectionTitle(String tag, boolean hidden) {
            this.tag = tag;
            this.hidden = hidden;
        }

        public void toggleHidden() {
            hidden = !hidden;
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem menuItem) {
        if (selectedItem == null) {
            //can only happen if it comes from the fragment
            return getChildFragmentManager().findFragmentByTag(PREF_FRAGMENT).onContextItemSelected(menuItem);
        }
        final FeedItem item = selectedItem;
        //reset selectedItem so we know whether it came from the fragment next time
        selectedItem = null;
        return FeedItemMenuHandler.onMenuItemClicked(this, menuItem.getItemId(), item);
    }

    public void setSelectedItem(FeedItem feedItem) {
        selectedItem = feedItem;
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(FeedItemEvent event) {
        updateSections(HomeSection.UpdateEvents.FEED_ITEM);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUnreadItemsChanged(UnreadItemsUpdateEvent event) {
        updateSections(HomeSection.UpdateEvents.UNREAD);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaybackPositionEvent event) {
        updateSections(HomeSection.UpdateEvents.QUEUE);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusChanged(PlayerStatusEvent event) {
        updateSections(HomeSection.UpdateEvents.QUEUE);
    }

    private void updateSections(HomeSection.UpdateEvents event, Boolean all) {
        TransitionManager.beginDelayedTransition(
                homeContainer,
                new ChangeBounds());

        for (HomeSection section: sections) {
            if (section.updateEvents.contains(event) || all) {
                section.updateItems(event);
            }
        }
    }

    private void updateSections(HomeSection.UpdateEvents event) {
        updateSections(event, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        //update all sections, especially play/pauseStatus in QueueSection
        updateSections(HomeSection.UpdateEvents.QUEUE, true);
    }
}
