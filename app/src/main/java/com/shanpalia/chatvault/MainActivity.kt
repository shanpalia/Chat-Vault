package com.shanpalia.chatvault

import android.Manifest
import android.content.pm.PackageManager
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.os.Build
import android.content.Intent
import android.content.Context
import androidx.core.content.FileProvider
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.DisposableEffect
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


private const val CHAT_VAULT_NOTIFICATION_CHANNEL = "chat_vault_backup_updates"
private const val CHAT_VAULT_LIVE_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-1717784184671933/3602440218"
private const val CHAT_VAULT_TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
// Keep the test unit active during development; switch to the live unit only for the production release.

private fun showBackupNotification(context: Context, title: String, message: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHAT_VAULT_NOTIFICATION_CHANNEL,
            "Backup updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Notifications about imported and exported chat backups" }
        manager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, CHAT_VAULT_NOTIFICATION_CHANNEL)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    runCatching { NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification) }
}

private val Mint = Color(0xFF16A887)
private val DarkMint = Color(0xFF0D725E)
private val Ink = Color(0xFF17352F)
private val Page = Color(0xFFF3F8F6)
private val ChatBg = Color(0xFFE8F1EC)

private enum class Tab(val title: String) { HOME("Home"), BACKUPS("Backups"), CHATS("Chats"), TOOLS("Tools"), SETTINGS("Settings") }

class MainActivity : ComponentActivity() {
    private val adsReady = mutableStateOf(false)
    private var interstitialAd: InterstitialAd? = null
    private var requestBackupPicker by mutableStateOf(false)
    private var sharedBackupUris by mutableStateOf<List<Uri>>(emptyList())
    private var sharedBackupText by mutableStateOf<String?>(null)

