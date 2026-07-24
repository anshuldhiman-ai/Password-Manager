package com.family.pswdmngr

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.fragment.app.FragmentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.banks.*
import com.family.pswdmngr.ui.cards.*
import com.family.pswdmngr.ui.docs.*
import com.family.pswdmngr.ui.notes.*
import com.family.pswdmngr.ui.screens.*
import com.family.pswdmngr.ui.tasks.TasksScreen
import com.family.pswdmngr.ui.theme.PswdMngrTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Block screenshots & screen recording of the vault
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            PswdMngrTheme {
                val nav = rememberNavController()
                val start = when {
                    !VaultSession.vaultExists(this) -> "onboarding"
                    else -> "unlock"
                }

                // Snap back to lock screen whenever auto-lock fires
                DisposableEffect(Unit) {
                    VaultApp.onLocked = {
                        runOnUiThread {
                            nav.navigate("unlock") { popUpTo(0) { inclusive = true } }
                        }
                    }
                    onDispose { VaultApp.onLocked = null }
                }

                NavHost(
                    navController = nav,
                    startDestination = start,
                    enterTransition = {
                        slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280))
                    },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = {
                        slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(240))
                    },
                ) {
                    composable("onboarding") { OnboardingScreen(nav) }
                    composable("unlock") { UnlockScreen(nav) }
                    composable("recoveryKey") { RecoveryKeyScreen(nav) }
                    composable("forgotPassword") { ForgotPasswordScreen(nav) }
                    composable("vault") { MainScreen(nav) }
                    composable("entry/{id}") { back ->
                        EntryScreen(nav, back.arguments?.getString("id")?.toLongOrNull() ?: -1L)
                    }
                    composable("edit/{id}") { back ->
                        EditEntryScreen(nav, back.arguments?.getString("id")?.toLongOrNull() ?: -1L)
                    }
                    composable("generator") { GeneratorScreen(nav) }
                    composable("settings") { SettingsScreen(nav) }
                    composable("sbiRewardz") { SbiRewardzScreen(nav) }
                    composable("googleAccounts") { GoogleAccountsScreen(nav) }
                    composable("googleEdit/{id}") { back -> GoogleAccountEditor(nav, back.arguments?.getString("id")?.toLongOrNull() ?: -1L) }

                    // v2: wallet
                    composable("cards") { CardsScreen(nav) }
                    composable("cardDetail/{id}") { back ->
                        CardDetailScreen(nav, back.arguments?.getString("id")?.toLongOrNull() ?: -1L)
                    }
                    composable("cardEdit/{id}") { back ->
                        EditCardScreen(nav, back.arguments?.getString("id")?.toLongOrNull() ?: -1L)
                    }
                    composable("cardScan") { CardScannerScreen(nav) }
                    composable("csd") { CsdCardsScreen(nav) }
                    composable("csdEdit/{id}") { back ->
                        EditCardScreen(nav, back.arguments?.getString("id")?.toLongOrNull() ?: -1L, "CSD")
                    }
                    composable("banks") { BanksScreen(nav) }
                    composable("bankDetail/{id}") { back ->
                        BankDetailScreen(nav, back.arguments?.getString("id")?.toLongOrNull() ?: -1L)
                    }
                    composable("bankEdit/{id}") { back ->
                        EditBankScreen(nav, back.arguments?.getString("id")?.toLongOrNull() ?: -1L)
                    }

                    // v2: documents
                    composable("docs") { DocsScreen(nav) }
                    composable("docDetail/{id}") { back ->
                        DocDetailScreen(nav, back.arguments?.getString("id")?.toLongOrNull() ?: -1L)
                    }
                    composable("docEdit/{id}") { back ->
                        EditDocScreen(nav, back.arguments?.getString("id")?.toLongOrNull() ?: -1L)
                    }

                    // v2: notes & tasks
                    composable("notes") { NotesScreen(nav) }
                    composable("noteEdit/{id}") { back ->
                        EditNoteScreen(nav, back.arguments?.getString("id")?.toLongOrNull() ?: -1L)
                    }
                    composable("tasks") { TasksScreen(nav) }
                    composable("recycleBin") { RecycleBinScreen(nav) }
                    composable("passwordHealth") { PasswordHealthScreen(nav) }
                    composable("argon2benchmark") { Argon2idBenchmarkScreen(nav) }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Immediate relock for high security could go here; we use the 30s grace in VaultApp
    }
}
