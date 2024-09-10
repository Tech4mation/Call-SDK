package com.nativetalk.call

interface PhoneStateInterface {
    fun destroy()

    fun isInCall(): Boolean
}
