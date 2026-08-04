package com.family.talkly.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.family.talkly.ui.theme.WhatsappGreen
import com.family.talkly.ui.theme.WhatsappTeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun ImageCropDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onImageCropped: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }

    val cropBoxSizeDp = 260.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isProcessing,
            dismissOnClickOutside = !isProcessing
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF111B21)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = null,
                            tint = WhatsappGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Crop Profile Picture",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.LightGray
                        )
                    }
                }

                Text(
                    text = "Pinch to zoom, drag to align photo inside circle",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Crop Viewport Box
                Box(
                    modifier = Modifier
                        .size(cropBoxSizeDp)
                        .clipToBounds()
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Image layer with scale, translation, rotation
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Crop Target Image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY,
                                rotationZ = rotationDegrees.toFloat()
                            )
                    )

                    // Overlay Mask with Circular Crop Frame
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val radius = canvasWidth / 2f - 8.dp.toPx()

                        // Dark semi-transparent backdrop outside circle
                        val path = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                            addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
                        }
                        drawPath(path = path, color = Color.Black.copy(alpha = 0.65f))

                        // Green boundary ring around crop circle
                        drawCircle(
                            color = WhatsappGreen,
                            radius = radius,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                        )

                        // Subtle Grid lines inside crop aperture
                        val thirdR = radius * 0.33f
                        val gridColor = Color.White.copy(alpha = 0.25f)
                        val strokeW = 1.dp.toPx()

                        // Horizontal lines
                        drawLine(gridColor, Offset(center.x - radius * 0.9f, center.y - thirdR), Offset(center.x + radius * 0.9f, center.y - thirdR), strokeWidth = strokeW)
                        drawLine(gridColor, Offset(center.x - radius * 0.9f, center.y + thirdR), Offset(center.x + radius * 0.9f, center.y + thirdR), strokeWidth = strokeW)
                        // Vertical lines
                        drawLine(gridColor, Offset(center.x - thirdR, center.y - radius * 0.9f), Offset(center.x - thirdR, center.y + radius * 0.9f), strokeWidth = strokeW)
                        drawLine(gridColor, Offset(center.x + thirdR, center.y - radius * 0.9f), Offset(center.x + thirdR, center.y + radius * 0.9f), strokeWidth = strokeW)
                    }
                }

                // Zoom & Control Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { scale = (scale - 0.2f).coerceAtLeast(1f) }
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White)
                        }

                        Slider(
                            value = scale,
                            onValueChange = { scale = it },
                            valueRange = 1f..4f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = WhatsappGreen,
                                activeTrackColor = WhatsappGreen,
                                inactiveTrackColor = Color.DarkGray
                            )
                        )

                        IconButton(
                            onClick = { scale = (scale + 0.2f).coerceAtMost(4f) }
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                rotationDegrees = (rotationDegrees + 90) % 360
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(imageVector = Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rotate ${rotationDegrees}°", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                rotationDegrees = 0
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset", fontSize = 12.sp)
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isProcessing
                    ) {
                        Text("Cancel", color = Color.Gray, fontSize = 15.sp)
                    }

                    Button(
                        onClick = {
                            isProcessing = true
                            scope.launch(Dispatchers.IO) {
                                val croppedUri = cropAndSaveImage(
                                    context = context,
                                    imageUri = imageUri,
                                    scale = scale,
                                    offsetX = offsetX,
                                    offsetY = offsetY,
                                    rotationDegrees = rotationDegrees,
                                    viewportSizePx = 600
                                )
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    if (croppedUri != null) {
                                        onImageCropped(croppedUri)
                                    } else {
                                        // Fallback to original image uri if crop calculation failed
                                        onImageCropped(imageUri)
                                    }
                                }
                            }
                        },
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsappGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isProcessing) "Cropping..." else "Crop & Save Photo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

private fun cropAndSaveImage(
    context: android.content.Context,
    imageUri: Uri,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    rotationDegrees: Int,
    viewportSizePx: Int = 600
): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(imageUri) ?: return null
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        if (originalBitmap == null) return null

        // Apply rotation if needed
        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
        } else {
            originalBitmap
        }

        // Target output size: square bitmap of viewportSizePx x viewportSizePx
        val outputBitmap = Bitmap.createBitmap(viewportSizePx, viewportSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        val srcW = rotatedBitmap.width.toFloat()
        val srcH = rotatedBitmap.height.toFloat()

        // Base fit scale to fit image in square viewport
        val baseScale = maxOf(viewportSizePx / srcW, viewportSizePx / srcH)
        val totalScale = baseScale * scale

        val scaledW = srcW * totalScale
        val scaledH = srcH * totalScale

        val normOffsetX = (offsetX / 260f) * viewportSizePx
        val normOffsetY = (offsetY / 260f) * viewportSizePx

        val left = (viewportSizePx - scaledW) / 2f + normOffsetX
        val top = (viewportSizePx - scaledH) / 2f + normOffsetY

        val destRect = RectF(left, top, left + scaledW, top + scaledH)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(rotatedBitmap, null, destRect, paint)

        // Save cropped output bitmap to local cache file
        val avatarFile = File(context.cacheDir, "cropped_avatar_${System.currentTimeMillis()}.jpg")
        FileOutputStream(avatarFile).use { out ->
            outputBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        Uri.fromFile(avatarFile)
    } catch (e: Exception) {
        Log.e("ImageCropDialog", "Failed cropping image: ${e.localizedMessage}", e)
        null
    }
}
