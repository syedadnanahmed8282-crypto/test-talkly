package com.family.talkly.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

// TALKLY 2026 VISUAL PALETTE
private val TalklyChatBg = Color(0xFF080B10)
private val TalklySurface = Color(0xFF11161D)
private val TalklyCard = Color(0xFF18212B)
private val TalklyElevated = Color(0xFF202B36)
private val TalklyCyan = Color(0xFF22D3EE)
private val TalklyAqua = Color(0xFF0EA5A4)
private val TalklyMint = Color(0xFF5EEAD4)
private val TalklyTextPrimary = Color(0xFFF8FAFC)
private val TalklyTextSecondary = Color(0xFFA7B0BA)
private val TalklyError = Color(0xFFF43F5E)

/**
 * Wallpaper categories as requested by Talkly specification.
 */
enum class WallpaperCategory(val displayName: String) {
    COLORS("Colors"),
    GRADIENTS("Gradients"),
    NATURE("Nature"),
    ABSTRACT("Abstract"),
    DARK("Dark"),
    CUSTOM("Custom"),
    GALLERY("Gallery")
}

/**
 * Scalable wallpaper catalog item supporting colors, gradients,
 * future bundled JPG assets in res/drawable-nodpi, remote URLs, and device gallery.
 */
data class WallpaperItem(
    val id: String,
    val name: String,
    val category: WallpaperCategory,
    val colorHex: String? = null,
    val gradientColors: List<String>? = null,
    /**
     * Future bundled JPG drawable asset reference (e.g., R.drawable.wallpaper_nature_01).
     * Exact JPG production assets can be added directly to res/drawable-nodpi.
     */
    val drawableRes: Int? = null,
    /**
     * Exact bundled JPG drawable name in res/drawable-nodpi or res/drawable (e.g. "wallpaper_vibrant_oil").
     */
    val drawableResName: String? = null,
    /**
     * Remote or local file image URI.
     */
    val imageUrl: String? = null,
    val isGalleryOption: Boolean = false,
    val isDefault: Boolean = false
) {
    fun resolveValue(context: Context): String {
        return when {
            drawableRes != null -> "android.resource://${context.packageName}/$drawableRes"
            !drawableResName.isNullOrBlank() -> {
                val resId = context.resources.getIdentifier(drawableResName, "drawable", context.packageName)
                if (resId != 0) {
                    "android.resource://${context.packageName}/$resId"
                } else {
                    "android.resource://${context.packageName}/drawable/$drawableResName"
                }
            }
            !imageUrl.isNullOrBlank() -> imageUrl
            !gradientColors.isNullOrEmpty() -> "gradient:" + gradientColors.joinToString(",")
            !colorHex.isNullOrBlank() -> colorHex
            isDefault -> "#080B10"
            else -> "#080B10"
        }
    }
}

/**
 * Clean wallpaper catalog structured by category.
 * Ready for future exact JPG production assets to be added to res/drawable-nodpi.
 */
