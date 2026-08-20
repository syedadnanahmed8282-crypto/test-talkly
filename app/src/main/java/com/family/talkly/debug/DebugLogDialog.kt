package com.family.talkly.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    var logText by remember { mutableStateOf("লোড হচ্ছে...") }
    var crashText by remember { mutableStateOf<String?>(null) }
    var lastSavedInfo by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        crashText = CrashHandler.getLastCrash(context)
        logText = LogcatHelper.captureRecentLogs()
    }

    fun fullLogText(): String {
        return (if (crashText != null) "=== LAST CRASH ===\n$crashText\n\n" else "") +
            "=== LOGCAT ===\n$logText"
    }

    fun saveToDownloads(): String? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "talkly_debug_$timestamp.txt"
        val content = fullLogText()
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

    LaunchedEffect(Unit) { refresh() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0D1117)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Debug Log", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { refresh(); lastSavedInfo = null }) { Text("Refresh") }
                    TextButton(onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("talkly_debug_log", fullLogText()))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Copy failed - use Save instead", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Copy") }
                    TextButton(onClick = {
                        val savedPath = saveToDownloads()
                        if (savedPath != null) {
                            lastSavedInfo = "সেভ হয়েছে: $savedPath"
                            Toast.makeText(context, "Saved: $savedPath", Toast.LENGTH_LONG).show()
                        } else {
                            lastSavedInfo = "সেভ ব্যর্থ হয়েছে"
                            Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Save") }
                }
                if (lastSavedInfo != null) {
                    Text(
                        lastSavedInfo ?: "",
                        color = Color(0xFF4ADE80),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (crashText != null) {
                        Text(
                            "সর্বশেষ CRASH:",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF87171)
                        )
                        Text(
                            crashText ?: "",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFFCA5A5)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            CrashHandler.clearLastCrash(context)
                            crashText = null
                        }) { Text("Clear crash log") }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Text("সাম্প্রতিক লগ (error/warning অগ্রাধিকার):", fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        logText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF9CDCFE)
                    )
                }
            }
        }
    }
}
