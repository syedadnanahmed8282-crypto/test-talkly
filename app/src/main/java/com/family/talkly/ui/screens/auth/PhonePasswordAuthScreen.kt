package com.family.talkly.ui.screens.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// =========================================================================
// TALKLY THEME PALETTE (DARK NAVY / BLACK + CYAN / BLUE VISUAL IDENTITY)
// Matches Talkly's existing visual identity throughout the application
// =========================================================================
private val AuthDarkCanvas = Color(0xFF080B10)        // Deep atmospheric dark navy / near-black
private val AuthCardSurface = Color(0xFF141C26)       // Dark navy base surface
private val AuthCardInner = Color(0xFF0E141D)         // Deep recessed inner card
private val AuthFieldBg = Color(0xFF090D13)           // Recessed input bed
private val AuthFieldFocusedBg = Color(0xFF101720)    // Elevated active input bed

private val ElectricCyan = Color(0xFF22D3EE)         // Talkly Primary Brand Accent (Bright Cyan)
private val DeepAqua = Color(0xFF0EA5A4)             // Talkly Secondary Accent (Deep Aqua/Blue)
private val TalklyBlue = Color(0xFF0284C7)           // Talkly Blue Accent

private val AuthTextPrimary = Color(0xFFF8FAFC)       // High-contrast clean white
private val AuthTextSecondary = Color(0xFFA7B0BA)     // Muted blue-gray
private val AuthTextMuted = Color(0xFF64748B)         // Input placeholder & subtle icons
private val AuthBorderSubtle = Color(0xFF1E293B)      // Deep card contour / subtle border
private val AuthBorderElevated = Color(0xFF24303E)    // Lifted border

private val AuthErrorRed = Color(0xFFF43F5E)
private val AuthErrorBg = Color(0x22F43F5E)
private val AuthSuccessGreen = Color(0xFF22C55E)

data class CountryCode(val country: String, val code: String, val flag: String)

val COUNTRY_CODES = listOf(
    CountryCode("Bangladesh", "+880", "🇧🇩"),
    CountryCode("United States", "+1", "🇺🇸"),
    CountryCode("United Kingdom", "+44", "🇬🇧"),
    CountryCode("India", "+91", "🇮🇳"),
    CountryCode("Pakistan", "+92", "🇵🇰"),
    CountryCode("Saudi Arabia", "+966", "🇸🇦"),
    CountryCode("UAE", "+971", "🇦🇪"),
    CountryCode("Canada", "+1", "🇨🇦"),
    CountryCode("Australia", "+61", "🇦🇺")
)

/**
 * Completely rebuilt Talkly Authentication Screen (Login & Register).
 * Built with an atmospheric dark background, animated electric border,
 * floating tab selector, custom neumorphic inputs, and tactile button animations.
 */
