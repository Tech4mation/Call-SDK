package com.nativetalk.call.activities.voip.viewmodels

import android.Manifest
import android.content.Context
import android.os.FileUtils
import androidx.lifecycle.*
import com.nativetalk.call.NativetalkCallSDK.coreContext
import com.nativetalk.call.R
import com.nativetalk.call.activities.voip.data.CallData
import com.nativetalk.call.utils.AppUtils
import com.nativetalk.call.utils.Event
import com.nativetalk.call.utils.PermissionHelper
import java.util.*
import kotlinx.coroutines.launch
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log

class CallsViewModel() : ViewModel() {
    val currentCallData = MutableLiveData<CallData>()

    val callsData = MutableLiveData<List<CallData>>()

    val inactiveCallsCount = MutableLiveData<Int>()

    val currentCallUnreadChatMessageCount = MutableLiveData<Int>()

    val chatAndCallsCount = MediatorLiveData<Int>()

    val isMicrophoneMuted = MutableLiveData<Boolean>()

    val isMuteMicrophoneEnabled = MutableLiveData<Boolean>()

    val askWriteExternalStoragePermissionEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val callConnectedEvent: MutableLiveData<Event<Call>> by lazy {
        MutableLiveData<Event<Call>>()
    }

    val callEndedEvent: MutableLiveData<Event<Call>> by lazy {
        MutableLiveData<Event<Call>>()
    }

    val callUpdateEvent: MutableLiveData<Event<Call>> by lazy {
        MutableLiveData<Event<Call>>()
    }

    val noMoreCallEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val askPermissionEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    // CA
    val comment: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    val customerName: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }
    val customerInitials: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    val newCallStateToTrack: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }
    val callPauseState: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }
    val callMicrophoneState: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }
    val title: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }


    private val listener = object : CoreListenerStub() {
        override fun onLastCallEnded(core: Core) {
            Log.i("[Calls] Last call ended")

            currentCallData.value?.destroy()
            noMoreCallEvent.value = Event(true)
        }

        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            Log.i("[Calls] Call with ID [${call.callLog.callId}] state changed: $state")

            if (state == Call.State.IncomingEarlyMedia || state == Call.State.IncomingReceived || state == Call.State.OutgoingInit) {
                if (!callDataAlreadyExists(call)) {
                    addCallToList(call)
                }
            }

            val currentCall = core.currentCall
            if (currentCall != null && currentCallData.value?.call != currentCall) {
                updateCurrentCallData(currentCall)
            } else if (currentCall == null && core.callsNb > 0) {
                updateCurrentCallData(currentCall)
            }

            if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error) {
                removeCallFromList(call)
                if (core.callsNb > 0) {
                    callEndedEvent.value = Event(call)
                }
            } else if (call.state == Call.State.UpdatedByRemote) {
                // If the correspondent asks to turn on video while audio call,
                // defer update until user has chosen whether to accept it or not
                val remoteVideo = call.remoteParams?.isVideoEnabled ?: false
                val localVideo = call.currentParams.isVideoEnabled
                val autoAccept = call.core.videoActivationPolicy.automaticallyAccept
                if (remoteVideo && !localVideo && !autoAccept) {
                    if (coreContext.core.isVideoCaptureEnabled || coreContext.core.isVideoDisplayEnabled) {
                        call.deferUpdate()
                        callUpdateEvent.value = Event(call)
                    }
                }
            } else if (state == Call.State.Connected) {
                callConnectedEvent.value = Event(call)
            } else if (state == Call.State.StreamsRunning) {
                callUpdateEvent.value = Event(call)
            }

            // CA
            callPauseState.value = state == Call.State.Pausing || state == Call.State.PausedByRemote || state == Call.State.Paused

