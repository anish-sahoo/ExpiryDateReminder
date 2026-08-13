package com.anish.expirydatereminder.logging

import android.util.Log as AndroidLog

/**
 * Forwards to Logcat.
 *
 * DEBUG and INFO are dropped in release builds so shipping binaries stay quiet, while
 * WARN and ERROR always survive: those are the ones worth seeing in a bug report.
 */
class AndroidLogger(private val debugBuild: Boolean) : Logger {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val prefixed = "$TAG_PREFIX$tag"
        when (level) {
            LogLevel.DEBUG -> if (debugBuild) AndroidLog.d(prefixed, message, throwable)
            LogLevel.INFO -> if (debugBuild) AndroidLog.i(prefixed, message, throwable)
            LogLevel.WARN -> AndroidLog.w(prefixed, message, throwable)
            LogLevel.ERROR -> AndroidLog.e(prefixed, message, throwable)
        }
    }

    private companion object {
        /** Makes `adb logcat | grep EDR/` pick up everything the app emits. */
        const val TAG_PREFIX = "EDR/"
    }
}
