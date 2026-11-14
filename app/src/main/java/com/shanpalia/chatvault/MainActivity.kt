package com.shanpalia.chatvault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val backupDir = File(filesDir, "backups").apply { mkdirs() }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Mint, secondary = DarkMint, background = Page, surface = Color.White, onSurface = Ink)) {
                ChatVaultApp(
                    backupDir = backupDir,
                    importFile = { uri -> importSelected(uri, backupDir) },
                    openWhatsApp = { business -> launchWhatsApp(business) },
                    floatingToggle = { enable -> toggleFloatingBubble(enable) }
                )
            }
        }
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

    private fun toggleFloatingBubble(enable: Boolean) {
        if (enable && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Allow display over other apps, then enable the bubble again.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, FloatingBubbleService::class.java)
        if (enable) startService(intent) else stopService(intent)
        Toast.makeText(this, if (enable) "Floating bubble enabled" else "Floating bubble stopped", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ChatVaultApp(
    backupDir: File,
    importFile: (Uri) -> File?,
    openWhatsApp: (Boolean) -> Unit,
    floatingToggle: (Boolean) -> Unit
) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var importedChat by remember { mutableStateOf<ImportedChat?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val file = importFile(uri)
            if (file != null) { refresh++; toast = "Backup imported successfully" } else toast = "Could not import this file"
        }
    }
    val backups = remember(refresh) { backupDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.sortedByDescending { it.lastModified() }.orEmpty() }

    LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(2400); toast = null } }

    if (selectedFile != null) {
        ChatViewer(file = selectedFile!!, backupDir = backupDir, onBack = { selectedFile = null })
        return
    }

    Scaffold(
        containerColor = Page,
        topBar = {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Ink), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Shield, null, tint = Mint, modifier = Modifier.size(29.dp))
                    Icon(Icons.Rounded.Chat, null, tint = Color.White, modifier = Modifier.size(17.dp).offset(x = (-2).dp, y = 2.dp))
                }
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
            Tab.SETTINGS -> SettingsScreen(Modifier.padding(padding), onFloating = floatingToggle)
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
                    Icon(Icons.Rounded.Security, null, tint = Mint, modifier = Modifier.size(54.dp))
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
private fun SettingsScreen(modifier: Modifier, onFloating: (Boolean) -> Unit) {
    var floating by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.BubbleChart, null, tint = Mint, modifier = Modifier.size(27.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text("Floating bubble", color = Ink, fontWeight = FontWeight.SemiBold); Text("Show a shortcut over other apps", color = Color.Gray, fontSize = 11.sp) }
            Switch(checked = floating, onCheckedChange = { floating = it; onFloating(it) }, colors = SwitchDefaults.colors(checkedTrackColor = Mint))
        }
        InfoNote("Storage & privacy", "Backups are stored locally in this app's private storage. Uninstalling Chat Vault removes its local app data. Keep another copy of important exports.")
        InfoNote("Permissions", "The floating shortcut requires Android's Display over other apps permission. File import uses Android's system file picker.")
        InfoNote("About Chat Vault", "Version 1.0 • By PaliaAPK HUB")
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
    val imported = remember(file) { runCatching { ChatParser.load(file, backupDir) }.getOrNull() }
    var search by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf("All") }
    var dateFilter by remember { mutableStateOf("") }
    val messages = imported?.messages.orEmpty()
    val senders = remember(messages) { listOf("All") + messages.map { it.sender }.filter { it.isNotBlank() }.distinct().sorted() }
    val filtered = messages.filter { (search.isBlank() || it.body.contains(search, true) || it.sender.contains(search, true)) && (sender == "All" || it.sender == sender) && (dateFilter.isBlank() || it.date?.let { d -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d).contains(dateFilter) } == true) }

    Column(Modifier.fillMaxSize().background(ChatBg)) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowBack, "Back", Modifier.clickable(onClick = onBack), tint = Mint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(imported?.title ?: file.name, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1); Text("${messages.size} messages • ${imported?.mediaFiles?.size ?: 0} media files", color = Color.Gray, fontSize = 10.sp) }
        }
        Column(Modifier.padding(10.dp)) {
            SearchField(search, { search = it }, "Search messages or contact")
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
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
        } else if (filtered.isEmpty()) {
            EmptyState("No messages found", "Try changing your search or filters.", Icons.Rounded.SearchOff)
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(filtered, key = { it.id }) { message ->
                    if (message.system) {
                        Text(message.body, Modifier.fillMaxWidth().padding(horizontal = 22.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFD8E8E0)).padding(9.dp), color = Color(0xFF52645C), fontSize = 10.sp)
                    } else {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.sender.isBlank()) Alignment.End else Alignment.Start) {
                            Column(Modifier.widthIn(max = 310.dp).clip(RoundedCornerShape(15.dp)).background(if (message.sender.isBlank()) Color(0xFFD9F5E6) else Color.White).padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(message.sender, color = DarkMint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(message.body, color = Ink, fontSize = 13.sp, lineHeight = 18.sp)
                                message.attachment?.let { Text("📎 ${it.name}", color = Mint, fontSize = 10.sp) }
                                message.date?.let { Text(SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(it), Modifier.align(Alignment.End), color = Color.Gray, fontSize = 9.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}
