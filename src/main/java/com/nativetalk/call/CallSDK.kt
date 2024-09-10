package com.nativetalk.call

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import org.linphone.core.AccountCreator
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.LogCollectionState
import org.linphone.core.ProxyConfig
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType
import org.linphone.core.tools.service.CoreService
import java.util.Locale

object NativetalkCallSDK {
    private lateinit var appContext: Context

    @SuppressLint("StaticFieldLeak")
    lateinit var corePreferences: CorePreferences

    @SuppressLint("StaticFieldLeak")
    lateinit var coreContext: CoreContext

    private lateinit var accountCreator: AccountCreator

    var username: String? = null
        set(value) {
            field = value
        }

    var password: String? = null
        set(value) {
            field = value
        }

    var displayName: String? = null
        set(value) {
            field = value
        }

    var domain: String? = null
        set(value) {
            field = value
        }

    fun initialize(context: Context, username: String, password: String, displayName: String, domain: String) {
        this.username = username
        this.password = password
        this.displayName = displayName
        this.domain = domain

        appContext = context.applicationContext
        createConfig(appContext)

        Log.d("[CallSDK]", "initialized CallSDK")
        ensureCoreExists(appContext)
        setUpAccount()
    }

    fun contextExists(): Boolean {
        return ::coreContext.isInitialized
    }


    fun ensureCoreExists(
        context: Context,
        pushReceived: Boolean = false,
        service: CoreService? = null,
        useAutoStartDescription: Boolean = false
    ): Boolean {
        if (!::corePreferences.isInitialized) {
            createConfig(context)
        }

        if (::coreContext.isInitialized && !coreContext.stopped) {
            Log.d("[CallSDK]", "Skipping Core creation (push received? $pushReceived)")
            return false
        }

        Log.i("[CallSDK]",  "Core context is being created ${if (pushReceived) "from push" else ""}")
        coreContext = CoreContext(context, corePreferences.config, service, useAutoStartDescription)
        coreContext.start()
        coreContext.displayName = displayName

        return true
    }

    private fun setUpAccount() {
        accountCreator = coreContext.core.createAccountCreator(corePreferences.xmlRpcServerUrl)
        accountCreator.language = Locale.getDefault().language
        accountCreator.reset()

        Log.i("[CallSDK]", "Loading default values")
        coreContext.core.loadConfigFromXml(corePreferences.defaultValuesPath)
        coreContext.core.clearAccounts()
        coreContext.core.clearAllAuthInfo()
        coreContext.core.clearProxyConfig()

        Log.i("[CallSDK]", "Account setup complete")
        createProxyConfig()
    }

    private var proxyConfigToCheck: ProxyConfig? = null
    private val coreListener = object : CoreListenerStub() {
        @Deprecated("Deprecated in Java")
        override fun onRegistrationStateChanged(
            core: Core,
            cfg: ProxyConfig,
            state: RegistrationState,
            message: String
        ) {
            if (cfg == proxyConfigToCheck) {
                Log.i("[Call SDK]", "Registration state is $state: $message")
                if (state == RegistrationState.Ok) {
                    core.removeListener(this)
                    Log.i("[Call SDK]", "Registration state is OK")
                } else if (state == RegistrationState.Failed) {
                    Log.i("[Call SDK]", "Registration state is Failed")
                    core.removeListener(this)
                }
            }
        }
    }

    fun createProxyConfig() {
        coreContext.core.addListener(coreListener)

        Log.i("[CallSDK]", "Creating proxy config, Config creating")
        accountCreator.username = this.username
        accountCreator.password = this.password
        accountCreator.domain = this.domain
        accountCreator.displayName = this.displayName
        accountCreator.transport = TransportType.Tcp

        val proxyConfig: ProxyConfig? = accountCreator.createProxyConfig()
        proxyConfigToCheck = proxyConfig

        if (proxyConfig == null) {
            Log.e("[CallSDK]", "Account creator couldn't create proxy config")
            coreContext.core.removeListener(coreListener)
            return
        }

        Log.i("[Call SDK]", "Proxy config created")
    }

    private fun createConfig(context: Context) {
        if (::corePreferences.isInitialized) {
            return
        }

        Factory.instance().apply {
            setLogCollectionPath(context.filesDir.absolutePath)
            enableLogCollection(LogCollectionState.Enabled)
            setCacheDir(context.cacheDir.absolutePath)
        }

        corePreferences = CorePreferences(context)

        val config = Factory.instance().createConfigWithFactory(
            corePreferences.configPath, corePreferences.factoryConfigPath
        )
        corePreferences.config = config

        Log.i("[CallSDK]", "Core config & preferences created")
    }

    fun startCall(addressToCall: String) {
//        val addressToCall = "08161883639"
        if (addressToCall.isNotEmpty()) {
            coreContext.startCall(addressToCall)
        }
    }

}
