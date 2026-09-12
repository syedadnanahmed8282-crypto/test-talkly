package com.family.talkly.ui.components

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.family.talkly.data.models.MessageType
import com.family.talkly.util.PhoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "TalklyGalleryPicker"

/**
 * Local media item model constructed from Android MediaStore.
 */
data class LocalMediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val dateModified: Long,
    val durationMs: Long = 0L,
    val displayName: String = "",
    val size: Long = 0L
)

enum class GalleryTab(val title: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos")
}

/**
 * Global lightweight thumbnail memory cache for video thumbnails
 */
private object VideoThumbnailCache {
    private val memoryCache = object : LruCache<Long, Bitmap>(25 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }

    fun get(id: Long): Bitmap? = memoryCache.get(id)
    fun put(id: Long, bitmap: Bitmap) {
        memoryCache.put(id, bitmap)
    }
}

/**
 * Custom Talkly Gallery Picker.
 *
 * Reads ONLY local device media through Android MediaStore (Photos & Videos).
 * Does not show cloud, chat, or server media.
 * Compatible with Android 8.1 (API 27) up to Android 15+.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TalklyGalleryPicker(
    initialTab: GalleryTab = GalleryTab.ALL,
    onDismiss: () -> Unit,
    onMediaSelected: (uris: List<String>, type: MessageType) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Required permissions based on Android version
    val requiredPermissions = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            }
            else -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    fun checkPermissionGranted(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    var hasPermission by remember { mutableStateOf(checkPermissionGranted()) }
    var permissionDeniedCount by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val granted = checkPermissionGranted()
        hasPermission = granted
        if (!granted) {
            permissionDeniedCount++
        }
    }

    // Tabs & Items
    var currentTab by remember { mutableStateOf(initialTab) }
    var allMediaItems by remember { mutableStateOf<List<LocalMediaItem>>(emptyList()) }
    var isLoadingMedia by remember { mutableStateOf(true) }

    // Multi-selection state
    val selectedItems = remember { mutableStateListOf<LocalMediaItem>() }
    var batchWarningMessage by remember { mutableStateOf<String?>(null) }

    // Grid states to retain scroll position per tab
    val allGridState = rememberLazyGridState()
    val photosGridState = rememberLazyGridState()
    val videosGridState = rememberLazyGridState()

    // Request permissions on entry if not granted
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    // Query device MediaStore
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoadingMedia = true
            withContext(Dispatchers.IO) {
                val mediaList = queryDeviceMediaStore(context)
                withContext(Dispatchers.Main) {
                    allMediaItems = mediaList
                    isLoadingMedia = false
                }
            }
        } else {
            isLoadingMedia = false
        }
    }

    // Auto-dismiss warning message after 2.5s
    LaunchedEffect(batchWarningMessage) {
        if (batchWarningMessage != null) {
            delay(2500)
            batchWarningMessage = null
        }
    }

    // Filter items based on active tab
    val displayedItems = remember(allMediaItems, currentTab) {
        when (currentTab) {
            GalleryTab.ALL -> allMediaItems
            GalleryTab.PHOTOS -> allMediaItems.filter { !it.isVideo }
            GalleryTab.VIDEOS -> allMediaItems.filter { it.isVideo }
        }
    }

    val photoCount = remember(allMediaItems) { allMediaItems.count { !it.isVideo } }
    val videoCount = remember(allMediaItems) { allMediaItems.count { it.isVideo } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF080B10))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Compact Dark Glass Top Header
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xF211161D),
                    border = BorderStroke(0.8.dp, Color(0x2222D3EE))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0x1AFFFFFF), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color(0xFFF8FAFC),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Gallery",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF8FAFC)
                                )
                                Text(
                                    text = "Select media to send",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color(0xFFA7B0BA)
                                )
                            }

                            if (selectedItems.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0x2222D3EE),
                                    border = BorderStroke(1.dp, Color(0x5522D3EE))
                                ) {
                                    Text(
                                        text = "${selectedItems.size} selected",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF22D3EE),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Filter Tabs: All, Photos, Videos
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GalleryTabPill(
                                title = "All",
                                count = allMediaItems.size,
                                isSelected = currentTab == GalleryTab.ALL,
                                modifier = Modifier.weight(1f),
                                onClick = { currentTab = GalleryTab.ALL }
                            )
                            GalleryTabPill(
                                title = "Photos",
                                count = photoCount,
                                isSelected = currentTab == GalleryTab.PHOTOS,
                                modifier = Modifier.weight(1f),
                                onClick = { currentTab = GalleryTab.PHOTOS }
                            )
                            GalleryTabPill(
                                title = "Videos",
                                count = videoCount,
                                isSelected = currentTab == GalleryTab.VIDEOS,
                                modifier = Modifier.weight(1f),
                                onClick = { currentTab = GalleryTab.VIDEOS }
                            )
                        }
                    }
                }

                // Batch Selection Warning Toast (subtle & non-blocking)
                AnimatedVisibility(
                    visible = batchWarningMessage != null,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .background(Color(0xE618212B), RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, Color(0x6622D3EE)), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = batchWarningMessage ?: "",
                            color = Color(0xFF5EEAD4),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Main Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        !hasPermission -> {
                            // Permission Denied View
                            GalleryPermissionState(
                                onGrantClicked = {
                                    if (permissionDeniedCount > 1) {
                                        // User repeatedly denied, offer App Settings
                                        val intent = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        )
                                        context.startActivity(intent)
                                    } else {
                                        permissionLauncher.launch(requiredPermissions)
                                    }
                                },
                                isSettingsFallback = permissionDeniedCount > 1
                            )
                        }

                        isLoadingMedia -> {
                            // Loading State
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF22D3EE),
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Loading gallery...",
                                        fontSize = 13.sp,
                                        color = Color(0xFFA7B0BA)
                                    )
                                }
                            }
                        }

                        displayedItems.isEmpty() -> {
                            // Empty Gallery State
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .background(Color(0x1A22D3EE), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PermMedia,
                                            contentDescription = null,
                                            tint = Color(0xFF22D3EE),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "No media found",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFF8FAFC)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = when (currentTab) {
                                            GalleryTab.ALL -> "No photos or videos found in your device gallery."
                                            GalleryTab.PHOTOS -> "No photos found on your device."
                                            GalleryTab.VIDEOS -> "No videos found on your device."
                                        },
                                        fontSize = 13.sp,
                                        color = Color(0xFFA7B0BA),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        else -> {
                            // Active Grid state per tab to preserve scroll position
                            val currentGridState = when (currentTab) {
                                GalleryTab.ALL -> allGridState
                                GalleryTab.PHOTOS -> photosGridState
                                GalleryTab.VIDEOS -> videosGridState
                            }

                            // 4-Column Media Grid
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                state = currentGridState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 2.dp,
                                    end = 2.dp,
                                    top = 2.dp,
                                    bottom = if (selectedItems.isNotEmpty()) 120.dp else 12.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                                verticalArrangement = Arrangement.spacedBy(2.5.dp)
                            ) {
                                items(
                                    items = displayedItems,
                                    key = { it.id }
                                ) { item ->
                                    val selectedIndex = selectedItems.indexOfFirst { it.id == item.id }
                                    val isSelected = selectedIndex >= 0

                                    // Selection compatibility check
                                    val hasSelection = selectedItems.isNotEmpty()
                                    val firstIsVideo = if (hasSelection) selectedItems.first().isVideo else false
                                    val isIncompatible = hasSelection && (firstIsVideo != item.isVideo)

                                    GalleryGridThumbnail(
                                        item = item,
                                        isSelected = isSelected,
                                        selectionIndex = if (isSelected) selectedIndex + 1 else 0,
                                        isIncompatible = isIncompatible,
                                        onClick = {
                                            if (isSelected) {
                                                selectedItems.removeAll { it.id == item.id }
                                            } else {
                                                if (hasSelection) {
                                                    if (firstIsVideo != item.isVideo) {
                                                        batchWarningMessage = if (firstIsVideo) {
                                                            "Select videos only for this batch."
                                                        } else {
                                                            "Select photos only for this batch."
                                                        }
                                                    } else {
                                                        selectedItems.add(item)
                                                    }
                                                } else {
                                                    selectedItems.add(item)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Bottom Selection Tray (Only visible when at least 1 item is selected)
            AnimatedVisibility(
                visible = selectedItems.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xF711161D),
                    border = BorderStroke(1.dp, Color(0x3322D3EE)),
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        // Selected items horizontal preview strip
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = selectedItems,
                                key = { it.id }
                            ) { selectedItem ->
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF18212B))
                                        .border(BorderStroke(1.dp, Color(0xFF22D3EE)), RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedItems.removeAll { it.id == selectedItem.id }
                                        }
                                ) {
                                    if (selectedItem.isVideo) {
                                        VideoThumbnailView(item = selectedItem, modifier = Modifier.fillMaxSize())
                                    } else {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(selectedItem.uri)
                                                .size(150)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    // Remove badge overlay
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(14.dp)
                                            .background(Color.Black.copy(alpha = 0.7f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Send and Summary Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isVideoBatch = selectedItems.firstOrNull()?.isVideo == true
                            val count = selectedItems.size
                            val typeLabel = if (isVideoBatch) {
                                if (count == 1) "video" else "videos"
                            } else {
                                if (count == 1) "photo" else "photos"
                            }

                            Column {
                                Text(
                                    text = "$count $typeLabel selected",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF8FAFC)
                                )
                                Text(
                                    text = "Ready to send",
                                    fontSize = 11.sp,
                                    color = Color(0xFF5EEAD4)
                                )
                            }

                            Button(
                                onClick = {
                                    if (selectedItems.isNotEmpty()) {
                                        val uris = selectedItems.map { it.uri.toString() }
                                        val messageType = if (isVideoBatch) MessageType.VIDEO else MessageType.IMAGE
                                        onMediaSelected(uris, messageType)
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF22D3EE),
                                    contentColor = Color(0xFF080B10)
                                ),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Send ($count)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab pill component for switching between All, Photos, and Videos
 */
