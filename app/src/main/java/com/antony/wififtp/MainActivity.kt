package com.antony.wififtp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Rich brand palette
private val Navy = Color(0xFF1B2356)
private val NavyDeep = Color(0xFF0E1338)
private val Indigo = Color(0xFF3B4DB8)
private val Gold = Color(0xFFF2B84B)
private val GoldLight = Color(0xFFFFD685)
private val Mint = Color(0xFF2ECC9A)
private val Coral = Color(0xFFFF6B6B)
private val CardBg = Color(0xFF222A5E)

class MainActivity : ComponentActivity() {

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op, optional permission */ }

    private val legacyStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handled via re-check on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            legacyStoragePermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Gold,
                    secondary = Mint,
                    background = NavyDeep,
                    surface = CardBg
                )
            ) {
                HelgaScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelgaScreen() {
    var running by remember { mutableStateOf(FtpServerService.isRunning) }
    var ip by remember { mutableStateOf(FtpServerService.getLocalIpAddress() ?: "Not connected to WiFi") }
    val port = FtpServerService.port
    val username = FtpServerService.username
    val password = FtpServerService.password
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true

    var storageGranted by remember { mutableStateOf(hasStorageAccess()) }

    // Self-diagnostic: list what's actually visible at the FTP root, independent
    // of any FTP client or Windows. If this says 0 but you know your phone has
    // files, the problem is Android permissions, not the FTP server or Explorer.
    var diagnosticResult by remember { mutableStateOf<String?>(null) }
    fun runDiagnostic() {
        val root = Environment.getExternalStorageDirectory()
        val canRead = root.canRead()
        val entries = root.listFiles()
        diagnosticResult = if (entries == null) {
            "Root path: ${root.absolutePath}\ncanRead(): $canRead\nlistFiles() returned NULL — permission is being denied at the OS level."
        } else {
            "Root path: ${root.absolutePath}\ncanRead(): $canRead\nVisible items: ${entries.size}" +
                if (entries.isNotEmpty()) "\nFirst few: ${entries.take(5).joinToString { it.name }}" else ""
        }
    }

    // Re-check permission state whenever the user comes back to this screen
    // (e.g. after granting "All files access" in Settings).
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                storageGranted = hasStorageAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }


    // URL with embedded credentials — this form reliably bypasses Windows
    // Explorer's anonymous-login attempt that otherwise causes
    // "Windows cannot access this file, make sure the path is correct".
    val ftpUrlWithAuth = "ftp://$username:$password@$ip:$port"
    val ftpUrlPlain = "ftp://$ip:$port"

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(NavyDeep, Navy, Indigo.copy(alpha = 0.55f)),
    )

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(40.dp))

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Gold, GoldLight))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(36.dp))
                }

                Spacer(Modifier.height(16.dp))
                Text("Helga", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text("Your WiFi file courier", fontSize = 14.sp, color = Color.White.copy(alpha = 0.65f))

                if (!storageGranted) {
                    Spacer(Modifier.height(20.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Coral.copy(alpha = 0.18f))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = Coral, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Storage access needed", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Helga needs \"All files access\" to show your folders over FTP. " +
                                    "Without it, Windows will connect but see an empty folder.",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Grant access")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // Status card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (running) Mint else Color(0xFF6B7299))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (running) "Server is running" else "Server is stopped",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        AnimatedVisibility(visible = running, enter = fadeIn(), exit = fadeOut()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(Modifier.height(20.dp))

                                Text(
                                    ftpUrlPlain,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Gold
                                )

                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Person, contentDescription = null, tint = Mint, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("$username / $password", fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f))
                                }

                                Spacer(Modifier.height(20.dp))

                                val qrBitmap = remember(ftpUrlWithAuth) { QrCodeGenerator.generate(ftpUrlWithAuth) }
                                Box(
                                    modifier = Modifier
                                        .size(196.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .padding(10.dp)
                                ) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "FTP QR Code",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(Modifier.height(20.dp))

                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Helga FTP address", ftpUrlWithAuth))
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Address with login copied — paste into Explorer's address bar")
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Copy address with login")
                                }

                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Paste the copied address directly — it has your\nusername & password baked in so Explorer\nwon't prompt for anonymous login.",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { runDiagnostic() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Run storage diagnostic")
                }

                diagnosticResult?.let { result ->
                    Spacer(Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            result,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = {
                        val intent = Intent(context, FtpServerService::class.java)
                        if (running) {
                            intent.action = FtpServerService.ACTION_STOP
                            context.startService(intent)
                            running = false
                        } else {
                            context.startForegroundService(intent)
                            running = true
                            ip = FtpServerService.getLocalIpAddress() ?: "Not connected to WiFi"
                        }
                    },
                    enabled = running || storageGranted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) Coral else Gold,
                        contentColor = NavyDeep
                    )
                ) {
                    Icon(if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (running) "Stop Server" else "Start Server",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(20.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Tip: if Windows Explorer still refuses to connect, " +
                            "use the copied address-with-login in WinSCP or FileZilla " +
                            "instead — they're far more reliable than Explorer's built-in FTP client.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
