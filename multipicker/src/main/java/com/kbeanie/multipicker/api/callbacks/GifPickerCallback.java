package com.kbeanie.multipicker.api.callbacks;

import com.kbeanie.multipicker.api.entity.ChosenGif;

import java.util.List;

public interface GifPickerCallback extends PickerCallback {
    void onGifsChosen(List<ChosenGif> gifs);
}
