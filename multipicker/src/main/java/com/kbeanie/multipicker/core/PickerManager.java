package com.kbeanie.multipicker.core;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kbeanie.multipicker.api.CacheLocation;
import com.kbeanie.multipicker.api.exceptions.PickerException;
import com.kbeanie.multipicker.storage.StoragePreferences;
import com.kbeanie.multipicker.utils.FileUtils;
import com.kbeanie.multipicker.utils.LogUtils;

import java.io.File;
import java.util.List;
import java.util.UUID;


/**
 * Abstract class for all types of Pickers
 */
public abstract class PickerManager {
    private final static String TAG = PickerManager.class.getSimpleName();

    @Nullable
    private Activity activity;

    @Nullable
    private Fragment fragment;

    @NonNull
    private final Context activityContext;

    public static boolean debuggable;

    protected final int pickerType;

    protected int requestId;

    protected boolean shouldVideo = true; //by default let user pick video

    protected int cacheLocation = CacheLocation.EXTERNAL_STORAGE_APP_DIR;

    protected Bundle extras;

    protected boolean allowMultiple;

    public PickerManager(@NonNull Activity activity, int pickerType) {
        this.activity = activity;
        this.pickerType = pickerType;
        this.activityContext = activity;
        initProperties();
    }

    public PickerManager(Fragment fragment, int pickerType) {
        this.fragment = fragment;
        this.pickerType = pickerType;
        this.activityContext = fragment.requireActivity();
        initProperties();
    }

    private void initProperties() {
        final Context context = getContext();
        debuggable = new StoragePreferences(context.getApplicationContext()).isDebuggable();
    }

    /**
     * Set extras which will be directly passed to the target applications. You should use this
     * to take advantage of specific applications
     * ex. Some applications support cropping, or editing the image itself before they give you
     * the final image
     *
     * @param extras extras
     */
    public void setExtras(Bundle extras) {
        this.extras = extras;
    }

    /**
     * Default cache location is {@link CacheLocation#EXTERNAL_STORAGE_APP_DIR}
     * <p/>
     * If you are setting the (@link CacheLocation#EXTERNAL_STORAGE_PUBLIC_DIR} make sure you
     * have the required permissions
     * available in the Manifest file. Else, a {@link RuntimeException} will be raised.
     * <p/>
     * Permissions required {@link Manifest.permission#WRITE_EXTERNAL_STORAGE} and
     * {@link Manifest.permission#READ_EXTERNAL_STORAGE}
     *
     * @param cacheLocation {@link CacheLocation}
     */
    public void setCacheLocation(int cacheLocation) {
        this.cacheLocation = cacheLocation;

        if (cacheLocation == CacheLocation.EXTERNAL_STORAGE_PUBLIC_DIR) {
            checkIfPermissionsAvailable();
        }
    }

    /**
     * Since {@link CacheLocation#EXTERNAL_STORAGE_PUBLIC_DIR} is deprecated, you will have no
     * option to set the folder name now. If at all you need to copy the files into the public
     * sotrage for exposing them to other applications, you will have to implement the
     * copying/moving the files code yourself.
     *
     * @param folderName name of folder
     */
    @Deprecated
    public void setFolderName(String folderName) {
        final Context context = getContext();
        StoragePreferences preferences = new StoragePreferences(context.getApplicationContext());
        preferences.setFolderName(folderName);
    }

    /**
     * Triggers pick image
     *
     * @return
     */
    protected abstract String pick() throws PickerException;

    /**
     * This method should be called after {@link Activity's onActivityResult(int, int, Intent)}
     * is  called.
     *
     * @param data data intent
     */
    public abstract void submit(Intent data);

    @Nullable
    protected String buildFilePath(String extension, String type) throws PickerException {
        String directoryPath = getDirectory(type);
        if (directoryPath != null) {
            return directoryPath + File.separator + UUID.randomUUID().toString() + "." + extension;
        }
        return null;
    }

