package com.shounakmulay.telephony.call

import android.content.Context
import com.shounakmulay.telephony.utils.Constants
import com.shounakmulay.telephony.utils.Constants.BACKGROUND_CALL_SERVICE_INITIALIZED
import com.shounakmulay.telephony.utils.Constants.CHANNEL_CALL_BACKGROUND
import com.shounakmulay.telephony.utils.Constants.HANDLE
import com.shounakmulay.telephony.utils.Constants.HANDLE_BACKGROUND_CALL_STATE
import com.shounakmulay.telephony.utils.Constants.SHARED_PREFERENCES_NAME
import com.shounakmulay.telephony.utils.Constants.SHARED_PREFS_BACKGROUND_CALL_HANDLE
import com.shounakmulay.telephony.utils.Constants.SHARED_PREFS_BACKGROUND_CALL_SETUP_HANDLE
import com.shounakmulay.telephony.utils.ContextHolder
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterJNI
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handle all the background processing on call state changes
 *
 * Call [setBackgroundSetupHandle] and [setBackgroundCallStateHandle] before performing any other operations.
 *
 *
 * Will throw [RuntimeException] if [backgroundChannel] was not initialized by calling [startBackgroundIsolate]
 * before calling [executeDartCallbackInBackgroundIsolate]
 */
object IncomingCallStateHandler : MethodChannel.MethodCallHandler {

    internal val backgroundCallStateQueue =
        Collections.synchronizedList(mutableListOf<HashMap<String, Any?>>())
    internal var isIsolateRunning = AtomicBoolean(false)

    private lateinit var backgroundChannel: MethodChannel
    private lateinit var backgroundFlutterEngine: FlutterEngine
    private lateinit var flutterLoader: FlutterLoader

    private var backgroundCallStateHandle: Long? = null

    /**
     * Initializes a background flutter execution environment and executes the callback
     * to setup the background [MethodChannel]
     *
     * Also initializes the method channel on the android side
     */
    fun startBackgroundIsolate(context: Context, callbackHandle: Long) {
        val appBundlePath = flutterLoader.findAppBundlePath()
        val flutterCallback = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle)

        val dartEntryPoint =
            DartExecutor.DartCallback(context.assets, appBundlePath, flutterCallback)

        backgroundFlutterEngine = FlutterEngine(context, flutterLoader, FlutterJNI())
        backgroundFlutterEngine.dartExecutor.executeDartCallback(dartEntryPoint)

        backgroundChannel =
            MethodChannel(backgroundFlutterEngine.dartExecutor, CHANNEL_CALL_BACKGROUND)
        backgroundChannel.setMethodCallHandler(this)
    }

    /**
     * Called when the background dart isolate has completed setting up the method channel
     *
     * If any call state changes were received during the background isolate was being initialized, it will process
     * all those call state changes.
     */
    fun onChannelInitialized(applicationContext: Context) {
        isIsolateRunning.set(true)
        synchronized(backgroundCallStateQueue) {

            // Handle all the call state changes received before the Dart isolate was
            // initialized, then clear the queue.
            val iterator = backgroundCallStateQueue.iterator()
            while (iterator.hasNext()) {
                executeDartCallbackInBackgroundIsolate(applicationContext, iterator.next())
            }
            backgroundCallStateQueue.clear()
        }
    }

    /**
     * Invoke the method on background channel to handle the call state change
     */
    internal fun executeDartCallbackInBackgroundIsolate(
        context: Context,
        callInfo: HashMap<String, Any?>
    ) {
        if (!this::backgroundChannel.isInitialized) {
            throw RuntimeException(
                "setBackgroundChannel was not called before call state changes came in, exiting."
            )
        }

        val args: MutableMap<String, Any?> = HashMap()
        if (backgroundCallStateHandle == null) {
            backgroundCallStateHandle = getBackgroundCallStateHandle(context)
        }
        args[HANDLE] = backgroundCallStateHandle
        args["callInfo"] = callInfo
        backgroundChannel.invokeMethod(HANDLE_BACKGROUND_CALL_STATE, args)
    }

    /**
     * Gets an instance of FlutterLoader from the FlutterInjector, starts initialization and
     * waits until initialization is complete.
     *
     * Should be called before invoking any other background methods.
     */
    internal fun initialize(context: Context) {
        val flutterInjector = FlutterInjector.instance()
        flutterLoader = flutterInjector.flutterLoader()
        flutterLoader.startInitialization(context)
        flutterLoader.ensureInitializationComplete(context.applicationContext, null)
    }

    fun setBackgroundCallStateHandle(context: Context, handle: Long) {
        backgroundCallStateHandle = handle

        // Store background call state handle in shared preferences so it can be retrieved
        // by other application instances.
        val preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().putLong(SHARED_PREFS_BACKGROUND_CALL_HANDLE, handle).apply()
    }

    fun setBackgroundSetupHandle(context: Context, setupHandle: Long) {
        // Store background setup handle in shared preferences so it can be retrieved
        // by other application instances.
        val preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().putLong(SHARED_PREFS_BACKGROUND_CALL_SETUP_HANDLE, setupHandle).apply()
    }

    private fun getBackgroundCallStateHandle(context: Context): Long {
        val preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        return preferences.getLong(SHARED_PREFS_BACKGROUND_CALL_HANDLE, 0)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            BACKGROUND_CALL_SERVICE_INITIALIZED -> {
                onChannelInitialized(ContextHolder.applicationContext!!)
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    /**
     * Process call state change in background if the app is not in foreground
     */
    fun processCallStateInBackground(context: Context, callInfo: HashMap<String, Any?>) {
        if (!isIsolateRunning.get()) {
            initialize(context)
            val preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            val backgroundCallbackHandle = preferences.getLong(SHARED_PREFS_BACKGROUND_CALL_SETUP_HANDLE, 0)
            startBackgroundIsolate(context, backgroundCallbackHandle)
            backgroundCallStateQueue.add(callInfo)
        } else {
            executeDartCallbackInBackgroundIsolate(context, callInfo)
        }
    }

    /**
     * Check if the application is currently in the foreground
     */
    fun isApplicationForeground(context: Context): Boolean {
        val preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        return preferences.getBoolean("isApplicationForeground", true)
    }

    /**
     * Handle call state change - entry point from TelephonyPlugin
     * Determines whether to process in background based on app state
     */
    fun handleCallStateChange(context: Context, callInfo: Map<String, Any>) {
        val callInfoHashMap = HashMap<String, Any?>()
        callInfo.forEach { (key, value) -> callInfoHashMap[key] = value }
        
        // Check if background call state handling is configured
        val preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val backgroundHandle = preferences.getLong(SHARED_PREFS_BACKGROUND_CALL_HANDLE, 0)
        
        if (backgroundHandle != 0L && !isApplicationForeground(context)) {
            // App is in background and background handling is configured
            processCallStateInBackground(context, callInfoHashMap)
        }
        // If app is in foreground, the normal channel will handle it
    }
}