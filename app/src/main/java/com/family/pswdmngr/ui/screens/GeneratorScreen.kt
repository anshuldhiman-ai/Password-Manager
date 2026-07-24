package com.family.pswdmngr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.family.pswdmngr.VaultApp
import com.family.pswdmngr.crypto.PasswordGenerator
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(nav: NavController) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as VaultApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var mode by remember { mutableStateOf(0) } // 0 = random, 1 = passphrase
    var length by remember { mutableStateOf(20f) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }
    var words by remember { mutableStateOf(5f) }

    fun make() = if (mode == 0)
        PasswordGenerator.generate(length.toInt(), useUpper, useDigits, useSymbols)
    else PasswordGenerator.passphrase(words.toInt())

    var result by remember { mutableStateOf(make()) }
    LaunchedEffect(mode, length, useUpper, useDigits, useSymbols, words) { result = make() }

    val entropy = PasswordGenerator.entropy(result)

    Scaffold(
        containerColor = Midnight,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Generator", color = TextPrimary) },
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
            Modifier.padding(pad).fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlassCard {
                Text(
                    result,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (entropy / 128.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (entropy >= 80) Mint else if (entropy >= 60) Amber else Coral,
                    trackColor = Surface2,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${entropy.toInt()} bits of entropy",
                    style = MaterialTheme.typography.labelMedium, color = TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradientButton(
                        "Copy", Modifier.weight(1f), icon = Icons.Rounded.ContentCopy,
                    ) {
                        app.copySecret("generated", result)
                        scope.launch { snackbar.showSnackbar("Copied — clears in 30s") }
                    }
                    OutlinedButton(
                        onClick = { result = make() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, null, tint = Cyan)
                        Spacer(Modifier.width(8.dp))
                        Text("New", color = Cyan)
                    }
                }
            }

            TabRow(
                selectedTabIndex = mode,
                containerColor = Surface1,
                contentColor = Violet,
            ) {
                Tab(selected = mode == 0, onClick = { mode = 0 }, text = { Text("Random") })
                Tab(selected = mode == 1, onClick = { mode = 1 }, text = { Text("Passphrase") })
            }

            if (mode == 0) {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("LENGTH")
                        Spacer(Modifier.weight(1f))
                        Text("${length.toInt()}", color = Cyan, style = MaterialTheme.typography.titleMedium)
                    }
                    Slider(
                        value = length, onValueChange = { length = it },
                        valueRange = 8f..64f,
                        colors = SliderDefaults.colors(thumbColor = Violet, activeTrackColor = Violet),
                    )
                    ToggleRow("Uppercase (A-Z)", useUpper) { useUpper = it }
                    ToggleRow("Digits (0-9)", useDigits) { useDigits = it }
                    ToggleRow("Symbols (!@#…)", useSymbols) { useSymbols = it }
                }
            } else {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("WORDS")
                        Spacer(Modifier.weight(1f))
                        Text("${words.toInt()}", color = Cyan, style = MaterialTheme.typography.titleMedium)
                    }
                    Slider(
                        value = words, onValueChange = { words = it },
                        valueRange = 3f..8f, steps = 4,
                        colors = SliderDefaults.colors(thumbColor = Violet, activeTrackColor = Violet),
                    )
                    Text(
                        "Easy to type and remember — good for the family shared accounts.",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Violet,
                uncheckedTrackColor = Surface2,
            ),
        )
    }
}
