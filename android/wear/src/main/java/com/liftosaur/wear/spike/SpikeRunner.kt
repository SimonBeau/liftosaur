package com.liftosaur.wear.spike

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

data class SpikeReport(
    val runtime: String,
    val bundle: String,
    val persistence: String,
    val details: List<String>,
    val passed: Boolean,
)

class SpikeRunner(private val context: Context) : AutoCloseable {
    private val engine = LiftosaurJsEngine(context)
    private val store = AtomicStorageStore(context)
    private val preferences = context.getSharedPreferences("wear-spike", Context.MODE_PRIVATE)
    private val deviceId: String = preferences.getString("device-id", null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString("device-id", it).apply()
    }

    suspend fun inspectRuntimeAndRestore(): SpikeReport {
        val availability = engine.initialize()
        if (availability is EngineAvailability.Unsupported) {
            return SpikeReport("UNSUPPORTED", "not executed", "not checked", listOf(availability.reason), false)
        }
        if (!store.exists()) {
            return SpikeReport("SUPPORTED", "real bundle loaded", "no saved workout yet", emptyList(), false)
        }
        return try {
            val storage = requireNotNull(withContext(Dispatchers.IO) { store.read() })
            val progress = data(engine.call("getProgress", storage))
            if (progress == null) {
                SpikeReport("SUPPORTED", "real bundle loaded", "storage restored; no active workout", emptyList(), false)
            } else {
                val sets = progress.getJSONArray("exercises").getJSONObject(0).getJSONArray("sets")
                val first = sets.getJSONObject(0)
                val second = sets.getJSONObject(1)
                val recovered = first.optBoolean("isCompleted") &&
                    first.getJSONObject("completedWeight").getDouble("value") == 135.0 &&
                    second.getJSONObject("weight").getDouble("value") == 145.0
                SpikeReport(
                    "SUPPORTED",
                    "real bundle loaded",
                    if (recovered) "PASS: active workout recovered after process start" else "saved active workout recovered",
                    listOf("set 1 completed=${first.optBoolean("isCompleted")}", "set 2=${weight(second, "weight")}"),
                    recovered,
                )
            }
        } catch (error: Throwable) {
            SpikeReport("SUPPORTED", "real bundle loaded", "restore failed", listOf(error.stackTraceToString()), false)
        }
    }

    suspend fun runProof(): SpikeReport {
        val availability = engine.initialize()
        if (availability is EngineAvailability.Unsupported) {
            return SpikeReport("UNSUPPORTED", "not executed", "not written", listOf(availability.reason), false)
        }

        return try {
            var storage = withContext(Dispatchers.IO) {
                context.assets.open("wear-spike-storage.json").bufferedReader().use { it.readText() }
            }
            requireSuccess(engine.call("validateStorage", storage), "validateStorage")

            val recommendation = requireNotNull(data(engine.call("getNextHistoryRecord", storage)))
            val exercise = recommendation.getJSONArray("exercises").getJSONObject(0)
            val recommendedFirst = exercise.getJSONArray("sets").getJSONObject(0)
            check(recommendation.getString("dayName") == "Engine Proof")
            check(recommendedFirst.getInt("reps") == 5)
            check(recommendedFirst.getJSONObject("weight").getDouble("value") == 100.0)

            storage = mutation("startWorkout", storage, deviceId)
            val started = requireNotNull(data(engine.call("getProgress", storage)))
            val initialSet = started.getJSONArray("exercises").getJSONObject(0).getJSONArray("sets").getJSONObject(0)

            storage = mutation("updateSetWeight", storage, deviceId, 0, 0, 135)
            storage = mutation("completeSet", storage, deviceId, 0, 0)

            val progress = requireNotNull(data(engine.call("getProgress", storage)))
            val sets = progress.getJSONArray("exercises").getJSONObject(0).getJSONArray("sets")
            val first = sets.getJSONObject(0)
            val second = sets.getJSONObject(1)
            val next = requireNotNull(data(engine.call("getNextEntryAndSetIndex", storage, 0, 0)))

            check(first.getBoolean("isCompleted"))
            check(first.getJSONObject("completedWeight").getDouble("value") == 135.0)
            check(second.getJSONObject("weight").getDouble("value") == 145.0)
            check(next.getInt("entryIndex") == 0 && next.getInt("setIndex") == 1)

            SpikeReport(
                runtime = "SUPPORTED",
                bundle = "PASS: real watch-bundle.js executed",
                persistence = "PASS: storage atomically saved after every mutation; force-stop and relaunch to verify",
                details = listOf(
                    "recommended=${recommendation.getString("dayName")}",
                    "exercise=${exercise.getString("name")}",
                    "initial=${initialSet.getInt("reps")} reps @ ${weight(initialSet, "weight")}",
                    "completed=${weight(first, "completedWeight")}",
                    "Liftoscript set 2=${weight(second, "weight")}",
                    "next=entry ${next.getInt("entryIndex")}, set ${next.getInt("setIndex")}",
                ),
                passed = true,
            )
        } catch (error: Throwable) {
            SpikeReport("SUPPORTED", "real bundle execution failed", "last successful mutation retained", listOf(error.stackTraceToString()), false)
        }
    }

    private suspend fun mutation(method: String, storage: String, vararg args: Any): String {
        val response = engine.call(method, storage, *args)
        val updated = requireNotNull(data(response)).toString()
        withContext(Dispatchers.IO) { store.write(updated) }
        return updated
    }

    private fun requireSuccess(response: JSONObject, operation: String) {
        check(response.optBoolean("success")) { "$operation: ${response.optString("error", "unknown JS error")}" }
    }

    private fun data(response: JSONObject): JSONObject? {
        requireSuccess(response, "watch API")
        return if (response.isNull("data")) null else response.getJSONObject("data")
    }

    private fun weight(set: JSONObject, key: String): String {
        val value = set.getJSONObject(key)
        return "${value.getDouble("value").let { if (it % 1.0 == 0.0) it.toInt() else it }}${value.getString("unit")}"
    }

    override fun close() = engine.close()
}
