package de.danoeh.antennapod.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.core.util.NetworkUtils;
import de.danoeh.antennapod.core.util.playback.RemoteMedia;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.service.playback.PlaybackService;
import de.danoeh.antennapod.core.util.DateUtils;
import de.danoeh.antennapod.core.util.playback.Playable;
import de.danoeh.antennapod.core.util.playback.PlaybackServiceStarter;
import de.danoeh.antennapod.dialog.StreamingConfirmationDialog;

import java.util.List;

/**
 * List adapter for showing a list of FeedItems with their title and description.
 */
public class FeedItemlistDescriptionAdapter extends ArrayAdapter<FeedItem> {
    private static final int MAX_LINES_COLLAPSED = 3;

    public FeedItemlistDescriptionAdapter(Context context, int resource, List<FeedItem> objects) {
        super(context, resource, objects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;

        FeedItem item = getItem(position);

        // Inflate layout
        if (convertView == null) {
            holder = new Holder();
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.itemdescription_listitem, parent, false);
            holder.title = convertView.findViewById(R.id.txtvTitle);
            holder.pubDate = convertView.findViewById(R.id.txtvPubDate);
            holder.description = convertView.findViewById(R.id.txtvDescription);
            holder.preview = convertView.findViewById(R.id.butPreview);

            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }

        holder.title.setText(item.getTitle());
        holder.pubDate.setText(DateUtils.formatAbbrev(getContext(), item.getPubDate()));
        if (item.getDescription() != null) {
            String description = item.getDescription()
                    .replaceAll("\n", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            holder.description.setText(description);
            holder.description.setMaxLines(MAX_LINES_COLLAPSED);
        }
        holder.description.setTag(Boolean.FALSE); // not expanded
        holder.preview.setVisibility(View.GONE);
        holder.preview.setOnClickListener(v -> {
            if (item.getMedia() == null) {
                return;
            }
            Playable playable = new RemoteMedia(item);
            if (!NetworkUtils.isStreamingAllowed()) {
                new StreamingConfirmationDialog(getContext(), playable).show();
                return;
            }
            new PlaybackServiceStarter(getContext(), playable)
                    .shouldStream(true)
                    .startWhenPrepared(true)
                    .callEvenIfRunning(true)
                    .start();
            getContext().startActivity(PlaybackService.getPlayerActivityIntent(getContext(), playable));
        });
        convertView.setOnClickListener(v -> {
            if (holder.description.getTag() == Boolean.TRUE) {
                holder.description.setMaxLines(MAX_LINES_COLLAPSED);
                holder.preview.setVisibility(View.GONE);
                holder.description.setTag(Boolean.FALSE);
            } else {
                holder.description.setMaxLines(2000);
                holder.preview.setVisibility(View.VISIBLE);
                holder.description.setTag(Boolean.TRUE);
            }
        });
        return convertView;
    }

    static class Holder {
        TextView title;
        TextView pubDate;
        TextView description;
        Button preview;
    }
}
