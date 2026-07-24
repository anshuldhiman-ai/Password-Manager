package com.family.pswdmngr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.family.pswdmngr.crypto.PasswordGenerator
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

    val entropy = PasswordGenerator.entropy(password)
    val strong = entropy >= 60 // ~12+ mixed chars
    val match = password == confirm && password.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HeroGradient)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(60.dp))
        IconBadge(Icons.Rounded.Shield, Violet, size = 84)
        Spacer(Modifier.height(24.dp))
        Text("Create your vault", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "One master password protects everything.\nIt is never stored — write it down and keep it safe.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))

        VaultTextField(
            value = password,
            onValueChange = { password = it },
            label = "Master password",
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

        // Strength meter
        val meterColor = when {
            entropy >= 60 -> Mint
            entropy >= 40 -> Amber
            else -> Coral
        }
        LinearProgressIndicator(
            progress = { (entropy / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = meterColor,
            trackColor = Surface2,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                password.isEmpty() -> " "
                entropy >= 60 -> "Strong"
                entropy >= 40 -> "Okay — longer is better"
                else -> "Too weak"
            },
            style = MaterialTheme.typography.labelMedium, color = meterColor,
            modifier = Modifier.align(Alignment.Start),
        )

        Spacer(Modifier.height(12.dp))
        VaultTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Confirm password",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )
        if (confirm.isNotEmpty() && !match) {
            Spacer(Modifier.height(4.dp))
            Text("Passwords don't match", color = Coral, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.Start))
        }

        Spacer(Modifier.weight(1f))
        if (working) {
            CircularProgressIndicator(color = Violet)
            Spacer(Modifier.height(8.dp))
            Text("Deriving encryption key…", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        } else {
            GradientButton(
                "Create vault",
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
