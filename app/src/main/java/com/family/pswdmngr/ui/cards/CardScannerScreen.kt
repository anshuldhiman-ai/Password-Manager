package com.family.pswdmngr.ui.cards

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.family.pswdmngr.ui.screens.GradientButton
import com.family.pswdmngr.ui.theme.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/**
 * Scans a credit/debit card using the camera + ML Kit text recognition.
 * Detected card number, expiry, and bank name are passed back to the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScannerScreen(nav: NavController) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }
    var torchOn by remember { mutableStateOf(false) }
    var detectedNumber by remember { mutableStateOf<String?>(null) }
    var detectedExpiry by remember { mutableStateOf<String?>(null) }
    var detectedBank by remember { mutableStateOf<String?>(null) }
    // Store camera reference for torch toggling
    var camera by remember { mutableStateOf<Camera?>(null) }

    // Once a card number is detected, auto-return to editor with data
    LaunchedEffect(detectedNumber) {
        detectedNumber?.let { num ->
            // Detect network from the number
            val network = CardNetwork.detect(num)
            val networkName = if (network != CardNetwork.UNKNOWN) network.name else "AUTO"

            // Pass data back to editor via savedStateHandle
            nav.previousBackStackEntry?.savedStateHandle?.apply {
                set("scanned_number", num)
                set("scanned_expiry", detectedExpiry ?: "")
                set("scanned_bank", detectedBank ?: "")
                set("scanned_network", networkName)
            }
            nav.popBackStack()
        }
    }

    Scaffold(
        containerColor = Midnight,
        topBar = {
            TopAppBar(
                title = { Text("Scan Card", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { torchOn = !torchOn }) {
                        Icon(
                            if (torchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                            "Torch", tint = if (torchOn) Amber else TextSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight),
            )
        },
    ) { pad ->
        if (!hasCameraPermission) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Camera permission needed", color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("We need camera access to scan your card.", color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                GradientButton("Grant permission") {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            return@Scaffold
        }

        Box(Modifier.fillMaxSize().padding(pad)) {
            // Camera preview
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val previewView = PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()

                        // Preview
                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(previewView.surfaceProvider)

                        // ML Kit text recognizer (on-device, no internet needed)
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                    if (detectedNumber != null) { // already scanned
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    val mediaImage = imageProxy.image
                                    if (mediaImage == null) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                    val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

                                    recognizer.process(inputImage)
                                        .addOnSuccessListener { result ->
                                            parseCardText(result.text)?.let { parsed ->
                                                detectedNumber = parsed.number
                                                detectedExpiry = parsed.expiry
                                                detectedBank = parsed.bank
                                            }
                                        }
                                        .addOnCompleteListener { imageProxy.close() }
                                }
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            provider.unbindAll()
                            val cam = provider.bindToLifecycle(
                                lifecycleOwner, cameraSelector, preview, imageAnalysis
                            )
                            camera = cam
                            // Set torch mode on initial bind
                            if (torchOn) {
                                cam.cameraControl.enableTorch(true)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(context))

                    previewView
                },
            )

            // Dynamic torch toggling
            LaunchedEffect(torchOn) {
                camera?.cameraControl?.enableTorch(torchOn)
            }

            // Card frame overlay guide
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1.586f)
                        .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                )
            }

            // Hint text at the bottom
            Column(Modifier.align(Alignment.BottomCenter).padding(24.dp)) {
                Text(
                    "Position the card inside the frame",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "The card number will be detected automatically",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(32.dp))
            }

            // Detecting indicator
            if (detectedNumber == null) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Midnight.copy(alpha = 0.75f),
                    ) {
                        Text(
                            "Hold steady — detecting card",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Parsed card data from OCR text. */
data class CardOcrResult(
    val number: String?,
    val expiry: String?,
    val bank: String?,
)

/** Extract card number, expiry and bank name from OCR text. */
fun parseCardText(text: String): CardOcrResult? {
    val clean = text.replace(Regex("[\\s\\n\\r]"), " ").trim()

    // Find card number by looking at grouped digit sequences (preserving natural spacing),
    // NOT by smashing all digits together (which would merge card number + expiry digits).
    // Look for 4-digit groups separated by spaces/dashes — typical card format.
    val groupedPattern = Regex("(?:\\d{4}[\\s-]?){3,5}")
    val number = groupedPattern.find(clean)?.value?.replace(Regex("[^0-9]"), "")?.take(19)
        ?: Regex("\\d{16}").find(clean.replace(Regex("[^0-9]"), ""))?.value
        ?: Regex("\\d{13,19}").find(clean.replace(Regex("[^0-9]"), ""))?.value
    // Only accept if it looks like a real card number (13-19 digits)
    val validNumber = number?.takeIf { it.length in 13..19 }

    // Find expiry: MM/YY or MM/YYYY (look for this BEFORE the number to avoid overlap)
    val expiry = Regex("(0[1-9]|1[0-2])\\s*/\\s*(\\d{2,4})").find(clean)?.let {
        val m = it.groupValues[1]
        val y = it.groupValues[2].take(2)
        "$m/$y"
    }

    // Find bank name from text (common bank keywords)
    val bank = detectBankFromText(clean)

    return if (validNumber != null) CardOcrResult(validNumber, expiry, bank) else null
}

/** Detect bank name from OCR text by looking for known bank keywords. */
private fun detectBankFromText(text: String): String? {
    val upper = text.uppercase()
    return when {
        "HDFC" in upper -> "HDFC"
        "ICICI" in upper -> "ICICI"
        "STATE BANK" in upper || "SBI" in upper -> "SBI"
        "AXIS" in upper -> "Axis"
        "KOTAK" in upper -> "Kotak"
        "YES BANK" in upper || "YES" in upper -> "Yes"
        "INDUSIND" in upper -> "IndusInd"
        "IDFC" in upper -> "IDFC"
        "PUNJAB NATIONAL" in upper || "PNB" in upper -> "PNB"
        "BANK OF BARODA" in upper || "BARODA" in upper -> "BoB"
        "CANARA" in upper -> "Canara"
        "UNION BANK" in upper -> "Union"
        "AMERICAN EXPRESS" in upper || "AMEX" in upper -> "Amex"
        else -> null
    }
}