    private fun loadChatVaultInterstitial() {
        if (interstitialAd != null) return
        InterstitialAd.load(
            AdRequest.Builder(CHAT_VAULT_TEST_INTERSTITIAL_AD_UNIT_ID).build(),
            object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }
                override fun onAdFailedToLoad(adError: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    private fun showChatVaultInterstitialAfterExport() {
        val ad = interstitialAd
        if (ad == null) return
        interstitialAd = null
        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                loadChatVaultInterstitial()
            }
            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError) {
                loadChatVaultInterstitial()
            }
        }
        ad.show(this)
    }

    @Suppress("DEPRECATION")
    private fun sharedUrisFrom(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableExtra(Intent.EXTRA_STREAM)
                listOfNotNull(stream ?: intent.clipData?.getItemAt(0)?.uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val streams = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                (streams?.toList().orEmpty() + (0 until (intent.clipData?.itemCount ?: 0)).mapNotNull { intent.clipData?.getItemAt(it)?.uri }).distinct()
            }
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> emptyList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread {
            MobileAds.initialize(
                this,
                InitializationConfig.Builder("ca-app-pub-1717784184671933~9904511724").build()
            ) {
                runOnUiThread { adsReady.value = true }
                loadChatVaultInterstitial()
            }
        }.start()
        requestBackupPicker = intent.getBooleanExtra("open_backup_picker", false)
        sharedBackupUris = sharedUrisFrom(intent)
        sharedBackupText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        val backupDir = File(filesDir, "backups").apply { mkdirs() }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Mint, secondary = DarkMint, background = Page, surface = Color.White, onSurface = Ink)) {
                ChatVaultApp(
                    backupDir = backupDir,
                    adsReady = adsReady.value,
                    onExportCompleted = { showChatVaultInterstitialAfterExport() },
                    importFile = { uri -> importSelected(uri, backupDir) },
                    openWhatsApp = { business -> launchWhatsApp(business) },
                    openHubLink = { url -> openExternalUrl(url) },
                    requestBackupPicker = requestBackupPicker,
                    onBackupPickerHandled = { requestBackupPicker = false },
                    sharedBackupUris = sharedBackupUris,
                    sharedBackupText = sharedBackupText,
                    onSharedBackupHandled = { sharedBackupUris = emptyList(); sharedBackupText = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_backup_picker", false)) requestBackupPicker = true
        sharedBackupUris = sharedUrisFrom(intent)
        sharedBackupText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun importSelected(uri: Uri, directory: File): File? {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "chat-export.txt"
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = File(directory, "${System.currentTimeMillis()}-$safeName")
        return try {
            contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            destination.takeIf { it.exists() && it.length() > 0 }
        } catch (_: Exception) { destination.delete(); null }
    }

    private fun launchWhatsApp(business: Boolean) {
        val packageName = if (business) "com.whatsapp.w4b" else "com.whatsapp"
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) startActivity(launch) else {
            Toast.makeText(this, if (business) "WhatsApp Business is not installed" else "WhatsApp is not installed", Toast.LENGTH_LONG).show()
        }
    }

    private fun openExternalUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

}

@Composable
private fun ChatVaultApp(
    backupDir: File,
    adsReady: Boolean,
    onExportCompleted: () -> Unit,
    importFile: (Uri) -> File?,
    openWhatsApp: (Boolean) -> Unit,
    openHubLink: (String) -> Unit,
    requestBackupPicker: Boolean,
    onBackupPickerHandled: () -> Unit,
    sharedBackupUris: List<Uri>,
    sharedBackupText: String?,
    onSharedBackupHandled: () -> Unit
) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var importedChat by remember { mutableStateOf<ImportedChat?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The user can change this later in Android Settings. */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    BackHandler {
        when {
            selectedFile != null -> selectedFile = null
            tab != Tab.HOME -> tab = Tab.HOME
            else -> showExitDialog = true
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val file = importFile(uri)
            if (file != null) { refresh++; toast = "Backup imported successfully"; showBackupNotification(context, "Backup imported", "Your chat backup is saved in Chat Vault.") } else toast = "Could not import this file"
        }
    }
    val zipExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val ok = exportBackups(context, backupDir.listFiles()?.filter { it.isFile }.orEmpty(), uri)
            toast = if (ok) "All backups exported as ZIP" else "Could not export backups"
            if (ok) {
                showBackupNotification(context, "Backup export complete", "Your Chat Vault backups were exported as a ZIP file.")
                onExportCompleted()
            }
        }
    }
    val backups = remember(refresh) { backupDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.sortedByDescending { it.lastModified() }.orEmpty() }
    LaunchedEffect(requestBackupPicker) {
        if (requestBackupPicker) {
            picker.launch(arrayOf("text/plain", "application/zip", "application/octet-stream"))
            onBackupPickerHandled()
        }
    }
    LaunchedEffect(sharedBackupUris, sharedBackupText) {
        var saved = 0
        sharedBackupUris.forEach { uri -> if (importFile(uri) != null) saved++ }
        if (saved > 0) {
            refresh++
            toast = if (saved == 1) "WhatsApp chat received and saved" else "$saved shared files saved"
            showBackupNotification(context, "Chat backup saved", if (saved == 1) "A shared chat backup was saved in Chat Vault." else "$saved shared files were saved in Chat Vault.")
            tab = Tab.BACKUPS
        } else if (sharedBackupText != null) {
            val textFile = File(backupDir, "${System.currentTimeMillis()}-shared-chat.txt")
            runCatching { textFile.writeText(sharedBackupText, Charsets.UTF_8); saved = 1 }
            if (saved > 0) { refresh++; toast = "Shared chat text saved"; showBackupNotification(context, "Chat backup saved", "Shared chat text was saved in Chat Vault."); tab = Tab.BACKUPS }
            else toast = "Could not save the shared chat"
        } else if (sharedBackupUris.isNotEmpty()) toast = "Could not import the shared chat file"
        if (sharedBackupUris.isNotEmpty() || sharedBackupText != null) onSharedBackupHandled()
    }
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1400); showSplash = false }
    if (showSplash) {
        BackHandler { /* Keep splash stable during launch */ }
        SplashScreen()
        return
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Chat Vault?", color = Ink, fontWeight = FontWeight.Bold) },
            text = { Text("Do you want to close the app? Your saved backups will remain on this device.") },
            confirmButton = { TextButton(onClick = { showExitDialog = false; (context as? ComponentActivity)?.finish() }) { Text("Exit", color = Mint) } },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Stay") } }
        )
    }

    LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(2400); toast = null } }

    if (selectedFile != null) {
        ChatViewer(file = selectedFile!!, backupDir = backupDir, onBack = { selectedFile = null })
        return
    }

    Scaffold(
        containerColor = Page,
        topBar = {
            Row(Modifier.fillMaxWidth().background(Color.White).statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.chat_vault_logo),
                    contentDescription = "Chat Vault logo",
                    modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Chat Vault", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    Text("By PaliaAPK HUB", fontSize = 11.sp, color = Mint, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Rounded.Lock, null, tint = Mint)
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Color.White)) {
                if (adsReady) ChatVaultTestBanner()
                NavigationBar(containerColor = Color.White, tonalElevation = 6.dp) {
                    Tab.values().forEach { item ->
                        val icon = when (item) {
                            Tab.HOME -> Icons.Rounded.Home
                            Tab.BACKUPS -> Icons.Rounded.Archive
                            Tab.CHATS -> Icons.Rounded.Chat
                            Tab.TOOLS -> Icons.Rounded.Build
                            Tab.SETTINGS -> Icons.Rounded.Settings
                        }
                        NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(icon, null) }, label = { Text(item.title, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Mint, selectedTextColor = Mint, indicatorColor = Color(0xFFE1F4EE)))
                    }
                }
            }
        },
        snackbarHost = { if (toast != null) Snackbar(Modifier.padding(12.dp)) { Text(toast!!) } }
    ) { padding ->
        when (tab) {
            Tab.HOME -> HomeScreen(Modifier.padding(padding), backups, onImport = { picker.launch(arrayOf("text/plain", "application/zip", "application/octet-stream")) }, onOpen = { selectedFile = it }, onWhatsApp = { openWhatsApp(false) }, onBusiness = { openWhatsApp(true) }, onBackups = { tab = Tab.BACKUPS })
            Tab.BACKUPS -> BackupScreen(Modifier.padding(padding), backups, onImport = { picker.launch(arrayOf("text/plain", "application/zip", "application/octet-stream")) }, onExport = { zipExporter.launch("Chat-Vault-Backups.zip") }, onOpen = { selectedFile = it })
            Tab.CHATS -> ChatsScreen(Modifier.padding(padding), backups, onOpen = { selectedFile = it })
            Tab.TOOLS -> ToolsScreen(Modifier.padding(padding), onWhatsApp = { openWhatsApp(false) }, onBusiness = { openWhatsApp(true) }, onImport = { picker.launch(arrayOf("text/plain", "application/zip", "application/octet-stream")) })
            Tab.SETTINGS -> SettingsScreen(Modifier.padding(padding), onOpenHubLink = openHubLink)
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier, backups: List<File>, onImport: () -> Unit, onOpen: (File) -> Unit, onWhatsApp: () -> Unit, onBusiness: () -> Unit, onBackups: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Ink).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your conversations.\nSafe in your vault.", color = Color.White, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Keep exported chats and media together, privately on this device.", color = Color(0xFFD2E9E2), fontSize = 12.sp)
                    }
                    Image(painterResource(R.drawable.chat_vault_logo), contentDescription = "Chat Vault", modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)))
                }
                Spacer(Modifier.height(18.dp))
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint)) {
                    Icon(Icons.Rounded.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Import Chat Backup", fontWeight = FontWeight.Bold)
                }
                Text("Supports WhatsApp TXT exports and ZIP exports with media", color = Color(0xFFB9D8CE), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item { Text("Quick access", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile("WhatsApp", Icons.Rounded.Chat, Modifier.weight(1f), onWhatsApp)
                QuickTile("Business", Icons.Rounded.Business, Modifier.weight(1f), onBusiness)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent backups", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f)); TextButton(onClick = onBackups) { Text("See all", color = Mint) }
            }
        }
        if (backups.isEmpty()) item { EmptyState("No backups yet", "Import a TXT or ZIP chat export to get started.", Icons.Rounded.Inventory2) }
        else items(backups.take(3), key = { it.absolutePath }) { BackupRow(it) { onOpen(it) } }
        item { InfoNote("Privacy first", "Your selected backup files stay in Chat Vault's private app storage. The app does not secretly access WhatsApp's private database.") }
    }
}

