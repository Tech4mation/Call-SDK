package com.nativetalk.call.activities.voip

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.window.layout.FoldingFeature
import com.nativetalk.call.Compatibility
import com.nativetalk.call.NativetalkCallSDK.coreContext
import com.nativetalk.call.NativetalkCallSDK.corePreferences
import com.nativetalk.call.R
import com.nativetalk.call.activities.ProximitySensorActivity
import com.nativetalk.call.activities.navigateToActiveCall
import com.nativetalk.call.activities.navigateToIncomingCall
import com.nativetalk.call.activities.navigateToOutgoingCall
import com.nativetalk.call.activities.voip.viewmodels.CallsViewModel
import com.nativetalk.call.activities.voip.viewmodels.ControlsViewModel
import com.nativetalk.call.databinding.VoipActivityBinding
import com.nativetalk.call.utils.PermissionHelper
import org.linphone.core.Call
import org.linphone.mediastream.Version

class CallActivity : ProximitySensorActivity() {
    private lateinit var binding: VoipActivityBinding
    private lateinit var controlsViewModel: ControlsViewModel
    private lateinit var callsViewModel: CallsViewModel
    private var alertDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("[CallActivity]", "On create method reached")
        super.onCreate(savedInstanceState)

        Compatibility.setShowWhenLocked(this, true)
        Compatibility.setTurnScreenOn(this, true)

        // Leaks on API 27+: https://stackoverflow.com/questions/60477120/keyguardmanager-memory-leak
        Compatibility.requestDismissKeyguard(this)

        binding = DataBindingUtil.setContentView(this, R.layout.voip_activity)
        binding.lifecycleOwner = this
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // This can't be done in onCreate(), has to be at least in onPostCreate() !
        val navController = binding.navHostFragment.findNavController()
        val navControllerStoreOwner = navController.getViewModelStoreOwner(R.id.call_nav_graph)

        controlsViewModel = ViewModelProvider(navControllerStoreOwner)[ControlsViewModel::class.java]
        binding.controlsViewModel = controlsViewModel

        callsViewModel = ViewModelProvider(navControllerStoreOwner)[CallsViewModel::class.java]

        controlsViewModel.askPermissionEvent.observe(
            this
        ) {
            it.consume { permission ->
                Log.i("[CallActivity]",  "[Call Activity] Asking for $permission permission")
                requestPermissions(arrayOf(permission), 0)
            }
        }

        controlsViewModel.fullScreenMode.observe(
            this
        ) { hide ->
            Compatibility.hideAndroidSystemUI(hide, window)
        }

        controlsViewModel.proximitySensorEnabled.observe(
            this
        ) { enabled ->
            enableProximitySensor(enabled)
        }


        callsViewModel.noMoreCallEvent.observe(
            this
        ) {
            it.consume { noMoreCall ->
                if (noMoreCall) {
                    Log.i("[CallActivity]", "[Call Activity] No more call event fired, finishing activity")
                    finish()
                }
            }
        }

        callsViewModel.askWriteExternalStoragePermissionEvent.observe(
            this
        ) {
            it.consume {
                Log.i("[CallActivity]", "[Call Activity] Asking for WRITE_EXTERNAL_STORAGE permission to take snapshot")
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
        }

        callsViewModel.currentCallData.observe(
            this
        ) { callData ->
            if (callData.call.conference == null) {
                Log.i("[CallActivity]", "[Call Activity] Current call isn't linked to a conference, changing fragment")
                navigateToActiveCall()
            }
        }

        callsViewModel.askPermissionEvent.observe(
            this
        ) {
            it.consume { permission ->
                Log.i("[CallActivity]", "[Call Activity] Asking for $permission permission")
                requestPermissions(arrayOf(permission), 0)
            }
        }

        checkPermissions()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
    }


