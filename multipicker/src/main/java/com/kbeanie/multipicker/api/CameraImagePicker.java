package com.kbeanie.multipicker.api;

import android.app.Activity;
import androidx.fragment.app.Fragment;

import com.kbeanie.multipicker.api.exceptions.PickerException;
import com.kbeanie.multipicker.core.ImagePickerImpl;

/**
 * Capture an image using the device's camera.
 */
public class CameraImagePicker extends ImagePickerImpl {
    /**
     * Constructor for triggering capture from an {@link Activity}
     *
     * @param activity activity
     */
    public CameraImagePicker(Activity activity) {
        super(activity, Picker.PICK_IMAGE_CAMERA);
    }

    /**
     * Constructor for triggering capture from a {@link Fragment}
     *
     * @param fragment fragment
     */
    public CameraImagePicker(Fragment fragment) {
        super(fragment, Picker.PICK_IMAGE_CAMERA);
    }

    /**
     * Re-initialize the {@link CameraImagePicker} object if your activity is destroyed
     *
     * @param activity activity
     * @param path path
     */
    public CameraImagePicker(Activity activity, String path) {
        super(activity, Picker.PICK_IMAGE_CAMERA);
        reinitialize(path);
    }

    /**
     * Re-initialize the {@link CameraImagePicker} object if your activity is destroyed
     *
     * @param fragment fragment
     * @param path path
     */
    public CameraImagePicker(Fragment fragment, String path) {
        super(fragment, Picker.PICK_IMAGE_CAMERA);
        reinitialize(path);
    }

    /**
     * Triggers image capture using the device's camera
     *
     * @return path as String
     */
    public String pickImage() {
        String path = null;
        try {
            path = pick();
        } catch (PickerException e) {
            e.printStackTrace();
            if (callback != null) {
                callback.onPickerException(e);
                callback.onPickerError(e.getMessage());
            }
        }
        return path;
    }
}
