package de.danoeh.antennapod.fragment;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.BlendModeColorFilterCompat;
import androidx.core.graphics.BlendModeCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.resource.bitmap.FitCenter;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import org.apache.commons.lang3.StringUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.core.event.PlaybackPositionEvent;
import de.danoeh.antennapod.core.feed.util.ImageResourceUtils;
import de.danoeh.antennapod.core.glide.ApGlideSettings;
import de.danoeh.antennapod.core.util.ChapterUtils;
import de.danoeh.antennapod.core.util.DateUtils;
import de.danoeh.antennapod.core.util.EmbeddedChapterImage;
import de.danoeh.antennapod.core.util.playback.PlaybackController;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.playback.Playable;
import io.reactivex.Maybe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Displays the cover and the title of a FeedItem.
 */
public class CoverFragment extends Fragment {

    private static final String TAG = "CoverFragment";
    static final double SIXTEEN_BY_NINE = 1.7;

    private View root;
    private TextView txtvPodcastTitle;
    private TextView txtvEpisodeTitle;
    private ImageView imgvCover;
    private ImageButton openDescription;
    private LinearLayout openDescriptionLayout;
    private Space counterweight;
    private Space spacer;
    private ImageButton butPrevChapter;
    private ImageButton butNextChapter;
    private LinearLayout episodeDetails;
    private LinearLayout chapterControl;
    private PlaybackController controller;
    private Disposable disposable;
    private int displayedChapterIndex = -1;
    private Playable media;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setRetainInstance(true);
        root = inflater.inflate(R.layout.cover_fragment, container, false);
        txtvPodcastTitle = root.findViewById(R.id.txtvPodcastTitle);
        txtvEpisodeTitle = root.findViewById(R.id.txtvEpisodeTitle);
        imgvCover = root.findViewById(R.id.imgvCover);
        episodeDetails = root.findViewById(R.id.episode_details);
        chapterControl = root.findViewById(R.id.chapterButton);
        butPrevChapter = root.findViewById(R.id.butPrevChapter);
        butNextChapter = root.findViewById(R.id.butNextChapter);

