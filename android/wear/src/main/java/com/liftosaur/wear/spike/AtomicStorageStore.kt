package com.liftosaur.wear.spike

import android.content.Context
import android.util.AtomicFile
import java.io.File

class AtomicStorageStore(context: Context) {
    private val storageFile = AtomicFile(File(context.filesDir, "liftosaur-storage.json"))

    fun exists(): Boolean = storageFile.baseFile.exists()

    fun read(): String? = if (exists()) storageFile.openRead().bufferedReader().use { it.readText() } else null

    fun write(value: String) {
        val output = storageFile.startWrite()
        try {
            output.write(value.toByteArray(Charsets.UTF_8))
            storageFile.finishWrite(output)
        } catch (error: Throwable) {
            storageFile.failWrite(output)
            throw error
        }
    }
}
