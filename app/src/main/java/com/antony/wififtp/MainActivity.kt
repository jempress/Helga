package com.antony.wififtp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Vivid brand palette
private val NavyDeep = Color(0xFFFFF8D6) // warm cream base
private val Navy = Color(0xFFFBF390) // logo gold
private val Indigo = Color(0xFFF2C94C) // deeper amber
private val Violet = Color(0xFFE8A23D) // honey accent
private val Gold = Color(0xFFF2B84B)
private val GoldLight = Color(0xFFFFD685)
private val Mint = Color(0xFF2ECC9A)
private val Coral = Color(0xFFFF6B6B)
private val Sky = Color(0xFF4FC3F7)
private val CardBg = Color(0xFF1A1A1A) // ink, matches bird silhouette

class MainActivity : ComponentActivity() {

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no op, optional permission */ }

    private val legacyStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* re-checked on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        Settings.init(applicationContext)

        // Edge to edge: let our gradient flow behind the status bar and
        // navigation bar
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)

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
            val view = LocalView.current
            SideEffect {
                // Dark icons on our light cream/gold system bars.
                val controller = WindowInsetsControllerCompat(window, view)
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Gold,
                    secondary = Mint,
                    background = NavyDeep,
                    surface = CardBg
                )
            ) {
                HelgaScreen(activity = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelgaScreen(activity: ComponentActivity) {
    var running by remember { mutableStateOf(FtpServerService.isRunning) }
    var ip by remember { mutableStateOf(FtpServerService.getLocalIpAddress() ?: "Not connected to WiFi") }
    var clientCount by remember { mutableIntStateOf(0) }

    var username by remember { mutableStateOf(Settings.username) }
    var password by remember { mutableStateOf(Settings.password) }
    var port by remember { mutableIntStateOf(Settings.port) }
    var keepScreenOn by remember { mutableStateOf(Settings.keepScreenOn) }
    var autoStart by remember { mutableStateOf(Settings.autoStartOnBoot) }
    var showSettings by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true

    var storageGranted by remember { mutableStateOf(hasStorageAccess()) }

    var diagnosticResult by remember { mutableStateOf<String?>(null) }
    fun runDiagnostic() {
        val root = Environment.getExternalStorageDirectory()
        val canRead = root.canRead()
        val entries = root.listFiles()
        diagnosticResult = if (entries == null) {
            "Root path: ${root.absolutePath}\ncanRead(): $canRead\nlistFiles() returned NULL permission is being denied at the OS level."
        } else {
            "Root path: ${root.absolutePath}\ncanRead(): $canRead\nVisible items: ${entries.size}" +
                if (entries.isNotEmpty()) "\nFirst few: ${entries.take(5).joinToString { it.name }}" else ""
        }
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    var batteryOptimized by remember { mutableStateOf(!isBatteryOptimizationIgnored()) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                storageGranted = hasStorageAccess()
                batteryOptimized = !isBatteryOptimizationIgnored()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(running) {
        while (running) {
            clientCount = FtpServerService.connectedClients.get()
            delay(1500)
        }
        clientCount = 0
    }

    DisposableEffect(running, keepScreenOn) {
        if (running && keepScreenOn) {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val ftpUrlWithAuth = "ftp://$username:$password@$ip:$port"
    val ftpUrlPlain = "ftp://$ip:$port"

    val infinite = rememberInfiniteTransition(label = "bg")
    val gradientShift by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shift"
    )
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(NavyDeep, Navy, lerp(Indigo, Violet, gradientShift).copy(alpha = 0.6f))
    )

    val pulse by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = CardBg.copy(alpha = 0.8f))
                    }
                }

                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Gold, GoldLight))),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "Helga logo",
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("Helga", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = CardBg)
                Spacer(Modifier.height(4.dp))
                Text("Your WiFi file courier", fontSize = 14.sp, color = CardBg.copy(alpha = 0.65f))

                if (!storageGranted) {
                    Spacer(Modifier.height(20.dp))
                    WarningCard(
                        icon = Icons.Filled.Lock,
                        title = "Storage access needed",
                        body = "Helga needs \"All files access\" to show your folders over FTP. Without it connections will succeed but folders will look empty.",
                        accent = Coral,
                        buttonLabel = "Grant access"
                    ) {
                        val intent = Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }

                if (batteryOptimized) {
                    Spacer(Modifier.height(12.dp))
                    WarningCard(
                        icon = Icons.Filled.BatteryAlert,
                        title = "Battery optimization may kill Helga",
                        body = "Some phones aggressively stop background apps. Exempt Helga from battery optimization to keep transfers reliable.",
                        accent = Sky,
                        buttonLabel = "Exempt Helga"
                    ) {
                        val intent = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            scope.launch { snackbarHostState.showSnackbar("Your phone doesn't support this directly check battery settings manually.") }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

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
                                    .scale(if (running) pulse else 1f)
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
                                Spacer(Modifier.height(14.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Group, contentDescription = null, tint = Sky, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (clientCount == 0) "No clients connected" else "$clientCount client(s) connected",
                                        fontSize = 12.sp,
                                        color = Sky
                                    )
                                }

                                Spacer(Modifier.height(14.dp))

                                Text(ftpUrlPlain, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Gold)

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
                                            snackbarHostState.showSnackbar("Address with login copied paste into Explorer's address bar")
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Copy address with login")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { runDiagnostic() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CardBg),
                    border = BorderStroke(1.dp, CardBg.copy(alpha = 0.4f)),
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
                        Text(result, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(14.dp))
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
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) Coral else Gold,
                        contentColor = NavyDeep
                    )
                ) {
                    Icon(if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "Stop Server" else "Start Server", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(20.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Tip: if Windows Explorer still refuses to connect, use the copied " +
                            "address-with-login in WinSCP or FileZilla instead — they're far more " +
                            "reliable than Explorer's built-in FTP client.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            containerColor = CardBg
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .padding(bottom = 24.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsFieldColors()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsFieldColors()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = port.toString(),
                    onValueChange = { it.toIntOrNull()?.let { v -> port = v } },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsFieldColors()
                )

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Keep screen on while running", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = { keepScreenOn = it; Settings.keepScreenOn = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Mint)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-start on boot", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = autoStart,
                        onCheckedChange = { autoStart = it; Settings.autoStartOnBoot = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Mint)
                    )
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        Settings.username = username
                        Settings.password = password
                        Settings.port = port
                        scope.launch {
                            snackbarHostState.showSnackbar("Saved restart the server for changes to apply")
                        }
                        showSettings = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = NavyDeep),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Save")
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(Modifier.height(16.dp))

                Text("About", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Helga v1.1 built by Anthony Wekesa (jempress)",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Licensed under GNU GPLv3 free and open source",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Gold,
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    focusedLabelColor = Gold,
    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
)

@Composable
private fun WarningCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    accent: Color,
    buttonLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, color = CardBg)
            }
            Spacer(Modifier.height(6.dp))
            Text(body, fontSize = 12.sp, color = CardBg.copy(alpha = 0.8f))
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(buttonLabel)
            }
        }
    }
}