val WALLPAPER_CATALOG: List<WallpaperItem> = listOf(
    // 1. Colors (Bundled production wallpapers + Dark tones)
    WallpaperItem(
        id = "wp_vibrant_oil",
        name = "Vibrant Oil",
        category = WallpaperCategory.COLORS,
        drawableResName = "wallpaper_vibrant_oil"
    ),
    WallpaperItem(
        id = "wp_pastel_blooms",
        name = "Pastel Blooms",
        category = WallpaperCategory.COLORS,
        drawableResName = "wallpaper_pastel_blooms"
    ),
    WallpaperItem(
        id = "wp_pastel_clouds",
        name = "Pastel Clouds",
        category = WallpaperCategory.COLORS,
        drawableResName = "wallpaper_pastel_clouds"
    ),
    WallpaperItem(
        id = "wp_pink_plumes",
        name = "Pink Plumes",
        category = WallpaperCategory.COLORS,
        drawableResName = "wallpaper_pink_plumes"
    ),
    WallpaperItem(id = "col_default", name = "Talkly Dark", category = WallpaperCategory.COLORS, colorHex = "#080B10", isDefault = true),
    WallpaperItem(id = "col_obsidian", name = "Obsidian", category = WallpaperCategory.COLORS, colorHex = "#0D1117"),
    WallpaperItem(id = "col_slate", name = "Midnight Slate", category = WallpaperCategory.COLORS, colorHex = "#0F172A"),
    WallpaperItem(id = "col_navy", name = "Deep Navy", category = WallpaperCategory.COLORS, colorHex = "#0B132B"),
    WallpaperItem(id = "col_charcoal", name = "Charcoal", category = WallpaperCategory.COLORS, colorHex = "#18212B"),
    WallpaperItem(id = "col_emerald", name = "Deep Emerald", category = WallpaperCategory.COLORS, colorHex = "#062C24"),
    WallpaperItem(id = "col_sapphire", name = "Dark Sapphire", category = WallpaperCategory.COLORS, colorHex = "#0C1B33"),
    WallpaperItem(id = "col_plum", name = "Dark Plum", category = WallpaperCategory.COLORS, colorHex = "#1F0D2B"),

    // 2. Gradients (Bundled production gradient wallpapers + ambient gradients)
    WallpaperItem(
        id = "wp_aqua_sheen",
        name = "Aqua Sheen",
        category = WallpaperCategory.GRADIENTS,
        drawableResName = "wallpaper_aqua_sheen"
    ),
    WallpaperItem(
        id = "wp_ocean_gradient",
        name = "Ocean Gradient",
        category = WallpaperCategory.GRADIENTS,
        drawableResName = "wallpaper_ocean_gradient"
    ),
    WallpaperItem(
        id = "wp_prism_silk",
        name = "Prism Silk",
        category = WallpaperCategory.GRADIENTS,
        drawableResName = "wallpaper_prism_silk"
    ),
    WallpaperItem(
        id = "wp_royal_plum",
        name = "Royal Plum",
        category = WallpaperCategory.GRADIENTS,
        drawableResName = "wallpaper_royal_plum"
    ),
    WallpaperItem(id = "grad_cyan_night", name = "Cyan Night", category = WallpaperCategory.GRADIENTS, gradientColors = listOf("#080B10", "#0B2A38")),
    WallpaperItem(id = "grad_aqua_depth", name = "Aqua Depths", category = WallpaperCategory.GRADIENTS, gradientColors = listOf("#080B10", "#092E2E")),
    WallpaperItem(id = "grad_midnight_indigo", name = "Midnight Indigo", category = WallpaperCategory.GRADIENTS, gradientColors = listOf("#0A0E1A", "#151F38")),
    WallpaperItem(id = "grad_emerald_twilight", name = "Emerald Twilight", category = WallpaperCategory.GRADIENTS, gradientColors = listOf("#080B10", "#0A261D")),
    WallpaperItem(id = "grad_cyber_violet", name = "Cyber Violet", category = WallpaperCategory.GRADIENTS, gradientColors = listOf("#080B10", "#241033")),
    WallpaperItem(id = "grad_nordic_fog", name = "Nordic Slate", category = WallpaperCategory.GRADIENTS, gradientColors = listOf("#0F172A", "#1E293B")),

    // 3. Nature (Category ready for future exact JPG assets in res/drawable-nodpi)
    // Bundled production JPG files will be registered here directly when provided.

    // 4. Abstract (Category ready for future exact JPG assets in res/drawable-nodpi)
    // Bundled production JPG files will be registered here directly when provided.

    // 5. Dark
    WallpaperItem(id = "dark_amoled", name = "Pure AMOLED", category = WallpaperCategory.DARK, colorHex = "#000000"),
    WallpaperItem(id = "dark_pitch", name = "Pitch Dark", category = WallpaperCategory.DARK, colorHex = "#05070A"),
    WallpaperItem(id = "dark_carbon", name = "Carbon Black", category = WallpaperCategory.DARK, colorHex = "#121214"),
    WallpaperItem(id = "dark_eclipse", name = "Eclipse Night", category = WallpaperCategory.DARK, colorHex = "#090D16"),

    // 6. Custom
    WallpaperItem(id = "cust_default", name = "Default Talkly", category = WallpaperCategory.CUSTOM, colorHex = "#080B10", isDefault = true),

    // 7. Gallery
    WallpaperItem(id = "gallery_picker", name = "Choose from Gallery", category = WallpaperCategory.GALLERY, isGalleryOption = true)
)

