package com.nativetalk.call.activities

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.nativetalk.call.R
import com.nativetalk.call.activities.voip.fragments.IncomingCallFragment
import com.nativetalk.call.activities.voip.fragments.OutgoingCallFragment
import com.nativetalk.call.activities.voip.fragments.SingleCallFragment

import com.nativetalk.call.activities.voip.CallActivity


internal fun Fragment.findMasterNavController(): NavController {
    return parentFragment?.parentFragment?.findNavController() ?: findNavController()
}

fun popupTo(
    popUpTo: Int = -1,
    popUpInclusive: Boolean = false,
    singleTop: Boolean = true
): NavOptions {
    val builder = NavOptions.Builder()
    builder.setPopUpTo(popUpTo, popUpInclusive).setLaunchSingleTop(singleTop)
    return builder.build()
}


internal fun CallActivity.navigateToActiveCall() {
    if (findNavController(R.id.nav_host_fragment).currentDestination?.id != R.id.singleCallFragment) {
        findNavController(R.id.nav_host_fragment).navigate(
            R.id.action_global_singleCallFragment,
            null,
            popupTo(R.id.singleCallFragment, true)
        )
    }
}


internal fun CallActivity.navigateToOutgoingCall() {
    findNavController(R.id.nav_host_fragment).navigate(
        R.id.action_global_outgoingCallFragment,
        null,
        popupTo(R.id.singleCallFragment, true)
    )
}

internal fun CallActivity.navigateToIncomingCall(earlyMediaVideoEnabled: Boolean) {
    val args = Bundle()
    args.putBoolean("earlyMediaVideo", earlyMediaVideoEnabled)
    findNavController(R.id.nav_host_fragment).navigate(
        R.id.action_global_incomingCallFragment,
        args,
        popupTo(R.id.singleCallFragment, true)
    )
}

internal fun OutgoingCallFragment.navigateToActiveCall() {
    findNavController().navigate(
        R.id.action_global_singleCallFragment,
        null,
        popupTo(R.id.outgoingCallFragment, true)
    )
}

internal fun IncomingCallFragment.navigateToActiveCall() {
    findNavController().navigate(
        R.id.action_global_singleCallFragment,
        null,
        popupTo(R.id.incomingCallFragment, true)
    )
}