package com.shanpalia.chatvault

import android.content.Intent
import android.content.Context
import androidx.core.content.FileProvider
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Mint = Color(0xFF16A887)
private val DarkMint = Color(0xFF0D725E)
private val Ink = Color(0xFF17352F)
private val Page = Color(0xFFF3F8F6)
private val ChatBg = Color(0xFFE8F1EC)

private enum class Tab(val title: String) { HOME("Home"), BACKUPS("Backups"), CHATS("Chats"), TOOLS("Tools"), SETTINGS("Settings") }

class MainActivity : ComponentActivity() {
    private var requestBackupPicker by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBackupPicker = intent.getBooleanExtra("open_backup_picker", false)
        if (getSharedPreferences("chat_vault_settings", MODE_PRIVATE).getBoolean("floating_enabled", false) && Settings.canDrawOverlays(this)) {\n            startService(Intent(this, FloatingBubbleService::class.java))\n        }\n        val backupDir = File(filesDir, "backups").apply { mkdirs() }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Mint, secondary = DarkMint, background = Page, surface = Color.White, onSurface = Ink)) {
                ChatVaultApp(
                    backupDir = backupDir,
                    importFile = { uri -> importSelected(uri, backupDir) },
                    openWhatsApp = { business -> launchWhatsApp(business) },
                    floatingToggle = { enable -> toggleFloatingBubble(enable) },
                    openUpdates = { openUpdatesPage() },
                    requestBackupPicker = requestBackupPicker,
                    onBackupPickerHandled = { requestBackupPicker = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_backup_picker", false)) requestBackupPicker = true
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

    private fun openUpdatesPage() {
        val url = "https://github.com/shanpalia/Chat-Vault/releases/latest"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun toggleFloatingBubble(enable: Boolean) {
        if (enable && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Allow display over other apps, then enable the bubble again.", Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences("chat_vault_settings", MODE_PRIVATE).edit().putBoolean("floating_enabled", enable).apply()\n        val intent = Intent(this, FloatingBubbleService::class.java)\n        if (enable) startService(intent) else stopService(intent)
        Toast.makeText(this, if (enable) "Floating bubble enabled" else "Floating bubble stopped", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ChatVaultApp(
    backupDir: File,
    importFile: (Uri) -> File?,
    openWhatsApp: (Boolean) -> Unit,
    floatingToggle: (Boolean) -> Unit,
    openUpdates: () -> Unit,
    requestBackupPicker: Boolean,
    onBackupPickerHandled: () -> Unit
) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var importedChat by remember { mutableStateOf<ImportedChat?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
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
            if (file != null) { refresh++; toast = "Backup imported successfully" } else toast = "Could not import this file"
        }
    }
    val backups = remember(refresh) { backupDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.sortedByDescending { it.lastModified() }.orEmpty() }
    LaunchedEffect(requestBackupPicker) {
        if (requestBackupPicker) {
            picker.launch(arrayOf("text/plain", "application/zip", "application/octet-stream"))
            onBackupPickerHandled()
        }
    }
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1400); showSplash = false; if (!Settings.canDrawOverlays(context)) showOverlayDialog = true }
    if (showSplash) {
        BackHandler { /* Keep splash stable during launch */ }
        SplashScreen()
        return
    }

    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayDialog = false },
            title = { Text("Enable floating shortcut?", color = Ink, fontWeight = FontWeight.Bold) },
            text = { Text("Chat Vault can show a small bubble over WhatsApp so you can quickly open your backups. Android will ask you to allow Display over other apps.") },
            confirmButton = { TextButton(onClick = { showOverlayDialog = false; floatingToggle(true) }) { Text("Allow", color = Mint) } },
            dismissButton = { TextButton(onClick = { showOverlayDialog = false }) { Text("Not now") } }
        )
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
        },
        snackbarHost = { if (toast != null) Snackbar(Modifier.padding(12.dp)) { Text(toast!!) } }
    ) { padding ->
        when (tab) {
            Tab.HOME -> HomeScreen(Modifier.padding(padding), backups, onImport = { picker.launch(arrayOf("text/plain", "application/zip", "application/octet-stream")) }, onOpen = { selectedFile = it }, onWhatsApp = { openWhatsApp(false) }, onBusiness = { openWhatsApp(true) }, onBackups = { tab = Tab.BACKUPS })
            Tab.BACKUPS -> BackupScreen(Modifier.padding(padding), backups, onImport = { picker.launch(arrayOf("text/plain", "application/zip", "application/octet-stream")) }, onOpen = { selectedFile = it })
            Tab.CHATS -> ChatsScreen(Modifier.padding(padding), backups, onOpen = { selectedFile = it })
            Tab.TOOLS -> ToolsScreen(Modifier.padding(padding), onWhatsApp = { openWhatsApp(false) }, onBusiness = { openWhatsApp(true) }, onImport = { picker.launch(arrayOf("text/plain", "application/zip", "application/octet-stream")) })
            Tab.SETTINGS -> SettingsScreen(Modifier.padding(padding), onFloating = floatingToggle, onUpdates = openUpdates)
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
private fun BackupScreen(modifier: Modifier, backups: List<File>, onImport: () -> Unit, onOpen: (File) -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Backup History", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f)); Text("${backups.size} saved", color = Mint, fontSize = 12.sp)
        }
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Add, null); Text("Import another backup") }
        Spacer(Modifier.height(14.dp))
        if (backups.isEmpty()) EmptyState("Nothing saved yet", "Your imported chat backups will appear here.", Icons.Rounded.Archive)
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

@Composable
private fun SettingsScreen(modifier: Modifier, onFloating: (Boolean) -> Unit, onUpdates: () -> Unit) {
    val context = LocalContext.current\n    var floating by remember { mutableStateOf(context.getSharedPreferences("chat_vault_settings", Context.MODE_PRIVATE).getBoolean("floating_enabled", false)) }\n    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.BubbleChart, null, tint = Mint, modifier = Modifier.size(27.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text("Floating bubble", color = Ink, fontWeight = FontWeight.SemiBold); Text("Show the Chat Vault logo on top of other apps. Tap it for Open App, Backup, Close menu or Exit Bubble. Allow Display over other apps permission to use this shortcut.", color = Color.Gray, fontSize = 11.sp, lineHeight = 15.sp) }
            Switch(checked = floating, onCheckedChange = { enabled -> floating = enabled; onFloating(enabled) }, colors = SwitchDefaults.colors(checkedTrackColor = Mint))
        }
        InfoNote("Storage & privacy", "Backups are stored locally in this app's private storage. Uninstalling Chat Vault removes its local app data. Keep another copy of important exports.")
        InfoNote("Permissions", "The floating shortcut requires Android's Display over other apps permission. File import uses Android's system file picker.")
        ToolRow("App updates", "Check and install the latest Chat Vault release from PaliaAPK HUB", Icons.Rounded.SystemUpdate, onUpdates)
        InfoNote("About Chat Vault", "Version 1.0.1 • By PaliaAPK HUB")
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
