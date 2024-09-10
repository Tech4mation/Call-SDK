/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.nativetalk.call.activities.voip.data

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.nativetalk.call.NativetalkCallSDK.coreContext
import com.nativetalk.call.utils.CallUtils
import kotlinx.coroutines.CoroutineScope
import org.linphone.core.Address
import org.linphone.core.ChatRoomSecurityLevel
import org.linphone.core.Friend

interface ContactDataInterface {
    val contact: MutableLiveData<Friend>

    val displayName: MutableLiveData<String>

    val securityLevel: MutableLiveData<ChatRoomSecurityLevel>

    val showGroupChatAvatar: Boolean
        get() = false

    val coroutineScope: CoroutineScope
}

open class GenericContactData(private val sipAddress: Address) : ContactDataInterface {
    final override val contact: MutableLiveData<Friend> = MutableLiveData<Friend>()
    final override val displayName: MutableLiveData<String> = MutableLiveData<String>()
    final override val securityLevel: MutableLiveData<ChatRoomSecurityLevel> = MutableLiveData<ChatRoomSecurityLevel>()
    final override val coroutineScope: CoroutineScope = coreContext.coroutineScope

    init {
        securityLevel.value = ChatRoomSecurityLevel.ClearText
        contactLookup()
    }

    open fun destroy() {
    }

    private fun contactLookup() {
        displayName.value = CallUtils.getDisplayName(sipAddress)

        val c = coreContext.contactsManager.findContactByAddress(sipAddress)
        if (c != null) {
            contact.value = c!!
        }
    }
}