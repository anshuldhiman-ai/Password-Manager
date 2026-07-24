package com.family.pswdmngr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.family.pswdmngr.crypto.PasswordGenerator
import com.family.pswdmngr.data.VaultEntry
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fully offline password health check.
 * Scans all login entries for:
 *  - Reused passwords
 *  - Weak passwords (< 60 bits entropy)
 *  - Old passwords (configurable threshold, default 365 days)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordHealthScreen(nav: NavController) {
    if (!VaultSession.isUnlocked) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by VaultSession.dao().observeAll().collectAsState(initial = emptyList())
    var analysis by remember { mutableStateOf<HealthAnalysis?>(null) }
    var working by remember { mutableStateOf(true) }
    var minEntropy by remember { mutableIntStateOf(60) } // bits
    var maxAgeDays by remember { mutableIntStateOf(365) } // days
    var showSettings by remember { mutableStateOf(false) }

    // Run analysis on data change
    LaunchedEffect(entries, minEntropy, maxAgeDays) {
        working = true
        analysis = withContext(Dispatchers.Default) {
            analyzeHealth(entries, minEntropy, maxAgeDays)
        }
        working = false
    }

    Scaffold(
        containerColor = Midnight,
        topBar = {
            TopAppBar(
                title = { Text("Password Health", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Rounded.Tune, "Settings", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight),
            )
        },
    ) { pad ->
        val a = analysis
        if (working || a == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Violet)
                    Spacer(Modifier.height(12.dp))
                    Text("Analyzing ${entries.size} entries…", color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Score card
            item {
                val score = a.score()
                val scoreColor = when { score >= 80 -> Mint; score >= 50 -> Amber; else -> Coral }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = scoreColor.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(28.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("HEALTH SCORE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text("${score}%", style = MaterialTheme.typography.displayLarge, color = scoreColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when { score >= 80 -> "Good — no urgent issues"
                                score >= 50 -> "Some passwords need attention"
                                else -> "Several weak or reused passwords found" },
                            color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            // Summary counts
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (a.reused > 0) {
                        SummaryChip("${a.reused} reused", Coral, Modifier.weight(1f))
                    }
                    if (a.weak > 0) {
                        SummaryChip("${a.weak} weak", Amber, Modifier.weight(1f))
                    }
                    if (a.old > 0) {
                        SummaryChip("${a.old} old", Cyan, Modifier.weight(1f))
                    }
                    if (a.reused == 0 && a.weak == 0 && a.old == 0) {
                        SummaryChip("All clear", Mint, Modifier.weight(1f))
                    }
                }
            }

            // Issues list
            if (a.issues.isEmpty()) {
                item {
                    Spacer(Modifier.height(40.dp))
                    EmptyState(Icons.Rounded.HealthAndSafety, Mint, "No issues found",
                        "All your passwords meet the current security thresholds.")
                }
            } else {
                item { Spacer(Modifier.height(4.dp)); SectionLabel("ISSUES") }
                items(a.issues, key = { it.hashCode() }) { issue ->
                    HealthIssueCard(issue)
                }
            }

            // Settings panel
            if (showSettings) {
                item {
                    Spacer(Modifier.height(4.dp)); SectionLabel("THRESHOLDS")
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Surface2.copy(alpha = 0.4f),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Minimum entropy: $minEntropy bits",
                                color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = minEntropy.toFloat(),
                                onValueChange = { minEntropy = it.toInt() },
                                valueRange = 20f..100f,
                                steps = 15,
                                colors = SliderDefaults.colors(
                                    thumbColor = Violet, activeTrackColor = Violet,
                                ),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Max password age: $maxAgeDays days",
                                color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = maxAgeDays.toFloat(),
                                onValueChange = { maxAgeDays = it.toInt() },
                                valueRange = 30f..730f,
                                steps = 20,
                                colors = SliderDefaults.colors(
                                    thumbColor = Cyan, activeTrackColor = Cyan,
                                ),
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun SummaryChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.10f),
        modifier = modifier,
    ) {
        Text(text, color = color, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

@Composable
private fun HealthIssueCard(issue: HealthIssue) {
    val color = when (issue.severity) {
        "reused" -> Coral; "weak" -> Amber; "old" -> Cyan; else -> TextSecondary
    }
    val icon = when (issue.severity) {
        "reused" -> Icons.Rounded.ContentCopy; "weak" -> Icons.Rounded.LockOpen
        "old" -> Icons.Rounded.Schedule; else -> Icons.Rounded.Info
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Surface2.copy(alpha = 0.4f),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(issue.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(issue.detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

// ── Analysis logic ───────────────────────────────────────────────────────

data class HealthIssue(
    val severity: String, // "reused" | "weak" | "old"
    val title: String,
    val detail: String,
)

data class HealthAnalysis(
    val total: Int,
    val reused: Int,
    val weak: Int,
    val old: Int,
    val issues: List<HealthIssue>,
) {
    fun score(): Int {
        if (total == 0) return 100
        var penalty = 0
        penalty += reused * 15
        penalty += weak * 10
        penalty += old * 5
        return (100 - penalty).coerceIn(0, 100)
    }
}

private fun analyzeHealth(
    entries: List<VaultEntry>,
    minEntropy: Int,
    maxAgeDays: Int,
): HealthAnalysis {
    val issues = mutableListOf<HealthIssue>()
    val passwordCounts = mutableMapOf<String, MutableList<VaultEntry>>()
    val now = System.currentTimeMillis()
    val maxAgeMs = maxAgeDays * 24L * 60L * 60L * 1000L

    for (entry in entries) {
        if (entry.password.isBlank()) continue

        // Group by password (to find reused)
        passwordCounts.getOrPut(entry.password) { mutableListOf() }.add(entry)

        // Check password age
        val age = now - entry.updatedAt
        if (age > maxAgeMs) {
            issues.add(HealthIssue("old", entry.title,
                "Password not changed in ${age / (24 * 60 * 60 * 1000)} days"))
        }

        // Check entropy
        val ent = PasswordGenerator.entropy(entry.password)
        if (ent < minEntropy) {
            issues.add(HealthIssue("weak", entry.title,
                "Entropy: ${ent.toInt()} bits (minimum: $minEntropy)"))
        }
    }

    // Find reused passwords
    for ((password, entriesWithPw) in passwordCounts) {
        if (entriesWithPw.size > 1) {
            val titles = entriesWithPw.joinToString(", ") { it.title }
            issues.add(HealthIssue("reused", "Password used in ${entriesWithPw.size} entries",
                "Entries: $titles"))
        }
    }

    return HealthAnalysis(
        total = entries.size,
        reused = issues.count { it.severity == "reused" },
        weak = issues.count { it.severity == "weak" },
        old = issues.count { it.severity == "old" },
        issues = issues,
    )
}
