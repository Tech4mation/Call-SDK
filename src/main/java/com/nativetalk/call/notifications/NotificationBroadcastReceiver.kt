/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.nativetalk.call.notifications

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat.getSystemService
import com.nativetalk.call.NativetalkCallSDK.coreContext
import com.nativetalk.call.NativetalkCallSDK.ensureCoreExists
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.tools.Log

class NotificationBroadcastReceiver : BroadcastReceiver() {
    private var phoneNumber: String? = null
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("[Notification Broadcast Receiver] Ensuring Core exists")
        ensureCoreExists(context, false)

        val notificationId = intent.getIntExtra(NotificationsManager.INTENT_NOTIF_ID, 0)
        Log.i("[Notification Broadcast Receiver] Got notification broadcast for ID [$notificationId]")

        if (intent.action == NotificationsManager.INTENT_ANSWER_CALL_NOTIF_ACTION || intent.action == NotificationsManager.INTENT_HANGUP_CALL_NOTIF_ACTION) {
            handleCallIntent(intent)
        }

        Log.i("[Notification Broadcast Receiver] about to be notified")

        // Handle incoming call notified
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            Log.i("[Notification Broadcast Receiver] notified that call state changed")
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val phone = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            if (phone != null) phoneNumber = phone
            Log.i(phoneNumber)
            val sharedPreference =
                coreContext.context.getSharedPreferences(
                    "nativetalk-business.user_data",
                    Context.MODE_PRIVATE
                )

            Log.i("The phone number is ", phoneNumber)

            // Handle if call is outgoing
            if (state == TelephonyManager.EXTRA_STATE_OFFHOOK && phoneNumber != null) {
                // because offhook gets triggered
                if (sharedPreference.getBoolean("ongoingIncoming", false)) {
                    sharedPreference.getBoolean("ongoingIncoming", false)
                    Log.i("Incoming call picked")
                    return
                }

                val callType = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                if (callType == TelephonyManager.EXTRA_STATE_RINGING) {
                    // It was a missed call
                    Log.i("[Notification Broadcast Receiver] Call was missed")

                    // Handle the missed call here
                }
            } else if (state == TelephonyManager.EXTRA_STATE_IDLE) {
                with(sharedPreference.edit()) {
                    putBoolean("ongoingIncoming", false)

                    apply()
                }
            }
        }
    }

    private fun handleCallIntent(intent: Intent) {
        Log.i("[NotificationBroadcastReceiver] got to handleCallIntent")
        val remoteSipAddress = intent.getStringExtra(NotificationsManager.INTENT_REMOTE_ADDRESS)
        if (remoteSipAddress == null) {
            Log.e("[Notification Broadcast Receiver] Remote SIP address is null for notification")
            return
        }

        val core: Core = coreContext.core

        val remoteAddress = core.interpretUrl(remoteSipAddress, false)
        val call = if (remoteAddress != null) core.getCallByRemoteAddress2(remoteAddress) else null
        if (call == null) {
            Log.e("[Notification Broadcast Receiver] Couldn't find call from remote address $remoteSipAddress")
            return
        }
    }
}
