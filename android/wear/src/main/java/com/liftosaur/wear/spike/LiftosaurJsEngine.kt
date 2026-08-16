package com.liftosaur.wear.spike

import android.content.Context
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed class EngineAvailability {
    data object Supported : EngineAvailability()
    data class Unsupported(val reason: String) : EngineAvailability()
}

class LiftosaurJsEngine(private val context: Context) : AutoCloseable {
    private val mutex = Mutex()
    private var sandbox: JavaScriptSandbox? = null
    private var isolate: JavaScriptIsolate? = null

    suspend fun initialize(): EngineAvailability = mutex.withLock {
        if (isolate != null) return EngineAvailability.Supported
        if (!JavaScriptSandbox.isSupported()) {
            return EngineAvailability.Unsupported(
                "AndroidX JavaScriptSandbox is not supported by this watch's installed WebView"
            )
        }

        return try {
            val connected = JavaScriptSandbox.createConnectedInstanceAsync(context.applicationContext).await()
            val createdIsolate = connected.createIsolate()
            createdIsolate.addOnTerminatedCallback(context.mainExecutor) {
                isolate = null
            }
            sandbox = connected
            isolate = createdIsolate

            val bundle = withContext(Dispatchers.IO) {
                context.assets.open("watch-bundle.js").bufferedReader().use { it.readText() }
            }
            createdIsolate.evaluateJavaScriptAsync(bundle).await()
            val exposed = createdIsolate.evaluateJavaScriptAsync("typeof globalThis.Liftosaur").await()
            if (exposed != "function") {
                closeInternal()
                EngineAvailability.Unsupported("watch-bundle.js loaded but Liftosaur was $exposed")
            } else {
                EngineAvailability.Supported
            }
        } catch (error: Throwable) {
            closeInternal()
            EngineAvailability.Unsupported("JavaScriptSandbox initialization failed: ${error.message}")
        }
    }

    suspend fun call(method: String, vararg arguments: Any?): JSONObject = mutex.withLock {
        val activeIsolate = isolate ?: error("JavaScript engine is not initialized")
        val encoded = arguments.joinToString(",") { argument ->
            when (argument) {
                null -> "undefined"
                is String -> JSONObject.quote(argument)
                is Boolean, is Number -> argument.toString()
                else -> error("Unsupported JavaScript argument: ${argument::class.java.simpleName}")
            }
        }
        val result = activeIsolate.evaluateJavaScriptAsync("Liftosaur.$method($encoded)").await()
        return JSONObject(result)
    }

    private fun closeInternal() {
        isolate?.close()
        sandbox?.close()
        isolate = null
        sandbox = null
    }

    override fun close() = closeInternal()
}
