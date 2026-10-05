package com.family.pswdmngr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    var working by remember { mutableStateOf(false) }

    val passwordFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }

    val strong = isPasswordStrong(password) // ~12+ mixed chars
    val match = password == confirm && password.isNotEmpty()

    AuthScrollColumn(
        modifier = Modifier.background(HeroGradient),
    ) {
        // The decorative hero shrinks on short viewports so the form itself
        // still clears the fold on a 5" screen instead of burying the fields.
        Spacer(Modifier.height(24.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(120.dp).clip(CircleShape)
                    .background(GlowCyan),
            )
            IconBadge(Icons.Rounded.Shield, Cyan, size = 84)
        }
        Spacer(Modifier.height(24.dp))
        Text("Welcome", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your passwords, cards, and notes — fully offline and encrypted.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        val features = listOf(
            Icons.Rounded.WifiOff to "100% Offline — no network ever",
            Icons.Rounded.Lock to "End-to-end encrypted vault",
            Icons.Rounded.Fingerprint to "Biometric quick unlock",
            Icons.Rounded.Backup to "Encrypted backup & restore",
        )
        features.forEach { (icon, text) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(Cyan.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = Cyan, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(text, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Create master password", style = MaterialTheme.typography.titleMedium, color = TextPrimary,
            modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(6.dp))
        Text(
            "One password protects everything. It is never stored — write it down and keep it safe.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Start,
            modifier = Modifier.align(Alignment.Start),
        )
        Spacer(Modifier.height(20.dp))

        VaultTextField(
            value = password,
            onValueChange = { password = it },
            label = "Master password",
            modifier = Modifier.focusRequester(passwordFocus),
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            trailingIcon = {
                IconButton(onClick = { showPw = !showPw }) {
                    Icon(
                        if (showPw) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        null, tint = TextSecondary
                    )
                }
            },
        )
        Spacer(Modifier.height(10.dp))

        PasswordStrengthMeter(password)

        Spacer(Modifier.height(12.dp))
        VaultTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Confirm password",
            modifier = Modifier.focusRequester(confirmFocus),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )
        if (confirm.isNotEmpty() && !match) {
            Spacer(Modifier.height(4.dp))
            Text("Passwords don't match", color = Coral, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.Start))
        }

        // Fixed spacer, not weight(1f): weight is illegal under the unbounded
        // max-height constraint a verticalScroll column imposes (it throws at
        // measure time), which is the trap that made this screen unscrollable.
        Spacer(Modifier.height(24.dp))
        if (working) {
            CircularProgressIndicator(color = Cyan)
            Spacer(Modifier.height(8.dp))
            Text("Deriving encryption key…", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        } else {
            AccentButton(
                "Get started",
                modifier = Modifier.fillMaxWidth(),
                enabled = strong && match,
            ) {
                working = true
                scope.launch {
                    val recoveryKey = withContext(Dispatchers.Default) {
                        VaultSession.create(ctx, password.toCharArray())
                    }
                    VaultSession.pendingRecoveryKey = recoveryKey
                    nav.navigate("recoveryKey") { popUpTo(0) { inclusive = true } }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
