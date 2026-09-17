package com.family.talkly.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var categorizedLogs by remember { mutableStateOf<CategorizedLogs?>(null) }
    var lastCrashReport by remember { mutableStateOf<String?>(null) }
    var lastSavedInfo by remember { mutableStateOf<String?>(null) }
    var lastUpdatedTime by remember { mutableStateOf("") }
    var expandedCategories by remember { mutableStateOf(setOf<LogCategory>()) }

    fun refreshLogs() {
        lastCrashReport = CrashHandler.getLastCrash(context)
        val logs = LogcatHelper.captureCategorizedLogs()
        categorizedLogs = logs
        lastUpdatedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        // Auto-expand Crash category if a persistent crash exists
        if (!lastCrashReport.isNullOrBlank()) {
            expandedCategories = expandedCategories + LogCategory.CRASH
        }
    }

    LaunchedEffect(Unit) {
        refreshLogs()
    }

    fun buildCategorizedReport(): String {
        val currentLogs = categorizedLogs ?: LogcatHelper.captureCategorizedLogs()
        val crash = CrashHandler.getLastCrash(context)
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val sb = StringBuilder()
        sb.append("Talkly Categorized Debug Report\n")
        sb.append("Exported: $timeStr\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})\n\n")

        for (cat in LogCategory.values()) {
            sb.append("${cat.exportHeader}\n")
            if (cat == LogCategory.CRASH) {
                var hasCrashContent = false
                if (!crash.isNullOrBlank()) {
                    sb.append("[PERSISTENT LAST CRASH REPORT]\n$crash\n\n")
                    hasCrashContent = true
                }
                val crashLines = currentLogs.categorized[cat] ?: emptyList()
                if (crashLines.isNotEmpty()) {
                    sb.append(crashLines.joinToString("\n"))
                    sb.append("\n")
                    hasCrashContent = true
                }
                if (!hasCrashContent) {
                    sb.append("(No crash logs recorded)\n")
                }
            } else {
                val lines = currentLogs.categorized[cat] ?: emptyList()
                if (lines.isNotEmpty()) {
                    sb.append(lines.joinToString("\n"))
                    sb.append("\n")
                } else {
                    sb.append("(No logs recorded)\n")
                }
            }
            sb.append("\n")
        }

        if (!currentLogs.rawLogFallback.isNullOrBlank()) {
            sb.append("=== RAW LOGCAT FALLBACK ===\n")
            sb.append(currentLogs.rawLogFallback)
            sb.append("\n")
        }

        return sb.toString()
    }

    fun saveToDownloads(): String? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "talkly_debug_$timestamp.txt"
        val content = buildCategorizedReport()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray())
                    }
                    "Downloads/$fileName"
                } else {
                    null
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { out ->
                    out.write(content.toByteArray())
                }
                file.absolutePath
            }
        } catch (e: Exception) {
            try {
                val fallbackDir = context.getExternalFilesDir(null)
                val file = File(fallbackDir, fileName)
                FileOutputStream(file).use { out ->
                    out.write(content.toByteArray())
                }
                file.absolutePath
            } catch (e2: Exception) {
                null
            }
        }
    }

    fun copyCategoryLogs(category: LogCategory) {
        val currentLogs = categorizedLogs ?: return
        val contentToCopy = when (category) {
            LogCategory.CRASH -> {
                val parts = mutableListOf<String>()
                if (!lastCrashReport.isNullOrBlank()) {
                    parts.add("[PERSISTENT LAST CRASH REPORT]\n$lastCrashReport")
                }
                val crashLines = currentLogs.categorized[category] ?: emptyList()
                if (crashLines.isNotEmpty()) {
                    parts.add(crashLines.joinToString("\n"))
                }
                if (parts.isEmpty()) "No crash logs recorded." else parts.joinToString("\n\n")
            }
            else -> {
                val lines = currentLogs.categorized[category] ?: emptyList()
                if (lines.isEmpty()) "No logs recorded for ${category.displayName}." else lines.joinToString("\n")
            }
        }

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Talkly ${category.displayName} Logs", contentToCopy))
            Toast.makeText(context, "${category.displayName} logs copied", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Copy failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0D1117)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🐞 Debug Logs",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (lastUpdatedTime.isNotBlank()) {
                            Text(
                                text = "Updated: $lastUpdatedTime",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Controls: Refresh, Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            refreshLogs()
                            lastSavedInfo = null
                            Toast.makeText(context, "Logs refreshed", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF22D3EE)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            val savedPath = saveToDownloads()
                            if (savedPath != null) {
                                lastSavedInfo = "Saved: $savedPath"
                                Toast.makeText(context, "Saved: $savedPath", Toast.LENGTH_LONG).show()
                            } else {
                                lastSavedInfo = "Save failed"
                                Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (lastSavedInfo != null) {
                    Text(
                        text = lastSavedInfo ?: "",
                        color = Color(0xFF4ADE80),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Categorized Log Sections
                val current = categorizedLogs
                if (current == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Loading logs...", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(LogCategory.values()) { category ->
                            val isExpanded = expandedCategories.contains(category)
                            val catLogs = current.categorized[category] ?: emptyList()
                            val count = if (category == LogCategory.CRASH) {
                                (if (!lastCrashReport.isNullOrBlank()) 1 else 0) + catLogs.size
                            } else {
                                catLogs.size
                            }

                            CategoryLogCard(
                                category = category,
                                count = count,
                                isExpanded = isExpanded,
                                logs = catLogs,
                                persistentCrash = if (category == LogCategory.CRASH) lastCrashReport else null,
                                onToggleExpand = {
                                    expandedCategories = if (isExpanded) {
                                        expandedCategories - category
                                    } else {
                                        expandedCategories + category
                                    }
                                },
                                onCopyCategory = {
                                    copyCategoryLogs(category)
                                },
                                onClearCrash = if (category == LogCategory.CRASH) {
                                    {
                                        CrashHandler.clearLastCrash(context)
                                        lastCrashReport = null
                                        Toast.makeText(context, "Crash log cleared", Toast.LENGTH_SHORT).show()
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryLogCard(
    category: LogCategory,
    count: Int,
    isExpanded: Boolean,
    logs: List<String>,
    persistentCrash: String?,
    onToggleExpand: () -> Unit,
    onCopyCategory: () -> Unit,
    onClearCrash: (() -> Unit)?
) {
    val cardBg = Color(0xFF161E28)
    val cardBorder = Color(0xFF223042)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Title, Count, Copy button, Expand/Collapse chevron
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Category Icon & Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = category.icon, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = category.displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "$count ${if (count == 1) "log" else "logs"}",
                            fontSize = 11.sp,
                            color = if (category == LogCategory.CRASH && count > 0) Color(0xFFF87171) else Color(0xFF94A3B8)
                        )
                    }
                }

                // Actions: Copy Button & Expand Chevron
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF202C3C),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onCopyCategory() }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy ${category.displayName}",
                                tint = Color(0xFF22D3EE),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Copy",
                                fontSize = 11.sp,
                                color = Color(0xFF22D3EE),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Expandable Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    // Persistent Crash Section if applicable
                    if (category == LogCategory.CRASH && !persistentCrash.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2B1318),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF7F1D1D), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⚠️ Persistent Crash Report",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFFF87171)
                                    )
                                    if (onClearCrash != null) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF451A1A),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable { onClearCrash() }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Clear",
                                                    tint = Color(0xFFFCA5A5),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = "Clear",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFFFCA5A5),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .verticalScroll(rememberScrollState())
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = persistentCrash,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        color = Color(0xFFFCA5A5),
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Category Logcat Entries
                    if (logs.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .background(Color(0xFF070B11), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF1B2430), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = logs.joinToString("\n"),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                color = if (category == LogCategory.CRASH) Color(0xFFFCA5A5) else Color(0xFF9CDCFE),
                                lineHeight = 15.sp
                            )
                        }
                    } else if (category != LogCategory.CRASH || persistentCrash.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF070B11), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "No logs recorded for this category.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        }
    }
}

