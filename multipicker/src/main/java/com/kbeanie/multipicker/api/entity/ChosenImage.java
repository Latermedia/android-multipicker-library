package com.kbeanie.multipicker.api.entity;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

/**
 * Contains details about the image that was chosen
 */
public class ChosenImage extends ChosenFile {
    private int orientation;
    private String thumbnailPath;
    private String thumbnailSmallPath;
    private int width;
    private int height;

    public ChosenImage() {
        super();
    }

    /**
     * Get orientation of the actual image
     *
     * @return orientation
     */
    public int getOrientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    /**
     * Get the path to the thumbnail(big) of the image
     *
     * @return path
     */
    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    /**
     * Get the path to the thumbnail(small) of the image
     *
     * @return path
     */
    public String getThumbnailSmallPath() {
        return thumbnailSmallPath;
    }

    public void setThumbnailSmallPath(String thumbnailSmallPath) {
        this.thumbnailSmallPath = thumbnailSmallPath;
    }

    /**
     * Get the image width
     *
     * @return image width
     */
    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * Get the image height;
     *
     * @return image height
     */
    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    private final static String STRING_FORMAT = "Height: %s, Width: %s, Orientation: %s";

    @NonNull
    @Override
    public String toString() {
        return super.toString() + " " + String.format(STRING_FORMAT, height, width,
                getOrientationName());
    }

    /**
     * Get Orientation user friendly label
     *
     * @return orientation
     */
    public String getOrientationName() {
        String orientationName = "NORMAL";
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                orientationName = "FLIP_HORIZONTAL";
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                orientationName = "FLIP_VERTICAL";
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                orientationName = "ROTATE_90";
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                orientationName = "ROTATE_180";
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                orientationName = "ROTATE_270";
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                orientationName = "TRANSPOSE";
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                orientationName = "TRANSVERSE";
                break;
            case ExifInterface.ORIENTATION_UNDEFINED:
                orientationName = "UNDEFINED";
                break;
        }
        return orientationName;
    }

}