@Composable
private fun QuickTile(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).clickable(onClick = onClick).padding(17.dp)) {
        Icon(icon, null, tint = Mint, modifier = Modifier.size(27.dp)); Spacer(Modifier.height(12.dp))
        Text(title, color = Ink, fontWeight = FontWeight.Bold); Text("Open app", color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun BackupScreen(modifier: Modifier, backups: List<File>, onImport: () -> Unit, onExport: () -> Unit, onOpen: (File) -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Backup History", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f)); Text("${backups.size} saved", color = Mint, fontSize = 12.sp)
        }
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Add, null); Text("Import another backup") }
        OutlinedButton(onClick = onExport, enabled = backups.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.FolderZip, null); Spacer(Modifier.width(8.dp)); Text("Export all backups as ZIP") }
        InfoNote("Deleted chat protection", "If you imported a chat here before it was deleted from WhatsApp, this saved copy stays in Chat Vault and remains available below. Chat Vault cannot recover chats that were never exported or imported before deletion.")
        Spacer(Modifier.height(14.dp))
        if (backups.isEmpty()) EmptyState("Nothing saved yet", "Import a chat export to keep a copy safe, even if it is later deleted from WhatsApp.", Icons.Rounded.Archive)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(backups, key = { it.absolutePath }) { BackupRow(it) { onOpen(it) } } }
    }
}

