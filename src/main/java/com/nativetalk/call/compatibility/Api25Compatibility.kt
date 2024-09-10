
package com.nativetalk.call

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Build
import android.provider.Settings

@TargetApi(25)
class Api25Compatibility {
    companion object {
        @SuppressLint("MissingPermission")
        fun getDeviceName(context: Context): String {
            var name = Settings.Global.getString(
                context.contentResolver, Settings.Global.DEVICE_NAME
            )
            if (name == null) {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                name = adapter?.name
            }
            if (name == null) {
                name = Settings.Secure.getString(
                    context.contentResolver,
                    "bluetooth_name"
                )
            }
            if (name == null) {
                name = Build.MANUFACTURER + " " + Build.MODEL
            }
            return name
        }
    }
}
