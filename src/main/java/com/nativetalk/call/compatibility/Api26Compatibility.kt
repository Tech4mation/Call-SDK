
package com.nativetalk.call
import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.RemoteViews
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.nativetalk.call.NativetalkCallSDK.coreContext
import com.nativetalk.call.NativetalkCallSDK.corePreferences
import org.linphone.core.Call
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import com.nativetalk.call.notifications.Notifiable
import com.nativetalk.call.notifications.NotificationsManager
import org.linphone.telecom.NativeCallWrapper
import com.nativetalk.call.utils.ImageUtils
import com.nativetalk.call.utils.CallUtils

@TargetApi(26)
class Api26Compatibility {
    companion object {
        fun enterPipMode(activity: Activity, conference: Boolean) {
            val supportsPip = activity.packageManager
                .hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            Log.i("[Call] Is PiP supported: $supportsPip")
            if (supportsPip) {
                // Force portrait layout if in conference, otherwise for landscape
                // Our layouts behave better in these orientation
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Compatibility.getPipRatio(activity, conference, !conference))
                    .build()
                try {
                    if (!activity.enterPictureInPictureMode(params)) {
                        Log.e("[Call] Failed to enter PiP mode")
                    } else {
                        Log.i("[Call] Entering PiP mode with ${if (conference) "portrait" else "landscape"} aspect ratio")
                    }
                } catch (e: Exception) {
                    Log.e("[Call] Can't build PiP params: $e")
                }
            }
        }

