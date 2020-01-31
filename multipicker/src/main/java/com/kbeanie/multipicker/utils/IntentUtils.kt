package com.kbeanie.multipicker.utils

import android.content.Intent
import android.net.Uri
import java.util.*

/**
 * Created by kbibek on 3/2/16.
 */

class IntentUtils(private val data: Intent) {

    /**
     * Converts an Intent received in activity from another app, into an Intent that conforms to
     * the standard that multipicker library needs
     */
    val pickerIntentForSharing: Intent
        get() = Intent().also {
            when (data.action) {
                Intent.ACTION_SEND_MULTIPLE -> {
                    val imageUris: ArrayList<Uri>? = data.extras?.getParcelableArrayList<Uri>(Intent.EXTRA_STREAM)
                    it.putParcelableArrayListExtra("uris", imageUris)
                }
                Intent.ACTION_SEND -> {
                    val imageUri: Uri? = data.getParcelableExtra(Intent.EXTRA_STREAM) as Uri?
                    it.data = imageUri
                }
            }
        }
}