package com.kbeanie.multipicker.threads;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;

import com.kbeanie.multipicker.api.callbacks.VideoPickerCallback;
import com.kbeanie.multipicker.api.entity.ChosenFile;
import com.kbeanie.multipicker.api.entity.ChosenImage;
import com.kbeanie.multipicker.api.entity.ChosenVideo;
import com.kbeanie.multipicker.api.exceptions.PickerException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import static com.kbeanie.multipicker.utils.StreamHelper.close;
import static com.kbeanie.multipicker.utils.StreamHelper.flush;

/**
 * Created by kbibek on 2/24/16.
 */
public class VideoProcessorThread extends FileProcessorThread {

    private final static String TAG = VideoProcessorThread.class.getSimpleName();

    private VideoPickerCallback callback;
    private boolean shouldGenerateMetadata;
    private boolean shouldGeneratePreviewImages;

    public VideoProcessorThread(Context context, List<? extends ChosenFile> files, int cacheLocation) {
        super(context, files, cacheLocation);
    }

    public void setVideoPickerCallback(VideoPickerCallback callback) {
        this.callback = callback;
    }

    @Override
    public void run() {
        super.run();
        try {
            postProcessVideos();
            onDone();
        } catch (Exception e) {
            e.printStackTrace();
            onError(e.getMessage());
        }
    }

    private void postProcessVideos() throws PickerException {
        for (ChosenFile file : files) {
            ChosenVideo video = (ChosenVideo) file;
            postProcessVideo(video);
        }
    }

    private void postProcessVideo(ChosenVideo video) throws PickerException {
        if (shouldGenerateMetadata) {
            MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
            metadataRetriever.setDataSource(video.getOriginalPath());
            String duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String orientation = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            String height = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String width = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);

            if (duration != null) {
                video.setDuration(Integer.parseInt(duration) / 1000);
            }
            if (orientation != null) {
                video.setOrientation(Integer.parseInt(orientation));
            }

            if (height != null) {
                video.setHeight(Integer.parseInt(height));
            }

            if (width != null) {
                video.setWidth(Integer.parseInt(width));
            }
            metadataRetriever.release();
        }

        if (shouldGeneratePreviewImages) {
            String previewPath = createPreviewImage(video.getOriginalPath());
            video.setPreviewImage(previewPath);
            String previewThumbnail = compressAndSaveImage(previewPath, THUMBNAIL_BIG);
            String previewThumbnailSmall = compressAndSaveImage(previewPath, THUMBNAIL_SMALL);
            video.setPreviewThumbnail(previewThumbnail);
            video.setPreviewThumbnailSmall(previewThumbnailSmall);
        }
    }

    private void onDone() {
        if (callback != null) {
            getActivityFromContext().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    callback.onVideosChosen((List<ChosenVideo>) files);
                }
            });
        }
    }

    private void onError(final String message) {
        if (callback != null) {
            getActivityFromContext().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    callback.onError(message);
                }
            });
        }
    }

    public void setShouldGenerateMetadata(boolean shouldGenerateMetadata) {
        this.shouldGenerateMetadata = shouldGenerateMetadata;
    }

    public void setShouldGeneratePreviewImages(boolean shouldGeneratePreviewImages) {
        this.shouldGeneratePreviewImages = shouldGeneratePreviewImages;
    }

    private String createPreviewImage(String videoPath) throws PickerException {
        String previewImage;
        previewImage = null;
        Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(videoPath,
                MediaStore.Video.Thumbnails.FULL_SCREEN_KIND);
        if (bitmap != null) {
            previewImage = generateFileNameForVideoPreviewImage();
            File file = new File(previewImage);

            FileOutputStream stream = null;
            try {
                stream = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            } catch (IOException e) {
                throw new PickerException(e);
            } finally {
                flush(stream);
                close(stream);
            }

        }
        return previewImage;
    }
}
