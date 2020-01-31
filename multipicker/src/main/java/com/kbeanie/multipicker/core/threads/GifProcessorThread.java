package com.kbeanie.multipicker.core.threads;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kbeanie.multipicker.api.callbacks.GifPickerCallback;
import com.kbeanie.multipicker.api.entity.ChosenFile;
import com.kbeanie.multipicker.api.entity.ChosenGif;
import com.kbeanie.multipicker.api.exceptions.PickerException;
import com.kbeanie.multipicker.utils.LogUtils;

import java.util.List;

public final class GifProcessorThread extends FileProcessorThread {
    private final static String TAG = "GifProcessorThread";

    private boolean shouldGenerateThumbnails;
    private boolean shouldGenerateMetadata;

    @Nullable
    private GifPickerCallback callback;

    public GifProcessorThread(@NonNull Context context, List<ChosenGif> paths, int cacheLocation) {
        super(context, paths, cacheLocation);
    }

    public void setShouldGenerateThumbnails(boolean shouldGenerateThumbnails) {
        this.shouldGenerateThumbnails = shouldGenerateThumbnails;
    }

    public void setGifPickerCallback(GifPickerCallback callback) {
        this.callback = callback;
    }

    @Override
    public void run() {
        super.run();
        postProcessGifs();
        checkErrorFiles();
        onDone();
    }

    private void onDone() {
        if (callback != null) {
            getActivityFromContext().runOnUiThread(() -> {
                errorFilesCallback();
                callback.onGifsChosen((List<ChosenGif>) files);
            });
        }
    }

    private void errorFilesCallback() {
        if (callback != null && !errorFiles.isEmpty()) {
            callback.onErrorFiles(errorFiles);
        }
    }

    private void postProcessGifs() {
        for (ChosenFile file : files) {
            ChosenGif gif = (ChosenGif) file;
            try {
                postProcessGif(gif);
                gif.setSuccess(true);
            } catch (PickerException e) {
                if (callback != null) {
                    callback.onPickerException(e);
                }
                e.printStackTrace();
                gif.setSuccess(false);
            }
        }
    }

    private void postProcessGif(ChosenGif gif) throws PickerException {
        LogUtils.d(TAG, "postProcessGif: " + gif.getMimeType());
        if (shouldGenerateMetadata) {
            try {
                generateMetadata(gif);
            } catch (Exception e) {
                LogUtils.d(TAG, "postProcessGif: Error generating metadata");
                if (callback != null) {
                    callback.onPickerException(e);
                }
                e.printStackTrace();
            }
        }
        if (shouldGenerateThumbnails) {
            generateThumbnails(gif);
        }
        LogUtils.d(TAG, "postProcessGif: " + gif);
    }

    private void generateMetadata(ChosenGif gif) {
        gif.setWidth(Integer.parseInt(getWidthOfImage(gif.getOriginalPath())));
        gif.setHeight(Integer.parseInt(getHeightOfImage(gif.getOriginalPath())));
        gif.setOrientation(getOrientation(gif.getOriginalPath()));
    }

    private void generateThumbnails(ChosenGif gif) throws PickerException {
        int quality = 100;
        String thumbnailBig = downScaleAndSaveImage(gif.getOriginalPath(), THUMBNAIL_BIG, quality);
        gif.setThumbnailPath(thumbnailBig);
        String thumbnailSmall = downScaleAndSaveImage(gif.getOriginalPath(), THUMBNAIL_SMALL,
                quality);
        gif.setThumbnailSmallPath(thumbnailSmall);
    }

    public void setShouldGenerateMetadata(boolean shouldGenerateMetadata) {
        this.shouldGenerateMetadata = shouldGenerateMetadata;
    }

}
