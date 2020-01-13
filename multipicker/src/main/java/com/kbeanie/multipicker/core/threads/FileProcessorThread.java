package com.kbeanie.multipicker.core.threads;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import com.kbeanie.multipicker.api.CacheLocation;
import com.kbeanie.multipicker.api.callbacks.FilePickerCallback;
import com.kbeanie.multipicker.api.entity.ChosenFile;
import com.kbeanie.multipicker.api.entity.ChosenImage;
import com.kbeanie.multipicker.api.entity.ErrorFile;
import com.kbeanie.multipicker.api.exceptions.PickerException;
import com.kbeanie.multipicker.utils.BitmapUtils;
import com.kbeanie.multipicker.utils.FileUtils;
import com.kbeanie.multipicker.utils.LogUtils;
import com.kbeanie.multipicker.utils.MediaType;
import com.kbeanie.multipicker.utils.MimeUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static com.kbeanie.multipicker.utils.StreamHelper.close;
import static com.kbeanie.multipicker.utils.StreamHelper.flush;
import static com.kbeanie.multipicker.utils.StreamHelper.verifyStream;

/**
 * Created by kbibek on 2/20/16.
 */
public class FileProcessorThread extends Thread {
    final static int THUMBNAIL_BIG = 1;
    final static int THUMBNAIL_SMALL = 2;

    private final static String TAG = "FileProcessorThread";

    private final int cacheLocation;
    protected final Context context;
    protected final List<? extends ChosenFile> files;
    protected final List<ErrorFile> errorFiles;

    @Nullable
    private FilePickerCallback callback;

    private int requestId;

    public FileProcessorThread(@NonNull Context context, List<? extends ChosenFile> files,
                               int cacheLocation) {
        this.context = context;
        this.files = files;
        this.errorFiles = new ArrayList<>();
        this.cacheLocation = cacheLocation;
    }

