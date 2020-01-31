package com.kbeanie.multipicker.core;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.kbeanie.multipicker.api.Picker;
import com.kbeanie.multipicker.api.callbacks.VideoPickerCallback;
import com.kbeanie.multipicker.api.entity.ChosenVideo;
import com.kbeanie.multipicker.api.exceptions.PickerException;
import com.kbeanie.multipicker.core.threads.VideoProcessorThread;
import com.kbeanie.multipicker.utils.LogUtils;
import com.kbeanie.multipicker.utils.MediaType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Class to pick images (Stored or capture a new image using the device's camera)
 * This class cannot be used directly. User {@link com.kbeanie.multipicker.api.VideoPicker} or
 * {@link com.kbeanie.multipicker.api.CameraVideoPicker}
 */
public abstract class VideoPickerImpl extends PickerManager {
    private final static String TAG = VideoPickerImpl.class.getSimpleName();

    private String path;

    protected VideoPickerCallback callback;
    private boolean generatePreviewImages = true;
    private boolean generateMetadata = true;
    private int quality = 100;

    public VideoPickerImpl(Activity activity, int pickerType) {
        super(activity, pickerType);
    }

    public VideoPickerImpl(Fragment fragment, int pickerType) {
        super(fragment, pickerType);
    }

    public void reinitialize(String path) {
        this.path = path;
    }

    public void setVideoPickerCallback(VideoPickerCallback callback) {
        this.callback = callback;
    }

    public void shouldGeneratePreviewImages(boolean generatePreviewImages) {
        this.generatePreviewImages = generatePreviewImages;
    }

    public void shouldGenerateMetadata(boolean generateMetadata) {
        this.generateMetadata = generateMetadata;
    }

    /**
     * Set quality of generated thumbnail images
     *
     * @param quality Hint to the compressor, 0-100. 0 meaning compress for
     *                small size, 100 meaning compress for max quality. Some
     *                formats, like PNG which is lossless, will ignore the
     *                quality setting. Defaults to 100 (max quality)
     */
    public void setQuality(int quality) {
        this.quality = quality;
    }

    @Override
    @Nullable
    protected String pick() throws PickerException {
        if (callback == null) {
            throw new PickerException("VideoPickerCallback null! Please set one");
        }
        if (pickerType == Picker.PICK_VIDEO_DEVICE) {
            return pickLocalVideo();
        } else if (pickerType == Picker.PICK_VIDEO_CAMERA) {
            path = takeVideoWithCamera();
            return path;
        }
        return null;
    }

    @Nullable
    protected String takeVideoWithCamera() throws PickerException {
        Uri uri;
        String tempFilePath;

        final Context context = getContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tempFilePath = getNewFileLocation("mp4", Environment.DIRECTORY_MOVIES);
            String fileProviderAuthority = getFileProviderAuthority();

            if (tempFilePath == null || fileProviderAuthority == null) {
                return null;
            }

            File file = new File(tempFilePath);
            uri = FileProvider.getUriForFile(context, fileProviderAuthority, file);
            LogUtils.d(TAG, "takeVideoWithCamera: Temp Uri: " + uri.getPath());
        } else {
            tempFilePath = buildFilePath("mp4", Environment.DIRECTORY_MOVIES);
            if (tempFilePath == null) {
                return null;
            }
            uri = Uri.fromFile(new File(tempFilePath));
        }
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);

        if (extras != null) {
            intent.putExtras(extras);
        }
        LogUtils.d(TAG, "Temp Path for Camera capture: " + tempFilePath);
        captureMediaInternal(intent, uri, Picker.PICK_VIDEO_CAMERA);
        return tempFilePath;
    }

    protected String pickLocalVideo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        if (extras != null) {
            intent.putExtras(extras);
        }
        // For reading from external storage (Content Providers)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        pickInternal(intent, Picker.PICK_VIDEO_DEVICE);
        return null;
    }

    @Override
    public void submit(Intent data) {
        if (pickerType == Picker.PICK_VIDEO_CAMERA) {
            handleCameraData(data);
        } else if (pickerType == Picker.PICK_VIDEO_DEVICE) {
            handleGalleryData(data);
        }
    }

    private void handleCameraData(Intent data) {
        LogUtils.d(TAG, "handleCameraData: " + path);
        if (path == null || path.isEmpty()) {
            throw new RuntimeException("Camera Path cannot be null. Re-initialize with correct " +
                    "path value.");
        } else {
            List<String> uris = new ArrayList<>();
            File file = new File(path);
            if (!file.exists()) {
                uris.add(data.getDataString());
            } else {
                uris.add(Uri.fromFile(file).toString());
            }
            processVideos(uris);
        }
    }

    @SuppressLint("NewApi")
    private void handleGalleryData(Intent intent) {
        List<String> uris = new ArrayList<>();
        if (intent != null) {
            if (intent.getDataString() != null && isClipDataApi() && intent.getClipData() == null) {
                String uri = intent.getDataString();
                LogUtils.d(TAG, "handleGalleryData: " + uri);
                uris.add(uri);
            } else if (isClipDataApi()) {
                if (intent.getClipData() != null) {
                    ClipData clipData = intent.getClipData();
                    LogUtils.d(TAG, "handleGalleryData: Multiple videos with ClipData");
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        ClipData.Item item = clipData.getItemAt(i);
                        LogUtils.d(TAG, "Item [" + i + "]: " + item.getUri().toString());
                        uris.add(item.getUri().toString());
                    }
                }
            }
            if (intent.hasExtra("uris")) {
                ArrayList<Uri> paths = intent.getParcelableArrayListExtra("uris");
                for (int i = 0; i < paths.size(); i++) {
                    uris.add(paths.get(i).toString());
                }
            }

            processVideos(uris);
        }
    }

    private void processVideos(List<String> uris) {
        final Context context = getContext();

        VideoProcessorThread thread = new VideoProcessorThread(context,
                getVideoObjects(uris), cacheLocation);
        thread.setRequestId(requestId);
        thread.setShouldGeneratePreviewImages(generatePreviewImages);
        thread.setShouldGenerateMetadata(generateMetadata);
        thread.setThumbnailsImageQuality(quality);
        thread.setVideoPickerCallback(callback);
        thread.start();
    }

    private List<ChosenVideo> getVideoObjects(List<String> uris) {
        List<ChosenVideo> videos = new ArrayList<>();
        for (String uri : uris) {
            ChosenVideo video = new ChosenVideo();
            video.setQueryUri(uri);
            video.setDirectoryType(Environment.DIRECTORY_MOVIES);
            video.setType(MediaType.VIDEO);
            videos.add(video);
        }
        return videos;
    }

    private void onError(final String errorMessage) {
        if (callback != null) {
            final Context context = getContext();
            ((Activity) context).runOnUiThread(() -> callback.onPickerError(errorMessage));
        }
    }
}
