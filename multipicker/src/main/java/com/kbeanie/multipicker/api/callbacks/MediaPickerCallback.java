package com.kbeanie.multipicker.api.callbacks;

import com.kbeanie.multipicker.api.entity.ChosenGif;
import com.kbeanie.multipicker.api.entity.ChosenImage;
import com.kbeanie.multipicker.api.entity.ChosenVideo;

import java.util.List;

import androidx.annotation.Nullable;

/**
 * Created by kbibek on 3/23/16.
 */
public interface MediaPickerCallback extends PickerCallback {
    void onMediaChosen(@Nullable List<ChosenImage> images,
                       @Nullable List<ChosenVideo> videos,
                       @Nullable List<ChosenGif> gifs);
}