    override fun onResume() {
        super.onResume()

        if (coreContext.core.callsNb == 0) {
            Log.d("[CallActivity]", "[Call Activity] Resuming but no call found...")
            if (isTaskRoot) {
                // When resuming app from recent tasks make sure MainActivity will be launched if there is no call
//                val intent = Intent()
//                intent.setClass(this, MainActivity::class.java)
//                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                startActivity(intent)
            } else {
                finish()
            }
            return
        }
        coreContext.removeCallOverlay()

        val currentCall = coreContext.core.currentCall
        when (currentCall?.state) {
            Call.State.OutgoingInit, Call.State.OutgoingEarlyMedia, Call.State.OutgoingProgress, Call.State.OutgoingRinging -> {
                navigateToOutgoingCall()
            }
            Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                val earlyMediaVideoEnabled = corePreferences.acceptEarlyMedia &&
                    currentCall.state == Call.State.IncomingEarlyMedia &&
                    currentCall.currentParams.isVideoEnabled
                navigateToIncomingCall(earlyMediaVideoEnabled)
            }
            else -> {}
        }
    }

    override fun onPause() {
        val core = coreContext.core
        if (core.callsNb > 0) {
            coreContext.createCallOverlay()
        }

        super.onPause()
    }

    override fun onDestroy() {
        coreContext.core.nativeVideoWindowId = null
        coreContext.core.nativePreviewWindowId = null

        super.onDestroy()
    }


    private fun checkPermissions() {
        val permissionsRequiredList = arrayListOf<String>()

        if (!PermissionHelper.get().hasRecordAudioPermission()) {
            Log.i("[CallActivity]", "[Call Activity] Asking for RECORD_AUDIO permission")
            permissionsRequiredList.add(Manifest.permission.RECORD_AUDIO)
        }

        if (callsViewModel.currentCallData.value?.call?.currentParams?.isVideoEnabled == true &&
            !PermissionHelper.get().hasCameraPermission()
        ) {
            Log.i("[CallActivity]", "[Call Activity] Asking for CAMERA permission")
            permissionsRequiredList.add(Manifest.permission.CAMERA)
        }

        if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12) && !PermissionHelper.get().hasBluetoothConnectPermission()) {
            Log.i("[CallActivity]", "[Call Activity] Asking for BLUETOOTH_CONNECT permission")
            permissionsRequiredList.add(Compatibility.BLUETOOTH_CONNECT)
        }

        if (permissionsRequiredList.isNotEmpty()) {
            val permissionsRequired = arrayOfNulls<String>(permissionsRequiredList.size)
            permissionsRequiredList.toArray(permissionsRequired)
            requestPermissions(permissionsRequired, 0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 0) {
            for (i in permissions.indices) {
                when (permissions[i]) {
                    Manifest.permission.RECORD_AUDIO -> if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.i("[CallActivity]", "[Call Activity] RECORD_AUDIO permission has been granted")
                        callsViewModel.updateMicState()
                    }
                    Manifest.permission.CAMERA -> if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.i("[CallActivity]", "[Call Activity] CAMERA permission has been granted")
                        coreContext.core.reloadVideoDevices()
                    }
                    Compatibility.BLUETOOTH_CONNECT -> if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.i("[CallActivity]", "[Call Activity] BLUETOOTH_CONNECT permission has been granted")
                    }
                }
            }
        } else if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i("[CallActivity]", "[Call Activity] WRITE_EXTERNAL_STORAGE permission has been granted, taking snapshot")
            }
        } else if (requestCode == 9) {
            Toast.makeText(this, "has sytem alert window permisdsions", Toast.LENGTH_SHORT).show()
            alertDialog?.dismiss()

            Log.i("[CallActivity]", "[Home] REquested permission")
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onLayoutChanges(foldingFeature: FoldingFeature?) {
        foldingFeature ?: return
        Log.i("[CallActivity]", "[Call Activity] Folding feature state changed: ${foldingFeature.state}, orientation is ${foldingFeature.orientation}")

        controlsViewModel.foldingState.value = foldingFeature
    }
}
