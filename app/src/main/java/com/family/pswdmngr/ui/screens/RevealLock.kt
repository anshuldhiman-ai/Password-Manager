package com.family.pswdmngr.ui.screens

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A field display that hides its content behind a "tap to reveal" gate.
 * The user must authenticate with biometric (or master password fallback)
 * *each time* they want to see the value — even within an already-unlocked
 * session. After [revealDurationMs] the value auto-hides again.
 *
 * Intended for the most sensitive fields: UPI PIN, CVV, Aadhaar/PAN numbers.
 */
@Composable
fun RevealLockField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    revealDurationMs: Long = 30_000L, // auto-hide after 30s
    onCopy: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var revealed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Auto-hide after the reveal duration
    LaunchedEffect(revealed) {
        if (revealed) {
            delay(revealDurationMs)
            revealed = false
        }
    }

    fun revealWithPassword() {
        scope.launch {
            error = "Use master password from the unlock screen to continue"
        }
    }

    fun revealWithBiometric() {
        val activity = ctx as? FragmentActivity ?: return
        if (BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            // No biometric available — fall back to master password
            revealWithPassword()
            return
        }
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(ctx),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    revealed = true
                    error = null
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        error = errString.toString()
                    }
                }
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Reveal $label")
                .setSubtitle("Additional authentication required")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
        )
    }

    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (revealed) Icons.Rounded.LockOpen else Icons.Rounded.Lock, null,
                tint = if (revealed) Mint else Coral, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                SectionLabel(label)
                Spacer(Modifier.height(2.dp))
                if (revealed) {
                    Text(
                        value,
                        color = TextPrimary,
                        style = if (mono) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                        else MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Row(
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { revealWithBiometric() }
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "••••••••",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Tap to reveal",
                            color = Cyan,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            if (revealed) {
                onCopy?.let {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCopy()
                    }) {
                        Icon(Icons.Rounded.ContentCopy, "Copy", tint = Violet)
                    }
                }
                IconButton(onClick = { revealed = false }) {
                    Icon(Icons.Rounded.VisibilityOff, "Hide", tint = TextSecondary)
                }
            }
        }
        error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = Coral, style = MaterialTheme.typography.bodySmall)
        }
    }
}
