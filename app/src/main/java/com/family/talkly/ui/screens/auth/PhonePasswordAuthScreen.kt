package com.family.talkly.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Color constants matching the exact design provided by the user
private val PurplePrimary = Color(0xFF7A55B7)
private val PurpleSecondary = Color(0xFF8764C1)
private val PurpleLight = Color(0xFFC7B7E0)
private val PurpleDarkText = Color(0xFF382559)
private val PurpleSubtext = Color(0xFF8C7CA6)
private val PurpleInputBg = Color(0xFFF9F8FC)
private val PurpleInputBorder = Color(0xFFE9E4F3)
private val PurpleBackground = Color(0xFFFAFAFE)

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PurpleBackground)
    ) {
        // --- Top Right Decorative Circles ---
        Box(
            modifier = Modifier
                .size(240.dp)
                .offset(x = 100.dp, y = (-40).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(PurpleSecondary, PurplePrimary)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .offset(x = 20.dp, y = (-20).dp)
                .clip(CircleShape)
                .background(PurpleSecondary.copy(alpha = 0.85f))
        )

        // --- Bottom Left Decorative Circles ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(130.dp)
                .offset(x = (-40).dp, y = 40.dp)
                .clip(CircleShape)
                .background(PurplePrimary)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(70.dp)
                .offset(x = 55.dp, y = 5.dp)
                .clip(CircleShape)
                .background(PurpleSecondary)
        )

        // --- Content Area ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(130.dp))

            // --- Tab Switcher ("Login" | "Register") ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                // Login Tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        selectedTab = 0
                        localValidationMessage = null
                        onClearError()
                    }
                ) {
                    Text(
                        text = "Login",
                        fontSize = 17.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTab == 0) PurpleDarkText else PurpleSubtext
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (selectedTab == 0) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(PurplePrimary)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }

                Spacer(modifier = Modifier.width(28.dp))

                // Register Tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        selectedTab = 1
                        localValidationMessage = null
                        onClearError()
                    }
                ) {
                    Text(
                        text = "Register",
                        fontSize = 17.sp,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTab == 1) PurpleDarkText else PurpleSubtext
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (selectedTab == 1) {
                        Box(
                            modifier = Modifier
                                .width(34.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(PurplePrimary)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // --- Form Container ---
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PurpleInputBorder, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    // Full Name Field (Register Mode Only)
                    if (selectedTab == 1) {
                        Text(
                            text = "User Name",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = PurpleSubtext
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = {
                                nameInput = it
                                localValidationMessage = null
                            },
                            textStyle = TextStyle(color = PurpleDarkText, fontSize = 14.sp),
                            placeholder = { Text("username/name", fontSize = 13.sp, color = PurpleSubtext.copy(alpha = 0.6f)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = PurpleLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PurplePrimary,
                                unfocusedBorderColor = PurpleInputBorder,
                                focusedContainerColor = PurpleInputBg,
                                unfocusedContainerColor = PurpleInputBg
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Mobile Phone Field (with Country Code selector)
                    Text(
                        text = "Mobile Phone Number",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = PurpleSubtext
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Surface(
                                color = PurpleInputBg,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PurpleInputBorder),
                                modifier = Modifier.clickable { dropdownExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${selectedCountry.flag} ${selectedCountry.code}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PurpleDarkText
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select country",
                                        tint = PurpleSubtext,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                COUNTRY_CODES.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text("${item.flag} ${item.country} (${item.code})", fontSize = 13.sp) },
                                        onClick = {
                                            selectedCountry = item
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedTextField(
                            value = phoneNumberInput,
                            onValueChange = { input ->
                                if (input.length <= 12 && input.all { it.isDigit() }) {
                                    phoneNumberInput = input
                                    localValidationMessage = null
                                }
                            },
                            textStyle = TextStyle(color = PurpleDarkText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            placeholder = { Text("1712345678", fontSize = 13.sp, color = PurpleSubtext.copy(alpha = 0.6f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = PurpleLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PurplePrimary,
                                unfocusedBorderColor = PurpleInputBorder,
                                focusedContainerColor = PurpleInputBg,
                                unfocusedContainerColor = PurpleInputBg
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Email Field (Register Mode)
                    if (selectedTab == 1) {
                        Text(
                            text = "Email",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = PurpleSubtext
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = {
                                emailInput = it
                                localValidationMessage = null
                            },
                            textStyle = TextStyle(color = PurpleDarkText, fontSize = 14.sp),
                            placeholder = { Text("nam@gmail.com", fontSize = 13.sp, color = PurpleSubtext.copy(alpha = 0.6f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = PurpleLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PurplePrimary,
                                unfocusedBorderColor = PurpleInputBorder,
                                focusedContainerColor = PurpleInputBg,
                                unfocusedContainerColor = PurpleInputBg
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Password Field
                    Text(
                        text = "Password",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = PurpleSubtext
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            localValidationMessage = null
                        },
                        textStyle = TextStyle(color = PurpleDarkText, fontSize = 14.sp),
                        placeholder = { Text("••••••••••••", fontSize = 13.sp, color = PurpleSubtext.copy(alpha = 0.6f)) },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (selectedTab == 0) ImeAction.Done else ImeAction.Next
                        ),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = PurpleLight,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Password Visibility",
                                    tint = PurpleSubtext,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PurplePrimary,
                            unfocusedBorderColor = PurpleInputBorder,
                            focusedContainerColor = PurpleInputBg,
                            unfocusedContainerColor = PurpleInputBg
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Confirm Password (Register Mode Only)
                    if (selectedTab == 1) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Confirm Password",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = PurpleSubtext
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = confirmPasswordInput,
                            onValueChange = {
                                confirmPasswordInput = it
                                localValidationMessage = null
                            },
                            textStyle = TextStyle(color = PurpleDarkText, fontSize = 14.sp),
                            placeholder = { Text("••••••••••••", fontSize = 13.sp, color = PurpleSubtext.copy(alpha = 0.6f)) },
                            singleLine = true,
                            visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = PurpleLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Confirm Password Visibility",
                                        tint = PurpleSubtext,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PurplePrimary,
                                unfocusedBorderColor = PurpleInputBorder,
                                focusedContainerColor = PurpleInputBg,
                                unfocusedContainerColor = PurpleInputBg
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Forgot Password Link (Login Mode Only)
                    if (selectedTab == 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    localValidationMessage = null
                                    showForgotPasswordDialog = true
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = "Forgot Password?",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PurplePrimary
                                )
                            }
                        }
                    }

                    // Error Message
                    val displayError = localValidationMessage ?: errorMessage
                    if (!displayError.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = displayError,
                            color = Color(0xFFE53935),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Action Button (Login / Register) ---
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .shadow(8.dp, RoundedCornerShape(25.dp), ambientColor = PurplePrimary, spotColor = PurplePrimary)
            ) {
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
                                localValidationMessage = "Please enter your user name."
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
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(25.dp),
                    modifier = Modifier
                        .width(180.dp)
                        .height(46.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = if (selectedTab == 0) "Login" else "Register",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // --- Forgot Password Dialog ---
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
                title = {
                    Text(
                        text = "Reset Password",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurpleDarkText
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Enter your mobile phone number to receive password reset instructions.",
                            fontSize = 13.sp,
                            color = PurpleSubtext
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Surface(
                                    color = PurpleInputBg,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, PurpleInputBorder),
                                    modifier = Modifier.clickable { forgotDropdownExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${forgotCountry.flag} ${forgotCountry.code}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PurpleDarkText
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = PurpleSubtext
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = forgotDropdownExpanded,
                                    onDismissRequest = { forgotDropdownExpanded = false }
                                ) {
                                    COUNTRY_CODES.forEach { item ->
                                        DropdownMenuItem(
                                            text = { Text("${item.flag} ${item.country} (${item.code})", fontSize = 13.sp) },
                                            onClick = {
                                                forgotCountry = item
                                                forgotDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedTextField(
                                value = forgotPhoneInput,
                                onValueChange = { input ->
                                    if (input.length <= 12 && input.all { it.isDigit() }) {
                                        forgotPhoneInput = input
                                        resetErrorMessage = null
                                        resetSuccessMessage = null
                                    }
                                },
                                placeholder = { Text("1712345678", fontSize = 13.sp, color = PurpleSubtext.copy(alpha = 0.6f)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!resetSuccessMessage.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = resetSuccessMessage!!,
                                color = PurplePrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (!resetErrorMessage.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = resetErrorMessage!!,
                                color = Color.Red,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
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
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                    ) {
                        if (isSendingReset) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Send Reset Link", color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showForgotPasswordDialog = false },
                        enabled = !isSendingReset
                    ) {
                        Text("Cancel", color = PurpleSubtext)
                    }
                }
            )
        }
    }
}
