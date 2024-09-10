
package com.nativetalk.call

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.pm.ShortcutManager
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.fragment.app.Fragment
import com.nativetalk.call.utils.CallUtils
import org.linphone.core.ChatRoom
import org.linphone.core.tools.Log

@TargetApi(30)
class Api30Compatibility {
    companion object {
        fun hasReadPhoneNumbersPermission(context: Context): Boolean {
            val granted = Compatibility.hasPermission(context, Manifest.permission.READ_PHONE_NUMBERS)
            if (granted) {
                Log.d("[Permission Helper] Permission READ_PHONE_NUMBERS is granted")
            } else {
                Log.w("[Permission Helper] Permission READ_PHONE_NUMBERS is denied")
            }
            return granted
        }

        fun requestReadPhoneNumbersPermission(fragment: Fragment, code: Int) {
            fragment.requestPermissions(arrayOf(Manifest.permission.READ_PHONE_NUMBERS), code)
        }

        fun requestTelecomManagerPermission(activity: Activity, code: Int) {
            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.READ_PHONE_NUMBERS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.MANAGE_OWN_CALLS
                ),
                code
            )
        }

        fun hasTelecomManagerPermission(context: Context): Boolean {
            return Compatibility.hasPermission(context, Manifest.permission.READ_PHONE_NUMBERS) &&
                Compatibility.hasPermission(context, Manifest.permission.READ_PHONE_STATE) &&
                Compatibility.hasPermission(context, Manifest.permission.MANAGE_OWN_CALLS)
        }

        fun hasCallLogsPermission(context: Context): Boolean {
            return Compatibility.hasPermission(context, Manifest.permission.READ_CALL_LOG) &&
                Compatibility.hasPermission(context, Manifest.permission.WRITE_CALL_LOG)
        }

        fun removeChatRoomShortcut(context: Context, chatRoom: ChatRoom) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            val id = CallUtils.getChatRoomId(chatRoom.localAddress, chatRoom.peerAddress)
            val shortcutsToRemoveList = arrayListOf(id)
            shortcutManager.removeLongLivedShortcuts(shortcutsToRemoveList)
        }

        fun hideAndroidSystemUI(hide: Boolean, window: Window) {
            if (hide) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let {
                    it.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    it.hide(WindowInsets.Type.systemBars())
                }
            } else {
                window.setDecorFitsSystemWindows(true)
                window.insetsController?.show(WindowInsets.Type.systemBars())
            }
        }
    }
}
