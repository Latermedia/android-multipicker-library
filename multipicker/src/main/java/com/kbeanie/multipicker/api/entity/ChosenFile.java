package com.kbeanie.multipicker.api.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Contains details about the file that was chosen.
 */
public class ChosenFile {

    private long id;
    private String queryUri;
    /**
     * Processed path to file. This should always be a local path on the device.
     */
    private String originalPath;
    /**
     * Mime Type of the processed file
     */
    private String mimeType;
    /**
     * Size of the file in bytes
     */
    private long size;
    /**
     * Extension of the file. It may be blank or null.
     */
    private String extension;
    /**
     * Type of the file (image, video, file, audio etc).
     * This is for internal use.
     */
    private String type;
    /**
     * Display name of the file
     */

    private int requestId;

    private String displayName;
    private boolean success;

    private String tempFile = "";

    public ChosenFile() {

    }

    /**
     * If this file has been successfully processed.
     *
     * @return flag as true/false
     */
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Display name of the file
     *
     * @return file display name
     */
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    private String directoryType;

    /**
     * Internal use
     *
     * @return id
     */
    public long getId() {
        return id;
    }

    /**
     * Internal use
     *
     * @return directory type
     */
    public String getDirectoryType() {
        return directoryType;
    }

    public void setDirectoryType(String directoryType) {
        this.directoryType = directoryType;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getQueryUri() {
        return queryUri;
    }

    public void setQueryUri(String queryUri) {
        this.queryUri = queryUri;
    }

    /**
     * Path to the processed file. This will always be a local path on the device.
     *
     * @return original path
     */
    public String getOriginalPath() {
        return originalPath;
    }

    public void setOriginalPath(String originalPath) {
        this.originalPath = originalPath;
    }

    /**
     * Get mime-type of the file
     *
     * @return mime type as String
     */
    @Nullable
    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * Get the size of the processed file in bytes
     *
     * @return file size
     */
    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    /**
     * For internal use
     *
     * @return file type
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Get the extension of the file
     * Ex. .pdf, .jpeg, .mp4
     *
     * @return file extension
     */
    @Nullable
    public String getFileExtensionFromMimeType() {
        if (mimeType != null) {
            String extension = null;
            String[] parts = mimeType.split("/");
            if (parts.length >= 2) {
                if (!parts[1].equals("*")) {
                    extension = "." + parts[1];
                }
            }
            return extension;
        }
        return null;
    }

    /**
     * Get only the file extension (Ex. jpg, mp4, pdf etc)
     *
     * @return extension
     */
    @Nullable
    public String getFileExtensionFromMimeTypeWithoutDot() {
        String extension = getFileExtensionFromMimeType();
        if (extension != null) {
            return extension.replace(".", "");
        }
        return null;
    }

    private final static String STRING_FORMAT = "Type: %s, QueryUri: %s, Original Path: %s, " +
            "MimeType: %s, Size: %s";

    @NonNull
    @Override
    public String toString() {
        return String.format(STRING_FORMAT, type, queryUri, originalPath, mimeType,
                getHumanReadableSize(false));
    }

    /**
     * Get File size in a pretty format.
     *
     * @param si size
     * @return readable size
     */
    private String getHumanReadableSize(boolean si) {
        int unit = si ? 1000 : 1024;
        if (size < unit) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + "";
        return String.format(Locale.ENGLISH, "%.1f %sB", size / Math.pow(unit, exp), pre);
    }

    /**
     * Get Duration (for audio and video) in a pretty format
     *
     * @param duration duration
     * @return formatted duration
     */
    public String getHumanReadableDuration(long duration) {
        return String.format(Locale.getDefault(), "%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(duration),
                TimeUnit.MILLISECONDS.toMinutes(duration) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(duration)),
                TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration)));
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }


    public String getTempFile() {
        return tempFile;
    }

    public void setTempFile(String tempFile) {
        this.tempFile = tempFile;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ChosenFile)) {
            return false;
        }

        ChosenFile other = (ChosenFile) obj;
        String otherString = other.getIdString();
        String thisString = this.getIdString();
        return otherString.equals(thisString);
    }

    @Override
    public int hashCode() {
        return this.getIdString().hashCode();
    }

    private String getIdString() {
        return queryUri + ":" + originalPath + ":" + mimeType + ":" + size;
    }
}
