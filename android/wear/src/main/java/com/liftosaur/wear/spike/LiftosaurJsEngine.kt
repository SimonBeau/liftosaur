package com.liftosaur.wear.spike

import android.content.Context
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.evaluate
import kotlinx.coroutines.Dispatchers
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
    private var quickJs: QuickJs? = null

    suspend fun initialize(): EngineAvailability = mutex.withLock {
        if (quickJs != null) return EngineAvailability.Supported

        return try {
            val created = QuickJs.create(Dispatchers.Default)
            quickJs = created

            val bundle = withContext(Dispatchers.IO) {
                context.assets.open("watch-bundle.js").bufferedReader().use { it.readText() }
            }
            created.evaluate<Any?>(bundle, filename = "watch-bundle.js")
            val exposed = created.evaluate<String>("typeof globalThis.Liftosaur")
            if (exposed != "function") {
                closeInternal()
                EngineAvailability.Unsupported("watch-bundle.js loaded but Liftosaur was $exposed")
            } else {
                EngineAvailability.Supported
            }
        } catch (error: Throwable) {
            closeInternal()
            EngineAvailability.Unsupported("Embedded QuickJS initialization failed: ${error.message}")
        }
    }

    suspend fun call(method: String, vararg arguments: Any?): JSONObject = mutex.withLock {
        val activeQuickJs = quickJs ?: error("JavaScript engine is not initialized")
        val encoded = arguments.joinToString(",") { argument ->
            when (argument) {
                null -> "undefined"
                is String -> JSONObject.quote(argument)
                is Boolean, is Number -> argument.toString()
                else -> error("Unsupported JavaScript argument: ${argument::class.java.simpleName}")
            }
        }
        val result = activeQuickJs.evaluate<String>("Liftosaur.$method($encoded)")
        return JSONObject(result)
    }

    private fun closeInternal() {
        quickJs?.close()
        quickJs = null
    }

    override fun close() = closeInternal()
}