//            callEndState.value = state == Call.State.End || state == Call.State.Released
            // End

            updateInactiveCallsCount()
        }
    }

    init {
        coreContext.core.addListener(listener)
        title.value = coreContext.displayName

        val currentCall = coreContext.core.currentCall
        coreContext.context.getSharedPreferences("nativetalk-business.user_data", Context.MODE_PRIVATE)

        if (currentCall != null) {
            currentCallData.value?.destroy()

            // Get the customer's name
            val viewModel = CallData(currentCall)
            currentCallData.value = viewModel
        }

        chatAndCallsCount.value = 0
        chatAndCallsCount.addSource(inactiveCallsCount) {
            chatAndCallsCount.value = updateCallsAndChatCount()
        }
        chatAndCallsCount.addSource(currentCallUnreadChatMessageCount) {
            chatAndCallsCount.value = updateCallsAndChatCount()
        }

        initCallList()
        updateInactiveCallsCount()
        updateUnreadChatCount()
        updateMicState()
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)

        currentCallData.value?.destroy()
        callsData.value.orEmpty().forEach(CallData::destroy)

        super.onCleared()
    }

    fun toggleMuteMicrophone() {
        if (!PermissionHelper.get().hasRecordAudioPermission()) {
            askPermissionEvent.value = Event(Manifest.permission.RECORD_AUDIO)
            return
        }

        val micMuted = currentCallData.value?.call?.microphoneMuted ?: false
        currentCallData.value?.call?.microphoneMuted = !micMuted

        // CA
        callMicrophoneState.value = !currentCallData.value?.call?.microphoneMuted!!
        // END

        updateMicState()
    }

    private fun initCallList() {
        val calls = arrayListOf<CallData>()

        for (call in coreContext.core.calls) {
            val data: CallData = if (currentCallData.value?.call == call) {
                currentCallData.value!!
            } else {
                CallData(call)
            }
            Log.i("[Calls] Adding call with ID ${call.callLog.callId} to calls list")
            calls.add(data)
        }

        callsData.value = calls
    }

    private fun addCallToList(call: Call) {
        Log.i("[Calls] Adding call with ID ${call.callLog.callId} to calls list")

        val calls = arrayListOf<CallData>()
        calls.addAll(callsData.value.orEmpty())

        val data = CallData(call)
        calls.add(data)

        callsData.value = calls
    }

    private fun removeCallFromList(call: Call) {
        Log.i("[Calls] Removing call with ID ${call.callLog.callId} from calls list")

        val calls = arrayListOf<CallData>()
        calls.addAll(callsData.value.orEmpty())

        val data = calls.find { it.call == call }
        if (data == null) {
            Log.w("[Calls] Data for call to remove wasn't found")
        } else {
            data.destroy()
            calls.remove(data)
        }

        callsData.value = calls
    }

    private fun updateCurrentCallData(currentCall: Call?) {
        var callToUse = currentCall

        if (currentCall == null) {
            if (coreContext.core.callsNb == 1) return // There is only one call, most likely it is paused

            Log.w("[Calls] Current call is now null")
            val firstCall = coreContext.core.calls.find { call ->
                call.state != Call.State.Error && call.state != Call.State.End && call.state != Call.State.Released
            }
            if (firstCall != null && currentCallData.value?.call != firstCall) {
                Log.i("[Calls] Using [${firstCall.remoteAddress.asStringUriOnly()}] call as \"current\" call")
                callToUse = firstCall
            }
        }

        if (callToUse == null) {
            Log.w("[Calls] No call found to be used as \"current\"")
            return
        }

        var found = false
        for (callData in callsData.value.orEmpty()) {
            if (callData.call == callToUse) {
                Log.i("[Calls] Updating current call to: ${callData.call.remoteAddress.asStringUriOnly()}")
                currentCallData.value = callData
                found = true
                break
            }
        }
        if (!found) {
            Log.w("[Calls] Call with ID [${callToUse.callLog.callId}] not found in calls data list, shouldn't happen!")
            val viewModel = CallData(callToUse)
            currentCallData.value = viewModel
        }

        updateMicState()
        // updateUnreadChatCount()
    }

    private fun callDataAlreadyExists(call: Call): Boolean {
        for (callData in callsData.value.orEmpty()) {
            if (callData.call == call) {
                return true
            }
        }
        return false
    }

    fun updateMicState() {
        isMicrophoneMuted.value = !PermissionHelper.get().hasRecordAudioPermission() || currentCallData.value?.call?.microphoneMuted == true
        isMuteMicrophoneEnabled.value = currentCallData.value?.call != null
    }

    private fun updateCallsAndChatCount(): Int {
        return (inactiveCallsCount.value ?: 0) + (currentCallUnreadChatMessageCount.value ?: 0)
    }

    private fun updateUnreadChatCount() {
        // For now we don't display in-call chat, so use global unread chat messages count
        currentCallUnreadChatMessageCount.value = coreContext.core.unreadChatMessageCountFromActiveLocals
    }

    private fun updateInactiveCallsCount() {
        val callsNb = coreContext.core.callsNb
        inactiveCallsCount.value = if (callsNb > 0) callsNb - 1 else 0
    }
}