@Composable
fun WallpaperSelectionDialog(
    currentValue: String,
    contactName: String,
    onDismiss: () -> Unit,
    onWallpaperSelected: (value: String, applyToAll: Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedValue by remember { mutableStateOf(currentValue) }
    var selectedCategory by remember { mutableStateOf(WallpaperCategory.COLORS) }
    var applyToAllChats by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedValue = it.toString()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = TalklySurface,
            border = BorderStroke(1.dp, TalklyElevated),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TalklyCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wallpaper,
                                contentDescription = null,
                                tint = TalklyCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Chat Wallpaper",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TalklyTextPrimary
                            )
                            Text(
                                text = "Customize chat background",
                                fontSize = 11.sp,
                                color = TalklyTextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TalklyTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Preview Card (Talkly 2026 chat simulation)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(125.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, TalklyElevated, RoundedCornerShape(16.dp))
                ) {
                    // Render current wallpaper selection in preview
                    when {
                        selectedValue.startsWith("http://") ||
                        selectedValue.startsWith("https://") ||
                        selectedValue.startsWith("content://") ||
                        selectedValue.startsWith("file://") ||
                        selectedValue.startsWith("android.resource://") -> {
                            AsyncImage(
                                model = selectedValue,
                                contentDescription = "Wallpaper Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.40f))
                            )
                        }
                        selectedValue.startsWith("gradient:") -> {
                            val hexList = selectedValue.removePrefix("gradient:").split(",")
                            val colors = hexList.mapNotNull {
                                try {
                                    Color(android.graphics.Color.parseColor(it.trim()))
                                } catch (e: Exception) {
                                    null
                                }
                            }.ifEmpty { listOf(TalklyChatBg, TalklyCard) }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.verticalGradient(colors))
                            )
                        }
                        selectedValue.startsWith("#") -> {
                            val col = try {
                                Color(android.graphics.Color.parseColor(selectedValue))
                            } catch (e: Exception) {
                                TalklyChatBg
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(col)
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(TalklyChatBg)
                            )
                        }
                    }

                    // Realistic Talkly Preview Message Bubbles
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Incoming message
                        Surface(
                            color = TalklyCard,
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 4.dp),
                            border = BorderStroke(0.5.dp, TalklyElevated),
                            modifier = Modifier.fillMaxWidth(0.82f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Hey! How does this wallpaper look?",
                                    fontSize = 11.sp,
                                    color = TalklyTextPrimary,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "10:42",
                                    fontSize = 9.sp,
                                    color = TalklyTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Outgoing message
                        Box(
                            modifier = Modifier
                                .align(Alignment.End)
                                .fillMaxWidth(0.82f)
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 4.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF0C3848), Color(0xFF114E5E))
                                    )
                                )
                                .border(
                                    0.5.dp,
                                    TalklyCyan.copy(alpha = 0.25f),
                                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 4.dp)
                                )
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Looks crisp with Talkly dark theme 👍",
                                    fontSize = 11.sp,
                                    color = TalklyTextPrimary,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "10:43 ✓✓",
                                    fontSize = 9.sp,
                                    color = TalklyMint
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Category Selector (Pill Tabs)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WallpaperCategory.values().forEach { category ->
                        val isCatSelected = category == selectedCategory
                        val targetBg = if (isCatSelected) TalklyCyan.copy(alpha = 0.16f) else TalklyCard
                        val targetBorder = if (isCatSelected) TalklyCyan else TalklyElevated
                        val targetText = if (isCatSelected) TalklyCyan else TalklyTextSecondary

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = targetBg,
                            border = BorderStroke(1.dp, targetBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedCategory = category }
                        ) {
                            Text(
                                text = category.displayName,
                                color = targetText,
                                fontSize = 12.sp,
                                fontWeight = if (isCatSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Wallpaper Content Area based on Category
                val categoryItems = remember(selectedCategory) {
                    WALLPAPER_CATALOG.filter { it.category == selectedCategory }
                }

                AnimatedContent(
                    targetState = selectedCategory,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                    },
                    label = "WallpaperCategoryContent"
                ) { category ->
                    when (category) {
                        WallpaperCategory.GALLERY -> {
                            // Dedicated Gallery Option
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(TalklyCard)
                                    .border(1.dp, TalklyElevated, RoundedCornerShape(16.dp))
                                    .clickable { galleryLauncher.launch("image/*") }
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(TalklyCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = "Gallery",
                                        tint = TalklyCyan,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Choose Photo from Gallery",
                                    color = TalklyTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (selectedValue.startsWith("content://") || selectedValue.startsWith("file://")) {
                                        "✓ Custom device photo selected"
                                    } else {
                                        "Select any image from your device storage"
                                    },
                                    color = if (selectedValue.startsWith("content://") || selectedValue.startsWith("file://")) TalklyMint else TalklyTextSecondary,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        WallpaperCategory.CUSTOM -> {
                            // Custom Options
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(TalklyCard)
                                    .border(1.dp, TalklyElevated, RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = TalklyCyan,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Talkly Default Theme",
                                    color = TalklyTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Reset back to the official Talkly dark ambiance",
                                    color = TalklyTextSecondary,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { selectedValue = "#080B10" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TalklyElevated,
                                        contentColor = TalklyCyan
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Apply Default Dark", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        WallpaperCategory.NATURE, WallpaperCategory.ABSTRACT -> {
                            if (categoryItems.isEmpty()) {
                                // Clean ready placeholder state for future exact bundled JPG production assets
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(TalklyCard)
                                        .border(1.dp, TalklyElevated, RoundedCornerShape(16.dp))
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (category == WallpaperCategory.NATURE) Icons.Default.Landscape else Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = TalklyCyan.copy(alpha = 0.5f),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${category.displayName} Collection",
                                        color = TalklyTextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Curated JPG wallpapers will be available here",
                                        color = TalklyTextSecondary,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                WallpaperGrid(
                                    items = categoryItems,
                                    selectedValue = selectedValue,
                                    context = context,
                                    onSelect = { selectedValue = it }
                                )
                            }
                        }
                        else -> {
                            // Colors, Gradients, Dark
                            WallpaperGrid(
                                items = categoryItems,
                                selectedValue = selectedValue,
                                context = context,
                                onSelect = { selectedValue = it }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Apply Scope Choice (Segmented selector)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isThisChatSelected = !applyToAllChats
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isThisChatSelected) TalklyCyan.copy(alpha = 0.12f) else TalklyCard,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isThisChatSelected) TalklyCyan else TalklyElevated
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { applyToAllChats = false }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .border(
                                        1.5.dp,
                                        if (isThisChatSelected) TalklyCyan else TalklyTextSecondary,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isThisChatSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(TalklyCyan, CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "This Chat",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isThisChatSelected) TalklyCyan else TalklyTextPrimary
                                )
                                Text(
                                    text = contactName,
                                    fontSize = 10.sp,
                                    color = TalklyTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    val isAllChatsSelected = applyToAllChats
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAllChatsSelected) TalklyCyan.copy(alpha = 0.12f) else TalklyCard,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isAllChatsSelected) TalklyCyan else TalklyElevated
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { applyToAllChats = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .border(
                                        1.5.dp,
                                        if (isAllChatsSelected) TalklyCyan else TalklyTextSecondary,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isAllChatsSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(TalklyCyan, CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "All Chats",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isAllChatsSelected) TalklyCyan else TalklyTextPrimary
                                )
                                Text(
                                    text = "Set as default",
                                    fontSize = 10.sp,
                                    color = TalklyTextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { selectedValue = "#080B10" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = TalklyTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reset Default",
                            color = TalklyTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            onWallpaperSelected(selectedValue, applyToAllChats)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TalklyCyan,
                            contentColor = Color(0xFF080B10)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Set Wallpaper",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF080B10)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperGrid(
    items: List<WallpaperItem>,
    selectedValue: String,
    context: Context,
    onSelect: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        items(items, key = { it.id }) { item ->
            val isSelected = when {
                item.drawableRes != null -> selectedValue == "android.resource://${context.packageName}/${item.drawableRes}"
                !item.drawableResName.isNullOrBlank() -> {
                    val resId = context.resources.getIdentifier(item.drawableResName, "drawable", context.packageName)
                    selectedValue == "android.resource://${context.packageName}/drawable/${item.drawableResName}" ||
                    (resId != 0 && selectedValue == "android.resource://${context.packageName}/$resId")
                }
                !item.imageUrl.isNullOrBlank() -> selectedValue == item.imageUrl
                !item.gradientColors.isNullOrEmpty() -> selectedValue == ("gradient:" + item.gradientColors.joinToString(","))
                !item.colorHex.isNullOrBlank() -> selectedValue.equals(item.colorHex, ignoreCase = true)
                item.isDefault -> selectedValue == "default" || selectedValue.equals("#080B10", ignoreCase = true)
                else -> false
            }

            val borderColor by animateColorAsState(
                targetValue = if (isSelected) TalklyCyan else TalklyElevated,
                animationSpec = tween(200),
                label = "WallpaperItemBorder"
            )

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        onSelect(item.resolveValue(context))
                    },
                contentAlignment = Alignment.Center
            ) {
                // Background visual
                when {
                    item.drawableRes != null -> {
                        AsyncImage(
                            model = item.drawableRes,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    !item.drawableResName.isNullOrBlank() -> {
                        val resId = context.resources.getIdentifier(item.drawableResName, "drawable", context.packageName)
                        val model = if (resId != 0) resId else "android.resource://${context.packageName}/drawable/${item.drawableResName}"
                        AsyncImage(
                            model = model,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    !item.imageUrl.isNullOrBlank() -> {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    !item.gradientColors.isNullOrEmpty() -> {
                        val colors = item.gradientColors.mapNotNull {
                            try {
                                Color(android.graphics.Color.parseColor(it.trim()))
                            } catch (e: Exception) {
                                null
                            }
                        }.ifEmpty { listOf(TalklyChatBg, TalklyCard) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(colors))
                        )
                    }
                    !item.colorHex.isNullOrBlank() -> {
                        val col = try {
                            Color(android.graphics.Color.parseColor(item.colorHex))
                        } catch (e: Exception) {
                            TalklyChatBg
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(col)
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(TalklyChatBg)
                        )
                    }
                }

                // Name label pill at bottom
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(vertical = 2.dp, horizontal = 2.dp)
                ) {
                    Text(
                        text = item.name,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = TalklyTextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Cyan selection indicator badge
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(18.dp)
                            .background(TalklyCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color(0xFF080B10),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

