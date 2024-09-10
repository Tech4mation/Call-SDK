
package com.nativetalk.call

import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import org.linphone.core.tools.Log

class PhoneStateListener(private val telephonyManager: TelephonyManager) : PhoneStateInterface {
    private var gsmCallActive = false
    private val phoneStateListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            gsmCallActive = when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    android.util.Log.i("CALLDEBUG", "[Context] Phone state is off hook")
                    true
                }
                TelephonyManager.CALL_STATE_RINGING -> {
                    android.util.Log.i("CALLDEBUG", "[Context] Phone state is ringing")
                    true
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    Log.i("[Context] Phone state is idle")
                    false
                }
                else -> {
                    Log.w("[Context] Phone state is unexpected: $state")
                    false
                }
            }
        }
    }

    init {
        Log.i("[Phone State Listener] Registering phone state listener")
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun destroy() {
        Log.i("[Phone State Listener] Unregistering phone state listener")
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
    }

    override fun isInCall(): Boolean {
        return gsmCallActive
    }
}