@Composable
private fun ChatsScreen(modifier: Modifier, backups: List<File>, onOpen: (File) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(18.dp)) {
        Text("Your chats", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold)
        Text("Open an imported backup to browse conversations by contact.", color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        SearchField(query, { query = it }, "Search backup files")
        Spacer(Modifier.height(12.dp))
        val filtered = backups.filter { it.name.contains(query, true) }
        if (filtered.isEmpty()) EmptyState("No matching chats", "Import a chat export or change your search.", Icons.Rounded.Chat)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(filtered, key = { it.absolutePath }) { BackupRow(it) { onOpen(it) } } }
    }
}

@Composable
private fun ToolsScreen(modifier: Modifier, onWhatsApp: () -> Unit, onBusiness: () -> Unit, onImport: () -> Unit) {
    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Text("Tools", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold)
        ToolRow("Open WhatsApp", "Launch the regular WhatsApp app", Icons.Rounded.Chat, onWhatsApp)
        ToolRow("Open WhatsApp Business", "Launch WhatsApp Business", Icons.Rounded.Business, onBusiness)
        ToolRow("Import chat export", "Select a TXT or ZIP file from your phone", Icons.Rounded.FileUpload, onImport)
        InfoNote("How to export", "In WhatsApp, open a chat → ⋮ → More → Export chat. Choose Include media for a ZIP when offered, then import that exported file here.")
        InfoNote("Restore limitation", "Chat Vault can read and preserve exports. It cannot write messages back into WhatsApp or restore an exported TXT as a live WhatsApp conversation.")
    }
}


