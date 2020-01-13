package com.kbeanie.multipicker.api.callbacks;

import com.kbeanie.multipicker.api.entity.ErrorFile;

import java.util.List;

/**
 * Created by kbibek on 2/24/16.
 */
public interface PickerCallback {
    void onProcessingFile(int oneOf, int total);
    void onPickerError(String message);
    void onPickerException(Throwable throwable);
    void onErrorFiles(List<ErrorFile> errorFiles);
}
