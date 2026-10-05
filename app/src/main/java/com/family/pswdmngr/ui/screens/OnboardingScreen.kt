package com.family.pswdmngr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OnboardingScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var showConfirmPw by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val passwordFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }

    val validLength = password.length >= 8
    val match = password == confirm && password.isNotEmpty()
    val canProceed = validLength && match && !working

    AuthScrollColumn(
        modifier = Modifier.background(HeroGradient),
    ) {
        Spacer(Modifier.height(16.dp))
        
        AppLogoBadge(size = 80.dp)

        Spacer(Modifier.height(18.dp))
        Text(
            "Create Master Password",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Set your master password to encrypt your vault.\n100% offline & zero-knowledge security.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        
        Spacer(Modifier.height(24.dp))

        // Security reassurance chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Surface2.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Stroke),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.WifiOff, null, tint = Cyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("100% Offline", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Rounded.Lock, null, tint = Mint, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Argon2id + AES", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        VaultTextField(
            value = password,
            onValueChange = { password = it },
            label = "Master password",
            modifier = Modifier.focusRequester(passwordFocus),
            leadingIcon = { Icon(Icons.Rounded.Lock, null, tint = Cyan) },
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onNext = { confirmFocus.requestFocus() }
            ),
            trailingIcon = {
                IconButton(onClick = { showPw = !showPw }) {
                    Icon(
                        if (showPw) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        null, tint = TextSecondary
                    )
                }
            },
        )
        Spacer(Modifier.height(8.dp))

        PasswordStrengthMeter(password)

        Spacer(Modifier.height(14.dp))
        VaultTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Confirm master password",
            modifier = Modifier.focusRequester(confirmFocus),
            leadingIcon = { 
                Icon(
                    if (match) Icons.Rounded.CheckCircle else Icons.Rounded.Key,
                    null,
                    tint = if (match) Mint else Cyan,
                ) 
            },
            visualTransformation = if (showConfirmPw) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = {
                    if (canProceed) {
                        working = true
                        error = null
                        scope.launch {
                            try {
                                val recoveryKey = withContext(Dispatchers.Default) {
                                    VaultSession.create(ctx, password.toCharArray())
                                }
                                VaultSession.pendingRecoveryKey = recoveryKey
                                nav.navigate("recoveryKey") { popUpTo(0) { inclusive = true } }
                            } catch (e: Throwable) {
                                working = false
                                error = e.localizedMessage ?: "Failed to create vault"
                            }
                        }
                    }
                }
            ),
            trailingIcon = {
                IconButton(onClick = { showConfirmPw = !showConfirmPw }) {
                    Icon(
                        if (showConfirmPw) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        null, tint = TextSecondary
                    )
                }
            },
        )
        
        if (confirm.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            if (match) {
                Text("✓ Passwords match", color = Mint, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.Start))
            } else {
                Text("Passwords do not match", color = Coral, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.Start))
            }
        }

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error ?: "", color = Coral, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.Start))
        }

        Spacer(Modifier.height(28.dp))
        if (working) {
            CircularProgressIndicator(color = Cyan)
            Spacer(Modifier.height(8.dp))
            Text("Deriving encryption key…", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        } else {
            AccentButton(
                "Create Vault",
                modifier = Modifier.fillMaxWidth(),
                enabled = canProceed,
                icon = Icons.Rounded.LockOpen,
            ) {
                working = true
                error = null
                scope.launch {
                    try {
                        val recoveryKey = withContext(Dispatchers.Default) {
                            VaultSession.create(ctx, password.toCharArray())
                        }
                        VaultSession.pendingRecoveryKey = recoveryKey
                        nav.navigate("recoveryKey") { popUpTo(0) { inclusive = true } }
                    } catch (e: Throwable) {
                        working = false
                        error = e.localizedMessage ?: "Failed to create vault"
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}