    void checkErrorFiles() {
        for (Iterator<? extends ChosenFile> iterator = files.iterator(); iterator.hasNext(); ) {
            ChosenFile item = iterator.next();
            if (!item.isSuccess()) {
                errorFiles.add(new ErrorFile(item.toString()));
                iterator.remove();
            }
        }
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    @Override
    public void run() {
        processFiles();
        checkErrorFiles();
        if (callback != null) {
            onDone();
        }
    }

    private void onDone() {
        if (callback != null) {
            getActivityFromContext().runOnUiThread(() -> {
                errorFilesCallback();
                callback.onFilesChosen(files);
            });
        }
    }

    private void errorFilesCallback() {
        if (callback != null && !errorFiles.isEmpty()) {
            callback.onErrorFiles(errorFiles);
        }
    }

    private void processFiles() {
        int totalFiles = files.size();
        for (int i = 0; i < totalFiles; i++) {
            ChosenFile file = files.get(i);
            try {
                if (callback != null) {
                    callback.onProcessingFile(i + 1, totalFiles);
                }
                file.setRequestId(requestId);
                LogUtils.d(TAG, "processFile: Before: " + file.toString());
                processFile(file);
                postProcess(file);
                file.setSuccess(true);
                LogUtils.d(TAG, "processFile: Final Path: " + file.toString());
            } catch (PickerException e) {
                file.setSuccess(false);
                if (callback != null) {
                    callback.onPickerException(e);
                }
                e.printStackTrace();
            }

        }
    }

    protected void postProcessFiles() {
        for (ChosenFile file : files) {
            try {
                postProcess(file);
            } catch (Exception e) {
                if (callback != null) {
                    callback.onPickerException(e);
                }
                e.printStackTrace();
            }
        }
    }

    private void postProcess(ChosenFile file) throws PickerException {
        File f = new File(file.getOriginalPath());
        file.setSize(f.length());
        copyFileToFolder(file);
    }

    private void copyFileToFolder(ChosenFile file) throws PickerException {
        LogUtils.d(TAG, "copyFileToFolder: folder: " + file.getDirectoryType());
        LogUtils.d(TAG, "copyFileToFolder: extension: " + file.getExtension());
        LogUtils.d(TAG, "copyFileToFolder: mimeType: " + file.getMimeType());
        LogUtils.d(TAG, "copyFileToFolder: type: " + file.getType());
        if (file.getType().equals(MediaType.IMAGE)) {
            file.setDirectoryType(Environment.DIRECTORY_PICTURES);
        } else if (file.getType().equals(MediaType.VIDEO)) {
            file.setDirectoryType(Environment.DIRECTORY_MOVIES);
        }
        String outputPath = getTargetLocationToCopy(file);
        LogUtils.d(TAG, "copyFileToFolder: Out Path: " + outputPath);
        // Check if file is already in the required destination
        if (outputPath.equals(file.getOriginalPath())) {
            return;
        }
        try {
            File inputFile = new File(file.getOriginalPath());
            File copyTo = new File(outputPath);
            FileUtils.copyFile(inputFile, copyTo);
            file.setOriginalPath(copyTo.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            throw new PickerException(e);
        }
    }

    private void processFile(ChosenFile file) throws PickerException {
        String uri = file.getQueryUri();
        LogUtils.d(TAG, "processFile: uri" + uri);
        if (uri.startsWith("file://") || uri.startsWith("/")) {
            sanitizeUri(file);
            file.setDisplayName(Uri.parse(file.getOriginalPath()).getLastPathSegment());
            file.setMimeType(guessMimeTypeFromUrl(file.getOriginalPath(), file.getType()));
        } else if (uri.startsWith("http")) {
            downloadAndSaveFile(file);
        } else if (uri.startsWith("content:")) {
            getAbsolutePathIfAvailable(file);
        }
        uri = file.getOriginalPath();
        // Still content:: Try ContentProvider stream import
        if (uri.startsWith("content:")) {
            getFromContentProvider(file);
        }
        uri = file.getOriginalPath();
        // Still content:: Try ContentProvider stream import alternate
        if (uri.startsWith("content:")) {
            getFromContentProviderAlternate(file);
        }

        // Check for URL Encoded file paths
        try {
            String decodedURL = Uri.parse(Uri.decode(file.getOriginalPath())).toString();
            if (!decodedURL.equals(file.getOriginalPath())) {
                file.setOriginalPath(decodedURL);
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onPickerException(e);
            }
            e.printStackTrace();
        }
    }

    // If starts with file: (For some content providers, remove the file prefix)
    private void sanitizeUri(ChosenFile file) {
        if (file.getQueryUri().startsWith("file://")) {
            file.setOriginalPath(file.getQueryUri().substring(7));
        }
    }

    private void getFromContentProviderAlternate(ChosenFile file) throws PickerException {
        BufferedOutputStream outStream = null;
        BufferedInputStream bStream = null;

        try {
            InputStream inputStream = context.getContentResolver()
                    .openInputStream(Uri.parse(file.getOriginalPath()));
            if (inputStream != null) {
                bStream = new BufferedInputStream(inputStream);
            }

            String mimeType = URLConnection.guessContentTypeFromStream(inputStream);

            verifyStream(file.getOriginalPath(), bStream);

            String localFilePath = generateFileName(file);

            outStream = new BufferedOutputStream(new FileOutputStream(localFilePath));
            byte[] buf = new byte[2048];
            int len;
            while ((len = bStream.read(buf)) > 0) {
                outStream.write(buf, 0, len);
            }
            file.setOriginalPath(localFilePath);
            if (file.getMimeType() != null && file.getMimeType().contains("/*")) {
                if (mimeType != null && !mimeType.isEmpty()) {
                    file.setMimeType(mimeType);
                } else {
                    file.setMimeType(guessMimeTypeFromUrl(file.getOriginalPath(), file.getType()));
                }
            }
        } catch (IOException e) {
            throw new PickerException(e);
        } catch (UnsupportedOperationException e) {
            throw new PickerException(e);
        } finally {
            flush(outStream);
            close(bStream);
            close(outStream);
        }
    }

    private void getFromContentProvider(ChosenFile file) throws PickerException {

        BufferedInputStream inputStream = null;
        BufferedOutputStream outStream = null;
        ParcelFileDescriptor parcelFileDescriptor = null;
        try {
            String localFilePath = generateFileName(file);
            parcelFileDescriptor = context
                    .getContentResolver().openFileDescriptor(Uri.parse(file.getOriginalPath()),
                            "r");
            verifyStream(file.getOriginalPath(), parcelFileDescriptor);

            FileDescriptor fileDescriptor = parcelFileDescriptor
                    .getFileDescriptor();

            inputStream = new BufferedInputStream(new FileInputStream(fileDescriptor));
            String mimeType = URLConnection.guessContentTypeFromStream(inputStream);
            BufferedInputStream reader = new BufferedInputStream(inputStream);

            outStream = new BufferedOutputStream(
                    new FileOutputStream(localFilePath));
            byte[] buf = new byte[2048];
            int len;
            while ((len = reader.read(buf)) > 0) {
                outStream.write(buf, 0, len);
            }
            flush(outStream);
            file.setOriginalPath(localFilePath);
            if (file.getMimeType() != null && file.getMimeType().contains("/*")) {
                if (mimeType != null && !mimeType.isEmpty()) {
                    file.setMimeType(mimeType);
                } else {
                    file.setMimeType(guessMimeTypeFromUrl(file.getOriginalPath(), file.getType()));
                }
            }
        } catch (IOException e) {
            throw new PickerException(e);
        } catch (UnsupportedOperationException e) {
            throw new PickerException(e);
        } finally {
            close(parcelFileDescriptor);
            flush(outStream);
            close(outStream);
            close(inputStream);
        }
    }

    // Try to get a local copy if available

    private void getAbsolutePathIfAvailable(ChosenFile file) {
        String[] projection = {MediaStore.MediaColumns.DATA
                , MediaStore.MediaColumns.DISPLAY_NAME
                , MediaStore.MediaColumns.MIME_TYPE};

        // Workaround for various implementations for Google Photos/Picasa
        if (file.getQueryUri().startsWith(
                "content://com.android.gallery3d.provider")) {
            file.setOriginalPath(Uri.parse(file.getQueryUri().replace(
                    "com.android.gallery3d", "com.google.android.gallery3d")).toString());
        } else {
            file.setOriginalPath(file.getQueryUri());
        }

        final Uri originalUri = Uri.parse(file.getOriginalPath());

        // Try to see if there's a cached local copy that is available
        if (file.getOriginalPath().startsWith("content://")) {
            try (Cursor cursor = context.getContentResolver().query(originalUri, projection,
                    null, null, null)) {
                if (cursor != null) {
                    trySettingOriginalPath(cursor, file);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onPickerException(e);
                }
                e.printStackTrace();
            }
        }

        // Check if DownloadsDocument in which case, we can get the local copy by using the
        // content provider
        if (file.getOriginalPath().startsWith("content:") && isDownloadsDocument(originalUri)) {
            String[] data = getPathAndMimeType(originalUri, file.getType());
            if (data != null) {
                if (data[0] != null) {
                    file.setOriginalPath(data[0]);
                }
                if (data[1] != null) {
                    file.setMimeType(data[1]);
                }
            }
        }
    }

    /**
     * Update's file original path in case local copy available
     *
     * @param cursor database cursor
     * @param file   chosen file object
     */
    private void trySettingOriginalPath(@NonNull Cursor cursor, ChosenFile file) {
        cursor.moveToFirst();

        // Samsung Bug
        if (!file.getOriginalPath().contains("com.sec.android.gallery3d.provider")) {
            if (isValidColumnIndex(cursor, MediaStore.MediaColumns.DATA)) {
                String path = cursor.getString(cursor
                        .getColumnIndexOrThrow(MediaStore.MediaColumns.DATA));
                if (path != null) {
                    file.setOriginalPath(path);
                }
                LogUtils.d(TAG, "processFile: Path: " + path);
            }
        }

        if (isValidColumnIndex(cursor, MediaStore.MediaColumns.DISPLAY_NAME)) {
            String displayName =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME));
            if (displayName != null) {
                file.setDisplayName(displayName);
            }
        }

        if (isValidColumnIndex(cursor, MediaStore.MediaColumns.MIME_TYPE)) {
            String mimeType =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE));
            if (mimeType != null) {
                file.setMimeType(mimeType);
            }
        }

        cursor.close();
    }

    /**
     * Checks if column exist in table
     *
     * @param cursor     database cursor
     * @param columnName column name to check
     * @return boolean as flag true/false
     */
    private boolean isValidColumnIndex(Cursor cursor, String columnName) {
        return cursor != null && cursor.getColumnIndex(columnName) != -1;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private String[] getPathAndMimeType(@NonNull Uri uri, @Nullable String fileType) {
        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("primary".equalsIgnoreCase(type)) {
                    return getDataAndMimeType(Environment.getExternalStorageDirectory() + "/" + split[1], fileType);
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                final String[] split = id.split(":");
                final String type = split[0];

                if ("raw".equalsIgnoreCase(type)) {
                    return getDataAndMimeType(split[1], fileType);
                } else {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        String[] contentUriPrefixesToTry = new String[]{
                                "content://downloads/public_downloads",
                                "content://downloads/my_downloads",
                                "content://downloads/all_downloads"
                        };

                        for (String contentUriPrefix : contentUriPrefixesToTry) {
                            final Uri contentUri =
                                    ContentUris.withAppendedId(Uri.parse(contentUriPrefix),
                                            Long.valueOf(id));
                            try {
                                String[] dataAndMimeType = getDataAndMimeType(contentUri, null,
                                        null, fileType);
                                if (dataAndMimeType != null && dataAndMimeType[0] != null) {
                                    return dataAndMimeType;
                                }
                            } catch (Exception ignore) {
                                //do nothing
                            }
                        }

                        // path could not be retrieved using ContentResolver,
                        // therefore copy file to accessible cache using streams
                        //Source: https://github.com/iPaulPro/aFileChooser/issues/101
                        String fileName = getFileName(context, uri);
                        File cacheDir = getDocumentCacheDir(context);
                        File generateFile = generateFileName(fileName, cacheDir);
                        String destinationPath = null;
                        if (generateFile != null) {
                            destinationPath = generateFile.getAbsolutePath();
                            saveFileFromUri(context, uri, destinationPath);
                        }
                        if (destinationPath != null) {
                            return getDataAndMimeType(destinationPath,
                                    guessMimeTypeFromUrl(destinationPath, fileType));
                        }
                    }
                    return getDataAndMimeType(uri, null, null, fileType);
                }
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if (MediaType.IMAGE.equals(type) || MediaType.GIF.equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if (MediaType.VIDEO.equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = MediaStore.MediaColumns._ID + "=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataAndMimeType(contentUri, selection, selectionArgs, fileType);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataAndMimeType(uri, null, null, fileType);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return getDataAndMimeType(uri.getPath(), fileType);
        }

        return getDataAndMimeType(uri.toString(), null);
    }


    //region workaround for crashes happens on Android 7 (non Samsung device)
    private String getFileName(@NonNull Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        String filename = null;

        if (mimeType == null) {
            String[] data = getPathAndMimeType(uri, null);
            if (data[0] == null) {
                filename = getName(uri.toString());
            } else {
                File file = new File(data[0]);
                filename = file.getName();
            }
        } else {
            Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
            if (returnCursor != null) {
                int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                returnCursor.moveToFirst();
                filename = returnCursor.getString(nameIndex);
                returnCursor.close();
            }
        }

        return filename;
    }

    private String getName(String filename) {
        if (filename == null) {
            return null;
        }
        int index = filename.lastIndexOf('/');
        return filename.substring(index + 1);
    }

    private File getDocumentCacheDir(@NonNull Context context) {
        File dir = new File(context.getCacheDir(), Environment.DIRECTORY_DOCUMENTS);
        if (!dir.exists()) {
            boolean mkdirs = dir.mkdirs();
        }
        return dir;
    }

    @Nullable
    private File generateFileName(@Nullable String name, File directory) {
        if (name == null) {
            return null;
        }

        File file = new File(directory, name);

        if (file.exists()) {
            String fileName = name;
            String extension = "";
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0) {
                fileName = name.substring(0, dotIndex);
                extension = name.substring(dotIndex);
            }

            int index = 0;

            while (file.exists()) {
                index++;
                name = fileName + '(' + index + ')' + extension;
                file = new File(directory, name);
            }
        }

        try {
            if (!file.createNewFile()) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        return file;
    }

    private void saveFileFromUri(Context context, Uri uri, String destinationPath) {
        InputStream inputStream = null;
        BufferedOutputStream outputStream = null;
        try {
            inputStream = context.getContentResolver().openInputStream(uri);
            outputStream = new BufferedOutputStream(new FileOutputStream(destinationPath, false));
            byte[] buf = new byte[1024];
            inputStream.read(buf);
            do {
                outputStream.write(buf);
            } while (inputStream.read(buf) != -1);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    //endregion

    private String[] getDataAndMimeType(@Nullable String path, @Nullable String type) {
        String[] data = new String[2];
        data[0] = path;
        data[1] = guessMimeTypeFromUrl(path, type);
        return data;
    }

    private String[] getDataAndMimeType(Uri uri, String selection,
                                        String[] selectionArgs, String type) {
        String[] data = new String[2];

        String[] projection = {MediaStore.MediaColumns.DATA};
        try (Cursor cursor = context.getContentResolver().query(uri, projection, selection,
                selectionArgs,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                if (isValidColumnIndex(cursor, MediaStore.MediaColumns.DATA)) {
                    final int columnIndex =
                            cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                    final String path = cursor.getString(columnIndex);
                    if (path != null) {
                        data[0] = path;
                        data[1] = guessMimeTypeFromUrl(path, type);
                        return data;
                    }
                }
            }
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    private void downloadAndSaveFile(ChosenFile file) {
        String localFilePath;
        try {
            URL u = new URL(file.getQueryUri());
            HttpURLConnection urlConnection = (HttpURLConnection) u.openConnection();
            InputStream stream = new BufferedInputStream(urlConnection.getInputStream());
            BufferedInputStream bStream = new BufferedInputStream(stream);

            String mimeType = guessMimeTypeFromUrl(file.getQueryUri(), file.getType());
            if (mimeType == null) {
                mimeType = URLConnection.guessContentTypeFromStream(stream);
            }

            if (mimeType == null && file.getQueryUri().contains(".")) {
                int index = file.getQueryUri().lastIndexOf(".");
                mimeType = file.getType() + "/" + file.getQueryUri().substring(index + 1);
            }

            if (mimeType == null) {
                mimeType = file.getType() + "/*";
            }

            file.setMimeType(mimeType);

            localFilePath = generateFileName(file);

            File localFile = new File(localFilePath);

            FileOutputStream fileOutputStream = new FileOutputStream(localFile);

            byte[] buffer = new byte[2048];
            int len;
            while ((len = bStream.read(buffer)) > 0) {
                fileOutputStream.write(buffer, 0, len);
            }
            fileOutputStream.flush();
            fileOutputStream.close();
            bStream.close();
            file.setOriginalPath(localFilePath);
        } catch (Exception e) {
            if (callback != null) {
                callback.onPickerException(e);
            }
            e.printStackTrace();
        }
    }

    @NonNull
    private String getTargetDirectory(String type) throws PickerException {
        String directory;
        switch (cacheLocation) {
            case CacheLocation.EXTERNAL_STORAGE_APP_DIR:
                directory = FileUtils.getExternalFilesDir(type, context);
                break;
            case CacheLocation.EXTERNAL_CACHE_DIR:
                directory = FileUtils.getExternalCacheDir(context);
                break;
            case CacheLocation.INTERNAL_APP_DIR:
                directory = FileUtils.getInternalFileDirectory(context);
                break;
            case CacheLocation.EXTERNAL_STORAGE_PUBLIC_DIR:
            default:
                directory = FileUtils.getExternalFilesDirectory(type, context);
                break;
        }
        return directory;
    }

    // Guess File extension from the file name
    private String guessExtensionFromUrl(@Nullable String url) {
        try {
            return MimeTypeMap.getFileExtensionFromUrl(url);
        } catch (Exception e) {
            return null;
        }
    }

    // Guess Mime Type from the file extension
    private String guessMimeTypeFromUrl(@Nullable String url, @Nullable String type) {
        String mimeType;
        String extension = guessExtensionFromUrl(url);
        if (extension == null || extension.isEmpty()) {
            if (url != null && url.contains(".")) {
                int index = url.lastIndexOf(".");
                extension = url.substring(index + 1);
            } else {
                extension = "*";
            }
        }
        if ("file".equals(type)) {
            mimeType = MimeUtils.guessMimeTypeFromExtension(extension);
        } else {
            mimeType = type + "/" + extension;
        }
        return mimeType;
    }

    private String getTargetLocationToCopy(ChosenFile file) throws PickerException {
        String fileName = file.getDisplayName();
        if (fileName == null || fileName.isEmpty()) {
            fileName = UUID.randomUUID().toString();
        }
        // If File name already contains an extension, we don't need to guess the extension
        if (!fileName.contains(".")) {
            String extension = file.getFileExtensionFromMimeType();
            if (extension != null && !extension.isEmpty()) {
                fileName += extension;
                file.setExtension(extension);
            }
        }

        String probableFileName = fileName;
        File probableFile = new File(getTargetDirectory(file.getDirectoryType()) + File.separator
                + probableFileName);
        return probableFile.getAbsolutePath();
    }

    private String generateFileName(ChosenFile file) throws PickerException {
        String fileName = file.getDisplayName();
        if (fileName == null || fileName.isEmpty()) {
            fileName = UUID.randomUUID().toString();
        }
        // If File name already contains an extension, we don't need to guess the extension
        if (!fileName.contains(".")) {
            String extension = file.getFileExtensionFromMimeType();
            if (extension != null && !extension.isEmpty()) {
                fileName += extension;
                file.setExtension(extension);
            }
        }

        String mimeType = file.getMimeType();
        if (mimeType == null || mimeType.isEmpty()) {
            file.setMimeType(guessMimeTypeFromUrl(file.getOriginalPath(), file.getType()));
        }

        String probableFileName = fileName;
        File probableFile = new File(getTargetDirectory(file.getDirectoryType()) + File.separator
                + probableFileName);
        //int counter = 0;
        //using current time to uniquely name files, counter could cause issues
        long currentTimeInMillis;
        while (probableFile.exists()) {
            currentTimeInMillis = System.currentTimeMillis();
            if (fileName.contains(".")) {
                int indexOfDot = fileName.lastIndexOf(".");
                probableFileName =
                        fileName.substring(0, indexOfDot - 1) + "-" + currentTimeInMillis + "." + fileName.substring(indexOfDot + 1);
            } else {
                probableFileName = fileName + "(" + currentTimeInMillis + ")";
            }
            probableFile = new File(getTargetDirectory(file.getDirectoryType()) + File.separator
                    + probableFileName);
        }
        fileName = probableFileName;

        file.setDisplayName(fileName);

        return getTargetDirectory(file.getDirectoryType()) + File.separator
                + fileName;
    }

    String generateFileNameForVideoPreviewImage() throws PickerException {
        String fileName = UUID.randomUUID().toString();
        // If File name already contains an extension, we don't need to guess the extension
        String extension = ".jpg";
        fileName += extension;
        return getTargetDirectory(Environment.DIRECTORY_PICTURES) + File.separator
                + fileName;
    }


    Activity getActivityFromContext() {
        return (Activity) context;
    }

    public void setFilePickerCallback(FilePickerCallback callback) {
        this.callback = callback;
    }

    ChosenImage ensureMaxWidthAndHeight(int maxWidth, int maxHeight, int quality,
                                        ChosenImage image) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BufferedInputStream boundsOnlyStream =
                    new BufferedInputStream(new FileInputStream(image.getOriginalPath()));
            Bitmap bitmap = BitmapFactory.decodeStream(boundsOnlyStream, null, options);
            if (bitmap != null) {
                bitmap.recycle();
            }
            boundsOnlyStream.close();

            int imageWidth = options.outWidth;
            int imageHeight = options.outHeight;

            int[] scaledDimension = BitmapUtils.getScaledDimensions(imageWidth, imageHeight,
                    maxWidth, maxHeight);
            if (!(scaledDimension[0] == imageWidth && scaledDimension[1] == imageHeight)) {
                ExifInterface originalExifInterface = new ExifInterface(image.getOriginalPath());
                String originalRotation =
                        originalExifInterface.getAttribute(ExifInterface.TAG_ORIENTATION);
                BufferedInputStream scaledInputStream =
                        new BufferedInputStream(new FileInputStream(image.getOriginalPath()));
                options.inJustDecodeBounds = false;
                bitmap = BitmapFactory.decodeStream(scaledInputStream, null, options);
                scaledInputStream.close();
                if (bitmap != null) {
                    File original = new File(image.getOriginalPath());
                    image.setTempFile(original.getAbsolutePath());
                    File file = new File(
                            (original.getParent() + File.separator + original.getName()
                                    .replace(".", "-resized.")));
                    FileOutputStream stream = new FileOutputStream(file);

                    Matrix matrix = new Matrix();
                    matrix.postScale((float) scaledDimension[0] / imageWidth,
                            (float) scaledDimension[1] / imageHeight);

                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                            bitmap.getHeight(), matrix, false);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream);
                    image.setOriginalPath(file.getAbsolutePath());
                    ExifInterface resizedExifInterface = new ExifInterface(file.getAbsolutePath());
                    resizedExifInterface.setAttribute(ExifInterface.TAG_ORIENTATION,
                            originalRotation);
                    resizedExifInterface.saveAttributes();
                    image.setWidth(scaledDimension[0]);
                    image.setHeight(scaledDimension[1]);
                    stream.close();
                }
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onPickerException(e);
            }
            e.printStackTrace();
        }
        return image;
    }

    String downScaleAndSaveImage(String image, int scale, int quality) throws PickerException {

        FileOutputStream stream = null;
        Bitmap bitmap;
        try {
            BitmapFactory.Options optionsForGettingDimensions = new BitmapFactory.Options();
            optionsForGettingDimensions.inJustDecodeBounds = true;
            BufferedInputStream boundsOnlyStream =
                    new BufferedInputStream(new FileInputStream(image));
            bitmap = BitmapFactory.decodeStream(boundsOnlyStream, null,
                    optionsForGettingDimensions);
            if (bitmap != null) {
                bitmap.recycle();
            }
            boundsOnlyStream.close();
            int w, l;
            w = optionsForGettingDimensions.outWidth;
            l = optionsForGettingDimensions.outHeight;

            ExifInterface exif = new ExifInterface(image);

            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            int rotate = 0;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = -90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
            }

            int what = w > l ? w : l;

            BitmapFactory.Options options = new BitmapFactory.Options();
            if (what > 3000) {
                options.inSampleSize = scale * 6;
            } else if (what > 2000) {
                options.inSampleSize = scale * 5;
            } else if (what > 1500) {
                options.inSampleSize = scale * 4;
            } else if (what > 1000) {
                options.inSampleSize = scale * 3;
            } else if (what > 400) {
                options.inSampleSize = scale * 2;
            } else {
                options.inSampleSize = scale;
            }

            options.inJustDecodeBounds = false;
            // TODO: Sometime the decode File Returns null for some images
            // For such cases, thumbnails can't be created.
            // Thumbnails will link to the original file
            BufferedInputStream scaledInputStream =
                    new BufferedInputStream(new FileInputStream(image));
            bitmap = BitmapFactory.decodeStream(scaledInputStream, null, options);
//            verifyBitmap(fileImage, bitmap);
            scaledInputStream.close();
            if (bitmap != null) {
                File original = new File(URLDecoder.decode(image, Charset.defaultCharset().name()));
                File file = new File(
                        (original.getParent() + File.separator + original.getName()
                                .replace(".", "-scale-" + scale + ".")));
                stream = new FileOutputStream(file);
                if (rotate != 0) {
                    Matrix matrix = new Matrix();
                    matrix.setRotate(rotate);
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                            bitmap.getHeight(), matrix, false);
                }

                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream);
                return file.getAbsolutePath();
            }

        } catch (Exception e) {
            throw new PickerException("Error while generating thumbnail: " + scale + " " + image);
        } finally {
            flush(stream);
            close(stream);
        }

        return null;
    }

    String getWidthOfImage(String path) {
        String width = "";
        try {
            ExifInterface exif = new ExifInterface(path);
            width = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH);
            if ("0".equals(width)) {
                SoftReference<Bitmap> bmp = getBitmapImage(path);
                width = Integer.toString(bmp.get().getWidth());
                bmp.clear();
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onPickerException(e);
            }
            e.printStackTrace();
        }
        return width;
    }

    String getHeightOfImage(String path) {
        String height = "";
        try {
            ExifInterface exif = new ExifInterface(path);
            height = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH);
            if ("0".equals(height)) {
                SoftReference<Bitmap> bmp = getBitmapImage(path);
                height = Integer.toString(bmp.get().getHeight());
                bmp.clear();
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onPickerException(e);
            }
            e.printStackTrace();
        }
        return height;
    }

    private SoftReference<Bitmap> getBitmapImage(String path) {
        SoftReference<Bitmap> bitmap;
        bitmap =
                new SoftReference<>(BitmapFactory.decodeFile(Uri.fromFile(new File(path)).getPath()));
        return bitmap;
    }

    protected int getOrientation(@NonNull String image) {
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try {
            ExifInterface exif = new ExifInterface(image);

            orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            if (callback != null) {
                callback.onPickerException(e);
            }
            e.printStackTrace();
        }
        return orientation;
    }
}
