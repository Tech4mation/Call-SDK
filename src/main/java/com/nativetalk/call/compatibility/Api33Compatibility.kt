
package com.nativetalk.call

import android.Manifest
import android.annotation.TargetApi
import android.content.Context
import androidx.fragment.app.Fragment

@TargetApi(33)
class Api33Compatibility {
    companion object {
        fun requestPostNotificationsPermission(fragment: Fragment, code: Int) {
            fragment.requestPermissions(
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                code
            )
        }

        fun hasPostNotificationsPermission(context: Context): Boolean {
            return Compatibility.hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        }

        fun requestReadMediaAndCameraPermissions(fragment: Fragment, code: Int) {
            fragment.requestPermissions(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.CAMERA
                ),
                code
            )
        }

        fun hasReadExternalStoragePermission(context: Context): Boolean {
            return Compatibility.hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ||
                Compatibility.hasPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ||
                Compatibility.hasPermission(context, Manifest.permission.READ_MEDIA_AUDIO)
        }
    }
}
