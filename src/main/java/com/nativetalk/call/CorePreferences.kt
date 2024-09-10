package com.nativetalk.call

import android.content.Context
import com.nativetalk.call.NativetalkCallSDK.coreContext
import org.linphone.core.Config

class CorePreferences(private val context: Context) {
    private var _config: Config? = null

    companion object {
        const val OVERLAY_CLICK_SENSITIVITY = 10

        private const val encryptedSharedPreferencesFile = "encrypted.pref"
    }

    var config: Config
        get() = _config ?: coreContext.core.config
        set(value) {
            _config = value
        }

    var keepServiceAlive: Boolean
        get() = config.getBool("app", "keep_service_alive", false)
        set(value) {
            config.setBool("app", "keep_service_alive", value)
        }

    var useTelecomManager: Boolean
        // Some permissions are required, so keep it to false so user has to manually enable it and give permissions
        get() = config.getBool("app", "use_self_managed_telecom_manager", false)
        set(value) {
            config.setBool("app", "use_self_managed_telecom_manager", value)
            // We need to disable audio focus requests when enabling telecom manager, otherwise it creates conflicts
            config.setBool("audio", "android_disable_audio_focus_requests", value)
        }

    var autoAnswerEnabled: Boolean
        get() = config.getBool("app", "auto_answer", false)
        set(value) {
            config.setBool("app", "auto_answer", value)
        }

    var autoAnswerDelay: Int
        get() = config.getInt("app", "auto_answer_delay", 0)
        set(value) {
            config.setInt("app", "auto_answer_delay", value)
        }

    var routeAudioToBluetoothIfAvailable: Boolean
        get() = config.getBool("app", "route_audio_to_bluetooth_if_available", true)
        set(value) {
            config.setBool("app", "route_audio_to_bluetooth_if_available", value)
        }

    var automaticallyStartCallRecording: Boolean
        get() = config.getBool("app", "auto_start_call_record", false)
        set(value) {
            config.setBool("app", "auto_start_call_record", value)
        }

    var xmlRpcServerUrl: String?
        get() = config.getString("assistant", "xmlrpc_url", null)
        set(value) {
            config.setString("assistant", "xmlrpc_url", value)
        }

    val defaultValuesPath: String
        get() = context.filesDir.absolutePath + "/assistant_default_values"

    val preventInterfaceFromShowingUp: Boolean
        get() = config.getBool("app", "keep_app_invisible", false)

    // By default we will record voice messages using MKV format and Opus audio encoding
    // If disabled, WAV format will be used instead. Warning: files will be heavier!
    var sendEarlyMedia: Boolean
        get() = config.getBool("sip", "outgoing_calls_early_media", false)
        set(value) {
            config.setBool("sip", "outgoing_calls_early_media", value)
        }

    val staticPicturePath: String
        get() = context.filesDir.absolutePath + "/share/images/nowebcamcif.jpg"

    var manuallyDisabledTelecomManager: Boolean
        get() = config.getBool("app", "user_disabled_self_managed_telecom_manager", false)
        set(value) {
            config.setBool("app", "user_disabled_self_managed_telecom_manager", value)
        }

    val defaultDomain: String
        get() = config.getString("app", "default_domain", "dashboard.nativetalk.com.ng:5060")!!


    var deviceName: String
        get() = config.getString("app", "device_name", Compatibility.getDeviceName(context))!!
        set(value) = config.setString("app", "device_name", value)

    var defaultAccountAvatarPath: String?
        get() = config.getString("app", "default_avatar_path", null)
        set(value) {
            config.setString("app", "default_avatar_path", value)
        }

    var storePresenceInNativeContact: Boolean
        get() = config.getBool("app", "store_presence_in_native_contact", false)
        set(value) {
            config.setBool("app", "store_presence_in_native_contact", value)
        }

    var forcePortrait: Boolean
        get() = config.getBool("app", "force_portrait_orientation", false)
        set(value) {
            config.setBool("app", "force_portrait_orientation", value)
        }

    var enableAnimations: Boolean
        get() = config.getBool("app", "enable_animations", false)
        set(value) {
            config.setBool("app", "enable_animations", value)
        }

    var acceptEarlyMedia: Boolean
        get() = config.getBool("sip", "incoming_calls_early_media", false)
        set(value) {
            config.setBool("sip", "incoming_calls_early_media", value)
        }

    var showCallOverlay: Boolean
        get() = config.getBool("app", "call_overlay", true)
        set(value) {
            config.setBool("app", "call_overlay", value)
        }

    // Show overlay even when app is in background, requires permission
    var systemWideCallOverlay: Boolean
        get() = config.getBool("app", "system_wide_call_overlay", false)
        set(value) {
            config.setBool("app", "system_wide_call_overlay", value)
        }

    var callRightAway: Boolean
        get() = config.getBool("app", "call_right_away", false)
        set(value) {
            config.setBool("app", "call_right_away", value)
        }


    private val darkModeAllowed: Boolean
        get() = config.getBool("app", "dark_mode_allowed", false)

    var darkMode: Int
        get() {
            if (!darkModeAllowed) return 0
            return config.getInt("app", "dark_mode", 0)
        }
        set(value) {
            config.setInt("app", "dark_mode", value)
        }


    val configPath: String
        get() = context.filesDir.absolutePath + "/.nativetalk"

    val factoryConfigPath: String
        get() = context.filesDir.absolutePath + "/nativetalk"

}