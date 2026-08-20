package com.family.talkly.debug

import java.io.BufferedReader
import java.io.InputStreamReader

object LogcatHelper {
    fun captureRecentLogs(maxLines: Int = 500): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val allLines = ArrayList<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                allLines.add(line!!)
            }
            reader.close()

            // Prioritize error/warning lines and anything from our own tags
            val relevant = allLines.filter {
                it.contains("Talkly", ignoreCase = true) ||
                it.contains("Firestore", ignoreCase = true) ||
                it.contains("AndroidRuntime", ignoreCase = true) ||
                it.contains(" E ") || it.contains(" W ") || it.contains("FATAL")
            }

            val finalLines = if (relevant.size > maxLines) relevant.takeLast(maxLines) else relevant

            if (finalLines.isEmpty()) {
                "কোনো প্রাসঙ্গিক error/warning পাওয়া যায়নি। সাম্প্রতিক raw log:\n\n" +
                    allLines.takeLast(maxLines).joinToString("\n")
            } else {
                finalLines.joinToString("\n")
            }
        } catch (e: Exception) {
            "Logcat capture failed: ${e.localizedMessage}\n\n(ফোনে logcat command ব্লক করা থাকতে পারে - এই ক্ষেত্রে CrashHandler এর মাধ্যমে ধরা crash info দেখুন)"
        }
    }
}