private data class HubUpdate(val version: String, val url: String, val notes: String, val available: Boolean)
private fun versionCompare(x: String, y: String): Int {
    val a=x.split(".", "-", "_").map { it.toIntOrNull() ?: 0 }; val b=y.split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
    for(i in 0 until maxOf(a.size,b.size)){ val d=(a.getOrNull(i)?:0).compareTo(b.getOrNull(i)?:0); if(d!=0)return d }; return 0
}
private fun getHubUpdate(installed: String): HubUpdate {
    val url="https://ralinnuegsbuvlhwpzln.supabase.co/rest/v1/apps?select=id,name,version,apk_url,telegram_message_id,release_notes&name=eq.Chat%20Vault&limit=1"
    val key="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJhbGlubnVlZ3NidXZsaHdwemxuIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODAyOTU2NDIsImV4cCI6MjA5NTg3MTY0Mn0.hIec6UxRx5gzSMTi5oJ3_xXw3d1QKCmKsPF-stBwIFE"
    val conn=(URL(url).openConnection() as HttpURLConnection).apply { connectTimeout=15000; readTimeout=15000; setRequestProperty("apikey",key); setRequestProperty("Authorization","Bearer $key"); setRequestProperty("Accept","application/json") }
    return try {
        val code=conn.responseCode; val body=(if(code in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
        if(code !in 200..299) error("PaliaAPK HUB server error ($code)")
        val rows=JSONArray(body); if(rows.length()==0) error("Chat Vault ka app record PaliaAPK HUB par nahi mila.")
        val app=rows.getJSONObject(0); val v=app.optString("version",""); if(v.isBlank())error("PaliaAPK HUB par version set nahi hai.")
        val id=app.optString("id",""); val tg=app.optString("telegram_message_id",""); val apk=app.optString("apk_url","")
        val download=when { tg.isNotBlank()&&tg!="null"->"https://paliaapk-telegram-api.onrender.com/download-apk/$tg"; apk.startsWith("https://")->apk; id.isNotBlank()&&id!="null"->"https://paliaapkhub.com/app.html?id=$id"; else->"https://paliaapkhub.com/" }
        HubUpdate(v,download,app.optString("release_notes",""),versionCompare(v,installed)>0)
    } finally { conn.disconnect() }
}

@Composable
private fun SettingsScreen(modifier: Modifier, onOpenHubLink: (String) -> Unit) {
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    val installed=remember { runCatching { @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName,0).versionName ?: "1.0.1" }.getOrDefault("1.0.1") }
    var checking by remember { mutableStateOf(false) }; var status by remember { mutableStateOf("Tap the button to check the latest version.") }
    var latest by remember { mutableStateOf<String?>(null) }; var notes by remember { mutableStateOf("") }; var apkLink by remember { mutableStateOf<String?>(null) }; var available by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Settings",fontSize=22.sp,color=Ink,fontWeight=FontWeight.Bold)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){ Icon(Icons.Rounded.Backup,null,tint=Mint); Spacer(Modifier.width(9.dp)); Text("WhatsApp chat backup kaise lein?",color=Ink,fontWeight=FontWeight.Bold,fontSize=16.sp) }
            Text("1. WhatsApp ya WhatsApp Business mein jis contact ki chat save karni hai, woh chat kholen.",color=Ink,fontSize=12.sp,lineHeight=18.sp)
            Text("2. Upar ⋮ menu → More → Export chat par tap karein.",color=Ink,fontSize=12.sp,lineHeight=18.sp)
            Text("3. Without media (sirf messages) ya Include media (messages aur media) chunen.",color=Ink,fontSize=12.sp,lineHeight=18.sp)
            Text("4. Share menu mein Chat Vault chunen. Na dikhe to Save to Files karein, phir Backups → Import another backup se TXT/ZIP file select karein.",color=Ink,fontSize=12.sp,lineHeight=18.sp)
            Text("5. Import ke baad Backups tab mein chat kholen aur messages browse/search karein.",color=Ink,fontSize=12.sp,lineHeight=18.sp)
            Text("Note: Export Chat aapko WhatsApp mein khud shuru karna hoga. Chat Vault private chats ko seedhe access nahi kar sakta.",color=DarkMint,fontSize=11.sp,lineHeight=16.sp,fontWeight=FontWeight.Medium)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){ Icon(Icons.Rounded.SystemUpdate,null,tint=Mint); Spacer(Modifier.width(9.dp)); Text("Chat Vault App Update",color=Ink,fontWeight=FontWeight.Bold,fontSize=16.sp) }
            Text("Installed version: v$installed",color=Color.Gray,fontSize=12.sp)
            Button(onClick={
                checking=true; status="PaliaAPK HUB se latest version check ho raha hai..."; latest=null; apkLink=null; available=false
                scope.launch { try { val u=withContext(Dispatchers.IO){getHubUpdate(installed)}; latest=u.version; notes=u.notes; apkLink=u.url; available=u.available; status=if(u.available) "New update available: v${u.version}" else "You're up to date • Latest v${u.version}" } catch(t:Throwable){status=t.message?: "Update check failed. Please try again."} finally{checking=false} }
            },enabled=!checking,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=Mint)) {
                if(checking)CircularProgressIndicator(Modifier.size(18.dp),color=Color.White,strokeWidth=2.dp) else Icon(Icons.Rounded.Refresh,null)
                Spacer(Modifier.width(8.dp)); Text(if(checking)"Checking..." else "Check for Updates")
            }
            Text(status,color=if(available)DarkMint else Ink,fontSize=12.sp,fontWeight=FontWeight.SemiBold,lineHeight=18.sp)
            if(latest!=null&&available){
                if(notes.isNotBlank())Text("What's new: $notes",color=Color.Gray,fontSize=11.sp,lineHeight=16.sp)
                Button(onClick={apkLink?.let(onOpenHubLink)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=DarkMint)){Icon(Icons.Rounded.Download,null);Spacer(Modifier.width(8.dp));Text("Download v$latest")}
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE5F7F0)),contentAlignment=Alignment.Center){Image(painterResource(R.drawable.chat_vault_logo),contentDescription="PaliaAPK HUB logo",modifier=Modifier.size(36.dp))}
                Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)){Text("PaliaAPK HUB",color=Ink,fontSize=17.sp,fontWeight=FontWeight.ExtraBold);Text("Apps • Games • Tools",color=Mint,fontSize=11.sp,fontWeight=FontWeight.SemiBold)}
                Icon(Icons.Rounded.Verified,null,tint=Mint)
            }
            Text("Discover more apps, games and useful tools from PaliaAPK HUB.",color=Color.Gray,fontSize=12.sp,lineHeight=17.sp)
            Button(onClick={onOpenHubLink("https://paliaapkhub.com/")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=Mint)){Icon(Icons.Rounded.Explore,null);Spacer(Modifier.width(8.dp));Text("Visit PaliaAPK HUB")}
            OutlinedButton(onClick={onOpenHubLink("https://paliaapkhub.com/updates.html")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Icon(Icons.Rounded.NewReleases,null);Spacer(Modifier.width(8.dp));Text("More Updates")}
            Text("Powered by PaliaAPK HUB • Developer: ShanPalia",color=Color.Gray,fontSize=10.sp)
        }
        InfoNote("Storage & privacy","Backups are stored locally in this app's private storage. Uninstalling Chat Vault removes its local app data. Keep another copy of important exports.")
        InfoNote("About Chat Vault","Version $installed • By PaliaAPK HUB")
    }
}