        imgvCover.setOnClickListener(v -> onPlayPause());
        openDescriptionLayout = root.findViewById(R.id.openDescriptionButton);
        openDescription = root.findViewById(R.id.openDescription);
        counterweight = root.findViewById(R.id.counterweight);
        spacer = root.findViewById(R.id.details_spacer);
        View.OnClickListener scrollToDesc = view ->
                ((AudioPlayerFragment) requireParentFragment()).scrollToPage(AudioPlayerFragment.POS_DESCRIPTION, true);
        openDescription.setOnClickListener(scrollToDesc);
        openDescriptionLayout.setOnClickListener(scrollToDesc);
        ColorFilter colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                txtvPodcastTitle.getCurrentTextColor(), BlendModeCompat.SRC_IN);
        openDescription.setColorFilter(colorFilter);
        butNextChapter.setColorFilter(colorFilter);
        butPrevChapter.setColorFilter(colorFilter);
        ChaptersFragment chaptersFragment = new ChaptersFragment();
        chapterControl.setOnClickListener(v ->
                chaptersFragment.show(getChildFragmentManager(), ChaptersFragment.TAG));
        butPrevChapter.setOnClickListener(v -> seekToPrevChapter());
        butNextChapter.setOnClickListener(v -> seekToNextChapter());

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        configureForOrientation(getResources().getConfiguration());
    }

    private void loadMediaInfo() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Maybe.<Playable>create(emitter -> {
            Playable media = controller.getMedia();
            if (media != null) {
                emitter.onSuccess(media);
            } else {
                emitter.onComplete();
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(media -> {
                    this.media = media;
                    displayMediaInfo(media);
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void displayMediaInfo(@NonNull Playable media) {
        String pubDateStr = DateUtils.formatAbbrev(getActivity(), media.getPubDate());
        txtvPodcastTitle.setText(StringUtils.stripToEmpty(media.getFeedTitle())
                + "\u00A0"
                + "・"
                + "\u00A0"
                + StringUtils.replace(StringUtils.stripToEmpty(pubDateStr), " ", "\u00A0"));
        txtvEpisodeTitle.setText(media.getEpisodeTitle());
        displayedChapterIndex = -1;
        refreshChapterData(ChapterUtils.getCurrentChapterIndex(media, media.getPosition())); //handles displayCoverImage
        updateDescriptionButtonVisibility();
    }

    private void updateDescriptionButtonVisibility() {
        if (media.getDescription() != null) {
            boolean hasShownotes = !StringUtils.isEmpty(media.getDescription());
            int newVisibility = hasShownotes ? View.VISIBLE : View.INVISIBLE;
            if (openDescriptionLayout.getVisibility() != newVisibility) {
                openDescriptionLayout.setVisibility(newVisibility);
                ObjectAnimator.ofFloat(
                        openDescriptionLayout,
                        "alpha",
                        hasShownotes ? 0 : 1,
                        hasShownotes ? 1 : 0)
                        .setDuration(getResources().getInteger(android.R.integer.config_shortAnimTime))
                        .start();
            }
        }
    }

    private void refreshChapterData(int chapterIndex) {
        if (media.getChapters() == null) {
            displayCoverImage();
            return;
        }

        boolean chapterControlVisible = true;

        if (chapterIndex > -1) {
            if (media.getPosition() > media.getDuration() || chapterIndex >= media.getChapters().size() - 1) {
                displayedChapterIndex = media.getChapters().size() - 1;
                butNextChapter.setVisibility(View.INVISIBLE);
            } else {
                displayedChapterIndex = chapterIndex;
                butNextChapter.setVisibility(View.VISIBLE);
            }
        } else {
            chapterControlVisible = false;
        }

        int newVisibility = chapterControlVisible ? View.VISIBLE : View.GONE;
        if (chapterControl.getVisibility() != newVisibility) {
            chapterControl.setVisibility(newVisibility);
            ObjectAnimator.ofFloat(chapterControl,
                    "alpha",
                    chapterControlVisible ? 0 : 1,
                    chapterControlVisible ? 1 : 0)
                    .setDuration(getResources().getInteger(android.R.integer.config_shortAnimTime))
                    .start();
        }

        displayCoverImage();
    }

    private Chapter getCurrentChapter() {
        if (media == null || media.getChapters() == null || displayedChapterIndex == -1) {
            return null;
        }
        return media.getChapters().get(displayedChapterIndex);
    }

    private void seekToPrevChapter() {
        Chapter curr = getCurrentChapter();

        if (controller == null || curr == null || displayedChapterIndex == -1) {
            return;
        }

        if (displayedChapterIndex < 1) {
            controller.seekTo(0);
        } else if ((controller.getPosition() - 10000 * controller.getCurrentPlaybackSpeedMultiplier())
                < curr.getStart()) {
            refreshChapterData(displayedChapterIndex - 1);
            controller.seekToChapter(media.getChapters().get(displayedChapterIndex));
        } else {
            controller.seekToChapter(curr);
        }
    }

    private void seekToNextChapter() {
        if (controller == null || media == null || media.getChapters() == null
                || displayedChapterIndex == -1 || displayedChapterIndex + 1 >= media.getChapters().size()) {
            return;
        }

        refreshChapterData(displayedChapterIndex + 1);
        controller.seekToChapter(media.getChapters().get(displayedChapterIndex));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // prevent memory leaks
        root = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        controller = new PlaybackController(getActivity()) {
            @Override
            public void loadMediaInfo() {
                CoverFragment.this.loadMediaInfo();
            }
        };
        controller.init();
        loadMediaInfo();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();

        if (disposable != null) {
            disposable.dispose();
        }
        controller.release();
        controller = null;
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaybackPositionEvent event) {
        int newChapterIndex = ChapterUtils.getCurrentChapterIndex(media, event.getPosition());
        if (newChapterIndex > -1 && newChapterIndex != displayedChapterIndex) {
            refreshChapterData(newChapterIndex);
        }
    }

    private void displayCoverImage() {
        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                .dontAnimate()
                .transforms(new FitCenter(),
                        new RoundedCorners((int) (16 * getResources().getDisplayMetrics().density)));

            RequestBuilder<Drawable> cover = Glide.with(this)
                    .load(media.getImageLocation())
                    .error(Glide.with(this)
                            .load(ImageResourceUtils.getFallbackImageLocation(media))
                            .apply(options))
                    .apply(options);

        if (displayedChapterIndex == -1 || media == null || media.getChapters() == null
                || TextUtils.isEmpty(media.getChapters().get(displayedChapterIndex).getImageUrl())) {
            cover.into(imgvCover);
        } else {
            Glide.with(this)
                    .load(EmbeddedChapterImage.getModelFor(media, displayedChapterIndex))
                    .apply(options)
                    .thumbnail(cover)
                    .error(cover)
                    .into(imgvCover);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        configureForOrientation(newConfig);
    }

    public float convertDpToPixel(float dp) {
        Context context = this.getActivity().getApplicationContext();
        return dp * ((float) context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

    private void configureForOrientation(Configuration newConfig) {
        LinearLayout mainContainer = getView().findViewById(R.id.cover_fragment);
        LinearLayout textContainer = getView().findViewById(R.id.cover_fragment_text_container);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) imgvCover.getLayoutParams();
        LinearLayout.LayoutParams textParams = (LinearLayout.LayoutParams) textContainer.getLayoutParams();
        double ratio = (float) newConfig.screenHeightDp / (float) newConfig.screenWidthDp;

        boolean spacerVisible = true;
        ViewGroup detailsParent = (ViewGroup) getView();
        int detailsWidth = ViewGroup.LayoutParams.MATCH_PARENT;

        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            double percentageWidth = 0.8;
            if (ratio <= SIXTEEN_BY_NINE) {
                percentageWidth = (ratio / SIXTEEN_BY_NINE) * percentageWidth * 0.8;
            }
            mainContainer.setOrientation(LinearLayout.VERTICAL);
            if (newConfig.screenWidthDp > 0) {
                params.width = (int) (convertDpToPixel(newConfig.screenWidthDp) * percentageWidth);
                params.height = params.width;
                textParams.weight = 0;
                imgvCover.setLayoutParams(params);
            }
        } else {
            double percentageHeight = ratio * 0.6;
            mainContainer.setOrientation(LinearLayout.HORIZONTAL);
            if (newConfig.screenHeightDp > 0) {
                params.height = (int) (convertDpToPixel(newConfig.screenHeightDp) * percentageHeight);
                params.width = params.height;
                textParams.weight = 1;
                imgvCover.setLayoutParams(params);
            }

            spacerVisible = false;
            detailsParent = textContainer;
            detailsWidth = ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        if (displayedChapterIndex == -1) {
            detailsWidth = ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        spacer.setVisibility(spacerVisible ? View.VISIBLE : View.GONE);
        counterweight.setVisibility(spacerVisible ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams wrapHeight = new LinearLayout.LayoutParams(detailsWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        episodeDetails.setLayoutParams(wrapHeight);
        getView().findViewById(R.id.vertical_divider).setVisibility(spacerVisible ? View.GONE : View.VISIBLE);

        if (episodeDetails.getParent() != detailsParent) {
            ((ViewGroup) episodeDetails.getParent()).removeView(episodeDetails);
            detailsParent.addView(episodeDetails);
        }
    }

    void onPlayPause() {
        if (controller == null) {
            return;
        }
        controller.playPause();
    }
}
