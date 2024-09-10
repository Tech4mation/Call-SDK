package com.nativetalk.call.activities.voip.data

import android.view.View
import androidx.lifecycle.MutableLiveData
import com.nativetalk.call.NativetalkCallSDK.coreContext
import com.nativetalk.call.activities.voip.data.GenericContactData
import com.nativetalk.call.utils.CallUtils
import kotlinx.coroutines.*
import org.linphone.core.*
import org.linphone.core.tools.Log
import java.util.Timer

open class CallData(val call: Call) : GenericContactData(call.remoteAddress) {
    interface CallContextMenuClickListener {
        fun onShowContextMenu(anchor: View, callData: CallData)
    }

    val displayableAddress = MutableLiveData<String>()
    val isPaused = MutableLiveData<Boolean>()
    val isRemotelyPaused = MutableLiveData<Boolean>()
    val canBePaused = MutableLiveData<Boolean>()
    val isRecording = MutableLiveData<Boolean>()
    val isOutgoing = MutableLiveData<Boolean>()
    val isIncoming = MutableLiveData<Boolean>()

    var contextMenuClickListener: CallContextMenuClickListener? = null
    private var timer: Timer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val listener = object : CallListenerStub() {
        override fun onStateChanged(call: Call, state: Call.State, message: String) {
            if (call != this@CallData.call) return
            Log.i("[Call] State changed: $state")
            update()
            if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error) {
                timer?.cancel()
            } else if (state == Call.State.StreamsRunning) {
                timer?.cancel()
            }
        }

        override fun onRemoteRecording(call: Call, recording: Boolean) {
            Log.i("[Call] Remote recording changed: $recording")
            isRecording.value = recording
        }
    }

    init {
        call.addListener(listener)
        isRemotelyPaused.value = call.remoteParams?.isRecording
        displayableAddress.value = CallUtils.getDisplayableAddress(call.remoteAddress)
        update()
    }

    override fun destroy() {
        call.removeListener(listener)
        timer?.cancel()
        scope.cancel()
        super.destroy()
    }

    fun togglePause() {
        if (isCallPaused()) {
            resume()
        } else {
            pause()
        }
    }

    fun pause() {
        call.pause()
    }

    fun resume() {
        call.resume()
    }

    fun accept() {
        call.accept()
    }

    fun terminate() {
        call.terminate()
    }

    fun toggleRecording() {
        if (call.isRecording) {
            call.stopRecording()
        } else {
            call.startRecording()
        }
        isRecording.value = call.isRecording
    }

    fun showContextMenu(anchor: View) {
        contextMenuClickListener?.onShowContextMenu(anchor, this)
    }

    private fun isCallPaused(): Boolean {
        return when (call.state) {
            Call.State.Paused, Call.State.Pausing -> true
            else -> false
        }
    }

    private fun isCallRemotelyPaused(): Boolean {
        return call.state == Call.State.PausedByRemote
    }

    private fun canCallBePaused(): Boolean {
        return !call.mediaInProgress() && call.state == Call.State.StreamsRunning
    }

    private fun update() {
        isPaused.value = isCallPaused()
        isRemotelyPaused.value = isCallRemotelyPaused()
        canBePaused.value = canCallBePaused()

        isOutgoing.value = call.state == Call.State.OutgoingInit ||
                call.state == Call.State.OutgoingEarlyMedia ||
                call.state == Call.State.OutgoingProgress ||
                call.state == Call.State.OutgoingRinging

        isIncoming.value = call.state == Call.State.IncomingReceived ||
                call.state == Call.State.IncomingEarlyMedia

        if (call.mediaInProgress()) {
            scope.launch {
                delay(1000)
                update()
            }
        }
    }
}
