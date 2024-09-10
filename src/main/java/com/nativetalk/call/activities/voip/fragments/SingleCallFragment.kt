package com.nativetalk.call.activities.voip.fragments

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.Toast
import androidx.navigation.navGraphViewModels
import com.nativetalk.call.R
import com.nativetalk.call.activities.*

import com.nativetalk.call.activities.GenericFragment
import com.nativetalk.call.activities.voip.viewmodels.CallsViewModel
import com.nativetalk.call.activities.voip.viewmodels.ControlsViewModel
import com.nativetalk.call.databinding.VoipSingleCallFragmentBinding

class SingleCallFragment : GenericFragment<VoipSingleCallFragmentBinding>() {
    private val controlsViewModel: ControlsViewModel by navGraphViewModels(R.id.call_nav_graph)
    private val callsViewModel: CallsViewModel by navGraphViewModels(R.id.call_nav_graph)

    private lateinit var sharedPreference: SharedPreferences

    private var dialog: Dialog? = null

    override fun getLayoutId(): Int = R.layout.voip_single_call_fragment

    override fun onStart() {
        useMaterialSharedAxisXForwardAnimation = false
        super.onStart()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        controlsViewModel.hideCallStats()

        binding.lifecycleOwner = viewLifecycleOwner
        binding.controlsViewModel = controlsViewModel
        binding.callsViewModel = callsViewModel

        sharedPreference = requireActivity().getSharedPreferences("nativetalk-call.user_data", Context.MODE_PRIVATE)

        callsViewModel.callPauseState.observe(viewLifecycleOwner) {
            val pauseImg = binding.root.findViewById<ImageView>(R.id.pause_call)
            if (it) {
                Toast.makeText(requireActivity(), "Call held", Toast.LENGTH_SHORT).show()
                pauseImg.setImageResource(R.drawable.nativetalk_dial_hold)
            } else {
                pauseImg.setImageResource(R.drawable.nativetalk_call_hold)
            }
        }

        controlsViewModel.callSpeakerState.observe(viewLifecycleOwner) {
            val speakerImg = binding.root.findViewById<ImageView>(R.id.speaker)
            if (it) {
                Toast.makeText(requireActivity(), "On speaker audio", Toast.LENGTH_SHORT).show()
                speakerImg.setImageResource(R.drawable.nativetalk_call_speaker_active)
            } else {
                speakerImg.setImageResource(R.drawable.nativetalk_call_speaker)
            }
        }

        callsViewModel.callMicrophoneState.observe(viewLifecycleOwner) {
            val microphoneImg = binding.root.findViewById<ImageView>(R.id.microphone)
            if (it) {
                microphoneImg.setImageResource(R.drawable.nativetalk_call_mute)
            } else {
                microphoneImg.setImageResource(R.drawable.nativetalk_call_mute_active)
            }
        }

        callsViewModel.currentCallData.observe(viewLifecycleOwner) {
            it?.let {
                val timer = binding.root.findViewById<Chronometer>(R.id.active_call_timer)
                timer.base = SystemClock.elapsedRealtime() - (1000 * it.call.duration)
                timer.start()
            }
        }

    }

    override fun onPause() {
        super.onPause()
        with(sharedPreference.edit()) {
            putBoolean("updateDetails", true)
            apply()
        }
        controlsViewModel.hideExtraButtons(true)
    }
}
