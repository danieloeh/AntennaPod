package de.danoeh.antennapod.fragment.homesections;

import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.fragment.HomeFragment;
import de.danoeh.antennapod.menuhandler.FeedItemMenuHandler;
import de.danoeh.antennapod.model.feed.FeedItem;
import kotlin.Unit;
import slush.AdapterAppliedResult;
import slush.Slush;
import slush.listeners.OnBindListener;

/**
 * Section on the HomeFragment
 * can have different I item types
 */
public abstract class HomeSection<I> implements View.OnCreateContextMenuListener {

    protected HomeFragment context;

    protected View section;
    protected TextView tvTitle;
    protected Button navigateButton;
    protected RecyclerView recyclerView;

    public enum UpdateEvents {
        FEED_ITEM, UNREAD, FAVORITES, QUEUE
    }

    //must be set by descendant
    protected String sectionTitle;
    protected String sectionNavigateTitle;
    public List<UpdateEvents> updateEvents = Collections.emptyList();

    protected FeedItem selectedItem;

    public HomeSection(HomeFragment context) {
        this.context = context;
        section = View.inflate(context.requireActivity(), R.layout.home_section, null);
        tvTitle = section.findViewById(R.id.sectionTitle);
        navigateButton = section.findViewById(R.id.sectionNavigate);
        recyclerView = section.findViewById(R.id.sectionRecyclerView);
    }

    public void addSectionTo(LinearLayout parent) {
        tvTitle.setText(sectionTitle);
        if (!TextUtils.isEmpty(sectionNavigateTitle)) {
            navigateButton.setText(
                    context.getString(R.string.navigate_arrows, sectionNavigateTitle.toLowerCase(Locale.getDefault())));
            navigateButton.setOnClickListener(navigate());
        } else {
            navigateButton.setVisibility(View.INVISIBLE);
        }

        parent.addView(section);
        context.registerForContextMenu(recyclerView);

        hideIfEmpty();
    }

    private void hideIfEmpty() {
        boolean isVisible = recyclerView.getAdapter() != null && recyclerView.getAdapter().getItemCount() > 0;
        section.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    protected abstract View.OnClickListener navigate();

    @NonNull
    protected abstract List<I> loadItems();

    public void updateItems(UpdateEvents event) {
        hideIfEmpty();
    }

    protected abstract Unit onItemClick(View view, I item);

    @Override
    public void onCreateContextMenu(ContextMenu contextMenu, View view, ContextMenu.ContextMenuInfo contextMenuInfo) {
        MenuInflater inflater = context.requireActivity().getMenuInflater();
        inflater.inflate(R.menu.feeditemlist_context, contextMenu);
        contextMenu.setHeaderTitle(selectedItem.getTitle());
        FeedItemMenuHandler.onPrepareMenu(contextMenu, selectedItem, R.id.skip_episode_item);
    }

    protected AdapterAppliedResult<I> easySlush(int layout, OnBindListener<I> onBindListener) {
        return new Slush.SingleType<I>()
                .setItemLayout(layout)
                .setLayoutManager(new LinearLayoutManager(context.getContext(), RecyclerView.HORIZONTAL, false))
                .setItems(loadItems())
                .onItemClickWithItem(this::onItemClick)
                .onBind(onBindListener)
                .into(recyclerView);
    }

}
