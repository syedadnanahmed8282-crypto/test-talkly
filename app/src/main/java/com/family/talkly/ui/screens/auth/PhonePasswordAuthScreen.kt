package com.family.talkly.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

// ==========================================
// TALKLY REFINED BRAND PALETTE
// ==========================================
private val CanvasBackground = Color(0xFF090D14)
private val CardBackground = Color(0xFF111722)
private val CardInnerBackground = Color(0xFF0E131C)
private val FieldBackground = Color(0xFF0B0F17)
private val FieldBackgroundFocused = Color(0xFF0F1520)

private val BrandCyan = Color(0xFF22D3EE)
private val BrandAqua = Color(0xFF0EA5E9)
private val BrandMint = Color(0xFF5EEAD4)
private val BrandDeepBlue = Color(0xFF0284C7)

private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF475569)
private val BorderMuted = Color(0xFF1E293B)
private val BorderElevated = Color(0xFF2A374A)

private val ErrorColor = Color(0xFFF43F5E)
private val ErrorBackground = Color(0x1EF43F5E)
private val SuccessColor = Color(0xFF10B981)

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
    var emailInput by remember { mutableStateOf("") }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var localValidationMessage by remember { mutableStateOf<String?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // ----------------------------------------------------
    // SHAKE ANIMATION FOR ERRORS
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
                    (-12f) at 50
                    12f at 100
                    (-8f) at 150
                    8f at 200
                    (-4f) at 250
                    4f at 300
                    0f at 400
                }
            )
        }
    }

    // ----------------------------------------------------
    // ENTRANCE FLOATING ANIMATION
    // ----------------------------------------------------
    var isCardEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isCardEntered = true
    }

    val cardAlpha by animateFloatAsState(
        targetValue = if (isCardEntered) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "cardAlpha"
    )
    val cardTranslateY by animateFloatAsState(
        targetValue = if (isCardEntered) 0f else 40f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "cardTranslateY"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
    ) {
        // Decorative ambient glow spots
        Box(
            modifier = Modifier
                .size(340.dp)
                .offset(x = (-80).dp, y = (-70).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BrandCyan.copy(alpha = 0.12f),
                            BrandDeepBlue.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(320.dp)
                .offset(x = 90.dp, y = 70.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BrandAqua.copy(alpha = 0.10f),
                            BrandMint.copy(alpha = 0.03f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 1. BRAND LOGO WITH NEUMORPHIC GLOW
            // ==========================================
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        alpha = cardAlpha
                        scaleX = if (isCardEntered) 1f else 0.85f
                        scaleY = if (isCardEntered) 1f else 0.85f
                    }
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = BrandCyan,
                        spotColor = BrandCyan
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF16202E),
                                Color(0xFF0F1722)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                BrandCyan.copy(alpha = 0.8f),
                                BrandAqua.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubble,
                    contentDescription = "Talkly Logo",
                    tint = BrandCyan,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Talkly",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (selectedTab == 0) "Sign in to continue to Talkly" else "Join Talkly and stay connected",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // 2. SEGMENTED TAB SELECTOR (LOGIN ↔ REGISTER)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBackground)
                    .border(1.dp, BorderMuted, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Login Tab
                val isLoginSelected = selectedTab == 0
                val loginBgColor by animateColorAsState(
                    targetValue = if (isLoginSelected) CardInnerBackground else Color.Transparent,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "loginTabBg"
                )
                val loginTextColor by animateColorAsState(
                    targetValue = if (isLoginSelected) BrandCyan else TextSecondary,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "loginTabText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(loginBgColor)
                        .then(
                            if (isLoginSelected) {
                                Modifier.border(1.dp, BorderElevated, RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            }
                        )
                        .clickable {
                            if (selectedTab != 0) {
                                selectedTab = 0
                                localValidationMessage = null
                                onClearError()
                            }
                        }
                        .testTag("tab_login"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Login",
                        fontSize = 14.sp,
                        fontWeight = if (isLoginSelected) FontWeight.Bold else FontWeight.Medium,
                        color = loginTextColor
                    )
                }

                // Register Tab
                val isRegisterSelected = selectedTab == 1
                val registerBgColor by animateColorAsState(
                    targetValue = if (isRegisterSelected) CardInnerBackground else Color.Transparent,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "registerTabBg"
                )
                val registerTextColor by animateColorAsState(
                    targetValue = if (isRegisterSelected) BrandCyan else TextSecondary,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "registerTabText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(registerBgColor)
                        .then(
                            if (isRegisterSelected) {
                                Modifier.border(1.dp, BorderElevated, RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            }
                        )
                        .clickable {
                            if (selectedTab != 1) {
                                selectedTab = 1
                                localValidationMessage = null
                                onClearError()
                            }
                        }
                        .testTag("tab_register"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Register",
                        fontSize = 14.sp,
                        fontWeight = if (isRegisterSelected) FontWeight.Bold else FontWeight.Medium,
                        color = registerTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // 3. ELECTRIC NEUMORPHIC CARD CONTAINER
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(x = shakeOffset.value.roundToInt(), y = cardTranslateY.roundToInt()) }
                    .graphicsLayer { alpha = cardAlpha }
            ) {
                // Inner Main Card Body
                Surface(
                    color = CardBackground,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, BorderElevated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(24.dp),
                            ambientColor = Color.Black,
                            spotColor = Color(0x33000000)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Dynamic Title inside the Card
                        Text(
                            text = if (selectedTab == 0) "Welcome Back" else "Create your account",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Full Name Field (Register Only)
                        AnimatedVisibility(
                            visible = selectedTab == 1,
                            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                        ) {
                            Column {
                                NeumorphicAuthInputField(
                                    label = "Full name",
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
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        // Email Field (Register Only)
                        AnimatedVisibility(
                            visible = selectedTab == 1,
                            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                        ) {
                            Column {
                                NeumorphicAuthInputField(
                                    label = "Email address",
                                    value = emailInput,
                                    onValueChange = {
                                        emailInput = it
                                        localValidationMessage = null
                                    },
                                    placeholder = "name@example.com",
                                    leadingIcon = Icons.Default.Email,
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                    testTag = "input_email"
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        // Mobile Phone Number Field with Country Picker
                        Text(
                            text = "Mobile number",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                        )

                        var isPhoneFocused by remember { mutableStateOf(false) }
                        val phoneBorderBrush = if (isPhoneFocused) {
                            Brush.horizontalGradient(
                                listOf(
                                    BrandCyan,
                                    BrandAqua
                                )
                            )
                        } else {
                            SolidColor(BorderMuted)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Country Code Dropdown Pill
                            Box {
                                Surface(
                                    color = FieldBackground,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, BorderMuted),
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
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select country code",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier
                                        .background(CardBackground)
                                        .border(1.dp, BorderElevated, RoundedCornerShape(12.dp))
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
                                                        color = TextPrimary,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = item.code,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = BrandCyan
                                                    )
                                                }
                                            },
                                            onClick = {
                                                selectedCountry = item
                                                dropdownExpanded = false
                                            },
                                            colors = MenuDefaults.itemColors(
                                                textColor = TextPrimary
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Phone Input Box with Inset Neumorphic Styling
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isPhoneFocused) FieldBackgroundFocused else FieldBackground)
                                    .border(
                                        width = if (isPhoneFocused) 1.5.dp else 1.dp,
                                        brush = phoneBorderBrush,
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
                                        tint = if (isPhoneFocused) BrandCyan else TextMuted,
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
                                            color = TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Next
                                        ),
                                        cursorBrush = SolidColor(BrandCyan),
                                        modifier = Modifier
                                            .weight(1f)
                                            .onFocusChanged { isPhoneFocused = it.isFocused }
                                            .testTag("input_phone_number"),
                                        decorationBox = { innerTextField ->
                                            if (phoneNumberInput.isEmpty()) {
                                                Text(
                                                    text = "1712345678",
                                                    color = TextMuted,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Password Field
                        NeumorphicAuthInputField(
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

                        // Confirm Password (Register Only)
                        AnimatedVisibility(
                            visible = selectedTab == 1,
                            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))
                                NeumorphicAuthInputField(
                                    label = "Confirm password",
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

                        // Forgot Password Link (Login Only)
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
                                        color = BrandCyan
                                    )
                                }
                            }
                        }

                        // Error Banner with Shake & Subtle Glow
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
                                    .background(ErrorBackground)
                                    .border(1.dp, ErrorColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = ErrorColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = displayError.orEmpty(),
                                    color = ErrorColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // 4. PRIMARY NEUMORPHIC ACTION BUTTON
            // ==========================================
            val isButtonEnabled = !isLoading
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            val buttonScale by animateFloatAsState(
                targetValue = when {
                    isLoading -> 0.98f
                    isPressed -> 0.96f
                    else -> 1f
                },
                animationSpec = tween(durationMillis = 120),
                label = "buttonScale"
            )

            Button(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()

                    val fullPhone = "${selectedCountry.code}${phoneNumberInput.trim()}"

                    if (phoneNumberInput.length < 6) {
                        localValidationMessage = "Please enter a valid mobile phone number."
                        return@Button
                    }

                    if (passwordInput.length < 6) {
                        localValidationMessage = "Password must be at least 6 characters long."
                        return@Button
                    }

                    if (selectedTab == 1) { // Register Mode
                        if (nameInput.isBlank()) {
                            localValidationMessage = "Please enter your full name."
                            return@Button
                        }
                        if (passwordInput != confirmPasswordInput) {
                            localValidationMessage = "Passwords do not match. Please re-check."
                            return@Button
                        }
                        onSignUp(fullPhone, passwordInput, nameInput.trim())
                    } else { // Login Mode
                        onSignIn(fullPhone, passwordInput)
                    }
                },
                enabled = isButtonEnabled,
                interactionSource = interactionSource,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .scale(buttonScale)
                    .shadow(
                        elevation = if (isButtonEnabled && !isPressed) 14.dp else 2.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = BrandCyan,
                        spotColor = BrandCyan
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isButtonEnabled) {
                            Brush.horizontalGradient(
                                colors = listOf(BrandCyan, BrandAqua, BrandDeepBlue)
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(
                                    BrandCyan.copy(alpha = 0.35f),
                                    BrandAqua.copy(alpha = 0.35f)
                                )
                            )
                        }
                    )
                    .testTag("btn_auth_submit")
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = CanvasBackground,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = if (selectedTab == 0) "Sign In" else "Create Account",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CanvasBackground,
                            letterSpacing = 0.4.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Footer Switcher ("Don't have an account? Create account" / "Already have an account? Login")
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (selectedTab == 0) "Don't have an account? " else "Already have an account? ",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Text(
                    text = if (selectedTab == 0) "Create account" else "Login",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandCyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            selectedTab = if (selectedTab == 0) 1 else 0
                            localValidationMessage = null
                            onClearError()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // ==========================================
        // 5. FORGOT PASSWORD DIALOG
        // ==========================================
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
                containerColor = CardBackground,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        text = "Reset Password",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Enter your mobile number to receive password reset instructions.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Surface(
                                    color = FieldBackground,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, BorderMuted),
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
                                            color = TextPrimary
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = forgotDropdownExpanded,
                                    onDismissRequest = { forgotDropdownExpanded = false },
                                    modifier = Modifier
                                        .background(CardBackground)
                                        .border(1.dp, BorderElevated, RoundedCornerShape(12.dp))
                                ) {
                                    COUNTRY_CODES.forEach { item ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${item.flag} ${item.country} (${item.code})",
                                                    fontSize = 13.sp,
                                                    color = TextPrimary
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
                                    .background(FieldBackground)
                                    .border(1.dp, BorderMuted, RoundedCornerShape(12.dp))
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
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    cursorBrush = SolidColor(BrandCyan),
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { innerTextField ->
                                        if (forgotPhoneInput.isEmpty()) {
                                            Text(
                                                text = "1712345678",
                                                color = TextMuted,
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
                                    .background(SuccessColor.copy(alpha = 0.12f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = resetSuccessMessage.orEmpty(),
                                    color = SuccessColor,
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
                                    .background(ErrorBackground)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = ErrorColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = resetErrorMessage.orEmpty(),
                                    color = ErrorColor,
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
                        colors = ButtonDefaults.buttonColors(containerColor = BrandCyan)
                    ) {
                        if (isSendingReset) {
                            CircularProgressIndicator(color = CanvasBackground, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Send Reset Link", color = CanvasBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showForgotPasswordDialog = false },
                        enabled = !isSendingReset
                    ) {
                        Text("Cancel", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            )
        }
    }
}

// ==========================================
// REUSABLE NEUMORPHIC AUTH INPUT FIELD
// ==========================================
@Composable
private fun NeumorphicAuthInputField(
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

    val borderBrush = if (isFocused) {
        Brush.horizontalGradient(
            listOf(
                BrandCyan,
                BrandAqua
            )
        )
    } else {
        SolidColor(BorderMuted)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isFocused) FieldBackgroundFocused else FieldBackground)
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
                    tint = if (isFocused) BrandCyan else TextMuted,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        color = TextPrimary,
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
                    cursorBrush = SolidColor(BrandCyan),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isFocused = it.isFocused }
                        .testTag(testTag),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = TextMuted,
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
                            tint = if (isPasswordVisible) BrandCyan else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