@Composable
private fun GalleryTabPill(
    title: String,
    count: Int,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(17.dp),
        color = if (isSelected) Color(0x2E22D3EE) else Color(0x1AFFFFFF),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF22D3EE) else Color(0x1A22D3EE)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color(0xFF22D3EE) else Color(0xFFA7B0BA)
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "($count)",
                    fontSize = 10.sp,
                    color = if (isSelected) Color(0xFF5EEAD4) else Color(0x88A7B0BA)
                )
            }
        }
    }
}

/**
 * Individual media thumbnail in the 4-column grid.
 */
@Composable
private fun GalleryGridThumbnail(
    item: LocalMediaItem,
    isSelected: Boolean,
    selectionIndex: Int,
    isIncompatible: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF141C24))
            .then(
                if (isSelected) Modifier.border(2.dp, Color(0xFF22D3EE), RoundedCornerShape(3.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        // Thumbnail content
        if (item.isVideo) {
            VideoThumbnailView(
                item = item,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.uri)
                    .size(240)
                    .crossfade(true)
                    .build(),
                contentDescription = item.displayName.ifBlank { "Photo" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Dim overlay if incompatible with current batch
        if (isIncompatible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
            )
        }

        // Selection overlay tint
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x2E22D3EE))
            )
        }

        // Video Badge: Play icon & Duration
        if (item.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = formatVideoDuration(item.durationMs),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Selection circle badge (Top-Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .then(
                    if (isSelected) {
                        Modifier
                            .background(Color(0xFF22D3EE), CircleShape)
                            .border(BorderStroke(1.2.dp, Color(0xFF080B10)), CircleShape)
                    } else if (!isIncompatible) {
                        Modifier
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                            .border(BorderStroke(1.2.dp, Color.White.copy(alpha = 0.7f)), CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Text(
                    text = selectionIndex.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF080B10)
                )
            }
        }
    }
}

