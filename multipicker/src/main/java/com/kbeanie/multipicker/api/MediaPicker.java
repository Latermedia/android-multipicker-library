package com.kbeanie.multipicker.api;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import androidx.fragment.app.Fragment;

import com.kbeanie.multipicker.api.callbacks.FilePickerCallback;
import com.kbeanie.multipicker.api.callbacks.GifPickerCallback;
import com.kbeanie.multipicker.api.callbacks.ImagePickerCallback;
import com.kbeanie.multipicker.api.callbacks.MediaPickerCallback;
import com.kbeanie.multipicker.api.callbacks.VideoPickerCallback;
import com.kbeanie.multipicker.api.entity.ChosenFile;
import com.kbeanie.multipicker.api.entity.ChosenGif;
import com.kbeanie.multipicker.api.entity.ChosenImage;
import com.kbeanie.multipicker.api.entity.ChosenVideo;
import com.kbeanie.multipicker.api.entity.ErrorFile;
import com.kbeanie.multipicker.api.exceptions.PickerException;
import com.kbeanie.multipicker.core.PickerManager;
import com.kbeanie.multipicker.core.threads.FileProcessorThread;
import com.kbeanie.multipicker.core.threads.GifProcessorThread;
import com.kbeanie.multipicker.core.threads.ImageProcessorThread;
import com.kbeanie.multipicker.core.threads.VideoProcessorThread;
import com.kbeanie.multipicker.utils.LogUtils;
import com.kbeanie.multipicker.utils.MediaType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * This is not yet full proof. It has bugs, which doesn't work on all devices. Use this at your
 * own risk.
 */
