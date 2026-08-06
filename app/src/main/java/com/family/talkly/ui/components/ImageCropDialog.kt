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
    isCoverCrop: Boolean = false,
    onDismiss: () -> Unit,
    onImageCropped: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalContext.current.resources.displayMetrics.density

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }

    val cropBoxWidthDp = if (isCoverCrop) 330.dp else 270.dp
    val cropBoxHeightDp = if (isCoverCrop) 185.dp else 270.dp

    val boxWidthPx = cropBoxWidthDp.value * density
    val boxHeightPx = cropBoxHeightDp.value * density

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
                .fillMaxWidth(0.96f)
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF111B21)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            text = if (isCoverCrop) "Crop Cover Photo" else "Crop Profile Picture",
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
                    text = if (isCoverCrop) "Pinch to zoom, drag to align inside cover box" else "Pinch to zoom, drag to align photo inside circle",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                // Crop Viewport Box
                Box(
                    modifier = Modifier
                        .size(width = cropBoxWidthDp, height = cropBoxHeightDp)
                        .clipToBounds()
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
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

                    // Overlay Mask
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        if (isCoverCrop) {
                            val cornerRadius = 14.dp.toPx()
                            val strokeWidthPx = 3.dp.toPx()
                            val halfStroke = strokeWidthPx / 2f

                            val path = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                                addRoundRect(
                                    androidx.compose.ui.geometry.RoundRect(
                                        rect = Rect(halfStroke, halfStroke, canvasWidth - halfStroke, canvasHeight - halfStroke),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
                                    )
                                )
                            }
                            drawPath(path = path, color = Color.Black.copy(alpha = 0.65f))

                            drawRoundRect(
                                color = WhatsappGreen,
                                topLeft = Offset(halfStroke, halfStroke),
                                size = Size(canvasWidth - strokeWidthPx, canvasHeight - strokeWidthPx),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthPx)
                            )

                            // Grid lines inside crop box
                            val gridColor = Color.White.copy(alpha = 0.25f)
                            val strokeW = 1.dp.toPx()
                            val hThird = canvasHeight / 3f
                            val wThird = canvasWidth / 3f

                            drawLine(gridColor, Offset(0f, hThird), Offset(canvasWidth, hThird), strokeWidth = strokeW)
                            drawLine(gridColor, Offset(0f, hThird * 2f), Offset(canvasWidth, hThird * 2f), strokeWidth = strokeW)
                            drawLine(gridColor, Offset(wThird, 0f), Offset(wThird, canvasHeight), strokeWidth = strokeW)
                            drawLine(gridColor, Offset(wThird * 2f, 0f), Offset(wThird * 2f, canvasHeight), strokeWidth = strokeW)
                        } else {
                            val radius = canvasWidth / 2f - 6.dp.toPx()

                            val path = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                                addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
                            }
                            drawPath(path = path, color = Color.Black.copy(alpha = 0.65f))

                            drawCircle(
                                color = WhatsappGreen,
                                radius = radius,
                                center = center,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                            )

                            val thirdR = radius * 0.33f
                            val gridColor = Color.White.copy(alpha = 0.25f)
                            val strokeW = 1.dp.toPx()

                            drawLine(gridColor, Offset(center.x - radius * 0.9f, center.y - thirdR), Offset(center.x + radius * 0.9f, center.y - thirdR), strokeWidth = strokeW)
                            drawLine(gridColor, Offset(center.x - radius * 0.9f, center.y + thirdR), Offset(center.x + radius * 0.9f, center.y + thirdR), strokeWidth = strokeW)
                            drawLine(gridColor, Offset(center.x - thirdR, center.y - radius * 0.9f), Offset(center.x - thirdR, center.y + radius * 0.9f), strokeWidth = strokeW)
                            drawLine(gridColor, Offset(center.x + thirdR, center.y - radius * 0.9f), Offset(center.x + thirdR, center.y + radius * 0.9f), strokeWidth = strokeW)
                        }
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
                            valueRange = 1f..5f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = WhatsappGreen,
                                activeTrackColor = WhatsappGreen,
                                inactiveTrackColor = Color.DarkGray
                            )
                        )

                        IconButton(
                            onClick = { scale = (scale + 0.2f).coerceAtMost(5f) }
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
                                    boxWidthPx = boxWidthPx,
                                    boxHeightPx = boxHeightPx,
                                    isCoverCrop = isCoverCrop
                                )
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    if (croppedUri != null) {
                                        onImageCropped(croppedUri)
                                    } else {
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
    boxWidthPx: Float,
    boxHeightPx: Float,
    isCoverCrop: Boolean
): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(imageUri) ?: return null
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        if (originalBitmap == null) return null

        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
        } else {
            originalBitmap
        }

        val outputWidthPx = if (isCoverCrop) 1200 else 800
        val outputHeightPx = if (isCoverCrop) 675 else 800

        val outputBitmap = Bitmap.createBitmap(outputWidthPx, outputHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        val srcW = rotatedBitmap.width.toFloat()
        val srcH = rotatedBitmap.height.toFloat()

        // ContentScale.Fit scale factor matching AsyncImage inside the viewport Box
        val fitScale = minOf(boxWidthPx / srcW, boxHeightPx / srcH)
        val renderedW = srcW * fitScale
        val renderedH = srcH * fitScale

        val finalW = renderedW * scale
        val finalH = renderedH * scale

        val imageLeftInBox = (boxWidthPx - finalW) / 2f + offsetX
        val imageTopInBox = (boxHeightPx - finalH) / 2f + offsetY

        val scaleRatioX = outputWidthPx.toFloat() / boxWidthPx
        val scaleRatioY = outputHeightPx.toFloat() / boxHeightPx

        val outLeft = imageLeftInBox * scaleRatioX
        val outTop = imageTopInBox * scaleRatioY
        val outRight = outLeft + (finalW * scaleRatioX)
        val outBottom = outTop + (finalH * scaleRatioY)

        val destRect = RectF(outLeft, outTop, outRight, outBottom)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(rotatedBitmap, null, destRect, paint)

        val prefix = if (isCoverCrop) "cropped_cover_" else "cropped_avatar_"
        val avatarFile = File(context.cacheDir, "${prefix}${System.currentTimeMillis()}.jpg")
        FileOutputStream(avatarFile).use { out ->
            outputBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        Uri.fromFile(avatarFile)
    } catch (e: Exception) {
        Log.e("ImageCropDialog", "Failed cropping image: ${e.localizedMessage}", e)
        null
    }
}
