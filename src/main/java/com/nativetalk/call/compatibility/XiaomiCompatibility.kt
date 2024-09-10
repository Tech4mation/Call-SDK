
package  com.nativetalk.call

import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nativetalk.call.NativetalkCallSDK.coreContext
import com.nativetalk.call.NativetalkCallSDK.corePreferences
import com.nativetalk.call.contact.getThumbnailUri
import com.nativetalk.call.notifications.Notifiable
import com.nativetalk.call.notifications.NotificationsManager
import com.nativetalk.call.utils.CallUtils
import com.nativetalk.call.utils.ImageUtils
import java.util.*
import org.linphone.core.Call
import org.linphone.core.Friend
import org.linphone.core.tools.Log

@TargetApi(26)
class XiaomiCompatibility {
    companion object {
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
            contact = coreContext.contactsManager.findContactByAddress(call.remoteAddress)
            roundPicture =
                ImageUtils.getRoundBitmapFromUri(context, contact?.getThumbnailUri())
            displayName = contact?.name ?: CallUtils.getDisplayName(call.remoteAddress)
            address = CallUtils.getDisplayableAddress(call.remoteAddress)
            info = context.getString(R.string.incoming_call_notification_title)

//            val declineActionLayout = RemoteViews(context.packageName, R.layout.custom_decline_action_layout)
//
//            val answerActionLayout = RemoteViews(context.packageName, R.layout.custom_answer_action_layout)

            val builder = NotificationCompat.Builder(
                context,
                context.getString(R.string.notification_channel_incoming_call_id)
            )
                .addPerson(notificationsManager.getPerson(contact, displayName, roundPicture))
                .setSmallIcon(R.drawable.ic_business_logo3)
                .setContentText(address)
                .setSubText(info)
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

//                .addAction(R.drawable.ic_custom_decline_icon, "Decline", pendingDeclineIntent)
//                .addAction(R.drawable.ic_custom_answer_icon, "Answer", pendingAnswerIntent)

            if (!corePreferences.preventInterfaceFromShowingUp) {
                builder.setContentIntent(pendingIntent)
            }

            return builder.build()
        }
    }
}