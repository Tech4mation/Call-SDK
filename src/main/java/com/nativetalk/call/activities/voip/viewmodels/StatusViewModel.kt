package com.nativetalk.call.activities.voip.viewmodels

import androidx.lifecycle.MutableLiveData
import com.nativetalk.call.NativetalkCallSDK.coreContext
import com.nativetalk.call.R
import com.nativetalk.call.utils.Event
import org.linphone.core.Call
import org.linphone.core.CallStats
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.MediaEncryption
import com.nativetalk.call.activities.main.viewmodels.StatusViewModel


class StatusViewModel : StatusViewModel() {
    val callQualityIcon = MutableLiveData<Int>()
    val callQualityContentDescription = MutableLiveData<Int>()

    val encryptionIcon = MutableLiveData<Int>()
    val encryptionContentDescription = MutableLiveData<Int>()
    val encryptionIconVisible = MutableLiveData<Boolean>()

    val showCallStatsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val listener = object : CoreListenerStub() {
        override fun onCallStatsUpdated(core: Core, call: Call, stats: CallStats) {
            updateCallQualityIcon()
        }

        override fun onCallEncryptionChanged(
            core: Core,
            call: Call,
            on: Boolean,
            authenticationToken: String?
        ) {
            if (call.currentParams.mediaEncryption == MediaEncryption.ZRTP && !call.authenticationTokenVerified) {
//                showZrtpDialogEvent.value = Event(call)
            } else {
                updateEncryptionInfo(call)
            }
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            if (call == core.currentCall) {
                updateEncryptionInfo(call)
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        updateCallQualityIcon()

        val currentCall = coreContext.core.currentCall
        if (currentCall != null) {
            updateEncryptionInfo(currentCall)

            if (currentCall.currentParams.mediaEncryption == MediaEncryption.ZRTP && !currentCall.authenticationTokenVerified) {
//                showZrtpDialogEvent.value = Event(currentCall)
            }
        }
    }

    fun showCallStats() {
        showCallStatsEvent.value = Event(true)
    }

    fun updateEncryptionInfo(call: Call) {
        if (call.dir == Call.Dir.Incoming && call.state == Call.State.IncomingReceived && call.core.isMediaEncryptionMandatory) {
            // If the incoming call view is displayed while encryption is mandatory,
            // we can safely show the security_ok icon
            encryptionIcon.value = R.drawable.security_ok
            encryptionIconVisible.value = true
            encryptionContentDescription.value = R.string.content_description_call_secured
            return
        }

        when (call.currentParams.mediaEncryption ?: MediaEncryption.None) {
            MediaEncryption.SRTP, MediaEncryption.DTLS -> {
                encryptionIcon.value = R.drawable.security_ok
                encryptionIconVisible.value = true
                encryptionContentDescription.value = R.string.content_description_call_secured
            }
            MediaEncryption.ZRTP -> {
                encryptionIcon.value = when (call.authenticationTokenVerified) {
                    true -> R.drawable.security_ok
                    else -> R.drawable.security_pending
                }
                encryptionContentDescription.value = when (call.authenticationTokenVerified) {
                    true -> R.string.content_description_call_secured
                    else -> R.string.content_description_call_security_pending
                }
                encryptionIconVisible.value = true
            }
            MediaEncryption.None -> {
                encryptionIcon.value = R.drawable.security_ko
                // Do not show unsecure icon if user doesn't want to do call encryption
                encryptionIconVisible.value = call.core.mediaEncryption != MediaEncryption.None
                encryptionContentDescription.value = R.string.content_description_call_not_secured
            }
        }
    }

    override fun refreshRegister() {
        coreContext.core.refreshRegisters()
    }

    private fun updateCallQualityIcon() {
        val call = coreContext.core.currentCall ?: coreContext.core.calls.firstOrNull()
        val quality = call?.currentQuality ?: 0f
        callQualityIcon.value = when {
            quality >= 4 -> R.drawable.call_quality_indicator_4
            quality >= 3 -> R.drawable.call_quality_indicator_3
            quality >= 2 -> R.drawable.call_quality_indicator_2
            quality >= 1 -> R.drawable.call_quality_indicator_1
            else -> R.drawable.call_quality_indicator_0
        }
        callQualityContentDescription.value = when {
            quality >= 4 -> R.string.content_description_call_quality_4
            quality >= 3 -> R.string.content_description_call_quality_3
            quality >= 2 -> R.string.content_description_call_quality_2
            quality >= 1 -> R.string.content_description_call_quality_1
            else -> R.string.content_description_call_quality_0
        }
    }
}
