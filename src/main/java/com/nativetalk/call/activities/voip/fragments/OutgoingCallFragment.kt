package com.nativetalk.call.activities.voip.fragments

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.Toast
import androidx.navigation.navGraphViewModels
import com.nativetalk.call.R
import com.nativetalk.call.activities.navigateToActiveCall
import com.nativetalk.call.activities.GenericFragment
import com.nativetalk.call.activities.voip.viewmodels.CallsViewModel
import com.nativetalk.call.activities.voip.viewmodels.ControlsViewModel
import com.nativetalk.call.databinding.VoipCallOutgoingFragmentBinding


class OutgoingCallFragment : GenericFragment<VoipCallOutgoingFragmentBinding>() {
    private val controlsViewModel: ControlsViewModel by navGraphViewModels(R.id.call_nav_graph)
    private val callsViewModel: CallsViewModel by navGraphViewModels(R.id.call_nav_graph)

    override fun getLayoutId(): Int = R.layout.voip_call_outgoing_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.controlsViewModel = controlsViewModel
        binding.callsViewModel = callsViewModel

        // Observe customer name and update UI
        callsViewModel.customerName.observe(viewLifecycleOwner) {
            binding.calleeName.setText(it)
        }

        // Observe speaker state changes
        controlsViewModel.callSpeakerState.observe(viewLifecycleOwner) {
            val speakerImg = binding.root.findViewById<ImageView>(R.id.speaker)

            if (it) {
                Toast.makeText(requireActivity(), "On speaker audio", Toast.LENGTH_SHORT).show()
                speakerImg.setImageResource(R.drawable.nativetalk_call_speaker_active)
            } else {
                speakerImg.setImageResource(R.drawable.nativetalk_call_speaker)
            }
        }

        // Observe call pause state changes
        callsViewModel.callPauseState.observe(viewLifecycleOwner) {
            val pauseImg = binding.root.findViewById<ImageView>(R.id.pause_call)

            if (it) {
                Toast.makeText(requireActivity(), "Call held", Toast.LENGTH_SHORT).show()
                pauseImg.setImageResource(R.drawable.nativetalk_dial_hold)
            } else {
                pauseImg.setImageResource(R.drawable.nativetalk_call_hold)
            }
        }

        // Observe microphone state changes
        callsViewModel.callMicrophoneState.observe(viewLifecycleOwner) {
            val microphoneImg = binding.root.findViewById<ImageView>(R.id.microphone)

            if (it) {
                microphoneImg.setImageResource(R.drawable.nativetalk_call_mute)
            } else {
                microphoneImg.setImageResource(R.drawable.nativetalk_call_mute_active)
            }
        }

        // Track new call state
        callsViewModel.newCallStateToTrack.observe(viewLifecycleOwner) {
            val sharedPreference = context?.getSharedPreferences(
                "nativetalk-business.user_data",
                Context.MODE_PRIVATE
            )
            sharedPreference?.edit()?.apply {
                putString("newCallStateToTrack", it)
                apply()
            }
        }

        // Handle call connected event
        callsViewModel.callConnectedEvent.observe(viewLifecycleOwner) {
            it.consume {
                navigateToActiveCall()
            }
        }

        // Handle call ended event
        callsViewModel.callEndedEvent.observe(viewLifecycleOwner) {
            it.consume {
                navigateToActiveCall()
            }
        }

        // Update call timer
        callsViewModel.currentCallData.observe(viewLifecycleOwner) {
            it?.let {
                val timer = binding.root.findViewById<Chronometer>(R.id.outgoing_call_timer)
                timer.base = SystemClock.elapsedRealtime() - (1000 * it.call.duration) // timestamps are in seconds
                timer.start()
            }
        }

        // Mark call history change
        val sharedPreference = requireContext().getSharedPreferences("nativetalk-business.user_data", Context.MODE_PRIVATE)
        with(sharedPreference.edit()) {
            putBoolean("callHistoriesChange", true)
            apply()
        }
    }
}
