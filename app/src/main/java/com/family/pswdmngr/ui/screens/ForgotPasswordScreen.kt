package com.family.pswdmngr.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.family.pswdmngr.crypto.PasswordGenerator
import com.family.pswdmngr.crypto.RecoveryKeyGenerator
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Forgot master password?" flow.
 *
 * Step 1: Enter recovery key → validates and unwraps the vault master key
 * Step 2: Set a new master password → replaces the password-wrapped blob
 * Step 3: Proceed to vault
 */
@Composable
fun ForgotPasswordScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(1) } // 1 = recovery key, 2 = new password, 3 = done
    var recoveryInput by remember { mutableStateOf("") }
    var recoveryError by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var vaultMasterKey by remember { mutableStateOf<ByteArray?>(null) }

    // New password
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var pwError by remember { mutableStateOf<String?>(null) }

    val normalized by derivedStateOf { RecoveryKeyGenerator.normalize(recoveryInput) }
    val isValidFormat by derivedStateOf { RecoveryKeyGenerator.isValid(normalized) }

    fun proceedToVault() {
        nav.navigate("vault") { popUpTo(0) { inclusive = true } }
    }

    Box(
        Modifier.fillMaxSize().background(HeroGradient).padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Step indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("Recovery Key", "New Password", "Done").forEachIndexed { i, label ->
                    val active = step == i + 1
                    val done = step > i + 1
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = when {
                                done -> Mint
                                active -> Cyan
                                else -> Surface2
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    when { done -> Icons.Rounded.Check; else -> Icons.Rounded.Circle },
                                    null,
                                    tint = if (done || active) Midnight else TextSecondary,
                                    modifier = Modifier.size(if (done) 16.dp else 10.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(label, color = if (active) TextPrimary else TextSecondary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    if (i < 2) {
                        Divider(
                            color = if (done) Mint else Surface2,
                            modifier = Modifier.width(40.dp).padding(bottom = 16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            AnimatedContent(targetState = step, label = "forgotPwStep", transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }) { s ->
                when (s) {
                    // ── Step 1: Enter Recovery Key ──────────────────────────────
                    1 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconBadge(Icons.Rounded.VpnKey, Amber, size = 72)
                        Spacer(Modifier.height(16.dp))
                        Text("Enter Recovery Key",
                            style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Paste or type the 24-character key you saved when setting up the vault.",
                            color = TextSecondary, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(24.dp))
                        OutlinedTextField(
                            value = recoveryInput,
                            onValueChange = { recoveryInput = it.uppercase(); recoveryError = null },
                            label = { Text("XG7K-9PLM-2QRT-7HDN-3WSE-6FKL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done,
                            ),
                            shape = RoundedCornerShape(18.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Amber,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                                cursorColor = Amber,
                                focusedContainerColor = Surface2.copy(alpha = 0.6f),
                                unfocusedContainerColor = Surface2.copy(alpha = 0.3f),
                            ),
                        )
                        if (recoveryInput.isNotBlank() && !isValidFormat) {
                            Text(
                                "Key should be 24 characters (letters + digits), optionally with hyphens",
                                color = Coral, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        recoveryError?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = Coral, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(24.dp))
                        GradientButton(
                            text = "Verify and recover",
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isValidFormat && !working,
                            icon = Icons.Rounded.CheckCircle,
                        ) {
                            working = true
                            scope.launch {
                                val mk = withContext(Dispatchers.Default) {
                                    VaultSession.unlockWithRecoveryKey(ctx, recoveryInput)
                                }
                                working = false
                                if (mk != null) {
                                    vaultMasterKey = mk
                                    step = 2
                                } else {
                                    recoveryError = "Wrong recovery key — check what you saved"
                                }
                            }
                        }
                    }

                    // ── Step 2: Set new master password ─────────────────────────
                    2 -> {
                        val entropy = PasswordGenerator.entropy(newPassword)
                        val strong = entropy >= 60
                        val match = newPassword == confirmPassword && newPassword.isNotEmpty()

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconBadge(Icons.Rounded.LockReset, Mint, size = 72)
                            Spacer(Modifier.height(16.dp))
                            Text("Set New Master Password",
                                style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Your Recovery Key worked. Now create a new master password " +
                                        "to keep using the vault. The old password will stop working.",
                                color = TextSecondary, textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(24.dp))

                            VaultTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it; pwError = null },
                                label = "New master password",
                                visualTransformation = if (showPw) VisualTransformation.None
                                else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Next),
                                trailingIcon = {
                                    IconButton(onClick = { showPw = !showPw }) {
                                        Icon(
                                            if (showPw) Icons.Rounded.VisibilityOff
                                            else Icons.Rounded.Visibility,
                                            null, tint = TextSecondary,
                                        )
                                    }
                                },
                            )

                            // Strength meter
                            val meterColor = when {
                                entropy >= 60 -> Mint; entropy >= 40 -> Amber; else -> Coral
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (entropy / 100.0).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = meterColor, trackColor = Surface2,
                            )
                            Text(
                                when {
                                    newPassword.isEmpty() -> " "
                                    entropy >= 60 -> "Strong"
                                    entropy >= 40 -> "Okay — longer is better"
                                    else -> "Too weak"
                                },
                                style = MaterialTheme.typography.labelMedium, color = meterColor,
                                modifier = Modifier.align(Alignment.Start),
                            )

                            Spacer(Modifier.height(10.dp))
                            VaultTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = "Confirm password",
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done),
                            )
                            if (confirmPassword.isNotEmpty() && !match) {
                                Text("Passwords don't match", color = Coral,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            pwError?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, color = Coral, style = MaterialTheme.typography.labelMedium)
                            }

                            Spacer(Modifier.height(24.dp))
                            GradientButton(
                                text = "Change password & unlock",
                                modifier = Modifier.fillMaxWidth(),
                                enabled = strong && match && !working,
                                icon = Icons.Rounded.CheckCircle,
                            ) {
                                working = true
                                scope.launch {
                                    val mk = vaultMasterKey ?: return@launch
                                    withContext(Dispatchers.Default) {
                                        VaultSession.changePassword(ctx, mk, newPassword.toCharArray())
                                    }
                                    // Change was successful — open DB with the master key
                                    VaultSession.unlockWithKey(ctx, mk)
                                    working = false
                                    step = 3
                                }
                            }
                        }
                    }

                    // ── Step 3: Done ────────────────────────────────────────────
                    3 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconBadge(Icons.Rounded.CheckCircle, Mint, size = 72)
                        Spacer(Modifier.height(16.dp))
                        Text("Password Changed",
                            style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Your vault is unlocked with your new master password. " +
                                    "Your Recovery Key still works too.",
                            color = TextSecondary, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(32.dp))
                        GradientButton(
                            text = "Enter vault",
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.Shield,
                        ) { proceedToVault() }
                    }
                }
            }
        }
    }
}
