package com.nativetalk.call

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.nativetalk.call.NativetalkCallSDK.corePreferences
import com.nativetalk.call.activities.voip.CallActivity
import com.nativetalk.call.contact.ContactsManager
import com.nativetalk.call.notifications.NotificationsManager
import com.nativetalk.call.utils.AppUtils
import com.nativetalk.call.utils.AudioRouteUtils
import com.nativetalk.call.utils.CallUtils
import com.nativetalk.call.utils.Event
import com.nativetalk.call.utils.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.linphone.core.Account
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.core.CallParams
import org.linphone.core.Config
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.MediaDirection
import org.linphone.core.MediaEncryption
import org.linphone.core.Reason
import org.linphone.core.RegistrationState
import org.linphone.core.tools.service.CoreService
import org.linphone.mediastream.Version
import org.linphone.telecom.TelecomHelper
import kotlin.math.abs


class CoreContext(
    val context: Context,
    coreConfig: Config,
    service: CoreService? = null,
    useAutoStartDescription: Boolean = false,
    ) :
    LifecycleOwner, ViewModelStoreOwner {
    private val _lifecycleRegistry = LifecycleRegistry(this)
    private val _viewModelStore = ViewModelStore()

    private var callOverlay: View? = null

    override val lifecycle: Lifecycle
        get() {
            return _lifecycleRegistry
        }
    override val viewModelStore: ViewModelStore
        get() {
            return _viewModelStore
        }

    val core: Core
    private lateinit var phoneStateListener: PhoneStateInterface
    val handler: Handler = Handler(Looper.getMainLooper())
    private var previousCallState = Call.State.Idle
    val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    var stopped = false
    var displayName: String? = null
        set(value) {
            field = value
        }

    var screenWidth: Float = 0f
    var screenHeight: Float = 0f

    private var overlayX = 0f
    private var overlayY = 0f


    init {
        _lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
        core = Factory.instance().createCoreWithConfig(coreConfig, context)
        _lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    val notificationsManager: NotificationsManager by lazy {
        NotificationsManager(context)
    }

    val callErrorMessageResourceId: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val contactsManager: ContactsManager by lazy {
        ContactsManager(context)
    }

    fun createCallOverlay() {
        if (!corePreferences.showCallOverlay || !corePreferences.systemWideCallOverlay || callOverlay != null) {
            return
        }

        if (overlayY == 0f) overlayY = AppUtils.pixelsToDp(40f)
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // WRAP_CONTENT doesn't work well on some launchers...
        val params: WindowManager.LayoutParams = WindowManager.LayoutParams(
            AppUtils.getDimension(R.dimen.call_overlay_size).toInt(),
            AppUtils.getDimension(R.dimen.call_overlay_size).toInt(),
            Compatibility.getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.x = overlayX.toInt()
        params.y = overlayY.toInt()
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        val overlay = LayoutInflater.from(context).inflate(R.layout.call_overlay, null)

        var initX = overlayX
        var initY = overlayY
        overlay.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x - event.rawX
                    initY = params.y - event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val x = (event.rawX + initX).toInt()
                    val y = (event.rawY + initY).toInt()

                    params.x = x
                    params.y = y
                    windowManager.updateViewLayout(overlay, params)
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(overlayX - params.x) < CorePreferences.OVERLAY_CLICK_SENSITIVITY &&
                        abs(overlayY - params.y) < CorePreferences.OVERLAY_CLICK_SENSITIVITY
                    ) {
                        view.performClick()
                    }
                    overlayX = params.x.toFloat()
                    overlayY = params.y.toFloat()
                }
                else -> return@setOnTouchListener false
            }
            true
        }
        overlay.setOnClickListener {
            onCallOverlayClick()
        }

        try {
            windowManager.addView(overlay, params)
            callOverlay = overlay
        } catch (e: Exception) {
            Log.e("[CoreContext]", "[Context] Failed to add overlay in windowManager: $e")
        }
    }

    fun onCallOverlayClick() {
        val call = core.currentCall ?: core.calls.firstOrNull()
        if (call != null) {
            Log.i("[CoreContext]", "[Context] Overlay clicked, go back to call view")
            when (call.state) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> onIncomingReceived()
                Call.State.OutgoingInit, Call.State.OutgoingProgress, Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> onOutgoingStarted()
                else -> onCallStarted()
            }
        } else {
            Log.e("[CoreContext]", "[Context] Couldn't find call, why is the overlay clicked?!")
        }
    }

    fun removeCallOverlay() {
        if (callOverlay != null) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(callOverlay)
            callOverlay = null
        }
    }


    fun declineCallDueToGsmActiveCall(): Boolean {
        if (!corePreferences.useTelecomManager) { // Can't use the following call with Telecom Manager API as it will "fake" GSM calls
            var gsmCallActive = false
            if (::phoneStateListener.isInitialized) {
                gsmCallActive = phoneStateListener.isInCall()
            }

            if (gsmCallActive) {
                Log.d("[CoreContext]", "[Context] Refusing the call with reason busy because a GSM call is active")
                return true
            }
        } else {
            Log.e("[CoreContext]", "[Context] Telecom Manager singleton wasn't created!")
        }
        return false
    }

    private fun onIncomingReceived() {
//        if (corePreferences.preventInterfaceFromShowingUp) {
//            Log.w("[CoreContext]", "[Context] We were asked to not show the incoming call screen")
//            return
//        }

        Log.i("[CoreContext]", "[Context] Starting Incoming call received")
        val intent = Intent(context, CallActivity::class.java)
        // This flag is required to start an Activity from a Service context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        context.startActivity(intent)
    }

    fun answerCall(call: Call) {
        Log.i("[CoreContext]", "[Context] Answering call $call")
        val params = core.createCallParams(call)
        if (params == null) {
            Log.w("[CoreContext]", "[Context] Answering call without params!")
            call.accept()
            return
        }

        if (call.callLog.wasConference()) {
            // Prevent incoming group call to start in audio only layout
            // Do the same as the conference waiting room
            params.isVideoEnabled = true
            params.videoDirection = if (core.videoActivationPolicy.automaticallyInitiate) MediaDirection.SendRecv else MediaDirection.RecvOnly
            Log.i("[CoreContext]", "[Context] Enabling video on call params to prevent audio-only layout when answering")
        }

        call.acceptWithParams(params)
    }

    private fun onOutgoingStarted() {
        Log.i("[CoreContext]", "[Context] Starting OutgoingCallActivity")
        val intent = Intent(context, CallActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun onCallStarted() {
        if (corePreferences.preventInterfaceFromShowingUp) {
            Log.w("[CoreContext]", "[Context] We were asked to not show the call screen")
            return
        }

        Log.i("[CoreContext]", "[Context] Starting CallActivity")
//        val intent = Intent(context, org.linphone.activities.voip.CallActivity::class.java)
//        // This flag is required to start an Activity from a Service context
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
//        context.startActivity(intent)
    }

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            Log.i("[CoreContext]", "[Context] Account [${account.params.identityAddress?.asStringUriOnly()}] registration state changed [$state]")
            if (state == RegistrationState.Ok && account == core.defaultAccount) {
                notificationsManager.stopForegroundNotificationIfPossible()
            }
        }

        override fun onPushNotificationReceived(core: Core, payload: String?) {
            Log.i("[CoreContext]", "[Context] Push notification received: $payload")
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            Log.i("[CoreContext]", "[Context] Call state changed [$state]")
            if (state == Call.State.IncomingReceived || state == Call.State.IncomingEarlyMedia) {
                if (declineCallDueToGsmActiveCall()) {
                    call.decline(Reason.Busy)
                    return
                }

                // Starting SDK 24 (Android 7.0) we rely on the fullscreen intent of the call incoming notification
                onIncomingReceived()

//                if (Version.sdkStrictlyBelow(Version.API24_NOUGAT_70)) {
//                    Log.i("[NotificationsManager]", "Incoming call received, opening activity")
//                } else {
//                    Log.i("[NotificationsManager]", "Version is ${Version.sdkStrictlyBelow(Version.API24_NOUGAT_70)}")
//                }

                if (corePreferences.autoAnswerEnabled) {
                    val autoAnswerDelay = corePreferences.autoAnswerDelay
                    if (autoAnswerDelay == 0) {
                        Log.w("[CoreContext]", "[Context] Auto answering call immediately")
                        answerCall(call)
                    } else {
                        Log.i("[CoreContext]", "[Context] Scheduling auto answering in $autoAnswerDelay milliseconds")
                        handler.postDelayed(
                            {
                                Log.w("[CoreContext]", "[Context] Auto answering call")
                                answerCall(call)
                            },
                            autoAnswerDelay.toLong()
                        )
                    }
                }
            } else if (state == Call.State.OutgoingProgress) {
                val conferenceInfo = core.findConferenceInformationFromUri(call.remoteAddress)
                // Do not show outgoing call view for conference calls, wait for connected state
                if (conferenceInfo == null) {
                    onOutgoingStarted()
                }

                if (core.callsNb == 1 && corePreferences.routeAudioToBluetoothIfAvailable) {
                    AudioRouteUtils.routeAudioToBluetooth(call)
                }
            } else if (state == Call.State.Connected) {
                if (corePreferences.automaticallyStartCallRecording) {
                    Log.i("[CoreContext]", "[Context] We were asked to start the call recording automatically")
                    call.startRecording()
                }
                onCallStarted()
            } else if (state == Call.State.StreamsRunning) {
                // Do not automatically route audio to bluetooth after first call
                if (core.callsNb == 1) {
                    // Only try to route bluetooth / headphone / headset when the call is in StreamsRunning for the first time
                    if (previousCallState == Call.State.Connected) {
                        Log.i("[CoreContext]", "[Context] First call going into StreamsRunning state for the first time, trying to route audio to headset or bluetooth if available")
                        if (AudioRouteUtils.isHeadsetAudioRouteAvailable()) {
                            AudioRouteUtils.routeAudioToHeadset(call)
                        } else if (corePreferences.routeAudioToBluetoothIfAvailable && AudioRouteUtils.isBluetoothAudioRouteAvailable()) {
                            AudioRouteUtils.routeAudioToBluetooth(call)
                        }
                    }
                }
            } else if (state == Call.State.End || state == Call.State.Error || state == Call.State.Released) {
                if (state == Call.State.End && CallUtils.isCallLogMissed(call.callLog)) {
                    coroutineScope.launch {
                        val sharedPreference = context.getSharedPreferences(
                            "nativetalk-business.user_data",
                            Context.MODE_PRIVATE
                        )
                    }
                } else if (state == Call.State.End) {
                    Log.w("[CoreContext]", "Call Ended")
                }

                if (state == Call.State.Error) {
                    val sharedPreferences = context
                        .getSharedPreferences("nativetalk.user_data", Context.MODE_PRIVATE)
                    val domain = sharedPreferences.getString("domain", "")
                    val ioErrorMessage = if (!domain.isNullOrEmpty()) {
                        context.getString(R.string.call_error_io_error) // Default string
                    } else {
                        context.getString(R.string.call_error_account_not_setup) // String to show if the condition is met
                    }

                    Log.w("[CoreContext]", "[Context] Call error reason is ${call.errorInfo.protocolCode} / ${call.errorInfo.reason} / ${call.errorInfo.phrase}")
                    val toastMessage = when (call.errorInfo.reason) {
                        Reason.Busy -> context.getString(R.string.call_error_user_busy)
                        Reason.IOError -> ioErrorMessage
                        Reason.NotAcceptable -> context.getString(R.string.call_error_incompatible_media_params)
                        Reason.NotFound -> context.getString(R.string.call_error_user_not_found)
                        Reason.ServerTimeout -> context.getString(R.string.call_error_server_timeout)
                        Reason.TemporarilyUnavailable -> context.getString(R.string.call_error_temporarily_unavailable)
                        else -> context.getString(R.string.call_error_generic).format("${call.errorInfo.protocolCode} / ${call.errorInfo.phrase}")
                    }
                    callErrorMessageResourceId.value = Event(toastMessage)
                } else if (state == Call.State.End &&
                    call.dir == Call.Dir.Outgoing &&
                    call.errorInfo.reason == Reason.Declined &&
                    core.callsNb == 0
                ) {
                    Log.i("[CoreContext]", "[Context] Call has been declined")
                    val toastMessage = context.getString(R.string.call_error_declined)
                    callErrorMessageResourceId.value = Event(toastMessage)
                }
            }

            previousCallState = state
        }

        override fun onLastCallEnded(core: Core) {
            Log.i("[CoreContext]", "[Context] Last call has ended")
            if (!core.isMicEnabled) {
                Log.w("[CoreContext]", "[Context] Mic was muted in Core, enabling it back for next call")
                core.isMicEnabled = true
            }
        }
    }

    private fun computeUserAgent() {
        val deviceName: String = corePreferences.deviceName
        val appName: String = context.resources.getString(R.string.user_agent_app_name)
        val androidVersion = "1.0"
        val userAgent = "$appName/$androidVersion ($deviceName) NativetalkCallSDK"
        val sdkVersion = context.getString(R.string.linphone_sdk_version)
        val sdkBranch = context.getString(R.string.linphone_sdk_branch)
        val sdkUserAgent = "$sdkVersion ($sdkBranch)"
        core.setUserAgent(userAgent, sdkUserAgent)
    }

    private fun configureCore() {
        Log.i("[CoreContext]", "[Context] Configuring Core")

        core.staticPicture = corePreferences.staticPicturePath

        // Migration code
        if (core.config.getBool("app", "incoming_call_vibration", true)) {
            core.isVibrationOnIncomingCallEnabled = true
            core.config.setBool("app", "incoming_call_vibration", false)
        }

        if (core.config.getInt("misc", "conference_layout", 1) > 1) {
            core.config.setInt("misc", "conference_layout", 1)
        }

        // Now LIME server URL is set on accounts
        val limeServerUrl = core.limeX3DhServerUrl.orEmpty()
        if (limeServerUrl.isNotEmpty()) {
            Log.w("[CoreContext]", "[Context] Removing LIME X3DH server URL from Core config")
            core.limeX3DhServerUrl = null
        }

        // Disable Telecom Manager on Android < 10 to prevent crash due to OS bug in Android 9
        if (Version.sdkStrictlyBelow(Version.API29_ANDROID_10)) {
            if (corePreferences.useTelecomManager) {
                Log.w("[CoreContext]", "[Context] Android < 10 detected, disabling telecom manager to prevent crash due to OS bug")
            }
            corePreferences.useTelecomManager = false
            corePreferences.manuallyDisabledTelecomManager = true
        }

        computeUserAgent()

        for (account in core.accountList) {
            if (account.params.identityAddress?.domain == corePreferences.defaultDomain) {
                var paramsChanged = false
                val params = account.params.clone()

                // Enable Bundle mode by default
                if (!account.params.isRtpBundleEnabled) {
                    Log.i("[CoreContext]", "[Context] Enabling RTP bundle mode on proxy config ${params.identityAddress?.asString()}")
                    params.isRtpBundleEnabled = true
                    paramsChanged = true
                }

                if (account.params.limeServerUrl == null && limeServerUrl.isNotEmpty()) {
                    params.limeServerUrl = limeServerUrl
                    paramsChanged = true
                    Log.i("[CoreContext]", "[Context] Moving Core's LIME X3DH server URL [$limeServerUrl] on account ${params.identityAddress?.asString()}")
                }

                if (paramsChanged) {
                    Log.i("[CoreContext]", "[Context] Account params have been updated, apply changes")
                    account.params = params
                }
            }
        }

        Log.i("[CoreContext]", "[Context] Core configured")
    }

    fun initPhoneStateListener() {
        if (PermissionHelper.required(context).hasReadPhoneStatePermission()) {
            try {
                phoneStateListener =
                    Compatibility.createPhoneListener(context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
            } catch (exception: SecurityException) {
                val hasReadPhoneStatePermission =
                    PermissionHelper.get().hasReadPhoneStateOrPhoneNumbersPermission()
                Log.e("[CoreContext]", "[Context] Failed to create phone state listener: $exception, READ_PHONE_STATE permission status is $hasReadPhoneStatePermission")
            }
        } else {
            Log.w("[CoreContext]", "[Context] Can't create phone state listener, READ_PHONE_STATE permission isn't granted")
        }
    }

    fun start() {
        Log.i("[CoreContext]", "[Context] Starting")

        core.addListener(listener)

        // CoreContext listener must be added first!
        if (Version.sdkAboveOrEqual(Version.API26_O_80) && corePreferences.useTelecomManager) {
            if (Compatibility.hasTelecomManagerPermissions(context)) {
                Log.i("[CoreContext]", "[Context] Creating Telecom Helper, disabling audio focus requests in AudioHelper")
                core.config.setBool("audio", "android_disable_audio_focus_requests", true)
                val telecomHelper = TelecomHelper.required(context)
                Log.i("[CoreContext]", "[Context] Telecom Helper created, account is ${if (telecomHelper.isAccountEnabled()) "enabled" else "disabled"}")
            } else {
                Log.w("[CoreContext]", "[Context] Can't create Telecom Helper, permissions have been revoked")
                corePreferences.useTelecomManager = false
            }
        }

        configureCore()

        core.start()
        _lifecycleRegistry.currentState = Lifecycle.State.STARTED

        initPhoneStateListener()

        notificationsManager.onCoreReady()

        if (corePreferences.keepServiceAlive) {
            Log.i("[CoreContext]", "[Context] Background mode setting is enabled, starting Service")
            notificationsManager.startForeground()
        }

        _lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        Log.i("[CoreContext]", "[Context] Started")
    }

    fun startCall(to: String) {
        val stringAddress = to

        val address: Address? = core.interpretUrl(to, CallUtils.applyInternationalPrefix())
        if (address == null) {
            Log.e("[CoreContext]", "[Context] Failed to parse $stringAddress, abort outgoing call")
            callErrorMessageResourceId.value = Event(context.getString(R.string.call_error_network_unreachable))
            return
        }

        startCall(address)
    }

    fun startCall(
        address: Address,
        callParams: CallParams? = null,
        forceZRTP: Boolean = false,
        localAddress: Address? = null
    ) {
        if (!core.isNetworkReachable) {
            Log.e("[CoreContext]", "[Context] Network unreachable, abort outgoing call")
            callErrorMessageResourceId.value = Event(context.getString(R.string.call_error_network_unreachable))
            return
        }

        val params = callParams ?: core.createCallParams(null)
        if (params == null) {
            val call = core.inviteAddress(address)
            Log.w("[CoreContext]", "[Context] Starting call $call without params")
            return
        }

        if (forceZRTP) {
            Log.w("[CoreContext]", "[Context] Starting call with ZRTP forced")
            params.mediaEncryption = MediaEncryption.ZRTP
        }
        if (CallUtils.checkIfNetworkHasLowBandwidth(context)) {
            Log.w("[CoreContext]", "[Context] Enabling low bandwidth mode!")
            params.isLowBandwidthEnabled = true
        }

        if (localAddress != null) {
            val account = core.accountList.find { account ->
                account.params.identityAddress?.weakEqual(localAddress) ?: false
            }
            if (account != null) {
                params.account = account
                Log.i("[CoreContext]", "[Context] Using account matching address ${localAddress.asStringUriOnly()} as From")
            } else {
                Log.e("[CoreContext]", "[Context] Failed to find account matching address ${localAddress.asStringUriOnly()}")
            }
        }

        if (corePreferences.sendEarlyMedia) {
            params.isEarlyMediaSendingEnabled = true
        }

        val call = core.inviteAddressWithParams(address, params)

        Log.i("[CoreContext]", "[Context] Starting call $call")
    }
}
