
package com.nativetalk.call.activities.voip.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.navGraphViewModels
import com.nativetalk.call.R
import com.nativetalk.call.activities.GenericFragment
import com.nativetalk.call.activities.voip.viewmodels.ControlsViewModel
import com.nativetalk.call.activities.voip.viewmodels.StatusViewModel
import com.nativetalk.call.databinding.VoipStatusFragmentBinding
import org.linphone.core.Call

class StatusFragment : GenericFragment<VoipStatusFragmentBinding>() {
    private lateinit var viewModel: StatusViewModel
    private val controlsViewModel: ControlsViewModel by navGraphViewModels(R.id.call_nav_graph)
    private var zrtpDialog: Dialog? = null

    override fun getLayoutId(): Int = R.layout.voip_status_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        useMaterialSharedAxisXForwardAnimation = false

        viewModel = ViewModelProvider(this)[StatusViewModel::class.java]
        binding.viewModel = viewModel

        binding.setRefreshClickListener {
            viewModel.refreshRegister()
        }

        viewModel.showCallStatsEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                controlsViewModel.showCallStats(skipAnimation = true)
            }
        }
    }

    override fun onDestroy() {
        if (zrtpDialog != null) {
            zrtpDialog?.dismiss()
        }
        super.onDestroy()
    }
}