/**
 * Video Thumbnail View that loads and caches video frames off-thread.
 * Compatible with Android 8.1 (API 27) through Android 15+.
 */
@Composable
private fun VideoThumbnailView(
    item: LocalMediaItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var thumbnailBitmap by remember(item.id) { mutableStateOf(VideoThumbnailCache.get(item.id)) }

    LaunchedEffect(item.id) {
        if (thumbnailBitmap == null) {
            withContext(Dispatchers.IO) {
                val bitmap = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(item.uri, Size(200, 200), null)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Video.Thumbnails.getThumbnail(
                            context.contentResolver,
                            item.id,
                            MediaStore.Video.Thumbnails.MINI_KIND,
                            null
                        ) ?: PhoneUtils.getVideoThumbnail(context, item.uri.toString())
                    }
                } catch (e: Throwable) {
                    PhoneUtils.getVideoThumbnail(context, item.uri.toString())
                }

                if (bitmap != null) {
                    VideoThumbnailCache.put(item.id, bitmap)
                    withContext(Dispatchers.Main) {
                        thumbnailBitmap = bitmap
                    }
                }
            }
        }
    }

    if (thumbnailBitmap != null) {
        Image(
            bitmap = thumbnailBitmap!!.asImageBitmap(),
            contentDescription = item.displayName.ifBlank { "Video" },
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color(0xFF141C24)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                tint = Color(0x66A7B0BA),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Clean permission request state with explanation & grant button.
 */
@Composable
private fun GalleryPermissionState(
    onGrantClicked: () -> Unit,
    isSettingsFallback: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF16202C),
            border = BorderStroke(1.dp, Color(0x3322D3EE)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color(0x1F22D3EE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PermMedia,
                        contentDescription = null,
                        tint = Color(0xFF22D3EE),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Gallery Access Required",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Talkly needs storage permission to show your device's photos and videos so you can select and share them in your chat.",
                    fontSize = 12.sp,
                    color = Color(0xFFA7B0BA),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onGrantClicked,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF22D3EE),
                        contentColor = Color(0xFF080B10)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isSettingsFallback) "Open App Settings" else "Allow Gallery Access",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Format video duration (e.g. 0:45, 2:15, 1:04:20)
 */
private fun formatVideoDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remMin = minutes % 60
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, remMin, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

/**
 * Queries Android MediaStore for both local photos and local videos.
 * Runs on IO dispatcher. Does NOT fetch remote or chat data.
 */
private suspend fun queryDeviceMediaStore(context: Context): List<LocalMediaItem> = withContext(Dispatchers.IO) {
    val items = mutableListOf<LocalMediaItem>()
    val contentResolver = context.contentResolver

    // 1. Query Images
    try {
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE
        )
        val imageSortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection,
            null,
            null,
            imageSortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
            val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                if (idCol != -1) {
                    val id = cursor.getLong(idCol)
                    val dateModified = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    items.add(
                        LocalMediaItem(
                            id = id,
                            uri = uri,
                            isVideo = false,
                            dateModified = dateModified,
                            durationMs = 0L,
                            displayName = name,
                            size = size
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error querying device images: ${e.localizedMessage}")
    }

    // 2. Query Videos
    try {
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )
        val videoSortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            null,
            null,
            videoSortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
            val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
            val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)

            while (cursor.moveToNext()) {
                if (idCol != -1) {
                    val id = cursor.getLong(idCol)
                    val dateModified = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""
                    val duration = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    items.add(
                        LocalMediaItem(
                            id = id,
                            uri = uri,
                            isVideo = true,
                            dateModified = dateModified,
                            durationMs = duration,
                            displayName = name,
                            size = size
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error querying device videos: ${e.localizedMessage}")
    }

    // Sort combined list newest first
    items.sortByDescending { it.dateModified }
    items
}
