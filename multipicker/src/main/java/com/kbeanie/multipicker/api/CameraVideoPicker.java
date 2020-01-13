package com.kbeanie.multipicker.api;

import android.app.Activity;
import androidx.fragment.app.Fragment;

import com.kbeanie.multipicker.api.exceptions.PickerException;
import com.kbeanie.multipicker.core.VideoPickerImpl;

/**
 * Captures a video using the device's Camera
 */
public class CameraVideoPicker extends VideoPickerImpl {

    /**
     * Constructor for triggering Video capture from an {@link Activity}
     * @param activity activity
     */
    public CameraVideoPicker(Activity activity) {
        super(activity, Picker.PICK_VIDEO_CAMERA);
    }

    /**
     * Constructor for triggering Video capture from a {@link Fragment}
     * @param fragment fragment
     */
    public CameraVideoPicker(Fragment fragment) {
        super(fragment, Picker.PICK_VIDEO_CAMERA);
    }

    /**
     * Re-initialize {@link CameraVideoPicker} object if your activity is destroyed
     * @param activity activity
     * @param path path
     */
    public CameraVideoPicker(Activity activity, String path) {
        super(activity, Picker.PICK_VIDEO_CAMERA);
        reinitialize(path);
    }

    /**
     * Re-initialize {@link CameraVideoPicker} object if your activity is destroyed
     * @param fragment fragment
     * @param path path
     */
    public CameraVideoPicker(Fragment fragment, String path) {
        super(fragment, Picker.PICK_VIDEO_CAMERA);
        reinitialize(path);
    }

    /**
     * Trigger Video Capture using the device's Camera
     * @return path as String
     */
    public String pickVideo() {
        String path = null;
        try {
            path = super.pick();
        } catch (PickerException e) {
            e.printStackTrace();
            if (callback != null) {
                callback.onPickerError(e.getMessage());
            }
        }
        return path;
    }
}