@Composable
fun PhonePasswordAuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSignIn: (phoneNumber: String, password: String) -> Unit,
    onSignUp: (phoneNumber: String, password: String, name: String) -> Unit,
    onForgotPassword: (String, (String) -> Unit, (String) -> Unit) -> Unit = { _, _, _ -> },
    onClearError: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Login, 1 = Register
    var selectedCountry by remember { mutableStateOf(COUNTRY_CODES[0]) }
    var phoneNumberInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var localValidationMessage by remember { mutableStateOf<String?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // ----------------------------------------------------
    // SHAKE ANIMATION FOR VALIDATION & BACKEND ERRORS
    // ----------------------------------------------------
    val shakeOffset = remember { Animatable(0f) }
    val displayError = localValidationMessage ?: errorMessage

    LaunchedEffect(displayError) {
        if (!displayError.isNullOrEmpty()) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    (-14f) at 50
                    14f at 100
                    (-10f) at 150
                    10f at 200
                    (-5f) at 250
                    5f at 300
                    0f at 400
                }
            )
        }
    }

    // ----------------------------------------------------
    // INFINITE ANIMATIONS: ROTATING ELECTRIC BORDER & PULSE GLOW
    // ----------------------------------------------------
    val infiniteTransition = rememberInfiniteTransition(label = "ElectricAndGlow")
    val electricRotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ElectricRotation"
    )

    val auraPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraPulse"
    )

    val logoFloatOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoFloat"
    )

    // Entrance animation
    var isAppeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isAppeared = true
    }

    val screenAlpha by animateFloatAsState(
        targetValue = if (isAppeared) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "ScreenAlpha"
    )
    val cardSlideUp by animateFloatAsState(
        targetValue = if (isAppeared) 0f else 35f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "CardSlideUp"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthDarkCanvas)
    ) {
        // =========================================================================
        // ATMOSPHERIC BACKGROUND: SOFT BRAND COLOR BLUR LIGHT SOURCES
        // =========================================================================
        // ATMOSPHERIC BACKGROUND: SOFT BRAND COLOR BLUR LIGHT SOURCES
        // =========================================================================
        // Upper-Left atmospheric light source (Talkly Cyan / Blue)
        Box(
            modifier = Modifier
                .size(360.dp)
                .offset(x = (-90).dp, y = (-70).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.12f),
                            DeepAqua.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Lower-Right atmospheric light source (Talkly Blue / Aqua Accent)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(340.dp)
                .offset(x = 100.dp, y = 80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            DeepAqua.copy(alpha = 0.10f),
                            TalklyBlue.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Center subtle ambiance
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(280.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
        )

        // =========================================================================
        // MAIN SCROLLABLE CONTENT
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // =====================================================================
            // 1. ANIMATED BRAND LOGO & HEADLINE
            // =====================================================================
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(y = logoFloatOffset.dp)
                    .graphicsLayer { alpha = screenAlpha }
            ) {
                // Outer subtle glowing aura for logo
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ElectricCyan.copy(alpha = auraPulseAlpha * 0.35f),
                                    DeepAqua.copy(alpha = auraPulseAlpha * 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Neumorphic Logo Container
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(68.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(22.dp),
                            ambientColor = DeepAqua,
                            spotColor = ElectricCyan
                        )
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF16202C),
                                    Color(0xFF0F1722)
                                )
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    ElectricCyan.copy(alpha = 0.85f),
                                    DeepAqua.copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = "Talkly Logo",
                        tint = ElectricCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Talkly",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = AuthTextPrimary,
                modifier = Modifier.graphicsLayer { alpha = screenAlpha }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (selectedTab == 0) "Family Messenger & Secure Connect" else "Join Talkly and connect with family",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AuthTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = screenAlpha }
            )

            Spacer(modifier = Modifier.height(26.dp))

            // =====================================================================
            // 2. CUSTOM FLOATING LOGIN / REGISTER SELECTOR
            // =====================================================================
            CustomFloatingAuthSelector(
                selectedTab = selectedTab,
                onTabSelect = { tabIndex ->
                    if (selectedTab != tabIndex) {
                        selectedTab = tabIndex
                        localValidationMessage = null
                        onClearError()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = screenAlpha }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // =====================================================================
            // 3. MAIN AUTHENTICATION PANEL WITH ANIMATED ELECTRIC BORDER & GLOW
            // =====================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(x = shakeOffset.value.roundToInt(), y = cardSlideUp.roundToInt()) }
                    .graphicsLayer { alpha = screenAlpha }
            ) {
                // (a) Pulsing Outer Glow around the card
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            scaleX = 1.04f
                            scaleY = 1.04f
                        }
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ElectricCyan.copy(alpha = auraPulseAlpha * 0.25f),
                                    DeepAqua.copy(alpha = auraPulseAlpha * 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // (b) Animated Electric Border wrapper
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .drawBehind {
                            // Moving electric highlight rotating continuously around perimeter
                            rotate(electricRotationAngle) {
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        0.0f to Color.Transparent,
                                        0.55f to Color.Transparent,
                                        0.70f to TalklyBlue.copy(alpha = 0.25f),
                                        0.82f to DeepAqua,
                                        0.92f to ElectricCyan,
                                        0.97f to Color.White.copy(alpha = 0.90f),
                                        1.0f to Color.Transparent
                                    ),
                                    radius = size.maxDimension * 0.9f
                                )
                            }
                        }
                        .padding(1.8.dp) // The visible electric border width
                ) {
                    // (c) Inner Deep Neumorphic Card Body
                    Surface(
                        color = AuthCardSurface,
                        shape = RoundedCornerShape(24.2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = 0.85f,
                                    stiffness = 350f
                                )
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            AuthCardSurface,
                                            AuthCardInner
                                        )
                                    )
                                )
                                .border(
                                    BorderStroke(1.dp, AuthBorderSubtle.copy(alpha = 0.6f)),
                                    RoundedCornerShape(24.2.dp)
                                )
                                .padding(horizontal = 20.dp, vertical = 22.dp)
                        ) {
                            // Dynamic Title & Subtitle with smooth transition
                            AnimatedContent(
                                targetState = selectedTab,
                                transitionSpec = {
                                    (slideInHorizontally { width -> if (targetState == 1) width else -width } + fadeIn(tween(250)))
                                        .togetherWith(slideOutHorizontally { width -> if (targetState == 1) -width else width } + fadeOut(tween(200)))
                                },
                                label = "AuthTitleTransition"
                            ) { tab ->
                                Column {
                                    Text(
                                        text = if (tab == 0) "Welcome Back" else "Create your account",
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AuthTextPrimary
                                    )
                                    Text(
                                        text = if (tab == 0) "Sign in with your mobile number" else "Enter your details to get started",
                                        fontSize = 12.sp,
                                        color = AuthTextSecondary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // FULL NAME FIELD (Register Only)
                            AnimatedVisibility(
                                visible = selectedTab == 1,
                                enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                                exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
                            ) {
                                Column {
                                    NeumorphicInputField(
                                        label = "Full Name",
                                        value = nameInput,
                                        onValueChange = {
                                            nameInput = it
                                            localValidationMessage = null
                                        },
                                        placeholder = "e.g. John Doe",
                                        leadingIcon = Icons.Default.Person,
                                        imeAction = ImeAction.Next,
                                        testTag = "input_fullname"
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                }
                            }

                            // MOBILE NUMBER FIELD WITH INTEGRATED COUNTRY PICKER
                            Text(
                                text = "Mobile Number",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AuthTextSecondary,
                                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                            )

                            var isPhoneFocused by remember { mutableStateOf(false) }
                            val phoneLiftOffset by animateDpAsState(
                                targetValue = if (isPhoneFocused) (-1.5).dp else 0.dp,
                                label = "PhoneLift"
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = phoneLiftOffset),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Country Code Dropdown Pill
                                Box {
                                    Surface(
                                        color = AuthFieldBg,
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, if (isPhoneFocused) ElectricCyan.copy(alpha = 0.5f) else AuthBorderSubtle),
                                        modifier = Modifier
                                            .height(52.dp)
                                            .clickable { dropdownExpanded = true }
                                            .testTag("country_code_selector")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${selectedCountry.flag} ${selectedCountry.code}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AuthTextPrimary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Select country code",
                                                tint = if (isPhoneFocused) ElectricCyan else AuthTextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = dropdownExpanded,
                                        onDismissRequest = { dropdownExpanded = false },
                                        modifier = Modifier
                                            .background(AuthCardSurface)
                                            .border(1.dp, AuthBorderElevated, RoundedCornerShape(14.dp))
                                    ) {
                                        COUNTRY_CODES.forEach { item ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(text = item.flag, fontSize = 16.sp)
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text(
                                                            text = item.country,
                                                            fontSize = 13.sp,
                                                            color = AuthTextPrimary,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = item.code,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = ElectricCyan
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    selectedCountry = item
                                                    dropdownExpanded = false
                                                },
                                                colors = MenuDefaults.itemColors(
                                                    textColor = AuthTextPrimary
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Phone Input Box
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isPhoneFocused) AuthFieldFocusedBg else AuthFieldBg)
                                        .border(
                                            width = if (isPhoneFocused) 1.5.dp else 1.dp,
                                            brush = if (isPhoneFocused) {
                                                Brush.horizontalGradient(listOf(ElectricCyan, DeepAqua))
                                            } else {
                                                SolidColor(AuthBorderSubtle)
                                            },
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = null,
                                            tint = if (isPhoneFocused) ElectricCyan else AuthTextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        BasicTextField(
                                            value = phoneNumberInput,
                                            onValueChange = { input ->
                                                if (input.length <= 12 && input.all { it.isDigit() }) {
                                                    phoneNumberInput = input
                                                    localValidationMessage = null
                                                }
                                            },
                                            textStyle = TextStyle(
                                                color = AuthTextPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Next
                                            ),
                                            cursorBrush = SolidColor(ElectricCyan),
                                            modifier = Modifier
                                                .weight(1f)
                                                .onFocusChanged { isPhoneFocused = it.isFocused }
                                                .testTag("input_phone_number"),
                                            decorationBox = { innerTextField ->
                                                if (phoneNumberInput.isEmpty()) {
                                                    Text(
                                                        text = "1712345678",
                                                        color = AuthTextMuted,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // PASSWORD FIELD
                            NeumorphicInputField(
                                label = "Password",
                                value = passwordInput,
                                onValueChange = {
                                    passwordInput = it
                                    localValidationMessage = null
                                },
                                placeholder = "••••••••••••",
                                leadingIcon = Icons.Default.Lock,
                                isPassword = true,
                                isPasswordVisible = isPasswordVisible,
                                onTogglePasswordVisibility = { isPasswordVisible = !isPasswordVisible },
                                keyboardType = KeyboardType.Password,
                                imeAction = if (selectedTab == 0) ImeAction.Done else ImeAction.Next,
                                onImeAction = {
                                    if (selectedTab == 0) {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }
                                },
                                testTag = "input_password"
                            )

                            // CONFIRM PASSWORD FIELD (Register Only)
                            AnimatedVisibility(
                                visible = selectedTab == 1,
                                enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                                exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    NeumorphicInputField(
                                        label = "Confirm Password",
                                        value = confirmPasswordInput,
                                        onValueChange = {
                                            confirmPasswordInput = it
                                            localValidationMessage = null
                                        },
                                        placeholder = "••••••••••••",
                                        leadingIcon = Icons.Default.Lock,
                                        isPassword = true,
                                        isPasswordVisible = isConfirmPasswordVisible,
                                        onTogglePasswordVisibility = { isConfirmPasswordVisible = !isConfirmPasswordVisible },
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done,
                                        onImeAction = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                        },
                                        testTag = "input_confirm_password"
                                    )
                                }
                            }

                            // FORGOT PASSWORD LINK (Login Only)
                            if (selectedTab == 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            localValidationMessage = null
                                            showForgotPasswordDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("btn_forgot_password")
                                    ) {
                                        Text(
                                            text = "Forgot password?",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ElectricCyan
                                        )
                                    }
                                }
                            }

                            // ERROR BANNER WITH SHAKE & GLOW
                            AnimatedVisibility(
                                visible = !displayError.isNullOrEmpty(),
                                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AuthErrorBg)
                                        .border(1.dp, AuthErrorRed.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Error",
                                        tint = AuthErrorRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = displayError.orEmpty(),
                                        color = AuthErrorRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // =====================================================================
            // 4. PRIMARY NEUMORPHIC ACTION BUTTON
            // =====================================================================
            CustomTactileAuthButton(
                text = if (selectedTab == 0) "Sign In" else "Create Account",
                isLoading = isLoading,
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()

                    val fullPhone = "${selectedCountry.code}${phoneNumberInput.trim()}"

                    if (phoneNumberInput.length < 6) {
                        localValidationMessage = "Please enter a valid mobile phone number."
                        return@CustomTactileAuthButton
                    }

                    if (passwordInput.length < 6) {
                        localValidationMessage = "Password must be at least 6 characters long."
                        return@CustomTactileAuthButton
                    }

                    if (selectedTab == 1) { // Register Mode
                        if (nameInput.isBlank()) {
                            localValidationMessage = "Please enter your full name."
                            return@CustomTactileAuthButton
                        }
                        if (passwordInput != confirmPasswordInput) {
                            localValidationMessage = "Passwords do not match. Please re-check."
                            return@CustomTactileAuthButton
                        }
                        onSignUp(fullPhone, passwordInput, nameInput.trim())
                    } else { // Login Mode
                        onSignIn(fullPhone, passwordInput)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = screenAlpha }
                    .testTag("btn_auth_submit")
            )

            Spacer(modifier = Modifier.height(18.dp))

            // =====================================================================
            // 5. SECONDARY SWITCHER (FOOTER)
            // =====================================================================
            Row(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .graphicsLayer { alpha = screenAlpha },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (selectedTab == 0) "Don't have an account? " else "Already have an account? ",
                    fontSize = 13.sp,
                    color = AuthTextSecondary
                )
                Text(
                    text = if (selectedTab == 0) "Create account" else "Login",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            selectedTab = if (selectedTab == 0) 1 else 0
                            localValidationMessage = null
                            onClearError()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("btn_switch_auth_mode")
                )
            }
        }

        // =========================================================================
        // 6. FORGOT PASSWORD DIALOG (NEUMORPHIC RESTYLE)
        // =========================================================================
        if (showForgotPasswordDialog) {
            var forgotPhoneInput by remember { mutableStateOf(phoneNumberInput) }
            var forgotCountry by remember { mutableStateOf(selectedCountry) }
            var forgotDropdownExpanded by remember { mutableStateOf(false) }
            var isSendingReset by remember { mutableStateOf(false) }
            var resetSuccessMessage by remember { mutableStateOf<String?>(null) }
            var resetErrorMessage by remember { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = {
                    if (!isSendingReset) {
                        showForgotPasswordDialog = false
                    }
                },
                containerColor = AuthCardSurface,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        text = "Reset Password",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AuthTextPrimary
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Enter your mobile phone number to receive password reset instructions.",
                            fontSize = 13.sp,
                            color = AuthTextSecondary,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Surface(
                                    color = AuthFieldBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, AuthBorderSubtle),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .clickable { forgotDropdownExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${forgotCountry.flag} ${forgotCountry.code}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AuthTextPrimary
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = AuthTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = forgotDropdownExpanded,
                                    onDismissRequest = { forgotDropdownExpanded = false },
                                    modifier = Modifier
                                        .background(AuthCardSurface)
                                        .border(1.dp, AuthBorderElevated, RoundedCornerShape(12.dp))
                                ) {
                                    COUNTRY_CODES.forEach { item ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${item.flag} ${item.country} (${item.code})",
                                                    fontSize = 13.sp,
                                                    color = AuthTextPrimary
                                                )
                                            },
                                            onClick = {
                                                forgotCountry = item
                                                forgotDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AuthFieldBg)
                                    .border(1.dp, AuthBorderSubtle, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicTextField(
                                    value = forgotPhoneInput,
                                    onValueChange = { input ->
                                        if (input.length <= 12 && input.all { it.isDigit() }) {
                                            forgotPhoneInput = input
                                            resetErrorMessage = null
                                            resetSuccessMessage = null
                                        }
                                    },
                                    textStyle = TextStyle(
                                        color = AuthTextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    cursorBrush = SolidColor(ElectricCyan),
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { innerTextField ->
                                        if (forgotPhoneInput.isEmpty()) {
                                            Text(
                                                text = "1712345678",
                                                color = AuthTextMuted,
                                                fontSize = 13.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                            }
                        }

                        // Success Message
                        AnimatedVisibility(
                            visible = !resetSuccessMessage.isNullOrEmpty(),
                            enter = fadeIn() + expandVertically()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AuthSuccessGreen.copy(alpha = 0.12f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AuthSuccessGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = resetSuccessMessage.orEmpty(),
                                    color = AuthSuccessGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Error Message
                        AnimatedVisibility(
                            visible = !resetErrorMessage.isNullOrEmpty(),
                            enter = fadeIn() + expandVertically()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AuthErrorBg)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = AuthErrorRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = resetErrorMessage.orEmpty(),
                                    color = AuthErrorRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (forgotPhoneInput.isBlank() || forgotPhoneInput.length < 6) {
                                resetErrorMessage = "Please enter a valid mobile phone number."
                                return@Button
                            }
                            val fullPhone = "${forgotCountry.code}${forgotPhoneInput.trim()}"
                            isSendingReset = true
                            resetErrorMessage = null
                            resetSuccessMessage = null
                            onForgotPassword(
                                fullPhone,
                                { successMsg ->
                                    isSendingReset = false
                                    resetSuccessMessage = successMsg
                                },
                                { errorMsg ->
                                    isSendingReset = false
                                    resetErrorMessage = errorMsg
                                }
                            )
                        },
                        enabled = !isSendingReset,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                    ) {
                        if (isSendingReset) {
                            CircularProgressIndicator(color = Color(0xFF040E14), modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Send Reset Link", color = Color(0xFF040E14), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showForgotPasswordDialog = false },
                        enabled = !isSendingReset
                    ) {
                        Text("Cancel", color = AuthTextSecondary, fontSize = 13.sp)
                    }
                }
            )
        }
    }
}

// =========================================================================
// CUSTOM COMPONENT: FLOATING AUTH SELECTOR (LOGIN ↔ REGISTER)
// =========================================================================
@Composable
private fun CustomFloatingAuthSelector(
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AuthCardSurface)
            .border(1.dp, AuthBorderSubtle, RoundedCornerShape(18.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tab 0: Login
        val isLogin = selectedTab == 0
        val loginBgColor by animateColorAsState(
            targetValue = if (isLogin) AuthCardInner else Color.Transparent,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            label = "LoginBg"
        )
        val loginTextColor by animateColorAsState(
            targetValue = if (isLogin) ElectricCyan else AuthTextSecondary,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            label = "LoginText"
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(loginBgColor)
                .then(
                    if (isLogin) {
                        Modifier
                            .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .shadow(4.dp, RoundedCornerShape(14.dp), ambientColor = ElectricCyan, spotColor = DeepAqua)
                    } else {
                        Modifier
                    }
                )
                .clickable { onTabSelect(0) }
                .testTag("tab_login"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Login",
                fontSize = 14.sp,
                fontWeight = if (isLogin) FontWeight.Bold else FontWeight.Medium,
                color = loginTextColor,
                letterSpacing = 0.4.sp
            )
        }

        // Tab 1: Register
        val isRegister = selectedTab == 1
        val registerBgColor by animateColorAsState(
            targetValue = if (isRegister) AuthCardInner else Color.Transparent,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            label = "RegisterBg"
        )
        val registerTextColor by animateColorAsState(
            targetValue = if (isRegister) ElectricCyan else AuthTextSecondary,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            label = "RegisterText"
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(registerBgColor)
                .then(
                    if (isRegister) {
                        Modifier
                            .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .shadow(4.dp, RoundedCornerShape(14.dp), ambientColor = ElectricCyan, spotColor = DeepAqua)
                    } else {
                        Modifier
                    }
                )
                .clickable { onTabSelect(1) }
                .testTag("tab_register"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Register",
                fontSize = 14.sp,
                fontWeight = if (isRegister) FontWeight.Bold else FontWeight.Medium,
                color = registerTextColor,
                letterSpacing = 0.4.sp
            )
        }
    }
}

// =========================================================================
// CUSTOM COMPONENT: NEUMORPHIC AUTH INPUT FIELD
// =========================================================================
@Composable
private fun NeumorphicInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    testTag: String = ""
) {
    var isFocused by remember { mutableStateOf(false) }

    val liftOffset by animateDpAsState(
        targetValue = if (isFocused) (-1.5).dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "InputLift"
    )

    val borderBrush = if (isFocused) {
        Brush.horizontalGradient(listOf(ElectricCyan, DeepAqua))
    } else {
        SolidColor(AuthBorderSubtle)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = liftOffset)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = AuthTextSecondary,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isFocused) AuthFieldFocusedBg else AuthFieldBg)
                .border(
                    width = if (isFocused) 1.5.dp else 1.dp,
                    brush = borderBrush,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isFocused) ElectricCyan else AuthTextMuted,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        color = AuthTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    singleLine = true,
                    visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onImeAction?.invoke() },
                        onNext = { onImeAction?.invoke() }
                    ),
                    cursorBrush = SolidColor(ElectricCyan),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isFocused = it.isFocused }
                        .testTag(testTag),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = AuthTextMuted,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                )

                if (isPassword && onTogglePasswordVisibility != null) {
                    IconButton(
                        onClick = onTogglePasswordVisibility,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                            tint = if (isPasswordVisible) ElectricCyan else AuthTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// CUSTOM COMPONENT: TACTILE NEUMORPHIC PRIMARY BUTTON
// =========================================================================
@Composable
private fun CustomTactileAuthButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isEnabled = !isLoading

    val buttonScale by animateFloatAsState(
        targetValue = when {
            isLoading -> 0.98f
            isPressed -> 0.96f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 120),
        label = "ButtonScale"
    )

    Button(
        onClick = onClick,
        enabled = isEnabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .height(54.dp)
            .scale(buttonScale)
            .shadow(
                elevation = if (isEnabled && !isPressed) 14.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = DeepAqua,
                spotColor = ElectricCyan
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isEnabled) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            ElectricCyan,
                            DeepAqua,
                            TalklyBlue
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.35f),
                            DeepAqua.copy(alpha = 0.35f)
                        )
                    )
                }
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color(0xFF040E14),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF040E14),
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}
