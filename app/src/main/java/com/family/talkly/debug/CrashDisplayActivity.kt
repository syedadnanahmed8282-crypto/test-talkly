package com.family.talkly.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.talkly.MainActivity

class CrashDisplayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CRASH_INFO = "extra_crash_info"

        fun start(context: Context, crashInfo: String) {
            val intent = Intent(context, CrashDisplayActivity::class.java).apply {
                putExtra(EXTRA_CRASH_INFO, crashInfo)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashInfo = intent.getStringExtra(EXTRA_CRASH_INFO) 
            ?: CrashHandler.getLastCrash(this) 
            ?: "কোনো ক্র্যাশ লগ খুঁজে পাওয়া যায়নি।"

        setContent {
            CrashDisplayScreen(
                crashInfo = crashInfo,
                onCopy = {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Talkly Crash Log", crashInfo)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "ক্র্যাশ রিপোর্ট কপি করা হয়েছে!", Toast.LENGTH_LONG).show()
                },
                onRestartApp = {
                    val restartIntent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(restartIntent)
                    finish()
                }
            )
        }
    }
}

@Composable
fun CrashDisplayScreen(
    crashInfo: String,
    onCopy: () -> Unit,
    onRestartApp: () -> Unit
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Talkly ক্র্যাশ করেছে (Crash Report)",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "নিচের সম্পূর্ণ ত্রুটির বিবরণ (Stack Trace) দেখুন",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("কপি করুন", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onRestartApp,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("রিস্টার্ট দিন", fontSize = 13.sp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Crash Details Console Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF020617), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScroll)
                        .horizontalScroll(horizontalScroll)
                ) {
                    Text(
                        text = crashInfo,
                        color = Color(0xFFF87171),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}