    @Nullable
    protected String getDirectory(String type) throws PickerException {
        String directory = null;

        final Context context = getContext();

        switch (cacheLocation) {
            case CacheLocation.EXTERNAL_STORAGE_PUBLIC_DIR:
                directory = FileUtils.getExternalFilesDirectory(type, context);
                break;
            case CacheLocation.EXTERNAL_STORAGE_APP_DIR:
                directory = FileUtils.getExternalFilesDir(type, context);
                break;
            case CacheLocation.EXTERNAL_CACHE_DIR:
                directory = FileUtils.getExternalCacheDir(context);
                break;
            case CacheLocation.INTERNAL_APP_DIR:
                directory = FileUtils.getInternalFileDirectory(context);
                break;
        }
        return directory;
    }

    @NonNull
    protected Context getContext() {
        return activityContext;
    }

    void captureMediaInternal(Intent intent, Uri uri, int type) {
        /*
        grant permissions
        Ref: https://medium.com/@a1cooke/using-v4-support-library-fileprovider-and-camera-intent
        -a45f76879d61#.34hcs7dml
         */
        List<ResolveInfo> resolvedIntentActivities = activityContext
                .getPackageManager().queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo resolvedIntentInfo : resolvedIntentActivities) {
            String packageName = resolvedIntentInfo.activityInfo.packageName;
            activityContext.grantUriPermission(packageName, uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        pickInternal(intent, type);
    }

    protected void pickInternal(Intent intent, int type) {
        if (allowMultiple) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        if (activity != null) {
            activity.startActivityForResult(intent, type);
        } else if (fragment != null) {
            fragment.startActivityForResult(intent, type);
        }
    }

    protected boolean isClipDataApi() {
        return true;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    private void checkIfPermissionsAvailable() {
        final Context context = getContext();

        boolean writePermissionInManifest =
                context.checkCallingOrSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        LogUtils.d(TAG,
                "checkIfPermissionsAvailable: In Manifest(WRITE_EXTERNAL_STORAGE): " + writePermissionInManifest);
        boolean readPermissionInManifest =
                context.checkCallingOrSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        LogUtils.d(TAG,
                "checkIfPermissionsAvailable: In Manifest(READ_EXTERNAL_STORAGE): " + readPermissionInManifest);

        if (!writePermissionInManifest || !readPermissionInManifest) {
            if (!writePermissionInManifest) {
                LogUtils.e(TAG, Manifest.permission.WRITE_EXTERNAL_STORAGE + " permission is " +
                        "missing in manifest file");
            }
            if (!readPermissionInManifest) {
                LogUtils.e(TAG, Manifest.permission.READ_EXTERNAL_STORAGE + " permission is " +
                        "missing in manifest file");
            }
            throw new RuntimeException("Permissions required in Manifest");
        }
    }

    public static long querySizeOfFile(Uri uri, @NonNull Context context) {
        if (uri.toString().startsWith("file")) {
            File file = new File(uri.getPath());
            return file.length();
        } else if (uri.toString().startsWith("content")) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE));
                } else {
                    return 0;
                }
            } catch (Exception e) {
                return 0;
            } finally {
                cursor.close();
            }
        }
        return 0;
    }

    @Nullable
    protected String getNewFileLocation(String extension, String type) {
        File file;
        String filePathName = "";

        final Context context = getContext();

        if (type.equals(Environment.DIRECTORY_MOVIES)) {
            filePathName = "movies";
        } else if (type.equals(Environment.DIRECTORY_PICTURES)) {
            filePathName = "pictures";
        }
        file = new File(context.getFilesDir(), filePathName);
        file.mkdirs();

        file = new File(file.getAbsolutePath() + File.separator + UUID.randomUUID().toString() +
                "." + extension);
        return file.getAbsolutePath();
    }

    @Nullable
    protected String getFileProviderAuthority() {
        final Context context = getContext();
        return context.getPackageName() + ".multipicker.fileprovider";
    }

    public void setDebuggable(boolean debuggable) {
        final Context context = getContext();
        new StoragePreferences(context.getApplicationContext()).setDebuggable(debuggable);
    }
}
