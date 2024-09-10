package com.nativetalk.call.activities.voip.fragments

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.navigation.navGraphViewModels
import com.nativetalk.call.R
import com.nativetalk.call.activities.GenericFragment
import com.nativetalk.call.activities.navigateToActiveCall
import com.nativetalk.call.activities.voip.viewmodels.CallsViewModel
import com.nativetalk.call.activities.voip.viewmodels.ControlsViewModel
import com.nativetalk.call.databinding.VoipCallIncomingFragmentBinding

class IncomingCallFragment : GenericFragment<VoipCallIncomingFragmentBinding>() {
    private val controlsViewModel: ControlsViewModel by navGraphViewModels(R.id.call_nav_graph)
    private val callsViewModel: CallsViewModel by navGraphViewModels(R.id.call_nav_graph)

    override fun getLayoutId(): Int = R.layout.voip_call_incoming_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.controlsViewModel = controlsViewModel
        binding.callsViewModel = callsViewModel

        callsViewModel.callConnectedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                navigateToActiveCall()
            }
        }

        callsViewModel.callEndedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                navigateToActiveCall()
            }
        }

        callsViewModel.newCallStateToTrack.observe(viewLifecycleOwner) {
            val sharedPreference = context?.getSharedPreferences(
                "nativetalk-business.user_data",
                Context.MODE_PRIVATE
            )
            if (sharedPreference != null) {
                with(sharedPreference.edit()) {
                    putString("newCallStateToTrack", it)
                    apply()
                }
            }
        }
    }
}
