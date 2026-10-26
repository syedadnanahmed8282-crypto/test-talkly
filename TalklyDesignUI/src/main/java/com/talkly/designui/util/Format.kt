package com.talkly.designui.util

import java.util.Locale

/** 380 KB, 2.4 MB, 1.3 GB ... */
fun formatSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < 4) { v /= 1024; i++ }
    val decimals = if (i == 0) 0 else if (v >= 100) 0 else if (v >= 10) 1 else 2
    var s = String.format(Locale.US, "%.${decimals}f", v)
    if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
    return "$s ${units[i]}"
}

/** 75 -> 1:15, 3700 -> 1:01:40 */
fun formatClock(totalSec: Int): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}
