package com.family.talkly.debug

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date
import kotlin.system.exitProcess

object CrashHandler {
    private const val PREFS_NAME = "talkly_crash_log"
    private const val KEY_LAST_CRASH = "last_crash"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val crashText = "সময়: ${Date()}\nThread: ${thread.name}\n\n$sw"
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CRASH, crashText)
                    .apply()
            } catch (e: Exception) {
                // Swallow - we don't want the crash handler itself to crash
            }

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(1)
            }
        }
    }

    fun getLastCrash(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CRASH, null)
    }

    fun clearLastCrash(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_CRASH)
            .apply()
    }
}
