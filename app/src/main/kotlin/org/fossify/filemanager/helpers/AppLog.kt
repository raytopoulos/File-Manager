package org.fossify.filemanager.helpers

import android.util.Log

object AppLog {
    private const val PREFIX = "FM"
    private const val MAX_BUFFER_LINES = 500

    private val lock = Any()
    private val buffer = ArrayDeque<String>(MAX_BUFFER_LINES)

    fun d(tag: String, message: String) = log(Log.DEBUG, tag, message, null)

    fun i(tag: String, message: String) = log(Log.INFO, tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) = log(Log.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) = log(Log.ERROR, tag, message, throwable)

    fun dump(): List<String> = synchronized(lock) { buffer.toList() }

    private fun log(priority: Int, tag: String, message: String, throwable: Throwable?) {
        val fullTag = "$PREFIX/$tag"
        val line = if (throwable == null) {
            "$fullTag: $message"
        } else {
            "$fullTag: $message\n${Log.getStackTraceString(throwable)}"
        }

        synchronized(lock) {
            while (buffer.size >= MAX_BUFFER_LINES) {
                buffer.removeFirstOrNull()
            }
            buffer.addLast(line)
        }

        // Ensure something shows up even if Logcat filters tags/levels oddly.
        runCatching { System.err.println(line) }

        if (throwable != null) {
            Log.println(priority, fullTag, message)
            Log.e(fullTag, message, throwable)
        } else {
            Log.println(priority, fullTag, message)
        }
    }
}