@Composable
private fun ToolRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color.White).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Mint, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) { Text(title, color = Ink, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Color.Gray, fontSize = 11.sp) }
        Icon(Icons.Rounded.ChevronRight, null, tint = Color.Gray)
    }
}

@Composable
private fun BackupRow(file: File, onOpen: () -> Unit) {
    val date = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()).format(Date(file.lastModified()))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color.White).clickable(onClick = onOpen).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFE4F5EF)), contentAlignment = Alignment.Center) { Icon(if (file.extension.equals("zip", true)) Icons.Rounded.FolderZip else Icons.Rounded.Description, null, tint = Mint) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name.substringAfter('-', file.name), color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(date, color = Color.Gray, fontSize = 10.sp)
            Text("${(file.length() / 1024).coerceAtLeast(1)} KB • ${file.extension.uppercase(Locale.getDefault())}", color = Mint, fontSize = 10.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Color.Gray)
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(25.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Mint, modifier = Modifier.size(42.dp)); Spacer(Modifier.height(9.dp))
        Text(title, color = Ink, fontWeight = FontWeight.Bold); Text(subtitle, color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun InfoNote(title: String, body: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(15.dp)) {
        Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp)); Text(body, color = Color.Gray, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = Mint); Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = Color.Gray, fontSize = 13.sp)
            BasicTextField(value, onChange, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 13.sp), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear", Modifier.clickable { onChange("") }, tint = Color.Gray)
    }
}

