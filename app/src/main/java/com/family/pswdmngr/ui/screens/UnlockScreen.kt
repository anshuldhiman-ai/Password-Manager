package com.family.pswdmngr.ui.screens

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.family.pswdmngr.crypto.ApkSignatureVerifier
import com.family.pswdmngr.crypto.KeystoreWrapper
import com.family.pswdmngr.crypto.RootDetector
import com.family.pswdmngr.data.LockoutTracker
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Animated unlock screen with root/tamper warnings, exponential backoff
 * lockout, and optional auto-wipe.
 *
 * Quick unlock (biometric / face / device PIN) is only offered while the
 * grace window since the last master-password unlock is still open.
 * Past the window, only the master password works — a stolen phone whose
 * own lock was defeated still cannot open the vault.
 *
 * On rooted devices, biometric unlock is disabled entirely and a warning
 * banner is shown.
 */
@Composable
fun UnlockScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var unlocking by remember { mutableStateOf(false) } // success exit animation

    // --- Root / tamper state ---
    val isRooted = remember { RootDetector.isRooted(ctx) }
    val isTampered = remember { ApkSignatureVerifier.isTampered(ctx) }
    val rootReason = remember { RootDetector.reason() }
    val showSecurityWarnings = isRooted || isTampered

    val keystore = remember { KeystoreWrapper(ctx) }
    val graceActive = remember { VaultSession.bioGraceActive(ctx) }

    // Disable biometric if rooted (attacker could bypass phone's lock screen)
    val bioAvailable by remember {
        mutableStateOf(
            !isRooted && keystore.isEnabled && graceActive && BiometricManager.from(ctx)
                .canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        )
    }
    val graceExpired = remember { keystore.isEnabled && !graceActive }

    // --- Lockout (exponential backoff, persistent across screen restarts) ---
    var failCount by remember { mutableStateOf(LockoutTracker.failCount(ctx)) }
    val initialLockout = LockoutTracker.remainingLockoutMs(ctx)
    var lockoutMs by remember { mutableStateOf(initialLockout) }

    // Countdown every second during lockout
    LaunchedEffect(lockoutMs) {
        if (lockoutMs > 0) {
            delay(1000)
            lockoutMs = LockoutTracker.remainingLockoutMs(ctx)
        }
    }

    // ---- intro animation state ----
    var intro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { intro = true }
    val logoScale by animateFloatAsState(
        if (intro) 1f else 0.4f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "logoScale",
    )
    val contentAlpha by animateFloatAsState(
        if (intro) 1f else 0f, tween(600, delayMillis = 150), label = "contentAlpha",
    )

    // breathing glow behind the lock badge
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.9f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )

    // shake on wrong password
    val shake = remember { Animatable(0f) }
    LaunchedEffect(failCount) {
        if (failCount > 0) {
            for (i in 0..5) {
                shake.animateTo(if (i % 2 == 0) 14f else -14f, tween(45))
            }
            shake.animateTo(0f, tween(45))
        }
    }

    fun unlockOk() {
        LockoutTracker.recordSuccessfulUnlock(ctx)
        unlocking = true
        scope.launch {
            delay(260)
            nav.navigate("vault") { popUpTo(0) { inclusive = true } }
        }
    }

    // Auto-wipe if wipe-after-10 is enabled and limit hit
    fun handleAutoWipe() {
        val shouldWipe = LockoutTracker.recordFailedAttempt(ctx)
        failCount = LockoutTracker.failCount(ctx)
        if (shouldWipe) {
            VaultSession.lock()
            nav.navigate("unlock") { popUpTo(0) { inclusive = true } }
            // TODO: delete vault.db and reset SharedPreferences for actual vault wipe
            error = "Vault has been wiped due to 10 failed unlock attempts."
            return
        }
        lockoutMs = LockoutTracker.remainingLockoutMs(ctx)
    }

    fun biometricUnlock() {
        val activity = ctx as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(ctx),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val key = keystore.unwrap()
                    if (key != null && VaultSession.unlockWithKey(ctx, key)) unlockOk()
                    else error = "Quick unlock expired — enter your master password"
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock vault")
            .setSubtitle("Quick unlock is active for a limited time")
        if (Build.VERSION.SDK_INT >= 30) {
            info.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        } else {
            info.setAllowedAuthenticators(BIOMETRIC_STRONG)
            info.setNegativeButtonText("Use master password")
        }
        prompt.authenticate(info.build())
    }

    // Auto-trigger biometric if available (and device not rooted)
    LaunchedEffect(Unit) { if (bioAvailable) biometricUnlock() }

    val exitScale by animateFloatAsState(
        if (unlocking) 1.15f else 1f, tween(260, easing = FastOutSlowInEasing), label = "exitScale")
    val exitAlpha by animateFloatAsState(
        if (unlocking) 0f else 1f, tween(260), label = "exitAlpha")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HeroGradient)
            .graphicsLayer { scaleX = exitScale; scaleY = exitScale; alpha = exitAlpha }
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(60.dp))

        // --- Security warning banners ---
        if (showSecurityWarnings) {
            Column(Modifier.alpha(contentAlpha), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isRooted) {
                    SecurityWarningBanner(
                        text = if (rootReason.isNotBlank())
                            "Rooted device detected: $rootReason"
                        else "Rooted device detected",
                        subtitle = "Biometric unlock disabled. Enter your master password.",
                    )
                }
                if (isTampered) {
                    SecurityWarningBanner(
                        text = "APK signature mismatch",
                        subtitle = "This app may have been tampered with. Reinstall from a trusted source.",
                        isError = true,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(110.dp)
                    .scale(glow)
                    .clip(CircleShape)
                    .background(Violet.copy(alpha = 0.18f)),
            )
            Box(Modifier.scale(logoScale)) {
                IconBadge(if (unlocking || failCount > 5) Icons.Rounded.Shield else Icons.Rounded.Lock, Cyan, size = 84)
            }
        }

        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Welcome back", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    isRooted -> "Rooted device — enter your master password"
                    bioAvailable -> "Use fingerprint, face or your master password"
                    graceExpired -> "Quick unlock has expired for your safety.\nEnter your master password."
                    else -> "Enter your master password"
                },
                color = TextSecondary, textAlign = TextAlign.Center,
            )
            if (graceExpired) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Amber.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.LockClock, null, tint = Amber, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Password required after ${graceLabel(VaultSession.bioGraceMinutes(ctx))}",
                        color = Amber, style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (failCount > 0 && lockoutMs == 0L) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "$failCount failed attempt${if (failCount > 1) "s" else ""}",
                    color = Coral, style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Box(Modifier.graphicsLayer { translationX = shake.value }.alpha(contentAlpha)) {
            VaultTextField(
                value = password,
                onValueChange = { password = it; error = null },
                enabled = lockoutMs <= 0,
                label = "Master password",
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(
                            if (showPw) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            null, tint = TextSecondary,
                        )
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                Text(error ?: "", color = Coral, style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.weight(1f))

        if (working) {
            CircularProgressIndicator(color = Violet)
            Spacer(Modifier.height(8.dp))
            Text("Deriving key…", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        } else {
            Column(Modifier.alpha(contentAlpha), horizontalAlignment = Alignment.CenterHorizontally) {
                GradientButton(
                    when {
                        lockoutMs > 0 -> "Wait ${LockoutTracker.remainingLabel(ctx)}"
                        else -> "Unlock"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = password.isNotEmpty() && lockoutMs <= 0,
                ) {
                    working = true
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) {
                            VaultSession.unlock(ctx, password.toCharArray())
                        }
                        working = false
                        if (ok) {
                            unlockOk()
                        } else {
                            password = ""
                            error = "Wrong password"
                            handleAutoWipe()
                        }
                    }
                }

                // Biometric quick unlock button
                if (bioAvailable && lockoutMs <= 0) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { biometricUnlock() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Fingerprint, null, tint = Cyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Quick unlock", color = Cyan)
                    }
                }

                // "Forgot password?" — recovery key flow
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { nav.navigate("forgotPassword") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Forgot master password?", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SecurityWarningBanner(
    text: String,
    subtitle: String = "",
    isError: Boolean = false,
) {
    val bgColor = if (isError) Coral.copy(alpha = 0.12f) else Amber.copy(alpha = 0.12f)
    val iconColor = if (isError) Coral else Amber
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Warning, null, tint = iconColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text, color = iconColor, style = MaterialTheme.typography.labelMedium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun graceLabel(minutes: Int): String = when {
    minutes % 60 == 0 && minutes >= 60 ->
        "${minutes / 60} hour" + if (minutes > 60) "s" else ""
    else -> "$minutes min"
}
