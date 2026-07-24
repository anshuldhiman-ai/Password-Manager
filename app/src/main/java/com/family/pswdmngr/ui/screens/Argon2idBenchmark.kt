package com.family.pswdmngr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.family.pswdmngr.crypto.VaultCrypto
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * On-device Argon2id benchmark. Measures actual KDF timing so you can tune
 * ARGON_M_KIB / ARGON_T / ARGON_P to hit the ~400-600ms sweet spot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Argon2idBenchmarkScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var lastMs by remember { mutableStateOf<Long?>(null) }
    var results by remember { mutableStateOf(listOf<Long>()) }

    // Current parameters (read-only display, editable in VaultCrypto.kt)
    val memKib = VaultCrypto.ARGON_M_KIB
    val tCost = VaultCrypto.ARGON_T
    val pCost = VaultCrypto.ARGON_P

    fun benchmark() {
        running = true
        scope.launch {
            val elapsed = withContext(Dispatchers.Default) {
                val t0 = System.nanoTime()
                val salt = VaultCrypto.randomBytes(16)
                val pw = "benchmark-password-2026".toByteArray()
                val key = VaultCrypto.deriveKey(pw, salt)
                VaultCrypto.wipe(key, pw)
                (System.nanoTime() - t0) / 1_000_000L
            }
            lastMs = elapsed
            results = (results + elapsed).takeLast(5)
            running = false
        }
    }

    Scaffold(
        containerColor = Midnight,
        topBar = {
            TopAppBar(
                title = { Text("Argon2id Benchmark", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Current Parameters", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(12.dp))

            Surface(shape = RoundedCornerShape(16.dp), color = Surface2.copy(alpha = 0.4f)) {
                Column(Modifier.padding(20.dp)) {
                    ParamRow("Memory (ARGON_M_KIB)", "$memKib KiB (${memKib / 1024} MiB)")
                    ParamRow("Iterations (ARGON_T)", "$tCost")
                    ParamRow("Parallelism (ARGON_P)", "$pCost")
                    ParamRow("Key length", "256 bits")
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Target: 400–600 ms on mid-range device",
                style = MaterialTheme.typography.labelMedium, color = TextSecondary)

            Spacer(Modifier.height(16.dp))

            if (lastMs != null) {
                val ms = lastMs!!
                val passes = ms in 250L..900L
                val color = when {
                    ms in 400L..600L -> Mint
                    passes -> Amber
                    else -> Coral
                }
                Text("$ms ms", style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Monospace), color = color)
                Text(
                    when {
                        ms in 400L..600L -> "✓ In target range"
                        ms < 400L -> "Too fast — increase ARGON_M_KIB for stronger protection"
                        ms > 600L -> "Slow — reduce ARGON_M_KIB to improve UX"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { benchmark() },
                enabled = !running,
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    if (running) Icons.Rounded.HourglassTop else Icons.Rounded.Speed,
                    null, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (running) "Benchmarking…" else "Run benchmark")
            }

            if (results.size > 1) {
                Spacer(Modifier.height(20.dp))
                Text("Last ${results.size} runs (ms)",
                    style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = Surface2.copy(alpha = 0.3f)) {
                    Text(
                        results.joinToString("  ·  "),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = TextPrimary,
                    )
                }
                val avg = results.average().toLong()
                Text("Average: $avg ms", style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
            }

            Spacer(Modifier.height(24.dp))

            Surface(shape = RoundedCornerShape(14.dp), color = Amber.copy(alpha = 0.08f)) {
                Text(
                    "Parameters are set in VaultCrypto.kt. Edit ARGON_M_KIB, ARGON_T, or ARGON_P, " +
                            "rebuild, and re-run this benchmark to tune for your target device.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ParamRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}