        fun createServiceChannel(context: Context, notificationManager: NotificationManagerCompat) {
            // Create service notification channel
            val id = context.getString(R.string.notification_channel_service_id)
            val name = context.getString(R.string.notification_channel_service_name)
            val description = context.getString(R.string.notification_channel_service_name)
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW)
            channel.description = description
            channel.enableVibration(false)
            channel.enableLights(false)
            channel.setShowBadge(false)
            notificationManager.createNotificationChannel(channel)
        }

        fun createMissedCallChannel(
            context: Context,
            notificationManager: NotificationManagerCompat
        ) {
            val id = context.getString(R.string.notification_channel_missed_call_id)
            val name = context.getString(R.string.notification_channel_missed_call_name)
            val description = context.getString(R.string.notification_channel_missed_call_name)
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW)
            channel.description = description
            channel.lightColor = context.getColor(R.color.notification_led_color)
            channel.enableVibration(true)
            channel.enableLights(true)
            channel.setShowBadge(true)
            notificationManager.createNotificationChannel(channel)
        }

        fun createIncomingCallChannel(
            context: Context,
            notificationManager: NotificationManagerCompat
        ) {
            // Create incoming calls notification channel
            val id = context.getString(R.string.notification_channel_incoming_call_id)
            val name = context.getString(R.string.notification_channel_incoming_call_name)
            val description = context.getString(R.string.notification_channel_incoming_call_name)
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
            channel.description = description
            channel.lightColor = context.getColor(R.color.notification_led_color)
            channel.enableVibration(true)
            channel.enableLights(true)
            channel.setShowBadge(true)
            notificationManager.createNotificationChannel(channel)
        }

        fun getOverlayType(): Int {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }

        fun getChannelImportance(
            notificationManager: NotificationManagerCompat,
            channelId: String
        ): Int {
            val channel = notificationManager.getNotificationChannel(channelId)
            return channel?.importance ?: NotificationManagerCompat.IMPORTANCE_NONE
        }

        fun createIncomingCallNotification(
            context: Context,
            call: Call,
            notifiable: Notifiable,
            pendingIntent: PendingIntent,
            notificationsManager: NotificationsManager
        ): Notification {
            val contact: Friend?
            val roundPicture: Bitmap?
            val displayName: String
            val address: String
            val info: String

            val remoteContact = call.remoteContact
            val conferenceAddress = if (remoteContact != null) coreContext.core.interpretUrl(remoteContact, false) else null
            val conferenceInfo = if (conferenceAddress != null) coreContext.core.findConferenceInformationFromUri(conferenceAddress) else null

            Log.i("[Notifications Manager] No conference info found for remote contact address $remoteContact")

            displayName = CallUtils.getDisplayName(call.remoteAddress)
            address = CallUtils.getDisplayableAddress(call.remoteAddress)
            info = context.getString(R.string.incoming_call_notification_title)

            val notificationLayoutHeadsUp = RemoteViews(context.packageName, R.layout.call_incoming_notification_heads_up)
            notificationLayoutHeadsUp.setTextViewText(R.id.caller, displayName)
            notificationLayoutHeadsUp.setTextViewText(R.id.sip_uri, address)
            notificationLayoutHeadsUp.setTextViewText(R.id.incoming_call_info, info)

//            if (roundPicture != null) {
//                notificationLayoutHeadsUp.setImageViewBitmap(R.id.caller_picture, roundPicture)
//            }

            val builder = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_incoming_call_id))
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
//                .addPerson(notificationsManager.getPerson(contact, displayName, roundPicture))
                .setSmallIcon(R.drawable.ic_business_logo3)
                .setContentTitle(displayName)
                .setContentText(context.getString(R.string.incoming_call_notification_title))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .setShowWhen(true)
                .setOngoing(true)
                .setColor(ContextCompat.getColor(context, R.color.primary_color))
                .setFullScreenIntent(pendingIntent, true)
                .addAction(notificationsManager.getCallDeclineAction(notifiable))
                .addAction(notificationsManager.getCallAnswerAction(notifiable))

            if (!corePreferences.preventInterfaceFromShowingUp) {
                builder.setContentIntent(pendingIntent)
            }

            return builder.build()
        }

        fun createCallNotification(
            context: Context,
            call: Call,
            notifiable: Notifiable,
            pendingIntent: PendingIntent,
            channel: String,
            notificationsManager: NotificationsManager
        ): Notification {
            @StringRes val stringResourceId: Int
            val iconResourceId: Int
            val roundPicture: Bitmap?
            val title: String
            val person: Person

            val conferenceAddress = CallUtils.getConferenceAddress(call)
            val conferenceInfo = if (conferenceAddress != null) coreContext.core.findConferenceInformationFromUri(conferenceAddress) else null
            if (conferenceInfo != null) {
                Log.i("[Notifications Manager] Displaying group call notification with subject ${conferenceInfo.subject}")
            } else {
                Log.i("[Notifications Manager] No conference info found for remote contact address ${call.remoteAddress} (${call.remoteContact})")
            }

            val displayName = CallUtils.getDisplayName(call.remoteAddress)
            title = displayName
            roundPicture = ImageUtils.getRoundBitmapFromUri(context, null)

            person = Person.Builder()
                .setName(title)
                .setIcon(roundPicture?.let { IconCompat.createWithBitmap(it) })
                .build()


            when (call.state) {
                Call.State.Paused, Call.State.Pausing, Call.State.PausedByRemote -> {
                    stringResourceId = R.string.call_notification_paused
                    iconResourceId = R.drawable.ic_business_logo3
                }
                Call.State.OutgoingRinging, Call.State.OutgoingProgress, Call.State.OutgoingInit, Call.State.OutgoingEarlyMedia -> {
                    stringResourceId = R.string.call_notification_outgoing

                    iconResourceId = R.drawable.ic_business_logo3
                }
                else -> {
                    stringResourceId = R.string.call_notification_active

                    iconResourceId = R.drawable.ic_business_logo3
                }
            }

            val address = CallUtils.getDisplayableAddress(call.remoteAddress)


            val builder = NotificationCompat.Builder(
                context, channel
            )
                .setContentText(address)
                .setSmallIcon(iconResourceId)
                .setLargeIcon(roundPicture)
                .addPerson(person)
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setOngoing(true)
                .setColor(ContextCompat.getColor(context, R.color.notification_led_color))
                .addAction(notificationsManager.getCallDeclineAction(notifiable))

            if (!corePreferences.preventInterfaceFromShowingUp) {
                builder.setContentIntent(pendingIntent)
            }

            return builder.build()
        }

        @SuppressLint("MissingPermission")
        fun eventVibration(vibrator: Vibrator) {
            val effect = VibrationEffect.createWaveform(longArrayOf(0L, 100L, 100L), intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0), -1)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
            vibrator.vibrate(effect, audioAttrs)
        }

        fun changeAudioRouteForTelecomManager(connection: NativeCallWrapper, route: Int): Boolean {
            Log.i("[Telecom Helper] Changing audio route [$route] on connection ${connection.callId}")

            val audioState = connection.callAudioState
            if (audioState != null && audioState.route == route) {
                Log.w("[Telecom Helper] Connection is already using this route")
                return false
            } else if (audioState == null) {
                Log.w("[Telecom Helper] Failed to retrieve connection's call audio state!")
                return false
            }

            connection.setAudioRoute(route)
            return true
        }

        fun requestTelecomManagerPermission(activity: Activity, code: Int) {
            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.MANAGE_OWN_CALLS
                ),
                code
            )
        }

        fun requestCallLogsPermission(activity: Activity, code: Int) {
            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.WRITE_CALL_LOG
                ),
                code
            )
        }
        fun hasCallLogsPermission(context: Context): Boolean {
            return Compatibility.hasPermission(context, Manifest.permission.READ_CALL_LOG) &&
                Compatibility.hasPermission(context, Manifest.permission.WRITE_CALL_LOG)
        }

        fun getImeFlagsForSecureChatRoom(): Int {
            return EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }

        fun startForegroundService(context: Context, intent: Intent) {
            context.startForegroundService(intent)
        }

    }
}
