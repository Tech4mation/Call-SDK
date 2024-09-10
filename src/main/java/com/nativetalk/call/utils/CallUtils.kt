package com.nativetalk.call.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.telephony.TelephonyManager.*
import com.nativetalk.call.NativetalkCallSDK.coreContext
import com.nativetalk.call.NativetalkCallSDK.corePreferences
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import org.linphone.core.*
import org.linphone.core.tools.Log

/**
 * Various utility methods for Linphone SDK
 */
class CallUtils {
    companion object {
        private const val RECORDING_DATE_PATTERN = "dd-MM-yyyy-HH-mm-ss"

        fun getDisplayName(address: Address?): String {
            if (address == null) return "[null]"
            if (address.displayName == null) {
                val account = coreContext.core.accountList.find { account ->
                    account.params.identityAddress?.asStringUriOnly() == address.asStringUriOnly()
                }
                val localDisplayName = account?.params?.identityAddress?.displayName
                // Do not return an empty local display name
                if (localDisplayName != null && localDisplayName.isNotEmpty()) {
                    return localDisplayName
                }
            }
            // Do not return an empty display name
            return address.displayName ?: address.username ?: address.asString()
        }

        fun getDisplayableAddress(address: Address?): String {
            if (address == null) return "[null]"
            return address.username ?: address.asStringUriOnly()
        }

        fun getCleanedAddress(address: Address): Address {
            // To remove the GRUU if any
            val cleanAddress = address.clone()
            cleanAddress.clean()
            return cleanAddress
        }

        fun getConferenceAddress(call: Call): Address? {
            val remoteContact = call.remoteContact
            val conferenceAddress = if (call.dir == Call.Dir.Incoming) {
                if (remoteContact != null)
                    coreContext.core.interpretUrl(remoteContact, false)
                else
                    null
            } else {
                call.remoteAddress
            }
            return conferenceAddress
        }

        fun isEndToEndEncryptedChatAvailable(): Boolean {
            val core = coreContext.core
            return core.isLimeX3DhEnabled &&
                (core.limeX3DhServerUrl != null || core.defaultAccount?.params?.limeServerUrl != null) &&
                core.defaultAccount?.params?.conferenceFactoryUri != null
        }
        @SuppressLint("MissingPermission")
        fun checkIfNetworkHasLowBandwidth(context: Context): Boolean {
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo: NetworkInfo? = connMgr.activeNetworkInfo
            if (networkInfo != null && networkInfo.isConnected) {
                if (networkInfo.type == ConnectivityManager.TYPE_MOBILE) {
                    return when (networkInfo.subtype) {
                        NETWORK_TYPE_EDGE, NETWORK_TYPE_GPRS, NETWORK_TYPE_IDEN -> true
                        else -> false
                    }
                }
            }
            // In doubt return false
            return false
        }

        fun isCallLogMissed(callLog: CallLog): Boolean {
            return (
                callLog.dir == Call.Dir.Incoming &&
                    (
                        callLog.status == Call.Status.Missed ||
                            callLog.status == Call.Status.Aborted ||
                            callLog.status == Call.Status.EarlyAborted
                        )
                )
        }

        fun areChatRoomsTheSame(chatRoom1: ChatRoom, chatRoom2: ChatRoom): Boolean {
            return chatRoom1.localAddress.weakEqual(chatRoom2.localAddress) &&
                chatRoom1.peerAddress.weakEqual(chatRoom2.peerAddress)
        }

        fun getChatRoomId(localAddress: Address, remoteAddress: Address): String {
            val localSipUri = localAddress.clone()
            localSipUri.clean()
            val remoteSipUri = remoteAddress.clone()
            remoteSipUri.clean()
            return "${localSipUri.asStringUriOnly()}~${remoteSipUri.asStringUriOnly()}"
        }

        fun getAccountsNotHidden(): List<Account> {
            val list = arrayListOf<Account>()

            for (account in coreContext.core.accountList) {
                if (account.getCustomParam("hidden") != "1") {
                    list.add(account)
                }
            }

            return list
        }

        fun applyInternationalPrefix(): Boolean {
            val account = coreContext.core.defaultAccount
            if (account != null) {
                val params = account.params
                return params.useInternationalPrefixForCallsAndChats
            }

            return true // Legacy behavior
        }
    }
}
