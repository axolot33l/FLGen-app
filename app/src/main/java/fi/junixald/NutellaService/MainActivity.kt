package fi.junixald.NutellaService

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fi.junixald.NutellaService.ui.theme.NutellaServiceTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NutellaServiceTheme {
                AppNavigation()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }
    
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var showBatteryDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> 
            hasNotificationPermission = isGranted
            // After notification permission is handled, check battery optimization
            checkBatteryOptimization(context) { showBatteryDialog = it }
        }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // If already has permission, just check battery optimization
            checkBatteryOptimization(context) { showBatteryDialog = it }
        }
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text("Disable Battery Optimization") },
            text = { Text("To ensure the notification updates reliably in the background, please disable battery optimization for this app.") },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryDialog = false
                    requestIgnoreBatteryOptimizations(context)
                }) {
                    Text("Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainPage(
                prefsManager = prefsManager,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToCredits = { navController.navigate("credits") },
                onNavigateToCodes = { navController.navigate("codes") }
            )
        }
        composable("settings") {
            SettingsPage(
                prefsManager = prefsManager,
                onBack = { navController.popBackStack() }
            )
        }
        composable("credits") {
            CreditsPage(onBack = { navController.popBackStack() })
        }
        composable("codes") {
            CodesPage(
                prefsManager = prefsManager,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private fun checkBatteryOptimization(context: Context, onShowDialog: (Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent()
        val packageName = context.packageName
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            onShowDialog(true)
        }
    }
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        context.startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPage(
    prefsManager: PreferencesManager,
    onNavigateToSettings: () -> Unit,
    onNavigateToCredits: () -> Unit,
    onNavigateToCodes: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secret by prefsManager.sharedSecret.collectAsState(initial = "")
    var textInput by remember { mutableStateOf("") }
    
    LaunchedEffect(secret) {
        textInput = secret
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Nutella Generator") },
                actions = {
                    IconButton(onClick = onNavigateToCodes) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "View Codes")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onNavigateToCredits) {
                        Icon(Icons.Default.Info, contentDescription = "Credits")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Shared Secret") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            prefsManager.setSharedSecret(textInput)
                            CodeNotificationService.start(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save")
                }
            }
            
            Text(
                text = "made with Love ❤️ by 0yne in finland 🇫🇮",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://fakecrime.bio/junixald"))
                    context.startActivity(intent)
                },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text("open my social")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodesPage(prefsManager: PreferencesManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val secret by prefsManager.sharedSecret.collectAsState(initial = "")
    val codes = remember(secret) {
        if (secret.isBlank()) emptyList()
        else {
            val currentTime = System.currentTimeMillis()
            val hourMs = 3600_000L
            val currentIntervalStart = (currentTime / hourMs) * hourMs
            
            List(25) { i ->
                val time = currentIntervalStart + (i * hourMs)
                val code = CodeGenerator.generateFlCode(secret, time / 1000)
                time to code
            }
        }
    }
    
    val dateFormatter = remember { SimpleDateFormat("EEE, HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upcoming Codes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (secret.isBlank()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Please set a Shared Secret first")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(codes) { (time, code) ->
                    Card(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("FL Code", code)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Code $code copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (time <= System.currentTimeMillis() && System.currentTimeMillis() < time + 3600_000L) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (time <= System.currentTimeMillis() && System.currentTimeMillis() < time + 3600_000L) "Current Code" else dateFormatter.format(Date(time)),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                if (time <= System.currentTimeMillis() && System.currentTimeMillis() < time + 3600_000L) {
                                    Text(
                                        text = "Expires at " + dateFormatter.format(Date(time + 3600_000L)),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Text(
                                text = code,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(prefsManager: PreferencesManager, onBack: () -> Unit) {
    val startOnBoot by prefsManager.startOnBoot.collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start on Boot", modifier = Modifier.weight(1.0f))
                Switch(
                    checked = startOnBoot,
                    onCheckedChange = { enabled ->
                        scope.launch { prefsManager.setStartOnBoot(enabled) }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credits") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Nutella Generator", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Developed by 0yne")
                Spacer(modifier = Modifier.height(16.dp))
                Text("This app uses HMACSha1 to generate codes based on a shared secret and current time.")
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "made with Love ❤️ by 0yne in finland 🇫🇮",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://fakecrime.bio/junixald"))
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("open my social")
            }
        }
    }
}