public class MediaPicker extends PickerManager implements FilePickerCallback, ImagePickerCallback,
        VideoPickerCallback, GifPickerCallback {
    private final static String TAG = MediaPicker.class.getSimpleName();
    private MediaPickerCallback callback;

    private boolean generateThumbnails = true;
    private boolean generateMetadata = true;
    private boolean generatePreviewImages = true;

    /**
     * Constructor for choosing media from an {@link Activity}
     *
     * @param activity activity
     */
    public MediaPicker(Activity activity) {
        super(activity, Picker.PICK_MEDIA);
    }

    /**
     * Constructor for choosing media from a {@link Fragment}
     *
     * @param fragment fragment
     */
    public MediaPicker(Fragment fragment) {
        super(fragment, Picker.PICK_MEDIA);
    }

    public void allowMultiple() {
        this.allowMultiple = true;
    }

    /**
     * Set this to true if you want thumbnails of the media files to be generated.
     *
     * @param generateThumbnails thumbnails
     */
    public void shouldGenerateThumbnails(boolean generateThumbnails) {
        this.generateThumbnails = generateThumbnails;
    }

    /**
     * Set this to true if you want the metadata of the chosen media to be processed
     *
     * @param generateMetadata metadata
     */
    public void shouldGenerateMetadata(boolean generateMetadata) {
        this.generateMetadata = generateMetadata;
    }

    /**
     * Set this to true if you want to allow video
     * @param flag true/false flag
     */
    public void shouldVideo(boolean flag) {
        this.shouldVideo = flag;
    }

    /**
     * Set this to true if you want to generate a preview thumbnail for video files
     *
     * @param generatePreviewImages preview images
     */
    public void shouldGeneratePreviewImages(boolean generatePreviewImages) {
        this.generatePreviewImages = generatePreviewImages;
    }

    @Override
    protected String pick() throws PickerException {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        if (shouldVideo) {
            intent.setType("*/*");
            String[] allowedMimeTypes = {"image/*", "video/*"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, allowedMimeTypes);
        } else {
            intent.setType("image/*");
        }

        if (extras != null) {
            intent.putExtras(extras);
        }

        // For reading from external storage (Content Providers)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        pickInternal(intent, Picker.PICK_MEDIA);
        return null;
    }

    /**
     * Triggers media selection
     */
    public void pickMedia() {
        try {
            pick();
        } catch (PickerException e) {
            if (callback != null) {
                callback.onPickerException(e);
            }
            e.printStackTrace();
        }
    }

    @Override
    public void submit(Intent intent) {
        List<String> uris = new ArrayList<>();
        if (intent != null) {
            if (intent.getDataString() != null && isClipDataApi() && intent.getClipData() == null) {
                String uri = intent.getDataString();
                LogUtils.d(TAG, "submit: Uri: " + uri);
                uris.add(uri);
            } else if (isClipDataApi()) {
                if (intent.getClipData() != null) {
                    ClipData clipData = intent.getClipData();
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
            // For Xiaomi Phones
            if (uris.size() == 0 && intent.hasExtra("pick-result-data")) {
                ArrayList<Uri> paths = intent.getParcelableArrayListExtra("pick-result-data");
                for (int i = 0; i < paths.size(); i++) {
                    uris.add(paths.get(i).toString());
                }
            }
        }

        processMedia(uris);
    }

    private void processMedia(List<String> uris) {
        final Context context = getContext();
        FileProcessorThread thread = new FileProcessorThread(context, getFileObjects(uris),
                cacheLocation);
        thread.setFilePickerCallback(this);
        thread.setRequestId(requestId);
        thread.start();
    }

    /**
     * Listener which gets callbacks when your media is processed and ready to be used.
     *
     * @param callback picker callback
     */
    public void setMediaPickerCallback(MediaPickerCallback callback) {
        this.callback = callback;
    }

    private List<ChosenFile> getFileObjects(List<String> uris) {
        List<ChosenFile> files = new ArrayList<>();
        for (String uri : uris) {
            ChosenFile file = new ChosenFile();
            file.setQueryUri(uri);
            file.setDirectoryType(Environment.DIRECTORY_DOCUMENTS);
            file.setType("file");
            files.add(file);
        }
        return files;
    }

    private List<ChosenVideo> videosToProcess = new ArrayList<>();
    private List<ChosenGif> gifsToProcess = new ArrayList<>();
    private List<ChosenImage> imagesToProcess = new ArrayList<>();

    @Override
    public void onFilesChosen(List<? extends ChosenFile> files) {

        imagesToProcess.clear();
        gifsToProcess.clear();
        videosToProcess.clear();

        for (ChosenFile file : files) {
            String mimeType = file.getMimeType();
            if (mimeType != null && mimeType.contains(MediaType.IMAGE)) {
                if (mimeType.contains(MediaType.GIF)) {
                    ChosenGif gif = new ChosenGif();
                    gif.setQueryUri(Uri.fromFile(new File(file.getOriginalPath())).toString());
                    gif.setType(MediaType.GIF);
                    gif.setDirectoryType(Environment.DIRECTORY_PICTURES);
                    gif.setDisplayName(file.getDisplayName());
                    gif.setExtension(file.getExtension());
                    gifsToProcess.add(gif);
                } else {
                    ChosenImage image = new ChosenImage();
                    image.setQueryUri(Uri.fromFile(new File(file.getOriginalPath())).toString());
                    image.setType(MediaType.IMAGE);
                    image.setDirectoryType(Environment.DIRECTORY_PICTURES);
                    image.setDisplayName(file.getDisplayName());
                    image.setExtension(file.getExtension());
                    imagesToProcess.add(image);
                }
            } else if (mimeType != null && mimeType.contains(MediaType.VIDEO)) {
                ChosenVideo video = new ChosenVideo();
                video.setQueryUri(Uri.fromFile(new File(file.getOriginalPath())).toString());
                video.setType(MediaType.VIDEO);
                video.setDirectoryType(Environment.DIRECTORY_MOVIES);
                video.setDisplayName(file.getDisplayName());
                video.setExtension(file.getExtension());
                videosToProcess.add(video);
            }
        }

        //Following sequence checks are important Why? Well, stupid library that we want to get
        // rid of someday!

        final Context context = getContext();

        if (imagesToProcess.size() > 0) {
            ImageProcessorThread imgThread = new ImageProcessorThread(context,
                    imagesToProcess, cacheLocation);
            imgThread.setImagePickerCallback(this);
            imgThread.setShouldGenerateMetadata(generateMetadata);
            imgThread.setShouldGenerateThumbnails(generateThumbnails);
            imgThread.setRequestId(requestId);
            imgThread.start();
        } else if (gifsToProcess.size() > 0) {
            GifProcessorThread gifThread = new GifProcessorThread(context, gifsToProcess,
                    cacheLocation);
            gifThread.setGifPickerCallback(this);
            gifThread.setShouldGenerateMetadata(generateMetadata);
            gifThread.setShouldGenerateThumbnails(generateThumbnails);
            gifThread.setRequestId(requestId);
            gifThread.start();
        } else if (videosToProcess.size() > 0) {
            VideoProcessorThread vidThread = new VideoProcessorThread(context,
                    videosToProcess, cacheLocation);
            vidThread.setRequestId(requestId);
            vidThread.setShouldGenerateMetadata(generateMetadata);
            vidThread.setShouldGeneratePreviewImages(generatePreviewImages);
            vidThread.setVideoPickerCallback(this);
            vidThread.start();
        }
    }

    @Override
    public void onPickerError(String message) {
        callback.onPickerError(message);
    }

    @Override
    public void onErrorFiles(List<ErrorFile> errorFiles) {
        callback.onErrorFiles(errorFiles);
    }

    @Override
    public void onPickerException(Throwable throwable) {
        callback.onPickerException(throwable);
    }

    private List<ChosenImage> images;
    private List<ChosenGif> gifs;

    /**
     * NOTE: onImagesChosen, onGifsChosen and onVideosChosen are poorly implemented methods
     * It's funny how sequence is important
     * Since imagesToProcess is checked first `onImagesChosen` method needs to check rest two
     * threads gifsToProcess
     * and videosToProcess
     * gifsToProcess is second in check so `onGifsChosen` methods need to check remaining
     * videosToProcess thread
     * And videosToProcess is last in condition check it doesn't need to start thread for anyone
     * else
     *
     * @param images chosen images
     */
    @Override
    public void onImagesChosen(List<ChosenImage> images) {
        this.images = images;

        final Context context = getContext();

        if (gifsToProcess.size() > 0) {
            GifProcessorThread gifThread = new GifProcessorThread(context, gifsToProcess,
                    cacheLocation);
            gifThread.setGifPickerCallback(this);
            gifThread.setShouldGenerateMetadata(generateMetadata);
            gifThread.setShouldGenerateThumbnails(generateThumbnails);
            gifThread.setRequestId(requestId);
            gifThread.start();
        } else if (videosToProcess.size() > 0) {
            VideoProcessorThread vidThread = new VideoProcessorThread(context,
                    videosToProcess, cacheLocation);
            vidThread.setRequestId(requestId);
            vidThread.setShouldGenerateMetadata(generateMetadata);
            vidThread.setShouldGeneratePreviewImages(generatePreviewImages);
            vidThread.setVideoPickerCallback(this);
            vidThread.start();
        } else {
            if (callback != null) {
                callback.onMediaChosen(images, null, null);
            }
        }
    }

    @Override
    public void onGifsChosen(List<ChosenGif> gifs) {
        this.gifs = gifs;
        final Context context = getContext();

        if (videosToProcess.size() > 0) {
            VideoProcessorThread vidThread = new VideoProcessorThread(context,
                    videosToProcess, cacheLocation);
            vidThread.setRequestId(requestId);
            vidThread.setShouldGenerateMetadata(generateMetadata);
            vidThread.setShouldGeneratePreviewImages(generatePreviewImages);
            vidThread.setVideoPickerCallback(this);
            vidThread.start();
        } else {
            if (callback != null) {
                callback.onMediaChosen(images, null, gifs);
            }
        }
    }

    @Override
    public void onVideosChosen(List<ChosenVideo> videos) {
        if (callback != null) {
            callback.onMediaChosen(images, videos, gifs);
        }
    }

    @Override
    public void onProcessingFile(int oneOf, int total) {
        if (callback != null) {
            callback.onProcessingFile(oneOf, total);
        }
    }
}
