package com.anish.expirydatereminder.logging

/**
 * Small logging seam shared by domain and data code.
 *
 * Exists so `shared/commonMain` can log without depending on `android.util.Log`, which
 * would break the moment an iOS target is added. The Android implementation forwards to
 * Logcat; tests can swap in a recording implementation.
 */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

interface Logger {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)
}

object Log : Logger {
    @Volatile
    private var delegate: Logger = NoOpLogger

    fun install(logger: Logger) {
        delegate = logger
    }

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) =
        delegate.log(level, tag, message, throwable)

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)

    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)

    fun w(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)
}

private object NoOpLogger : Logger {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) = Unit
}
