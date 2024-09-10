
package com.nativetalk.call

import android.annotation.TargetApi
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import java.util.concurrent.Executor
import org.linphone.core.tools.Log

@TargetApi(31)
class TelephonyListener(private val telephonyManager: TelephonyManager) : PhoneStateInterface {
    private var gsmCallActive = false

    private fun runOnUiThreadExecutor(): Executor {
        val handler = Handler(Looper.getMainLooper())
        return Executor {
            handler.post(it)
        }
    }

    inner class TelephonyListener : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            gsmCallActive = when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    Log.i("[Context] Phone state is off hook")
                    true
                }
                TelephonyManager.CALL_STATE_RINGING -> {
                    Log.i("[Context] Phone state is ringing")
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
    private val telephonyListener = TelephonyListener()

    init {
        Log.i("[Telephony Listener] Registering telephony callback")
        telephonyManager.registerTelephonyCallback(runOnUiThreadExecutor(), telephonyListener)
    }

    override fun destroy() {
        Log.i("[Telephony Listener] Unregistering telephony callback")
        telephonyManager.unregisterTelephonyCallback(telephonyListener)
    }

    override fun isInCall(): Boolean {
        return gsmCallActive
    }
}