@Composable
private fun ChatViewer(file: File, backupDir: File, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val imported = remember(file) { runCatching { ChatParser.load(file, backupDir) }.getOrNull() }
    var myName by remember(file) { mutableStateOf("") }
    var showMyNameMenu by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf("All") }
    var dateFilter by remember { mutableStateOf("") }
    var showMedia by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val mediaFiles = imported?.mediaFiles.orEmpty()
    val messages = imported?.messages.orEmpty()
    val senders = remember(messages) { listOf("All") + messages.map { it.sender }.filter { it.isNotBlank() }.distinct().sorted() }
    val filtered = messages.filter { (search.isBlank() || it.body.contains(search, true) || it.sender.contains(search, true)) && (sender == "All" || it.sender == sender) && (dateFilter.isBlank() || it.date?.let { d -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d).contains(dateFilter) } == true) }

    Column(Modifier.fillMaxSize().background(ChatBg)) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowBack, "Back", Modifier.clickable(onClick = onBack), tint = Mint)
            Image(painterResource(R.drawable.chat_vault_logo), contentDescription = "Chat Vault", modifier = Modifier.size(38.dp).padding(start = 6.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(imported?.title ?: file.name, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1); Text("${messages.size} messages • ${imported?.mediaFiles?.size ?: 0} media files", color = Color.Gray, fontSize = 10.sp) }
        }
        Column(Modifier.padding(10.dp)) {
            if (mediaFiles.isNotEmpty()) {
                TextButton(onClick = { showMedia = !showMedia }, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Rounded.Collections, null); Text(if (showMedia) "Hide media" else "Media & files (${mediaFiles.size})")
                }
                if (showMedia) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 150.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)) {
                        items(mediaFiles, key = { it.absolutePath }) { media ->
                            Row(Modifier.fillMaxWidth().clickable { openMediaFile(context, media) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(mediaIcon(media), null, tint = Mint); Spacer(Modifier.width(8.dp))
                                Text(media.name, Modifier.weight(1f), color = Ink, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("OPEN", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            SearchField(search, { search = it }, "Search messages or contact")
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AssistChip(onClick = { showMyNameMenu = true }, label = { Text(if (myName.isBlank()) "Set your name" else "You: $myName", maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) })
                    DropdownMenu(expanded = showMyNameMenu, onDismissRequest = { showMyNameMenu = false }) {
                        senders.filter { it != "All" }.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { myName = name; showMyNameMenu = false })
                        }
                    }
                }
                var menu by remember { mutableStateOf(false) }
                Box {
                    AssistChip(onClick = { menu = true }, label = { Text(if (sender == "All") "All contacts" else sender, maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.Person, null) })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        senders.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { sender = it; menu = false }) }
                    }
                }
                OutlinedTextField(value = dateFilter, onValueChange = { dateFilter = it }, modifier = Modifier.weight(1f).height(52.dp), singleLine = true, placeholder = { Text("dd/MM/yyyy", fontSize = 11.sp) }, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp), trailingIcon = { if (dateFilter.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear date", Modifier.clickable { dateFilter = "" }) })
            }
        }
        if (imported == null) {
            EmptyState("Could not read this backup", "The file may be damaged or use an unsupported format.", Icons.Rounded.ErrorOutline)
        } else if (myName.isBlank() && senders.size > 1) {
            Column(Modifier.fillMaxWidth().padding(18.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)) {
                Text("Which name is yours?", color = Ink, fontWeight = FontWeight.Bold)
                Text("Choose your name once. Your messages will appear on the right; everyone else's on the left.", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                senders.filter { it != "All" }.forEach { name ->
                    TextButton(onClick = { myName = name }, modifier = Modifier.fillMaxWidth()) { Text(name, color = Mint) }
                }
            }
        } else if (filtered.isEmpty()) {
            EmptyState("No messages found", "Try changing your search or filters.", Icons.Rounded.SearchOff)
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(filtered, key = { it.id }) { message ->
                    if (message.system) {
                        Text(message.body, Modifier.fillMaxWidth().padding(horizontal = 22.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFD8E8E0)).padding(9.dp), color = Color(0xFF52645C), fontSize = 10.sp)
                    } else {
                        val isMine = myName.isNotBlank() && message.sender == myName
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                            Column(Modifier.widthIn(max = 310.dp).clip(RoundedCornerShape(15.dp)).background(if (isMine) Color(0xFFD9F5E6) else Color.White).padding(horizontal = 12.dp, vertical = 8.dp)) {
                                if (!isMine) Text(message.sender, color = DarkMint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(message.body, color = Ink, fontSize = 13.sp, lineHeight = 18.sp)
                                message.attachment?.let { media ->
                                    Text("📎 ${media.name}", Modifier.clickable { openMediaFile(context, media) }, color = Mint, fontSize = 10.sp)
                                }
                                message.date?.let { Text(SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(it), Modifier.align(Alignment.End), color = Color.Gray, fontSize = 9.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

 
private fun mediaIcon(file: File): androidx.compose.ui.graphics.vector.ImageVector {
    return when (file.extension.lowercase(Locale.getDefault())) {
        "jpg", "jpeg", "png", "webp", "gif" -> Icons.Rounded.Image
        "mp4", "mov", "3gp", "mkv" -> Icons.Rounded.Videocam
        "mp3", "aac", "ogg", "opus", "wav", "m4a" -> Icons.Rounded.AudioFile
        "pdf" -> Icons.Rounded.PictureAsPdf
        else -> Icons.Rounded.InsertDriveFile
    }
}

private fun exportBackups(context: Context, backups: List<File>, destination: Uri): Boolean {
    if (backups.isEmpty()) return false
    return try {
        context.contentResolver.openOutputStream(destination)?.use { output ->
            ZipOutputStream(output).use { zip ->
                backups.filter { it.isFile }.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: return false
        true
    } catch (_: Exception) {
        false
    }
}

private fun openMediaFile(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = when (file.extension.lowercase(Locale.getDefault())) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4", "mov", "3gp", "mkv" -> "video/*"
            "mp3", "aac", "ogg", "opus", "wav", "m4a" -> "audio/*"
            "pdf" -> "application/pdf"
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

 
@Composable
private fun ChatVaultTestBanner() {
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val adView = remember(context) { AdView(context) }
    val adSize = remember(screenWidthDp) {
        AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, screenWidthDp.coerceAtLeast(320))
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        factory = { adView }
    )

    LaunchedEffect(adSize) {
        val request = BannerAdRequest.Builder(
            "ca-app-pub-3940256099942544/9214589741",
            adSize
        ).build()
        adView.loadAd(request, object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(ad: BannerAd) { }
            override fun onAdFailedToLoad(adError: LoadAdError) { }
        })
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }
}

@Composable
private fun SplashScreen() {
    Column(Modifier.fillMaxSize().background(Color.White), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.chat_vault_logo), contentDescription = "Chat Vault logo", modifier = Modifier.size(116.dp))
        Spacer(Modifier.height(18.dp))
        Text("Chat Vault", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(5.dp))
        Text("By PaliaAPK HUB", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("BACKUP", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("•", color = Mint)
            Text("VIEW CHATS", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("•", color = Mint)
            Text("SECURE", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
