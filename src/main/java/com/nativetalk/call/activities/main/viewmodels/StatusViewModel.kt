package com.nativetalk.call.activities.main.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nativetalk.call.NativetalkCallSDK.coreContext
import com.nativetalk.call.R
import com.nativetalk.call.utils.Event
import org.linphone.core.Account
import org.linphone.core.Content
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.RegistrationState
import java.util.*
import org.linphone.core.tools.Log

open class StatusViewModel : ViewModel() {
    val registrationStatusText = MutableLiveData<Int>()
    val registrationStatusDrawable = MutableLiveData<Int>()
    val title = MutableLiveData("Welcome \uD83D\uDC4B")

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState,
            message: String
        ) {
            if (account == core.defaultAccount) {
                updateDefaultAccountRegistrationStatus(state)
            } else if (core.accountList.isEmpty()) {
                // Update registration status when default account is removed
                registrationStatusText.value = getStatusIconText(state)
                registrationStatusDrawable.value = getStatusIconResource(state)
            }
        }

//        override fun onNotifyReceived(
//            core: Core,
//            event: Event,
//            notifiedEvent: String,
//            body: Content
//        ) {
//            if (body.type == "application" && body.subtype == "simple-message-summary" && body.size > 0) {
//                val data = body.utf8Text?.lowercase(Locale.getDefault())
//                val voiceMail = data?.split("voice-message: ")
//                if ((voiceMail?.size ?: 0) >= 2) {
//                    val toParse = voiceMail!![1].split("/", limit = 0)
//                    try {
//                        val unreadCount: Int = toParse[0].toInt()
//                        voiceMailCount.value = unreadCount
//                    } catch (nfe: NumberFormatException) {
//                        Log.e("[Status Fragment] $nfe")
//                    }
//                }
//            }
//        }
    }

    init {
        val core = coreContext.core
        core.addListener(listener)

        var state: RegistrationState = RegistrationState.None
        val defaultAccount = core.defaultAccount
        if (defaultAccount != null) {
            state = defaultAccount.state
        }
        updateDefaultAccountRegistrationStatus(state)
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        super.onCleared()
    }

    open fun refreshRegister() {
        coreContext.core.refreshRegisters()
    }

    fun updateDefaultAccountRegistrationStatus(state: RegistrationState) {
        registrationStatusText.value = getStatusIconText(state)
        registrationStatusDrawable.value = getStatusIconResource(state)
    }

    private fun getStatusIconText(state: RegistrationState): Int {
        return when (state) {
            RegistrationState.Ok -> R.string.status_connected
            RegistrationState.Progress -> R.string.status_in_progress
            RegistrationState.Failed -> R.string.status_error
            else -> R.string.status_not_connected
        }
    }

    private fun getStatusIconResource(state: RegistrationState): Int {
        return when (state) {
            RegistrationState.Ok -> R.drawable.led_registered
            RegistrationState.Progress -> R.drawable.led_registration_in_progress
            RegistrationState.Failed -> R.drawable.led_error
            else -> R.drawable.led_not_registered
        }
    }
}
