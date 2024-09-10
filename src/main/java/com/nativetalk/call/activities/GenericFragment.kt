package com.nativetalk.call.activities

import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.linphone.core.tools.Log

abstract class GenericFragment<T : ViewDataBinding> : Fragment() {
    private var _binding: T? = null
    protected val binding get() = _binding!!

    protected var useMaterialSharedAxisXForwardAnimation = true

    protected fun isBindingAvailable(): Boolean {
        return _binding != null
    }

    private fun getFragmentRealClassName(): String {
        return this.javaClass.name
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            try {
                val navController = findNavController()
                Log.d("[Generic Fragment] ${getFragmentRealClassName()} handleOnBackPressed")
                if (!navController.popBackStack()) {
                    Log.d("[Generic Fragment] ${getFragmentRealClassName()} couldn't pop")
                    if (!navController.navigateUp()) {
                        Log.d("[Generic Fragment] ${getFragmentRealClassName()} couldn't navigate up")
                        // Disable this callback & start a new back press event
                        isEnabled = false
                        goBack()
                    }
                }
            } catch (ise: IllegalStateException) {
                Log.e("[Generic Fragment] ${getFragmentRealClassName()} Can't go back: $ise")
            }
        }
    }

    abstract fun getLayoutId(): Int

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = DataBindingUtil.inflate(inflater, getLayoutId(), container, false)
        return _binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()

        onBackPressedCallback.remove()
        _binding = null
    }

    protected fun goBack() {
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    private fun fetchPhoneNumber(contactId: String): String? {
        val contentResolver = requireContext().contentResolver
        val phoneCursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )

        var phoneNumber: String? = null
        phoneCursor?.use {
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (numberIndex != -1 && it.moveToFirst()) {
                phoneNumber = it.getString(numberIndex)
            }
        }
        return phoneNumber
    }
}
